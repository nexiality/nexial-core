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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.commons.utils.TextUtils.ListItemConverter;
import org.nexial.core.ExecutionThread;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.variable.CsvTransformer;
import org.nexial.core.variable.Expression;
import org.nexial.core.variable.ExpressionParser;
import org.nexial.core.variable.TypeConversionException;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;

import static org.nexial.core.NexialConst.Data.NULL;
import static org.nexial.core.NexialConst.FILTER_TEMP_DELIM1;
import static org.nexial.core.NexialConst.FILTER_TEMP_DELIM2;
import static org.nexial.core.NexialConst.FlowControls.*;
import static org.nexial.core.model.NexialFilterComparator.Any;

/**
 * specification of what a filter looks like and does within the context of automation.  Filter is currently
 * used in {@link FlowControl} for conditional flow control, and in {@link CsvTransformer} as a way to filter out
 * portions of CSV content.
 */
public class NexialFilter implements Serializable {
    protected String subject;
    protected NexialFilterComparator comparator;
    protected String controls;
    protected List<String> controlList;
    static final String ITEM_SEP = "|";
    static final String PREFIX_KEY = "^^^";
    private static final List<String> EMPTY_ALIASES = Arrays.asList("null", "\"\"", "(empty)", "");

    public static class ListItemConverterImpl implements ListItemConverter<NexialFilter> {
        @Override
        public NexialFilter convert(String data) {
            data = StringUtils.replace(data, FILTER_TEMP_DELIM1, "\\" + ITEM_SEP);
            data = StringUtils.replace(data, FILTER_TEMP_DELIM2, ITEM_SEP);
            return newInstance(data);
        }
    }

    public NexialFilter(String subject, NexialFilterComparator comparator, String controls) {
        this.subject = subject;
        this.comparator = comparator;
        this.controls = controls;
    }

    public NexialFilter(String subject, NexialFilterComparator operator, Pair<String, List<String>> controlValues) {
        this.subject = subject;
        this.comparator = operator;
        this.controls = controlValues.getKey();
        this.controlList = controlValues.getValue();
    }

    public static NexialFilter newInstance(String filterText) throws IllegalArgumentException {
        String errPrefix = "invalid filter '" + filterText + "': ";

        if (StringUtils.isBlank(filterText)) { throw new IllegalArgumentException(errPrefix + "null"); }

        //identify expression
        ExecutionContext context = ExecutionThread.get();
        ExpressionParser expressionParser = new ExpressionParser(context);
        Map<String, String> expressionMap = new HashMap<>();
        String changedFilterText = filterText;
        try {
            int i = 0;
            while (changedFilterText.contains("[")) {
                Expression ex = expressionParser.parse(changedFilterText, true);
                if (ex == null) { break; }
                String expText = ex.getOriginalExpression();
                changedFilterText = changedFilterText.replace(expText, PREFIX_KEY + i);
                expressionMap.put(PREFIX_KEY + i, expText);
                i++;
            }
        } catch (TypeConversionException | NullPointerException e) {
            // No error handling case
        }

        // check for unary filter: true|false|!${var}|${var}
        String unaryFilter = filterText.trim();
        if (RegexUtils.isExact(unaryFilter, REGEX_IS_UNARY_FILTER)) {
            // could be an unary filter
            boolean negate = StringUtils.startsWith(unaryFilter, "!${") || StringUtils.startsWith(unaryFilter, "not ");
            return new TrueOrFalseFilter(unaryFilter, negate);
        }

        // general pattern: [subject] [comparator] [control]
        String regexFilter = NexialFilterComparator.getRegexFilter();
        if (!RegexUtils.isExact(changedFilterText, regexFilter, true)) {
            throw new IllegalArgumentException(errPrefix + "does not match required format");
        }

        List<String> parts = RegexUtils.collectGroups(changedFilterText, regexFilter, false, true);
        if (CollectionUtils.size(parts) != 3) {
            throw new IllegalArgumentException(errPrefix + "does not contain the required 3 parts of a valid filter");
        }

        String subject = StringUtils.trim(parts.get(0));
        if (StringUtils.isBlank(subject)) { throw new IllegalArgumentException(errPrefix + "empty/blank subject"); }

        NexialFilterComparator operator = NexialFilterComparator.findComparator(parts.get(1));
        String contValue = StringUtils.trim(parts.get(2));

        //revert replaced expression
        if (expressionMap.size() > 0) {
            subject = replaceKey(expressionMap, subject);
            contValue = replaceKey(expressionMap, contValue);
        }

        Pair<String, List<String>> controlValues = operator.formatControlValues(contValue);

        return new NexialFilter(subject, operator, controlValues);
    }

