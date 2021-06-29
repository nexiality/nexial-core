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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.jsonwebtoken.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONObject;
import org.nexial.commons.utils.TextUtils;
import org.nexial.commons.utils.web.URLEncodingUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.OutputResolver;

import static io.jsonwebtoken.SignatureAlgorithm.HS256;
import static io.jsonwebtoken.impl.TextCodec.BASE64URL;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Ws.*;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.plugins.ws.WebServiceClient.hideAuthDetails;
import static org.nexial.core.utils.CheckUtils.requires;
import static org.nexial.core.utils.CheckUtils.requiresNotBlank;

public class WsCommand extends BaseCommand {
    protected boolean verbose;

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    @Override
    public void init(ExecutionContext context) { super.init(context); }

    @Override
    public String getTarget() { return "ws"; }

    protected Map<String, Map<String, String>> oauthProviderDetails;

    public Map<String, Map<String, String>> getOauthProviderDetails() {
        return oauthProviderDetails;
    }

    public void setOauthProviderDetails(
            Map<String, Map<String, String>> oauthProviderDetails) {
        this.oauthProviderDetails = oauthProviderDetails;
    }

    public StepResult download(String url, String queryString, String saveTo) {
        requiresNotBlank(url, "invalid url", url);
        requiresValidAndNotReadOnlyVariableName(saveTo);

        WebServiceClient client = new WebServiceClient(context);
        client.setVerbose(context.isVerbose());

        try {
            if (StringUtils.isNotBlank(queryString)) {
                @NotNull OutputResolver outputResolver = newOutputResolver(queryString, false);
                queryString = outputResolver.getContent();
            }
            client.download(url, queryString, saveTo);
            return StepResult.success("Successfully downloaded '" + hideAuthDetails(url) +
                                      (StringUtils.isBlank(queryString) ? "" : ("?" + queryString)) + "' to " + saveTo);
        } catch (Throwable e) {
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

    public StepResult post(String url, String body, String var)  { return requestWithBody(url, body, var, "post"); }

    public StepResult put(String url, String body, String var)   { return requestWithBody(url, body, var, "put"); }

    public StepResult patch(String url, String body, String var) { return requestWithBody(url, body, var, "patch"); }

    public StepResult head(String url, String var)               { return requestNoBody(url, null, var, "head"); }

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

            if (statusCode == response.getReturnCode()) {
                return StepResult.success("EXPECTED return code found");
            } else {
                return StepResult.fail("return code (" + response.getReturnCode() + ") DID NOT match expected (" +
                                       returnCode + ")");
            }
        } catch (ClassCastException e) {
            return StepResult.fail("Error: " + e.getMessage());
        }
    }

    public StepResult header(String name, String value) {
        requiresNotBlank(name, "Invalid HTTP Header name", name);

        String key = WS_REQ_HEADER_PREFIX + name;
        if (context.isNullOrEmptyOrBlankValue(value)) {
            String previousValue = context.removeData(key);
            ConsoleUtils.log("removed " + name + " as HTTP header; previous value=" + previousValue);
        } else {
            context.setData(key, value);
        }

        return StepResult.success("set HTTP header " + name + "=" + value);
    }

    public StepResult headerByVar(String name, String var) {
        requiresNotBlank(name, "Invalid HTTP Header name", name);
        requiresValidAndNotReadOnlyVariableName(var);

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
        requiresValidAndNotReadOnlyVariableName(var);
        requiresNotBlank(file, "invalid output file", file);

        boolean appendRequired = CheckUtils.toBoolean(append);

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

        context.getCurrentTestStep().addNestedScreenCapture(f.getAbsolutePath(), "Follow the link to view content");

        return StepResult.success("Response payload (" + var + ") written to '" + file + "'");
    }

    public StepResult soap(String action, String url, String payload, String var) {
        requires(StringUtils.isNotBlank(action), "invalid action", action);
        header(WS_CONTENT_TYPE, WS_SOAP_CONTENT_TYPE);
        header("SOAPAction", TextUtils.wrapIfMissing(action, "\"", "\""));
        return post(url, payload, var);
    }

