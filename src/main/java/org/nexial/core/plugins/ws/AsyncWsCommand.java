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

import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.web.URLEncodingUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.utils.OutputFileUtils;

import static org.nexial.core.utils.CheckUtils.requiresNotBlank;
import static org.nexial.core.utils.CheckUtils.requiresValidVariableName;

public class AsyncWsCommand extends WsCommand {
    private AsyncWebServiceClient client;

    @Override
    public void init(ExecutionContext context) {
        super.init(context);
        client = new AsyncWebServiceClient(context);
        client.setVerbose(verbose);
    }

    @Override
    public String getTarget() {
        return "ws.async";
    }

    @Override
    public StepResult download(String url, String queryString, String saveTo) {
        requiresNotBlank(url, "invalid url", url);
        requiresValidVariableName(saveTo);

        String request;
        if (StringUtils.isBlank(queryString)) {
            request = url;
        } else {
            // is queryString a file?
            try {
                queryString = OutputFileUtils.resolveContent(queryString, context, compactRequestPayload());
                queryString = URLEncodingUtils.encodeQueryString(queryString);
                request = url + "?" + queryString;
            } catch (IOException e) {
                return StepResult.fail("Unable to read input '" + queryString + "' due to " + e.getMessage());
            }
        }

        logRequest(url, queryString);

        try {
            client.download(url, queryString, saveTo);
            return StepResult.success("Successfully requested '" + request + "' to " + saveTo);
        } catch (IOException e) {
            return StepResult.fail("FAILED to downloaded from '" + request + "': " + e.getMessage());
        }
    }

    @Override
    public StepResult get(String url, String queryString, String output) {
        return super.get(url, queryString, output);
    }

    @Override
    public StepResult post(String url, String body, String output) {
        return super.post(url, body, output);
    }

    @Override
    public StepResult put(String url, String body, String output) {
        return super.put(url, body, output);
    }

    @Override
    public StepResult patch(String url, String body, String output) {
        return super.patch(url, body, output);
    }

    @Override
    public StepResult head(String url, String output) {
        return super.head(url, output);
    }

    @Override
    protected StepResult requestNoBody(String url, String queryString, String output, String method) {
        requiresNotBlank(url, "invalid url", url);
        requiresNotBlank(output, "invalid output", output);
        File outputFile = new File(output);

        // is queryString a file?
        try {
            queryString = OutputFileUtils.resolveContent(queryString, context, compactRequestPayload());
            queryString = URLEncodingUtils.encodeQueryString(queryString);
        } catch (IOException e) {
            return StepResult.fail("Unable to read input '" + queryString + "' due to " + e.getMessage());
        }

        logRequest(url, queryString);

        try {
            if (StringUtils.equals(method, "get")) { client.get(url, queryString, outputFile); }
            if (StringUtils.equals(method, "head")) { client.head(url, outputFile); }
            if (StringUtils.equals(method, "delete")) { client.delete(url, queryString, outputFile); }

            return StepResult.success("Successfully invoked '" + url + "', output will be saved to " + output);
        } catch (IOException e) {
            return toFailResult(url, e);
        }
    }

    @Override
    protected StepResult requestWithBody(String url, String body, String output, String method) {
        requiresNotBlank(url, "invalid url", url);
        requiresNotBlank(output, "invalid output", output);
        File outputFile = new File(output);

        // is body a file?
        try {
            body = OutputFileUtils.resolveContent(body, context, compactRequestPayload());
        } catch (IOException e) {
            return StepResult.fail("Unable to read input '" + body + "' due to " + e.getMessage());
        }

        logRequestWithBody(url, body);

        try {
            if (StringUtils.equals(method, "post")) { client.post(url, body, outputFile); }
            if (StringUtils.equals(method, "patch")) { client.patch(url, body, outputFile); }
            if (StringUtils.equals(method, "put")) { client.put(url, body, outputFile); }
            if (StringUtils.equals(method, "delete")) { client.deleteWithPayload(url, body, outputFile); }

            return StepResult.success("Successfully invoked '" + url + "'");
        } catch (IOException e) {
            return toFailResult(url, e);
        }
    }
}
