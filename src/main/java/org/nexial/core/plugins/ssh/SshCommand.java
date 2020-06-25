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
import java.io.IOException;
import java.util.Properties;
import java.util.Vector;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.IntegrationConfigException;
import org.nexial.core.model.StepResult;
import org.nexial.core.model.RemoteFileActionOutcome;
import org.nexial.core.model.RemoteFileActionOutcome.TransferAction;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.ConsoleUtils;

import com.jcraft.jsch.*;
import com.jcraft.jsch.ChannelSftp.*;

import static com.jcraft.jsch.ChannelSftp.*;
import static java.io.File.separator;
import static org.nexial.core.model.RemoteFileActionOutcome.TransferAction.*;
import static org.nexial.core.model.RemoteFileActionOutcome.TransferProtocol.SCP;
import static org.nexial.core.model.RemoteFileActionOutcome.TransferProtocol.SFTP;
import static org.nexial.core.utils.CheckUtils.*;

public class SshCommand extends BaseCommand {

    @Override
    public String getTarget() { return "ssh"; }

    public StepResult sftpCopyFrom(String var, String profile, String remote, String local)
        throws IntegrationConfigException {
        sanityCheck(var, profile, remote, local);

        RemoteFileActionOutcome outcome = new RemoteFileActionOutcome().setProtocol(SFTP)
                                                                       .setAction(COPY_FROM)
                                                                       .setLocalPath(local)
                                                                       .setRemotePath(remote);

        StepResult stepResult = preRemoteActionChecks(outcome);
        if (stepResult != null) { return stepResult; }

        remote = outcome.getRemotePath();
        local = outcome.getLocalPath();

        SshClientConnection connection = resolveSshClientConnection(profile);

        Session session = null;
        ChannelSftp channel = null;

        try {
            session = connect(connection);
            channel = openSftpChannel(session);
            channel.get(remote, local);
            return succeedSingleFile(var, local, outcome, "copy from " + remote + " to " + local);
        } catch (JSchException | SftpException e) {
            return failSingleFile(var, remote, outcome, e);
        } finally {
            closeSshClient(session, channel);
        }
    }

    public StepResult sftpCopyTo(String var, String profile, String local, String remote)
        throws IntegrationConfigException {

        sanityCheck(var, profile, remote, local);

        RemoteFileActionOutcome outcome = new RemoteFileActionOutcome().setProtocol(SFTP)
                                                                       .setAction(COPY_TO)
                                                                       .setLocalPath(local)
                                                                       .setRemotePath(remote);

        StepResult stepResult = preRemoteActionChecks(outcome);
        if (stepResult != null) { return stepResult; }

        remote = outcome.getRemotePath();
        local = outcome.getLocalPath();

        SshClientConnection connection = resolveSshClientConnection(profile);

        Session session = null;
        ChannelSftp channel = null;

        try {
            session = connect(connection);
            channel = openSftpChannel(session);
            channel.put(local, remote);
            return succeedSingleFile(var, remote, outcome, "copy from " + local + " to " + remote);
        } catch (JSchException | SftpException e) {
            return failSingleFile(var, remote, outcome, e);
        } finally {
            closeSshClient(session, channel);
        }
    }

    public StepResult sftpMoveFrom(String var, String profile, String remote, String local)
        throws IntegrationConfigException {

        sanityCheck(var, profile, remote, local);

        RemoteFileActionOutcome outcome = new RemoteFileActionOutcome().setProtocol(SFTP)
                                                                       .setAction(MOVE_FROM)
                                                                       .setLocalPath(local)
                                                                       .setRemotePath(remote);

        StepResult stepResult = preRemoteActionChecks(outcome);
        if (stepResult != null) { return stepResult; }

        remote = outcome.getRemotePath();
        local = outcome.getLocalPath();

        SshClientConnection connection = resolveSshClientConnection(profile);

        Session session = null;
        ChannelSftp channel = null;

        try {
            session = connect(connection);
            channel = openSftpChannel(session);

            // 1. get remote file attr
            Vector remoteFileList = channel.ls(remote);
            if (CollectionUtils.isEmpty(remoteFileList)) {
                return failSingleFile(var, remote, outcome, "Remote file " + remote + " cannot be found or is invalid");
            }
            if (CollectionUtils.size(remoteFileList) > 1) {
                return failSingleFile(var, remote, outcome, "Remote path " + remote + " represents multiple files, " +
                                                            "but only single file is supported at this time");
            }

            // only interested in 1 file
            LsEntry remoteFile = (LsEntry) remoteFileList.get(0);
            SftpATTRS fileAttrs = remoteFile.getAttrs();
            long remoteFileSize = fileAttrs.getSize();

            // 2. get remote file
            channel.get(remote, local);

            // check local file matching remote file size
            if (!FileUtil.isFileReadable(local)) {
                return failSingleFile(var, remote, outcome, "Local file " + local + " is not accessible");
            }
            File localFile = new File(local);
            if (localFile.length() != remoteFileSize) {
                return failSingleFile(var, local, outcome,
                                      "Local file size (" + localFile.length() + ") is different than " +
                                      "remote file size (" + remoteFileSize + ")");
            }

            // remove remote file
            channel.rm(remote);

            return succeedSingleFile(var, local, outcome, "move from (sftp) " + remote + " to " + local);
        } catch (JSchException | SftpException e) {
            return failSingleFile(var, remote, outcome, e);
        } finally {
            closeSshClient(session, channel);
        }
    }

