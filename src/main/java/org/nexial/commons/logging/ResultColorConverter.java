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
 */

package org.nexial.commons.logging;

import org.nexial.core.NexialConst;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;

import static ch.qos.logback.classic.Level.ERROR;
import static ch.qos.logback.core.pattern.color.ANSIConstants.*;
import static org.nexial.core.excel.ExcelConfig.*;

public class ResultColorConverter extends ForegroundCompositeConverterBase<ILoggingEvent> {
    @Override
    protected String getForegroundColorCode(ILoggingEvent event) {
        Level level = event.getLevel();
        if (level == ERROR) { return BOLD + RED_FG; }

        String message = event.getMessage();
        if (message.contains(" - " + MSG_PASS)) { return GREEN_FG; }
        if (message.contains(" - [repeat-until] " + MSG_PASS)) { return GREEN_FG; }
        if (message.contains(" - " + MSG_FAIL)) { return BOLD + RED_FG; }
        if (message.contains(" - [repeat-until] " + MSG_FAIL)) { return BOLD + RED_FG; }
        if (message.contains(" - " + MSG_WARN)) { return YELLOW_FG; }
        if (message.contains(" - " + MSG_SKIPPED)) { return MAGENTA_FG; }
        if (message.contains(" - [repeat-until] " + MSG_SKIPPED)) { return MAGENTA_FG; }
        if (message.contains(" - " + NexialConst.MSG_ABORT)) { return BOLD + BLUE_FG; }
        if (message.startsWith(NexialConst.MSG_ABORT)) { return BOLD + BLUE_FG; }

        return DEFAULT_FG;
    }
}
