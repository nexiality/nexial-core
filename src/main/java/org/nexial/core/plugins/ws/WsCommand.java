/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.plugins.ws;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.jsonwebtoken.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.TextUtils;
import org.nexial.commons.utils.web.URLEncodingUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.OutputResolver;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import static io.jsonwebtoken.SignatureAlgorithm.HS256;
import static io.jsonwebtoken.impl.TextCodec.BASE64URL;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Ws.*;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.plugins.ws.WebServiceClient.hideAuthDetails;
import static org.nexial.core.utils.CheckUtils.*;

public class WsCommand extends BaseCommand {
    protected boolean verbose;
    protected Map<String, Map<String, String>> oauthProviderDetails;

    @Override
    public void init(@NotNull ExecutionContext context) { super.init(context); }

    @Override
    public String getTarget() { return "ws"; }

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    public Map<String, Map<String, String>> getOauthProviderDetails() { return oauthProviderDetails; }

    public void setOauthProviderDetails(Map<String, Map<String, String>> oauthProviderDetails) {
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

    public StepResult post(String url, String body, String var) { return requestWithBody(url, body, var, "post"); }

    public StepResult put(String url, String body, String var) { return requestWithBody(url, body, var, "put"); }

    public StepResult patch(String url, String body, String var) { return requestWithBody(url, body, var, "patch"); }

    public StepResult head(String url, String var) { return requestNoBody(url, null, var, "head"); }

    public StepResult delete(String url, String body, String var) {
        return StringUtils.isNotEmpty(body) ?
               requestWithBody(url, body, var, "delete") :
               requestNoBody(url, "", var, "delete");
    }

    public StepResult graphql(String url, String body, String var) {
        return requestWithBody(url, body, var, "graphql");
    }

    /**
     * as of v4.2, `returnCode` now supports range or list
     */
    public StepResult assertReturnCode(String var, String returnCode) {
        requiresValidVariableName(var);
        requiresNotBlank(returnCode, "invalid return code", returnCode);

        Response response;
        try {
            response = resolveResponseObject(var);
            if (response == null) { return StepResult.fail("No response variable found using '" + var + "'"); }
        } catch (ClassCastException e) {
            return StepResult.fail("Error: " + e.getMessage());
        }

        List<Integer> returnCodes = expandReturnCodes(returnCode);
        requiresNotEmpty(returnCodes, "unable to derive any useful return code for validation", returnCode);

        if (returnCodes != null && returnCodes.contains(response.getReturnCode())) {
            return StepResult.success("EXPECTED return code found");
        } else {
            return StepResult.fail("return code (" + response.getReturnCode() + ") DID NOT match expected (" +
                                   returnCode + ")");
        }
    }

    @Nullable
    protected List<Integer> expandReturnCodes(String returnCode) {
        if (NumberUtils.isDigits(returnCode)) { return Collections.singletonList(NumberUtils.toInt(returnCode)); }

        String delim = context.getTextDelim();
        return TextUtils
            .toList(returnCode, delim, true).stream().map(code -> {
                // single digits
                if (NumberUtils.isDigits(code)) { return Collections.singletonList(NumberUtils.toInt(code)); }

                // range specified. e.g. 200-300
                if (StringUtils.contains(code, "-") && !StringUtils.startsWith(code, "-")) {
                    int startCode = NumberUtils.toInt(StringUtils.trim(StringUtils.substringBefore(code, "-")), -1);
                    int endCode = NumberUtils.toInt(StringUtils.trim(StringUtils.substringAfter(code, "-")), -1);
                    if (startCode == -1 || endCode == -1 || endCode < startCode) {
                        ConsoleUtils.error("Ignoring invalid return code range: " + code);
                        return null;
                    }

                    return IntStream.rangeClosed(startCode, endCode).boxed().collect(Collectors.toList());
                }

                ConsoleUtils.error("Ignoring invalid return code: " + code);
                return null;
            })
            .filter(CollectionUtils::isNotEmpty)
            .flatMap(Collection::stream)
            .filter(code -> code > 99 && code < 999)
            .collect(Collectors.toList());
    }

    public StepResult header(String name, String value) {
        requiresNotBlank(name, "Invalid HTTP Header name", name);

        String key = WS_REQ_HEADER_PREFIX + name;
        if (context.isNullOrEmptyOrBlankValue(value)) {
            String previousValue = context.removeData(key);
            ConsoleUtils.log("removed " + name + " as HTTP Header; previous value=" + previousValue);
        } else {
            context.setData(key, value);
        }

        return StepResult.success("set HTTP Header " + name + "=" + value);
    }

    /**
     * Clears the HTTP headers passed as comma(,) separated values. If the parameter passed in is <b>ALL</b>,
     * then it clears all the HTTP headers set so far.
     * <p>
     * For example <b>clearHeaders("x1,x2,x3")</b> clears the headers x1, x2 and x3, while
     * <b>clearHeaders("ALL")</b> clears all the headers set earlier.
     *
     * @param headers the headers to be cleared. This is a comma separated headers string or <b>ALL</b>.
     * @return {@link StepResult#success(String)} on clearing the headers.
     */
    public StepResult clearHeaders(String headers) {
        requiresNotBlank(headers, "Invalid Header(s)", headers);
        if (headers.equals(WS_ALL_HEADERS)) {
            context.removeDataByPrefix(WS_REQ_HEADER_PREFIX);
        } else {
            String delim = context.getTextDelim();
            Arrays.stream(StringUtils.split(headers, delim)).forEach(x -> {
                String key = WS_REQ_HEADER_PREFIX + x;
                String previousValue = context.removeData(key);
                ConsoleUtils.log("removed " + key + " as HTTP Header; previous value=" + previousValue);
            });
        }
        return StepResult.success("HTTP Header(s) removed.");
    }

    public StepResult headerByVar(String name, String var) {
        requiresNotBlank(name, "Invalid HTTP Header name", name);
        requiresValidAndNotReadOnlyVariableName(var);

        String key = WS_REQ_HEADER_PREFIX + name;
        Object value = context.getObjectData(var);
        if (value == null) {
            String previousValue = context.removeData(key);
            ConsoleUtils.log("removed " + name + " as HTTP Header; previous value=" + previousValue);
        } else {
            if (value instanceof String) {
                context.setData(key, (String) value);
            } else {
                context.setData(key, value);
            }
        }

        return StepResult.success("set HTTP Header " + name + "=" + value);
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
     * However, in some cases, the value could be expressed as JSON array or JSON object.
     * <p>
     * With all proper parameters properly specified, the target {@code url} would return, among other things, a
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
        String responseBody = response.getBody();
        if (response.getReturnCode() != 200) {
            ConsoleUtils.log("ERROR:\n" +
                             response.getReturnCode() + " " + response.getStatusText() + "\n" + responseBody);
            return StepResult.fail(failPrefix + response.getReturnCode() + " " + response.getStatusText());
        }
        if (response.getContentLength() < 5) {
            return StepResult.fail(failPrefix + "response content length=" + response.getContentLength());
        }

        String responseContentType = response.getHeaders().get(WS_CONTENT_TYPE);
        if (!StringUtils.startsWith(responseContentType, WS_JSON_CONTENT_TYPE)) {
            return StepResult.fail(failPrefix + "unexpected response content type: " + responseContentType);
        }

        return processOAuthResponse(var, responseBody);
    }

    private StepResult processOAuthResponse(String var, String responseBody) {
        // parse response as JSON
        JsonObject json = GSON_COMPRESSED.fromJson(responseBody, JsonObject.class);

        // save response to var
        Map<String, String> oauthResponse = new HashMap<>();
        json.entrySet().forEach(entry -> {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            // accommodate situation where json response contains multi-element array
            if (value.isJsonArray()) {
                JsonArray array = value.getAsJsonArray();
                oauthResponse.put(key, array.size() == 1 ? array.get(0).toString() : array.toString());
            } else {
                oauthResponse.put(key, value.getAsString());
            }
        });
        context.setData(var, oauthResponse);

        String tokenType = oauthResponse.get(OAUTH_TOKEN_TYPE);
        if (StringUtils.isBlank(tokenType)) {
            return StepResult.fail("OAuth request failed: invalid " + OAUTH_TOKEN_TYPE + ": " + tokenType);
        }

        // bearer support
        // some proxy generator or oauth app generates "BearerToken" instead of "Bearer". However, we must use
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

        return StepResult.fail("OAuth request failed: unknown/unsupported " + OAUTH_TOKEN_TYPE + "found: " + tokenType);
    }

    /**
     * Request to grant OAuth token(s) after reading the properties specified against the profile.
     *
     * @param var     the variable to store the result of the OAuth call.
     * @param profile the profile containing the details related to the OAuth like client_id, client_secret, username,
     *                password et.
     * @return {@link StepResult#success(String)} or {@link StepResult#fail(String)} based on the success or failure.
     */
    public StepResult oauthProfile(String var, String profile) {
        requiresValidAndNotReadOnlyVariableName(var);
        requiresNotBlank(profile, "Invalid profile", profile);

        ExecutionContext context = ExecutionThread.get();
        Map<String, String> config = context.getDataByPrefix(profile + ".");
        if (MapUtils.isEmpty(config)) { return StepResult.fail("No OAuth2 configuration found for '" + profile + "'"); }

        String type = config.get("type");
        if (!type.equals(OAUTH_CUSTOM_TYPE)) {
            Map<String, String> vendorDetails = oauthProviderDetails.get(type);
            if (vendorDetails == null || vendorDetails.isEmpty()) {
                return StepResult.fail(type + " is not a valid OAuth provider type.");
            }
            config.putAll(vendorDetails);
        }

        try {
            return processOAuth(var, config);
        } catch (Exception e) {
            return StepResult.fail("Error occurred while processing OAuth2 for '" + profile + "': " + e.getMessage());
        }
    }

    /**
     * Makes call to the OAuth url with the configuration values provided as part of profile. The necessary token(s) as
     * well as the other information will be retrieved as part of the post request.
     *
     * @param config the OAuth configuration details like client_id, client_secret, username, password ,scope etc.
     * @return OAuth related token(s) as well as other information needed for authenticating further calls.
     */
    private StepResult processOAuth(String var, Map<String, String> config) throws IOException {
        String oAuthUrl = config.get("url");
        if (oAuthUrl.contains(OAUTH_URL_PLACEHOLDER)) {
            oAuthUrl = oAuthUrl.replace(OAUTH_URL_PLACEHOLDER, config.get("tenant_id"));
        }

        if (!config.get("type").equals(OAUTH_CUSTOM_TYPE)) { config.put(OAUTH_GRANT_TYPE, config.get("grantType")); }

        String basicAuthUsername = config.get(OAUTH_BASIC_AUTH_USER);
        String basicAuthPassword = config.get(OAUTH_BASIC_AUTH_PASSWORD);

        Arrays.asList("url", "type", OAUTH_BASIC_AUTH_USER, OAUTH_BASIC_AUTH_PASSWORD).forEach(config::remove);

        StringBuilder postData = new StringBuilder();
        for (Map.Entry<String, String> param : config.entrySet()) {
            if (postData.length() != 0) { postData.append('&'); }
            postData.append(URLEncoder.encode(param.getKey(), DEF_FILE_ENCODING));
            postData.append('=');
            postData.append(URLEncoder.encode(String.valueOf(param.getValue()), DEF_FILE_ENCODING));
        }
        byte[] postDataBytes = postData.toString().getBytes(DEF_FILE_ENCODING);

        URL url = new URL(oAuthUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");

        if (StringUtils.isNotEmpty(basicAuthUsername) && StringUtils.isNotEmpty(basicAuthPassword)) {
            String encoded =
                new String(Base64.encodeBase64((basicAuthUsername + ":" + basicAuthPassword).getBytes(UTF_8)));
            conn.setRequestProperty(AUTHORIZATION, OAUTH_TOKEN_TYPE_BASIC + " " + encoded);
        }
        conn.getOutputStream().write(postDataBytes);

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder buffer = new StringBuilder();
        for (String line = reader.readLine(); line != null; line = reader.readLine()) { buffer.append(line); }

        return processOAuthResponse(var, buffer.toString());
    }

    /**
     * download content of {@code @url}, assuming the content is of text nature.
     */
    public static String resolveWebContent(String url) throws IOException { return retrieveWebContent(url).getBody(); }

    /**
     * download content of {@code @url}, assuming the content can be text or binary in nature
     */
    public static byte[] resolveWebContentBytes(String url) throws IOException {
        return retrieveWebContent(url).getRawBody();
    }

    @Nonnull
    private static Response retrieveWebContent(String url) throws IOException {
        if (StringUtils.isBlank(url)) { throw new IOException("Unable to retrieve content from URL [" + url + "]"); }

        WebServiceClient wsClient = new WebServiceClient(null);
        Response response = wsClient.get(url, null);
        int returnCode = response.getReturnCode();
        if (returnCode <= 199 || returnCode >= 300) {
            // sleep and try again...
            try { Thread.sleep(5000); } catch (InterruptedException e) { }
            response = wsClient.get(url, null);
            returnCode = response.getReturnCode();
            if (returnCode <= 199 || returnCode >= 300) {
                // give up...
                throw new IOException("Unable to retrieve content from '" + url + "': " + response.getStatusText());
            }
        }

        return response;
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
        ConsoleUtils.log("setting HTTP Header " + AUTHORIZATION + " as " + bearerCode);
        header(AUTHORIZATION, bearerCode);
        return StepResult.success("OAuth exchange completed and access token added to HTTP Header");
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
                if (StringUtils.equals(method, "graphql")) { response = client.graphql(url, payloadBytes); }
            } else {
                String payload = outputResolver.getContent();
                if (StringUtils.equals(method, "post")) { response = client.post(url, payload); }
                if (StringUtils.equals(method, "patch")) { response = client.patch(url, payload); }
                if (StringUtils.equals(method, "put")) { response = client.put(url, payload); }
                if (StringUtils.equals(method, "delete")) { response = client.deleteWithPayload(url, payload); }
                if (StringUtils.equals(method, "graphql")) { response = client.graphql(url, payload); }
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
            // the content is expected to be in the form of "key=value\nkey=value\n..."
            String content = outputResolver.getContent();

            Map<String, String> params = TextUtils.toMap(content, "\n", "=");

            // need to decide whether to use POST or PUT
            String uploadMethod = "POST";
            if (params.containsKey(WS_UPLOAD_METHOD)) {
                uploadMethod = StringUtils.upperCase(StringUtils.trim(params.get(WS_UPLOAD_METHOD)));
                params.remove(WS_UPLOAD_METHOD);
            }

            // need to decide whether to use multipart or not
            boolean isMultipart = true;
            if (params.containsKey(WS_UPLOAD_MULTIPART)) {
                String multipartFlag = StringUtils.trim(params.get(WS_UPLOAD_MULTIPART));
                isMultipart = BooleanUtils.toBoolean(multipartFlag);
                params.remove(WS_UPLOAD_MULTIPART);
            }
            
            String delim = context == null ? "," : context.getTextDelim();
            if (!isMultipart && StringUtils.contains(fileParams, delim)) {
                throw new IllegalArgumentException("Multi-file params are not compatible with non-multipart request!");
            }

            content = TextUtils.toString(params, "\n", "=");

            Response response = null;
            switch (uploadMethod) {
                case "POST": {
                    if (isMultipart) {
                        response = client.postMultipart(url, content, fileParams);
                    } else {
                        response = client.post(url, extractUploadContent(params.get(fileParams)));
                    }
                    break;
                }
                case "PUT": {
                    if (isMultipart) {
                        response = client.putMultipart(url, content, fileParams);
                    } else {
                        response = client.putWithPayload(url, extractUploadContent(params.get(fileParams)), null);
                    }
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Unsupported HTTP Method for upload: " + uploadMethod);
                }
            }

            context.setData(var, response);
            return StepResult.success("Successfully invoked web service '" + hideAuthDetails(url) + "'");
        } catch (IOException e) {
            return toFailResult(url, e);
        } finally {
            addDetailLogLink(client);
        }
    }

    private byte[] extractUploadContent(String file) throws IOException {
        if (StringUtils.isBlank(file)) { throw new IllegalArgumentException("upload file not specified!"); }
        if (!FileUtil.isFileReadable(file, 1)) {
            throw new IllegalArgumentException("specified upload file is not readable or does not exist");
        }

        return FileUtils.readFileToByteArray(new File(file));
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
     * priority given to `nexial.ws.requestPayloadAsRaw` then predefined HTTP Header Content-Type
     */
    @NotNull
    protected OutputResolver newOutputResolver(String body) {
        if (context.hasData(WS_REQ_FILE_AS_RAW)) {
            return newOutputResolver(body, context.getBooleanData(WS_REQ_FILE_AS_RAW));
        }

        // any content-type starting with "text" (like "text/plain") would be treated as NOT binary
        String contentType = context.getDataByPrefix(WS_REQ_HEADER_PREFIX).get(CONTENT_TYPE);
        boolean isBinary = StringUtils.isNotBlank(contentType) &&
                           TEXT_MIME_TYPES.stream().noneMatch(contentType::contains);
        return newOutputResolver(body, isBinary);
    }

    @NotNull
    protected OutputResolver newOutputResolver(String body, boolean asBinary) {
        // is body a file? resolver will figure out
        return new OutputResolver(body, context, asBinary, compactRequestPayload());
    }
}
