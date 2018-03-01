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

import java.util.Map;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.commons.lang3.StringUtils;

import static javax.naming.Context.*;

public class JmsJndiClientConfig extends JmsClientConfig {
    // url in the form of jnp://[host]:1099
    // jndi://ConnectionFactory
    private String connectionFactory;
    private String initialContextFactory;
    private String jndiUsername;
    private String jndiPassword;

    public void setConnectionFactory(String connectionFactory) { this.connectionFactory = connectionFactory; }

    @Override
    public String toString() { return super.toString() + ", connectionFactory=" + connectionFactory;}

    @Override
    public void init(Map<String, String> config) {
        super.init(config);
        initialContextFactory = config.get("initialContextFactory");
        connectionFactory = config.get("connectionFactory");
        if (StringUtils.isNotBlank(connectionFactory)) {
            throw new IllegalArgumentException("JNDI reference for ConnectionFactory must be specified");
        }
        jndiUsername = config.get("jndiUsername");
        jndiPassword = config.get("jndiPassword");
    }

    @Override
    public Connection createConnection() throws JMSException {

        Properties env = new Properties();
        env.put(PROVIDER_URL, url);
        if (StringUtils.isNotBlank(jndiUsername)) { env.put(SECURITY_PRINCIPAL, jndiUsername); }
        if (StringUtils.isNotBlank(jndiPassword)) { env.put(SECURITY_CREDENTIALS, jndiPassword); }
        if (StringUtils.isNotBlank(initialContextFactory)) {
            env.put(INITIAL_CONTEXT_FACTORY, initialContextFactory);
        }

        try {
            InitialContext jndi = new InitialContext(env);
            ConnectionFactory connFactory = (ConnectionFactory) jndi.lookup(connectionFactory);
            return createConnection(connFactory);
        } catch (NamingException e) {
            throw new JMSException("Unable to create connection via JNDI: " + e.getMessage());
        }
    }

    @Override
    public Destination resolveDestination(Session session) throws JMSException {
        return isTopic ? session.createTopic(destination) : session.createQueue(destination);
    }
}
