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

import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.utils.JsonUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;

public class WsCommandTest {
    ExecutionContext context = new MockExecutionContext();
    private String secret = "1234567890abcdefghijk";

    @Before
    public void init() {
    }

    @After
    public void tearDown() {
        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
    }

    @Test
    public void assertReturnCode() {

    }

    @Test
    public void header() {

    }

    @Test
    public void headerByVar() {

    }

    @Test
    public void saveResponsePayload() {

    }

    @Test
    public void jwtSignHS256() {
        WsCommand subject = new WsCommand();
        subject.init(context);

        StepResult result = subject.jwtSignHS256(
            "fixture",
            "{ \"name\":\"John Doe\", \"citizenship\":\"Unknown\", \"Jimmy crack corn\":\"and I don't care!\" }",
            secret);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        String token = context.getStringData("fixture");
        System.out.println("token = " + token);
        Assert.assertNotNull(token);
        Assert.assertTrue(StringUtils.isNotBlank(token));
    }

    @Test
    public void jwtParseHS256() {
        WsCommand subject = new WsCommand();
        subject.init(context);

        StepResult result = subject.jwtParse(
            "fixture",
            "eyJhbGciOiJIUzI1NiJ9.eyAibmFtZSI6IkpvaG4gRG9lIiwgImNpdGl6ZW5zaGlwIjoiVW5rbm93biIsICJKaW1teSBjcmFjayBjb3JuIjoiYW5kIEkgZG9uJ3QgY2FyZSEiIH0.45CGtWSayaPh3B-Ie49kpzMHcYswSBY3mA9lXTfVq7g",
            secret);
        // StepResult result = subject.jwtParse("fixture", "eyJhbGciOiJIUzI1NiJ9.eyAibmFtZSI6IkpvaG4gRG9lIiwgImNpdGl6ZW5zaGlwIjoiVW5rbm93biIsICJKaW1teSBjcmFjayBjb3JuIjoiYW5kIEkgZG9uJ3QgY2FyZSEiIH0.45CGtWSayaPh3B-Ie49kpzMHcYswSBY3mA9lXTfVq7g", secret);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        String payload = context.getStringData("fixture");
        System.out.println("payload = " + payload);
        Assert.assertNotNull(payload);
        Assert.assertTrue(StringUtils.isNotBlank(payload));

        JSONObject json = JsonUtils.toJSONObject(payload);
        System.out.println("json = " + json);
        Assert.assertNotNull(json);
        Assert.assertEquals("John Doe", json.get("name"));
        Assert.assertEquals("Unknown", json.get("citizenship"));
    }

    @Test
    public void jwtParseHS256_badkey() {
        WsCommand subject = new WsCommand();
        subject.init(context);

        StepResult result = subject.jwtParse(
            "fixture",
            "eyJhbGciOiJIUzI1NiJ9.eyAibmFtZSI6IkpvaG4gRG9lIiwgImNpdGl6ZW5zaGlwIjoiVW5rbm93biIsICJKaW1teSBjcmFjayBjb3JuIjoiYW5kIEkgZG9uJ3QgY2FyZSEiIH0.45CGtWSayaPh3B-Ie49kpzMHcYswSBY3mA9lXTfVq7g",
            null);
        Assert.assertNotNull(result);
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String payload = context.getStringData("fixture");
        System.out.println("payload = " + payload);
        Assert.assertNotNull(payload);
        Assert.assertTrue(StringUtils.isNotBlank(payload));

        JSONObject json = JsonUtils.toJSONObject(payload);
        System.out.println("json = " + json);
        Assert.assertNotNull(json);
        Assert.assertEquals("John Doe", json.get("name"));
        Assert.assertEquals("Unknown", json.get("citizenship"));
    }

