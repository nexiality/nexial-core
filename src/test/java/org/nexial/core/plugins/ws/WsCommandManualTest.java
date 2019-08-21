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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.model.StepResult;

/**
 * CANNOT AUTOMATE THIS TEST DUE TO MISSING CONNECTION TO PROXY SRV
 */
public class WsCommandManualTest {
    private MockExecutionContext context;

    @Before
    public void init() {
        context = new MockExecutionContext();
    }

    @After
    public void tearDown() {
        if (context != null) { context.cleanProject(); }
    }

    @Test
    public void testGoogleApi() throws Exception {
        //NexialTestUtils.prep(this.getClass(), ResourceUtils.getFile("classpath:./").getAbsolutePath());

        String url = "http://ajax.googleapis.com/ajax/services/search/web";
        String queryString = "v=1.0&q=cars";

        // context.setData(WS_PROXY_REQUIRED, "true");

        WebServiceClient client = new WebServiceClient(context);
        Response response = client.get(url, queryString);

        System.out.println("response = " + response);

        Assert.assertNotNull(response);
        Assert.assertEquals(response.returnCode, 200);
        Assert.assertTrue(response.getContentLength() > 1);

        String jsonContent = new String(response.getRawBody());
        JSONObject json = new JSONObject(jsonContent);
        Assert.assertNotNull(json);
        System.out.println("json = " + json);
    }

    @Test
    public void testOpenWeatherApi() throws Exception {
        String url = "http://api.openweathermap.org/data/2.5/weather";
        String queryString = "q=London&APPID=50b270e6e8327679f7f34bf2c709e938";

        // context.setData(WS_PROXY_REQUIRED, "true");

        WebServiceClient client = new WebServiceClient(context);
        Response response = client.get(url, queryString);

        System.out.println("response = " + response);

        Assert.assertNotNull(response);
        Assert.assertEquals(response.returnCode, 200);
        Assert.assertTrue(response.getContentLength() > 1);

        String jsonContent = new String(response.getRawBody());
        JSONObject json = new JSONObject(jsonContent);
        Assert.assertNotNull(json);
        System.out.println("json = " + json);
    }

    @Test
    public void testUpload() throws Exception {
        String url = "http://localhost:3000/projects/psg/reports/perf-test.12.32.22.1a";

        WsCommand ws = new WsCommand();
        ws.init(context);

        StepResult result = ws.upload(url,
                                      "reportZip=/Users/ml093043/Downloads/junk/mliu_Employees-API_rev1_2018_06_06.zip\n" +
                                      "favourite=chicken\n" +
                                      "more data=yes\n" +
                                      "you&me=now&forever",
                                      "reportZip",
                                      "response");
        System.out.println("result = " + result);

        Object response = context.getObjectData("response");
        System.out.println("response = " + response);

        Response resp = (Response) response;

        Assert.assertNotNull(resp);
        Assert.assertEquals(resp.returnCode, 200);
        Assert.assertTrue(resp.getContentLength() > 1);

        String responseContent = new String(resp.getRawBody());
        System.out.println("responseContent = " + responseContent);
    }

    @Test
    public void saveWebContent() throws Exception {
        String url = "https://s3-us-west-2.amazonaws.com/ep.qm/sentry-artifact/DataServices-BI_ReportUI/" +
                     "Regression.20190820_224530/captures/ReportServicesUI-plan.020%2CDS_2545.20190820_" +
                     "224532.001~001.xlsx_Scenario_A28.png";
        File target = new File(StringUtils.appendIfMissing(SystemUtils.JAVA_IO_TMPDIR, File.separator) + "junk.png");
        File saveTo = WsCommand.saveWebContent(url, target);

        Assert.assertEquals(target.getAbsolutePath(), saveTo.getAbsolutePath());
        Assert.assertEquals(56840, saveTo.length());

        // test 2
        url = "https://s3.console.aws.amazon.com/s3/object/ep.qm/sentry-artifact/FENIX-UI-REG-TEST/" +
              "qa2.20190820_180233/captures/ST_UI_Reg_Plan.005%252CST_Summary_Checkcode_UI.20190820_" +
              "182527.001~001.xlsx_Checkcode_Edit_Timepunches_A142.png?region=us-west-2&tab=overview";
        target = new File(StringUtils.appendIfMissing(SystemUtils.JAVA_IO_TMPDIR, File.separator) + "junk2.png");
        saveTo = WsCommand.saveWebContent(url, target);

        Assert.assertEquals(target.getAbsolutePath(), saveTo.getAbsolutePath());
        Assert.assertEquals(989, saveTo.length());

    }

    // @Test
    // public void testBasicAuthWithProxy() throws Exception {
    // 	String url = "http://sanddapisct1/manager/html";
    // 	String queryString = "";
    //
    // 	context.setData(WS_PROXY_REQUIRED, "true");
    // 	context.setData(WS_BASIC_USER, "topdog");
    // 	context.setData(WS_BASIC_PWD, "mongolia");
    //
    // 	WebServiceClient client = new WebServiceClient(context);
    // 	Response response = client.get(url, queryString);
    //
    // 	System.out.println("response = " + response);
    //
    // 	Assert.assertNotNull(response);
    // 	Assert.assertEquals(response.returnCode, 200);
    // 	Assert.assertTrue(response.getContentLength() > 1);
    //
    // 	String html = new String(response.getRawBody());
    // 	Assert.assertNotNull(html);
    // 	Assert.assertTrue(StringUtils.contains(html, "Tomcat Web Application Manager"));
    // 	System.out.println("html = " + html);
    // }

    // @Test
    // public void testInternalApiWithNoProxy() throws Exception {
    // 	String url = "http://sanddapisct1/docs/web-socket-howto.html";
    // 	String queryString = "";
    //
    // 	context.setData(WS_PROXY_REQUIRED, "false");
    //
    // 	WebServiceClient client = new WebServiceClient(context);
    // 	Response response = client.get(url, queryString);
    //
    // 	System.out.println("response = " + response);
    //
    // 	Assert.assertNotNull(response);
    // 	Assert.assertEquals(response.returnCode, 200);
    // 	Assert.assertTrue(response.getContentLength() > 1);
    //
    // 	String html = new String(response.getRawBody());
    // 	Assert.assertNotNull(html);
    // 	Assert.assertTrue(StringUtils.contains(html, "WebSocket How-To"));
    // 	System.out.println("html = " + html);
    // }

    // static {
    //     System.setProperty(OPT_OUT_DIR, SystemUtils.getJavaIoTmpDir().getAbsolutePath());
    //     System.setProperty(OPT_DELAY_BROWSER, "true");
    // }
}