    public StepResult sftpMoveTo(String var, String profile, String local, String remote)
        throws IntegrationConfigException {

        sanityCheck(var, profile, remote, local);

        RemoteFileActionOutcome outcome = new RemoteFileActionOutcome().setProtocol(SFTP)
                                                                       .setAction(COPY_TO)
                                                                       .setLocalPath(local)
                                                                       .setRemotePath(remote);

        StepResult stepResult = preRemoteActionChecks(outcome);
        if (stepResult != null) { return stepResult; }

        remote = outcome.getRemotePath();
        local = outcome.getLocalPath();
        File localPath = new File(local);
        long expectedFileSize = localPath.length();

        SshClientConnection connection = resolveSshClientConnection(profile);

        Session session = null;
        ChannelSftp channel = null;

        try {
            session = connect(connection);
            channel = openSftpChannel(session);

            // 1. push file to remote
            channel.put(local, remote);

            // 2. check remote file existence and file size
            Vector fileListing = channel.ls(remote);

            Pair<String, Long> remoteEntry = resolveRemotePath(fileListing, outcome);
            if (remoteEntry == null) {
                return failSingleFile(var, local, outcome, "cannot put file " + local + " to remote " + remote);
            }

            Long remoteFileSize = remoteEntry.getValue();
            if (remoteFileSize != expectedFileSize) {
                return failSingleFile(var, local, outcome,
                                      "Local file size (" + expectedFileSize + ") is different than " +
                                      "remote file size (" + remoteFileSize + ")");
            }

            // 3. remove local file
            if (!FileUtils.deleteQuietly(localPath)) {
                return failSingleFile(var, local, outcome, "Cannot delete local file " + local);
            }

            return succeedSingleFile(var, remote, outcome, "move from " + local + " to " + remote);
        } catch (JSchException | SftpException e) {
            return failSingleFile(var, remote, outcome, e);
        } finally {
            closeSshClient(session, channel);
        }
    }

    public StepResult sftpList(String var, String profile, String remote)
        throws IntegrationConfigException {

        sanityCheckRemote(var, profile, remote);

        RemoteFileActionOutcome outcome = new RemoteFileActionOutcome().setProtocol(SFTP)
                                                                       .setAction(LIST)
                                                                       .setRemotePath(remote);

        StepResult stepResult = preRemoteActionChecks(outcome);
        if (stepResult != null) { return stepResult; }

        remote = outcome.getRemotePath();

        SshClientConnection connection = resolveSshClientConnection(profile);

        String logPrefix = resolveLogPrefix(outcome);

        Session session = null;
        ChannelSftp channel = null;

        try {
            session = connect(connection);
            channel = openSftpChannel(session);

            Vector remoteFiles = channel.ls(outcome.getRemotePath());
            ConsoleUtils.log(logPrefix + "list from " + remote + ": " + CollectionUtils.size(remoteFiles) + " file(s)");

            if (CollectionUtils.isEmpty(remoteFiles)) {
                return succeedSingleFile(var, null, outcome, "No files found via (sftp) " + remote);
            }

            String remoteDir = resolveRemoteParentPath(outcome, remoteFiles);

            remoteFiles.forEach(entry -> {
                if (entry != null && entry instanceof LsEntry) {
                    String filename = ((LsEntry) entry).getFilename();
                    if (!StringUtils.equals(filename, ".") && !StringUtils.equals(filename, "..")) {
                        outcome.addAffected(remoteDir + filename);
                    }
                }
            });

            context.setData(var, outcome.end());
            return StepResult.success("Successfully list files from (sftp) " + remote);
        } catch (JSchException | SftpException e) {
            return failSingleFile(var, remote, outcome, e);
        } finally {
            closeSshClient(session, channel);
        }

    }

