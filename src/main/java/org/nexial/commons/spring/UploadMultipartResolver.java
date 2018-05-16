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

import java.util.HashMap;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.ConcurrentUploadException;
import org.nexial.commons.utils.web.SpringWebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.MessageSource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

/**
 * More work needed to make it for general use
 *
 * @author Mike Liu
 */
public class UploadMultipartResolver extends CommonsMultipartResolver implements ApplicationContextAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadMultipartResolver.class);
    private final static long ONE_MB = 1048576;

    private ApplicationContext context;
    private String requestParamForId;
    private String requestParamForRid;
    private String requestParamForRecipient;
    private String listenerBeanName;
    private String requestParamForUserId;
    private MessageSource msg;
    private long maxUploadAllowedSeconds;

    private class ParamHolder {
        private String identifier;
        private String userId;
        private String recipient;
        private long contentLength;
        private String rid;

        private ParamHolder setParmas(HttpServletRequest request) {
            identifier = request.getParameter(requestParamForId);
            userId = request.getParameter(requestParamForUserId);
            recipient = request.getParameter(requestParamForRecipient);
            contentLength = request.getContentLength();
            rid = request.getParameter(requestParamForRid);
            return this;
        }
    }

    public void setMaxUploadAllowedSeconds(long maxUploadAllowedSeconds) {
        this.maxUploadAllowedSeconds = maxUploadAllowedSeconds;
    }

    public void setMsg(MessageSource msg) { this.msg = msg; }

    public void setRequestParamForRid(String requestParamForRid) { this.requestParamForRid = requestParamForRid; }

    public void setRequestParamForUserId(String requestParamForUserId) {
        this.requestParamForUserId = requestParamForUserId;
    }

    public void setListenerBeanName(String listenerBeanName) { this.listenerBeanName = listenerBeanName; }

    public void setRequestParamForId(String requestParamForId) { this.requestParamForId = requestParamForId; }

    public void setApplicationContext(ApplicationContext context) throws BeansException { this.context = context; }

    public void setRequestParamForRecipient(String requestParamForRecipient) {
        this.requestParamForRecipient = requestParamForRecipient;
    }

    @Override
    protected MultipartParsingResult parseRequest(HttpServletRequest request)
        throws MultipartException {
        if (LOGGER.isDebugEnabled()) { LOGGER.debug("ENTER - parseRequest"); }

        String encoding = determineEncoding(request);

        ParamHolder paramHolder = new ParamHolder().setParmas(request);

        UploadListener listener;
        try {
            listener = StringUtils.isNotEmpty(request.getParameter("tracerNo")) ?
                       createListener(paramHolder) :
                       createClientIDUploadListener(paramHolder);
            if (listener.isCompleted() && StringUtils.isNotBlank(listener.getStatus())) {
                // we haven't started to upload yet... this is that we failed to create listener since
                // there are concurrent upload
                if (LOGGER.isDebugEnabled()) { LOGGER.debug("listener completed before upload!!"); }
                throw new ConcurrentUploadException(listener.getStatus());
            }
        } catch (ConcurrentUploadException ex) {
            //request.setAttribute("request_parse_error", msg.getMessage("1107", new String[]{paramHolder.recipient, Long.toString(maxUploadAllowedSeconds / 60)}, null));
            return new MultipartParsingResult(new LinkedMultiValueMap<>(),
                                              new HashMap<>(),
                                              new HashMap<>());
        }

        FileItemFactory factory = new MonitoredDiskFileItemFactory(listener);
        FileUpload fileUpload = newFileUpload(factory);
        fileUpload.setSizeMax(getFileUpload().getSizeMax());
        fileUpload.setHeaderEncoding(encoding);

        try {
            List fileItems = ((ServletFileUpload) fileUpload).parseRequest(request);
            if (!CollectionUtils.isEmpty(fileItems)) {
                for (Object fileItem1 : fileItems) {
                    FileItem fileItem = (FileItem) fileItem1;
                    if (fileItem != null && StringUtils.isNotBlank(fileItem.getName())) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("downloading backup doc: " +
                                         fileItem.getFieldName() + "," + fileItem.getName());
                        }
                    }
                }
            }
            return parseFileItems(fileItems, encoding);
        } catch (FileUploadBase.SizeLimitExceededException ex) {
            if (listener != null) { listener.error(ex.getMessage()); }

            //request.setAttribute("request_parse_error", msg.getMessage("1106", new String[]{paramHolder.recipient, Long.toString(ex.getActualSize() / ONE_MB), Long.toString(getFileUpload().getSizeMax() / ONE_MB)}, null));
            return new MultipartParsingResult(new LinkedMultiValueMap<>(),
                                              new HashMap<>(),
                                              new HashMap<>());
        } catch (FileUploadException e) {
            // maybe the user cancelled the upload?
            LOGGER.error("Exception during backup transfer", e);

            if (listener != null) {
                listener.error(e.getMessage());

                //request.setAttribute("request_parse_error", msg.getMessage("1108", new String[]{paramHolder.recipient, e.getMessage()}, null));
                return new MultipartParsingResult(new LinkedMultiValueMap<>(),
                                                  new HashMap<>(),
                                                  new HashMap<>());
            }
            throw new MultipartException("Could not parse multipart servlet request", e);
        } finally {
            if (LOGGER.isDebugEnabled()) { LOGGER.debug("EXIT - parseRequest"); }
        }
    }

    private UploadListener createClientIDUploadListener(ParamHolder paramHolder) {

        UploadListener listener = SpringWebUtils.getSessionObject("clientInfoUpload", UploadListener.class);
        if (listener == null) {
            listener = (UploadListener) context.getBean(listenerBeanName);
        }
        if (listener.getRequestUid() == null) {
            listener.setRequestUid("clientInfoUpload");
        }
        listener.setId("clientInfoUpload");
        listener.setIdentifier("clientInfoUpload");
        listener.setFileSize(paramHolder.contentLength);
        listener.setBackupType("tempRecipient");
        listener.setUserId(paramHolder.userId);

        if (LOGGER.isDebugEnabled()) { LOGGER.debug("adding upload listener to session: clientInfoUpload."); }
        SpringWebUtils.setSessionObject("clientInfoUpload", listener);
        return listener;
    }

    private UploadListener createListener(ParamHolder paramHolder) {

        String id = paramHolder.identifier + "-listener" + paramHolder.recipient;

        UploadListener listener = SpringWebUtils.getSessionObject(id, UploadListener.class);
        if (listener == null) {
            listener = (UploadListener) context.getBean(listenerBeanName);
        }
        if (listener.getRequestUid() == null) {
            listener.setRequestUid(paramHolder.rid);
        }
        listener.setId(id);
        listener.setIdentifier(paramHolder.identifier);
        listener.setFileSize(paramHolder.contentLength);
        listener.setBackupType(paramHolder.recipient);
        listener.setUserId(paramHolder.userId);
        listener.init();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("listener.getRequestUid():" + listener.getRequestUid() +
                         ", paramHolder.rid:" + paramHolder.rid +
                         ", listener.isCompleted():" + listener.isCompleted());
        }
        if (listener.getRequestUid() != null && !StringUtils.equals(listener.getRequestUid(), paramHolder.rid)) {
            // When a request for upload is received and a listener for previous request is completed but not yet
            // removed from session, this condition avoids false identification of cuncurrent upload
            if (listener.isCompleted()) {
                listener.setCompleted(false);
            }
            if (listener.isCancelled()) {
                listener.setCancelled(false);
            }
            listener.resetCompletionStatus();
        }

        if (LOGGER.isDebugEnabled()) { LOGGER.debug("adding upload listener to session: " + id); }
        SpringWebUtils.setSessionObject(id, listener);
        return listener;
    }
}
