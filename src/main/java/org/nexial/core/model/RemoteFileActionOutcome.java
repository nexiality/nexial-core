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

package org.nexial.core.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.list.TreeList;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.TextUtils;

import static java.lang.System.lineSeparator;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.nexial.core.NexialConst.DATE_FORMAT_NOW;

public class RemoteFileActionOutcome implements Serializable {
    private List<String> affected = new TreeList<>();
    private List<String> failed = new TreeList<>();
    private String errors;
    private long startTime;
    private long elapsedTime;
    private TransferProtocol protocol;
    private TransferAction action;
    private String remotePath;
    private String localPath;

    public enum TransferProtocol {SFTP, SCP, AWS}

    public enum TransferAction {
        COPY_TO("copy to"),
        COPY_FROM("copy from"),
        MOVE_TO("move to"),
        MOVE_FROM("move from"),
        LIST("list"),
        DELETE("delete");

        private final String description;

        TransferAction(String description) { this.description = description; }

        public String getDescription() { return description; }

        @Override
        public String toString() { return description; }
    }

    public RemoteFileActionOutcome() { this.startTime = System.currentTimeMillis(); }

    public List<String> getAffected() { return affected; }

    public RemoteFileActionOutcome setAffected(List<String> affected) {
        this.affected = affected;
        return this;
    }

    public RemoteFileActionOutcome addAffected(String... affected) {
        if (ArrayUtils.isNotEmpty(affected)) { this.affected.addAll(Arrays.asList(affected)); }
        return this;
    }

    public List<String> getFailed() { return failed; }

    public RemoteFileActionOutcome setFailed(List<String> failed) {
        this.failed = failed;
        return this;
    }

    public RemoteFileActionOutcome addFailed(String... failed) {
        if (ArrayUtils.isNotEmpty(failed)) { this.failed.addAll(Arrays.asList(failed)); }
        return this;
    }

    public String getErrors() { return errors; }

    public RemoteFileActionOutcome setErrors(String errors) {
        this.errors = errors;
        return this;
    }

    public RemoteFileActionOutcome appendError(String... errors) {
        if (ArrayUtils.isNotEmpty(errors)) {
            if (this.errors == null) {
                this.errors = "";
            }
            Arrays.stream(errors).forEach(error -> {
                if (StringUtils.isNotBlank(error)) {
                    this.errors += error + lineSeparator();
                }
            });
        }

        return this;
    }

    public boolean hasError() { return StringUtils.isNotBlank(errors); }

    public long getStartTime() { return startTime; }

    public RemoteFileActionOutcome setStartTime(long startTime) {
        this.startTime = startTime;
        return this;
    }

    public RemoteFileActionOutcome start() {
        this.startTime = System.currentTimeMillis();
        return this;
    }

    public long getElapsedTime() { return elapsedTime; }

    public RemoteFileActionOutcome setElapsedTime(long elapsedTime) {
        this.elapsedTime = elapsedTime;
        return this;
    }

    public RemoteFileActionOutcome end() {
        this.elapsedTime = System.currentTimeMillis() - this.startTime;
        return this;
    }

    public TransferProtocol getProtocol() { return protocol; }

    public RemoteFileActionOutcome setProtocol(TransferProtocol protocol) {
        this.protocol = protocol;
        return this;
    }

    public TransferAction getAction() { return action; }

    public RemoteFileActionOutcome setAction(TransferAction action) {
        this.action = action;
        return this;
    }

    public String getRemotePath() { return remotePath; }

    public RemoteFileActionOutcome setRemotePath(String remotePath) {
        this.remotePath = remotePath;
        return this;
    }

    public String getLocalPath() { return localPath; }

    public RemoteFileActionOutcome setLocalPath(String localPath) {
        if (IS_OS_WINDOWS) {
            this.localPath = StringUtils.replace(localPath, "/", "\\");
        } else {
            this.localPath = StringUtils.replace(localPath, "\\", "/");
        }
        return this;
    }

    @Override
    public String toString() {
        return TextUtils.prettyToString(
            "protocol=" + protocol,
            "action=" + action,
            "startTime=" + DateUtility.format(startTime, DATE_FORMAT_NOW),
            "elapsedTime=" + elapsedTime,
            "remotePath=" + StringUtils.defaultString(remotePath),
            "localPath=" + StringUtils.defaultString(localPath),
            (CollectionUtils.isNotEmpty(affected) ? "affected=" + affected : ""),
            (CollectionUtils.isNotEmpty(failed) ? "failed=" + failed : ""),
            (StringUtils.isNotBlank(errors) ? "errors=" + StringUtils.defaultString(errors) : ""));
    }
}
