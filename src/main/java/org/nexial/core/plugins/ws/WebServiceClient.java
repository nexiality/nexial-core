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

package org.nexial.core.plugins.ws;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.http.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.protocol.HttpContext;

import org.nexial.core.ExecutionThread;
import org.nexial.core.WebProxy;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.TestStep;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.*;

class WebServiceClient {
    protected static final SSLConnectionSocketFactory SSL_SF = new NaiveConnectionSocketFactory();
    protected ExecutionContext context;

    public WebServiceClient(ExecutionContext context) { this.context = context; }

    Response get(String url, String queryString) throws IOException {
        GetRequest request = new GetRequest(context);
        request.setUrl(url);
        request.setQueryString(queryString);
        return invokeRequest(request);
    }

    Response download(String url, String queryString, String saveTo) throws IOException {
        GetRequest request = new GetRequest(context);
        request.setUrl(url);
        request.setQueryString(queryString);
        request.setPayloadSaveTo(saveTo);
        return invokeRequest(request);
    }

    Response post(String url, String payload) throws IOException {
        PostRequest request = new PostRequest(context);
        request.setUrl(url);
        request.setPayload(payload);
        return invokeRequest(request);
    }

    Response head(String url) throws IOException {
        HeadRequest request = new HeadRequest(context);
        request.setUrl(url);
        return invokeRequest(request);
    }

    Response delete(String url, String queryString) throws IOException {
        DeleteRequest request = new DeleteRequest(context);
        request.setUrl(url);
        if (StringUtils.isNotEmpty(queryString)) { request.setQueryString(queryString); }
        return invokeRequest(request);
    }

    Response deleteWithPayload(String url, String payload) throws IOException {
        DeleteWithPayloadRequest request = new DeleteWithPayloadRequest(context);
        request.setUrl(url);
        request.setPayload(payload);
        return invokeRequest(request);
    }

    Response patch(String url, String payload) throws IOException {
        PatchRequest request = new PatchRequest(context);
        request.setUrl(url);
        request.setPayload(payload);
        return invokeRequest(request);
    }

    Response put(String url, String payload) throws IOException {
        PutRequest request = new PutRequest(context);
        request.setUrl(url);
        request.setPayload(payload);
        return invokeRequest(request);
    }

    protected void log(String msg) {
        ExecutionContext context = ExecutionThread.get();
        if (context != null) {
            TestStep testStep = context.getCurrentTestStep();
            if (testStep != null) {
                context.getLogger().log(testStep, msg);
                return;
            }
        }

        ConsoleUtils.log(msg);
    }

    protected Response invokeRequest(Request request) throws IOException {
        StopWatch tickTock = new StopWatch();
        tickTock.start();

        boolean requireProxy = context.getBooleanData(WS_PROXY_REQUIRED, false);
        HttpHost proxy = requireProxy ? WebProxy.getApacheProxy(context) : null;
        BasicCredentialsProvider credsProvider =
            requireProxy ? WebProxy.getApacheCredentialProvider(context) : null;

        RequestConfig requestConfig = prepRequestConfig(request, proxy, credsProvider);
        CloseableHttpClient client = prepareHttpClient(request, requestConfig, proxy, credsProvider);
        HttpUriRequest http = request.prepRequest(requestConfig);

        Response response = new Response();
        CloseableHttpResponse httpResponse = null;

        try {
            log("Executing request " + http.getRequestLine());

            boolean digestAuth = isDigestAuth();
            boolean basicAuth = isBasicAuth();
            if (digestAuth || basicAuth) {
                HttpClientContext httpContext = digestAuth ?
                                                newDigestEnabledHttpContext(request) :
                                                newBasicEnabledHttpContext(request);
                httpResponse = client.execute(http, httpContext);
            } else {
                httpResponse = client.execute(http);
            }

            StatusLine statusLine = httpResponse.getStatusLine();
            log("Executed request " + http.getRequestLine() + ": " + statusLine);
            response.setReturnCode(statusLine.getStatusCode());
            response.setStatusText(statusLine.getReasonPhrase());

            HttpEntity responseEntity = httpResponse.getEntity();
            if (request instanceof GetRequest
                && StringUtils.isNotBlank(((GetRequest) request).getPayloadSaveTo())) {

                // check for response code; only 2xx means we are downloading
                if (statusLine.getStatusCode() >= 200 && statusLine.getStatusCode() < 300) {
                    String saveTo = ((GetRequest) request).getPayloadSaveTo();
                    log("Saving response payload to " + saveTo);
                    response.setContentLength(saveResponsePayload(responseEntity, saveTo));
                } else {
                    // status NOT in 2xx means failure
                    log("Unable to download due to " + statusLine);
                    throw new IOException(statusLine + "");
                }
            } else {
                log("Saving response payload as raw bytes to Response object");
                byte[] rawBody = harvestResponsePayload(responseEntity);
                if (rawBody == null) {
                    response.setRawBody(null);
                    response.setContentLength(0);
                } else {
                    long contentLength = Math.max(responseEntity.getContentLength(), rawBody.length);
                    response.setRawBody(rawBody);
                    response.setContentLength(contentLength);
                }
            }

            response.setHeaders(handleResponseHeaders(httpResponse));
        } finally {
            if (httpResponse != null) { try { httpResponse.close(); } catch (IOException e) { } }
        }

        tickTock.stop();
        response.setElapsedTime(tickTock.getTime());

        // log("HttpResponse converted to ws.Response");
        return response;
    }

