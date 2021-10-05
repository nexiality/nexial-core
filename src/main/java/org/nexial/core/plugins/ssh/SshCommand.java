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

package org.nexial.core.plugins.ssh;

import com.jcraft.jsch.*;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.IntegrationConfigException;
import org.nexial.core.model.RemoteFileActionOutcome;
import org.nexial.core.model.RemoteFileActionOutcome.TransferAction;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.ConsoleUtils;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static com.jcraft.jsch.ChannelSftp.*;
import static java.io.File.separator;
import static org.nexial.core.model.RemoteFileActionOutcome.TransferAction.*;
import static org.nexial.core.model.RemoteFileActionOutcome.TransferProtocol.SCP;
import static org.nexial.core.utils.CheckUtils.*;

public class SshCommand extends BaseCommand {
    public static final String MSG_MOVED_SUCCESS = "[%s] moved (sftp) to %s - SUCCESS";
    public static final String MSG_COPIED_SUCCESS = "[%s] copied (sftp) to %s - SUCCESS";
    public static final String MSG_LIST = "Successfully list files from (sftp) %s";
    public static final String MSG_DELETE = "Successfully list delete from (sftp) %s";

    @Override
    public String getTarget() { return "ssh"; }

    public StepResult sftpCopyFrom(String var, String profile, String remote, String local)
        throws IntegrationConfigException {

        requiresValidVariableName(var);
        requiresNotBlank(profile, "Invalid profile", profile);
        RemoteFileActionOutcome outcome =
            new SftpWorker(COPY_FROM, remote, local).doAction(resolveSshClientConnection(profile));
        context.setData(var, outcome);

        if (outcome.hasError()) {
            return StepResult.fail(outcome.getErrors());
        } else {
            return StepResult.success(String.format(MSG_COPIED_SUCCESS, remote, local));
        }
    }

    public StepResult sftpCopyTo(String var, String profile, String local, String remote)
        throws IntegrationConfigException {

        requiresValidVariableName(var);
        requiresNotBlank(profile, "Invalid profile", profile);
        RemoteFileActionOutcome outcome =
            new SftpWorker(COPY_TO, remote, local).doAction(resolveSshClientConnection(profile));
        context.setData(var, outcome);

        if (outcome.hasError()) {
            return StepResult.fail(outcome.getErrors());
        } else {
            return StepResult.success(String.format(MSG_COPIED_SUCCESS, local, remote));
        }
    }

    public StepResult sftpMoveFrom(String var, String profile, String remote, String local)
        throws IntegrationConfigException {

        requiresValidVariableName(var);
        requiresNotBlank(profile, "Invalid profile", profile);
        RemoteFileActionOutcome outcome =
            new SftpWorker(MOVE_FROM, remote, local).doAction(resolveSshClientConnection(profile));
        context.setData(var, outcome);

        if (outcome.hasError()) {
            return StepResult.fail(outcome.getErrors());
        } else {
            return StepResult.success(String.format(MSG_MOVED_SUCCESS, remote, local));
        }
    }

    public StepResult sftpMoveTo(String var, String profile, String local, String remote)
        throws IntegrationConfigException {

        requiresValidVariableName(var);
        requiresNotBlank(profile, "Invalid profile", profile);
        RemoteFileActionOutcome outcome =
            new SftpWorker(MOVE_TO, remote, local).doAction(resolveSshClientConnection(profile));
        context.setData(var, outcome);

        if (outcome.hasError()) {
            return StepResult.fail(outcome.getErrors());
        } else {
            return StepResult.success(String.format(MSG_MOVED_SUCCESS, local, remote));
        }
    }

    public StepResult sftpList(String var, String profile, String remote) throws IntegrationConfigException {

        requiresValidVariableName(var);
        requiresNotBlank(profile, "Invalid profile", profile);
        RemoteFileActionOutcome outcome =
            new SftpWorker(LIST, remote, null).doAction(resolveSshClientConnection(profile));
        context.setData(var, outcome);

        if (outcome.hasError()) {
            return StepResult.fail(outcome.getErrors());
        } else {
            return StepResult.success(String.format(MSG_LIST, remote));
        }
    }

    public StepResult sftpDelete(String var, String profile, String remote) throws IntegrationConfigException {

        requiresValidVariableName(var);
        requiresNotBlank(profile, "Invalid profile", profile);
        RemoteFileActionOutcome outcome =
            new SftpWorker(DELETE, remote, null).doAction(resolveSshClientConnection(profile));
        context.setData(var, outcome);

        if (outcome.hasError()) {
            return StepResult.fail(outcome.getErrors());
        } else {
            return StepResult.success(String.format(MSG_DELETE, remote));
        }
    }

