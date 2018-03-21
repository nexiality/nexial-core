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

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;

public class WebsphereMQJmsClientConfig extends JmsClientConfig {
    private String channel;
    private String queueManager;

    @Override
    public void init(Map<String, String> config) {
        super.init(config);
        this.channel = config.get("channel");
        this.queueManager = config.get("queueManager");
    }

    @Override
    public Connection createConnection() throws JMSException {
        // MQConnectionFactory cf = new MQConnectionFactory();
        // cf.setTransportType(WMQ_CM_CLIENT);
        //
        // if (StringUtils.contains(url, ",")) {
        //     cf.setConnectionNameList(url);
        // } else {
        //     if (StringUtils.contains(url, ":")) {
        //         String host = StringUtils.substringBefore(url, ":");
        //         String port = StringUtils.substringAfter(url, ":");
        //         ConsoleUtils.log("Using host '" + host + "' and port '" + port + "' to connect to WebSphere MQ");
        //         cf.setHostName(host);
        //         cf.setPort(NumberUtils.toInt(port));
        //     }
        // }
        //
        // cf.setChannel(channel);
        // cf.setQueueManager(queueManager);

        ConnectionFactory cf;
        try {
            cf = (ConnectionFactory) Class.forName("com.ibm.mq.jms.MQConnectionFactory").newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassCastException | ClassNotFoundException e) {
            String message = driverInfo != null ?
                             driverInfo.toString() :
                             "Fail to load requested JMS connector.  Make sure the appropriate jar is added to lib/.";
            ConsoleUtils.showMissingLibraryError(message);
            ExecutionContext context = ExecutionThread.get();
            if (context != null) { context.setFailImmediate(true); }
            throw new RuntimeException(message);
        }

        try {
            // com.ibm.msg.client.wmq.common.CommonConstants.WMQ_CM_CLIENT
            BeanUtils.setProperty(cf, "transportType", 1);

            if (StringUtils.contains(url, ",")) {
                BeanUtils.setProperty(cf, "connectionNameList", url);
            } else {
                if (StringUtils.contains(url, ":")) {
                    String host = StringUtils.substringBefore(url, ":");
                    String port = StringUtils.substringAfter(url, ":");
                    ConsoleUtils.log("Using host '" + host + "' and port '" + port + "' to connect to WebSphere MQ");
                    BeanUtils.setProperty(cf, "hostName", host);
                    BeanUtils.setProperty(cf, "port", NumberUtils.toInt(port));
                }
            }

            BeanUtils.setProperty(cf, "channel", channel);
            BeanUtils.setProperty(cf, "queueManager", queueManager);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new JMSException("Unable to configure connection: " + e.getMessage());
        }

        return createConnection(cf);
    }

    @Override
    public Destination resolveDestination(Session session) throws JMSException {
        return isTopic ? session.createTopic(destination) : session.createQueue(destination);
    }
}