    protected RequestConfig prepRequestConfig(Request request, HttpHost proxy, BasicCredentialsProvider credsProvider) {
        Builder requestConfigBuilder = RequestConfig.custom()
                                                    .setConnectTimeout(request.connectionTimeout)
                                                    .setSocketTimeout(request.socketTimeout)
                                                    .setConnectionRequestTimeout(request.socketTimeout)
                                                    .setRedirectsEnabled(request.enableRedirects)
                                                    .setExpectContinueEnabled(request.enableExpectContinue)
                                                    .setCircularRedirectsAllowed(request.allowCircularRedirects)
                                                    .setRelativeRedirectsAllowed(request.allowRelativeRedirects);

        if (proxy != null) {
            requestConfigBuilder = requestConfigBuilder.setProxy(proxy);
        }

        return requestConfigBuilder.build();
    }

    protected CloseableHttpClient prepareHttpClient(final Request request,
                                                    RequestConfig requestConfig,
                                                    HttpHost proxy,
                                                    CredentialsProvider credsProvider)
        throws IOException {

        SocketConfig socketConfig = SocketConfig.custom()
                                                .setSoKeepAlive(true)
                                                .setSoReuseAddress(true)
                                                .setSoTimeout(request.socketTimeout)
                                                .setSoLinger(request.socketTimeout).build();

        HttpClientBuilder httpClientBuilder = HttpClients.custom()
                                                         .setSSLSocketFactory(SSL_SF)
                                                         .setDefaultRequestConfig(requestConfig)
                                                         .setDefaultSocketConfig(socketConfig);

        if (proxy != null && credsProvider != null) {
            httpClientBuilder = httpClientBuilder.setDefaultCredentialsProvider(credsProvider);
            httpClientBuilder = httpClientBuilder.setProxy(proxy);

            HttpRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxy) {
                @Override
                public HttpRoute determineRoute(HttpHost host, HttpRequest req, HttpContext execution)
                    throws HttpException {

                    String hostname = host.getHostName();
                    // todo, need more configurable way
                    if (isIntranet(hostname)) {
                        HttpHost target;
                        if (host.getPort() <= 0) {
                            try {
                                URL url = new URL(request.getUrl());
                                target = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());
                            } catch (MalformedURLException e) {
                                throw new HttpException("Unable to decipher URL " + request.getUrl() + ": " +
                                                        e.getMessage());
                            }
                        } else {
                            target = host;
                        }

                        boolean secure = StringUtils.equalsIgnoreCase("https", target.getSchemeName());
                        HttpClientContext clientContext = HttpClientContext.adapt(execution);
                        RequestConfig config = clientContext.getRequestConfig();
                        InetAddress local = config.getLocalAddress();

                        return new HttpRoute(target, local, secure);
                        //return new HttpRoute(host);
                    }

                    return super.determineRoute(host, req, execution);
                }
            };

