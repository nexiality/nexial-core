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

package org.nexial.core.variable;

import java.util.Arrays;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;

import static org.nexial.core.NexialConst.Data.TEXT_DELIM;
import static org.nexial.core.SystemVariables.getDefault;

public class Array {

    public String item(String input, String index) {
        return StringUtils.isEmpty(input) ? "" : item(toArray(input), index);
    }

    public String length(String input) {
        return StringUtils.isEmpty(input) ? "0" : toArray(input).length + "";
    }

    public String reverse(String input) {
        if (StringUtils.isEmpty(input)) { return input; }

        String[] arr = toArray(input);
        ArrayUtils.reverse(arr);
        return toString(Arrays.stream(arr));
    }

    public String subarray(String input, String start, String end) {
        return StringUtils.isEmpty(input) ? "" : toString(subarray(toArray(input), start, end));
    }

    public String distinct(String input) {
        return StringUtils.isEmpty(input) ? "" : toString(Arrays.stream(toArray(input)).distinct());
    }

    public String ascending(String input) {
        return StringUtils.isEmpty(input) ? "" : toString(sort(toArray(input), true));
    }

    public String descending(String input) {
        return StringUtils.isEmpty(input) ? "" : toString(sort(toArray(input), false));
    }

    public static Stream<String> sort(String[] array, boolean ascending) {
        return Arrays.stream(array).sorted((o1, o2) -> ascending ? compare(o1, o2) : compare(o2, o1));
    }

