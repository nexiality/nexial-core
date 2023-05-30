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

package org.nexial.commons.utils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.InvalidInputRuntimeException;
import org.nexial.core.utils.ConsoleUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import static java.awt.event.KeyEvent.CHAR_UNDEFINED;
import static java.lang.Character.UnicodeBlock.SPECIALS;
import static java.lang.System.lineSeparator;
import static org.nexial.commons.utils.TextUtils.CleanNumberStrategy.CSV;
import static org.nexial.commons.utils.TextUtils.CleanNumberStrategy.OCTAL;
import static org.nexial.commons.utils.TextUtils.DuplicateKeyStrategy.NotAllowed;
import static org.nexial.core.NexialConst.*;

/**
 * @author Mike Liu
 */
public final class TextUtils {

    public enum DuplicateKeyStrategy {
        FavorFirst, FavorLast, NotAllowed;

        public static DuplicateKeyStrategy toStrategy(String text) {
            if (StringUtils.isBlank(text)) { throw new IllegalArgumentException("Invalid strategy: " + text); }
            text = StringUtils.trim(text);
            if (StringUtils.equals(text, "error")) { return NotAllowed; }
            if (StringUtils.equals(text, "first")) { return FavorFirst; }
            if (StringUtils.equals(text, "last")) { return FavorLast; }
            throw new IllegalArgumentException("Unknown strategy: " + text);
        }
    }

    // FORM_UNFRIENDLY_TEXT and NEWLINES must match in size
    public static final String[] FORM_UNFRIENDLY_TEXT =
        new String[]{"&lt;BR/&gt;", "&lt;br/&gt;", "<BR/>", "<BR>", "<br>", "<br/>", "\r"};
    public static final String[] NEWLINES = new String[]{"\n", "\n", "\n", "\n", "\n", "\n", "\n"};
    // INLINE_UNFRIENDLY_TEXT and INLINE_BR must match in size
    public static final String[] INLINE_UNFRIENDLY_TEXT =
        new String[]{"\n", "\r\n", "\n\r", "&lt;BR/&gt;", "&lt;br/&gt;"};
    public static final String[] INLINE_BR = new String[]{"<br/>", "<br/>", "<br/>", "<br/>", "<br/>"};

    private static final Map<String, String> ESCAPE_HTML_MAP = initDefaultEscapeHtmlMapping();
    private final static Map<String, String> CSV_SAFE_REPLACEMENT = TextUtils.toMap("=",
                                                                                    " \n = ",
                                                                                    " \r\n = ",
                                                                                    " \r = ",
                                                                                    " \t = ",
                                                                                    " \n= ",
                                                                                    " \r\n= ",
                                                                                    " \r= ",
                                                                                    " \t= ",
                                                                                    "\n = ",
                                                                                    "\r\n = ",
                                                                                    "\r = ",
                                                                                    "\t = ");
    private static final int TO_STRING_KEY_LENGTH = 14;

    // polymatcher
    private static final List<String> MATCHES = new ArrayList<>();
    public static final String CONTAIN = registerPolyMatcherKeyword("CONTAIN:");
    public static final String CONTAIN_ANY_CASE = registerPolyMatcherKeyword("CONTAIN_ANY_CASE:");
    public static final String START = registerPolyMatcherKeyword("START:");
    public static final String START_ANY_CASE = registerPolyMatcherKeyword("START_ANY_CASE:");
    public static final String END = registerPolyMatcherKeyword("END:");
    public static final String END_ANY_CASE = registerPolyMatcherKeyword("END_ANY_CASE:");
    public static final String REGEX = registerPolyMatcherKeyword(REGEX_PREFIX);
    public static final String EXACT = registerPolyMatcherKeyword("EXACT:");
    public static final String EMPTY = registerPolyMatcherKeyword("EMPTY:");
    public static final String BLANK = registerPolyMatcherKeyword("BLANK:");
    public static final String LENGTH = registerPolyMatcherKeyword("LENGTH:");
    public static final String NUMERIC = registerPolyMatcherKeyword("NUMERIC:");
    public static final String REGEX_NUMERIC_COMPARE = "^\\s*([><=!]+)\\s*([\\d\\-\\.]+)\\s*$";

    /** retrieve string before the first whitespace character found in {@literal text} */
    public static String substringBeforeWhitespace(String text) { return substringOnWhitespace(text, true); }

    /** retrieve string after the first whitespace character found in {@literal text} */
    public static String substringAfterWhitespace(String text) { return substringOnWhitespace(text, false); }

    private static String substringOnWhitespace(String text, boolean before) {
        if (StringUtils.isEmpty(text)) { return text; }

        int pos = -1;
        final int strLen = text.length();
        for (int i = 0; i < strLen; i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                pos = i;
                break;
            }
        }

