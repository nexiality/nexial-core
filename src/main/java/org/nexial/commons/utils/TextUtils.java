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

package org.nexial.commons.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.utils.ConsoleUtils;

import static java.awt.event.KeyEvent.*;
import static java.lang.Character.UnicodeBlock.SPECIALS;
import static java.lang.System.lineSeparator;
import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;

/**
 * @author Mike Liu
 */
public final class TextUtils {
    // FORM_UNFRIENDLY_TEXT and NEWLINES must match in size
    public static final String[] FORM_UNFRIENDLY_TEXT =
        new String[]{"&lt;BR/&gt;", "&lt;br/&gt;", "<BR/>", "<BR>", "<br>", "<br/>", "\r"};
    public static final String[] NEWLINES = new String[]{"\n", "\n", "\n", "\n", "\n", "\n", "\n"};
    // INLINE_UNFRIENDLY_TEXT and INLINE_BR must match in size
    public static final String[] INLINE_UNFRIENDLY_TEXT =
        new String[]{"\n", "\r\n", "\n\r", "&lt;BR/&gt;", "&lt;br/&gt;"};
    public static final String[] INLINE_BR = new String[]{"<br/>", "<br/>", "<br/>", "<br/>", "<br/>"};

    private static final Map<String, String> ESCAPE_HTML_MAP = initDefaultEscapeHtmlMapping();

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

    public static List<String> replaceItems(List<String> list, String find, String replaceWith) {
        if (CollectionUtils.isEmpty(list)) { return list; }
        if (StringUtils.isEmpty(find)) { return list; }

        List<String> newList = new ArrayList<>(list.size());
        list.forEach(item -> newList.add(StringUtils.replace(item, find, replaceWith)));
        return newList;
    }

    public static String insert(String text, int position, String extra) {
        if (StringUtils.isBlank(text)) { return text; }
        if (StringUtils.isEmpty(extra)) { return text; }
        if (position < 0) { return text; }
        if (position > StringUtils.length(text)) { return text; }

        return StringUtils.substring(text, 0, position) + extra + StringUtils.substring(text, position);
    }

    public static Map<String, String> toMap(String delim, String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();

        if (ArrayUtils.isEmpty(pairs)) { return map; }
        if (StringUtils.isEmpty(delim)) { return map; }

        for (String pair : pairs) {
            map.put(StringUtils.substringBefore(pair, delim), StringUtils.substringAfter(pair, delim));
        }

        return map;
    }

    public static Map<String, String> toMap(String text, String pairDelim, String nameValueDelim) {
        Map<String, String> map = new LinkedHashMap<>();

        if (StringUtils.isEmpty(text)) { return map; }
        if (StringUtils.isEmpty(pairDelim)) { return map; }
        if (StringUtils.isEmpty(nameValueDelim)) { return map; }

        String[] pairs = StringUtils.splitByWholeSeparator(text, pairDelim);
        for (String pair : pairs) {
            String key = StringUtils.substringBefore(pair, nameValueDelim);
            String value = StringUtils.substringAfter(pair, nameValueDelim);
            map.put(key, value);
        }

        return map;
    }

