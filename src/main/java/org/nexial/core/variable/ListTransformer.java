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

package org.nexial.core.variable;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.collections4.SetUtils.SetView;
import org.apache.commons.collections4.set.ListOrderedSet;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.TEXT_DELIM;
import static org.nexial.core.SystemVariables.getDefault;
import static org.nexial.core.variable.ExpressionConst.ALIAS_EMPTY;
import static org.nexial.core.variable.ExpressionUtils.fixControlChars;
import static org.nexial.core.variable.NumberTransformer.DEC_SCALE;
import static org.nexial.core.variable.NumberTransformer.ROUND;

public class ListTransformer<T extends ListDataType> extends Transformer {
    private static final Map<String, Integer> FUNCTION_TO_PARAM = discoverFunctions(ListTransformer.class);
    private static final Map<String, Method> FUNCTIONS =
        toFunctionMap(FUNCTION_TO_PARAM, ListTransformer.class, ListDataType.class);

    public TextDataType text(T data) { return super.text(data); }

    public NumberDataType sum(T data) {
        NumberDataType newData;
        try {
            newData = new NumberDataType("0");
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to create number: " + e.getMessage(), e);
        }

        if (data == null || data.getValue() == null) { return newData; }

        BigDecimal[] sum = new BigDecimal[]{new BigDecimal(0)};
        String[] array = data.getValue();
        Arrays.stream(array).forEach(item -> sum[0] = toBigDecimal(item).add(sum[0]));

        newData.setTextValue(sum[0].toPlainString());
        newData.setValue(sum[0]);
        return newData;
    }

    public NumberDataType max(T data) {
        if (data == null || data.getValue() == null) { return null; }

        Number[] max = new Number[]{null};
        String[] array = data.getValue();
        Arrays.stream(array).forEach(item -> {
            String numString = StringUtils.trim(item);
            if (StringUtils.isNotEmpty(numString)) {
                Number num = NumberUtils.createNumber(numString);
                if (max[0] == null || num.doubleValue() > max[0].doubleValue()) { max[0] = num; }
            }
        });

        // probably no compare done
        if (max[0] == null) { return null; }

        try {
            return new NumberDataType(max[0] + "");
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to create number: " + e.getMessage(), e);
        }
    }

    public NumberDataType min(T data) {
        if (data == null || data.getValue() == null) { return null; }

        Number[] min = new Number[]{null};
        String[] array = data.getValue();
        Arrays.stream(array).forEach(item -> {
            String numString = StringUtils.trim(item);
            if (StringUtils.isNotEmpty(numString)) {
                Number num = NumberUtils.createNumber(numString);
                if (min[0] == null || num.doubleValue() < min[0].doubleValue()) { min[0] = num; }
            }
        });

        // probably no compare done
        if (min[0] == null) { return null; }

        try {
            return new NumberDataType(min[0] + "");
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to create number: " + e.getMessage(), e);
        }
    }

    public NumberDataType average(T data) {
        NumberDataType newData;
        try {
            newData = new NumberDataType("0");
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to create number: " + e.getMessage(), e);
        }

        if (data == null || data.getValue() == null) { return newData; }

        String[] array = data.getValue();
        BigDecimal sum = new BigDecimal(0);
        int count = 0;
        for (String item : array) {
            item = StringUtils.trim(item);
            if (NumberUtils.isCreatable(StringUtils.trim(item))) {
                sum = sum.add(new BigDecimal(item));
                count++;
            }
        }

        if (count == 0) { return newData; }

        double average = sum.divide(BigDecimal.valueOf(count), DEC_SCALE, ROUND).doubleValue();
        if (average == 0) { return newData; }

        newData.setTextValue(BigDecimal.valueOf(average).toPlainString());
        newData.setValue(average);
        return newData;
    }

