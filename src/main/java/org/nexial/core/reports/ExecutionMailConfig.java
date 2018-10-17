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
 */

package org.nexial.core.reports;

import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;

import static javax.naming.Context.*;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.*;

/**
 * central object to resolve and avail mail-related configuration for the purpose of sending execution-level report
 * via email (aka nexial email notification)
 *
 * This object respects the general design of data overrides, where:
 * <ol>
 * <li>System properties has the highest level of impact where no overrides will occur</li>
 * <li>project.properties can add to what's missing in System properties, but not override them</li>
 * <li>data variables in data sheets can enhance the 2 above, but not override either</li>
 * </ol>
 *
 * It is possible during parallel execution that some data variables found in data sheets might override each other
 * unexpectedly.  Hence it is ALWAYS a good idea to push as much as possible such configuration to System properties
 * (i.e. -D...) or to project.properties.
 */
public class ExecutionMailConfig {

    // standalone smtp config?
    private static final List<String> MAIL_CONIG_KEYS = Arrays.asList(
        MAIL_KEY_BUFF_SIZE, MAIL_KEY_PROTOCOL, MAIL_KEY_MAIL_HOST, MAIL_KEY_MAIL_PORT, MAIL_KEY_TLS_ENABLE,
        MAIL_KEY_AUTH, MAIL_KEY_DEBUG, MAIL_KEY_CONTENT_TYPE, MAIL_KEY_USERNAME, MAIL_KEY_PASSWORD, OPT_MAIL_FROM,
        OPT_MAIL_CC, OPT_MAIL_BCC, OPT_MAIL_XMAILER);

    // jndi smtp config?
    private static final List<String> JNDI_CONFIG_KEY = Arrays.asList(
        MAIL_KEY_MAIL_JNDI_URL, INITIAL_CONTEXT_FACTORY, OBJECT_FACTORIES, STATE_FACTORIES,
        URL_PKG_PREFIXES, PROVIDER_URL, DNS_URL, AUTHORITATIVE, BATCHSIZE, REFERRAL, SECURITY_PROTOCOL,
        SECURITY_AUTHENTICATION, SECURITY_PRINCIPAL, SECURITY_CREDENTIALS, LANGUAGE);

    // enable for email notification?
    private static final List<String> CONFIG_KEYS =
        ListUtils.sum(ListUtils.sum(Arrays.asList(ENABLE_EMAIL, MAIL_TO, MAIL_TO2), MAIL_CONIG_KEYS), JNDI_CONFIG_KEY);

    private static ExecutionMailConfig self;

    private Map<String, String> configurations = new ConcurrentHashMap<>();

    public static ExecutionMailConfig configure(ExecutionContext context) {
        if (self == null) { self = new ExecutionMailConfig(); }

        Optional.ofNullable(self.getConfigKeys()).orElse(new ArrayList<>()).forEach(key -> {
            if (!self.configurations.containsKey(key) && StringUtils.isNotBlank(context.getStringData(key))) {
                self.configurations.put(key, context.getStringData(key));
            }
        });

        return self;
    }

    // might generate new instance of `ExecutionMailConfig` to avoid NPE
    public static ExecutionMailConfig get() { return self == null ? new ExecutionMailConfig() : self; }

    public boolean isReady() {
        if (!BooleanUtils.toBoolean(MapUtils.getString(configurations, ENABLE_EMAIL, DEF_ENABLE_EMAIL))) {
            return false;
        }

        if (StringUtils.isBlank(MapUtils.getString(configurations, MAIL_TO)) &&
            StringUtils.isBlank(MapUtils.getString(configurations, MAIL_TO2))) { return false; }

        return isReadyForNotification();
    }

    public boolean isReadyForNotification() {
        if (StringUtils.isNotBlank(MapUtils.getString(configurations, MAIL_KEY_MAIL_JNDI_URL))) {
            return StringUtils.isNotBlank(MapUtils.getString(configurations, INITIAL_CONTEXT_FACTORY));
        }

        return StringUtils.isNotBlank(MapUtils.getString(configurations, MAIL_KEY_PROTOCOL)) &&
               StringUtils.isNotBlank(MapUtils.getString(configurations, MAIL_KEY_MAIL_HOST)) &&
               StringUtils.isNotBlank(MapUtils.getString(configurations, MAIL_KEY_MAIL_PORT));
    }

    public Properties toMailProperties() {
        Properties props = new Properties();
        MAIL_CONIG_KEYS.forEach(key -> {
            if (configurations.containsKey(key)) { props.setProperty(key, configurations.get(key)); }
        });

        try {
            props.setProperty(MAIL_KEY_SMTP_LOCALHOST, EnvUtils.getHostName());
        } catch (UnknownHostException e) {
            ConsoleUtils.log("Unable to query localhost's hostname, setting '" + MAIL_KEY_SMTP_LOCALHOST +
                             "' to 'localhost', but it probably won't work. " + e.getMessage());
            props.setProperty(MAIL_KEY_SMTP_LOCALHOST, "localhost");
        }

        return props;
    }

    public Hashtable toJndiEnv() {
        Hashtable env = new Hashtable();
        JNDI_CONFIG_KEY.forEach(key -> {
            if (configurations.containsKey(key)) { env.put(key, configurations.get(key)); }
        });
        return env;
    }

    public String[] getRecipients() {
        String recipients = configurations.get(MAIL_TO);
        if (StringUtils.isBlank(recipients)) { recipients = configurations.get(MAIL_TO2); }
        if (StringUtils.isBlank(recipients)) { return null; }
        return StringUtils.split(StringUtils.replace(recipients, ";", ","), ",");
    }

    protected List<String> getConfigKeys() { return CONFIG_KEYS; }
}
