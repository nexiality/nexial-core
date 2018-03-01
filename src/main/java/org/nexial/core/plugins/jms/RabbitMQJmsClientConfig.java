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

package org.nexial.core.plugins.jms;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.commons.lang3.StringUtils;

import com.rabbitmq.jms.admin.RMQConnectionFactory;
import com.rabbitmq.jms.admin.RMQDestination;

public class RabbitMQJmsClientConfig extends JmsClientConfig {
    @Override
    public Connection createConnection() throws JMSException {
        RMQConnectionFactory connectionFactory = new RMQConnectionFactory();
        connectionFactory.setUri(url);
        if (StringUtils.isNotBlank(username)) { connectionFactory.setUsername(username); }
        if (StringUtils.isNotBlank(password)) { connectionFactory.setPassword(password); }

        String vHost = StringUtils.trim(StringUtils.substringAfter(StringUtils.substringAfter(url, "://"), "/"));
        if (StringUtils.isBlank(vHost)) { vHost = "/"; }
        connectionFactory.setVirtualHost(vHost);

        Connection connection = connectionFactory.createConnection();
        connection.start();
        return connection;
    }

    @Override
    public Destination resolveDestination(Session session) { return new RMQDestination(destination, !isTopic, false); }
}