/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.browsermob;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
import org.java_bandwidthlimiter.BandwidthLimiter;
import org.java_bandwidthlimiter.StreamManager;
import org.nexial.core.model.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.core.har.HarLog;
import net.lightbody.bmp.core.har.HarNameVersion;
import net.lightbody.bmp.core.har.HarPage;
import net.lightbody.bmp.core.util.ThreadUtils;
import net.lightbody.bmp.exception.JettyException;
import net.lightbody.bmp.proxy.BrowserMobProxyHandler;
import net.lightbody.bmp.proxy.ProxyServer;
import net.lightbody.bmp.proxy.dns.AdvancedHostResolver;
import net.lightbody.bmp.proxy.http.RequestInterceptor;
import net.lightbody.bmp.proxy.http.ResponseInterceptor;
import net.lightbody.bmp.proxy.jetty.http.HttpContext;
import net.lightbody.bmp.proxy.jetty.http.HttpListener;
import net.lightbody.bmp.proxy.jetty.http.SocketListener;
import net.lightbody.bmp.proxy.jetty.jetty.Server;
import net.lightbody.bmp.proxy.jetty.util.InetAddrPort;
import net.lightbody.bmp.util.BrowserMobProxyUtil;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class NexialProxyServer extends ProxyServer {
    private static final HarNameVersion CREATOR = new HarNameVersion("BrowserMob Proxy",
                                                                     BrowserMobProxyUtil.getVersionString()
                                                                     + "-legacy");
    private static final Logger LOG = LoggerFactory.getLogger(NexialProxyServer.class);

    private Server server;
    private int port = 0;
    private NexialBrowserMobHttpClient client;
    private StreamManager streamManager;
    private HarPage currentPage;
    private BrowserMobProxyHandler handler;
    private AtomicInteger pageCount = new AtomicInteger(1);
    private AtomicInteger requestCounter = new AtomicInteger(0);
    private ExecutionContext context;

    public NexialProxyServer() { }

    public NexialProxyServer(int port) { this.port = port; }

    public void setContext(ExecutionContext context) { this.context = context; }

    public void start() {
        if (port == -1) { throw new IllegalStateException("Must set port before starting"); }

        //create a stream manager that will be capped to 100 Megabits
        //remember that by default it is disabled!
        streamManager = new StreamManager(100 * BandwidthLimiter.OneMbps);

        server = new Server();
        HttpListener listener = new SocketListener(new InetAddrPort(getLocalHost(), getPort()));
        server.addListener(listener);
        HttpContext context = new HttpContext();
        context.setContextPath("/");
        server.addContext(context);

        handler = new BrowserMobProxyHandler();
        handler.setJettyServer(server);
        handler.setShutdownLock(new Object());

        client = new NexialBrowserMobHttpClient(streamManager, requestCounter);
        ProxyHandler proxyHandler = new ProxyHandler();
        proxyHandler.setContext(this.context);
        client.setProxyHandler(proxyHandler);

        // if native DNS fallback is explicitly disabled, replace the default resolver with a dnsjava-only resolver
        if ("false".equalsIgnoreCase(System.getProperty(ALLOW_NATIVE_DNS_FALLBACK))) {
            client.setResolver(ClientUtil.createDnsJavaResolver());
        }

        client.prepareForBrowser();
        handler.setHttpClient(client);

        context.addHandler(handler);
        try {
            server.start();
        } catch (Exception e) {
            // wrapping unhelpful Jetty Exception into a RuntimeException
            throw new JettyException("Exception occurred when starting the server", e);
        }

        setPort(listener.getPort());
    }

    public void cleanup() { handler.cleanup(); }

    public void stop() {
        cleanup();
        if (client != null) { client.shutdown(); }
        try {
            if (server != null) { server.stop(); }
        } catch (InterruptedException e) {
            // the try/catch block in server.stop() is manufacturing a phantom InterruptedException, so this should not occur
            throw new JettyException("Exception occurred when stopping the server", e);
        }
    }

    public int getPort() { return port; }

    public void setPort(int port) { this.port = port; }

    @Override
    public Har getHar() {
        // Wait up to 5 seconds for all active requests to cease before returning the HAR.
        // This helps with race conditions but won't cause deadlocks should a request hang
        // or error out in an unexpected way (which of course would be a bug!)
        boolean success = ThreadUtils.pollForCondition(() -> requestCounter.get() == 0, 5, SECONDS);

        if (!success) { LOG.warn("Waited 5 seconds for requests to cease before returning HAR; giving up!"); }

        return client.getHar();
    }

    @Override
    public Har newHar() { return newHar(null); }

    @Override
    public Har newHar(String initialPageRef) { return newHar(initialPageRef, null); }

    @Override
    public Har newHar(String initialPageRef, String initialPageTitle) {
        pageCount.set(0); // this will be automatically incremented by newPage() below

        Har oldHar = getHar();

        Har har = new Har(new HarLog(CREATOR));
        client.setHar(har);
        newPage(initialPageRef, initialPageTitle);

        return oldHar;
    }

    @Override
    public Har newPage() { return newPage(null); }

    public Har newPage(String pageRef) { return newPage(pageRef, null); }

    @Override
    public Har newPage(String pageRef, String pageTitle) {
        if (pageRef == null) { pageRef = "Page " + pageCount.get(); }

        if (pageTitle == null) { pageTitle = pageRef; }

        client.setHarPageRef(pageRef);
        currentPage = new HarPage(pageRef, pageTitle);
        client.getHar().getLog().addPage(currentPage);

        pageCount.incrementAndGet();

        return client.getHar();
    }

    public void endPage() {
        if (currentPage == null) { return; }
        currentPage.getPageTimings().setOnLoad(new Date().getTime() - currentPage.getStartedDateTime().getTime());
        client.setHarPageRef(null);
        currentPage = null;
    }

    public void remapHost(String source, String target) { client.remapHost(source, target); }

    @Deprecated
    public void addRequestInterceptor(HttpRequestInterceptor i) { client.addRequestInterceptor(i); }

    public void addRequestInterceptor(RequestInterceptor interceptor) { client.addRequestInterceptor(interceptor); }

    @Deprecated
    public void addResponseInterceptor(HttpResponseInterceptor i) { client.addResponseInterceptor(i); }

    public void addResponseInterceptor(ResponseInterceptor interceptor) { client.addResponseInterceptor(interceptor); }

    public StreamManager getStreamManager() { return streamManager; }

    //use getStreamManager().setDownstreamKbps instead
    @Deprecated
    public void setDownstreamKbps(long downstreamKbps) {
        streamManager.setDownstreamKbps(downstreamKbps);
        streamManager.enable();
    }

    //use getStreamManager().setUpstreamKbps instead
    @Deprecated
    public void setUpstreamKbps(long upstreamKbps) {
        streamManager.setUpstreamKbps(upstreamKbps);
        streamManager.enable();
    }

    //use getStreamManager().setLatency instead
    @Deprecated
    public void setLatency(long latency) {
        streamManager.setLatency(latency);
        streamManager.enable();
    }

    public void setRequestTimeout(int requestTimeout) { client.setRequestTimeout(requestTimeout); }

    public void setSocketOperationTimeout(int readTimeout) { client.setSocketOperationTimeout(readTimeout); }

    public void setConnectionTimeout(int connectionTimeout) { client.setConnectionTimeout(connectionTimeout); }

    public void autoBasicAuthorization(String domain, String username, String password) {
        client.autoBasicAuthorization(domain, username, password);
    }

    public void rewriteUrl(String match, String replace) { client.rewriteUrl(match, replace); }

    @Override
    public void blacklistRequests(String pattern, int responseCode) {
        client.blacklistRequests(pattern, responseCode, null);
    }

    @Override
    public void blacklistRequests(String pattern, int responseCode, String method) {
        client.blacklistRequests(pattern, responseCode, method);
    }

    public void whitelistRequests(String[] patterns, int responseCode) {
        client.whitelistRequests(patterns, responseCode);
    }

    public void addHeader(String name, String value) { client.addHeader(name, value); }

    public void setCaptureHeaders(boolean captureHeaders) { client.setCaptureHeaders(captureHeaders); }

    public void setCaptureContent(boolean captureContent) { client.setCaptureContent(captureContent); }

    public void setCaptureBinaryContent(boolean captureBinaryContent) {
        client.setCaptureBinaryContent(captureBinaryContent);
    }

    @Override
    public void clearDNSCache() {
        if (client != null && client.getResolver() instanceof AdvancedHostResolver) {
            AdvancedHostResolver advancedHostResolver = client.getResolver();
            advancedHostResolver.clearDNSCache();
        } else {
            LOG.warn(
                "Attempting to clear DNS cache, but host resolver is not an AdvancedHostRemapper. Host resolver is: {}",
                client != null ? client.getResolver() : null);
        }
    }

    @Override
    public void setDNSCacheTimeout(int timeout) {
        if (client != null && client.getResolver() instanceof AdvancedHostResolver) {
            AdvancedHostResolver advancedHostResolver = client.getResolver();
            advancedHostResolver.setNegativeDNSCacheTimeout(timeout, MILLISECONDS);
            advancedHostResolver.setPositiveDNSCacheTimeout(timeout, MILLISECONDS);
        } else {
            LOG.warn(
                "Attempting to set DNS cache timeout, but host resolver is not an AdvancedHostRemapper. Host resolver is: {}",
                client != null ? client.getResolver() : null);
        }
    }

    @Override
    public void waitForNetworkTrafficToStop(final long quietPeriodInMs, long timeoutInMs) {
        boolean result = ThreadUtils.pollForCondition(() -> {
            Date lastCompleted = null;
            Har har = client.getHar();
            if (har == null || har.getLog() == null) { return true; }

            for (HarEntry entry : har.getLog().getEntries()) {
                // if there is an active request, just stop looking
                if (entry.getResponse().getStatus() < 0) { return false; }

                Date end = new Date(entry.getStartedDateTime().getTime() + entry.getTime());
                if (lastCompleted == null) {
                    lastCompleted = end;
                } else if (end.after(lastCompleted)) {
                    lastCompleted = end;
                }
            }

            return lastCompleted != null && System.currentTimeMillis() - lastCompleted.getTime() >= quietPeriodInMs;
        }, timeoutInMs, MILLISECONDS);

        if (!result) {
            throw new TimeoutException("Timed out after "
                                       + timeoutInMs
                                       + " ms while waiting for network traffic to stop");
        }
    }

    public void setOptions(Map<String, String> options) {
        if (options.containsKey("httpProxy")) { client.setHttpProxy(options.get("httpProxy")); }
    }
}
