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

package org.nexial.core.plugins.ssh;

import java.io.File;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.IntegrationConfigException;
import org.nexial.core.NexialConst.Ssh;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.Ssh.*;

public class SshClientConnection {
    private String username;
    private String password;
    private String host;
    private int port;
    private boolean strictHostKeyChecking;
    private File knownHostsFile;

    public static SshClientConnection resolveFrom(ExecutionContext context, String profile)
        throws IntegrationConfigException {
        if (context == null) { return null; }
        if (StringUtils.isBlank(profile)) { return null; }

        String prefix = SSH_CLIENT_PREFIX + profile;
        if (context.hasData(prefix)) {
            Object obj = context.getObjectData(prefix);
            if (obj instanceof SshClientConnection) {
                ConsoleUtils.log("reusing established SSH connection '" + profile + "'");
                return (SshClientConnection) obj;
            }

            // nope.. wrong type - toss it away
            context.removeData(prefix);
        }

        String prefix1 = profile + ".";
        Map<String, String> config = context.getDataByPrefix(prefix1);
        if (MapUtils.isEmpty(config)) {
            // retry with backward compatible naming scheme (ie. nexial.ssh.[PROFILE].*)
            prefix1 = prefix + ".";
            config = context.getDataByPrefix(prefix1);
            if (MapUtils.isEmpty(config)) {
                context.logCurrentStep("No connection configuration found for '" + profile + "'; Unable to connect.");
                return null;
            }
        }

        String username = config.get(SSH_USERNAME);
        if (StringUtils.isBlank(username)) { throw IntegrationConfigException.missingConfig(prefix1 + SSH_USERNAME); }

        String password = config.get(SSH_PASSWORD);
        // password is not necessarily required
        // if (StringUtils.isBlank(password)) { throw IntegrationConfigException.missingConfig(prefix1 + SSH_PASSWORD); }

        String host = config.get(SSH_HOST);
        if (StringUtils.isBlank(host)) { throw IntegrationConfigException.missingConfig(prefix1 + SSH_HOST); }

        int port = NumberUtils.toInt(config.getOrDefault(SSH_PORT, DEF_SSH_PORT));

        boolean strictHostKeyChecking = BooleanUtils.toBoolean(config.getOrDefault(SSH_HOST_KEY_CHECK, "false"));

        String knownHostsFile = config.get(SSH_KNOWN_HOSTS);

        SshClientConnection connection = new SshClientConnection();
        connection.username = username;
        connection.password = password;
        connection.host = host;
        connection.port = port;
        connection.strictHostKeyChecking = strictHostKeyChecking;
        if (FileUtil.isFileReadable(knownHostsFile, 15)) { connection.knownHostsFile = new File(knownHostsFile); }

        context.setData(prefix, connection);
        return connection;
    }

    public String getUsername() { return username; }

    public String getPassword() { return password; }

    public String getHost() { return host; }

    public int getPort() { return port; }

    public boolean isStrictHostKeyChecking() { return strictHostKeyChecking; }

    public File getKnownHostsFile() { return knownHostsFile; }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                   .append("host", host)
                   .append("port", port)
                   .append("username", username)
                   .append("knownHostsFile", knownHostsFile)
                   .append("strictHostKeyChecking", strictHostKeyChecking)
                   .toString();
    }
}
