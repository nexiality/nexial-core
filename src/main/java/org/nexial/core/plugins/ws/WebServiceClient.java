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

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.logs.ExecutionLogger;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.TestStep;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.OutputFileUtils;
import org.nexial.core.variable.Syspath;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.NL;
import static org.nexial.core.NexialConst.Ws.*;
import static org.nexial.core.SystemVariables.getDefaultBool;

public class WebServiceClient {
    protected static final SSLConnectionSocketFactory SSL_SF = new NaiveConnectionSocketFactory();
    protected static final String REGEX_URL_HAS_AUTH = "(.+\\ )?(http[s]?)\\:\\/\\/(.+)\\:(.+)\\@(.+)";
    protected static final String WS_DISABLE_CONTEXT = "__DISABLE_CONTEXT_AS_CONFIG__";
    protected static final String RETRY_COUNT = "__RETRY_COUNT__";
    protected static final long WAIT_BETWEEN_RETRIES = 10000;

    protected ExecutionContext context;
    protected boolean verbose = true;
    protected Map<String, String> priorityConfigs = new HashMap<>();

    public WebServiceClient(ExecutionContext context) {
        if (context == null) { context = ExecutionThread.get(); }
        this.context = context;
    }

    @NotNull
    public WebServiceClient disableContextConfiguration() {
        priorityConfigs.put(WS_DISABLE_CONTEXT, "true");
        return this;
    }

    /**
     * fluid setter to muzzle additional/unnecessary log incurred during execution
     */
    @NotNull
    public WebServiceClient configureAsQuiet() {
        priorityConfigs.put(WS_LOG_DETAIL, "false");
        priorityConfigs.put(WS_LOG_SUMMARY, "false");
        return this;
    }

    /**
     * support retries per client instance. Retry only in the context of time out or network exception
     */
    @NotNull
    public WebServiceClient enableRetry(int retry) {
        if (retry < 0) { throw new IllegalArgumentException("retry must be a positive number: " + retry); }
        priorityConfigs.put(RETRY_COUNT, "" + retry);
        return this;
    }

    /**
     * fluid setter to add priority configuration that would override those set at the context level or env. level
     */
    @NotNull
    public WebServiceClient setPriorityConfiguration(String name, String value) {
        priorityConfigs.put(name, value);
        return this;
    }

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    @NotNull
    public Response get(String url, String queryString) throws IOException {
        return invokeRequest(new GetRequest(resolveContextForRequest(), url, queryString));
    }

    /**
     * extension method to allow for better internal code-level integration. The other {@link #get(String, String)}
     * method is designed for {@link org.nexial.core.plugins.web.WebCommand}; this one is designed for internal use.
     */
    @NotNull
    public Response get(String url, String queryString, Map<String, Object> headers) throws IOException {
        GetRequest request = new GetRequest(resolveContextForRequest(), url, queryString);
        request.setHeaders(headers);
        return invokeRequest(request);
    }

    @NotNull
    public Response download(String url, String queryString, String saveTo) throws IOException {
        GetRequest request = new GetRequest(resolveContextForRequest(), url, queryString);
        request.setPayloadLocation(saveTo);
        return invokeRequest(request);
    }

    @NotNull
    public Response post(String url, String payload) throws IOException {
        return invokeRequest(new PostRequest(resolveContextForRequest(), url, payload, null));
    }

    /**
     * extension method to allow for better internal code-level integration. The other {@link #post(String, String)}
     * method is designed for {@link org.nexial.core.plugins.web.WebCommand}; this one is designed for internal use.
     */
    @NotNull
    public Response post(String url, String payload, Map<String, Object> headers) throws IOException {
        PostRequest request = new PostRequest(resolveContextForRequest(), url, payload, null);
        request.setHeaders(headers);
        return invokeRequest(request);
    }

    @NotNull
    public Response post(String url, byte[] payload) throws IOException {
        return invokeRequest(new PostRequest(resolveContextForRequest(), url, null, payload));
    }

    @NotNull
    public Response postMultipart(String url, String payload, String fileParams) throws IOException {
        return invokeRequest(toPostMultipartRequest(url, payload, fileParams));
    }