            httpClientBuilder.setRoutePlanner(routePlanner);
        }

        // add basic auth, if specified
        httpClientBuilder = addBasicAuth(httpClientBuilder, request);

        // add digest auth, if specified
        httpClientBuilder = addDigestAuth(httpClientBuilder, request);

        return httpClientBuilder.build();
    }

    protected boolean isIntranet(String hostname) {
        return NumberUtils.isDigits(StringUtils.substringBefore(hostname, ".")) ||
               !StringUtils.contains(hostname, ".");
    }

    protected HttpClientBuilder addDigestAuth(HttpClientBuilder httpClientBuilder, Request request)
        throws MalformedURLException {
        if (!isDigestAuth()) { return httpClientBuilder; }

        String digestUser = context.getStringData(WS_DIGEST_USER);
        String digestPwd = context.getStringData(WS_DIGEST_PWD);

        URL url = new URL(request.getUrl());

        CredentialsProvider digestCredsProvider = new BasicCredentialsProvider();
        digestCredsProvider.setCredentials(new AuthScope(url.getHost(), url.getPort()),
                                           new UsernamePasswordCredentials(digestUser, digestPwd));

        return httpClientBuilder.setDefaultCredentialsProvider(digestCredsProvider);
    }

    protected HttpClientBuilder addBasicAuth(HttpClientBuilder httpClientBuilder, Request request)
        throws MalformedURLException {
        if (!isBasicAuth()) { return httpClientBuilder; }

        String basicUser = context.getStringData(WS_BASIC_USER);
        String basicPwd = context.getStringData(WS_BASIC_PWD);

        URL url = new URL(request.getUrl());

        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(url.getHost(), url.getPort()),
                                     new UsernamePasswordCredentials(basicUser, basicPwd));

        return httpClientBuilder.setDefaultCredentialsProvider(credsProvider);
    }

    protected HttpClientContext newDigestEnabledHttpContext(Request request) throws MalformedURLException {
        String realm = context.getStringData(WS_DIGEST_REALM);
        String nounce = context.getStringData(WS_DIGEST_NONCE);

        // Generate DIGEST scheme object, initialize it and add it to the local auth cache
        DigestScheme digestAuth = new DigestScheme();
        digestAuth.overrideParamter("realm", realm);
        if (StringUtils.isNotBlank(nounce)) { digestAuth.overrideParamter("nonce", nounce); }
        digestAuth.overrideParamter("qop", "auth");

        URL url = new URL(request.getUrl());
        HttpHost digestTarget = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());

        // Create AuthCache instance
        AuthCache authCache = new BasicAuthCache();
        authCache.put(digestTarget, digestAuth);

        // Add AuthCache to the execution execution
        HttpClientContext httpContext = HttpClientContext.create();
        httpContext.setAuthCache(authCache);
        return httpContext;
    }

    protected byte[] harvestResponsePayload(HttpEntity responseEntity) throws IOException {
        if (responseEntity == null) { return null; }

        //long contentLength = responseEntity.getContentLength();
        //Header contentEncoding = responseEntity.getContentEncoding();
        //String encoding = contentEncoding != null ? contentEncoding.getValue() : null;
        InputStream responseBody = responseEntity.getContent();
        if (responseBody == null) { return null; }
        return IOUtils.toByteArray(responseBody);
    }

    protected long saveResponsePayload(HttpEntity responseEntity, String saveTo) throws IOException {
        InputStream responseBody = responseEntity.getContent();
        if (responseBody == null) { return 0; }

        FileOutputStream fos = null;
        try {
            fos = FileUtils.openOutputStream(new File(saveTo));
            return IOUtils.copyLarge(responseBody, fos);
        } finally {
            if (fos != null) {
                fos.flush();
                fos.close();
            }
        }
    }

    protected Map<String, String> handleResponseHeaders(HttpResponse response) {
        Header[] responseHeaders = response.getAllHeaders();
        Map<String, String> headers = new HashMap<>();
        if (responseHeaders != null) {
            for (Header header : responseHeaders) {
                if (headers.containsKey(header.getName())) {
                    headers.put(header.getName(), headers.get(header.getName()) + "; " + header.getValue());
                } else {
                    headers.put(header.getName(), header.getValue());
                }
            }
        }
        return headers;
    }

    protected boolean isDigestAuth() {
        String digestUser = context.getStringData(WS_DIGEST_USER);
        String digestPwd = context.getStringData(WS_DIGEST_PWD);
        String digestRealm = context.getStringData(WS_DIGEST_REALM);
        return StringUtils.isNotBlank(digestUser) &&
               StringUtils.isNotBlank(digestPwd) &&
               StringUtils.isNotBlank(digestRealm);
    }

    protected boolean isBasicAuth() {
        String basicUser = context.getStringData(WS_BASIC_USER);
        String basicPwd = context.getStringData(WS_BASIC_PWD);
        return StringUtils.isNotBlank(basicUser) && StringUtils.isNotBlank(basicPwd);
    }

    private HttpClientContext newBasicEnabledHttpContext(Request request) throws MalformedURLException {
        URL url = new URL(request.getUrl());
        HttpHost target = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());

        BasicAuthCache authCache = new BasicAuthCache();
        authCache.put(target, new BasicScheme());

        // Add AuthCache to the execution execution
        HttpClientContext httpContext = HttpClientContext.create();
        httpContext.setAuthCache(authCache);
        return httpContext;
    }
}