    public TextDataType item(T data, String... indices) {
        if (data == null || data.getValue() == null || ArrayUtils.isEmpty(indices)) { return null; }

        ExecutionContext context = ExecutionThread.get();
        String delim = context == null ? getDefault(TEXT_DELIM) : context.getTextDelim();
        StringBuilder buffer = new StringBuilder();

        for (String index : indices) {
            if (!NumberUtils.isDigits(index)) {
                throw new IllegalArgumentException("LIST: " + index + " is not a number; index must be numeric");
            }

            buffer.append(Array.item(data.getValue(), index)).append(delim);
        }

        try {
            return new TextDataType(StringUtils.removeEnd(buffer.toString(), delim));
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to extract text: " + e.getMessage(), e);
        }
    }

    public NumberDataType size(T data) { return length(data); }

    public NumberDataType count(T data) { return length(data); }

    public NumberDataType length(T data) {
        NumberDataType length;
        try {
            length = new NumberDataType("0");
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to create number: " + e.getMessage(), e);
        }

        if (data == null || data.getValue() == null) { return length; }

        int size = data.getValue().length;
        length.setValue(size);
        length.setTextValue(size + "");
        return length;
    }

    public TextDataType first(T data) {
        if (data == null || data.getValue() == null) { return null; }

        try {
            String[] array = data.getValue();
            if (ArrayUtils.isEmpty(array)) { return null; }
            return new TextDataType(array[0]);
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to extract text: " + e.getMessage(), e);
        }
    }

    public TextDataType last(T data) {
        if (data == null || data.getValue() == null) { return null; }

        try {
            String[] array = data.getValue();
            if (ArrayUtils.isEmpty(array)) { return null; }
            return new TextDataType(array[array.length - 1]);
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to extract text: " + e.getMessage(), e);
        }
    }

    public T pack(T data) {
        if (data == null || data.getValue() == null) { return null; }
        return updateValue(data, Array.pack(data.getValue()));
    }

    public T reverse(T data) {
        if (data == null || data.getValue() == null) { return data; }

        String[] arr = data.getValue();
        ArrayUtils.reverse(arr);
        return updateValue(data, arr);
    }

    public T sublist(T data, String start, String end) {
        if (data == null || data.getValue() == null) { return data; }
        return updateValue(data, Array.subarray(data.getValue(), start, end));
    }

    public T distinct(T data) {
        if (data == null || data.getValue() == null) { return null; }
        return updateValue(data, Arrays.stream(data.getValue()).distinct());
    }

    public T ascending(T data) {
        return data == null || data.getValue() == null ? data : updateValue(data, Array.sort(data.getValue(), true));
    }

    public T descending(T data) {
        return data == null || data.getValue() == null ? data : updateValue(data, Array.sort(data.getValue(), false));
    }

    public T remove(T data, String index) {
        if (data == null || data.getValue() == null || !NumberUtils.isDigits(index)) { return data; }

        int idx = NumberUtils.toInt(index);
        if (idx < 0) { return data; }

        return updateValue(data, ArrayUtils.remove(data.getValue(), idx));
    }

    public T removeItems(T data, String... items) {
        if (data == null || data.getValue() == null || ArrayUtils.isEmpty(items)) { return data; }

        String[] array = data.getValue();
        for (String item : items) { array = ArrayUtils.removeAllOccurences(array, fixControlChars(item)); }
        return updateValue(data, array);
    }

    public T insert(T data, String index, String item) {
        if (data == null || StringUtils.isEmpty(item) || !NumberUtils.isDigits(index)) { return data; }

        int idx = NumberUtils.toInt(index);
        if (idx < 0) { return data; }

        return updateValue(data, ArrayUtils.insert(idx, data.getValue(), item));
    }

    public T prepend(T data, String... items) {
        if (data == null || data.getValue() == null) { return null; }
        if (ArrayUtils.isEmpty(items)) {
            return updateValue(data, ArrayUtils.insert(0, data.getValue(), ""));
        } else {
            return updateValue(data, ArrayUtils.insert(0, data.getValue(), items));
        }
    }

