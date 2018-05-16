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

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.java_bandwidthlimiter.StreamManager;
import org.nexial.core.utils.ConsoleUtils;

import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.core.har.HarPostData;
import net.lightbody.bmp.core.har.HarRequest;
import net.lightbody.bmp.proxy.http.BrowserMobHttpClient;
import net.lightbody.bmp.proxy.http.BrowserMobHttpRequest;
import net.lightbody.bmp.proxy.http.BrowserMobHttpResponse;
import net.lightbody.bmp.proxy.http.RequestInterceptor;

import static org.nexial.core.NexialConst.OPT_PROXY_LOCALHOST;

public class NexialBrowserMobHttpClient extends BrowserMobHttpClient {
    private ProxyHandler proxyHandler;
    private boolean requestCaptured = false;

    public NexialBrowserMobHttpClient(StreamManager streamManager, AtomicInteger requestCounter) {
        super(streamManager, requestCounter);
    }

    public void setProxyHandler(ProxyHandler proxyHandler) { this.proxyHandler = proxyHandler; }

    @Override
    public BrowserMobHttpResponse execute(BrowserMobHttpRequest req) {
        try {
            if (proxyHandler.isInvokeInterceptor()) {
                if (requestCaptured) {
                    req.getProxyRequest().destroy();
                } else {
                    List<RequestInterceptor> ri = (List<RequestInterceptor>) getReflectiveField("requestInterceptors");
                    for (RequestInterceptor interceptor : ri) {
                        interceptor.process(req, getHar());
                    }
                    if (req.getMethod().toString().contains("POST")) {
                        requestCaptured = true;
                        if (req.getProxyRequest().getURI() == null) { return null; }

                        HttpRequestBase method = req.getMethod();
                        String url = method.getURI().toString();

                        DefaultHttpClient httpClient = (DefaultHttpClient) getReflectiveField("httpClient");
                        BasicHttpContext ctx = new BasicHttpContext();
                        httpClient.execute(new HttpHost(OPT_PROXY_LOCALHOST), method, ctx);

                        String harPageRef = (String) getReflectiveField("harPageRef");
                        HarEntry myEntry = new HarEntry(harPageRef);
                        myEntry.setRequest(new HarRequest(method.getMethod(),
                                                          url,
                                                          method.getProtocolVersion().getProtocol()));
                        HarPostData data = new HarPostData();
                        data.setText(new String(req.getCopy().toByteArray()));
                        myEntry.getRequest().setPostData(data);
                        super.getHar().getLog().addEntry(myEntry);
                        req.getProxyRequest().destroy();
                    }
                }
            } else {
                requestCaptured = false;
                super.execute(req);
            }
        } catch (Exception e) {
            ConsoleUtils.error("Error occurred while executing " +
                               (req != null ? req.getMethod().getURI() : "unknown URI") +
                               ": " + e.getMessage());
        }

        return null;
    }

    private Object getReflectiveField(String fieldName) throws IllegalAccessException, NoSuchFieldException {
        Field field = this.getClass().getSuperclass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(this);
    }
}
