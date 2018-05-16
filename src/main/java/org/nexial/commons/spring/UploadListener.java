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

package org.nexial.commons.spring;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import javax.servlet.ServletContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.nexial.commons.ConcurrentUploadException;
import org.nexial.commons.UploadCancelException;
import org.nexial.commons.utils.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.web.context.ServletContextAware;

/**
 *

 */
public class UploadListener implements OutputStreamListener, ServletContextAware {
    public static final NumberFormat NF = new DecimalFormat("0.00");
    public static final String DEF_DATETIME_FORMAT = "MM/dd/yyyy HH:mm:ss";
    public static final double SYNTHETIC_RATIO = 2.25;
    public static final double HUNDRED_PERCENT = 100d;

    private static final Logger LOGGER = LoggerFactory.getLogger(UploadListener.class);

    protected String id;
    protected long fileSize;
    protected long bytesRead;
    protected long startTime;
    protected long endTime;
    protected String uploadBasePath;
    protected String completionStatus;

    protected String identifier;
    protected String backupType;
    protected String userId;
    /* not necessarily means it ended without error */
    protected boolean isCompleted;

    protected ServletContext context;
    protected long maxUploadAllowedSeconds;
    protected String uploadProgressPrefix;
    protected MessageSource messages;
    protected boolean deleteWhenFailed;

    protected boolean isCanceled;
    protected boolean isErrored;
    protected boolean listenerRemovalAllowed;

    protected String requestUid;

    public String getRequestUid() { return requestUid; }

    public void setRequestUid(String requestUid) { this.requestUid = requestUid; }

    public boolean isListenerRemovalAllowed() { return listenerRemovalAllowed; }

    public void setListenerRemovalAllowed(boolean listenerRemovalAllowed) {
        this.listenerRemovalAllowed = listenerRemovalAllowed;
    }

    public boolean isErrored() { return isErrored; }

    public void setErrored(boolean isErrored) { this.isErrored = isErrored; }

    public void setUserId(String userId) { this.userId = userId; }

    public String getBackupType() { return backupType; }

    public void setBackupType(String backupType) { this.backupType = backupType; }

    public boolean isCancelled() { return isCanceled; }

    public void setCancelled(boolean isCanceled) { this.isCanceled = isCanceled; }

    public void setDeleteWhenFailed(boolean deleteWhenFailed) { this.deleteWhenFailed = deleteWhenFailed; }

    public void setMessages(MessageSource messages) { this.messages = messages; }

    public void setMaxUploadAllowedSeconds(long maxUploadAllowedSeconds) {
        this.maxUploadAllowedSeconds = maxUploadAllowedSeconds;
    }

    public void setUploadProgressPrefix(String uploadProgressPrefix) {
        this.uploadProgressPrefix = uploadProgressPrefix;
    }

    public void setUploadBasePath(String uploadBasePath) {this.uploadBasePath = uploadBasePath;}

    public void setId(String id) { this.id = id; }

    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public void init() {
        // ensure no other upload is in progress for the same tracer
        if (isAttachInProgress()) {
            String error = messages.getMessage("attachUpload.fail.concurrent",
                                               new String[]{identifier, backupType},
                                               null);
            LOGGER.error(error);
            completionStatus = "Error: " + error;
            unsetInProgress();
            throw new ConcurrentUploadException(error);
        } else {
            startTime = System.currentTimeMillis();
            this.bytesRead = 0;
            if (LOGGER.isInfoEnabled()) { LOGGER.info("ready to download files of total size " + fileSize); }
        }
    }

    /**
     * start is _NOT_REALLY_ start since there might be multiple downloads per request.
     *
     * @see #done()
     * @see #getFileSize()
     */
    public void start() { if (LOGGER.isInfoEnabled()) { LOGGER.info(id + " -> start"); } }

    public void bytesRead(int i) { bytesRead += i; }

    public long getBytesRead() { return bytesRead; }

    public String getStatus() {
        if (StringUtils.isBlank(completionStatus)) {
            return NF.format((double) bytesRead / fileSize * 100);
        } else {
            return completionStatus;
        }
    }

    public void resetCompletionStatus() { completionStatus = null; }

    /**
     * return the size of the overall upload stream, which may include multiple files
     *
     * @see #done()
     * @see #getFileSize()
     */
    public long getFileSize() { return fileSize; }

    public void setFileSize(long fileSize) {
        // we double the content length to synthetically reserve half of the process time for
        // other activities outside of byte transfer.  In other word:
        // 75% of time = file transfer
        // 25% of time = database access, HTTP call to tracer image, yada yada
        this.fileSize = (long) (fileSize * getSyntheticRatio());
    }