    public T append(T data, String... items) {
        if (data == null || data.getValue() == null) { return data; }
        if (ArrayUtils.isEmpty(items)) {
            return updateValue(data, ArrayUtils.addAll(data.getValue(), ""));
        } else {
            return updateValue(data, ArrayUtils.addAll(data.getValue(), items));
        }
    }

    public NumberDataType index(T data, String item) {
        if (data == null || data.getValue() == null || StringUtils.isEmpty(item)) { return null; }

        String[] list = data.getValue();
        if (ArrayUtils.isEmpty(list)) { return null; }

        try {
            int position = -1;
            String matchTo = fixControlChars(item);

            if (StringUtils.startsWith(matchTo, CONTAIN_PREFIX)) {
                String substring = StringUtils.substringAfter(matchTo, CONTAIN_PREFIX);
                position = IntStream.range(0, list.length)
                                    .filter(i -> StringUtils.contains(list[i], substring))
                                    .findFirst()
                                    .orElse(-1);
            } else if (StringUtils.startsWith(matchTo, REGEX_PREFIX)) {
                String regex = StringUtils.substringAfter(matchTo, REGEX_PREFIX);
                position = IntStream.range(0, list.length)
                                    .filter(i -> RegexUtils.match(list[i], regex))
                                    .findFirst()
                                    .orElse(-1);
            } else {
                position = ArrayUtils.indexOf(list, matchTo);
            }

            if (position < 0) { return null; }

            NumberDataType number = new NumberDataType("0");
            number.setValue(position);
            number.setTextValue(position + "");
            return number;
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to create number: " + e.getMessage(), e);
        }
    }

    public T replica(T data, String count) {
        if (data == null || data.getValue() == null) { return null; }
        return updateValue(data, Array.replica(data.getValue(), count));
    }

    public T replicaUntil(T data, String size) {
        if (data == null || data.getValue() == null) { return null; }
        return updateValue(data, Array.replicaUntil(data.getValue(), size));
    }

    public T join(T data, String... array) {
        if (data == null || data.getValue() == null || ArrayUtils.isEmpty(array)) { return data; }
        return updateValue(data, ArrayUtils.addAll(data.getValue(), array));
    }

    public T union(T data, String... array) {
        if (data == null || data.getValue() == null || ArrayUtils.isEmpty(array)) { return data; }
        SetView<String> union = SetUtils.union(ListOrderedSet.listOrderedSet(Arrays.asList(data.getValue())),
                                               ListOrderedSet.listOrderedSet(Arrays.asList(array)));
        return updateValue(data, union.toArray(new String[union.size()]));
    }

    public T intersect(T data, String... array) {
        if (data == null || data.getValue() == null || ArrayUtils.isEmpty(array)) { return data; }
        List<String> intersect = ListUtils.intersection(Arrays.asList(data.getValue()), Arrays.asList(array));
        return updateValue(data, intersect.toArray(new String[intersect.size()]));
    }

    /**
     * replace text occurrences in each list item as specified via {@code searchFor} with {@code replaceWidth}
     */
    public T replace(T data, String searchFor, String replaceWith) {
        if (data == null || data.getValue() == null) { return data; }

        String replaceBy = treatCommonValueShorthand(fixControlChars(StringUtils.defaultString(replaceWith)));
        String searchBy = treatCommonValueShorthand(fixControlChars(searchFor));

        List<String> replaced = new ArrayList<>();
        Arrays.stream(data.getValue()).forEach(item -> replaced.add(
            StringUtils.equals(item, searchBy) ? replaceBy : StringUtils.replace(item, searchBy, replaceBy)));

        return updateValue(data, replaced.toArray(new String[replaced.size()]));
    }

