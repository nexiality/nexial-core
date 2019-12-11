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
 */

package org.nexial.core.plugins.ws;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.nexial.core.ShutdownAdvisor;
import org.nexial.core.WebProxy;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.ForcefulTerminate;
import org.nexial.core.utils.ConsoleUtils;

import com.google.gson.JsonObject;

import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static org.nexial.core.NexialConst.GSON;
import static org.nexial.core.NexialConst.Ws.WS_ASYNC_SHUTDOWN_TIMEOUT;
import static org.nexial.core.NexialConst.Ws.WS_PROXY_REQUIRED;
import static org.nexial.core.SystemVariables.getDefaultInt;
import static org.nexial.core.plugins.ws.NaiveConnectionSocketFactory.I_TRUST_EVERYONE;
import static org.nexial.core.plugins.ws.NaiveConnectionSocketFactory.NOOP_HOST_VERIFIER;

public class AsyncWebServiceClient extends WebServiceClient implements ForcefulTerminate {
    private static final ExecutorService ASYNC_EXEC_SERVICE = Executors.newWorkStealingPool(5);
    private static final Map<Integer, FutureCallback<HttpResponse>> INFLIGHTS = new ConcurrentHashMap<>();
    private long shutdownTimeout = -1;

    public interface ResponseSink<T> {
        void receive(T response, String error);
    }

    public AsyncWebServiceClient(ExecutionContext context) {
        super(context);
        // delay shutdown event
        if (context != null) {
            shutdownTimeout = context.getIntData(WS_ASYNC_SHUTDOWN_TIMEOUT, getDefaultInt(WS_ASYNC_SHUTDOWN_TIMEOUT));
        }
        ShutdownAdvisor.addAdvisor(this);
    }

    public void get(String url, String queryString, File output) throws IOException {
        invokeRequest(new GetRequest(context, url, queryString), output);
    }

    public void head(String url, File output) throws IOException {
        invokeRequest(new HeadRequest(context, url, null), output);
    }

    public void post(String url, String payload, File output) throws IOException {
        invokeRequest(new PostRequest(context, url, payload, null), output);
    }

    public void post(String url, byte[] payload, File output) throws IOException {
        invokeRequest(new PostRequest(context, url, null, payload), output);
    }

    public void delete(String url, String queryString, File output) throws IOException {
        invokeRequest(new DeleteRequest(context, url, queryString), output);
    }

    public void deleteWithPayload(String url, String payload, File output) throws IOException {
        invokeRequest(new DeleteWithPayloadRequest(context, url, payload, null), output);
    }

    public void deleteWithPayload(String url, byte[] payload, File output) throws IOException {
        invokeRequest(new DeleteWithPayloadRequest(context, url, null, payload), output);
    }

    public void patch(String url, String payload, File output) throws IOException {
        invokeRequest(new PatchRequest(context, url, payload, null), output);
    }

    public void patch(String url, byte[] payload, File output) throws IOException {
        invokeRequest(new PatchRequest(context, url, null, payload), output);
    }

    public void put(String url, String payload, File output) throws IOException {
        invokeRequest(new PutRequest(context, url, payload, null), output);
    }

    public void put(String url, byte[] payload, File output) throws IOException {
        invokeRequest(new PutRequest(context, url, null, payload), output);
    }

    @Override
    public Response download(String url, String queryString, String saveTo) throws IOException {
        GetRequest request = new GetRequest(context, url, queryString);
        request.setPayloadLocation(saveTo);
        invokeRequest(request, new File(saveTo + ".json"));
        return null;
    }

    @Override
    public boolean mustForcefullyTerminate() { return !ASYNC_EXEC_SERVICE.isTerminated(); }

    @Override
    public void forcefulTerminate() {
        if (shutdownTimeout > 0) {
            // while system variable is captured in ms, here we are dealing in seconds (easier for display)
            long timeout = shutdownTimeout / 1000;
            long sleep = 1;
            while (!INFLIGHTS.isEmpty() && timeout > 0) {
                try {
                    ConsoleUtils.log("Waiting for " + INFLIGHTS.size() + " inflight requests " +
                                     INFLIGHTS.keySet() + " to complete. Time out at " + timeout + " seconds...");
                    Thread.sleep(sleep * 1000);
                    timeout -= sleep;
                } catch (InterruptedException e) {
                }
            }
        }

        ASYNC_EXEC_SERVICE.shutdown();
    }

    public void invokeRequest(@NotNull Request request, @NotNull File output) throws IOException {
        long startTime = System.currentTimeMillis();

        invokeRequestAsync(request, (response, error) -> {
            if (output != null) {
                JsonObject content = new JsonObject();
                content.addProperty("startTime", startTime);
                content.add("request", GSON.toJsonTree(request));
                if (response != null) { content.add("response", GSON.toJsonTree(response)); }
                if (StringUtils.isNotEmpty(error)) { content.addProperty("error", error); }

                try {
                    FileUtils.forceMkdirParent(output);
                } catch (IOException e) {
                    ConsoleUtils.error("Unable to create directory for " + output + ": " + e.getMessage());
                }

                try {
                    FileUtils.writeStringToFile(output, content.toString(), DEF_FILE_ENCODING);
                } catch (IOException e) {
                    ConsoleUtils.error("Unable to write to request and response to " + output + ": " + e.getMessage());
                }
            }
        });
    }