    public StepResult scpCopyFrom(String var, String profile, String remote, String local)
        throws IntegrationConfigException {
        sanityCheck(var, profile, remote, local);

        RemoteFileActionOutcome outcome = new RemoteFileActionOutcome().setProtocol(SCP)
                                                                       .setAction(COPY_FROM)
                                                                       .setLocalPath(local)
                                                                       .setRemotePath(remote);

        StepResult stepResult = preRemoteActionChecks(outcome);
        if (stepResult != null) { return stepResult; }

        remote = outcome.getRemotePath();
        local = outcome.getLocalPath();

        SshClientConnection connection = resolveSshClientConnection(profile);

        Session session = null;
        ChannelExec channel = null;

        try {
            session = connect(connection);
            ScpHelper.doScpCopyFrom(session, remote, local);
            return succeedSingleFile(var, local, outcome, "copy from " + remote + " to " + local);
        } catch (JSchException | IOException e) {
            return failSingleFile(var, remote, outcome, e);
        } finally {
            closeSshClient(session, channel);
        }
    }

    public StepResult scpCopyTo(String var, String profile, String local, String remote)
        throws IntegrationConfigException {
        sanityCheck(var, profile, remote, local);

        RemoteFileActionOutcome outcome = new RemoteFileActionOutcome().setProtocol(SCP)
                                                                       .setAction(COPY_TO)
                                                                       .setLocalPath(local)
                                                                       .setRemotePath(remote);

        StepResult stepResult = preRemoteActionChecks(outcome);
        if (stepResult != null) { return stepResult; }

        remote = outcome.getRemotePath();
        local = outcome.getLocalPath();

        SshClientConnection connection = resolveSshClientConnection(profile);

        Session session = null;
        ChannelExec channel = null;

        try {
            session = connect(connection);
            ScpHelper.doScpCopyTo(session, local, remote);
            return succeedSingleFile(var, local, outcome, "copy from " + local + " to " + remote);
        } catch (JSchException | IOException e) {
            return failSingleFile(var, remote, outcome, e);
        } finally {
            closeSshClient(session, channel);
        }
    }

    // todo: should be protected / move test class to same package
    public static StepResult preRemoteActionChecks(RemoteFileActionOutcome outcome) {
        TransferAction action = outcome.getAction();
        String logPrefix = outcome.getProtocol() + ":" + action + " - ";

        String localPath = outcome.getLocalPath();
        boolean localFileReadable = FileUtil.isFileReadable(localPath);
        boolean localDirectory = FileUtil.isDirectoryReadable(localPath);

        String remotePath = outcome.getRemotePath();

        StepResult result;
        if (action == COPY_FROM || action == MOVE_FROM) {
            // so we are copying from remote to local
            if ((result = requireValidRemotePath(remotePath, false, false)) != null) { return result; }

            if ((result = requireValidLocalPath(localPath, true, false)) != null) { return result; }

            if (localDirectory) {
                String filename = FileUtil.extractFilename(remotePath);
                if (StringUtils.isBlank(filename)) {
                    // uh uh.. we don't support directory copy yet!
                    return StepResult.fail("remote path " + remotePath +
                                           " is a directory, but directory copy/move is currently UNSUPPORTED");
                }

                outcome.setLocalPath(StringUtils.appendIfMissing(localPath, separator) + filename);
            } else if (localFileReadable) {
                // if not, we'd assume this as file
                ConsoleUtils.log(logPrefix + "local file " + localPath + " will be OVERWRITTEN");
            }

            return null;
        }

        if (action == COPY_TO || action == MOVE_TO) {
            // so we are copy from local to remote
            if ((result = requireValidRemotePath(remotePath, true, false)) != null) { return result; }

            if ((result = requireValidLocalPath(localPath, false, false)) != null) { return result; }

            if (!localFileReadable) { return StepResult.fail("local path " + localPath + " is not readable"); }

            if (StringUtils.endsWithAny(remotePath, "\\", "/")) {
                String filename = FileUtil.extractFilename(localPath);
                outcome.setRemotePath(StringUtils.appendIfMissing(remotePath, "/") + filename);
            } else {
                ConsoleUtils.log(logPrefix + "remote file " + remotePath + " will be OVERWRITTEN");
            }

            return null;
        }

        if (action == LIST || action == DELETE) {
            if ((result = requireValidRemotePath(remotePath, true, true)) != null) { return result; }
            outcome.setLocalPath(null);
            return null;
        }

        return null;
    }

    protected ChannelExec openExecChannel(Session session) throws JSchException {
        if (session == null || !session.isConnected()) { throw new JSchException("session is invalid or disconnected");}

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.connect();
        return channel;
    }

    protected StepResult succeedSingleFile(String var, String file, RemoteFileActionOutcome outcome, String message) {
        ConsoleUtils.log(resolveLogPrefix(outcome) + (StringUtils.isNotBlank(file) ? "[" + file + "]" : "") +
                         " " + message + " - SUCCESS");
        context.setData(var, outcome.addAffected(file).end());
        return StepResult.success(message);
    }

    protected StepResult failSingleFile(String var, String file, RemoteFileActionOutcome outcome, String message) {
        ConsoleUtils.error(resolveLogPrefix(outcome) + (StringUtils.isNotBlank(file) ? "[" + file + "]" : "") +
                           " FAIL: " + message);
        context.setData(var, outcome.addFailed(file).appendError(message).end());
        return StepResult.fail(message);
    }