    public T replaceRegex(T data, String searchFor, String replaceWith) {
        if (data == null || data.getValue() == null || StringUtils.isEmpty(searchFor)) { return data; }

        String replaceBy = treatCommonValueShorthand(fixControlChars(StringUtils.defaultString(replaceWith)));
        String searchBy = treatCommonValueShorthand(fixControlChars(searchFor));

        List<String> replaced = new ArrayList<>();
        Arrays.stream(data.getValue())
              .forEach(item -> replaced.add(RegexUtils.replace(item, searchBy, replaceBy)));

        return updateValue(data, replaced.toArray(new String[replaced.size()]));
    }

    /**
     * replace items that entirely matches {@code searchFor} with {@code replaceWith} (also entirely)
     */
    public T replaceItem(T data, String searchFor, String replaceWith) {
        if (data == null || data.getValue() == null) { return data; }

        String replaceBy = treatCommonValueShorthand(fixControlChars(StringUtils.defaultString(replaceWith)));
        String searchBy = treatCommonValueShorthand(fixControlChars(searchFor));

        List<String> replaced = new ArrayList<>();
        Arrays.stream(data.getValue())
              .forEach(item -> replaced.add(StringUtils.equals(item, searchBy) ? replaceBy : item));

        return updateValue(data, replaced.toArray(new String[replaced.size()]));
    }

    public TextDataType combine(T data, String delim) {
        TextDataType textData;
        try {
            textData = new TextDataType("");
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to generate text: " + e.getMessage(), e);
        }

        if (data == null || data.getValue() == null) { return textData; }

        if (ALIAS_EMPTY.contains(delim)) { delim = ""; }
        String delimiter = StringUtils.defaultString(fixControlChars(delim));

        StringBuilder buffer = new StringBuilder();
        String[] array = data.getValue();
        Arrays.stream(array).forEach(item -> buffer.append(StringUtils.defaultString(item)).append(delimiter));
        String text = buffer.toString();
        if (StringUtils.isNotEmpty(delimiter)) { text = StringUtils.removeEnd(text, delimiter); }

        textData.setValue(text);
        return textData;
    }

    public T store(T data, String var) {
        snapshot(var, data);
        return data;
    }

    public T saveItems(T data, String... indexAndVar) {
        if (data == null || ArrayUtils.isEmpty(data.value)) { return data; }
        if (ArrayUtils.isEmpty(indexAndVar)) { return data; }

        ExecutionContext context = ExecutionThread.get();

        Map<String, String> mapping = TextUtils.toMap("=", indexAndVar);
        mapping.forEach((index, var) -> {
            if (!NumberUtils.isDigits(StringUtils.trim(index))) {
                ConsoleUtils.log(index + " is not a valid index for current LIST");
            } else {
                int idx = NumberUtils.toInt(StringUtils.trim(index));
                int listSize = data.value.length;
                if (idx < 0 || idx >= listSize) {
                    ConsoleUtils.log(index + " is not a valid index for current LIST (size=" + listSize + ")");
                } else {
                    context.setData(StringUtils.trim(var), data.value[idx]);
                }
            }
        });

        return data;
    }

    @Override
    Map<String, Integer> listSupportedFunctions() { return FUNCTION_TO_PARAM; }

    @Override
    Map<String, Method> listSupportedMethods() { return FUNCTIONS; }

    protected static BigDecimal toBigDecimal(String num) {
        if (StringUtils.isBlank(num)) { return BigDecimal.valueOf(0); }
        int numberOfDecimal = StringUtils.length(StringUtils.substringAfterLast(num, "."));
        return NumberUtils.toScaledBigDecimal(NumberUtils.toDouble(num), numberOfDecimal, ROUND);
    }

    protected T updateValue(T data, Stream<String> updated) {
        if (data == null || updated == null) { return null; }
        String textValue = Array.toString(updated);
        data.setTextValue(textValue);
        data.setValue(Array.toArray(textValue));
        return data;
    }

    protected T updateValue(T data, String... array) {
        data.setValue(Arrays.stream(array).map(ExpressionUtils::fixControlChars).toArray(String[]::new));
        data.setTextValue(fixControlChars(Array.toString(array)));
        return data;
    }
}
