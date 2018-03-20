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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.FlowControls.IS_CLOSE_TAG;
import static org.nexial.core.NexialConst.FlowControls.IS_OPEN_TAG;
import static org.nexial.core.model.NexialFilter.ITEM_SEP;

/** all possible ways to evaluate a filter */
public enum NexialFilterComparator {
    Equal("="),
    NotEqual("!="),

    Greater(">"),
    GreaterOrEqual(">="),
    Lesser("<"),
    LesserOrEqual("<="),

    Is("is"),
    IsNot("is not"),
    NotIn("not in"),
    In("in"),

    Between("between"),
    Contains("contain"),
    StartsWith("start with"),
    EndsWith("end with"),
    Match("match"),

    Any(null);

    // todo: "is empty", "is not found", "with length of.."

    private static final String REGEX_CONTROLS = "(\".+?\"|.+?)";
    private static final String REGEX_FILTER = initRegexFilter();
    private String symbol;

    interface InternalMappings {
        Map<String, NexialFilterComparator> COMPARATOR_MAP = new HashMap<>();

    }

    NexialFilterComparator(String symbol) {
        this.symbol = symbol;
        if (symbol != null) { InternalMappings.COMPARATOR_MAP.put(symbol, this); }
    }

    public String getSymbol() { return symbol; }

    public static NexialFilterComparator findComparator(String symbol) {
        return symbol == null ? Any : InternalMappings.COMPARATOR_MAP.get(symbol);
    }

    public static String getRegexFilter() { return REGEX_FILTER; }

    /**
     * determine if the string presentation of {@code controls} is meaningful/usable for a given operator.
     */
    public boolean isValidControlValues(String controls) {
        // any - don't care
        if (symbol == null) { return true; }

        switch (this) {
            case Equal:
            case NotEqual:
            case Match: {
                return StringUtils.isNotBlank(controls);
            }

            case Greater:
            case GreaterOrEqual:
            case Lesser:
            case LesserOrEqual: {
                return NexialFilter.canBeNumber(controls);
            }

            case StartsWith:
            case EndsWith:
            case Contains: {
                // supports both single value or list
                // either enclosed in [...] or not, cannot have dangling bracket
                return StringUtils.isNotBlank(controls) &&
                       StringUtils.startsWith(controls, IS_OPEN_TAG) ==
                       StringUtils.endsWith(controls, IS_CLOSE_TAG);
            }

            case Is:
            case In:
            case IsNot:
            case NotIn: {
                return TextUtils.isBetween(controls, IS_OPEN_TAG, IS_CLOSE_TAG);
            }

            case Between: {
                if (!TextUtils.isBetween(controls, IS_OPEN_TAG, IS_CLOSE_TAG)) { return false; }

                controls = StringUtils.substringBetween(controls, IS_OPEN_TAG, IS_CLOSE_TAG);
                String[] parts = StringUtils.split(controls, ITEM_SEP);
                return ArrayUtils.getLength(parts) == 2 &&
                       NexialFilter.canBeNumber(parts[0]) &&
                       NexialFilter.canBeNumber(parts[1]);
            }

            default: {
                ConsoleUtils.error("Invalid/unknown operator: " + this);
                return false;
            }
        }
    }

    private static String initRegexFilter() {
        try {
            Class.forName(NexialFilterComparator.class.getName());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unable to load NexialFilterComparator!", e);
        }

        Map<Object, Object> replacements = new HashMap<>();
        replacements.put("!", "\\!");
        replacements.put("=", "\\=");
        replacements.put("<", "\\<");
        replacements.put(">", "\\>");

        StringBuilder regexOperator = new StringBuilder("(");
        Arrays.stream(NexialFilterComparator.values()).forEach(operator ->
                                                                   regexOperator
                                                                       .append(TextUtils.replace(operator.getSymbol(),
                                                                                                 replacements))
                                                                       .append("|"));
        String regexOps = StringUtils.removeEnd(regexOperator.toString(), "|") + ")";
        // hack: order is important. "is" must come after "is not"
        regexOps = StringUtils.replace(regexOps, "is|is not", "is not|is");

        // narrative:
        // - start with any spaces
        // - capture "subject"
        // - at least 1 space
        // - capure "operator"
        // - at least 1 space
        // - capture "controls"
        // - end with any space
        return "^\\s*" + REGEX_CONTROLS + "\\s+" + regexOps + "\\s+" + REGEX_CONTROLS + "\\s*$";
    }
}
