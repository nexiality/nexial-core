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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
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
    private Map<String, String> failures;

    public List<String> getToRecipients() {
        return toRecipients;
    }

    public void setToRecipients(List<String> toRecipients) {
        this.toRecipients = toRecipients;
    }

    public List<String> getCcRecipients() {
        return ccRecipients;
    }

    public void setCcRecipients(List<String> ccRecipients) {
        this.ccRecipients = ccRecipients;
    }

    public List<String> getBccRecipients() {
        return bccRecipients;
    }

    public void setBccRecipients(List<String> bccRecipients) {
        this.bccRecipients = bccRecipients;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Map<String, String> getFailures() {
        return failures;
    }

    public void setFailure(String config, String message) {
        if (!MapUtils.isNotEmpty(failures)) {
            failures = new HashMap<>();
        }
        failures.put(config, message);
    }

    public void clearFailure(String config) {
        if (MapUtils.isNotEmpty(failures)) {
            failures.remove(config);
        }
    }

    public Map<String, File> getAttachments() {
        return attachments;
    }

    public void setAttachments(Map<String, File> attachments) {
        this.attachments = attachments;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("toRecipients", toRecipients)
                .append("ccRecipients", ccRecipients)
                .append("bccRecipients", bccRecipients)
                .append("subject", subject)
                .append("body", body)
                .append("attachments", attachments)
                .append("failures are:-\n", failures)
                .toString();
    }

    /**
     * Displays all the failures that occurred in a String format.
     *
     * @return all the failures.
     */
    public String displayFailures() {
        if (MapUtils.isEmpty(failures)) {
            return StringUtils.EMPTY;
        }

        StringBuilder messages = new StringBuilder(StringUtils.EMPTY);
        for (String key : failures.keySet()) {
            messages.append(failures.get(key)).append("\n");
        }
        return messages.toString();
    }
}