    @Test
    public void jwtParseRS256_badkey() {
        WsCommand subject = new WsCommand();
        subject.init(context);

        StepResult result = subject.jwtParse(
            "fixture",
            "eyJhbGciOiJSUzI1NiIsImtpZCI6ImJwODExIn0.eyJzdWIiOiJic2FuZGVyc0BlcC5jb20iLCJyb2xlIjoiQ2VudHJhbENhc3RpbmdfQW55X1N5c3RlbUFkbWluIiwic2NvcGVzIjoiQ2FzdGluZ0FQSUluZGV4ZXJTY29wZSBDYXN0aW5nQVBJT2NjdXJyZW5jZVN1c3BlbnNpb25zU2NvcGUgQ2FzdGluZ0FQSVNlYXJjaFNjb3BlIENhc3RpbmdBUElMb29rdXBTY29wZSBDYXN0aW5nQVBJVGFsZW50U2NvcGUgQ2FzdGluZ0FQSVRhbGVudE5pY2tTdGF0dXNTY29wZSBDYXN0aW5nQVBJVGFsZW50Q2FzdGluZ1Njb3BlIENhc3RpbmdBUElUYWdTY29wZSBDYXN0aW5nQVBJVGFsZW50RGVsZXRlUmVxdWVzdFNjb3BlIENhc3RpbmdBUElQU0dTY29wZSBDYXN0aW5nQVBJUmVhZFdyaXRlVGFnU2NvcGUgQ2FzdGluZ0FQSUFkbWluU2NvcGUiLCJlbWFpbCI6ImJzYW5kZXJzQGVwLmNvbSIsImF1ZCI6IkNlbnRyYWxDYXN0aW5nIiwianRpIjoidTNXYmlwdEN5YllZNXdQR2lZSU0zViIsImlzcyI6Imh0dHBzOlwvXC9pZC1kZXYuZXAuY29tIiwiaWF0IjoxNDc5MjIzMDU3LCJleHAiOjE0NzkyMjMzNTcsInBpLnNyaSI6Ikh5cWlJbTNZRDg5cTF0aVdpb0E5ZUtOZWlFZyIsImF1dGhfdGltZSI6MTQ3OTIyMzA1N30.lYTkVVd1ub1Tcn2HUrt9RGJE2XlDtF7EvuMUB452JNQ3GsuKxjTjFyxnPk5w-Bt-gCvaTLv6cCH-WUgyFcY-jJzyRmWBdVdjiqQ_-RpvNibyfyq2-ME5yvuullHKiBxXbyXZsq4pxjRHb5OxgkN-LaUsy-wB8uvjN-vC4XpxaiXWnWrgy1T_ZuuiTY_J2UTFUDz_gsaELVVQiHT7E2ISRoP0jXWFtk_mzUxKxBseuZrLFvgoo2epnnvlbBMcM5_IPMNj4D-OzPCSs6VYP63ePzkSJlCbzyRco4-WNdZ-aHnc9SaAvtqbx_YksJtP4YM9FwA8ULggjLcsDhte-Db8aQ",
            null);
        Assert.assertNotNull(result);
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String payload = context.getStringData("fixture");
        System.out.println("payload = " + payload);
        Assert.assertNotNull(payload);
        Assert.assertTrue(StringUtils.isNotBlank(payload));

        JSONObject json = JsonUtils.toJSONObject(payload);
        System.out.println("json = " + json);
        Assert.assertNotNull(json);
        Assert.assertEquals("bsanders@ep.com", json.get("sub"));
        Assert.assertEquals("CentralCasting", json.get("aud"));
        Assert.assertEquals("CastingAPIIndexerScope " +
                            "CastingAPIOccurrenceSuspensionsScope " +
                            "CastingAPISearchScope " +
                            "CastingAPILookupScope " +
                            "CastingAPITalentScope " +
                            "CastingAPITalentNickStatusScope " +
                            "CastingAPITalentCastingScope " +
                            "CastingAPITagScope " +
                            "CastingAPITalentDeleteRequestScope " +
                            "CastingAPIPSGScope " +
                            "CastingAPIReadWriteTagScope " +
                            "CastingAPIAdminScope", json.get("scopes"));
    }

