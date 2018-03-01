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

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;

import static org.nexial.core.NexialConst.MS_UNDEFINED;
import static org.nexial.core.utils.CheckUtils.*;

public class JmsCommand extends BaseCommand {
    private JmsClient jmsClient;
    private Map<String, String> jmsClientConfigs;

    @Override
    public void init(ExecutionContext context) {
        super.init(context);
        jmsClient.setContext(context);
    }

    @Override
    public String getTarget() { return "jms"; }

    public void setJmsClient(JmsClient jmsClient) { this.jmsClient = jmsClient;}

    public void setJmsClientConfigs(Map<String, String> jmsClientConfigs) { this.jmsClientConfigs = jmsClientConfigs; }

    public StepResult sendText(String config, String id, String payload) {
        requiresNotBlank(config, "Invalid config", config);
        requiresNotBlank(id, "Invalid message ID", id);
        requiresNotBlank(payload, "Invalid payload", payload);
        return send(config, id, payload);
    }

    public StepResult sendMap(String config, String id, String payload) {
        requiresNotBlank(config, "Invalid config", config);
        requiresNotBlank(id, "Invalid message ID", id);
        requiresNotBlank(payload, "Invalid payload", payload);
        if (!StringUtils.contains(payload, "=")) {
            fail("Invalid payload for map message; payload must be in name=value form");
        }

        String delim = context.getTextDelim();
        payload = StringUtils.replace(payload, "\r\n", delim);
        payload = StringUtils.replace(payload, "\n", delim);
        Map<String, String> map = TextUtils.toMap(payload, delim, "=");
        return send(config, id, map);
    }

    public StepResult receive(String var, String config, String waitMs) {
        requiresValidVariableName(var);
        requiresNotBlank(config, "Invalid config", config);
        long timeout = NumberUtils.isDigits(waitMs) ? NumberUtils.toInt(waitMs) : MS_UNDEFINED;

        try {
            Object received = jmsClient.receive(resolveJmsClientConfig(config), timeout);
            if (received != null) {
                context.setData(var, received);
                return StepResult.success("message received and save to variable '" + var + "'");
            } else {
                context.removeData(var);
                return StepResult.success("No message to receive");
            }
        } catch (Throwable e) {
            return StepResult.fail("message FAILED to sent due to " + e.getMessage());
        }
    }

    protected StepResult send(String config, String id, Object payload) {
        try {
            jmsClient.sendObject(resolveJmsClientConfig(config), id, payload);
            return StepResult.success("message sent successfully");
        } catch (Throwable e) {
            return StepResult.fail("message FAILED to sent due to " + e.getMessage());
        }
    }

    protected JmsClientConfig resolveJmsClientConfig(String config) {
        Map<String, String> configData = context.getDataByPrefix(StringUtils.appendIfMissing(config, "."));
        if (MapUtils.isEmpty(configData)) { fail("Invalid config; no JMS settings found: " + config); }

        String provider = configData.get("provider");
        if (MapUtils.isEmpty(jmsClientConfigs) && !jmsClientConfigs.containsKey(provider)) {
            throw new IllegalArgumentException("JMS provider '" + provider + "' currently NOT SUPPORTED");
        }

        String configClass = null;
        try {
            configClass = jmsClientConfigs.get(provider);
            Object configObject = Class.forName(configClass).newInstance();
            if (configObject instanceof JmsClientConfig) {
                JmsClientConfig configInstance = (JmsClientConfig) configObject;
                configInstance.init(configData);
                return configInstance;
            }

            throw new IllegalArgumentException("JMS provider '" + provider + "' via '" + configClass +
                                               "' currently NOT SUPPORTED");
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new IllegalArgumentException("JMS provider '" + provider + "' via '" + configClass +
                                               "' cannot be loaded: " + e.getMessage());
        }

    }

}
