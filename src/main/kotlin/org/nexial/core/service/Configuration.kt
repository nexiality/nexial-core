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

package org.nexial.core.service

import com.google.gson.Gson
import org.nexial.core.service.ServiceUtils.Companion.gson
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.TEXT_PLAIN
import org.springframework.http.converter.ByteArrayHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.converter.json.GsonHttpMessageConverter
import org.springframework.messaging.converter.MessageConverter
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.DefaultManagedTaskScheduler
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import java.util.*

@Configuration
class MvcConfigurer : WebMvcConfigurer {

    @Bean
    fun gson(): Gson = gson

    override fun configureMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        val stringConverter = StringHttpMessageConverter()
        stringConverter.setWriteAcceptCharset(false)
        stringConverter.supportedMediaTypes = Collections.singletonList(TEXT_PLAIN)
        converters.add(stringConverter)

        converters.add(ByteArrayHttpMessageConverter())

        val gsonHttpMessageConverter = GsonHttpMessageConverter()
        gsonHttpMessageConverter.gson = gson()
        gsonHttpMessageConverter.supportedMediaTypes = Arrays.asList(APPLICATION_JSON)
        converters.add(gsonHttpMessageConverter)

        super.configureMessageConverters(converters)
    }
}

@Configuration
@EnableWebSocketMessageBroker
@EnableScheduling
@ConfigurationProperties("nexial-ready")
class ServiceConfig : WebSocketMessageBrokerConfigurer {

    var debug: Boolean = false
    lateinit var distroLocation: String
    lateinit var stompEndpoint: String
    lateinit var stompDestinationPrefix: String
    lateinit var stompDestination: Array<String>
    lateinit var allowedOrigins: Array<String>

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config
            // destination the clients communicate through
            .setApplicationDestinationPrefixes(stompDestinationPrefix)

            // destination the clients subscribe to
            .enableSimpleBroker(*stompDestination)

            .setTaskScheduler(DefaultManagedTaskScheduler())

            // 5 second heartbeat between established server/client
            .setHeartbeatValue(longArrayOf(5000, 5000))
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry
            // prefix for all STOMP communication
            .addEndpoint(stompEndpoint)
            // not allowing "*" any origin, just those from local project inspector files
            .setAllowedOrigins(*allowedOrigins)
        //.withSockJS()
    }

    override fun configureMessageConverters(messageConverters: MutableList<MessageConverter>): Boolean {
        val gsonConverter = GsonMessageConverter(listOf(APPLICATION_JSON))
        gsonConverter.gson = gson
        messageConverters += gsonConverter
        return super.configureMessageConverters(messageConverters)
    }
}
