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

package org.nexial.core.mail;

import java.io.File;
import java.io.Serializable;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static org.nexial.core.NexialConst.Data.MIME_HTML;

public class MailData implements Serializable {
    private List<String> toAddr;
    private List<String> ccAddr;
    private List<String> bccAddr;
    private List<String> replyToAddr;
    private String fromAddr;
    private String subject;
    private String content;
    private List<File> attachments;
    private String mimeType;

    private transient boolean footer = false;

    public boolean isFooter() { return footer; }

    public MailData setFooter(boolean footer) {
        this.footer = footer;
        return this;
    }

    public List<String> getToAddr() { return toAddr; }

    public MailData setToAddr(List<String> toAddr) {
        this.toAddr = toAddr;
        return this;
    }

    public List<String> getCcAddr() { return ccAddr; }

    public MailData setCcAddr(List<String> ccAddr) {
        this.ccAddr = ccAddr;
        return this;
    }

    public List<String> getBccAddr() { return bccAddr; }

    public MailData setBccAddr(List<String> bccAddr) {
        this.bccAddr = bccAddr;
        return this;
    }

    public List<String> getReplyToAddr() { return replyToAddr; }

    public MailData setReplyToAddr(List<String> replyToAddr) {
        this.replyToAddr = replyToAddr;
        return this;
    }

    public String getFromAddr() { return fromAddr; }

    public MailData setFromAddr(String fromAddr) {
        this.fromAddr = fromAddr;
        return this;
    }

    public String getSubject() { return subject; }

    public MailData setSubject(String subject) {
        this.subject = subject;
        return this;
    }

    public String getContent() { return content; }

    public MailData setContent(String content) {
        this.content = content;
        return this;
    }

    public List<File> getAttachments() { return attachments; }

    public MailData setAttachments(List<File> attachments) {
        this.attachments = attachments;
        return this;
    }

    public String getMimeType() { return mimeType; }

    public MailData setMimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    public boolean isHTML() { return StringUtils.equals(MIME_HTML, mimeType); }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("toAddr", toAddr)
                                        .append("ccAddr", ccAddr)
                                        .append("bccAddr", bccAddr)
                                        .append("fromAddr", fromAddr)
                                        .append("replyToAddr", replyToAddr)
                                        .append("subject", subject)
                                        .append("content", content)
                                        .append("attachments", attachments)
                                        .append("mimeType", mimeType)
                                        .toString();
    }
}
