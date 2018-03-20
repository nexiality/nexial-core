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

import java.io.Serializable;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nexial.core.plugins.ThirdPartyDriverInfo;

import static org.apache.commons.lang3.builder.ToStringStyle.SIMPLE_STYLE;

public abstract class JmsClientConfig implements Serializable {

    protected String provider;
    protected String url;
    protected String username;
    protected String password;
    protected String destination;
    protected boolean isTopic;
    protected ThirdPartyDriverInfo driverInfo;

    public String getUrl() { return url;}

    public void setUrl(String url) { this.url = url;}

    public String getDestination() { return destination;}

    public void setDestination(String destination) { this.destination = destination;}

    public String getUsername() { return username;}

    public void setUsername(String username) { this.username = username;}

    public String getPassword() { return password;}

    public void setPassword(String password) { this.password = password;}

    public String getProvider() { return provider;}

    public void setProvider(String provider) { this.provider = provider;}

    public boolean isTopic() { return isTopic;}

    public void setTopic(boolean topic) { isTopic = topic;}

    public ThirdPartyDriverInfo getDriverInfo() { return driverInfo; }

    public void setDriverInfo(ThirdPartyDriverInfo driverInfo) { this.driverInfo = driverInfo; }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(url).append(destination).toHashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        JmsClientConfig jmsClientConfig = (JmsClientConfig) o;
        return new EqualsBuilder().append(url, jmsClientConfig.url)
                                  .append(destination, jmsClientConfig.destination)
                                  .isEquals();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SIMPLE_STYLE)
                   .append("url", url)
                   .append("username", username)
                   .append("password", password)
                   .append("destination", destination)
                   .append("provider", provider)
                   .toString();
    }

    public void init(Map<String, String> config) {
        if (MapUtils.isEmpty(config)) { throw new IllegalArgumentException("No JMS settings specified");}

        provider = config.get("provider");

        url = config.get("url");
        if (StringUtils.isBlank(url)) { throw new IllegalArgumentException("JMS url not specified"); }

        destination = config.get("destination");
        if (StringUtils.isBlank(destination)) { throw new IllegalArgumentException("JMS destination not specified"); }

        username = config.get("username");
        password = config.get("password");
        isTopic = BooleanUtils.toBoolean(config.get("isTopic"));
    }

    public abstract Connection createConnection() throws JMSException;

    public abstract Destination resolveDestination(Session session) throws JMSException;

    protected Connection createConnection(ConnectionFactory connFactory) throws JMSException {
        Connection connection;
        if (StringUtils.isNotBlank(username)) {
            if (StringUtils.isNotBlank(password)) {
                connection = connFactory.createConnection(username, password);
            } else {
                connection = connFactory.createConnection(username, null);
            }
        } else {
            connection = connFactory.createConnection();
        }

        connection.start();
        return connection;
    }
}
