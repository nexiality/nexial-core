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

import org.springframework.context.MessageSource;

/**
 * @author Mike Liu
 */
public class MessageSourceHelper {
    private MessageSource messages;

    public MessageSource getMessages() { return messages; }

    public void setMessages(MessageSource messages) { this.messages = messages; }

    /**
     * "instance" version of {@link #getMessage(MessageSource, String, Object...)}
     *
     * @see #getMessage(MessageSource, String, Object...)
     */
    public String getMessage(String code, Object... args) {
        return MessageSourceHelper.getMessage(messages, code, args);
    }

    /**
     * assemble the appropriate message text based on {@code code} and {@code args}.
     * <p/>
     * If {@code message} is null, just the {@code code} parameter is returned.  If {@code code} is null, then a
     * standard "unknown message" is returned.
     */
    public static String getMessage(MessageSource source, String code, Object... args) {
        if (code == null) { return "Unknown message; null code provided"; }
        if (source == null) { return code; }
        if (args == null || args.length < 1) { return source.getMessage(code, null, null); }
        return source.getMessage(code, args, null);
    }
}
