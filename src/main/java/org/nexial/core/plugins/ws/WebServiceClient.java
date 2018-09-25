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
import javax.validation.constraints.NotNull;

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
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.WebProxy;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.TestStep;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.*;

public class WebServiceClient {
    protected static final SSLConnectionSocketFactory SSL_SF = new NaiveConnectionSocketFactory();
    protected static final String REGEX_URL_HAS_AUTH = "(.+\\ )?(http[s]?)\\:\\/\\/(.+)\\:(.+)\\@(.+)";
    protected ExecutionContext context;
    protected boolean verbose = true;

    public WebServiceClient(ExecutionContext context) { this.context = context; }

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    public Response get(String url, String queryString) throws IOException {
        return invokeRequest(toGetRequest(url, queryString));
    }

    public Response download(String url, String queryString, String saveTo) throws IOException {
        GetRequest request = toGetRequest(url, queryString);
        request.setPayloadLocation(saveTo);
        return invokeRequest(request);
    }

    public Response post(String url, String payload) throws IOException {
        return invokeRequest(toPostRequest(url, payload));
    }

    public Response head(String url) throws IOException { return invokeRequest(toHeadRequest(url)); }

    public Response delete(String url, String queryString) throws IOException {
        return invokeRequest(toDeleteRequest(url, queryString));
    }

    public Response deleteWithPayload(String url, String payload) throws IOException {
        return invokeRequest(toDeleteRequestWithPayload(url, payload));
    }

    public Response patch(String url, String payload) throws IOException {
        return invokeRequest(toPatchRequest(url, payload));
    }

    public Response put(String url, String payload) throws IOException {
        return invokeRequest(toPutRequest(url, payload));
    }

    @NotNull
    public GetRequest toGetRequest(String url, String queryString) {
        GetRequest request = new GetRequest(context);
        request.setUrl(url);
        request.setQueryString(queryString);
        return request;
    }

    @NotNull
    public PostRequest toPostRequest(String url, String payload) {
        PostRequest request = new PostRequest(context);
        request.setUrl(url);
        request.setPayload(payload);
        return request;
    }

    @NotNull
    public HeadRequest toHeadRequest(String url) {
        HeadRequest request = new HeadRequest(context);
        request.setUrl(url);
        return request;
    }

    @NotNull
    public DeleteRequest toDeleteRequest(String url, String queryString) {
        DeleteRequest request = new DeleteRequest(context);
        request.setUrl(url);
        if (StringUtils.isNotEmpty(queryString)) { request.setQueryString(queryString); }
        return request;
    }

    @NotNull
    public DeleteWithPayloadRequest toDeleteRequestWithPayload(String url, String payload) {
        DeleteWithPayloadRequest request = new DeleteWithPayloadRequest(context);
        request.setUrl(url);
        request.setPayload(payload);
        return request;
    }

    @NotNull
    public PatchRequest toPatchRequest(String url, String payload) {
        PatchRequest request = new PatchRequest(context);
        request.setUrl(url);
        request.setPayload(payload);
        return request;
    }

    @NotNull
    public PutRequest toPutRequest(String url, String payload) {
        PutRequest request = new PutRequest(context);
        request.setUrl(url);
        request.setPayload(payload);
        return request;
    }

    public static String hideAuthDetails(RequestLine requestLine) {
        return requestLine.getMethod() + " " + hideAuthDetails(requestLine.getUri()) + " " +
               requestLine.getProtocolVersion();
    }

    public static String hideAuthDetails(String url) {
        return RegexUtils.match(url, REGEX_URL_HAS_AUTH) ?
               RegexUtils.replace(url, REGEX_URL_HAS_AUTH, "$1$2://$5") : url;
    }

    protected Response invokeRequest(Request request) throws IOException {
        StopWatch tickTock = new StopWatch();
        tickTock.start();

        boolean requireProxy = context != null && context.getBooleanData(WS_PROXY_REQUIRED, false);
        HttpHost proxy = requireProxy ? WebProxy.getApacheProxy(context) : null;
        BasicCredentialsProvider credsProvider = requireProxy ? WebProxy.getApacheCredentialProvider(context) : null;

        RequestConfig requestConfig = prepRequestConfig(request, proxy, credsProvider);
        CloseableHttpClient client = prepareHttpClient(request, requestConfig, proxy, credsProvider);
        HttpUriRequest http = request.prepRequest(requestConfig);

        CloseableHttpResponse httpResponse = null;

        try {
            log("Executing request " + hideAuthDetails(http.getRequestLine()));

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

            Response response = gatherResponseData(http, request, httpResponse);

            tickTock.stop();
            response.setElapsedTime(tickTock.getTime());

            return response;
        } finally {
            if (httpResponse != null) { try { httpResponse.close(); } catch (IOException e) { } }
        }
    }

    protected void log(String msg) {
        // ExecutionContext context = ExecutionThread.get();
        if (context != null) {
            TestStep testStep = context.getCurrentTestStep();
            if (testStep != null) { context.getLogger().log(testStep, msg); }
        }

        debug(msg);
    }

    protected void debug(String msg) { if (verbose && StringUtils.isNotBlank(msg)) { ConsoleUtils.log(msg); } }