        if (pos == -1) { return ""; }
        return before ? StringUtils.substring(text, 0, pos) : StringUtils.substring(text, pos + 1);
    }

    private static String registerPolyMatcherKeyword(String keyword) {
        MATCHES.add(keyword);
        return keyword;
    }

    /**
     * line break conversion strategies -- currently only two, namely (1)
     * convert to HTML BR tag, or (2) join with previous line.
     */
    public enum LineBreakConversion {
        CONVERT_TO_BR(true, false),
        JOIN_WITH_PREVIOUS_LINE(false, true);

        private final boolean br;
        private final boolean join;

        LineBreakConversion(boolean br, boolean join) {
            this.br = br;
            this.join = join;
        }

        public String convert(String text) {
            if (br) {
                text = StringUtils.replace(text, "\n\r", "<br/>");
                text = StringUtils.replace(text, "\r\n", "<br/>");
                text = StringUtils.replace(text, "\n", "<br/>");
                text = StringUtils.replace(text, "\r", "<br/>");
                return text;
            }

            if (join) {
                text = StringUtils.remove(text, "\n\r");
                text = StringUtils.remove(text, "\r\n");
                text = StringUtils.remove(text, "\n");
                text = StringUtils.remove(text, "\r");
                return text;
            }

            return text;
        }
    }

    public enum CleanNumberStrategy {
        CSV(null, "£$%, \t"),
        REAL("0123456789.E", null),
        OCTAL("012345678", null),
        NON_DIGITS("0123456789", null);

        private final String keeps;
        private final String removes;

        CleanNumberStrategy(String keeps, String removes) {
            this.keeps = keeps;
            this.removes = removes;
        }

        public String getKeeps() { return keeps; }

        public String getRemoves() { return removes; }
    }

    /**
     * generic-based converter for each of the list candidate.  Implementation can convert each candidate into a type
     * of either {@code E} or subclass of {@code E};
     */
    public interface ListItemConverter<T> {
        T convert(String data);
    }

    public interface ExtractCellByPosition<T> {
        String getCell(T row, int position);
    }

    private TextUtils() { }

    public static boolean isPrintableChar(char c, boolean unicode) {
        if (unicode) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            return !Character.isISOControl(c) &&
                   c != CHAR_UNDEFINED &&
                   block != null &&
                   block != SPECIALS;
        } else {
            // ascii only
            return c >= 32 && c <= 126;
        }
    }

    public static String replaceStrings(String text, Map<String, String> searchAndReplaces) {
        if (StringUtils.isBlank(text)) { return text; }
        if (MapUtils.isEmpty(searchAndReplaces)) { return text; }

        String replaced = text;
        for (String search : searchAndReplaces.keySet()) {
            replaced = StringUtils.replace(replaced, search, searchAndReplaces.get(search));
        }

        return replaced;
    }

    public static String replace(String text, Map<Object, Object> searchAndReplaces) {
        if (StringUtils.isBlank(text)) { return text; }
        if (MapUtils.isEmpty(searchAndReplaces)) { return text; }

        String replaced = text;
        for (Object search : searchAndReplaces.keySet()) {
            replaced = StringUtils.replace(replaced,
                                           Objects.toString(search, ""),
                                           Objects.toString(searchAndReplaces.get(search), ""));
        }

        return replaced;
    }

    public static String replaceTokens(String text,
                                       String tokenPrefix,
                                       String tokenSuffix,
                                       Map<Object, Object> searchAndReplaces) {
        if (StringUtils.isBlank(text)) { return text; }
        if (MapUtils.isEmpty(searchAndReplaces)) { return text; }
        final String prefix = StringUtils.isEmpty(tokenPrefix) ? "{" : tokenPrefix;
        final String suffix = StringUtils.isEmpty(tokenSuffix) ? "}" : tokenSuffix;

        final String[] replaced = {text};
        searchAndReplaces.forEach((key, value) -> replaced[0] = StringUtils.replace(replaced[0],
                                                                                    prefix + key + suffix,
                                                                                    Objects.toString(value, "")));
        return replaced[0];
    }

    public static List<String> replaceItems(List<String> list, String find, String replaceWith) {
        if (CollectionUtils.isEmpty(list)) { return list; }
        if (StringUtils.isEmpty(find)) { return list; }

        List<String> newList = new ArrayList<>(list.size());
        list.forEach(item -> newList.add(StringUtils.replace(item, find, replaceWith)));
        return newList;
    }

    public static String insert(String text, int position, String insertWith) {
        if (StringUtils.isAnyEmpty(text, insertWith) || position < 0 || position > StringUtils.length(text)) {
            return text;
        }
        return StringUtils.substring(text, 0, position) + insertWith + StringUtils.substring(text, position);
    }

    /**
     * return new String that has {@code insert} after the {@code after} in {@code text}. Note that
     * {@code after} must be the found from the beginning of {@code text}.
     */
    public static String insertAfter(String text, String after, String insert) {
        if (StringUtils.isAnyEmpty(text, after, insert) || !StringUtils.contains(text, after)) { return text; }
        return StringUtils.substringBefore(text, after) + after + insert + StringUtils.substringAfter(text, after);
    }

    /**
     * return new String that has {@code insertWith} before the {@code before} character sequence in {@code text}. If
     * the {@code before} character sequence is not found, then {@code text} is returned.
     */
    public static String insertBefore(String text, String before, String insertWith) {
        if (StringUtils.isAnyEmpty(text, before, insertWith) || !StringUtils.contains(text, before)) { return text; }
        return StringUtils.substringBefore(text, before) + insertWith + before +
               StringUtils.substringAfter(text, before);
    }

    @NotNull
    public static Map<String, String> toMap(String delim, String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();

        if (ArrayUtils.isEmpty(pairs)) { return map; }
        if (StringUtils.isEmpty(delim)) { return map; }

        for (String pair : pairs) {
            map.put(StringUtils.substringBefore(pair, delim), StringUtils.substringAfter(pair, delim));
        }

        return map;
    }

    @NotNull
    public static Map<String, String> toMap(String text, String pairDelim, String nameValueDelim) {
        return toMap(text, pairDelim, nameValueDelim, false);
    }

    @NotNull
    public static Map<String, String> toMap(String text, String pairDelim, String nameValueDelim, boolean unescape) {
        Map<String, String> map = new LinkedHashMap<>();

        if (StringUtils.isEmpty(text)) { return map; }
        if (StringUtils.isEmpty(pairDelim)) { return map; }
        if (StringUtils.isEmpty(nameValueDelim)) { return map; }

        String[] pairs = StringUtils.splitByWholeSeparatorPreserveAllTokens(text, pairDelim);
        for (String pair : pairs) {
            String key = StringUtils.substringBefore(pair, nameValueDelim);
            if (unescape) { key = escapeLiterals(key); }

            String value = StringUtils.substringAfter(pair, nameValueDelim);
            if (unescape) { value = escapeLiterals(value); }

            map.put(key, value);
        }

        return map;
    }

    public static String[][] to2dArray(String text, String rowSeparator, String delim) {
        if (StringUtils.isEmpty(text)) { return new String[0][0]; }

        // defaults
        if (StringUtils.isEmpty(rowSeparator)) { rowSeparator = "\n"; }
        if (StringUtils.isEmpty(delim)) { delim = ","; }

        String[] rows = StringUtils.splitByWholeSeparatorPreserveAllTokens(text, rowSeparator);
        if (ArrayUtils.getLength(rows) < 1) { return new String[0][0]; }

        String[][] twoD = new String[rows.length][];
        for (int i = 0; i < rows.length; i++) {
            twoD[i] = StringUtils.splitPreserveAllTokens(rows[i], delim);
        }

        return twoD;
    }

    public static List<List<String>> to2dList(String text, String rowSep, String delim) {
        ArrayList<List<String>> twoD = new ArrayList<>();

        if (StringUtils.isEmpty(text)) { return twoD; }

        // defaults
        if (StringUtils.isEmpty(rowSep)) { rowSep = "\n"; }
        if (StringUtils.isEmpty(delim)) { delim = ","; }

        // special handling for single-char delim: any escaped delimiter will be temporarily displaced.
        String tempDelim = "~`~[DELIM]~`~";
        if (StringUtils.length(delim) == 1) { text = StringUtils.replace(text, "\\" + delim, tempDelim); }

        // special handling for single-char row separator: any escaped row separator will be temporarily displaced.
        String tempRowSep = "@!@[ROW]@!@";
        if (StringUtils.length(rowSep) == 1) { text = StringUtils.replace(text, "\\" + rowSep, tempRowSep); }

        String[] rows = StringUtils.splitByWholeSeparatorPreserveAllTokens(text, rowSep);
        if (ArrayUtils.getLength(rows) < 1) { return twoD; }

        String delimiter = delim;
        String rowDelim = rowSep;
        Arrays.stream(rows).forEach(row -> {
            List<String> list = new ArrayList<>();

            if (StringUtils.countMatches(row, "\"") > 1) {
                row = RegexUtils.replaceMultiLines(row, "\"(.+?)\\" + delimiter + "(.+?)\"", "$1" + tempDelim + "$2");
            }

            List<String> items = toListPreserveEmpty(row, delimiter, false);
            if (CollectionUtils.isNotEmpty(items)) {
                items.forEach(item -> list.add(
                    StringUtils.replace(StringUtils.replace(item, tempDelim, "\\" + delimiter),
                                        tempRowSep,
                                        "\\" + rowDelim)));
                twoD.add(list);
            }
        });

        return twoD;
    }

    /**
     * transform string to {@link List} (of String).  Use {@code delim} to determine the delimiter and {@code trim}
     * to determine if the delimited list should be trimmed returned.
     */
    public static <T> List<T> toList(String text, String delim, ListItemConverter<T> itemConverter) {
        if (StringUtils.isEmpty(text)) { return null; }
        if (StringUtils.isEmpty(delim)) { return null; }

        String[] items;
        if (StringUtils.isBlank(delim)) {
            items = text.split("(" + delim + ")+");
        } else {
            items = StringUtils.splitByWholeSeparatorPreserveAllTokens(text, delim);
        }

        if (items == null) { return null; }

        List<T> list = new ArrayList<>();
        Arrays.stream(items).forEach(s -> list.add(itemConverter.convert(s)));
        return list;
    }

    /**
     * transform string to {@link List} (of String).  Use {@code delim} to determine the delimiter and {@code trim}
     * to determine if the delimited list should be trimmed returned.
     */
    @NotNull
    public static List<String> toList(String text, String delim, boolean trim) {
        List<String> list = new ArrayList<>();
        if (StringUtils.isEmpty(text) || StringUtils.isEmpty(delim)) { return list; }

        String[] items;
        if (StringUtils.isBlank(delim)) {
            items = text.split("(" + delim + ")+");
        } else {
            items = StringUtils.length(delim) == 1 ?
                    StringUtils.splitPreserveAllTokens(text, delim) :
                    StringUtils.splitByWholeSeparatorPreserveAllTokens(text, delim);
        }

        if (items == null) { return list; }

        for (String item : items) {
            if (trim) { item = StringUtils.trim(item); }
            list.add(item);
        }

        int lastPos = list.size() - 1;
        if (trim && StringUtils.isEmpty(list.get(lastPos))) { list.remove(lastPos); }

        return list;
    }

    public static List<String> toListPreserveEmpty(String text, String delim, boolean trim) {
        if (StringUtils.isEmpty(text)) { return null; }
        if (StringUtils.isEmpty(delim)) { return null; }

        List<String> list = new ArrayList<>();
        while (StringUtils.isNotEmpty(text)) {
            if (StringUtils.contains(text, delim)) {
                String item = StringUtils.substringBefore(text, delim);
                if (trim) { item = StringUtils.trim(item); }
                list.add(item);
                text = StringUtils.substringAfter(text, delim);
            } else {
                if (trim) { text = StringUtils.trim(text); }
                list.add(text);
                break;
            }
        }

        return list;
    }

    /** convert {@code map} to a list (of string) */
    public static List<String> toList(Map map, String delim) {
        if (MapUtils.isEmpty(map)) { return null; }
        if (StringUtils.isEmpty(delim)) { throw new IllegalArgumentException("delim is missing"); }

        List<String> list = new ArrayList<>();
        for (Object key : map.keySet()) { list.add(key + delim + map.get(key)); }
        return list;
    }

    /**
     * transform string to {@link List} (of String) without discounting consecutive delimiters.
     * Consecutive delimiters would render empty value instead.
     * Use {@code delim} to determine the delimiter and {@code trim}
     * to determine if the delimited list should be trimmed returned.
     */
    public static List<String> toListPreserveTokens(String text, String delim, boolean trim) {
        if (StringUtils.isEmpty(text)) { return null; }
        if (StringUtils.isEmpty(delim)) { return null; }

        List<String> list = new ArrayList<>();

        String[] items = StringUtils.splitByWholeSeparatorPreserveAllTokens(text, delim);
        for (String item : items) {
            if (trim) { item = StringUtils.trim(item); }
            list.add(item);
        }

        return list;
    }

    public static String escapeDoubleQuotes(String text) {
        if (StringUtils.isEmpty(text)) { return text; }
        return StringUtils.replace(text, "\"", "\\\"");
    }

    public static String escapeDoubleQuotes(Object obj) {
        if (obj == null) { return null; }
        return escapeDoubleQuotes(obj.toString());
    }

    /**
     * checks its a valid word based on the last char is space or not
     *
     * @param word input String
     * @return true if its a not broken word
     */
    public static boolean isValidWord(String word) { return StringUtils.endsWith(word, " "); }

    /**
     * Retrieves the string content from text depending on startLength and endLength
     *
     * @return String
     */
    public static String getString(StringBuilder text, int startLength, int endLength) {
        if (text == null || text.length() < startLength || text.length() + 1 < endLength || endLength < startLength) {
            return null;
        }
        return text.substring(startLength, endLength);
    }

    /** Deletes the string content from text depending on startLength and endLength */
    public static void deleteString(StringBuilder text, int startLength, int endLength) {
        if (text == null || text.length() < startLength || text.length() + 1 < endLength || endLength < startLength) {
            return;
        }
        text.delete(startLength, endLength);
    }

    /** Right Trims the String content */
    public static String rtrim(String source) {
        if (source == null || source.isEmpty()) { return null; }
        StringBuilder trimContent = new StringBuilder(source);
        for (int loop = source.length() - 1; loop >= 0; loop--) {
            if (Character.isWhitespace(source.charAt(loop))) {
                trimContent.deleteCharAt(loop);
            } else {
                break;
            }
        }

        return trimContent.toString();
    }

    /**
     * trimLeadingZeros function trims leading zeros.
     * ex  For input value is  00023 output would be 23.
     * input value is  00000 output would be 0.
     * exception will result in empty string being returned.
     */
    public static String trimLeadingZeros(String value) {
        if (StringUtils.isBlank(value)) { return ""; }
        try {
            return Integer.parseInt(value) + "";
        } catch (Throwable e) {
            return "";
        }
    }

    /**
     * convert {@code array} into a string, with optional delimiter, prefix and suffix.  For example,
     * {@code toString(new String[]{"a", "b", "c"}, ",", "'", "'")} will return {@code "'a','b','c'"}
     */
    @NotNull
    public static String toString(String[] array, String delim, String prefix, String suffix) {
        if (array == null || array.length == 0) { return ""; }

        String p = StringUtils.defaultString(prefix);
        String s = StringUtils.defaultString(suffix);
        String d = StringUtils.defaultString(delim);

        StringBuilder sb = new StringBuilder();
        for (String value : array) { sb.append(p).append(value).append(s).append(d); }
        return sb.substring(0, sb.length() - d.length());
    }

    @NotNull
    public static String toCsvLine(String[] array, String delim, String recordDelim) {
        if (ArrayUtils.isEmpty(array)) { return recordDelim; }

        StringBuilder sb = new StringBuilder();
        for (String value : array) {
            String data = StringUtils.containsAny(value, delim, "\r", "\n") ?
                          TextUtils.wrapIfMissing(value, "\"", "\"") : value;
            sb.append(data).append(delim);
        }

        return StringUtils.removeEnd(sb.toString(), delim) + recordDelim;
    }

    @NotNull
    public static String toCsvLine(List<String> array, String delim, String recordDelim) {
        if (CollectionUtils.isEmpty(array)) { return recordDelim; }

        StringBuilder sb = new StringBuilder();
        for (String value : array) {
            String data = StringUtils.containsAny(value, delim, "\r", "\n") ?
                          TextUtils.wrapIfMissing(value, "\"", "\"") : value;
            sb.append(data).append(delim);
        }

        return StringUtils.removeEnd(sb.toString(), delim) + recordDelim;
    }

    public static String toString(Map map, String pairDelim, String nameValueDelim) {
        if (MapUtils.isEmpty(map)) { return ""; }
        if (StringUtils.isEmpty(pairDelim)) { throw new IllegalArgumentException("pairDelim is missing"); }
        if (StringUtils.isEmpty(nameValueDelim)) { throw new IllegalArgumentException("nameValueDelim is missing"); }

        StringBuilder sb = new StringBuilder();
        for (Object key : map.keySet()) {
            sb.append(key).append(nameValueDelim).append(map.get(key)).append(pairDelim);
        }
        // remove last pairDelim
        return sb.deleteCharAt(sb.length() - pairDelim.length()).toString();
    }

    @NotNull
    public static String toString(Collection list, String delim) { return CollectionUtil.toString(list, delim); }

    @NotNull
    public static String toString(Iterable<?> iterable, String delim, String nullValue) {
        return IterableUtils.toString(iterable, input -> input == null ? nullValue : input.toString(), delim, "", "");
    }

    @NotNull
    public static String toString(List<?> list, String delim, String prefix, String suffix) {
        if (CollectionUtils.isEmpty(list)) { return ""; }

        String p = StringUtils.defaultString(prefix);
        String s = StringUtils.defaultString(suffix);
        String d = StringUtils.defaultString(delim);

        StringBuilder sb = new StringBuilder();
        list.forEach(item -> sb.append(p).append(item).append(s).append(d));
        return StringUtils.removeEnd(sb.toString(), d);
    }

    @NotNull
    public static String toString(Set<?> set, String delim) { return CollectionUtil.toString(set, delim); }

    /** test to see if {@code matchList} contains any element that starts with {@code matchBy}. */
    public static boolean startWith(List<String> matchList, String matchBy) {
        if (matchList == null || matchList.isEmpty()) { return false; }
        if (StringUtils.isEmpty(matchBy)) { return matchList.contains(matchBy); }

        for (String item : matchList) { if (matchBy.startsWith(item)) { return true; } }
        return false;
    }

    /**
     * similar to {@link StringUtils#defaultIfEmpty(CharSequence, CharSequence)}, this method returns either
     * {@code text} or {@code defaultText} depending on whether {@code text} is empty/blank.
     */
    public static String defaultIfBlank(String text, String defaultText) {
        return StringUtils.isBlank(text) ? defaultText : text;
    }

    /**
     * similar to {@link StringUtils#defaultString(String)}, this method returns either {@code text} or
     * {@code null} depending on whether {@code text} is empty/blank.
     */
    public static String defaultIfBlank(String text) { return StringUtils.isBlank(text) ? null : text; }

    public static String initCap(String text) {
        return StringUtils.isNotBlank(text) ? text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase() : "";
    }

    /**
     * to avoid display of <BR/> in comment text area,replace <BR/> with \n before assigning the comments to text
     * area or other form input.
     */
    public static String prepForFormDisplay(String text) {
        if (StringUtils.isBlank(text)) { return StringUtils.defaultString(text); }

        return StringUtils.replaceEach(text, FORM_UNFRIENDLY_TEXT, NEWLINES);
    }

    /** transfer comment text so that newlines are rendered as HTML BR */
    public static String prepForInlineDisplay(String text) {
        if (StringUtils.isBlank(text)) { return StringUtils.defaultString(text); }
        return StringUtils.replaceEach(text, INLINE_UNFRIENDLY_TEXT, INLINE_BR);
    }

    @Nullable
    public static String substringBetweenFirstPair(String text, String open, String close, boolean includeSep) {
        if (StringUtils.isBlank(text)) { return null; }
        if (StringUtils.isEmpty(open)) { return null; }
        if (StringUtils.isEmpty(close)) { return null; }

        int indexFirstClose = text.indexOf(close);
        if (indexFirstClose == -1) { return null; }

        int indexClosestOpen = StringUtils.lastIndexOf(StringUtils.substring(text, 0, indexFirstClose), open);
        if (indexClosestOpen == -1) { return null; }

        String substring = StringUtils.substring(text, indexClosestOpen + 1, indexFirstClose);
        return includeSep ? open + substring + close : substring;
    }

    /**
     * find the string between the closest pair of <code >open</code> and <code >close</code>.  For example:
     * <pre>
     *     String a = "((mary had a ) ((little lamb))";
     *     String b = TextUtils.substringBetweenClosestPair(a, "(", ")");
     * </pre>
     * <code >b</code> would be "mary had a ";
     */
    @Nullable
    public static String substringBetweenFirstPair(String text, String open, String close) {
        return substringBetweenFirstPair(text, open, close, false);
    }

    /**
     * determine if {@code text} is between {@code start} and {@code end}
     */
    public static boolean isBetween(String text, String start, String end) {
        if (StringUtils.isEmpty(text)) { return false; }
        if (StringUtils.isEmpty(start)) { return false; }
        if (StringUtils.isEmpty(end)) { return false; }
        if (StringUtils.length(text) < (StringUtils.length(start) + StringUtils.length(end) + 1)) { return false; }
        return StringUtils.startsWith(text, start) && StringUtils.endsWith(text, end);
    }

    /**
     * determine if {@literal substring} is between {@literal start} string and {@literal end} string of the specified
     * {@literal text} string.
     */
    public static boolean isSubstringBetween(String text, String start, String end, String substring) {
        if (StringUtils.isEmpty(text)) { return false; }
        if (StringUtils.isEmpty(substring)) { return true; }
        if (!StringUtils.contains(text, substring)) { return false; }

        int posStart = StringUtils.isNotEmpty(start) ? text.indexOf(start) + start.length() : -1;
        int posEnd = StringUtils.isNotEmpty(end) ? text.indexOf(end, posStart == -1 ? 0 : posStart) : -1;

        if (posStart == -1) {
            return posEnd == -1 || text.indexOf(substring) + substring.length() <= posEnd;
        } else {
            int posMatch = text.indexOf(substring, posStart);
            return posEnd == -1 ? posMatch != -1 : posMatch != -1 && (posMatch + substring.length() <= posEnd);
        }
    }

    public static String wrapIfMissing(String text, String start, String end) {
        if (StringUtils.isEmpty(text)) { return start + end; }
        return StringUtils.prependIfMissing(StringUtils.appendIfMissing(text, end), start);
    }

    /**
     * substring between the first occurrence of {@code start} and the last occurrence of {@code end} from {@code text}.
     */
    public static String unwrap(String text, String start, String end) {
        if (StringUtils.isEmpty(text)) { return ""; }
        return StringUtils.substringBeforeLast(StringUtils.substringAfter(text, start), end);
    }

    /**
     * join the line(s) of text in {@code multiLineText} into a single line -- meaning without carriage
     * return or newline characters.  Optionally, this method can trim off the beginning and trailing spaces
     * in each line before joining them.
     */
    public static String toOneLine(String multiLineText, boolean trimBeforeJoin) {
        if (StringUtils.isEmpty(multiLineText)) { return multiLineText; }

        String[] lines = StringUtils.split(multiLineText, "\r\n");
        if (lines == null || lines.length < 2) { return trimBeforeJoin ? multiLineText.trim() : multiLineText; }

        StringBuilder sb = new StringBuilder();
        for (String line : lines) { sb.append(trimBeforeJoin ? line.trim() : line).append(" "); }

        return sb.delete(sb.length() - 1, sb.length()).toString();
    }

    public static String escapeHTML(String text, LineBreakConversion conversion) {
        text = TextUtils.replaceStrings(text, ESCAPE_HTML_MAP);
        return conversion.convert(text);
    }

    public static List<String> groups(String text, String groupStart, String groupEnd, boolean includeGroupChars) {
        List<String> groups = new ArrayList<>();

        if (StringUtils.isEmpty(text) || StringUtils.isEmpty(groupStart) || StringUtils.isEmpty(groupEnd)) {
            return groups;
        }

        if (StringUtils.isBlank(text)) {
            groups.add(text);
            return groups;
        }

        while (StringUtils.isNotEmpty(text)) {
            String group = substringBetweenFirstPair(text, groupStart, groupEnd);
            if (StringUtils.isEmpty(group)) {
                // we are done
                return groups;
            } else {
                if (includeGroupChars) { group = groupStart + group + groupEnd; }
                groups.add(group);
                text = StringUtils.remove(text, groupStart + group + groupEnd);
            }
        }

        return groups;
    }

    public static String[] toOneCharArray(String text) {
        if (StringUtils.isEmpty(text)) { return null; }

        String[] arr = new String[text.length()];
        for (int i = 0; i < arr.length; i++) { arr[i] = text.substring(i, i + 1); }
        return arr;
    }

    /**
     * convert {@literal array} into a {@literal String[]}. If {@literal array} is not an array (of any type), this
     * method will return empty {@literal String[]}.
     */
    @NotNull
    public static String[] toStringArray(Object array) {
        if (array == null || !array.getClass().isArray()) { return new String[0]; }

        int arrayLength = ArrayUtils.getLength(array);
        String[] strings = new String[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            strings[i] = Objects.toString(Array.get(array, i));
        }

        return strings;
    }

    /**
     * convert {@literal collection} into a {@literal List<String>}. If {@literal collection} is not a collection (of
     * any type), this method will return empty {@literal List<String>}.
     */
    @NotNull
    public static List<String> toStringList(Object collection) {
        if (!(collection instanceof Collection)) { return new ArrayList<>(); }

        List<String> strings = new ArrayList<>();
        ((Collection) collection).forEach(item -> strings.add(Objects.toString(item)));
        return strings;
    }

    /**
     * convert {@literal map} into a {@literal Map<String,String>}. If {@literal map} is not an array (of any type),
     * this method will return empty {@literal Map<String,String>}.
     */
    @NotNull
    public static Map<String, String> toStringMap(Object map) {
        if (!(map instanceof Map)) { return new HashMap<>(); }

        Map<String, String> strings = new LinkedHashMap<>();
        ((Map) map).forEach((key, value) -> strings.put(Objects.toString(key), Objects.toString(value)));
        return strings;
    }

    public static String removeFirst(String text, String remove) {
        if (StringUtils.isEmpty(text)) { return text; }
        if (StringUtils.isEmpty(remove)) { return text; }

        int start = StringUtils.indexOf(text, remove);
        if (start < 0) { return text; }

        return StringUtils.substring(text, 0, start) + StringUtils.substring(text, start + remove.length());
    }

    /**
     * remove all extraneous whitespaces, including space, tab, newline, carriage return so that {@code text} would
     * contain NO CONTIGUOUS whitespace.
     * <p>
     * Note that this method will convert all whitespaces (non-printable) to space (ASCII 20), and remove space
     * duplicates.
     */
    @NotNull
    public static String removeExcessWhitespaces(String text) {
        if (StringUtils.isEmpty(text)) { return ""; }
        if (StringUtils.isBlank(text)) { return " "; }

        text = RegExUtils.replaceAll(text, "\\s+", " ");
        text = RegExUtils.replaceAll(text, "  ", " ");

        return text;
    }

    @NotNull
    public static String removeEndRepeatedly(String text, String endWith) {
        if (StringUtils.isEmpty(text)) { return ""; }
        if (StringUtils.isEmpty(endWith)) { return ""; }

        while (StringUtils.endsWith(text, endWith)) { text = StringUtils.removeEnd(text, endWith); }
        return text;
    }

    public static String xpathFriendlyQuotes(String name) {
        if (name == null) { return "''"; }
        if (StringUtils.isBlank(name)) { return "'" + name + "'"; }
        if (!StringUtils.contains(name, "'")) { return "'" + name + "'"; }
        if (!StringUtils.contains(name, "\"")) { return "\"" + name + "\""; }

        String substitute = "concat(";

        while (StringUtils.isNotEmpty(name)) {
            int posStart = StringUtils.indexOfAny(name, '\'', '"');
            if (posStart == -1) {
                substitute += "'" + name + "',";
                break;
            }

            String quote = StringUtils.equals(StringUtils.substring(name, posStart, posStart + 1), "'") ?
                           "\"" : "'";
            substitute += quote + StringUtils.substring(name, 0, posStart + 1) + quote + ",";
            name = StringUtils.substring(name, posStart + 1);
        }

        return StringUtils.removeEnd(substitute, ",") + ")";
    }

    public static String xpathNormalize(String text) {
        if (StringUtils.isBlank(text)) { return ""; }

        text = RegExUtils.replaceAll(StringUtils.trim(text), "\\p{Space}", " ");
        while (StringUtils.contains(text, "  ")) { text = StringUtils.replace(text, "  ", " "); }

        return text;
    }

    public static int countMatches(List<String> list, String exact) {
        if (CollectionUtils.isEmpty(list)) { return StringUtils.isEmpty(exact) ? 1 : 0; }
        return (int) list.stream().filter((String item) -> StringUtils.equals(item, exact)).count();
    }

    /**
     * create ASCII table (aka ascii art) based on a {@link List} of objects, which can varied in types.  The extraction
     * of cell-level value would be differed to {@link ExtractCellByPosition} implementation
     */
    @NotNull
    public static <T> String createAsciiTable(List<String> headers,
                                              List<T> records,
                                              ExtractCellByPosition<T> extractor) {
        // figure out the right width to apply
        Map<Integer, Integer> widths = new HashMap<>();
        if (CollectionUtils.isNotEmpty(headers)) {
            for (int i = 0; i < headers.size(); i++) { widths.put(i, StringUtils.length(headers.get(i)) + 1); }
        }

        if (CollectionUtils.isNotEmpty(records)) {
            records.forEach(
                row -> widths.forEach(
                    (index, currentWidth) -> {
                        int width =
                            Math.max(currentWidth, StringUtils.length(extractor.getCell(row, index)) + 1);
                        widths.put(index, width);
                    }));
        }

        int[] totalWidth = new int[]{1};
        widths.forEach((index, width) -> totalWidth[0] += width + 1);

        String linSep = lineSeparator();
        String lineAcross = StringUtils.repeat("-", totalWidth[0]) + linSep;

        // ready to draw
        StringBuilder tableContent = new StringBuilder();

        if (CollectionUtils.isNotEmpty(headers)) {
            tableContent.append(lineAcross);
            tableContent.append("|");
            for (int i = 0; i < headers.size(); i++) {
                tableContent.append(StringUtils.rightPad(headers.get(i), widths.get(i))).append("|");
            }
            tableContent.append(linSep).append(lineAcross);
        }

        if (CollectionUtils.isNotEmpty(records)) {
            records.forEach(row -> {
                tableContent.append("|");
                widths.forEach(
                    (position, width) -> {
                        String cell = StringUtils.rightPad(extractor.getCell(row, position), widths.get(position));
                        tableContent.append(cell).append("|");
                    });
                tableContent.append(linSep).append(lineAcross);
            });
        }

        return tableContent.toString();
    }

    /**
     * create HTML table with optional {@code tableStyleClass}.
     * <p>
     * This implementation requires the same size of {@code headers} and {@code records}.  {@code headers} is thus
     * expected to <b>NOT TO BE EMPTY</b>, or no table HTML would be generated.
     */
    @NotNull
    public static <T> String createHtmlTable(List<String> headers,
                                             List<T> records,
                                             ExtractCellByPosition<T> extractor,
                                             String tableStyleClass) {
        int bufferSize = Math.max(20 * CollectionUtils.size(headers) * CollectionUtils.size(records), 100);
        StringBuilder buffer = new StringBuilder(bufferSize);

        String lineSep = lineSeparator();

        if (StringUtils.isNotBlank(tableStyleClass)) {
            buffer.append("<table class=\"").append(tableStyleClass).append("\">");
        } else {
            buffer.append("<table>");
        }
        buffer.append(lineSep);

        // header
        int columnCount = CollectionUtils.size(headers);
        if (CollectionUtils.isNotEmpty(headers)) {
            buffer.append("<thead><tr>");
            headers.forEach(header -> buffer.append("<th>").append(header).append("</th>"));
            buffer.append("</tr></thead>").append(lineSep);
        }

        // body
        if (CollectionUtils.isNotEmpty(records)) {
            buffer.append("<tbody>").append(lineSep);
            records.forEach(row -> {
                buffer.append("<tr>");
                for (int i = 0; i < columnCount; i++) {
                    buffer.append("<td>").append(extractor.getCell(row, i)).append("</td>");
                }
                buffer.append("</tr>").append(lineSep);
            });
            buffer.append("</tbody>").append(lineSep);
        }

        buffer.append("</table>").append(lineSep);

        return buffer.toString();
    }

    @NotNull
    public static String createHtmlTable(List<String> headers,
                                         List<List<String>> data,
                                         String tableStyleClass) {
        return createHtmlTable(headers, data, List::get, tableStyleClass);
    }

    @NotNull
    public static String createCsv(List<String> headers,
                                   List<List<String>> data,
                                   String recordSep,
                                   String fieldSep,
                                   String wrapChar) {
        int bufferSize = Math.max(20 * CollectionUtils.size(headers) * CollectionUtils.size(data), 100);
        StringBuilder buffer = new StringBuilder(bufferSize);

        // header
        if (CollectionUtils.isNotEmpty(headers)) {
            buffer.append(TextUtils.toString(headers, fieldSep)).append(recordSep);
        }

        // content
        if (CollectionUtils.isNotEmpty(data)) {
            data.forEach(row -> buffer.append(TextUtils.toString(row, fieldSep, wrapChar, wrapChar)).append(recordSep));
        }

        return buffer.toString();
    }

    /**
     * load the properties stored in {@code propFile} into a {@link Map} object.  This method is different than
     * the standard Java {@link Properties#load(InputStream)} in that it will make special concession towards property
     * names with space characters.
     */
    public static Map<String, String> loadProperties(String propFile) { return loadProperties(propFile, false); }

    public static Map<String, String> loadProperties(String propFile, boolean trimKey) {
        return loadProperties(propFile, trimKey, NotAllowed);
    }

    public static Map<String, String> loadProperties(String propFile,
                                                     boolean trimKey,
                                                     DuplicateKeyStrategy duplicateKeyStrategy) {
        // load only properties without sections
        Map<String, String> properties = new LinkedHashMap<>();
        Map<String, Map<String, String>> properties1 = loadProperties(propFile, trimKey, false, duplicateKeyStrategy);
        if (properties1 != null) { properties1.values().forEach(properties::putAll); }
        return properties;
    }

    public static Map<String, Map<String, String>> loadProperties(String propFile,
                                                                  boolean trimKey,
                                                                  boolean readCommentAsKeyValue) {
        return loadProperties(propFile, trimKey, readCommentAsKeyValue, NotAllowed);
    }

    public static Map<String, Map<String, String>> loadProperties(String propFile,
                                                                  boolean trimKey,
                                                                  boolean readCommentAsKeyValue,
                                                                  DuplicateKeyStrategy duplicateKeyStrategy) {
        // load properties as section to key-value pair
        if (!FileUtil.isFileReadable(propFile, 5)) { return null; }

        File prop = new File(propFile);
        String propPath = prop.getAbsolutePath();
        String section = "";
        Map<String, Map<String, String>> properties = new LinkedHashMap<>();
        try {
            List<String> lines = FileUtils.readLines(prop, DEF_FILE_ENCODING);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (StringUtils.isBlank(line)) { continue; }

                boolean isComment = StringUtils.startsWithAny(line.trim(), "#", "!");
                if (isComment) {
                    if (StringUtils.startsWithAny(line.trim(), PROPERTIES_SECTION_START)) {
                        section = StringUtils.substringAfter(line, PROPERTIES_SECTION_START);
                        continue;
                    } else if (!readCommentAsKeyValue) { continue; }
                }

                if (StringUtils.endsWith(line, "\\")) {
                    line = StringUtils.removeEnd(line, "\\");

                    // handle continuation
                    for (int j = i + 1; j < lines.size(); j++) {
                        String continuation = StringUtils.stripStart(lines.get(j), " \t");
                        if (isComment) {
                            if (!StringUtils.startsWithAny(continuation, "#", ":")) { break; }
                            continuation = StringUtils.substring(continuation, 1);
                        }

                        if (StringUtils.endsWith(continuation, "\\")) {
                            line += StringUtils.removeEnd(continuation, "\\");
                        } else {
                            line += continuation;
                            i = j;
                            break;
                        }
                    }
                }

                String key = line;
                String value = "";

                List<String> collectGroups = RegexUtils.collectGroups(line, PROPERTIES_REGEX, false, true);
                if (CollectionUtils.isNotEmpty(collectGroups)) {
                    key = StringUtils.replace(StringUtils.replace(collectGroups.get(0), "\\=", "="), "\\:", ":");
                    value = collectGroups.get(2);
                } else if (readCommentAsKeyValue) { continue; }

                if (trimKey) { key = StringUtils.trim(key); }

                if (properties.containsKey(section)) {
                    Map<String, String> maps = properties.get(section);
                    if (maps.containsKey(key)) {
                        switch (duplicateKeyStrategy) {
                            case NotAllowed -> throw new InvalidInputRuntimeException(
                                RB.Abort.text("dupProjectProperties", propPath, key));
                            case FavorFirst -> ConsoleUtils.log(
                                RB.Commons.text("dupProjectProperties.first", propPath, key));
                            case FavorLast -> {
                                ConsoleUtils.log(RB.Commons.text("dupProjectProperties.last", propPath, key));
                                maps.put(key, value);
                            }
                        }
                    } else {
                        maps.put(key, value);
                    }
                } else {
                    Map<String, String> keyValueMap = new LinkedHashMap<>();
                    keyValueMap.put(key, value);
                    properties.put(section, keyValueMap);
                }
            }

        } catch (IOException e) {
            ConsoleUtils.error("Unable to read project properties " + propFile + ": " + e.getMessage());
            return null;
        }

        return properties;
    }

    /**
     * sanitize phone number so that:<ol>
     * <li>It starts with '+' symbol, follow by country code (i.e. '1' for US)</li>
     * <li>All letters are converted to number, according to traditional phone number dial pad</li>
     * </ol>
     * <p>
     * In addition, it checks that {@literal phoneNumber} must be 10 characters or more.
     */
    @NotNull
    public static String sanitizePhoneNumber(String phoneNumber) {
        if (StringUtils.isBlank(phoneNumber)) { throw new IllegalArgumentException("phone number cannot be empty"); }
        if (StringUtils.length(phoneNumber) < 10) {
            throw new IllegalArgumentException("invalid phone number: " + phoneNumber);
        }

        StringBuilder phone = new StringBuilder();

        char[] phoneChars = phoneNumber.toCharArray();
        for (int i = 0; i < phoneChars.length; i++) {
            char ch = phoneChars[i];

            if (i == 0 && ch == '+') { phone.append(ch); }

            if (Character.isDigit(ch)) {
                phone.append(ch);
                continue;
            }

            if (Character.isLetter(ch)) {
                ch = Character.toUpperCase(ch);
                switch (ch) {
                    case 'A', 'B', 'C' -> phone.append('2');
                    case 'D', 'E', 'F' -> phone.append('3');
                    case 'G', 'H', 'I' -> phone.append('4');
                    case 'J', 'K', 'L' -> phone.append('5');
                    case 'M', 'N', 'O' -> phone.append('6');
                    case 'P', 'Q', 'R', 'S' -> phone.append('7');
                    case 'T', 'U', 'V' -> phone.append('8');
                    case 'W', 'X', 'Y', 'Z' -> phone.append('9');
                    default -> { }
                }
                continue;
            }

            if (ch == '*' || ch == '#') { phone.append(ch); }
        }

        phoneNumber = phone.toString();
        if (!StringUtils.startsWith(phoneNumber, "+")) { phoneNumber = "+" + phoneNumber; }
        if (StringUtils.length(phoneNumber) < 12) { phoneNumber = "+1" + StringUtils.substring(phoneNumber, 1); }

        return phoneNumber;
    }

    public static String removeOnly(String text, String removeTheseOnly) {
        if (StringUtils.isEmpty(removeTheseOnly)) { return text; }
        if (StringUtils.isEmpty(text)) { return text; }

        char[] removeChars = removeTheseOnly.toCharArray();
        Character[] remove = new Character[removeChars.length];
        Arrays.setAll(remove, i -> removeChars[i]);
        List<Character> distinctRemove = Arrays.stream(remove).distinct().collect(Collectors.toList());

        StringBuilder buffer = new StringBuilder();
        char[] chars = text.toCharArray();
        for (char c : chars) { if (!distinctRemove.contains(c)) { buffer.append(c); } }

        return buffer.toString();
    }

    public static String keepOnly(String text, String keepTheseOnly) {
        if (StringUtils.isEmpty(keepTheseOnly)) { return text; }
        if (StringUtils.isEmpty(text)) { return text; }

        char[] wantedChars = keepTheseOnly.toCharArray();
        Character[] wanted = new Character[wantedChars.length];
        Arrays.setAll(wanted, i -> wantedChars[i]);
        List<Character> distinctWanted = Arrays.stream(wanted).distinct().collect(Collectors.toList());

        StringBuilder buffer = new StringBuilder();
        char[] chars = text.toCharArray();
        for (char c : chars) { if (distinctWanted.contains(c)) { buffer.append(c); } }

        return buffer.toString();
    }

    @NotNull
    public static String toCsvContent(List<List<String>> values, String delim, String recordDelim) {
        StringBuilder csvBuffer = new StringBuilder();
        values.forEach(row -> {
            StringBuilder rowBuffer = new StringBuilder();
            row.forEach(cell -> rowBuffer
                                    .append(cell.contains(delim) ? StringUtils.wrapIfMissing(cell, "\"") : cell)
                                    .append(delim));
            csvBuffer.append(StringUtils.removeEnd(rowBuffer.toString(), delim)).append(recordDelim);
        });
        return csvBuffer.toString();
    }

    @NotNull
    public static String csvSafe(String text, String delim, boolean oneLine) {
        if (StringUtils.isBlank(text)) { return text; }

        AtomicReference<String> safe = new AtomicReference<>(text);
        if (oneLine) {
            CSV_SAFE_REPLACEMENT.forEach((find, replace) -> safe.set(StringUtils
                                                                         .replace(safe.get(), find, replace)));
            safe.set(StringUtils.replace(safe.get(), "\r", ""));
            safe.set(StringUtils.replace(safe.get(), "\n", " "));
            safe.set(StringUtils.replace(safe.get(), "\t", " "));
        }

        String safeText = safe.get();
        if (!TextUtils.isBetween(safeText, "\"", "\"") && StringUtils.contains(safeText, delim)) {
            safeText = "\"" + safeText + "\"";
        }

        return safeText;
    }

    public static String base64encode(String plain) {
        return StringUtils.isEmpty(plain) ? plain : Base64.getEncoder().encodeToString(plain.getBytes());
    }

    public static String base64decode(String encoded) {
        return StringUtils.isEmpty(encoded) ? encoded : new String(Base64.getDecoder().decode(encoded.getBytes()));
    }

    public static byte[] base64decodeAsBytes(String encoded) {
        return StringUtils.isEmpty(encoded) ? new byte[0] : Base64.getDecoder().decode(encoded.getBytes());
    }

    public static String demarcate(String text, int markPosition, String delim) {
        if (StringUtils.isEmpty(text)) { return text; }
        if (StringUtils.isEmpty(delim)) { return text; }
        if (markPosition < 1 || markPosition > text.length()) { return text; }

        int delimLength = delim.length();
        int seekPos = markPosition;

        while (seekPos < text.length()) {
            text = text.substring(0, seekPos) + delim + text.substring(seekPos);
            seekPos += markPosition + delimLength;
        }

        return text;
    }

    public static String cleanNumber(String text, CleanNumberStrategy howToClean) {
        text = StringUtils.trim(text);
        if (StringUtils.isBlank(text)) { return "0"; }

        // in case start with - or +
        boolean isNegative = StringUtils.startsWith(text, "-");
        if (isNegative) { text = StringUtils.removeStart(text, "-"); }

        // remove characters not suitable to represent number
        if (howToClean != null) {
            if (howToClean == CSV) { text = StringUtils.unwrap(StringUtils.unwrap(text, "'"), "\""); }
            if (howToClean.removes != null) { text = removeOnly(text, howToClean.removes); }
            if (howToClean.keeps != null) { text = keepOnly(text, howToClean.keeps); }
        }

        // remove leading zero's
        if (howToClean != OCTAL) { text = RegExUtils.removeFirst(text, "^0{1,}"); }

        if (StringUtils.isBlank(text)) { return "0"; }

        // transform .001 to 0.001
        if (StringUtils.startsWith(text, ".")) { text = "0" + text; }

        // put negative sign back
        if (isNegative) { text = "-" + text; }

        // we still good?
        if (!NumberUtils.isCreatable(text)) {
            throw new IllegalArgumentException("'" +
                                               text +
                                               " is not a valid number");
        }

        // we good
        return text;
    }

    public static String decorateTextRange(String text,
                                           String startsFrom,
                                           String endsWith,
                                           String decorateStart,
                                           String decorateEnd) {
        if (StringUtils.isEmpty(text) ||
            StringUtils.isEmpty(startsFrom) ||
            StringUtils.isEmpty(endsWith) ||
            StringUtils.isEmpty(decorateStart) ||
            StringUtils.isEmpty(decorateEnd)) { return text; }

        int startPos = text.indexOf(startsFrom);
        while (startPos != -1) {
            int endPos = text.indexOf(endsWith, startPos + startsFrom.length());
            if (endPos == -1) { break; }

            text = text.substring(0, startPos + startsFrom.length()) +
                   decorateStart +
                   text.substring(startPos + startsFrom.length(), endPos) +
                   decorateEnd +
                   text.substring(endPos);
            startPos = text.indexOf(startsFrom, endPos + decorateEnd.length() + decorateEnd.length());
        }

        return text;
    }

    @NotNull
    public static String prettyToString(String... keyValues) { return prettyToStringWithDelim(NL, keyValues); }

    @NotNull
    public static String prettyToStringWithDelim(String delimiter, String... keyValues) {
        if (ArrayUtils.isEmpty(keyValues)) { return ""; }

        return NL +
               Arrays.stream(keyValues).map(pair -> {
                   String key = StringUtils.substringBefore(pair, "=");
                   String value = StringUtils.substringAfter(pair, "=");
                   return StringUtils.isBlank(key) ?
                          "" : StringUtils.rightPad(key, TO_STRING_KEY_LENGTH) + " = " + value + delimiter;
               }).collect(Collectors.joining(""));
    }

    @Nullable
    public static String escapeLiterals(String text) {
        if (StringUtils.isBlank(text)) { return text; }
        text = StringUtils.replace(text, "\\t", "\t");
        text = StringUtils.replace(text, "\\b", "\b");
        text = StringUtils.replace(text, "\\n", "\n");
        text = StringUtils.replace(text, "\\r", "\r");
        text = StringUtils.replace(text, "\\f", "\f");
        text = StringUtils.replace(text, "\\'", "'");
        text = StringUtils.replace(text, "\\\"", "\"");
        text = StringUtils.replace(text, "\\\\", "\\");
        return text;
    }

    public static boolean isPolyMatcher(String text) {
        return MATCHES.stream().anyMatch(match -> StringUtils.startsWith(text, match));
    }

    /** check if one of the items of {@code actual} would poly-match against the specified {@code expected}. */
    public static boolean polyMatch(List<String> actual, String expected) {
        return actual.stream().anyMatch(item -> polyMatch(item, expected, false));
    }

    /** check if one of the items of {@code actual} would poly-match against the specified {@code expected}. */
    public static boolean polyMatchAll(List<String> actual, String expected) {
        return actual.stream().allMatch(item -> polyMatch(item, expected, false));
    }

    public static boolean polyMatch(String actual, String expected) { return polyMatch(actual, expected, false); }

    public static boolean polyMatch(String actual, String expected, boolean trim) {
        if (trim) {
            actual = StringUtils.trim(actual);
            expected = StringUtils.trim(expected);
        }

        // short circuit
        if (StringUtils.isEmpty(actual) && StringUtils.isEmpty(expected)) { return true; }

        if (StringUtils.startsWith(expected, REGEX)) {
            return RegexUtils.match(actual, StringUtils.substringAfter(expected, REGEX));
        }

        if (StringUtils.startsWith(expected, CONTAIN)) {
            return StringUtils.contains(actual, StringUtils.substringAfter(expected, CONTAIN));
        }

        if (StringUtils.startsWith(expected, CONTAIN_ANY_CASE)) {
            return StringUtils.containsIgnoreCase(actual, StringUtils.substringAfter(expected, CONTAIN_ANY_CASE));
        }

        if (StringUtils.startsWith(expected, START)) {
            return StringUtils.startsWith(actual, StringUtils.substringAfter(expected, START));
        }

        if (StringUtils.startsWith(expected, START_ANY_CASE)) {
            return StringUtils.startsWithIgnoreCase(actual, StringUtils.substringAfter(expected, START_ANY_CASE));
        }

        if (StringUtils.startsWith(expected, END)) {
            return StringUtils.endsWith(actual, StringUtils.substringAfter(expected, END));
        }

        if (StringUtils.startsWith(expected, END_ANY_CASE)) {
            return StringUtils.endsWithIgnoreCase(actual, StringUtils.substringAfter(expected, END_ANY_CASE));
        }

        if (StringUtils.startsWith(expected, EMPTY)) {
            if (BooleanUtils.toBoolean(StringUtils.trim(StringUtils.substringAfter(expected, EMPTY)))) {
                return StringUtils.isEmpty(actual);
            } else {
                return StringUtils.isNotEmpty(actual);
            }
        }

        if (StringUtils.startsWith(expected, BLANK)) {
            if (BooleanUtils.toBoolean(StringUtils.trim(StringUtils.substringAfter(expected, BLANK)))) {
                return StringUtils.isBlank(actual);
            } else {
                return StringUtils.isNotBlank(actual);
            }
        }

        if (StringUtils.startsWith(expected, LENGTH)) {
            String lengthCheck =
                StringUtils.removeStart(StringUtils.trim(StringUtils.substringAfter(expected, LENGTH)), "+");
            // if all we got is just the number, then assume equality comparison
            List<String> groups = NumberUtils.isDigits(lengthCheck) ?
                                  Arrays.asList("=", lengthCheck) :
                                  RegexUtils.collectGroups(lengthCheck, REGEX_NUMERIC_COMPARE);
            if (CollectionUtils.size(groups) != 2) {
                throw new IllegalArgumentException("Invalid PolyMatcher syntax: " + expected);
            }
            return polyMatcherNumeric(groups.get(0),
                                      StringUtils.length(actual),
                                      NumberUtils.toInt(groups.get(1)),
                                      expected);
        }

        if (StringUtils.startsWith(expected, NUMERIC)) {
            String numberCheck =
                StringUtils.removeStart(StringUtils.trim(StringUtils.substringAfter(expected, NUMERIC)), "+");
            // if all we got is a just the number, then assume equality comparison
            List<String> groups = NumberUtils.isParsable(numberCheck) ?
                                  Arrays.asList("=", numberCheck) :
                                  RegexUtils.collectGroups(numberCheck, REGEX_NUMERIC_COMPARE);
            if (CollectionUtils.size(groups) != 2) {
                throw new IllegalArgumentException("Invalid PolyMatcher syntax: " + expected);
            }
            return polyMatcherNumeric(groups.get(0),
                                      new BigDecimal(StringUtils.trim(actual)).doubleValue(),
                                      new BigDecimal(StringUtils.trim(groups.get(1))).doubleValue(),
                                      expected);
        }

        if (StringUtils.startsWith(expected, EXACT)) {
            return StringUtils.equals(actual, StringUtils.substringAfter(expected, EXACT));
        }

        // finally, exact match
        return StringUtils.equals(actual, expected);
    }

    private static boolean polyMatcherNumeric(String comparator, double actual, double expected, String polyMatcher) {
        return switch (comparator) {
            case ">" -> actual > expected;
            case ">=" -> actual >= expected;
            case "<" -> actual < expected;
            case "<=" -> actual <= expected;
            case "=" -> actual == expected;
            case "!=" -> actual != expected;
            default -> throw new IllegalArgumentException("Unknown comparator in PolyMatcher: " + polyMatcher);
        };
    }

    private static Map<String, String> initDefaultEscapeHtmlMapping() {
        Map<String, String> searchReplace = new HashMap<>();
        searchReplace.put("<", "&lt;");
        searchReplace.put(">", "&gt;");
        searchReplace.put("‚", "&#130");
        searchReplace.put("[", "&#91;");
        searchReplace.put("]", "&#93;");
        searchReplace.put("\"", "&#34;");
        searchReplace.put(",", "&#44;");
        searchReplace.put(":", "&#58;");
        searchReplace.put("{", "&#123;");
        searchReplace.put("}", "&#125;");

        return searchReplace;
    }
}