    // @Test
    // can't run this now... blocked by ingress network rule
    public void oauth() {
        WsCommand subject = new WsCommand();
        subject.init(context);

        StepResult result = subject.oauth("dummy", "https://oidc-dev.api.ep.com/oauth/token",
                                            "client_id=5MrGaUmuplzAL08ZN87kMm89CdAlM3dz\n" +
                                            "client_secret=SOmlVfTpvfaWbZLa\n" +
                                            "scope=NotificationAPINotificationScope\n" +
                                            "grant_type=client_credentials");
        System.out.println("result = " + result);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        Map oauthVar = context.getMapData("dummy");
        System.out.println("context.getObjectData(dummy) = \n" + oauthVar);
        // System.out.println(context.replaceTokens("${dummy}.[access_token]"));
        // System.out.println(context.replaceTokens("${dummy}.access_token"));
        Assert.assertEquals(oauthVar.get("access_token"), context.replaceTokens("${dummy}.[access_token]"));
        Assert.assertEquals(oauthVar.get("organization_name"), context.replaceTokens("${dummy}.organization_name"));
    }

    public void _old_oauth() {
        String tokenLocation = "https://oidc-dev.api.ep.com/oauth/token";
        String clientId = "5MrGaUmuplzAL08ZN87kMm89CdAlM3dz";
        String clientSecret = "SOmlVfTpvfaWbZLa";
        String scope = "NotificationAPINotificationScope";
        String grantType = "client_credentials";
        String basicHeader = "Basic " + new String(Base64.encodeBase64((clientId + ":" + clientSecret).getBytes()));

        WsCommand subject = new WsCommand();
        subject.init(context);

        subject.header(AUTHORIZATION, basicHeader);
        subject.header(CONTENT_TYPE, "application/x-www-form-urlencoded");
        subject.post(tokenLocation, "grant_type=" + grantType + "&" + "scope=" + scope, "dummy");
        Response response = (Response) context.getObjectData("dummy");
        System.out.println("response = " + response);
        System.out.println("response body = " + response.getBody());

		/*
		{
		  "refresh_token_expires_in" : "0",
		  "api_product_list" : "[CentralCastingConnectProduct, NotificationProduct]",
		  "organization_name" : "ep",
		  "developer.email" : "jhart@ep.com",
		  "token_type" : "BearerToken",
		  "issued_at" : "1490326890373",
		  "client_id" : "5MrGaUmuplzAL08ZN87kMm89CdAlM3dz",
		  "access_token" : "KZd2gkOLqJw8RfFcDA6Re2TcvzoY",
		  "application_name" : "3e6a09ef-34bd-4caa-9c78-6e67869ecd53",
		  "scope" : "NotificationAPINotificationScope",
		  "expires_in" : "1799",
		  "refresh_count" : "0",
		  "status" : "approved"
		}
		*/
        Gson GSON = new GsonBuilder().setPrettyPrinting()
                                     .disableHtmlEscaping()
                                     .disableInnerClassSerialization()
                                     .setLenient()
                                     .create();
        JsonObject json = GSON.fromJson(response.getBody(), JsonObject.class);
        System.out.println(StringUtils.rightPad("API Products", 20) + json.get("api_product_list").getAsString());
        System.out.println(StringUtils.rightPad("organization", 20) + json.get("organization_name").getAsString());
        System.out.println(StringUtils.rightPad("Developer Email", 20) + json.get("developer.email").getAsString());
        System.out.println(StringUtils.rightPad("Issued", 20) + new java.util.Date(json.get("issued_at").getAsLong()));
        System.out.println(StringUtils.rightPad("Client ID", 20) + json.get("client_id").getAsString());
        System.out.println(StringUtils.rightPad("Access Token", 20) + json.get("access_token").getAsString());
        System.out.println(StringUtils.rightPad("Application", 20) + json.get("application_name").getAsString());
        System.out.println(StringUtils.rightPad("Scope", 20) + json.get("scope").getAsString());
        System.out.println(StringUtils.rightPad("Expires In", 20) + json.get("expires_in").getAsLong());
        System.out.println(StringUtils.rightPad("Refresh Count", 20) + json.get("refresh_count").getAsString());
        System.out.println(StringUtils.rightPad("Status", 20) + json.get("status").getAsString());
    }
}