    public StepResult jwtSignHS256(String var, String payload, String key) {
        requiresValidAndNotReadOnlyVariableName(var);
        requires(StringUtils.isNotBlank(key), "Invalid key", key);
        requires(StringUtils.isNotBlank(payload), "Invalid payload", payload);

        String token = Jwts.builder().signWith(HS256, key).setPayload(payload).compact();
        updateDataVariable(var, token);
        return StepResult.success();
    }

    public StepResult jwtParse(String var, String token, String key) {
        requiresValidAndNotReadOnlyVariableName(var);
        requires(StringUtils.isNotBlank(token), "Invalid token", token);

        if (StringUtils.isBlank(key)) {
            if (StringUtils.countMatches(token, ".") != 2) {
                return StepResult.fail("Invalid JWT token since it does not contain exactly 2 period characters");
            }

            String header = BASE64URL.decodeToString(StringUtils.substringBefore(token, "."));
            ConsoleUtils.log("parsed JWT header: " + header);

            String payload = BASE64URL.decodeToString(StringUtils.substringBetween(token, ".", "."));
            updateDataVariable(var, payload);
            return StepResult.success();
        }

        try {
            Jws<Claims> parsed = Jwts.parser().setSigningKey(key).parseClaimsJws(token);
            if (parsed == null || parsed.getBody() == null || parsed.getBody().size() < 1) {
                return StepResult.fail("Unable to parse JWT token, invalid payload returned");
            } else {
                Claims body = parsed.getBody();
                updateDataVariable(var, GSON_COMPRESSED.toJsonTree(body).toString());
                return StepResult.success();
            }
        } catch (ExpiredJwtException e) {
            Claims badClaims = e.getClaims();
            if (badClaims == null) { return StepResult.fail("Unable to parse JWT token: " + e.getMessage()); }

            ConsoleUtils.error("JWT parsing exception: " + e.getMessage());
            updateDataVariable(var, badClaims.toString());
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
     * <p>
     * Each of the above details are to be specified in name=value form, and each pair in separate lines.
     * However in some cases, the value could be expressed as JSON array or JSON object.
     * <p>
     * With all proper parameters properly specified, the target {@code url} would return back, among other things, a
     * one-use, time-bound access_token.  This token can be subsequently used as a header parameter
     * ({@literal Authorization}) to access protected resources.
     */
    public StepResult oauth(String var, String url, String auth) {
        requiresValidAndNotReadOnlyVariableName(var);
        requires(StringUtils.startsWithIgnoreCase(url, "http"), "Invalid url", url);
        requiresNotBlank(auth, "Invalid authentication details", auth);

        Map<String, String> inputs = TextUtils.toMap(StringUtils.remove(auth, "\r"), "\n", "=");

        // check that all the required auth inputs are specified
        if (MapUtils.isEmpty(inputs)) { return StepResult.fail("Unable to parse 'auth' into usable details"); }
        StringBuilder errorMessages = new StringBuilder();
        OAUTH_REQUIRED_INPUTS.forEach(inputName -> {
            if (StringUtils.isBlank(inputs.get(inputName))) {
                errorMessages.append("'auth' missing required '").append(inputName).append("' input!").append(NL);
            }
        });
        if (errorMessages.length() > 0) { return StepResult.fail(errorMessages.toString()); }

        // establish headers
        if (StringUtils.isNotBlank(inputs.get(OAUTH_CLIENT_SECRET))) {
            String clientId = inputs.get(OAUTH_CLIENT_ID);
            String clientSecret = inputs.get(OAUTH_CLIENT_SECRET);
            String basicHeader = "Basic " + new String(Base64.encodeBase64((clientId + ":" + clientSecret).getBytes()));
            ConsoleUtils.log("setting HTTP Header " + AUTHORIZATION + " as " + basicHeader);
            header(AUTHORIZATION, basicHeader);
        }
        header(CONTENT_TYPE, WS_FORM_CONTENT_TYPE);

        // post oauth request
        String formData = inputs.keySet().stream()
                                .map(name -> name + "=" + URLEncodingUtils.encodeParamValue(inputs.get(name)))
                                .collect(Collectors.joining("&"));

        String varResponse = var + "_Response";
        StepResult result = post(url, formData, varResponse);
        if (result.failed()) { return result; }

        // check response
        Response response = (Response) context.getObjectData(varResponse);
        String failPrefix = "OAuth request failed: ";

        if (response == null) { return StepResult.fail(failPrefix + "no response found"); }
        if (response.getReturnCode() != 200) {
            ConsoleUtils.log("ERROR:\n" +
                             response.getReturnCode() + " " + response.getStatusText() + "\n" + response.getBody());
            return StepResult.fail(failPrefix + response.getReturnCode() + " " + response.getStatusText());
        }
        if (response.getContentLength() < 5) {
            return StepResult.fail(failPrefix + "response content length=" + response.getContentLength());
        }

        String responseContentType = response.getHeaders().get(WS_CONTENT_TYPE);
        if (!StringUtils.startsWith(responseContentType, WS_JSON_CONTENT_TYPE)) {
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
            // accommodate situation where json response contains multi-element array
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
        // some proxy generator or oauth app generates "BearerToken" instead of "Bearer". However we must use
        // "Bearer" in the header for subsequent WS request
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

    public StepResult oauthProfile(String var, String profile) {
        requiresValidAndNotReadOnlyVariableName(var);
        requiresNotBlank(profile, "Invalid profile", profile);

        ExecutionContext context = ExecutionThread.get();
        Map<String, String> config = context.getDataByPrefix(profile + ".");
        if (MapUtils.isEmpty(config)) {
            context.logCurrentStep("No OAuth2 configuration found for '" + profile + "'; Unable to connect.");
            return null;
        }

        String type = config.get("type");
        if (!type.equals(OAUTH_CUSTOM_TYPE)) {
            Map<String, String> vendorDetails = oauthProviderDetails.get(type);

            if (vendorDetails == null || vendorDetails.isEmpty()) {
                return StepResult.fail(type + " is not a valid OAuth provider type.");
            }
            config.putAll(vendorDetails);
        }

        Map<String, String> responses;
        try {
            responses = getOAuthResponse(config);
        } catch (Exception e) {
            return StepResult.fail("An error occurred. Error message is " + e.getMessage());
        }
        context.setData(var, responses);
        return StepResult.success("OAuth2 responses are:- " + responses);
    }

    private Map<String, String> getOAuthResponse(Map<String, String> config) throws IOException {
        String oAuthUrl = config.get("url");
        if (oAuthUrl.contains(OAUTH_URL_PALCEHOLDER)) {
            oAuthUrl = oAuthUrl.replace(OAUTH_URL_PALCEHOLDER, config.get("tenant_id"));
        }

        if (!config.get("type").equals(OAUTH_CUSTOM_TYPE)) {
            config.put(OAUTH_GRANT_TYPE, config.get("grantType"));
        }

        String basicAuthUsernameKey = config.get(OAUTH_BASIC_AUTH_USERNAME_KEY);
        String basicAuthPasswordKey = config.get(OAUTH_BASIC_AUTH_PASSWORD_KEY);

        String basicAuthUsername = config.get(basicAuthUsernameKey);
        String basicAuthPassword = config.get(basicAuthPasswordKey);

        String[] keys = {"url", "type", OAUTH_BASIC_AUTH_USERNAME_KEY, OAUTH_BASIC_AUTH_PASSWORD_KEY,
                         basicAuthUsernameKey, basicAuthPasswordKey};
        Arrays.stream(keys).forEach(config::remove);

        StringBuilder postData = new StringBuilder();
        for (Map.Entry<String, String> param : config.entrySet()) {
            if (postData.length() != 0) {
                postData.append('&');
            }
            postData.append(URLEncoder.encode(param.getKey(), DEF_FILE_ENCODING));
            postData.append('=');
            postData.append(URLEncoder.encode(String.valueOf(param.getValue()), DEF_FILE_ENCODING));
        }
        byte[] postDataBytes = postData.toString().getBytes(DEF_FILE_ENCODING);

        URL url = new URL(oAuthUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoOutput(true);
        con.setUseCaches(false);
        con.setRequestMethod("POST");

        if (StringUtils.isNotEmpty(basicAuthUsername) && StringUtils.isNotEmpty(basicAuthPassword)) {
            String encoding = java.util.Base64.getEncoder()
                                              .encodeToString((basicAuthUsername + ":" + basicAuthPassword)
                                                                      .getBytes("UTF-8"));
            con.setRequestProperty("Authorization",
                                   StringUtils.join(OAUTH_TOKEN_TYPE_BASIC, StringUtils.SPACE,  encoding));
        }
        con.getOutputStream().write(postDataBytes);

        BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder buffer = new StringBuilder();
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            buffer.append(line);
        }

        Map<String, String> oauthResponse = new HashMap<>();
        JSONObject json = new JSONObject(buffer.toString());
        for (String key : json.keySet()) {
            oauthResponse.put(key, String.valueOf(json.get(key)));
        }
        return oauthResponse;
    }

    /**
     * download content of {@code @url}, assuming the content is of text nature.
     */
    public static String resolveWebContent(String url) throws IOException {
        if (StringUtils.isBlank(url)) { throw new IOException("Unable to retrieve content from URL [" + url + "]"); }
        WebServiceClient wsClient = new WebServiceClient(null);
        Response response = wsClient.get(url, null);
        int returnCode = response.getReturnCode();
        if (returnCode <= 199 || returnCode >= 300) {
            // sleep and try again...
            try { Thread.sleep(5000); } catch (InterruptedException e) { }
            response   = wsClient.get(url, null);
            returnCode = response.getReturnCode();
            if (returnCode <= 199 || returnCode >= 300) {
                // give up...
                throw new IOException("Unable to retrieve content from '" + url + "': " + response.getStatusText());
            }
        }
        return response.getBody();
    }

    /**
     * download content of {@code @url}, assuming the content can be text or binary in nature
     */
    public static byte[] resolveWebContentBytes(String url) throws IOException {
        if (StringUtils.isBlank(url)) { throw new IOException("Unable to retrieve content from URL [" + url + "]"); }

        WebServiceClient wsClient = new WebServiceClient(null);
        Response response = wsClient.get(url, null);
        int returnCode = response.getReturnCode();
        if (returnCode <= 199 || returnCode >= 300) {
            // sleep and try again...
            try { Thread.sleep(5000); } catch (InterruptedException e) { }
            response   = wsClient.get(url, null);
            returnCode = response.getReturnCode();
            if (returnCode <= 199 || returnCode >= 300) {
                // give up
                throw new IOException("Unable to retrieve content from '" + url + "': " + response.getStatusText());
            }
        }
        return response.getRawBody();
    }

    /**
     * save the content of {@code url} to {@code target} -- essentially a download op.
     */
    public static File saveWebContent(String url, File target) throws IOException {
        WebServiceClient wsClient = new WebServiceClient(null);
        wsClient.download(url, null, target.getAbsolutePath());
        return target;
    }

    protected boolean compactRequestPayload() {
        return context.getBooleanData(WS_REQ_PAYLOAD_COMPACT, getDefaultBool(WS_REQ_PAYLOAD_COMPACT));
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
        requiresValidAndNotReadOnlyVariableName(var);

        // clear out any existing state of `var`
        context.removeData(var);

        WebServiceClient client = new WebServiceClient(context);
        client.setVerbose(context.isVerbose());

        try {
            if (StringUtils.isNotBlank(queryString)) {
                @NotNull OutputResolver outputResolver = newOutputResolver(queryString, false);
                queryString = URLEncodingUtils.encodeQueryString(outputResolver.getContent());
            }

            Response response = null;
            if (StringUtils.equals(method, "get")) { response = client.get(url, queryString); }
            if (StringUtils.equals(method, "head")) { response = client.head(url); }
            if (StringUtils.equals(method, "delete")) { response = client.delete(url, queryString); }
            context.setData(var, response);

            return StepResult.success("Successfully invoked web service '" + hideAuthDetails(url) + "'");
        } catch (Throwable e) {
            return toFailResult(url, e);
        } finally {
            addDetailLogLink(client);
        }
    }

    protected StepResult requestWithBody(String url, String body, String var, String method) {
        requiresNotBlank(url, "invalid url", url);
        requiresValidAndNotReadOnlyVariableName(var);

        // clear out any existing state of `var`
        context.removeData(var);

        WebServiceClient client = new WebServiceClient(context);
        client.setVerbose(context.isVerbose());

        try {
            OutputResolver outputResolver = newOutputResolver(body);
            Response response = null;
            if (outputResolver.getAsBinary()) {
                byte[] payloadBytes = outputResolver.getBytes();
                if (StringUtils.equals(method, "post")) { response = client.post(url, payloadBytes); }
                if (StringUtils.equals(method, "patch")) { response = client.patch(url, payloadBytes); }
                if (StringUtils.equals(method, "put")) { response = client.put(url, payloadBytes); }
                if (StringUtils.equals(method, "delete")) { response = client.deleteWithPayload(url, payloadBytes); }
            } else {
                String payload = outputResolver.getContent();
                if (StringUtils.equals(method, "post")) { response = client.post(url, payload); }
                if (StringUtils.equals(method, "patch")) { response = client.patch(url, payload); }
                if (StringUtils.equals(method, "put")) { response = client.put(url, payload); }
                if (StringUtils.equals(method, "delete")) { response = client.deleteWithPayload(url, payload); }
            }

            context.setData(var, response);

            return StepResult.success("Successfully invoked web service '" + hideAuthDetails(url) + "'");
        } catch (Throwable e) {
            return toFailResult(url, e);
        } finally {
            addDetailLogLink(client);
        }
    }

    protected StepResult requestWithBodyMultipart(String url, String body, String fileParams, String var) {
        requiresNotBlank(url, "invalid url", url);
        requiresValidAndNotReadOnlyVariableName(var);

        WebServiceClient client = new WebServiceClient(context);
        client.setVerbose(context.isVerbose());

        @NotNull OutputResolver outputResolver = newOutputResolver(body, false);

        try {
            String content = outputResolver.getContent();
            Response response = client.postMultipart(url, content, fileParams);
            context.setData(var, response);
            return StepResult.success("Successfully invoked web service '" + hideAuthDetails(url) + "'");
        } catch (IOException e) {
            return toFailResult(url, e);
        } finally {
            addDetailLogLink(client);
        }
    }

    protected Response resolveResponseObject(String var) throws ClassCastException {
        Object response = context.getObjectData(var);
        if (!(response instanceof Response)) {
            throw new ClassCastException("Variable '" + var + "' does not resolve to a valid HTTP response object");
        }
        return (Response) response;
    }

    @NotNull
    protected static StepResult toFailResult(String url, Throwable e) {
        String error = StringUtils.defaultString(ExceptionUtils.getRootCauseMessage(e), e.getMessage());
        return StepResult.fail("Unable to invoke '" + hideAuthDetails(url) + "': " + error);
    }

    protected void addDetailLogLink(WebServiceClient client) {
        // add detail log as link
        if (context.getBooleanData(WS_LOG_DETAIL, getDefaultBool(WS_LOG_DETAIL))) {
            addLinkRef(null, "log", client.resolveDetailLogFile(context.getCurrentTestStep()).getAbsolutePath());
        }
    }

    /**
     * priority given to `nexial.ws.requestPayloadAsRaw` then predefined HTTP header Content-Type
     */
    @NotNull
    protected OutputResolver newOutputResolver(String body) {
        return newOutputResolver(body, context.hasData(WS_REQ_FILE_AS_RAW) ?
                                       context.getBooleanData(WS_REQ_FILE_AS_RAW) :
                                       BooleanUtils.toBoolean(context.getDataByPrefix(WS_REQ_HEADER_PREFIX)
                                                                     .get(CONTENT_TYPE)));
    }

    @NotNull
    protected OutputResolver newOutputResolver(String body, boolean asBinary) {
        // is body a file? resolver will figure out
        return new OutputResolver(body, context, asBinary, compactRequestPayload());
    }
}
