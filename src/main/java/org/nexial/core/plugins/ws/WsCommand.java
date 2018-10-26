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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.validation.constraints.NotNull;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.commons.utils.web.URLEncodingUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.OutputFileUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.jsonwebtoken.*;

import static io.jsonwebtoken.SignatureAlgorithm.HS256;
import static io.jsonwebtoken.impl.TextCodec.BASE64URL;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.plugins.ws.WebServiceClient.hideAuthDetails;
import static org.nexial.core.utils.CheckUtils.*;

public class WsCommand extends BaseCommand {
    protected boolean verbose;

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    @Override
    public void init(ExecutionContext context) { super.init(context); }

    @Override
    public String getTarget() { return "ws"; }

    public StepResult download(String url, String queryString, String saveTo) {
        requiresNotBlank(url, "invalid url", url);
        requiresValidVariableName(saveTo);

        WebServiceClient client = new WebServiceClient(context);
        client.setVerbose(verbose);

        // is queryString a file?
        try {
            queryString = OutputFileUtils.resolveContent(queryString, context, compactRequestPayload());
        } catch (IOException e) {
            return StepResult.fail("Unable to read input '" + queryString + "' due to " + e.getMessage());
        }

        logRequest(url, queryString);

        try {
            Response response = client.download(url, queryString, saveTo);
            logResponseForDownload(response, saveTo);
            return StepResult.success("Successfully downloaded '" + hideAuthDetails(url) +
                                      (StringUtils.isBlank(queryString) ? "" : ("?" + queryString)) + "' to " + saveTo);
        } catch (IOException e) {
            return StepResult.fail("FAILED to downloaded from '" + hideAuthDetails(url) +
                                   (StringUtils.isBlank(queryString) ? "" : ("?" + queryString)) +
                                   "': " + e.getMessage());
        }
    }

    public StepResult upload(String url, String body, String fileParams, String var) {
        return requestWithBodyMultipart(url, body, fileParams, var);
    }

    public StepResult get(String url, String queryString, String var) {
        return requestNoBody(url, queryString, var, "get");
    }

    public StepResult post(String url, String body, String var) { return requestWithBody(url, body, var, "post"); }

    public StepResult put(String url, String body, String var) { return requestWithBody(url, body, var, "put"); }

    public StepResult patch(String url, String body, String var) { return requestWithBody(url, body, var, "patch"); }

    public StepResult head(String url, String var) { return requestNoBody(url, null, var, "head"); }

    public StepResult delete(String url, String body, String var) {
        return StringUtils.isNotEmpty(body) ?
               requestWithBody(url, body, var, "delete") :
               requestNoBody(url, "", var, "delete");
    }

    public StepResult assertReturnCode(String var, String returnCode) {
        requires(StringUtils.isNotBlank(var), "invalid variable", var);

        int statusCode = NumberUtils.toInt(returnCode);
        requires(statusCode > 99 && statusCode < 999, "Invalid value for expected return code: '" + returnCode + "'");
        try {
            Response response = resolveResponseObject(var);
            if (response == null) { return StepResult.fail("No response variable found using '" + var + "'"); }
            return assertEqual(returnCode, response.getReturnCode() + "");
        } catch (ClassCastException e) {
            return StepResult.fail("Error: " + e.getMessage());
        }
    }

    public StepResult header(String name, String value) {
        requiresNotBlank(name, "Invalid HTTP Header name", name);

        String key = WS_REQ_HEADER_PREFIX + name;
        if (StringUtils.isBlank(value) || StringUtils.equals(value, context.getNullValueToken())) {
            String previousValue = context.removeData(key);
            ConsoleUtils.log("removed " + name + " as HTTP header; previous value=" + previousValue);
        } else {
            context.setData(key, value);
        }

        return StepResult.success("set HTTP header " + name + "=" + value);
    }

    public StepResult headerByVar(String name, String var) {
        requiresNotBlank(name, "Invalid HTTP Header name", name);
        requiresValidVariableName(var);

        String key = WS_REQ_HEADER_PREFIX + name;
        Object value = context.getObjectData(var);
        if (value == null) {
            String previousValue = context.removeData(key);
            ConsoleUtils.log("removed " + name + " as HTTP header; previous value=" + previousValue);
        } else {
            if (value instanceof String) {
                context.setData(key, (String) value);
            } else {
                context.setData(key, value);
            }
        }

        return StepResult.success("set HTTP header " + name + "=" + value);
    }