    protected Response gatherResponseData(HttpUriRequest http, Request request, HttpResponse httpResponse)
        throws IOException {
        StatusLine statusLine = httpResponse.getStatusLine();
        log("Executed request " + hideAuthDetails(http.getRequestLine()) + ": " + statusLine);

        Response response = new Response();
        response.setReturnCode(statusLine.getStatusCode());
        response.setStatusText(statusLine.getReasonPhrase());

        HttpEntity responseEntity = httpResponse.getEntity();
        if (request instanceof GetRequest && StringUtils.isNotBlank(((GetRequest) request).getPayloadLocation())) {

            // check for response code; only 2xx means we are downloading
            if (statusLine.getStatusCode() >= 200 && statusLine.getStatusCode() < 300) {
                String saveTo = ((GetRequest) request).getPayloadLocation();
                log("Saving response payload to " + saveTo);
                response.setContentLength(saveResponsePayload(responseEntity, saveTo));
                response.setPayloadLocation(saveTo);
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

        return response;
    }

    @NotNull
    protected DefaultProxyRoutePlanner resolveRoutePlanner(Request request, HttpHost proxy) {
        return new DefaultProxyRoutePlanner(proxy) {
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
                            throw new HttpException("Unable to decipher URL " + hideAuthDetails(request.getUrl()) +
                                                    ": " + e.getMessage());
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
    }

    protected RequestConfig prepRequestConfig(Request request, HttpHost proxy, BasicCredentialsProvider credsProvider) {
        Builder requestConfigBuilder = RequestConfig.custom()
                                                    .setConnectTimeout(request.connectionTimeout)
                                                    .setSocketTimeout(request.socketTimeout)
                                                    .setConnectionRequestTimeout(request.socketTimeout)
                                                    .setRedirectsEnabled(request.enableRedirects)
                                                    .setExpectContinueEnabled(request.enableExpectContinue)
                                                    .setCircularRedirectsAllowed(request.allowCircularRedirects)
                                                    .setRelativeRedirectsAllowed(request.allowRelativeRedirects)
                                                    .setCookieSpec(CookieSpecs.STANDARD);

        if (proxy != null) { requestConfigBuilder = requestConfigBuilder.setProxy(proxy); }

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
            httpClientBuilder = httpClientBuilder.setDefaultCredentialsProvider(credsProvider)
                                                 .setProxy(proxy)
                                                 .setRoutePlanner(resolveRoutePlanner(request, proxy));
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

    protected boolean isBasicAuth() {
        if (context == null) { return false; }
        return StringUtils.isNotBlank(context.getStringData(WS_BASIC_USER)) &&
               StringUtils.isNotBlank(context.getStringData(WS_BASIC_PWD));
    }

    protected HttpClientBuilder addBasicAuth(HttpClientBuilder httpClientBuilder, Request request)
        throws MalformedURLException {
        if (!isBasicAuth()) { return httpClientBuilder; }
        return httpClientBuilder.setDefaultCredentialsProvider(resolveBasicAuthCredentialProvider(request));
    }

    protected HttpAsyncClientBuilder addBasicAuth(HttpAsyncClientBuilder httpClientBuilder, Request request)
        throws MalformedURLException {
        if (!isBasicAuth()) { return httpClientBuilder; }
        return httpClientBuilder.setDefaultCredentialsProvider(resolveBasicAuthCredentialProvider(request));
    }

    protected CredentialsProvider resolveBasicAuthCredentialProvider(Request request) throws MalformedURLException {
        return resolveCredentialProvider(request, WS_BASIC_USER, WS_BASIC_PWD);
    }

    protected HttpClientContext newBasicEnabledHttpContext(Request request) throws MalformedURLException {
        URL url = new URL(request.getUrl());
        HttpHost target = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());

        BasicAuthCache authCache = new BasicAuthCache();
        authCache.put(target, new BasicScheme());

        // Add AuthCache to the execution execution
        HttpClientContext httpContext = HttpClientContext.create();
        httpContext.setAuthCache(authCache);
        return httpContext;
    }

    protected boolean isDigestAuth() {
        if (context == null) { return false; }
        return StringUtils.isNotBlank(context.getStringData(WS_DIGEST_USER)) &&
               StringUtils.isNotBlank(context.getStringData(WS_DIGEST_PWD)) &&
               StringUtils.isNotBlank(context.getStringData(WS_DIGEST_REALM));
    }

    protected HttpClientBuilder addDigestAuth(HttpClientBuilder httpClientBuilder, Request request)
        throws MalformedURLException {
        if (!isDigestAuth()) { return httpClientBuilder; }
        return httpClientBuilder.setDefaultCredentialsProvider(resolveDigestAuthCredentialProvider(request));
    }

    protected HttpAsyncClientBuilder addDigestAuth(HttpAsyncClientBuilder httpClientBuilder, Request request)
        throws MalformedURLException {
        if (!isDigestAuth()) { return httpClientBuilder; }
        return httpClientBuilder.setDefaultCredentialsProvider(resolveDigestAuthCredentialProvider(request));
    }

    protected CredentialsProvider resolveDigestAuthCredentialProvider(Request request) throws MalformedURLException {
        return resolveCredentialProvider(request, WS_DIGEST_USER, WS_DIGEST_PWD);
    }

    protected CredentialsProvider resolveCredentialProvider(Request request,
                                                            String userVariable,
                                                            String passwordVariable)
        throws MalformedURLException {
        if (context == null) { return null; }

        String digestUser = context.getStringData(userVariable);
        String digestPwd = context.getStringData(passwordVariable);

        URL url = new URL(request.getUrl());

        CredentialsProvider digestCredsProvider = new BasicCredentialsProvider();
        digestCredsProvider.setCredentials(new AuthScope(url.getHost(), url.getPort()),
                                           new UsernamePasswordCredentials(digestUser, digestPwd));

        return digestCredsProvider;
    }

    protected HttpClientContext newDigestEnabledHttpContext(Request request) throws MalformedURLException {
        if (context == null) { return null; }

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
}
