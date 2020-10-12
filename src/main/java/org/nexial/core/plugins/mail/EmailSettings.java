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

package org.nexial.core.plugins.mail;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * This is a class for setting the Email configurations on the fly.
 */
public class EmailSettings {
    private List<String> toRecipients;
    private List<String> ccRecipients;
    private List<String> bccRecipients;
    private String subject;
    private String body;
    private Map<String, File> attachments;
    private String failure;

    public List<String> getToRecipients() { return toRecipients; }

    public void setToRecipients(List<String> toRecipients) { this.toRecipients = toRecipients; }

    public List<String> getCcRecipients() {return ccRecipients; }

    public void setCcRecipients(List<String> ccRecipients) { this.ccRecipients = ccRecipients; }

    public List<String> getBccRecipients() {return bccRecipients; }

    public void setBccRecipients(List<String> bccRecipients) { this.bccRecipients = bccRecipients; }

    public String getSubject() {return subject; }

    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() {return body; }

    public void setBody(String body) { this.body = body; }

    public String getFailure() {return failure; }

    public void setFailure(String failure) { this.failure = failure; }

    public void clearFailure() { this.failure = null; }

    public Map<String, File> getAttachments() {return attachments; }

    public void setAttachments(Map<String, File> attachments) { this.attachments = attachments; }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                   .append("toRecipients", toRecipients)
                   .append("ccRecipients", ccRecipients)
                   .append("bccRecipients", bccRecipients)
                   .append("subject", subject)
                   .append("body", body)
                   .append("attachments", attachments)
                   .append("failure", failure)
                   .toString();
    }
}
