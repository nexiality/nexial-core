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

import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.aws.AwsSesSettings;
import org.nexial.core.utils.ConsoleUtils;

import com.amazonaws.regions.Regions;

import static com.amazonaws.regions.Regions.DEFAULT_REGION;
import static javax.naming.Context.INITIAL_CONTEXT_FACTORY;
import static org.apache.commons.lang3.SystemUtils.USER_NAME;
import static org.nexial.core.NexialConst.AwsSettings.*;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.Mailer.*;
import static org.nexial.core.SystemVariables.getDefault;
import static org.nexial.core.utils.ExecUtils.NEXIAL_MANIFEST;

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
    private static ExecutionMailConfig self;
    private final Map<String, String> configurations = new ConcurrentHashMap<>();

    @NotNull
    public static ExecutionMailConfig configure(ExecutionContext context) {
        if (self == null) {
            self = new ExecutionMailConfig();
            MAILER_KEYS.forEach(key -> {
                if (!isConfigFound(self.configurations, key) && context.hasData(key)) {
                    self.configurations.put(key, context.getStringData(key));
                }
            });
        }

        return self;
    }

    @Nullable
    public static ExecutionMailConfig get() { return self; }

    public boolean isReady() {
        String enableEmail = MapUtils.getString(configurations, ENABLE_EMAIL, getDefault(ENABLE_EMAIL));
        if (!BooleanUtils.toBoolean(enableEmail)) {
            ConsoleUtils.log(NOT_READY_PREFIX + ENABLE_EMAIL + "=" + enableEmail);
            return false;
        }

        // String mailTo = MapUtils.getString(configurations, POST_EXEC_MAIL_TO_OLD);
        String mailTo2 = MapUtils.getString(configurations, POST_EXEC_MAIL_TO);
        // if (StringUtils.isBlank(mailTo) && StringUtils.isBlank(mailTo2)) {
        //     ConsoleUtils.log(NOT_READY_PREFIX +
        //                      POST_EXEC_MAIL_TO_OLD + "=" + mailTo + ", " + POST_EXEC_MAIL_TO + "=" + mailTo2);
        //     return false;
        // }
        if (StringUtils.isBlank(mailTo2)) {
            ConsoleUtils.log(NOT_READY_PREFIX + POST_EXEC_MAIL_TO + "=" + mailTo2);
            return false;
        }

        return isReadyForNotification();
    }

    public boolean isReadyForNotification() {
        // jndi as first priority
        if (isConfigFound(configurations, MAIL_KEY_MAIL_JNDI_URL)) {
            if (!isConfigFound(configurations, INITIAL_CONTEXT_FACTORY)) {
                ConsoleUtils.log(JNDI_NOT_READY);
            } else {
                return true;
            }
        }

        // smtp/imap as second priority
        if (isConfigFound(configurations, MAIL_KEY_MAIL_HOST)) {
            if (!isConfigFound(configurations, MAIL_KEY_PROTOCOL) ||
                !isConfigFound(configurations, MAIL_KEY_MAIL_PORT)) {
                ConsoleUtils.log(SMTP_NOT_READY);
            } else {
                return true;
            }
        }

        // ses as third priority
        if (isConfigFound(configurations, SES_PREFIX + AWS_ACCESS_KEY)) {
            if (!isConfigFound(configurations, SES_PREFIX + AWS_SECRET_KEY) ||
                !isConfigFound(configurations, SES_PREFIX + AWS_SES_FROM)) {
                ConsoleUtils.log(SES_NOT_READY);
            } else {
                return true;
            }
        }

        // can't find required config for JNDI, SMTP, IMAP or SES
        ConsoleUtils.log(MAILER_NOT_READY);
        return false;
    }

    @NotNull
    public Properties toSmtpConfigs() {
        Properties props = new Properties();

        SMTP_KEYS.forEach(key -> {
            if (configurations.containsKey(key)) { props.setProperty(key, configurations.get(key)); }
        });

        props.setProperty(MAIL_KEY_LOCALHOST, EnvUtils.getHostName());

        return props;
    }

    @NotNull
    public AwsSesSettings toSesConfigs() {
        String accessKey = configurations.get(SES_PREFIX + AWS_ACCESS_KEY);
        String secretKey = configurations.get(SES_PREFIX + AWS_SECRET_KEY);
        String from = configurations.get(SES_PREFIX + AWS_SES_FROM);
        if (StringUtils.isBlank(accessKey) || StringUtils.isBlank(secretKey) || StringUtils.isBlank(from)) {
            return null;
        }

        String region = StringUtils.defaultIfBlank(configurations.get(SES_PREFIX + AWS_REGION),
                                                   DEFAULT_REGION.getName());

        AwsSesSettings settings = new AwsSesSettings(accessKey, secretKey, Regions.fromName(region), from);
        settings.setAssumeRoleArn(StringUtils.defaultString(configurations.get(SES_PREFIX + AWS_STS_ROLE_ARN), ""));
        settings.setAssumeRoleSession(StringUtils.defaultString(configurations.get(SES_PREFIX + AWS_STS_ROLE_SESSION),
                                                                ""));
        settings.setAssumeRoleDuration(
            NumberUtils
                .toInt(StringUtils.defaultIfBlank(configurations.get(SES_PREFIX + AWS_STS_ROLE_DURATION), "900")));
        settings.setReplyTo(StringUtils.defaultString(configurations.get(SES_PREFIX + AWS_SES_REPLY_TO), ""));
        settings.setCc(StringUtils.defaultString(configurations.get(SES_PREFIX + AWS_SES_CC), ""));
        settings.setBcc(StringUtils.defaultString(configurations.get(SES_PREFIX + AWS_SES_BCC), ""));
        settings.setConfigurationSetName(StringUtils.defaultString(configurations.get(SES_PREFIX + AWS_SES_CONFIG_SET),
                                                                   ""));
        settings.setXmailer(
            StringUtils.defaultIfBlank(configurations.get(SES_PREFIX + AWS_XMAILER),
                                       NEXIAL_MANIFEST + "/" + USER_NAME + "@" + EnvUtils.getHostName()));

        return settings;
    }

    @NotNull
    public Hashtable toJndiEnv() {
        Hashtable env = new Hashtable();
        JNDI_KEYS.forEach(key -> { if (configurations.containsKey(key)) { env.put(key, configurations.get(key)); }});
        return env;
    }

    @Nullable
    public List<String> getRecipients() {
        // String recipients = configurations.get(POST_EXEC_MAIL_TO_OLD);
        // if (StringUtils.isBlank(recipients)) { recipients = configurations.get(POST_EXEC_MAIL_TO); }
        String recipients = configurations.get(POST_EXEC_MAIL_TO);
        if (StringUtils.isBlank(recipients)) { return null; }
        return TextUtils.toList(StringUtils.replace(recipients, ";", ","), ",", true);
    }

    @Nullable
    public String getCustomMailSubject() { return configurations.get(POST_EXEC_EMAIL_SUBJECT); }

    @Nullable
    public String getCustomMailHeader() { return configurations.get(POST_EXEC_EMAIL_HEADER); }

    @Nullable
    public String getCustomMailFooter() { return configurations.get(POST_EXEC_EMAIL_FOOTER); }

    protected static boolean isConfigFound(Map<String, String> config, String key) {
        return StringUtils.isNotBlank(MapUtils.getString(config, key));
    }
}