    public StepResult saveResponsePayload(String var, String file, String append) {
        requiresValidVariableName(var);
        requiresNotBlank(file, "invalid output file", file);

        boolean appendRequired = BooleanUtils.toBoolean(append);

        File f = new File(file);
        if (f.isDirectory()) { return StepResult.fail("file cannot be a directory: '" + file + "'"); }

        // just in case
        f.getParentFile().mkdirs();

        try {
            Response response = resolveResponseObject(var);
            if (response == null) { return StepResult.fail("No response variable found using '" + var + "'"); }

            byte[] rawContent = response.getRawBody();
            FileUtils.writeByteArrayToFile(f, rawContent, appendRequired);
        } catch (ClassCastException e) {
            return StepResult.fail("Error: " + e.getMessage());
        } catch (Exception e) {
            return StepResult.fail("Unable to write to '" + file + "' due to " + e.getMessage());
        }

        String outputUrl = context.resolveRunModeSpecificUrl(f);
        context.getCurrentTestStep().addNestedScreenCapture(outputUrl, "Follow the link to view content");

        return StepResult.success("Response payload (" + var + ") written to '" + file + "'");
    }

    public StepResult soap(String action, String url, String payload, String var) {
        requires(StringUtils.isNotBlank(action), "invalid action", action);
        header(WS_CONTENT_TYPE, WS_SOAP_CONTENT_TYPE);
        header("SOAPAction", "\"" + action + "\"");
        return post(url, payload, var);
    }

    public StepResult jwtSignHS256(String var, String payload, String key) {
        requiresValidVariableName(var);
        requires(StringUtils.isNotBlank(key), "Invalid key", key);
        requires(StringUtils.isNotBlank(payload), "Invalid payload", payload);

        String token = Jwts.builder().signWith(HS256, key).setPayload(payload).compact();
        context.setData(var, token);
        return StepResult.success();
    }

    public StepResult jwtParse(String var, String token, String key) {
        requiresValidVariableName(var);
        requires(StringUtils.isNotBlank(token), "Invalid token", token);

        if (StringUtils.isBlank(key)) {
            if (StringUtils.countMatches(token, ".") != 2) {
                return StepResult.fail("Invalid JWT token since it does not contain exactly 2 period characters");
            }

            String header = BASE64URL.decodeToString(StringUtils.substringBefore(token, "."));
            ConsoleUtils.log("parsed JWT header: " + header);

            String payload = BASE64URL.decodeToString(StringUtils.substringBetween(token, ".", "."));
            context.setData(var, payload);
            return StepResult.success();
        }

        try {
            Jws<Claims> parsed = Jwts.parser().setSigningKey(key).parseClaimsJws(token);
            if (parsed == null || parsed.getBody() == null || parsed.getBody().size() < 1) {
                return StepResult.fail("Unable to parse JWT token, invalid payload returned");
            } else {
                Claims body = parsed.getBody();
                context.setData(var, GSON_COMPRESSED.toJsonTree(body).toString());
                return StepResult.success();
            }
        } catch (ExpiredJwtException e) {
            Claims badClaims = e.getClaims();
            if (badClaims == null) { return StepResult.fail("Unable to parse JWT token: " + e.getMessage()); }

            ConsoleUtils.error("JWT parsing exception: " + e.getMessage());
            context.setData(var, badClaims.toString());
            return StepResult.success("JWT parsing failed (" + e.getMessage() + ") and ignored.");
        } catch (SignatureException | UnsupportedJwtException | MalformedJwtException | IllegalArgumentException e) {
            return StepResult.fail("Unable to parse JWT token: " + e.getMessage());
        }
    }

