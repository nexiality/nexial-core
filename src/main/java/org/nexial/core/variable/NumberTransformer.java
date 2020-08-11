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

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Map;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.plugins.base.NumberCommand;
import org.nexial.core.utils.ConsoleUtils;

import static java.math.RoundingMode.UP;
import static org.nexial.commons.utils.TextUtils.CleanNumberStrategy.REAL;
import static org.nexial.core.variable.ExpressionConst.REGEX_DEC_NUM;

public class NumberTransformer<T extends NumberDataType> extends Transformer {
    private static final Map<String, Integer> FUNCTION_TO_PARAM_LIST = discoverFunctions(NumberTransformer.class);
    private static final Map<String, Method> FUNCTIONS =
        toFunctionMap(FUNCTION_TO_PARAM_LIST, NumberTransformer.class, NumberDataType.class);
    private static final Random randomHelper = new Random();
    static final int DEC_SCALE = 25;
    static final RoundingMode ROUND = UP;

    public TextDataType text(T data) { return super.text(data); }

    public T abs(T data) {
        if (data == null || data.getTextValue() == null) { return data; }

        Number number = data.getValue();
        if (number == null) { return data; }

        if (isDecimal(number)) {
            double abs = Math.abs(number.doubleValue());
            data.setTextValue(abs);
            data.setValue(abs);
        } else {
            long abs = Math.abs((long) number.doubleValue());
            data.setTextValue(abs);
            data.setValue(abs);
        }

        return data;
    }

    public T whole(T data) {
        if (data == null || data.getTextValue() == null) { return data; }

        Number number = data.getValue();
        if (number == null) { return data; }

        if (isDecimal(number)) {
            long round = (long) number.doubleValue();
            data.setTextValue(round);
            data.setValue(round);
        }

        return data;
    }

    public T roundTo(T data, String closestDigit) {
        if (data == null || data.getTextValue() == null) { return data; }

        Number number = data.getValue();
        if (number == null) { return data; }

        String rounded = NumberCommand.roundTo(number.doubleValue(), closestDigit);
        data.setTextValue(rounded);
        data.setValue(NumberUtils.createNumber(rounded));

        return data;
    }

    public T average(T data, String... numbers) {
        if (data == null || data.getTextValue() == null) { return data; }
        if (ArrayUtils.isEmpty(numbers)) {
            ConsoleUtils.log(data.getName() + ".average(): cannot operate on empty number");
            return data;
        }

        Number numberObject = data.getValue();
        if (numberObject == null) { return data; }

        data.setValue(average(numberObject, numbers));
        data.setTextValue(data.getValue());
        return data;
    }

    public T add(T data, String... numbers) {
        if (data == null || data.getTextValue() == null) { return data; }
        if (ArrayUtils.isEmpty(numbers)) {
            ConsoleUtils.log(data.getName() + ".add(): cannot operate on empty number");
            return data;
        }

        Number numberObject = data.getValue();
        if (numberObject == null) { return data; }

        data.setValue(addSequentially(numberObject, numbers));
        data.setTextValue(data.getValue());
        return data;
    }

    public T minus(T data, String... numbers) {
        if (data == null || data.getTextValue() == null) { return data; }
        if (ArrayUtils.isEmpty(numbers)) {
            ConsoleUtils.log(data.getName() + ".minus(): cannot operate on '" + ArrayUtils.toString(numbers) + "'");
            return data;
        }

        Number numberObject = data.getValue();
        if (numberObject == null) { return data; }

        data.setValue(minusSequentially(numberObject, numbers));
        data.setTextValue(data.getValue());
        return data;
    }

    public T multiply(T data, String... numbers) {
        if (data == null || data.getTextValue() == null) { return data; }
        if (ArrayUtils.isEmpty(numbers)) {
            ConsoleUtils.log(data.getName() + ".multiply(): cannot operate on empty list of numbers");
            return data;
        }

        Number numberObject = data.getValue();
        if (numberObject == null) { return data; }

        data.setValue(multiplySequentially(numberObject, numbers));
        data.setTextValue(data.getValue());
        return data;
    }

    public T divide(T data, String... numbers) {
        if (data == null || data.getTextValue() == null) { return data; }
        if (ArrayUtils.isEmpty(numbers)) {
            ConsoleUtils.log(data.getName() + ".divide(): cannot operate on '" + ArrayUtils.toString(numbers) + "'");
            return data;
        }

        Number numberObject = data.getValue();
        if (numberObject == null) { return data; }

        data.setValue(divideSequential(numberObject, numbers));
        data.setTextValue(data.getValue());
        return data;
    }

    public T randomDigits(T data, String length) {
        String rand = randomHelper.integer(length);
        data.setTextValue(rand);
        data.setValue(NumberUtils.toLong(rand));
        return data;
    }

    public T randomDecimal(T data, String wholeLength, String fractionLength) {
        String rand = randomHelper.decimal(wholeLength + "," + fractionLength);
        data.setTextValue(rand);
        data.setValue(NumberUtils.toDouble(rand));
        return data;
    }

    public T randomInteger(T data, String atLeast, String atMost) {
        String rand = randomHelper.numeric(atLeast, atMost);
        data.setTextValue(rand);
        data.setValue(NumberUtils.toLong(rand));
        return data;
    }

