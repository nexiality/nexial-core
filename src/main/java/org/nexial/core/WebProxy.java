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

package org.nexial.core;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.ForcefulTerminate;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.SecretUtils;
import org.openqa.selenium.Proxy;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import net.lightbody.bmp.proxy.ProxyServer;

import static org.nexial.core.NexialConst.*;
import static org.openqa.selenium.Proxy.ProxyType.DIRECT;

/**
 * TODO: FIXME: CURRENT NOT WORKING... BE PATIENT
 */
public final class WebProxy implements ForcefulTerminate {
	private static final String PROP = "com/ep/qa/nexial/utils/config.properties";
	private static final WebProxy THIS = new WebProxy();

	private Properties config = readConfig(PROP);
	private HttpHost apacheProxy;
	private BasicCredentialsProvider credsProvider;
	private Proxy seleniumProxy;
	private Proxy noProxy;
	private ProxyServer proxyServer;

	private WebProxy() {
		config = readConfig(PROP);
	}

	public static BasicCredentialsProvider getApacheCredentialProvider(ExecutionContext context) {
		if (THIS.credsProvider == null) { THIS.initApacheProxy(context); }
		return THIS.credsProvider;
	}

	public static HttpHost getApacheProxy(ExecutionContext context) {
		if (THIS.apacheProxy == null) { THIS.initApacheProxy(context); }
		return THIS.apacheProxy;
	}

	public static Proxy getSeleniumProxy() {
		if (THIS.seleniumProxy == null) { THIS.initSeleniumProxy(); }
		return THIS.seleniumProxy;
	}

	public static ProxyServer getProxyServer() { return THIS.proxyServer; }

	public static Proxy getDirect() {
		if (THIS.noProxy == null) { THIS.initNoProxy(); }
		return THIS.noProxy;
	}

	@Override
	public boolean mustForcefullyTerminate() { return seleniumProxy != null; }

	@Override
	public void forcefulTerminate() {
		seleniumProxy = null;
		if (proxyServer != null) {
			try {
				proxyServer.stop();
			} catch (Exception e) {
				ConsoleUtils.error("Error stopping proxy server: " + e.getMessage());
			}
			proxyServer.cleanup();
		}
	}

	private Properties readConfig(String propertiesFile) {
		try {
			Properties prop = PropertiesLoaderUtils.loadAllProperties(propertiesFile);
			Set<Object> keys = prop.keySet();
			for (Object key : keys) { prop.put(key, SecretUtils.unscramble(prop.getProperty((String) key))); }
			return prop;
		} catch (IOException e) {
			throw new RuntimeException("Unable to load " + propertiesFile + ": " + e.getMessage());
		}
	}

	private void initNoProxy() {
		noProxy = new Proxy().setProxyType(DIRECT);
		ConsoleUtils.log("setting HTTP(s) proxy to DIRECT (no proxy)");
	}

	private void initSeleniumProxy() {
		ShutdownAdvisor.addAdvisor(this);
		//boolean proxyOverAdpProxy = BooleanUtils.toBoolean(System.getProperty(OPT_PROXY_OVER_ADP_PROXY, "true"));

		try {
			proxyServer = new ProxyServer(PROXY_PORT);
			proxyServer.start();

			/*
			if (proxyOverAdpProxy) {
				String httpProxy = config.getProperty(WS_PROXY_HOST) + ":" + config.getProperty(WS_PROXY_PORT);
				String proxyUser = config.getProperty(WS_PROXY_USER);
				String proxyPwd = config.getProperty(WS_PROXY_PWD);

				Map<String, String> options = new HashMap<>();
				options.put("httpProxy", httpProxy);
				options.put("httpsProxy", httpProxy);
				proxyServer.setOptions(options);

				proxyServer.autoBasicAuthorization("ES", proxyUser, proxyPwd);
				final byte[] auth = (proxyUser + ":" + proxyPwd).getBytes();
				proxyServer.addRequestInterceptor(new HttpRequestInterceptor() {
					String userinfo = new String(Base64.encodeBase64(auth));

					@Override
					public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
						request.removeHeaders("Proxy-Authorization");
						//request.setHeader("WWW-Authenticate", "NTLM");
						request.setHeader("Proxy-Authorization", "Basic " + userinfo);
					}
				});

				ConsoleUtils.log("setting HTTP(s) proxy to " + httpProxy);
			}
			*/

			// doesn't work
			//server.whitelistRequests(
			//	new String[]{
			//		"http://sanddapisct1.ga.adp.com/.*"
			//		"http://localhost/*",
			//		"http://127.0.0.1/*",
			//		"http://*.*.adp.com/.*",
			//		"http://*.adp.com/.*",
			//		"https://*.adp.com/.*",
			//		"https://*.*.adp.com/.*",
			//		"https://sand*/.*",
			//		"https://sand*.*/.*"
			//	}, 200);

			proxyServer.setCaptureHeaders(false);
			proxyServer.setCaptureContent(false);
			proxyServer.setCaptureBinaryContent(false);

			// needed for ie
			//InetAddress localHost = InetAddress.getLocalHost();
			//proxyServer.setLocalHost(localHost);
			proxyServer.setPort(PROXY_PORT);

			seleniumProxy = proxyServer.seleniumProxy();
			//if (proxyOverAdpProxy) {
			//	seleniumProxy.setNoProxy("localhost;127.0.0.1;.adp.com;*.adp.com; " +
			//	                         "localhost, 127.0.0.1, .adp.com, *.adp.com");
			//}
		} catch (Exception e) {
			throw new RuntimeException("Unable to initialize proxy: " + e.getMessage());
		}
	}

	private void initApacheProxy(ExecutionContext context) {
		String hostname;
		try {
			hostname = EnvUtils.getHostName();
		} catch (UnknownHostException e) {
			ConsoleUtils.log("Unable to determine host name, default to localhost: " + e.getMessage());
			hostname = "localhost";
		}

		String proxyHost = context.getStringData(WS_PROXY_HOST);
		int proxyPort = NumberUtils.toInt(context.getStringData(WS_PROXY_PORT));

		if (StringUtils.isNotBlank(proxyHost) && proxyPort > 0) {
			// custom apacheProxy setting
			apacheProxy = new HttpHost(proxyHost, proxyPort);

			String proxyUser = context.getStringData(WS_PROXY_USER);
			String proxyPwd = context.getStringData(WS_PROXY_PWD);
			if (StringUtils.isNotBlank(proxyUser) && StringUtils.isNotBlank(proxyPwd)) {
				credsProvider = new BasicCredentialsProvider();
				credsProvider.setCredentials(new AuthScope(apacheProxy),
				                             new NTCredentials(proxyUser, proxyPwd, hostname, "ES"));
				//new UsernamePasswordCredentials(proxyUser, proxyPwd));
			}

		} else {
			// use default creds. prvder
			// todo: default no longer relevant
			proxyHost = config.getProperty(WS_PROXY_HOST);
			proxyPort = NumberUtils.toInt(config.getProperty(WS_PROXY_PORT));
			apacheProxy = new HttpHost(proxyHost, proxyPort);

			String proxyUser = config.getProperty(WS_PROXY_USER);
			String proxyPwd = config.getProperty(WS_PROXY_PWD);
			credsProvider = new BasicCredentialsProvider();
			credsProvider.setCredentials(new AuthScope(apacheProxy, "ES", null),
			                             new NTCredentials(proxyUser, proxyPwd, hostname, "ES"));
			//new UsernamePasswordCredentials(proxyUser, proxyPwd));
		}
	}
}