    /**
     * request to grant OAuth token from {@code url} based on specified {@code auth}. The parameter {@code auth} would
     * include the following:<ul>
     * <li>client_id: The client or consumer ID provided by the resource server when you register your app with it.</li>
     * <li>client_secret: The client or consumer secret provided by the resource server when you register your app
     * with it.</li>
     * <li>scope: This is an optional parameter. It represents the scope of the access request. The access token that
     * is returned by the server has access to only those services mentioned in the scope.</li>
     * <li>grant_type: This needs to be set to client_credentials representing the client credentials grant.</li>
     * </ul>
     *
     * Each of the above details are to be specified in name=value form, and each pair in separate lines.
     * However in some cases, the value could be expressed as JSON array or JSON object.
     *
     * With all proper parameters properly specified, the target {@code url} would return back, among other things, a
     * one-use, time-bound access_token.  This token can be subsequently used as a header parameter
     * ({@literal Authorization}) to access protected resources.
     */
    public StepResult oauth(String var, String url, String auth) {
        requiresValidVariableName(var);
        requires(StringUtils.startsWithIgnoreCase(url, "http"), "Invalid url", url);
        requiresNotBlank(auth, "Invalid authentication details", auth);

        Map<String, String> inputs = TextUtils.toMap(StringUtils.remove(auth, "\r"), "\n", "=");

        // check that all the required auth inputs are specified
        if (MapUtils.isEmpty(inputs)) { return StepResult.fail("Unable to parse 'auth' into usable details"); }
        StringBuilder errorMessages = new StringBuilder();
        OAUTH_REQUIRED_INPUTS.forEach(inputName -> {
            if (StringUtils.isBlank(inputs.get(inputName))) {
                errorMessages.append("'auth' missing required '").append(inputName).append("' input!").append("\n");
            }
        });
        if (errorMessages.length() > 0) { return StepResult.fail(errorMessages.toString()); }

        // establlish headers
        String clientId = inputs.get(OAUTH_CLIENT_ID);
        String clientSecret = inputs.get(OAUTH_CLIENT_SECRET);
        String basicHeader = "Basic " + new String(Base64.encodeBase64((clientId + ":" + clientSecret).getBytes()));
        ConsoleUtils.log("setting HTTP Header " + AUTHORIZATION + " as " + basicHeader);
        header(AUTHORIZATION, basicHeader);
        header(CONTENT_TYPE, WS_FORM_CONTENT_TYPE);

        // post oauth request
        StringBuilder formData = new StringBuilder();
        inputs.forEach((name, value) -> formData.append(name).append("=").append(value).append("&"));

        String varResponse = var + "_Response";
        StepResult result = post(url, StringUtils.removeEnd(formData.toString(), "&"), varResponse);
        if (result.failed()) { return result; }

        // check response
        Response response = (Response) context.getObjectData(varResponse);
        String failPrefix = "OAuth request failed: ";

        if (response == null) { return StepResult.fail(failPrefix + "no response found"); }
        if (response.getReturnCode() != 200) {
            return StepResult.fail(failPrefix + response.getReturnCode() + " " + response.getStatusText());
        }
        if (response.getContentLength() < 5) {
            return StepResult.fail(failPrefix + "response content length=" + response.getContentLength());
        }

        String responseContentType = response.getHeaders().get(WS_CONTENT_TYPE);
        if (!StringUtils.equals(responseContentType, WS_JSON_CONTENT_TYPE) &&
            !StringUtils.equals(responseContentType, WS_JSON_CONTENT_TYPE2)) {
            return StepResult.fail(failPrefix + "unexpected response content type: " + responseContentType);
        }

        // parse response as JSON
        JsonObject json = GSON_COMPRESSED.fromJson(response.getBody(), JsonObject.class);
        Set<Entry<String, JsonElement>> jsonProps = json.entrySet();

        // save response to var
        Map<String, String> oauthResponse = new HashMap<>();
        jsonProps.forEach(entry -> {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            // accomodate situation where json response contains multi-element array
            if (value.isJsonArray()) {
                JsonArray arrayValue = value.getAsJsonArray();
                if (arrayValue.size() == 1) {
                    oauthResponse.put(key, arrayValue.get(0).toString());
                } else {
                    oauthResponse.put(key, arrayValue.toString());
                }
            } else {
                oauthResponse.put(key, value.getAsString());
            }
        });
        context.setData(var, oauthResponse);

        String tokenType = oauthResponse.get(OAUTH_TOKEN_TYPE);
        if (StringUtils.isBlank(tokenType)) {
            return StepResult.fail(failPrefix + "invalid " + OAUTH_TOKEN_TYPE + ": " + tokenType);
        }

        // bearer support
        if (StringUtils.equalsIgnoreCase(tokenType, OAUTH_TOKEN_TYPE_BEARER) ||
            StringUtils.equalsIgnoreCase(tokenType, OAUTH_TOKEN_TYPE_BEARER2)) {
            return addAccessTokenToHeader(oauthResponse, OAUTH_TOKEN_TYPE_BEARER);
        }

        // basic support
        if (StringUtils.equalsIgnoreCase(tokenType, OAUTH_TOKEN_TYPE_BASIC)) {
            return addAccessTokenToHeader(oauthResponse, OAUTH_TOKEN_TYPE_BASIC);
        }

        // not yet supported
        // if (StringUtils.equalsIgnoreCase(tokenType, OAUTH_TOKEN_TYPE_MAC)) {
        // }

        return StepResult.fail(failPrefix + "unknown/unsupported " + OAUTH_TOKEN_TYPE + "found: " + tokenType);
    }

    protected boolean compactRequestPayload() {
        return context.getBooleanData(WS_REQ_PAYLOAD_COMPACT, DEF_WS_REQ_PAYLOAD_COMPACT);
    }

