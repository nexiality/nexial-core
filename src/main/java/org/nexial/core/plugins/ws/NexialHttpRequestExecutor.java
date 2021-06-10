package org.nexial.core.plugins.ws;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.protocol.HttpRequestExecutor;

/**
 * override Apache HttpClient's implement to support the retrieval response content as lng as the response status code
 * is 200 or greater. This means that even if the response status code is 205 (RESET CONTENT), HttpClient will still
 * attempt to retrieve response body.
 * <p>
 * This implementation allows Nexial to support non-conforming API implementation (such as those that sends response
 * body with status code 205) and a better feature parity with popular API testing tools like Postman.
 */
public class NexialHttpRequestExecutor extends HttpRequestExecutor {
    @Override
    protected boolean canResponseHaveBody(HttpRequest request, HttpResponse response) {
        return super.canResponseHaveBody(request, response) ||
               response.getStatusLine().getStatusCode() >= HttpStatus.SC_OK;
    }
}