    /**
     * calculating total upload time since the initial start time (when this object is instantiated)
     * and until the "latest" end time.  Since there could be multiple downloads, the end time might
     * not be accurate.
     */
    public long getUploadSecond() { return endTime - startTime; }

    public boolean isCompleted() { return isCompleted; }

    public void setCompleted(boolean completed) { isCompleted = completed; }

    public void setServletContext(ServletContext servletContext) { context = servletContext; }

    /**
     * issued from controller (proxying user's request).  we need to stop download process from
     * this point on.
     */
    public void cancel(String error) {
        if (deleteWhenFailed) {
            String uniqueFilePrefix = createUniqueFilePrefix(identifier, userId, backupType);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("removing any " + uniqueFilePrefix +
                             ".* files since user cancelled upload");

            }
            try {
                FileUtil.deleteFiles(uploadBasePath,
                                     uniqueFilePrefix + ".*",
                                     false,
                                     false);
            } catch (IOException e) {
                throw new UploadCancelException(messages.getMessage("attachUpload.cancel.fail.too.late",
                                                                    null,
                                                                    null));
            }
        }

        LOGGER.error("User cancelled uploading " + id + ": " + error);
        completionStatus = "Error: " + error;
        isCanceled = true;
        unsetInProgress();
    }

    public void error(String message) {
        LOGGER.error("Error while uploading " + id + ": " + message);
        completionStatus = message;
        isErrored = true;
        isCompleted = true;
        unsetInProgress();

        if (deleteWhenFailed) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("removing any " + identifier + ".* files since error had occurred");
            }
            destroyUploadedFileset();
        }
    }

    /**
     * done is _NOT_REALLY_ done since we might have multiple files to download.
     *
     * @see #start()
     * @see #getFileSize()
     */
    public void done() {
        endTime = System.currentTimeMillis();
        if (LOGGER.isInfoEnabled()) { LOGGER.info(id + " -> done"); }
    }

    public void uploadComplete(boolean cleanFiles) {
        isCompleted = true;
        unsetInProgress();
        // no news is good news... no status means everything went fine
        bytesRead = fileSize;

        if (cleanFiles) {
            destroyUploadedFileset();
        }
    }

    public void incrementUploadStatus(int percent) {
        long total = this.getFileSize();
        long bytesRead = this.getBytesRead();
        long newBytesRead = (int) (percent / HUNDRED_PERCENT * total);
        if (LOGGER.isDebugEnabled()) { LOGGER.debug("increment bytes-read from " + bytesRead + " by " + newBytesRead); }
        this.bytesRead((int) newBytesRead);
    }

    protected double getSyntheticRatio() { return SYNTHETIC_RATIO; }

    protected void destroyUploadedFileset() {
        String uniqueFilePrefix = createUniqueFilePrefix(identifier, userId, backupType);
        FileUtil.deleteFilesQuietly(uploadBasePath, uniqueFilePrefix + ".*", false, true);
    }

    protected String createUniqueFilePrefix(String identifier, String userId, String uploadType) {
        return identifier + "_" + userId + "_" + uploadType + "~";
    }

    /**
     * ensure that there isn't another upload for the same tracer.  If there isn't, system will
     * set the current tracer as in "upload in progress" so that the next similar request could be
     * rejected.
     */
    protected boolean isAttachInProgress() {

        String key = uploadProgressPrefix + identifier + backupType;
        long rightNow = System.currentTimeMillis();

        if (LOGGER.isDebugEnabled()) { LOGGER.debug("context is :" + context); }
        if (context == null) { return false; }

        Object progressObject = context.getAttribute(key);
        if (progressObject instanceof Long) {
            long startTime = (Long) progressObject;
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("another backup attachment is in progress for tracer " + identifier +
                            " since " + DateFormatUtils.format(startTime, DEF_DATETIME_FORMAT));
            }

            if (rightNow - startTime < maxUploadAllowedSeconds * 1000) { return true; }

            // else, that upload has progress for way too long... we should assume that it's no
            // longer valid
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("a backup attachment for tracer " + identifier +
                            " was taking more than " + maxUploadAllowedSeconds +
                            " seconds... assuming it's dead and allow another one to proceed.");
            }

            context.removeAttribute(key);
        }

        // bind it now so that next upload request for the same tracer would be rejected
        context.setAttribute(key, rightNow);
        return false;
    }

    protected void unsetInProgress() {
        String key = uploadProgressPrefix + identifier + backupType;
        Object progressObject = context.getAttribute(key);
        if (progressObject == null) { return; }
        context.removeAttribute(key);
    }

}