    @NotNull
    public Response head(String url) throws IOException {
        return invokeRequest(new HeadRequest(resolveContextForRequest(), url, null));
    }

    @NotNull
    public Response delete(String url, String queryString) throws IOException {
        return invokeRequest(new DeleteRequest(resolveContextForRequest(), url, queryString));
    }

    @NotNull
    public Response deleteWithPayload(String url, String payload) throws IOException {
        return invokeRequest(new DeleteWithPayloadRequest(resolveContextForRequest(), url, payload, null));
    }

    @NotNull
    public Response deleteWithPayload(String url, byte[] payload) throws IOException {
        return invokeRequest(new DeleteWithPayloadRequest(resolveContextForRequest(), url, null, payload));
    }

    @NotNull
    public Response patch(String url, String payload) throws IOException {
        return invokeRequest(new PatchRequest(resolveContextForRequest(), url, payload, null));
    }

    @NotNull
    public Response patch(String url, byte[] payload) throws IOException {
        return invokeRequest(new PatchRequest(resolveContextForRequest(), url, null, payload));
    }

    @NotNull
    public Response put(String url, String payload) throws IOException {
        return invokeRequest(new PutRequest(resolveContextForRequest(), url, payload, null));
    }

    @NotNull
    public Response put(String url, byte[] payload) throws IOException {
        return invokeRequest(new PutRequest(resolveContextForRequest(), url, null, payload));
    }

    @NotNull
    public PostRequest toPostMultipartRequest(String url, String payload, String fileParams) {
        PostMultipartRequest request = new PostMultipartRequest(resolveContextForRequest());
        request.setUrl(url);
        request.setPayload(payload, StringUtils.split(fileParams, context.getTextDelim()));
        return request;
    }

    @NotNull
    public static String hideAuthDetails(RequestLine requestLine) {
        return requestLine.getMethod() + " " + hideAuthDetails(requestLine.getUri()) + " " +
               requestLine.getProtocolVersion();
    }

    @NotNull
    public static String hideAuthDetails(String url) {
        return RegexUtils.match(url, REGEX_URL_HAS_AUTH) ?
               RegexUtils.replace(url, REGEX_URL_HAS_AUTH, "$1$2://$5") : url;
    }

    @NotNull
    protected Response invokeRequest(Request request) throws IOException {
        StopWatch tickTock = new StopWatch();
        tickTock.start();

        // proxy code not ready for prime time...
        // boolean requireProxy = context != null && context.getBooleanData(WS_PROXY_REQUIRED, false);
        // HttpHost proxy = requireProxy ? WebProxy.getApacheProxy(context) : null;
        // BasicCredentialsProvider credsProvider = requireProxy ? WebProxy.getApacheCredentialProvider(context) : null;
        // RequestConfig requestConfig = prepRequestConfig(request, proxy, credsProvider);
        // CloseableHttpClient client = prepHttpClient(request, requestConfig, proxy, credsProvider);
        RequestConfig requestConfig = prepRequestConfig(request, null, null);
        CloseableHttpClient client = prepHttpClient(request, requestConfig, null, null);
        HttpUriRequest http = request.prepRequest(requestConfig);

        String url = request.getUrl();
        int maxRetry = Math.max(retryCount(), 1);
        int retried = 0;
        long requestStartTime = tickTock.getStartTime();
        logRequest(http, request, requestStartTime);

        try {

            CloseableHttpResponse httpResponse = null;
            while (retried < maxRetry) {
                retried++;
                try {
                    httpResponse = invokeRequest(request, client, http);
                    break;
                } catch (SocketException e) {
                    // oops... shall we try again?
                    log("Error while invoking request " + url + ": " + e.getMessage());
                    if (retried < maxRetry) {
                        log("Retry " + retried + " of " + maxRetry + "...");
                        try { Thread.sleep(WAIT_BETWEEN_RETRIES); } catch (InterruptedException e1) { }
                    } else {
                        // time's up
                        throw e;
                    }
                }
            }

            if (httpResponse == null) {
                throw new IOException("Unable to invoke " + url + " after " + maxRetry + " retry");
            }

            StatusLine statusLine = httpResponse.getStatusLine();
            Response response = gatherResponseData(request, httpResponse, tickTock.getTime());
            try { httpResponse.close(); } catch (IOException e) { }

            tickTock.stop();
            response.setRequestTime(requestStartTime);
            response.setElapsedTime(tickTock.getTime());
            logResponse(http, request, statusLine, response);

            return response;
        } catch (IOException e) {
            logResponse(requestStartTime, http, request, e);
            throw e;
        }
    }