    @NotNull
    private static String replaceKey(final Map<String, String> expressionMap, String text) {
        if (text == null || MapUtils.isEmpty(expressionMap)) { return null; }
        if (text.contains(PREFIX_KEY)) {
            for (String key : expressionMap.keySet()) {
                if (text.contains(key)) {
                    String value = expressionMap.get(key);
                    text = text.replace(key, value);
                    text = replaceKey(expressionMap, text);
                }
            }
        }
        return text;
    }

    public String getSubject() { return subject; }

    public boolean isAnySubject() { return StringUtils.equals(subject, ANY_FIELD); }

    public NexialFilterComparator getComparator() { return comparator; }

    public String getControls() { return controls; }

    public List<String> getControlList() { return controlList; }

    public boolean isMatch(ExecutionContext context, String msgPrefix) {

        String actual = context.replaceTokens(subject);

        // for Is operator, we will defer the double-quote-wrap until isAtLeastOneMatched()
        String expected = context.replaceTokens(controls);

        String msg;

        if (msgPrefix == null) {
            msg = null;
            // assign empty for msgPrefix for some methods to avoid null pointer exception
            msgPrefix = "";
        } else {
            msg = msgPrefix + "(";
            msg += context.containsCrypt(subject) ? subject : actual;
            msg += comparator.getSymbol();
            msg += context.containsCrypt(controls) ? controls : expected;
            msg += ")\t\t=> ";
        }

        boolean result;
        switch (comparator) {
            // always good
            case Any -> result = true;
            case Equal, Equal_2 -> result = isEqualsTextOrNumeric(actual, expected);
            case NotEqual, NotEqual_2 -> result = !isEqualsTextOrNumeric(actual, expected);
            case Greater, Greater_2, GreaterOrEqual, GreaterOrEqual_2, Lesser, Lesser_2, LesserOrEqual, LesserOrEqual_2 ->
                result = isNumericMatch(actual, context, msgPrefix);
            case In, Is -> result = isAtLeastOneMatched(actual, context);
            case IsNot, NotIn -> result = isNoneMatched(actual, context);
            case Between -> result = isInRange(actual, context, msgPrefix);
            case StartsWith, EndsWith, Contain, NotStartsWith, NotEndsWith, NotContain ->
                result = isMatchingStringCompare(actual, context);
            case Match -> result = isMatchByRegex(actual, context);
            case HasLengthOf -> result = isNumericMatch(String.valueOf(StringUtils.length(actual)), context, msgPrefix);
            case IsEmpty -> result = StringUtils.isEmpty(actual);
            case IsNotEmpty -> result = StringUtils.isNotEmpty(actual);
            case IsDefined -> result = context.hasData(subject);
            case IsUndefined -> result = !context.hasData(subject);
            case ReadableFileWithSize -> {
                if (!NumberUtils.isCreatable(expected)) {
                    ConsoleUtils.error(msgPrefix + "NOT A NUMBER: " + expected);
                    return false;
                }
                long expectedFileLength = NumberUtils.toLong(expected);
                result = FileUtil.isFileReadable(actual, expectedFileLength);
                if (expectedFileLength < 0) { result = !result; }
            }
            case NotReadableFile -> result = !FileUtil.isFileReadable(actual);
            case ReadableFile -> result = FileUtil.isFileReadable(actual);
            case NotReadablePath -> result = !FileUtil.isDirectoryReadable(actual);
            case ReadablePath -> result = FileUtil.isDirectoryReadable(actual);
            case NotEmptyPath -> result = !FileUtil.isEmptyDirectory(actual);
            case EmptyPath -> result = FileUtil.isEmptyDirectory(actual);
            case ContainFilePattern, ContainFile ->
                result = FileUtil.isDirectoryReadable(actual) && FileUtil.listFiles(actual, expected, false).size() > 0;
            case HasFileContentPattern -> result = FileUtil.isContentMatched(actual, expected, true);
            case HasFileContent -> result = FileUtil.isContentMatched(actual, expected, false);
            case LastModifiedGreater, LastModifiedLesser, LastModifiedEqual ->
                result = compareLastModified(actual, expected);
            default -> {
                ConsoleUtils.error("Unsupported comparison: " + comparator.getSymbol());
                result = false;
            }
        }

        if (msg != null) { ConsoleUtils.log(msg + (!result ? "NOT " : "") + "MATCHED"); }
        return result;
    }

