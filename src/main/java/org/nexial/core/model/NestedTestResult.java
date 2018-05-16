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

package org.nexial.core.model;

import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.utils.MessageUtils;

/**
 *
 */
public class NestedTestResult extends NestedMessage {

    public NestedTestResult(Worksheet worksheet, String message) {
        super(worksheet, message);
    }

    public NestedTestResult(Worksheet worksheet, String message, boolean isPass) {
        this(worksheet, MessageUtils.markResult(message, isPass));
    }
}