    protected StepResult failSingleFile(String var, String file, RemoteFileActionOutcome outcome, Exception e) {
        ConsoleUtils.error(context.getCurrentTestStep().showPosition(), "connection failed", e);
        String message = e.getMessage();
        if (e instanceof SftpException) {
            int codeRootCause = ((SftpException) e).id;
            switch (codeRootCause) {
                case SSH_FX_NO_SUCH_FILE: {
                    message = "Remote file does not exist";
                    break;
                }
                case SSH_FX_PERMISSION_DENIED: {
                    message = "Permission denied to operate on the specified remote file";
                    break;
                }
                case SSH_FX_NO_CONNECTION: {
                    message = "No connection to remote server is established";
                    break;
                }
                case SSH_FX_CONNECTION_LOST: {
                    message = "Connection to the remote server is lost";
                    break;
                }
                case SSH_FX_EOF: {
                    message = "Error reading the specified remote file";
                    break;
                }
                case SSH_FX_OP_UNSUPPORTED: {
                    message = "The specified operation is not supported on the target remote server";
                    break;
                }
            }
        }

        return failSingleFile(var, file, outcome, message);
    }

    protected void closeSshClient(Session session, Channel channel) {
        if (channel != null) {
            if (channel instanceof ChannelSftp) { ((ChannelSftp) channel).exit(); }
            channel.disconnect();
        }

        if (session != null) { session.disconnect(); }
    }

    protected String resolveLogPrefix(RemoteFileActionOutcome outcome) {
        return outcome.getProtocol() + ":" + outcome.getAction() + " - ";
    }

    protected Session connect(SshClientConnection connection) throws JSchException {
        JSch ssh = new JSch();

        File knownHostsFile = connection.getKnownHostsFile();
        if (knownHostsFile != null) { ssh.setKnownHosts(knownHostsFile.getAbsolutePath()); }

        Session session = ssh.getSession(connection.getUsername(), connection.getHost(), connection.getPort());

        Properties config = new Properties();
        if (!connection.isStrictHostKeyChecking()) { config.setProperty("StrictHostKeyChecking", "no"); }

        // https://stackoverflow.com/questions/10881981/sftp-connection-through-java-asking-for-weird-authentication
        config.setProperty("PreferredAuthentications", "publickey,keyboard-interactive,password");
        session.setConfig(config);

        if (StringUtils.isNotEmpty(connection.getPassword())) { session.setPassword(connection.getPassword()); }
        session.connect();

        return session;
    }

    protected static StepResult requireValidRemotePath(String remotePath, boolean dirOK, boolean wildcardOK) {
        if (StringUtils.isBlank(remotePath)) { return StepResult.fail("remote path MUST be specified"); }
        if (!StringUtils.startsWithAny(remotePath, "/", "\\")) {
            return StepResult.fail("remote path MUST be fully qualified (absolute path)");
        }
        if (!dirOK && StringUtils.endsWithAny(remotePath, "\\", "/")) {
            return StepResult.fail("remote path " + remotePath +
                                   " is a directory, but current command does NOT SUPPORT remote directory");
        }
        if (!wildcardOK && hasWildcard(remotePath)) {
            return StepResult.fail("remote path " + remotePath +
                                   " represents multiple files - currently UNSUPPORTED");
        }
        return null;
    }

    protected static StepResult requireValidLocalPath(String localPath, boolean dirOK, boolean wildcardOK) {
        if (StringUtils.isBlank(localPath)) { return StepResult.fail("local path MUST be specified"); }
        if (!dirOK && FileUtil.isDirectoryReadable(localPath)) {
            return StepResult.fail("local path " + localPath +
                                   " is a directory, but corrent command does NOT SUPPORT local directory");
        }
        if (!wildcardOK && hasWildcard(localPath)) {
            return StepResult.fail("local path " + localPath + " represents multiple files - currently UNSUPPORTED");
        }
        return null;
    }

    protected static boolean hasWildcard(String path) {
        return StringUtils.isNotBlank(path) && StringUtils.contains(path, "*");
    }

    protected SshClientConnection resolveSshClientConnection(String profile) throws IntegrationConfigException {
        SshClientConnection connection = SshClientConnection.resolveFrom(context, profile);
        requiresNotNull(connection, "Unable to resolve SSH connection");
        return connection;
    }

    protected void sanityCheck(String var, String profile, String remotePath, String localPath) {
        sanityCheckRemote(var, profile, remotePath);
        requiresNotBlank(localPath, "Invalid local path", localPath);
    }

    protected void sanityCheckRemote(String var, String profile, String remotePath) {
        requiresValidAndNotReadOnlyVariableName(var);
        requiresNotBlank(profile, "Invalid profile", profile);
        requiresNotBlank(remotePath, "Invalid remote path", remotePath);
    }
}