    public StepResult sftpDelete(String var, String profile, String remote)
        throws IntegrationConfigException {

        sanityCheckRemote(var, profile, remote);

        RemoteFileActionOutcome outcome = new RemoteFileActionOutcome().setProtocol(SFTP)
                                                                       .setAction(LIST)
                                                                       .setRemotePath(remote);

        StepResult stepResult = preRemoteActionChecks(outcome);
        if (stepResult != null) { return stepResult; }

        remote = outcome.getRemotePath();

        SshClientConnection connection = resolveSshClientConnection(profile);

        Session session = null;
        ChannelSftp channel = null;

        try {
            session = connect(connection);
            channel = openSftpChannel(session);
            channel.rm(outcome.getRemotePath());
            return succeedSingleFile(var, remote, outcome, "delete remote file " + remote);
        } catch (JSchException | SftpException e) {
            return failSingleFile(var, remote, outcome, e);
        } finally {
            closeSshClient(session, channel);
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

    protected ChannelSftp openSftpChannel(Session session) throws JSchException {
        if (session == null || !session.isConnected()) { throw new JSchException("session is invalid or disconnected");}

        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();
        return channel;
    }

    protected ChannelExec openExecChannel(Session session) throws JSchException {
        if (session == null || !session.isConnected()) { throw new JSchException("session is invalid or disconnected");}

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.connect();
        return channel;
    }

    protected Pair<String, Long> resolveRemotePath(Vector fileListing, RemoteFileActionOutcome outcome) {
        String remote = outcome.getRemotePath();
        String local = outcome.getLocalPath();

        // if nothing found, then fail
        if (CollectionUtils.isEmpty(fileListing)) { return null; }

        if (CollectionUtils.size(fileListing) == 1) {
            // if I find exactly 1 file, then that's the file
            LsEntry lsEntry = (LsEntry) fileListing.get(0);
            String remoteFilename = lsEntry.getFilename();
            long size = lsEntry.getAttrs().getSize();
            if (!StringUtils.endsWith(remote, remoteFilename)) {
                // remote file name already part of `remote`, so `remote` is the full path.. otherwise append to `remote`
                remote = StringUtils.appendIfMissing(remote, "/") + remoteFilename;
            }
            ConsoleUtils.log(resolveLogPrefix(outcome), "resolve remote path as " + remote);
            return new ImmutablePair<>(remote, size);
        }

        // if I find more than 1, then `remote` was a directory - we need to find the one with same file anem
        String remoteFilename = FileUtil.extractFilename(local);
        for (Object item : fileListing) {
            LsEntry lsEntry = (LsEntry) item;
            if (StringUtils.equals(lsEntry.getFilename(), remoteFilename)) {
                // found it
                remote = StringUtils.appendIfMissing(remote, "/") + remoteFilename;
                return new ImmutablePair<>(remote, lsEntry.getAttrs().getSize());
            }
        }

        return null;
    }

    protected static String resolveRemoteParentPath(RemoteFileActionOutcome outcome, Vector remoteFiles) {
        String remoteDir = outcome.getRemotePath();

        String dirName = FileUtil.extractFilename(remoteDir);
        if (StringUtils.isBlank(dirName)) {
            // strange...
            ConsoleUtils.log("possibly an error... Can't resolve remote directory from " + remoteDir);
        } else {
            if (CollectionUtils.size(remoteFiles) > 1) {
                // we have many files, hence outcome.remote must not be a file, but a listing or wildcard

                if (StringUtils.contains(dirName, "*")) {
                    // last bit is a wildcard, hence remoteDir should be the parent path
                    remoteDir = StringUtils.substringBeforeLast(remoteDir, "/");
                }
                // else.. the entire remote path is the directory
            } else {
                // only 1 file return,
                if (StringUtils.contains(dirName, "*")) {
                    // last bit is a wildcard, hence remoteDir should be the parent path
                    remoteDir = StringUtils.substringBeforeLast(remoteDir, "/");
                } else {
                    // test if that single file is in fact outcome.remote
                    if (StringUtils.equals(dirName, ((LsEntry) remoteFiles.get(0)).getFilename())) {
                        // this means outcome.remote is that same file
                        remoteDir = StringUtils.substringBeforeLast(remoteDir, "/");
                    }
                    // else ... the entire remote path is the directory
                }
            }
        }

        return StringUtils.appendIfMissing(remoteDir, "/");
    }

    protected StepResult succeedSingleFile(String var, String file, RemoteFileActionOutcome outcome, String message) {
        ConsoleUtils.log(resolveLogPrefix(outcome) + "[" + file + "] " + message + " - SUCCESS");
        context.setData(var, outcome.addAffected(file).end());
        return StepResult.success(message);
    }

    protected StepResult failSingleFile(String var, String file, RemoteFileActionOutcome outcome, String message) {
        ConsoleUtils.error(resolveLogPrefix(outcome) + "[" + file + "] FAIL: " + message);
        context.setData(var, outcome.addFailed(file).appendError(message).end());
        return StepResult.fail(message);
    }

    protected StepResult failSingleFile(String var, String file, RemoteFileActionOutcome outcome, Exception e) {
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