    public static String[][] to2dArray(String text, String rowSeparator, String delim) {
        if (StringUtils.isEmpty(text)) { return new String[0][0]; }

        // defautls
        if (StringUtils.isEmpty(rowSeparator)) { rowSeparator = "\n"; }
        if (StringUtils.isEmpty(delim)) { delim = ","; }

        String[] rows = StringUtils.splitByWholeSeparator(text, rowSeparator);
        if (ArrayUtils.getLength(rows) < 1) { return new String[0][0]; }

        String[][] twoD = new String[rows.length][];
        for (int i = 0; i < rows.length; i++) {
            twoD[i] = StringUtils.split(rows[i], delim);
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

        String[] rows = StringUtils.splitByWholeSeparator(text, rowSep);
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
     * tranform string to {@link List} (of String).  Use {@code delim} to determine the delimiter and {@code trim}
     * to determine if the delimited list should be trimmed returned.
     */
    public static <T> List<T> toList(String text, String delim, ListItemConverter<T> itemConverter) {
        if (StringUtils.isEmpty(text)) { return null; }
        if (StringUtils.isEmpty(delim)) { return null; }

        String[] items;
        if (StringUtils.isBlank(delim)) {
            items = text.split("(" + delim + ")+");
        } else {
            items = StringUtils.splitByWholeSeparator(text, delim);
        }

        if (items == null) { return null; }

        List<T> list = new ArrayList<>();
        Arrays.stream(items).forEach(s -> list.add(itemConverter.convert(s)));
        return list;
    }

    /**
     * tranform string to {@link List} (of String).  Use {@code delim} to determine the delimiter and {@code trim}
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
                    StringUtils.split(text, delim) : StringUtils.splitByWholeSeparator(text, delim);
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
     * tranform string to {@link List} (of String) without discounting consecutive delimiters.
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
        sb = sb.deleteCharAt(sb.length() - pairDelim.length());
        return sb.toString();
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
        return StringUtils.isNotBlank(text) ?
               text.substring(0, 1).toUpperCase() + text.substring(1, text.length()).toLowerCase() : "";
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

    /**
     * find the string between the closest pair of <code >open</code> and <code >close</code>.  For example:
     * <pre>
     *     String a = "((mary had a ) ((little lamb))";
     *     String b = TextUtils.substringBetweenClosestPair(a, "(", ")");
     * </pre>
     * <code >b</code> would be "mary had a ";
     */
    public static String substringBetweenFirstPair(String text, String open, String close) {
        if (StringUtils.isBlank(text)) { return null; }
        if (StringUtils.isEmpty(open)) { return null; }
        if (StringUtils.isEmpty(close)) { return null; }

        int indexFirstClose = text.indexOf(close);
        if (indexFirstClose == -1) { return null; }

        int indexClosestOpen = StringUtils.lastIndexOf(StringUtils.substring(text, 0, indexFirstClose), open);
        if (indexClosestOpen == -1) { return null; }

        return StringUtils.substring(text, indexClosestOpen + 1, indexFirstClose);
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
     *
     * Note that this method will convert all whitespaces (non-printable) to space (ASCII 20), and remove space dups.
     */
    @NotNull
    public static String removeExcessWhitespaces(String text) {
        if (StringUtils.isEmpty(text)) { return ""; }
        if (StringUtils.isBlank(text)) { return " "; }

        text = StringUtils.replaceAll(text, "\\s+", " ");
        text = StringUtils.replaceAll(text, "  ", " ");

        return text;
    }

    public static String xpathFriendlyQuotes(String name) {
        if (name == null) { return "''"; }
        if (StringUtils.isBlank(name)) { return "'" + name + "'"; }
        if (!StringUtils.contains(name, "'")) { return "'" + name + "'"; }
        if (!StringUtils.contains(name, "\"")) { return "\"" + name + "\""; }

        String substitue = "concat(";

        while (StringUtils.isNotEmpty(name)) {
            int posStart = StringUtils.indexOfAny(name, '\'', '"');
            if (posStart == -1) {
                substitue += "'" + name + "',";
                break;
            }

            String quote = StringUtils.equals(StringUtils.substring(name, posStart, posStart + 1), "'") ?
                           "\"" : "'";
            substitue += quote + StringUtils.substring(name, 0, posStart + 1) + quote + ",";
            name = StringUtils.substring(name, posStart + 1);
        }

        return StringUtils.removeEnd(substitue, ",") + ")";
    }

    public static String xpathNormalize(String text) {
        if (StringUtils.isBlank(text)) { return ""; }

        text = StringUtils.trim(text);
        text = StringUtils.replaceAll(text, "\\p{Space}", " ");
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
                        int width = Math.max(currentWidth, StringUtils.length(extractor.getCell(row, index)) + 1);
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
     *
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
    @NotNull
    public static Map<String, String> loadProperties(String propFile) {
        if (!FileUtil.isFileReadable(propFile, 5)) { return null; }

        Map<String, String> properties = new LinkedHashMap<>();
        // String projectPropsContent;
        try {

            List<String> lines = FileUtils.readLines(new File(propFile), DEF_FILE_ENCODING);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (StringUtils.isBlank(line)) { continue; }
                if (StringUtils.startsWith(line.trim(), "#") || StringUtils.startsWith(line.trim(), "!")) { continue; }

                if (StringUtils.endsWith(line, "\\")) {
                    line = StringUtils.removeEnd(line, "\\");

                    // handle continuation
                    for (int j = i + 1; j < lines.size(); j++) {
                        String continuation = StringUtils.stripStart(lines.get(j), " \t");
                        if (StringUtils.endsWith(continuation, "\\")) {
                            line += StringUtils.removeEnd(continuation, "\\");
                        } else {
                            line += continuation;
                            i = j;
                            break;
                        }
                    }
                }

                int posEqual = StringUtils.indexOf(line, "=");
                int posColon = StringUtils.indexOf(line, ":");
                if (posEqual == -1) {
                    if (posColon == -1) {
                        // not name/value pair
                        properties.put(line, "");
                    } else {
                        properties.put(StringUtils.substringBefore(line, ":"), StringUtils.substringAfter(line, ":"));
                    }
                } else {
                    if (posColon == -1) {
                        properties.put(StringUtils.substringBefore(line, "="), StringUtils.substringAfter(line, "="));
                    } else {
                        String delim = posEqual < posColon ? "=" : ":";
                        properties.put(StringUtils.substringBefore(line, delim),
                                       StringUtils.substringAfter(line, delim));
                    }
                }
            }

            // projectPropsContent = FileUtils.readFileToString(new File(propFile), DEF_FILE_ENCODING);
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
     *
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
                    case 'A':
                    case 'B':
                    case 'C': {
                        phone.append('2');
                        break;
                    }
                    case 'D':
                    case 'E':
                    case 'F': {
                        phone.append('3');
                        break;
                    }
                    case 'G':
                    case 'H':
                    case 'I': {
                        phone.append('4');
                        break;
                    }
                    case 'J':
                    case 'K':
                    case 'L': {
                        phone.append('5');
                        break;
                    }
                    case 'M':
                    case 'N':
                    case 'O': {
                        phone.append('6');
                        break;
                    }
                    case 'P':
                    case 'Q':
                    case 'R':
                    case 'S': {
                        phone.append('7');
                        break;
                    }
                    case 'T':
                    case 'U':
                    case 'V': {
                        phone.append('8');
                        break;
                    }
                    case 'W':
                    case 'X':
                    case 'Y':
                    case 'Z': {
                        phone.append('9');
                        break;
                    }
                    default: {
                        break;
                    }
                }
                continue;
            }

            if (ch == '*' || ch == '#') {
                phone.append(ch);
            }
        }

        phoneNumber = phone.toString();
        if (!StringUtils.startsWith(phoneNumber, "+")) { phoneNumber = "+" + phoneNumber; }
        if (StringUtils.length(phoneNumber) < 12) { phoneNumber = "+1" + StringUtils.substring(phoneNumber, 1); }

        return phoneNumber;
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
            row.forEach(cell -> rowBuffer.append(cell).append(delim));
            csvBuffer.append(StringUtils.removeEnd(rowBuffer.toString(), delim)).append(recordDelim);
        });
        return csvBuffer.toString();
    }

    public static String base64encoding(String plain) {
        return StringUtils.isEmpty(plain) ? plain : Base64.getEncoder().encodeToString(plain.getBytes());
    }

    public static String base64decoding(String encoded) {
        return StringUtils.isEmpty(encoded) ? encoded : new String(Base64.getDecoder().decode(encoded.getBytes()));
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