    /** invoke HTTP request asynchronously, with option to capture response (via {@link ResponseSink}). */
    public void invokeRequestAsync(@NotNull Request request, ResponseSink<Response> sink) throws IOException {
        if (ASYNC_EXEC_SERVICE.isTerminated() || ASYNC_EXEC_SERVICE.isShutdown()) {
            throw new IOException("Unable to invoke request asynchronously because ws client has been terminated");
        }

        StopWatch tickTock = new StopWatch();
        tickTock.start();

        boolean requireProxy = context != null && context.getBooleanData(WS_PROXY_REQUIRED, false);
        HttpHost proxy = requireProxy ? WebProxy.getApacheProxy(context) : null;
        BasicCredentialsProvider credsProvider = requireProxy ? WebProxy.getApacheCredentialProvider(context) : null;

        RequestConfig requestConfig = prepRequestConfig(request, proxy, credsProvider);

        HttpAsyncClientBuilder httpClientBuilder = HttpAsyncClients.custom()
                                                                   .setSSLHostnameVerifier(NOOP_HOST_VERIFIER)
                                                                   .setSSLContext(I_TRUST_EVERYONE)
                                                                   .setDefaultRequestConfig(requestConfig)
                                                                   .setRedirectStrategy(LaxRedirectStrategy.INSTANCE);
        if (proxy != null && credsProvider != null) {
            httpClientBuilder.setDefaultCredentialsProvider(credsProvider)
                             .setProxy(proxy)
                             .setRoutePlanner(resolveRoutePlanner(request, proxy));
        }

        // add basic auth, if specified
        httpClientBuilder = addBasicAuth(httpClientBuilder, request);

        // add digest auth, if specified
        httpClientBuilder = addDigestAuth(httpClientBuilder, request);

        CloseableHttpAsyncClient client = httpClientBuilder.build();

        HttpUriRequest http = request.prepRequest(requestConfig);

        CountDownLatch latch = new CountDownLatch(1);
        int callbackId = RandomUtils.nextInt();

        // either collect all data (if sink is available) or collect nothing
        FutureCallback<HttpResponse> collectAllResponseData = new FutureCallback<HttpResponse>() {
            private final String safeUrl = hideAuthDetails(request.getUrl());
            private int id = callbackId;

            @Override
            public void completed(HttpResponse httpResponse) {
                long ttfb = tickTock.getTime();
                debug(safeUrl + ": Response received in " + ttfb + "ms");
                if (sink != null) {
                    try {
                        Response response = gatherResponseData(request, httpResponse, ttfb);
                        tickTock.stop();
                        response.setRequestTime(tickTock.getStartTime());
                        response.setElapsedTime(tickTock.getTime());
                        AsyncResponse asyncResponse = AsyncResponse.toAsyncResponse(response);
                        response = null;

                        ConsoleUtils.log("[ASYNC WS COMPLETED][" + id + "] " + safeUrl + ": " +
                                         asyncResponse.getContentLength() + " bytes, " +
                                         asyncResponse.getElapsedTime() + "ms");
                        logResponse(http, request, httpResponse.getStatusLine(), asyncResponse);
                        sink.receive(asyncResponse, null);
                    } catch (IOException e) {
                        ConsoleUtils.error("Error occurred while handling async HTTP response: " + e.getMessage());
                        sink.receive(null, e.getMessage());
                    }
                } else {
                    tickTock.stop();
                }

                latch.countDown();
                INFLIGHTS.remove(id);
            }

            @Override
            public void failed(Exception e) {
                tickTock.stop();
                ConsoleUtils.error("[ASYNC WS ERROR][" + id + "] " + safeUrl + ": " + e.getMessage());
                if (sink != null) { sink.receive(null, e.getMessage()); }
                latch.countDown();
                INFLIGHTS.remove(id);
            }

            @Override
            public void cancelled() {
                tickTock.stop();
                ConsoleUtils.error("[ASYNC WS CANCELLED][" + id + "] " + safeUrl);
                if (sink != null) { sink.receive(null, "CANCELLED"); }
                latch.countDown();
                INFLIGHTS.remove(id);
            }
        };

        logRequest(http, request, tickTock.getStartTime());

        boolean digestAuth = isDigestAuth();
        boolean basicAuth = isBasicAuth();

        ASYNC_EXEC_SERVICE.submit(() -> {
            client.start();

            if (context != null) { INFLIGHTS.put(callbackId, collectAllResponseData); }

            try {
                if (digestAuth || basicAuth) {
                    HttpClientContext httpContext = digestAuth ?
                                                    newDigestEnabledHttpContext(request) :
                                                    newBasicEnabledHttpContext(request);
                    client.execute(http, httpContext, collectAllResponseData);
                } else {
                    client.execute(http, collectAllResponseData);
                }

                latch.await();
            } catch (InterruptedException | MalformedURLException e) {
                ConsoleUtils.error("Error while invoking HTTP async: " + e.getMessage());
            } finally {
                try {
                    client.close();
                } catch (IOException e) {
                    ConsoleUtils.error("Unable to cleanly close HTTP client: " + e.getMessage());
                }
            }
        });
    }

    @Override
    protected Response invokeRequest(@NotNull Request request) throws IOException {
        invokeRequestAsync(request, null);
        return null;
    }
}