    public boolean isMatch(String data) { return isMatch(data, false); }

        /**
         * used for `CSV Expression` fetch, filter and removeRows operations
         */
    public boolean isMatch(String data, boolean caseSensitive) {
        if (comparator == Any) { return true; }

        data = data == null ? NULL : normalizeCondition(data);

        switch (comparator) {
            case Equal, Equal_2 -> {
                return isEqualsTextOrNumeric(data, normalizeCondition(controls));
            }
            case NotEqual, NotEqual_2 -> {
                return !isEqualsTextOrNumeric(data, normalizeCondition(controls));
            }
            case Greater, Greater_2 -> {
                return toDouble(data) > toDouble(controls);
            }
            case GreaterOrEqual, GreaterOrEqual_2 -> {
                return toDouble(data) >= toDouble(controls);
            }
            case Lesser, Lesser_2 -> {
                return toDouble(data) < toDouble(controls);
            }
            case LesserOrEqual, LesserOrEqual_2 -> {
                return toDouble(data) <= toDouble(controls);
            }
            case Between -> {
                Pair<Double, Double> range = toNumericRange(controls);
                double value = toDouble(data);
                return value >= range.getLeft() && value <= range.getRight();
            }
            case StartsWith -> {
                return TextUtils.isBetween(controls, IS_OPEN_TAG, IS_CLOSE_TAG) ?
                       toControlStream(controls).anyMatch(data::startsWith) :
                       StringUtils.startsWith(data, normalizeCondition(controls));
            }
            case NotStartsWith -> {
                return TextUtils.isBetween(controls, IS_OPEN_TAG, IS_CLOSE_TAG) ?
                       toControlStream(controls).noneMatch(data::startsWith) :
                       !StringUtils.startsWith(data, normalizeCondition(controls));
            }
            case EndsWith -> {
                return TextUtils.isBetween(controls, IS_OPEN_TAG, IS_CLOSE_TAG) ?
                       toControlStream(controls).anyMatch(data::endsWith) :
                       StringUtils.endsWith(data, normalizeCondition(controls));
            }
            case NotEndsWith -> {
                return TextUtils.isBetween(controls, IS_OPEN_TAG, IS_CLOSE_TAG) ?
                       toControlStream(controls).noneMatch(data::endsWith) :
                       !StringUtils.endsWith(data, normalizeCondition(controls));
            }
            case Contain -> {
                return TextUtils.isBetween(controls, IS_OPEN_TAG, IS_CLOSE_TAG) ?
                       toControlStream(controls).anyMatch(data::contains) :
                       StringUtils.contains(data, normalizeCondition(controls));
            }
            case NotContain -> {
                return TextUtils.isBetween(controls, IS_OPEN_TAG, IS_CLOSE_TAG) ?
                       toControlStream(controls).noneMatch(data::contains) :
                       !StringUtils.contains(data, normalizeCondition(controls));
            }
            case Match -> {
                return RegexUtils.isExact(data, controls, true, caseSensitive);
            }
            case Is, In -> {
                // true if `controls` is empty and data is also empty, or data is found in `controls`
                return
                    (StringUtils.equals(StringUtils.deleteWhitespace(controls), IS_OPEN_TAG + IS_CLOSE_TAG) &&
                     StringUtils.isEmpty(data)) ||
                    toControlStream(controls).anyMatch(data::equals);
            }
            case IsNot, NotIn -> {
                // true if `controls` is [] and data is also empty, or data is not found in `controls`
                return
                    (StringUtils.equals(StringUtils.deleteWhitespace(controls), IS_OPEN_TAG + IS_CLOSE_TAG) &&
                     !StringUtils.isEmpty(data)) ||
                    toControlStream(controls).noneMatch(data::equals);
            }

            // not applicable in this case
            case IsDefined, IsUndefined -> throw new IllegalArgumentException("Not applicable filter: " + comparator);
            case IsEmpty -> {
                return StringUtils.isEmpty(data);
            }
            case IsNotEmpty -> {
                return StringUtils.isNotEmpty(data);
            }
            case HasLengthOf -> {
                return StringUtils.length(data) == toDouble(controls);
            }
            case ReadableFileWithSize -> {
                if (!NumberUtils.isCreatable(controls)) {
                    throw new IllegalArgumentException("NOT A NUMBER: " + controls);
                }
                long expectedFileLength = NumberUtils.toLong(controls);
                boolean result = FileUtil.isFileReadable(data, expectedFileLength);
                if (expectedFileLength < 0) { result = !result; }
                return result;
            }
            case NotReadableFile -> {
                return !FileUtil.isFileReadable(data);
            }
            case ReadableFile -> {
                return FileUtil.isFileReadable(data);
            }
            case NotReadablePath -> {
                return !FileUtil.isDirectoryReadable(data);
            }
            case ReadablePath -> {
                return FileUtil.isDirectoryReadable(data);
            }
            case NotEmptyPath -> {
                return !FileUtil.isEmptyDirectory(data);
            }
            case EmptyPath -> {
                return FileUtil.isEmptyDirectory(data);
            }
            default -> throw new IllegalArgumentException("Invalid/unknown comparator: " + comparator.getSymbol());
        }
    }

