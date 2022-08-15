/*
 * Copyright 2012-2022 the original author or authors.
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

package org.nexial.core.interactive

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS
import org.java_websocket.WebSocket
import org.java_websocket.framing.CloseFrame.UNEXPECTED_CONDITION
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.DefaultSSLWebSocketServerFactory
import org.java_websocket.server.DefaultWebSocketServerFactory
import org.java_websocket.server.WebSocketServer
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.Project.USER_NEXIAL_HOME
import org.nexial.core.utils.ConsoleUtils
import java.io.File
import java.io.File.separator
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory


// constants
internal const val DEFAULT_PORT = 20183
internal const val CERT_KEY_TYPE = "JKS"
internal const val CERT_KEY_ALGO = "SunX509"
internal const val SSL_PROTOCOL = "TLS"
internal const val JKS_FILENAME = "nexial-keystore.jks"
internal const val CERT_STORE_PASSWORD = "nexialrocks"
internal const val CERT_KEY_PASSWORD = "nexialrocks"

// internal val FRONTDESK_HOME = "${System.getProperty(Project.NEXIAL_HOME)}${separator}bin${separator}frontdesk"
internal const val FRONTDESK_HOME = "C:\\projects\\nexial\\nexial-ui"
internal val FRONTDESK_LAUNCHER =
    if (IS_OS_WINDOWS) "${System.getenv("APPDATA")}${separator}npm${separator}neu.cmd"
    else "${System.getenv("HOME")}${separator}npm${separator}neu.sh"
internal const val WS_PREFIX = "ws://"
internal const val WS_PREFIX_SECURE = "wss://"
internal const val HANDSHAKE_KEY = "nexial-handshake"
internal const val CALLBACK_KEY = "callback-ws"

private val MSG_HANDSHAKE_VERIFIED = "HANDSHAKE_VERIFIED"

class NexialWebSocket(port: Int = DEFAULT_PORT, val secure: Boolean = false, val handshake: String) :
    WebSocketServer(InetSocketAddress(port)) {
    private val logId = "nexial-ready@${Thread.currentThread().id}"
    private val connectedListeners = mutableListOf<ConnectedEventListener>()
    private val messageListeners = mutableListOf<MessageEventListener>()
    private val errorListeners = mutableListOf<ErrorEventListener>()
    private val closeListeners = mutableListOf<CloseEventListener>()
    private val clients = mutableListOf<WebSocket>()

    override fun start() {
        setWebSocketFactory(
            if (secure) DefaultSSLWebSocketServerFactory(newSslContext())
            else DefaultWebSocketServerFactory())
        super.start()
    }

    private fun newSslContext(): SSLContext {
        val jksFile = "$USER_NEXIAL_HOME${separator}$JKS_FILENAME"

        val ks = KeyStore.getInstance(CERT_KEY_TYPE)
        ks.load(FileInputStream(File(jksFile)), CERT_STORE_PASSWORD.toCharArray())

        val kmf = KeyManagerFactory.getInstance(CERT_KEY_ALGO)
        kmf.init(ks, CERT_KEY_PASSWORD.toCharArray())

        val tmf = TrustManagerFactory.getInstance(CERT_KEY_ALGO)
        tmf.init(ks)

        val sslContext = SSLContext.getInstance(SSL_PROTOCOL)
        sslContext.init(kmf.keyManagers, tmf.trustManagers, null)

        return sslContext
    }

    override fun onOpen(ws: WebSocket, handshake: ClientHandshake) {
        val resourceDescriptor = handshake.resourceDescriptor
        val resourceMap = TextUtils.toMap(StringUtils.removeStart(resourceDescriptor, "/?"), " ", "=");
        val incomingHandshake = resourceMap["nexial-handshake"]
        if (!StringUtils.equals(this.handshake, incomingHandshake)) {
            ConsoleUtils.error(logId, "Invalid handshake found: $incomingHandshake")
            ws.close(UNEXPECTED_CONDITION, "Invalid handshake")
        } else {
            ConsoleUtils.log(logId, "verified connection: $resourceDescriptor")
            ws.send(MSG_HANDSHAKE_VERIFIED)
            clients += ws
            connectedListeners.forEach { listener -> GlobalScope.launch { listener.onConnected(ws) } }
        }
    }

    override fun onClose(ws: WebSocket, code: Int, reason: String, remote: Boolean) {
        closeListeners.forEach { listener -> GlobalScope.launch { listener.onClose(ws, code, reason, remote) } }
        ConsoleUtils.log("connection closed: ${ws}, code: ${code}, reason: $reason")
    }

    override fun onMessage(ws: WebSocket, message: String) {
        ConsoleUtils.log("message received from ${ws}: $message")
        messageListeners.forEach { listener -> GlobalScope.launch { listener.onMessage(ws, message) } }
    }

    override fun onError(ws: WebSocket, ex: Exception) {
        ConsoleUtils.error("connection error from ${ws}: $ex")
        errorListeners.forEach { listener -> GlobalScope.launch { listener.onError(ws, ex) } }
    }

    override fun onStart() {
        connectionLostTimeout = 0
    }

    fun addConnectedListener(listener: ConnectedEventListener) {
        if (!connectedListeners.contains(listener)) connectedListeners += listener
    }

    fun addMessageListener(listener: MessageEventListener) {
        if (!messageListeners.contains(listener)) messageListeners += listener
    }

    fun addErrorListener(listener: ErrorEventListener) {
        if (!errorListeners.contains(listener)) errorListeners += listener
    }

    fun addCloseListener(listener: CloseEventListener) {
        if (!closeListeners.contains(listener)) closeListeners += listener
    }

    fun terminateClientsAndClose() {
        clients.forEach { ws -> ws.send("close:0") }
        clients.clear()
        stop(1000)
        ConsoleUtils.log(logId, "sent termination command to all connected clients.")
    }
}

interface ConnectedEventListener {
    fun onConnected(ws: WebSocket)
}

interface MessageEventListener {
    fun onMessage(ws: WebSocket, message: String)
}

interface ErrorEventListener {
    fun onError(ws: WebSocket, ex: Exception)
}

interface CloseEventListener {
    fun onClose(ws: WebSocket, code: Int, reason: String, remote: Boolean)
}

