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

package org.nexial.core.model;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.model.NexialFilterComparator.TrueOrFalse;

public class TrueOrFalseFilter extends NexialFilter {
    protected boolean negate;

    public TrueOrFalseFilter(String subject, boolean negate) {
        super(subject, TrueOrFalse, "");
        this.negate = negate;
    }

    public boolean isNegate() { return negate; }

    public boolean isMatch(ExecutionContext context, String msgPrefix) {
        String filter = StringUtils.trim(StringUtils.removeStart(StringUtils.removeStart(subject, "!"), "not "));
        boolean actual = BooleanUtils.toBoolean(context.replaceTokens(filter));
        if (negate) { actual = !actual; }
        boolean result = actual;
        ConsoleUtils.log(msgPrefix + "(" + subject + " = " + actual + ")\t\t=> " + (!result ? "NOT " : "") + "MATCHED");
        return result;
    }

    /**
     * used for `CSV Expression` fetch, filter and removeRows operations
     */
    public boolean isMatch(String data) {
        if (StringUtils.equals(subject, "true") || StringUtils.equals(subject, "false")) {
            return StringUtils.equals(subject, data);
        }

        return negate != BooleanUtils.toBoolean(data);
    }

    @Override
    public String toString() { return subject; }

}