    @Override
    public String toString() {
        return subject + (comparator == Any ? " MATCH ANY " : comparator) + StringUtils.defaultString(controls);
    }

    protected static boolean isEqualsTextOrNumeric(String expected, String actual) {
        if (StringUtils.isEmpty(expected) && EMPTY_ALIASES.contains(actual) ||
            StringUtils.equals(expected, actual)) { return true; }

        return NumberUtils.isCreatable(numericReady(expected)) &&
               NumberUtils.isCreatable(numericReady(actual)) &&
               toDouble(expected) == toDouble(actual);
    }

    /**
     * not all conditions are specified within double quotes, but we should provide warning when there are
     * unbalanced quotes in condition.
     */
    protected static String normalizeCondition(String value) {
        value = StringUtils.trim(value);
        if ((StringUtils.startsWith(value, "\"") && !StringUtils.endsWith(value, "\"")) ||
            (!StringUtils.startsWith(value, "\"") && StringUtils.endsWith(value, "\""))) {
            ConsoleUtils.error("Found unbalance double-quote -- possible data issue: " + value);
        }

        if (StringUtils.startsWith(value, "\"") && StringUtils.endsWith(value, "\"")) {
            value = StringUtils.unwrap(value, "\"");
        } else {
            // could be literal number, boolean, string.  But we need to trim because it's likely to have
            // space(s) between conditions.
            value = StringUtils.trim(value);
        }

        return value;
    }

    protected static Pair<Double, Double> toNumericRange(String controls) {
        controls = StringUtils.trim(StringUtils.substringBetween(controls, IS_OPEN_TAG, IS_CLOSE_TAG));
        String[] array = StringUtils.split(controls, ITEM_SEP);
        if (ArrayUtils.getLength(array) != 2) {
            throw new IllegalArgumentException("range controls must be exactly 2 value (low, high): " + controls);
        }

        double num1 = toDouble(array[0]);
        double num2 = toDouble(array[1]);
        return num1 > num2 ? new ImmutablePair<>(num2, num1) : new ImmutablePair<>(num1, num2);
    }