    public T floor(T data) {
        if (data == null || data.getTextValue() == null) { return data; }

        Number number = data.getValue();
        if (number == null) { return data; }

        if (isDecimal(number)) {
            long floor = (long) Math.floor(number.doubleValue());
            data.setTextValue(floor + "");
            data.setValue(floor);
        }

        return data;
    }

    public T ceiling(T data) {
        if (data == null || data.getTextValue() == null) { return data; }

        Number number = data.getValue();
        if (number == null) { return data; }

        if (isDecimal(number)) {
            long ceiling = (long) Math.ceil(number.doubleValue());
            data.setTextValue(ceiling + "");
            data.setValue(ceiling);
        }

        return data;
    }

    public T store(T data, String var) {
        snapshot(var, data);
        return data;
    }

    @NotNull
    public static Number average(Number base, String... numbers) {
        if (ArrayUtils.isEmpty(numbers)) { return base; }

        // +1 because of base
        numbers = Arrays.stream(numbers).filter(StringUtils::isNotBlank).toArray(String[]::new);
        int count = numbers.length + (base == null ? 0 : 1);
        return BigDecimal.valueOf(addSequentially(base, numbers).doubleValue())
                         .divide(BigDecimal.valueOf(count), DEC_SCALE, ROUND)
                         .doubleValue();
    }

    public static Number addSequentially(Number base, String... numbers) {
        if (base == null) { base = 0; }
        for (String number : numbers) {
            if (StringUtils.isBlank(number)) { continue; }
            try {
                number = TextUtils.cleanNumber(number, REAL);
                if (isDecimal(number) || base instanceof Double || base instanceof Float) {
                    base = BigDecimal.valueOf(base.doubleValue())
                                     .add(BigDecimal.valueOf(NumberUtils.toDouble(number)))
                                     .doubleValue();
                } else {
                    base = base.longValue() + NumberUtils.toLong(number);
                }
            } catch (IllegalArgumentException e) {
                ConsoleUtils.log("[" + number + "] is not a number; ignored...");
            }
        }

        return base;
    }

    public static Number minusSequentially(Number base, String... numbers) {
        if (base == null) { base = 0; }
        for (String number : numbers) {
            if (StringUtils.isBlank(number)) { continue; }
            try {
                number = TextUtils.cleanNumber(number, REAL);
                if (isDecimal(number) || base instanceof Double || base instanceof Float) {
                    base = BigDecimal.valueOf(base.doubleValue())
                                     .subtract(BigDecimal.valueOf(NumberUtils.toDouble(number))).doubleValue();
                } else {
                    base = base.longValue() - NumberUtils.toLong(number);
                }
            } catch (IllegalArgumentException e) {
                ConsoleUtils.log("[" + number + "] is not a number; ignored...");
            }
        }
        return base;
    }

    public static Number multiplySequentially(Number base, String... numbers) {
        for (String number : numbers) {
            if (StringUtils.isBlank(number)) { continue; }
            try {
                number = TextUtils.cleanNumber(number, REAL);
                if (base == null) {
                    base = isDecimal(number) ?
                           BigDecimal.valueOf(NumberUtils.toDouble(number)) : NumberUtils.toLong(number);
                } else if (isDecimal(number) || isDecimal(base)) {
                    base = BigDecimal.valueOf(base.doubleValue())
                                     .multiply(BigDecimal.valueOf(NumberUtils.toDouble(number)))
                                     .doubleValue();
                } else {
                    base = base.longValue() * NumberUtils.toLong(number);
                }
            } catch (IllegalArgumentException e) {
                ConsoleUtils.log("[" + number + "] is not a number; ignored...");
            }
        }

        return base;
    }

    public static Number divideSequential(Number base, String... numbers) {
        for (String num : numbers) {
            if (StringUtils.isBlank(num)) { continue; }
            try {
                String number = TextUtils.cleanNumber(num, REAL);
                if (StringUtils.equals(number, "0") && RegexUtils.match(num, "[A-Za-z]+")) {
                    // not a number, we want to avoid "divide by zero"
                    ConsoleUtils.log("[" + num + "] is not a number; ignored...");
                    continue;
                }

                if (base == null) {
                    base = isDecimal(number) ? NumberUtils.toDouble(number) : NumberUtils.toLong(number);
                    continue;
                }

                if (isDecimal(number) || isDecimal(base)) {
                    base = BigDecimal.valueOf(base.doubleValue())
                                     .divide(BigDecimal.valueOf(NumberUtils.toDouble(number)), DEC_SCALE, ROUND)
                                     .doubleValue();
                } else {
                    BigDecimal result =
                        BigDecimal.valueOf(base.longValue())
                                  .divide(BigDecimal.valueOf(NumberUtils.toLong(number)), DEC_SCALE, ROUND);
                    if (result.doubleValue() == result.longValue()) {
                        // maintain current "whole number" representation
                        base = result.longValue();
                    } else {
                        base = result.doubleValue();
                    }
                }
            } catch (IllegalArgumentException e) {
                ConsoleUtils.log("[" + num + "] is not a number; ignored...");
            }
        }

        return base;
    }

    @Override
    Map<String, Integer> listSupportedFunctions() { return FUNCTION_TO_PARAM_LIST; }

    @Override
    Map<String, Method> listSupportedMethods() { return FUNCTIONS; }

    protected static boolean isDecimal(String number) { return RegexUtils.isExact(number, REGEX_DEC_NUM); }

    protected static boolean isDecimal(Number number) {
        return number instanceof BigDecimal || number instanceof Double || number instanceof Float;
    }
}
