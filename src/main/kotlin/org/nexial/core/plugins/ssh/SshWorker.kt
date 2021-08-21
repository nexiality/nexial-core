/*
 * Copyright 2012-2018 the original author or authors.
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

package org.nexial.core.plugins.ssh

import com.jcraft.jsch.*
import com.jcraft.jsch.ChannelSftp.LsEntry
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.DirectoryFileFilter.DIRECTORY
import org.apache.commons.io.filefilter.FileFileFilter
import org.apache.commons.io.filefilter.RegexFileFilter
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.core.model.RemoteFileActionOutcome
import org.nexial.core.model.RemoteFileActionOutcome.TransferAction
import org.nexial.core.model.RemoteFileActionOutcome.TransferAction.*
import org.nexial.core.model.RemoteFileActionOutcome.TransferProtocol.SFTP
import org.nexial.core.utils.ConsoleUtils
import java.io.File
import java.io.File.separator
import java.util.*

open class SshWorker(internal val action: TransferAction, val remote: String, val local: String?) {
    private val remoteMustBeFQ = listOf(COPY_FROM, COPY_TO, MOVE_FROM, MOVE_TO, LIST, DELETE)
    private val remoteMustBeSingle = listOf(COPY_TO, MOVE_TO)
    private val localMustNotBeFQ = listOf(COPY_FROM, COPY_TO, MOVE_FROM, MOVE_TO)
    private val localMustBeSingle = listOf<TransferAction>() // listOf(COPY_TO, MOVE_TO)

    private val msgRemoteFQ = "remote path MUST be a fully qualified path"
    private val msgRemoteNoWildcard = "remote path MUST not contain wildcard"
    private val msgLocalFQ = "local path MUST be a fully qualified path"
    private val msgLocalNoWildcard = "local path MUST not contain wildcard"

    internal fun preActionChecks() {
        val prefix = "[$action]: "
        if (remoteMustBeFQ.contains(action) && !remote.startsWith("/"))
            throw IllegalArgumentException("$prefix$msgRemoteFQ")

        if (remoteMustBeSingle.contains(action) && remote.contains("*"))
            throw IllegalArgumentException("$prefix$msgRemoteNoWildcard")

        if (localMustNotBeFQ.contains(action) && StringUtils.isBlank(local))
            throw IllegalArgumentException("$prefix$msgLocalFQ")

        if (localMustBeSingle.contains(action) && (local == null || local.contains("*")))
            throw IllegalArgumentException("$prefix$msgLocalNoWildcard")
    }

    internal fun deriveFQN(path: String, filename: String) = File(path).absolutePath + separator + filename

    internal fun addErrorOnRemote(outcome: RemoteFileActionOutcome, message: String): RemoteFileActionOutcome {
        ConsoleUtils.error("${logRemoteHeader(outcome)}$message - FAIL")
        outcome.addFailed(outcome.remotePath).appendError(message)
        return outcome
    }

    internal fun addErrorOnLocal(outcome: RemoteFileActionOutcome, message: String): RemoteFileActionOutcome {
        ConsoleUtils.error("${logLocalHeader(outcome)}$message - FAIL")
        outcome.addFailed(outcome.localPath).appendError(message)
        return outcome
    }

    internal fun addSingleFileSuccess(outcome: RemoteFileActionOutcome, affected: String?, message: String):
            RemoteFileActionOutcome {
        ConsoleUtils.log("${logRemoteHeader(outcome)}[$affected] $message - SUCCESS")
        outcome.addAffected(affected)
        return outcome
    }

    internal fun logRemoteHeader(o: RemoteFileActionOutcome) = "[${o.protocol} ${o.action} ${o.remotePath}] - "

    internal fun logLocalHeader(o: RemoteFileActionOutcome) = "[${o.protocol} ${o.action} ${o.localPath}] - "

    internal fun listLocal(local: String) =
            when {
                FileUtil.isDirectoryReadable(local) ->
                    FileUtils.listFiles(File(local), FileFileFilter.INSTANCE, DIRECTORY).toList()
                FileUtil.isFileReadable(local)      ->
                    listOf(File(local))
                else                                -> {
                    val localPath = StringUtils.replace(local, "\\", "/")
                    val dir = StringUtils.substringBeforeLast(localPath, "/")
                    val pattern = localPath.substringAfterLast("/")
                        .replace(".", "\\.")
                        .replace("*", ".+")
                        .replace(":", "\\:")
                    FileUtils.listFiles(File(dir), RegexFileFilter(pattern), DIRECTORY).toList()
                }
            }

    @Throws(JSchException::class)
    internal fun connect(connection: SshClientConnection): Session {
        val ssh = JSch()

        val knownHostsFile = connection.knownHostsFile
        if (knownHostsFile != null) ssh.setKnownHosts(knownHostsFile.absolutePath)

        val config = Properties()
        if (!connection.isStrictHostKeyChecking) config.setProperty("StrictHostKeyChecking", "no")
        // https://stackoverflow.com/questions/10881981/sftp-connection-through-java-asking-for-weird-authentication
        config.setProperty("PreferredAuthentications", "publickey,keyboard-interactive,password")

        val session = ssh.getSession(connection.username, connection.host, connection.port)
        session.setConfig(config)
        if (StringUtils.isNotEmpty(connection.password)) session.setPassword(connection.password)

        session.connect()
        return session
    }

    @Throws(JSchException::class)
    internal fun openSftpChannel(session: Session): ChannelSftp {
        if (!session.isConnected) throw JSchException("session is invalid or disconnected")
        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect()
        return channel
    }

    internal fun closeSshClient(session: Session?, channel: Channel?) {
        if (channel is ChannelSftp) channel.exit()
        channel?.disconnect()
        session?.disconnect()
    }
}

class SftpWorker(action: TransferAction, remote: String, local: String?) : SshWorker(action, remote, local) {
    fun doAction(connection: SshClientConnection): RemoteFileActionOutcome {
        // make sure arguments meet pre-connection requirement
        preActionChecks()

        // also starts timer (inside RemoteFileActionOutcome)
        val outcome = RemoteFileActionOutcome()
            .setProtocol(SFTP).setAction(action).setRemotePath(remote).setLocalPath(local)

        // connect
        val session = connect(connection)
        val channel = openSftpChannel(session)

        // perform action
        return try {
            when (action) {
                MOVE_FROM -> copyRemoteToLocal(channel, outcome, true)
                MOVE_TO   -> copyLocalToRemote(channel, outcome, true)
                COPY_FROM -> copyRemoteToLocal(channel, outcome, false)
                COPY_TO   -> copyLocalToRemote(channel, outcome, false)
                LIST      -> list(channel, outcome)
                DELETE    -> delete(channel, outcome)
            }
        } catch (e: JSchException) {
            addErrorOnRemote(outcome, e.message!!)
        } catch (e: SftpException) {
            addErrorOnRemote(outcome, e.message!!)
        } finally {
            closeSshClient(session, channel)
        }.end()
    }

    private fun list(channel: ChannelSftp, outcome: RemoteFileActionOutcome): RemoteFileActionOutcome {
        val remoteFiles = channel.ls(outcome.remotePath)

        ConsoleUtils.log("${logRemoteHeader(outcome)}list from $remote: ${CollectionUtils.size(remoteFiles)} file(s)")

        if (CollectionUtils.isEmpty(remoteFiles))
            return addSingleFileSuccess(outcome, null, "No files found via (sftp) ${outcome.remotePath}")

        val remoteDir = resolveRemoteParentPath(outcome, remoteFiles)
        remoteFiles.forEach { entry ->
            if (entry is LsEntry) {
                val filename = entry.filename
                if (filename != "." && filename != "..") outcome.addAffected(remoteDir + filename)
            }
        }

        return outcome
    }

    private fun delete(channel: ChannelSftp, outcome: RemoteFileActionOutcome): RemoteFileActionOutcome {

        val remote = outcome.remotePath
        val remoteFiles = channel.ls(remote)
        val remoteFileCount = CollectionUtils.size(remoteFiles)
        return if (remoteFileCount < 0)
            addSingleFileSuccess(outcome, null, "No files to delete (sftp) from $remote")
        else {
            if (remoteFileCount == 1) {
                val lstat = channel.lstat(remote)
                if (lstat.isDir) {
                    channel.rmdir(remote)
                    addSingleFileSuccess(outcome, remote, "Remote path $remote deleted (sftp)")
                } else {
                    channel.rm(remote)
                    addSingleFileSuccess(outcome, remote, "Remote file $remote deleted (sftp)")
                }
            } else {
                val remotePath = remote.substringBeforeLast("/") + "/"
                remoteFiles.forEach { file ->
                    if (file is LsEntry) {
                        val remoteFile = remotePath + file.filename
                        channel.rm(remoteFile)
                        addSingleFileSuccess(outcome, remoteFile, "Remote file $remoteFile deleted (sftp)")
                    }
                }
                outcome
            }
        }
    }

    private fun copyRemoteToLocal(channel: ChannelSftp, outcome: RemoteFileActionOutcome, move: Boolean):
            RemoteFileActionOutcome {

        // list remote files
        val remoteFiles = channel.ls(remote)
        return if (CollectionUtils.isEmpty(remoteFiles))
            addErrorOnRemote(outcome, "remote file ${outcome.remotePath} cannot be found")
        else {
            // if single file
            if (remoteFiles.size == 1)
                copyRemoteToLocal(channel, remoteFiles[0] as LsEntry, remote, local!!, outcome, move)
            else {
                // if multiple files
                val remotePath = StringUtils.substringBeforeLast(remote, "/") + "/"
                val localPath = StringUtils.appendIfMissing(local, separator)
                remoteFiles.forEach { file ->
                    if (file is LsEntry && !file.attrs.isDir)
                        copyRemoteToLocal(channel, file, remotePath + file.filename, localPath, outcome, move)
                }

                outcome
            }
        }
    }

    private fun copyRemoteToLocal(channel: ChannelSftp,
                                  remoteEntry: LsEntry,
                                  remote: String,
                                  local: String,
                                  outcome: RemoteFileActionOutcome,
                                  move: Boolean): RemoteFileActionOutcome {

        val filename = remoteEntry.filename

        // local might be a directory
        val localPath = if (FileUtil.isDirectoryReadable(local)) deriveFQN(local, filename) else local

        // 1. get remote file
        channel.get(remote, localPath)

        // 2. check that transfer was successful
        if (!FileUtil.isFileReadable(localPath))
            return addErrorOnLocal(outcome, "Local file '${outcome.localPath}' is not accessible")

        // 3. check local file matching remote file size
        val failed = testFileSize(outcome, remoteEntry, File(localPath))
        if (failed != null) return failed

        // 4. remove remote file
        return try {
            if (move) channel.rm(remote)
            addSingleFileSuccess(outcome, remote, "${outcome.remotePath} moved to $localPath")
        } catch (e: SftpException) {
            addErrorOnRemote(outcome, "Unable to delete remote file ${outcome.remotePath}: ${e.message}")
        }
    }

    private fun copyLocalToRemote(channel: ChannelSftp, outcome: RemoteFileActionOutcome, move: Boolean):
            RemoteFileActionOutcome {

        // list local files
        val localFiles = listLocal(outcome.localPath)
        if (CollectionUtils.isEmpty(localFiles))
            return addErrorOnLocal(outcome, "local file ${outcome.localPath} cannot be found")

        val lstat = channel.lstat(outcome.remotePath)
        val remotePath = StringUtils.appendIfMissing(outcome.remotePath, "/")

        // if single file
        return if (localFiles.size == 1)
            copyLocalToRemote(channel,
                              localFiles[0],
                              if (lstat.isDir) remotePath + localFiles[0].name else outcome.remotePath,
                              outcome,
                              move)
        else {
            // if multiple files
            // then we must assume that the remote path is a directory
            if (!lstat.isDir)
                addErrorOnRemote(outcome, "remote '${outcome.remotePath}' is NOT a directory as expected")
            else {
                for (f in localFiles) copyLocalToRemote(channel, f, remotePath + f.name, outcome, move)
                outcome
            }
        }
    }

    private fun copyLocalToRemote(channel: ChannelSftp,
                                  local: File,
                                  remotePath: String,
                                  outcome: RemoteFileActionOutcome,
                                  move: Boolean): RemoteFileActionOutcome {

        channel.put(local.absolutePath, remotePath)

        val remoteFileListing = channel.ls(remotePath)
                                ?: return addErrorOnRemote(outcome, "Unable to transfer to remote file $remotePath")

        val failed = testFileSize(outcome, remoteFileListing[0] as LsEntry, local)
        if (failed != null) return failed

        if (move && !FileUtils.deleteQuietly(local)) return addErrorOnLocal(outcome, "Cannot delete local file $local")

        return addSingleFileSuccess(outcome, remotePath, "$local moved to $remotePath")
    }

    private fun testFileSize(outcome: RemoteFileActionOutcome, remote: LsEntry, local: File): RemoteFileActionOutcome? {
        val remoteSize = remote.attrs.size
        val localSize = local.length()
        return if (localSize != remoteSize)
            addErrorOnRemote(outcome, "Local file size ($localSize) is different than remote file size ($remoteSize)")
        else null
    }

    private fun resolveRemoteParentPath(outcome: RemoteFileActionOutcome, remoteFiles: Vector<Any>): String {
        val dirName = FileUtil.extractFilename(outcome.remotePath)
        return StringUtils.appendIfMissing(
            if (StringUtils.isBlank(dirName)) {
                // strange...
                ConsoleUtils.log("possibly an error... Can't resolve remote directory from ${outcome.remotePath}")
                outcome.remotePath
            } else
                if (CollectionUtils.size(remoteFiles) > 1 && dirName == (remoteFiles[0] as LsEntry).filename)
                    outcome.remotePath.substringBeforeLast("/")
                else
                    if (dirName.contains("*")) outcome.remotePath.substringBeforeLast("/") else dirName,
            "/")
    }
}