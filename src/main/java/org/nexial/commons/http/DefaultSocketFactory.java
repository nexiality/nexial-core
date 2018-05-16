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

package org.nexial.commons.http;

//import java.io.IOException;
//import java.net.InetAddress;
//import java.net.InetSocketAddress;
//import java.net.Socket;
//import java.net.SocketAddress;
//
//import javax.net.SocketFactory;
//import javax.net.ssl.SSLContext;
//import javax.net.ssl.TrustManager;
//
//import org.apache.http.params.HttpConnectionParams;
//import org.apache.http.client.protocol.ProtocolSocketFactory;
//import org.apache.log4j.Logger;

/**
 * DefaultSocketFactory can be used to creats SSL {@link Socket}s that accept self-signed
 * certificates.
 * <p/>
 * Example of using custom protocol socket factory for a specific host:
 * <pre>
 * Protocol easyhttps = new Protocol("https", new DefaultSocketFactory(), 443);
 *
 * URI uri = new URI("https://localhost/", true);
 * // use relative url only
 * GetMethod httpget = new GetMethod(uri.getPathQuery());
 * HostConfiguration hc = new HostConfiguration();
 * hc.setHost(uri.getHost(), uri.getPort(), easyhttps);
 * HttpClient client = new HttpClient();
 * client.executeMethod(hc, httpget);
 * </pre>
 * <p/>
 * Example of using custom protocol socket factory per default instead of the standard one:
 * <pre>
 * Protocol easyhttps = new Protocol("https", new DefaultSocketFactory(), 443);
 * Protocol.registerProtocol("https", easyhttps);
 *
 * HttpClient client = new HttpClient();
 * GetMethod httpget = new GetMethod("https://localhost/");
 * client.executeMethod(httpget);
 * </pre>
 *
 * @author Mike Liu
 */
public class DefaultSocketFactory /*implements ProtocolSocketFactory {
	private static final Logger LOGGER = Logger.getLogger(DefaultSocketFactory.class);
	private SSLContext sslcontext;

	private static SSLContext createEasySSLContext() {
		try {
			SSLContext context = SSLContext.getInstance("SSL");
			context.init(null, new TrustManager[]{new DefaultTrustManager(null)}, null);
			return context;
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			throw new RuntimeException(e);
		}
	}

	private SSLContext getSSLContext() {
		if (sslcontext == null) { sslcontext = createEasySSLContext(); }
		return sslcontext;
	}

	public Socket createSocket(String host, int port, InetAddress clientHost, int clientPort) throws IOException {
		return getSSLContext().getSocketFactory().createSocket(host, port, clientHost, clientPort);
	}

	*//**
 * Attempts to get a new socket connection to the given host within the given time limit.
 * <p>
 * To circumvent the limitations of older JREs that do not support connect timeout a
 * controller thread is executed. The controller thread attempts to create a new socket
 * within the given limit of time. If socket constructor does not return until the
 * timeout expires, the controller terminates and throws an {@link IOException}
 * </p>
 *
 * @param host         the host name/IP
 * @param port         the port on the host
 * @param localAddress the local host name/IP to bind the socket to
 * @param localPort    the port on the local machine
 * @param params       {@link HttpConnectionParams Http connection parameters}
 * @return Socket a new socket
 * @throws IOException if an I/O error occurs while creating the socket
 *//*
	public Socket createSocket(final String host,
	                           final int port,
	                           final InetAddress localAddress,
	                           final int localPort,
	                           final HttpConnectionParams params) throws IOException {
		if (params == null) { throw new IllegalArgumentException("Parameters may not be null"); }
		int timeout = params.getConnectionTimeout();
		SocketFactory socketfactory = getSSLContext().getSocketFactory();
		if (timeout == 0) { return socketfactory.createSocket(host, port, localAddress, localPort); }

		Socket socket = socketfactory.createSocket();
		SocketAddress localaddr = new InetSocketAddress(localAddress, localPort);
		SocketAddress remoteaddr = new InetSocketAddress(host, port);
		socket.bind(localaddr);
		socket.connect(remoteaddr, timeout);
		return socket;
	}

	public Socket createSocket(String host, int port) throws IOException {
		return getSSLContext().getSocketFactory().createSocket(host, port);
	}

	@Override
	public boolean equals(Object obj) { return obj != null && obj.getClass().equals(this.getClass()); }

	@Override
	public int hashCode() { return DefaultSocketFactory.class.hashCode(); }
}*/ { }