    protected StepResult addAccessTokenToHeader(Map<String, String> oauthResponse, String authPrefix) {
        String bearerCode = oauthResponse.get(OAUTH_ACCESS_TOKEN);
        if (StringUtils.isBlank(bearerCode)) {
            return StepResult.fail("OAuth request failed: missing " + OAUTH_ACCESS_TOKEN + " in response");
        }

        bearerCode = authPrefix + " " + bearerCode;
        ConsoleUtils.log("setting HTTP header " + AUTHORIZATION + " as " + bearerCode);
        header(AUTHORIZATION, bearerCode);
        return StepResult.success("OAuth exchange completed and access token added to header");
    }

    protected StepResult requestNoBody(String url, String queryString, String var, String method) {
        requiresNotBlank(url, "invalid url", url);
        requiresValidVariableName(var);

        WebServiceClient client = new WebServiceClient(context);
        client.setVerbose(verbose);

        // is queryString a file?
        try {
            queryString = OutputFileUtils.resolveContent(queryString, context, compactRequestPayload());
            queryString = URLEncodingUtils.encodeQueryString(queryString);
        } catch (IOException e) {
            return StepResult.fail("Unable to read input '" + queryString + "' due to " + e.getMessage());
        }

        logRequest(url, queryString);

        try {
            Response response = null;
            if (StringUtils.equals(method, "get")) { response = client.get(url, queryString); }
            if (StringUtils.equals(method, "head")) { response = client.head(url); }
            if (StringUtils.equals(method, "delete")) { response = client.delete(url, queryString); }

            context.setData(var, response);
            logResponse(response, var);
            return StepResult.success("Successfully invoked web service '" + hideAuthDetails(url) + "'");
        } catch (IOException e) {
            return toFailResult(url, e);
        }
    }

    @NotNull
    protected static StepResult toFailResult(String url, IOException e) {
        String error = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        return StepResult.fail("Unable to invoke '" + hideAuthDetails(url) + "': " + error);
    }

    protected StepResult requestWithBody(String url, String body, String var, String method) {
        requiresNotBlank(url, "invalid url", url);
        requiresValidVariableName(var);

        WebServiceClient client = new WebServiceClient(context);
        client.setVerbose(verbose);

        // is body a file?
        try {
            body = OutputFileUtils.resolveContent(body, context, compactRequestPayload());
        } catch (IOException e) {
            return StepResult.fail("Unable to read input '" + body + "' due to " + e.getMessage());
        }

        try {
            logRequestWithBody(url, body);

            Response response = null;
            if (StringUtils.equals(method, "post")) { response = client.post(url, body); }
            if (StringUtils.equals(method, "patch")) { response = client.patch(url, body); }
            if (StringUtils.equals(method, "put")) { response = client.put(url, body); }
            if (StringUtils.equals(method, "delete")) { response = client.deleteWithPayload(url, body); }

            context.setData(var, response);
            logResponse(response, var);
            return StepResult.success("Successfully invoked web service '" + hideAuthDetails(url) + "'");
        } catch (IOException e) {
            return toFailResult(url, e);
        }
    }

    protected StepResult requestWithBodyMultipart(String url, String body, String fileParams, String var) {
        requiresNotBlank(url, "invalid url", url);
        requiresValidVariableName(var);

        WebServiceClient client = new WebServiceClient(context);
        client.setVerbose(verbose);

        // is body a file?
        try {
            body = OutputFileUtils.resolveContent(body, context, compactRequestPayload());
        } catch (IOException e) {
            return StepResult.fail("Unable to read input '" + body + "' due to " + e.getMessage());
        }

        try {
            logRequestWithBody(url, body);

            Response response = client.postMultipart(url, body, fileParams);

            context.setData(var, response);
            logResponse(response, var);
            return StepResult.success("Successfully invoked web service '" + hideAuthDetails(url) + "'");
        } catch (IOException e) {
            return toFailResult(url, e);
        }
    }

    protected Response resolveResponseObject(String var) throws ClassCastException {
        Object response = context.getObjectData(var);
        if (!(response instanceof Response)) {
            throw new ClassCastException("Variable '" + var + "' does not resolve to a valid HTTP response object");
        }
        return (Response) response;
    }

    protected void logRequestWithBody(String url, String body) {
        ConsoleUtils.log("REQUEST  --> '" + hideAuthDetails(url) + "', body length=" + StringUtils.length(body));
    }

    protected void logRequest(String url, String queryString) {
        ConsoleUtils.log("REQUEST  --> '" + hideAuthDetails(url) + "', " +
                         "queryString='" + StringUtils.defaultString(queryString, "<NONE>") + "'");
    }

    protected void logResponse(Response response, String saveTo) {
        if (context.isVerbose()) { log("RESPONSE payload save to '" + saveTo + "' --> \n" + response); }
    }

    protected void logResponseForDownload(Response response, String saveTo) {
        if (context.isVerbose()) {
            log("RESPONSE payload save to '" + saveTo + "' --> \n" + response.getContentLength());
        }
    }
}
