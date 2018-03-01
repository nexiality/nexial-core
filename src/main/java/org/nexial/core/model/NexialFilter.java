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

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.variable.CsvTransformer;

import static org.nexial.core.NexialConst.Data.NULL;
import static org.nexial.core.NexialConst.FlowControls.*;
import static org.nexial.core.model.NexialFilterComparator.Any;

/**
 * specification of what a filter looks like and does within the context of automation.  Filter is currently
 * used in {@link FlowControl} for conditional flow control, and in {@link CsvTransformer} as a way to filter out
 * portions of CSV content.
 */
public class NexialFilter implements Serializable {
    private String subject;
    private NexialFilterComparator comparator;
    private String controls;

    public NexialFilter(String subject, NexialFilterComparator comparator, String controls) {
        this.subject = subject;
        this.comparator = comparator;
        this.controls = controls;
    }

    public static NexialFilter newInstance(String filterText) throws IllegalArgumentException {
        String errPrefix = "invalid filter '" + filterText + "': ";

        if (StringUtils.isBlank(filterText)) { throw new IllegalArgumentException(errPrefix + "null"); }

        String regexFilter = NexialFilterComparator.getRegexFilter();

        // general pattern: [subject] [comparator] [control]
        if (!RegexUtils.isExact(filterText, regexFilter, true)) {
            throw new IllegalArgumentException(errPrefix + "does not match required format");
        }

        List<String> parts = RegexUtils.collectGroups(filterText, regexFilter, false, true);
        if (CollectionUtils.size(parts) != 3) {
            throw new IllegalArgumentException(errPrefix + "does not contain the required 3 parts of a valid filter");
        }

        String subject = StringUtils.trim(parts.get(0));
        if (StringUtils.isBlank(subject)) { throw new IllegalArgumentException(errPrefix + "empty/blank subject"); }

        NexialFilterComparator operator = NexialFilterComparator.findComparator(StringUtils.trim(parts.get(1)));

        String controls = StringUtils.trim(parts.get(2));
        if (StringUtils.isBlank(controls) && operator != Any) {
            throw new IllegalArgumentException(errPrefix + "empty/blank controls");
        }

        if (!operator.isValidControlValues(controls)) {
            throw new IllegalArgumentException(errPrefix + "'" + controls + "' is not suitable for '" + operator + "'");
        }

        return new NexialFilter(subject, operator, controls);
    }

    public String getSubject() { return subject; }

    public boolean isAnySubject() { return StringUtils.equals(subject, ANY_FIELD); }

    public NexialFilterComparator getComparator() { return comparator; }

    public String getControls() { return controls; }

    public boolean isMatch(String data) {
        if (comparator == Any) { return true; }

        data = data == null ? NULL : normalizeCondition(data);

        switch (comparator) {
            case Equal:
                return StringUtils.equals(data, normalizeCondition(controls));
            case NotEqual:
                return !StringUtils.equals(data, normalizeCondition(controls));

            case Match:
                return RegexUtils.isExact(data, controls);

            case Greater:
                return toDouble(data) > toDouble(controls);
            case GreaterOrEqual:
                return toDouble(data) >= toDouble(controls);
            case Lesser:
                return toDouble(data) < toDouble(controls);
            case LesserOrEqual:
                return toDouble(data) <= toDouble(controls);

            case StartsWith: {
                return TextUtils.isBetween(controls, IS_OPEN_TAG, IS_CLOSE_TAG) ?
                       toControlStream(controls).anyMatch(data::startsWith) :
                       StringUtils.startsWith(data, normalizeCondition(controls));
            }
            case EndsWith: {
                return TextUtils.isBetween(controls, IS_OPEN_TAG, IS_CLOSE_TAG) ?
                       toControlStream(controls).anyMatch(data::endsWith) :
                       StringUtils.endsWith(data, normalizeCondition(controls));
            }
            case Contains: {
                return TextUtils.isBetween(controls, IS_OPEN_TAG, IS_CLOSE_TAG) ?
                       toControlStream(controls).anyMatch(data::contains) :
                       StringUtils.contains(data, normalizeCondition(controls));
            }

            case Is:
            case In: {
                return StringUtils.isEmpty(data) || toControlStream(controls).anyMatch(data::equals);
            }

            case IsNot:
            case NotIn: {
                return
                    (StringUtils.equals(StringUtils.deleteWhitespace(controls), "[]") && StringUtils.isEmpty(data)) ||
                    toControlStream(controls).noneMatch(data::equals);
            }

            case Between: {
                Pair<Double, Double> range = toNumericRange(controls);
                double value = toDouble(data);
                return value >= range.getLeft() && value <= range.getRight();
            }

            default:
                throw new IllegalArgumentException("Invalid/unknown comparator: " + comparator);
        }
    }

    /**
     * not all conditions are specified within double quotes, but we should provide warning when there are
     * unbalanced quotes in condition.
     */
    protected static String normalizeCondition(String value) {
        if ((StringUtils.startsWith(value, "\"") && !StringUtils.endsWith(value, "\"")) ||
            (!StringUtils.startsWith(value, "\"") && StringUtils.endsWith(value, "\""))) {
            ConsoleUtils.error("Found unbalance double-quote -- possible data issue: " + value);
        }

        if (StringUtils.startsWith(value, "\"") && StringUtils.endsWith(value, "\"")) {
            value = StringUtils.unwrap(value, "\"");

			/*
			// temp. substitute for escaped double quotes
			value = StringUtils.replace(value, "\\\"", "`");

			// get substring between double quotes
			value = StringUtils.substringBetween(value, "\"");

			// put back the escape double quotes
			value = StringUtils.replace(value, "`", "\"");
			*/
        } else {
            // could be literal number, boolean, string.  But we need to trim because it's likely to have
            // space(s) between conditions.
            value = StringUtils.trim(value);
        }

        return value;
    }

    protected Pair<Double, Double> toNumericRange(String controls) {
        controls = StringUtils.trim(StringUtils.substringBetween(controls, IS_OPEN_TAG, IS_CLOSE_TAG));
        String[] array = StringUtils.split(controls, ",");
        if (ArrayUtils.getLength(array) != 2) {
            throw new IllegalArgumentException("range controls must be exactly 2 value (low, high): " + controls);
        }

        double num1 = toDouble(array[0]);
        double num2 = toDouble(array[1]);
        return num1 > num2 ? new ImmutablePair<>(num2, num1) : new ImmutablePair<>(num1, num2);
    }

    protected Stream<String> toControlStream(String controls) {
        controls = StringUtils.trim(StringUtils.substringBetween(controls, IS_OPEN_TAG, IS_CLOSE_TAG));
        return Arrays.stream(StringUtils.split(controls, ","))
                     .map(StringUtils::trim)
                     .map(NexialFilter::normalizeCondition);
    }

    protected static boolean canBeNumber(String controls) { return NumberUtils.isCreatable(numericReady(controls)); }

    protected static double toDouble(String controls) { return NumberUtils.toDouble(numericReady(controls)); }

    protected static String numericReady(String controls) {
        controls = StringUtils.unwrap(controls, "\"");
        controls = StringUtils.unwrap(controls, "'");
        controls = StringUtils.trim(controls);
        controls = StringUtils.removeStart(controls, "$");
        return StringUtils.deleteWhitespace(controls);
    }

}
