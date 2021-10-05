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

package org.nexial.core.plugins.jms;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.jms.*;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.model.ExecutionContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import static javax.jms.DeliveryMode.NON_PERSISTENT;
import static javax.jms.Session.AUTO_ACKNOWLEDGE;
import static org.nexial.core.NexialConst.MS_UNDEFINED;

public class JmsClient implements ApplicationContextAware {
    protected ApplicationContext spring;
    protected ExecutionContext context;

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException { spring = ctx; }

    public void setContext(ExecutionContext context) { this.context = context; }

    public void send(JmsClientConfig config, String messageId, String payload) throws JMSException {
        sendObject(config, messageId, payload);
    }

    public void send(JmsClientConfig config, String messageId, Map<String, String> payload) throws JMSException {
        sendObject(config, messageId, payload);
    }

    public Object receive(JmsClientConfig config, long timeout) throws JMSException {
        if (config == null) { throw new IllegalArgumentException("config is null"); }

        Connection connection = null;
        Session session = null;

        try {
            connection = config.createConnection();
            if (connection == null) { throw new IllegalArgumentException("Unable to resolve JMS connection"); }

            session = connection.createSession(false, AUTO_ACKNOWLEDGE);
            if (session == null) { throw new IllegalArgumentException("Unable to resolve JMS session"); }

            Destination destination = config.resolveDestination(session);
            if (destination == null) { throw new IllegalArgumentException("Unable to resolve JMS destination"); }

            MessageConsumer consumer = session.createConsumer(destination);
            Message msg = timeout == MS_UNDEFINED ? consumer.receive() : consumer.receive(timeout);
            if (msg == null) { return null; }

            if (msg instanceof TextMessage) { return handleTextMessage((TextMessage) msg); }
            if (msg instanceof MapMessage) { return handleMapMessage((MapMessage) msg); }

            throw new UnsupportedOperationException("Unknown/unsupported message type: " + msg.getClass());
        } finally {
            if (session != null) { try { session.close(); } catch (JMSException e) { } }
            if (connection != null) { try { connection.close(); } catch (JMSException e) { } }
        }
    }

    protected void sendObject(JmsClientConfig config, String messageId, Object payload) throws JMSException {
        if (config == null) { throw new IllegalArgumentException("config is null"); }
        if (payload == null) { throw new IllegalArgumentException("payload is missing"); }

        Connection connection = null;
        Session session = null;

        try {
            connection = config.createConnection();
            if (connection == null) { throw new IllegalArgumentException("Unable to resolve JMS connection"); }

            session = connection.createSession(false, AUTO_ACKNOWLEDGE);
            if (session == null) { throw new IllegalArgumentException("Unable to resolve JMS session"); }

            Destination destination = config.resolveDestination(session);
            if (destination == null) { throw new IllegalArgumentException("Unable to resolve JMS destination"); }

            MessageProducer producer = session.createProducer(destination);
            producer.setDeliveryMode(NON_PERSISTENT);

            Message msg;
            if (payload instanceof Map) {
                msg = session.createMapMessage();
                Map<String, Object> map = (Map<String, Object>) payload;
                for (String name : map.keySet()) { msg.setObjectProperty(name, map.get(name)); }
            } else {
                msg = session.createTextMessage(String.valueOf(payload));
            }

            if (StringUtils.isNotBlank(messageId)) { msg.setJMSMessageID(messageId); }

            producer.send(msg);
        } finally {
            if (session != null) { try { session.close(); } catch (JMSException e) { } }
            if (connection != null) { try { connection.close(); } catch (JMSException e) { } }
        }
    }

    private Map<String, String> handleMapMessage(MapMessage msg) throws JMSException {
        Map<String, String> content = new HashMap<>();
        Enumeration names = msg.getMapNames();
        while (names.hasMoreElements()) {
            String name = String.valueOf(names.nextElement());
            content.put(String.valueOf(name), String.valueOf(msg.getObject(name)));
        }

        if (context.isVerbose()) {
            context.logCurrentStep("message received: " + StringUtils.truncate(content.toString(), 500));
        }

        return content;
    }

    private String handleTextMessage(TextMessage msg) throws JMSException {
        String content = msg.getText();
        if (context.isVerbose()) { context.logCurrentStep("message received: " + StringUtils.truncate(content, 500)); }
        return content;
    }
}
