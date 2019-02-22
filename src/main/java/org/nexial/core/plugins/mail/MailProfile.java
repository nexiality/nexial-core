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

package org.nexial.core.plugins.mail;

import java.util.Map;
import java.util.Properties;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.CheckUtils;

import static org.nexial.core.NexialConst.Data.MIME_HTML;
import static org.nexial.core.NexialConst.Mailer.*;
import static org.nexial.core.utils.CheckUtils.requiresNotBlank;
import static org.nexial.core.utils.CheckUtils.requiresNotNull;

class MailProfile {
    private String host;
    private int port = 25;
    private boolean auth;
    private String username;
    private String password;
    private String from;
    private boolean tls;
    private String protocol = "smtp";
    private String contentType = MIME_HTML;
    private int bufferSize = 512;

    public String getHost() { return host;}

    public int getPort() { return port;}

    public boolean isAuth() { return auth;}

    public String getUsername() { return username;}

    public String getPassword() { return password;}

    public String getFrom() { return from;}

    public boolean isTls() { return tls;}

    public String getProtocol() { return protocol;}

    public String getContentType() { return contentType;}

    public int getBufferSize() { return bufferSize;}

    public Properties toProperties() {
        Properties properties = new Properties();
        properties.setProperty(MAIL_KEY_MAIL_HOST, host);
        properties.setProperty(MAIL_KEY_MAIL_PORT, port + "");
        properties.setProperty(MAIL_KEY_AUTH, auth + "");
        properties.setProperty(MAIL_KEY_USERNAME, StringUtils.defaultString(username));
        properties.setProperty(MAIL_KEY_PASSWORD, StringUtils.defaultString(password));
        properties.setProperty(MAIL_KEY_PROTOCOL, protocol);
        properties.setProperty(MAIL_KEY_BUFF_SIZE, bufferSize + "");
        properties.setProperty(MAIL_KEY_TLS_ENABLE, tls + "");
        properties.setProperty(MAIL_KEY_CONTENT_TYPE, contentType);
        return properties;
    }

    static MailProfile newInstance(String profile) {
        requiresNotBlank(profile, "Invalid profile", profile);

        ExecutionContext context = ExecutionThread.get();
        requiresNotNull(context, "Unable to obtain execution context");

        Map<String, String> config = context.getDataByPrefix(profile + ".");
        if (MapUtils.isEmpty(config)) {
            context.logCurrentStep("No mail configuration found for '" + profile + "'; Unable to connect.");
            return null;
        }

        MailProfile settings = new MailProfile();
        if (config.containsKey("host")) { settings.host = config.get("host"); }
        if (config.containsKey("port")) { settings.port = NumberUtils.toInt(config.get("port")); }
        if (config.containsKey("auth")) { settings.auth = BooleanUtils.toBoolean(config.get("auth")); }
        if (settings.auth) {
            if (config.containsKey("username")) { settings.username = config.get("username"); }
            if (config.containsKey("password")) { settings.password = config.get("password"); }
            if (StringUtils.isBlank(settings.username) || StringUtils.isBlank(settings.password)) {
                CheckUtils.fail("SMTP auth is enabled but username and/or password is not specified");
            }
        }

        if (config.containsKey("from")) { settings.from = config.get("from"); }
        if (config.containsKey("tls")) { settings.tls = BooleanUtils.toBoolean(config.get("tls")); }
        if (config.containsKey("protocol")) { settings.protocol = config.get("protocol"); }
        if (config.containsKey("contentType")) { settings.contentType = config.get("contentType"); }
        if (config.containsKey("bufferSize")) { settings.bufferSize = NumberUtils.toInt(config.get("bufferSize")); }

        return settings;
    }
}