    private CloseableHttpResponse invokeRequest(Request request, CloseableHttpClient client, HttpUriRequest http)
        throws IOException {
        if (isDigestAuth()) { return client.execute(http, newDigestEnabledHttpContext(request)); }
        if (isBasicAuth()) { return client.execute(http, newBasicEnabledHttpContext(request)); }
        return client.execute(http);
    }

    protected Response gatherResponseData(Request request, HttpResponse httpResponse, long ttfb)
        throws IOException {
        Response response = new Response();
        response.setTtfb(ttfb);

        StatusLine statusLine = httpResponse.getStatusLine();
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
            log("Saving response payload as raw bytes");
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

    protected void logRequest(HttpUriRequest http, Request request, long requestTimeMs) {
        if (context == null) { return; }

        String url = hideAuthDetails(http.getURI().toString());
        String content = null;
        int contentLength = 0;
        boolean requestWithBody = request instanceof PostRequest;
        if (requestWithBody) {
            content = ((PostRequest) request).getPayload();
            contentLength = StringUtils.length(content);
            if (contentLength == 0) {
                byte[] contentBytes = ((PostRequest) request).getPayloadBytes();
                contentLength = ArrayUtils.getLength(contentBytes);
            }
        }

        log("Executing request " + hideAuthDetails(http.getRequestLine()));

        if (context.isVerbose()) {
            if (contentLength > 0) { log("Request Body Length: " + contentLength); }
            if (StringUtils.isNotEmpty(content)) {
                log("Request Body (1st kb) -->" + NL + StringUtils.abbreviate(content, 1024));
            }
        }

        TestStep testStep = context.getCurrentTestStep();

        if (shouldLogDetail()) {
            StringBuilder details = new StringBuilder();
            appendLog(details, "Test Step       : ", ExecutionLogger.toHeader(testStep));
            details.append(StringUtils.repeat("-", 80)).append(NL);
            appendLog(details, "Request Time    : ", DateUtility.formatLogDate(requestTimeMs));
            appendLog(details, "Request URL     : ", url);
            appendLog(details, "Request Method  : ", http.getMethod());
            appendLog(details, "Request Headers : ", "{" + TextUtils.toString(request.getHeaders(), ", ", ": ") + "}");
            if (requestWithBody && contentLength > 0) {
                appendLog(details, "Request Body    : ",
                          contentLength + " bytes. " +
                          (StringUtils.isNotEmpty(content) ? NL + content : " (binary content)"));
            }
            details.append(StringUtils.repeat("-", 80)).append(NL);
            writeDetailLog(testStep, details.toString());
        }
    }

    protected void logResponse(long requestStartTime, HttpUriRequest http, Request request, Exception e) {
        if (context == null) { return; }
        if (e == null) { return; }

        String error = ExceptionUtils.getRootCauseMessage(e);
        ConsoleUtils.error(error);

        TestStep testStep = context.getCurrentTestStep();

        if (shouldLogDetail()) {
            StringBuilder details = new StringBuilder();
            appendLog(details, "ERROR           : ", error);
            appendLog(details, "Exception       : ", ExceptionUtils.getStackTrace(e));
            writeDetailLog(testStep, details.toString());
        }

        if (shouldLogSummary()) {
            int requestBodyLength = 0;
            boolean requestWithBody = request instanceof PostRequest;
            if (requestWithBody) {
                requestBodyLength = StringUtils.length(((PostRequest) request).getPayload());
                if (requestBodyLength == 0) { ArrayUtils.getLength(((PostRequest) request).getPayloadBytes()); }
            }

            writeSummaryLog(DateUtility.formatLogDate(requestStartTime),
                            ExecutionLogger.toHeader(context),
                            context.getCurrentScenario(),
                            "Row " + (testStep.getRowIndex() + 1),
                            hideAuthDetails(http.getURI().toString()),
                            http.getMethod(),
                            requestBodyLength,
                            -1,
                            error,
                            -1,
                            -1,
                            -1);
        }
    }

    protected void logResponse(HttpUriRequest http, Request request, StatusLine statusLine, Response response) {
        if (context == null) { return; }

        if (!(response instanceof AsyncResponse)) {
            log("Executed request " + hideAuthDetails(http.getRequestLine()) + ": " + statusLine);
        }

        long payloadLength = response.getContentLength();
        String saveTo = response.getPayloadLocation();
        String payload = response.getBody();

        if (context.isVerbose()) {
            if (payloadLength > 0) { log("Response Body Length: " + payloadLength); }
            if (StringUtils.isNotBlank(saveTo)) {
                log("Response Body saved to " + saveTo);
            } else if (StringUtils.isNotEmpty(payload)) {
                log("Response Body -->" + NL + payload);
            }
        }

        TestStep testStep = context.getCurrentTestStep();

        if (shouldLogDetail()) {
            StringBuilder details = new StringBuilder();
            appendLog(details, "Return Code     : ", response.getReturnCode());
            appendLog(details, "Status Text     : ", response.getStatusText());
            appendLog(details, "TTFB            : ", response.getTtfb());
            appendLog(details, "Elapsed Time    : ", response.getElapsedTime());
            appendLog(details, "Response Headers: ", "{" + TextUtils.toString(response.getHeaders(), ", ", ": ") + "}");
            if (payloadLength > 0) {
                appendLog(details, "Response Body   : ",
                          payloadLength + " bytes. " +
                          (StringUtils.isNotBlank(saveTo) ? "Saved to " + saveTo : NL + payload));
            }
            writeDetailLog(testStep, details.toString());
        }

        if (shouldLogSummary()) {
            int requestBodyLength = 0;
            boolean requestWithBody = request instanceof PostRequest;
            if (requestWithBody) {
                requestBodyLength = StringUtils.length(((PostRequest) request).getPayload());
                if (requestBodyLength == 0) {
                    ArrayUtils.getLength(((PostRequest) request).getPayloadBytes());
                }
            }

            writeSummaryLog(DateUtility.formatLogDate(response.getRequestTime()),
                            ExecutionLogger.toHeader(context),
                            context.getCurrentScenario(),
                            "Row " + (testStep.getRowIndex() + 1),
                            hideAuthDetails(http.getURI().toString()),
                            http.getMethod(),
                            requestBodyLength,
                            response.getReturnCode(),
                            response.getStatusText(),
                            response.getTtfb(),
                            response.getElapsedTime(),
                            payloadLength);
        }
    }

    protected void writeSummaryLog(Object... content) {
        if (context == null) { return; }

        if (ArrayUtils.isEmpty(content)) { return; }

        File log = new File(new Syspath().log("fullpath") + separator + "nexial-ws-" + context.getRunId() + ".log");
        try {
            // for first use, let's add log file header
            String data = (!FileUtil.isFileReadable(log) ?
                           "request-time,script,scenario,row-id,url,method,request-body-length," +
                           "return-code,status-code,ttfb,elapsed-time,response-body-length" + NL :
                           "") +
                          Arrays.stream(content)
                                .reduce((previous, next) -> previous + "," +
                                                            (StringUtils.contains(Objects.toString(next), ",") ?
                                                             "\"" + next + "\"" : next))
                                .orElse("") +
                          "";
            FileUtils.writeStringToFile(log, data + "\n", DEF_CHARSET, true);
        } catch (IOException e) {
            ConsoleUtils.error("Unable to log WS detail to " + log.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    protected void writeDetailLog(TestStep testStep, String content) {
        File log = resolveDetailLogFile(testStep);
        try {
            FileUtils.writeStringToFile(log, content, DEF_CHARSET, true);
        } catch (IOException e) {
            ConsoleUtils.error("Unable to log WS detail to " + log.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    @NotNull
    protected File resolveDetailLogFile(TestStep testStep) {
        return new File(new Syspath().out("fullpath") + separator +
                        OutputFileUtils.generateOutputFilename(testStep, ".ws-detail.log"));
    }

    protected void appendLog(StringBuilder buffer, String name, Object value) {
        buffer.append(name).append(value).append(NL);
    }

    protected void log(String msg) {
        if (context != null && !isContextAsConfigDisabled()) {
            TestStep testStep = context.getCurrentTestStep();
            if (testStep != null) { context.getLogger().log(testStep, msg); }
        }
    }

    protected void debug(String msg) { if (verbose && StringUtils.isNotBlank(msg)) { ConsoleUtils.log(msg); } }

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

    protected CloseableHttpClient prepHttpClient(final Request request,
                                                 RequestConfig requestConfig,
                                                 HttpHost proxy,
                                                 CredentialsProvider credsProvider) throws IOException {

        SocketConfig socketConfig = SocketConfig.custom()
                                                .setSoKeepAlive(request.keepAlive)
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
        return NumberUtils.isDigits(StringUtils.substringBefore(hostname, ".")) || !StringUtils.contains(hostname, ".");
    }

    protected boolean isBasicAuth() { return context != null && StringUtils.isNotBlank(getBasicUsername()); }

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
        return StringUtils.isNotBlank(getConfiguration(WS_DIGEST_USER)) &&
               StringUtils.isNotBlank(getConfiguration(WS_DIGEST_PWD)) &&
               StringUtils.isNotBlank(getConfiguration(WS_DIGEST_REALM));
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

    protected CredentialsProvider resolveCredentialProvider(Request request, String userVar, String passwordVar)
        throws MalformedURLException {
        if (context == null) { return null; }

        String user = getConfiguration(userVar);
        String pwd = getConfiguration(passwordVar);

        URL url = new URL(request.getUrl());

        CredentialsProvider digestCredsProvider = new BasicCredentialsProvider();
        digestCredsProvider.setCredentials(new AuthScope(url.getHost(), url.getPort()),
                                           new UsernamePasswordCredentials(user, pwd));

        return digestCredsProvider;
    }

    protected HttpClientContext newDigestEnabledHttpContext(Request request) throws MalformedURLException {
        if (context == null) { return null; }

        String realm = getConfiguration(WS_DIGEST_REALM);
        String nounce = getConfiguration(WS_DIGEST_NONCE);

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
            // `FileUtils.openOutputStream` will ensure parent directories are available
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

    protected boolean shouldLogDetail() {
        return MapUtils.getBoolean(priorityConfigs, WS_LOG_DETAIL,
                                   context.getBooleanData(WS_LOG_DETAIL, getDefaultBool(WS_LOG_DETAIL)));
    }

    protected boolean shouldLogSummary() {
        return MapUtils.getBoolean(priorityConfigs, WS_LOG_SUMMARY,
                                   context.getBooleanData(WS_LOG_SUMMARY, getDefaultBool(WS_LOG_SUMMARY)));
    }

    protected int retryCount() { return MapUtils.getInteger(priorityConfigs, RETRY_COUNT, 0); }

    protected String getBasicUsername() { return getConfiguration(WS_BASIC_USER); }

    protected String getBasicPassword() { return getConfiguration(WS_BASIC_PWD); }

    protected String getConfiguration(String key) {
        return MapUtils.getString(priorityConfigs, key, context.getStringData(key));
    }

    protected boolean isContextAsConfigDisabled() {
        return MapUtils.getBoolean(priorityConfigs, WS_DISABLE_CONTEXT, false);
    }

    /**
     * for internal use, Nexial should not utilize WebClient (and its dependent Request classes) using ExecutionContext,
     * which contains user-specified settings that might affect Nexial's internal use of web services (such as
     * downloading webdriver).
     */
    @Nullable
    protected ExecutionContext resolveContextForRequest() { return isContextAsConfigDisabled() ? null : context; }
}
