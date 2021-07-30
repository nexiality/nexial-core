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

package org.nexial.core.plugins.web

import org.apache.commons.lang3.StringUtils
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.extensions.permessage_deflate.PerMessageDeflateExtension
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import org.junit.Test
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.JsonUtils
import java.net.URI

class WebSocketTester {

    @Test
    fun testSocket() {
        val client = TestClient("deathstar716", "Club", 600)
        val connected = client.connectBlocking()
        println("connected = $connected")

        while (client.isOpen) {
            Thread.sleep(250)
        }

        println(client.messageIds)
    }
}

class TestClient(val inbox: String, val subjectSearch: String, val noOlderThanMinutes: Long) :
    WebSocketClient(toWSUri(inbox), Draft_6455(PerMessageDeflateExtension()), toWsRequestHeaders(inbox)) {

    private var timeout = initTimeout()
    private var wsAccept: String? = null
    private var disconnectReason: String? = null
    val messageIds = mutableSetOf<String>()

    override fun onOpen(handshake: ServerHandshake?) {
        wsAccept = handshake?.getFieldValue("Sec-WebSocket-Accept")
        timeout = initTimeout()
    }

    override fun onMessage(message: String?) {
        if (isTimedOut()) {
            disconnectReason = "TIME_OUT"
            close()
        }

        if (StringUtils.isBlank(message)) return

        val json = JsonUtils.toJSONObject(message)

        // not json
        if (json.has("errorCode")) {
            ConsoleUtils.log("ignoring non-conforming incoming message: $message")
            return
        }

        if (!json.has("msgs")) return

        println(JsonUtils.beautify(message))
        val messages = json.getJSONArray("msgs")
        if (messages == null || messages.length() < 1) return

        messageIds.addAll(
            messages.asSequence()
                .filterIsInstance<JSONObject>()
                .filter { it.has("subject") && StringUtils.contains(it.getString("subject"), subjectSearch) }
                .filter { it.has("seconds_ago") && it.getInt("seconds_ago") < (noOlderThanMinutes * 60) }
                .map { it.getString("id") }.toList())
        disconnectReason = "OK"
        close()
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        if (code == 1006) {
            disconnectReason = reason?.substringBefore(".") ?: "UNKNOWN"
            ConsoleUtils.log("[$code] $disconnectReason")
        } else {
            disconnectReason = reason
            ConsoleUtils.log("[$code] $reason")
        }

        close()
    }

    override fun onError(e: Exception?) {
        disconnectReason = e?.message ?: "UNKNOWN"
        ConsoleUtils.log("ERROR: $e")
        close()
    }

    private fun initTimeout() = System.currentTimeMillis() + (1000 * 20)

    private fun isTimedOut() = System.currentTimeMillis() - timeout > 0

    companion object {
        fun toWSUri(inbox: String) = URI("wss://www.mailinator.com/ws/fetchpublic?to=$inbox")

        fun toWsRequestHeaders(inbox: String) = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Encoding" to "gzip, deflate, br",
            "Accept-Language" to "en-US,en;q=0.9",
            "Cache-Control" to "no-cache",
            "Connection" to "Upgrade",
            "Cookie" to "SERVERID=s2; last_public_inbox=$inbox",
            "Host" to "www.mailinator.com",
            "Origin" to "https://www.mailinator.com",
            "Pragma" to "no-cache",
            "Sec-WebSocket-Extensions" to "permessage-deflate; client_max_window_bits",
            "Sec-WebSocket-Version" to "13",
            "Upgrade" to "websocket",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36"
        )
    }
}