    protected static Stream<String> toControlStream(String controls) {
        controls = StringUtils.trim(StringUtils.substringBetween(controls, IS_OPEN_TAG, IS_CLOSE_TAG));
        return Arrays.stream(StringUtils.split(controls, ITEM_SEP))
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

    protected boolean isMatchByRegex(String actual, ExecutionContext context) {
        return RegexUtils.isExact(actual, context.replaceTokens(controls));
    }

    protected boolean isMatchingStringCompare(String actual, ExecutionContext context) {
        String expected =
            context.replaceTokens(CollectionUtils.isNotEmpty(controlList) ? controlList.get(0) : controls);

        return switch (comparator) {
            case StartsWith -> StringUtils.startsWith(actual, expected);
            case NotStartsWith -> !StringUtils.startsWith(actual, expected);
            case EndsWith -> StringUtils.endsWith(actual, expected);
            case NotEndsWith -> !StringUtils.endsWith(actual, expected);
            case Contain -> StringUtils.contains(actual, expected);
            case NotContain -> !StringUtils.contains(actual, expected);
            default -> false;
        };
    }

    protected boolean isInRange(String actual, ExecutionContext context, String msgPrefix) {
        msgPrefix = msgPrefix + " [" + comparator.getSymbol() + "] - ";

        if (!NumberUtils.isCreatable(actual)) {
            ConsoleUtils.error(msgPrefix + "NOT A NUMBER: " + actual);
            return false;
        }

        if (CollectionUtils.size(controlList) != 2) {
            ConsoleUtils.error(msgPrefix + "EXPECTS [min|max]: " + controls);
            return false;
        }

        String minVal = context.replaceTokens(StringUtils.trim(controlList.get(0)));
        if (StringUtils.startsWith(minVal, "\"") && StringUtils.endsWith(minVal, "\"")) {
            minVal = StringUtils.trim(StringUtils.substringBetween(minVal, "\"", "\""));
        }
        if (!NumberUtils.isCreatable(minVal)) {
            ConsoleUtils.error(msgPrefix + "NOT A NUMBER: " + minVal);
            return false;
        }

        String maxVal = context.replaceTokens(StringUtils.trim(controlList.get(1)));
        if (StringUtils.startsWith(maxVal, "\"") && StringUtils.endsWith(maxVal, "\"")) {
            maxVal = StringUtils.trim(StringUtils.substringBetween(maxVal, "\"", "\""));
        }
        if (!NumberUtils.isCreatable(maxVal)) {
            ConsoleUtils.error(msgPrefix + "NOT A NUMBER: " + maxVal);
            return false;
        }

        return CheckUtils.isInRange(NumberUtils.toDouble(actual),
                                    NumberUtils.toDouble(minVal),
                                    NumberUtils.toDouble(maxVal));
    }

    protected boolean isAtLeastOneMatched(String actual, ExecutionContext context) {
        return toSubstitutedControlList(context).contains(actual);
    }

    protected boolean isNoneMatched(String actual, ExecutionContext context) {
        return !toSubstitutedControlList(context).contains(actual);
    }

    protected boolean compareLastModified(String path, String lastModified) {
        if (!(FileUtil.isFileReadable(path) || FileUtil.isDirectoryReadable(path))) {
            ConsoleUtils.error("File/directory " + path + " doesn't exist or is not readable");
            return false;
        }

        long actualLastModified = new File(path).lastModified();
        long expectedLastModified = Long.parseLong(lastModified);

        return switch (comparator) {
            case LastModifiedGreater -> actualLastModified > expectedLastModified;
            case LastModifiedLesser -> actualLastModified < expectedLastModified;
            case LastModifiedEqual -> actualLastModified == expectedLastModified;
            default -> false;
        };
    }

    protected boolean isNumericMatch(String actual, ExecutionContext context, String msgPrefix) {
        msgPrefix = msgPrefix + " [" + comparator.getSymbol() + "] - ";

        if (!NumberUtils.isCreatable(actual)) {
            ConsoleUtils.error(msgPrefix + "NOT A NUMBER: " + actual);
            return false;
        }

        String expected = context.replaceTokens(controls);
        if (!NumberUtils.isCreatable(expected)) {
            ConsoleUtils.error(msgPrefix + "NOT A NUMBER: " + expected);
            return false;
        }

        double actualNum = NumberUtils.toDouble(actual);
        double expectedNum = NumberUtils.toDouble(expected);

        return switch (comparator) {
            case Greater, Greater_2 -> actualNum > expectedNum;
            case GreaterOrEqual, GreaterOrEqual_2 -> actualNum >= expectedNum;
            case Lesser, Lesser_2 -> actualNum < expectedNum;
            case LesserOrEqual, LesserOrEqual_2 -> actualNum <= expectedNum;
            case HasLengthOf -> actualNum == expectedNum;
            default -> false;
        };
    }

    @NotNull
    private List<String> toSubstitutedControlList(ExecutionContext context) {
        List<String> matchList = new ArrayList<>();
        controlList.forEach(control -> matchList.add(context.replaceTokens(control)));
        return matchList;
    }
}