    public String remove(String input, String index) {
        if (StringUtils.isEmpty(input)) { return ""; }
        if (!NumberUtils.isDigits(index)) { return ""; }

        int idx = NumberUtils.toInt(index);
        if (idx < 0) { return ""; }

        String[] arr = toArray(input);
        if (arr.length <= idx) { return ""; }

        String delim = getDelim();
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i == idx) { continue; }
            buffer.append(arr[i]).append(delim);
        }

        return StringUtils.removeEnd(buffer.toString(), delim);
    }

    public String insert(String input, String index, String item) {
        if (StringUtils.isEmpty(input)) { return ""; }
        if (StringUtils.isEmpty(item)) { return ""; }
        if (!NumberUtils.isDigits(index)) { return ""; }

        int idx = NumberUtils.toInt(index);
        if (idx < 0) { return ""; }

        String[] arr = toArray(input);
        if (arr.length <= idx) { return ""; }

        String delim = getDelim();
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i == idx) { buffer.append(item).append(delim); }
            buffer.append(arr[i]).append(delim);
        }

        return StringUtils.removeEnd(buffer.toString(), delim);
    }

    public String prepend(String input, String item) {
        if (StringUtils.isEmpty(input)) { return ""; }
        if (StringUtils.isEmpty(item)) { return ""; }
        return item + getDelim() + input;
    }

    public String append(String input, String item) {
        if (StringUtils.isEmpty(input)) { return ""; }
        if (StringUtils.isEmpty(item)) { return ""; }
        String delim = getDelim();
        return StringUtils.removeEnd(input, delim) + delim + item;
    }

    public String index(String input, String item) {
        if (StringUtils.isEmpty(input)) { return ""; }
        if (StringUtils.isEmpty(item)) { return ""; }

        String[] arr = toArray(input);
        for (int i = 0; i < arr.length; i++) {
            if (StringUtils.equals(arr[i], item)) { return i + ""; }
        }

        return "";
    }

    /** remove empty or null items from {@code input} */
    public String pack(String input) {
        if (StringUtils.isEmpty(input)) { return ""; }

        String[] arr = pack(toArray(input));
        if (ArrayUtils.isEmpty(arr)) { return ""; }
        return toString(Arrays.stream(arr));
    }

    public String replica(String input, String count) {
        if (StringUtils.isEmpty(input)) { return ""; }
        String[] replica = replica(toArray(input), count);
        if (ArrayUtils.isEmpty(replica)) { return ""; }
        return toString(Arrays.stream(replica));
    }

    public String replicaUntil(String input, String size) {
        if (StringUtils.isEmpty(input)) { return ""; }
        String[] replica = replicaUntil(toArray(input), size);
        if (ArrayUtils.isEmpty(replica)) { return ""; }
        return toString(Arrays.stream(replica));
    }

    public static String toString(String... strings) {
        return strings == null ? null : toString(Arrays.stream(strings));
    }

    protected static int compare(String value1, String value2) {
        if (NumberUtils.isParsable(value1) && NumberUtils.isParsable(value2)) {
            return NumberUtils.createBigDecimal(value1).compareTo(NumberUtils.createBigDecimal(value2));
        } else {
            return value1.compareTo(value2);
        }
    }

    protected static String item(String[] arr, String index) {
        return !NumberUtils.isDigits(index) ? "" : item(arr, NumberUtils.toInt(index));
    }

    protected static String item(String[] arr, int idx) {
        if (ArrayUtils.isEmpty(arr)) { return ""; }
        if (idx < 0) { return ""; }
        if (arr.length <= idx) { return ""; }

        return arr[idx];
    }

    /** subarray between start and end, both ends inclusively */
    protected static String[] subarray(String[] arr, String start, String end) {
        if (ArrayUtils.isEmpty(arr)) { return null; }

        if (!NumberUtils.isDigits(start)) { return null; }
        int idxStart = NumberUtils.toInt(start);
        if (idxStart < 0) { return null; }

        int idxEnd;
        if (StringUtils.isEmpty(end) || end.equals("-1")) {
            idxEnd = arr.length - 1;
        } else if (!NumberUtils.isCreatable(end)) {
            return null;
        } else {
            idxEnd = NumberUtils.toInt(end);
        }
        if (idxEnd >= arr.length) { return arr; }
        if (idxEnd == -1) {
            idxEnd = arr.length - 1;
        } else if (idxEnd <= idxStart) {
            return null;
        }

        return ArrayUtils.subarray(arr, idxStart, idxEnd + 1);
    }

    protected static String[] pack(String[] arr) {
        if (ArrayUtils.isEmpty(arr)) { return null; }
        return ArrayUtils.removeElements(ArrayUtils.removeAllOccurrences(arr, ""), (String) null);
    }

    protected static String[] replica(String[] arr, String count) {
        if (ArrayUtils.isEmpty(arr)) { return null; }

        if (!NumberUtils.isDigits(count)) { return arr; }
        int counter = NumberUtils.toInt(count);
        if (counter < 0) { return arr; }

        String[] newArr = new String[]{};
        for (int i = 0; i < counter; i++) {
            newArr = ArrayUtils.addAll(newArr, arr);
        }
        return newArr;
    }

    protected static String[] replicaUntil(String[] arr, String size) {
        if (ArrayUtils.isEmpty(arr)) { return null; }

        if (!NumberUtils.isDigits(size)) { return arr; }

        int maxSize = NumberUtils.toInt(size);
        if (maxSize < 0) { return arr; }

        if (maxSize > arr.length) {
            int maxLoopCount = (maxSize / arr.length) + 1;
            String[] newArr = new String[]{};
            for (int i = 0; i < maxLoopCount; i++) { newArr = ArrayUtils.addAll(newArr, arr); }
            arr = newArr;
        }

        return ArrayUtils.subarray(arr, 0, maxSize);
    }

    protected static String toString(Stream<String> stream) {
        String delim = getDelim();
        StringBuilder buffer = new StringBuilder();
        stream.forEach(item -> buffer.append(StringUtils.replace(item, delim, "\\" + delim)).append(delim));
        return StringUtils.removeEnd(buffer.toString(), delim);
    }

    protected static String[] toArray(String array) { return toArray(array, getDelim()); }

    protected static String[] toArray(String array, String delim) {
        if (!StringUtils.contains(array, delim)) { return new String[]{array}; }

        // need special parsing to compensate the case of web elements which looks like [[...]],[[...]],..
        if (TextUtils.isBetween(array, "[", "]")) {
            if (StringUtils.startsWith(array, "[[") && StringUtils.contains(array, "],[[")) {
                // likely web element lists.. need special care
                String[] split = StringUtils.splitByWholeSeparator(array, "],[");
                return Arrays.stream(split)
                             .map(str -> (StringUtils.startsWith(str, "[[") ? "" : "[") +
                                         StringUtils.appendIfMissing(str, "]"))
                             .toArray(String[]::new);
            }

            array = TextUtils.substringBetweenFirstPair(array, "[", "]");
        }

        if (TextUtils.isBetween(array, "{", "}")) {
            array = TextUtils.substringBetweenFirstPair(array, "{", "}");
        }

        return StringUtils.splitByWholeSeparatorPreserveAllTokens(array, delim);
    }

    protected static String getDelim() {
        ExecutionContext context = ExecutionThread.get();
        return context == null ? getDefault(TEXT_DELIM) : context.getTextDelim();
    }

    protected void init() { }
}
