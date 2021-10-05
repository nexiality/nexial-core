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

import static org.nexial.core.excel.ExcelConfig.MSG_SCREENCAPTURE;

/**
 * used to capture and render nested screen capture(s) post execution of a test step.
 *
 * @see NestedMessage
 */
public class NestedScreenCapture extends NestedMessage {
    private String link;
    private String label = MSG_SCREENCAPTURE;

    public NestedScreenCapture(String message, String link) {
        super(message);
        this.link = link;
    }

    public NestedScreenCapture(String message, String link, String label) {
        this(message, link);
        this.label = label;
    }

    public String getLabel() { return label; }

    public String getLink() { return link; }
}
