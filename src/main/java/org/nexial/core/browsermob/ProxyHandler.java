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

package org.nexial.core.browsermob;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import org.nexial.core.model.ExecutionContext;
import net.lightbody.bmp.core.har.Har;

import static org.nexial.core.NexialConst.OPT_PROXY_PASSWORD;
import static org.nexial.core.NexialConst.OPT_PROXY_USER;

public class ProxyHandler {
	private NexialProxyServer server;
	private Har har;
	private boolean invokeInterceptor;
	private ExecutionContext context;
	private Properties prop;

	private PostDataComponentManager interceptedPDCM;

	public boolean isInvokeInterceptor() {
		return invokeInterceptor;
	}

	public void setInvokeInterceptor(boolean invokeInterceptor) {
		this.invokeInterceptor = invokeInterceptor;
	}

	public PostDataComponentManager getInterceptedPDCM() {
		return interceptedPDCM;
	}

	public void setInterceptedPDCM(PostDataComponentManager interceptedPDCM) {
		this.interceptedPDCM = interceptedPDCM;
	}

	public void setContext(ExecutionContext context) { this.context = context; }

	public Har getHar() { return har; }

	public NexialProxyServer getServer() { return server; }

	public void startProxy() {
		try {
			prop = PropertiesLoaderUtils.loadAllProperties(
				"org/nexial/core/plugins/har/browsermob.properties");

			server = new NexialProxyServer(Integer.parseInt(prop.getProperty("browsermob.port")));
			server.setContext(context);
			server.start();

			configureProxy(context.getStringData(OPT_PROXY_USER), context.getStringData(OPT_PROXY_PASSWORD));

			createHAR();
			invokeInterceptor = false;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void configureProxy(String user, String password) {
		Map<String, String> options = new HashMap<>();
		options.put("httpProxy", prop.getProperty("proxy.http"));
		options.put("httpsProxy", prop.getProperty("proxy.https"));

		final byte[] info = (user + ":" + password).getBytes();

		server.setOptions(options);
		server.setCaptureHeaders(true);
		server.setCaptureContent(true);
		server.addRequestInterceptor(new HttpRequestInterceptor() {
			String userinfo = new String(Base64.encodeBase64(info));

			@Override
			public void process(HttpRequest request, HttpContext context) {
				request.removeHeaders("Proxy-Authorization");
				request.setHeader("Proxy-Authorization", "Basic " + userinfo);
			}
		});
	}

	public void stopProxy() {
		try {
			server.cleanup();
			server.stop();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void writeHARToFile(String path) {
		try {
			Thread.sleep(2000);
			har = server.getHar();
			FileOutputStream fos = new FileOutputStream(path);
			har.writeTo(fos);
			har = server.newHar("HAR");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void createHAR() {
		har = server.newHar("HAR");
	}

}
