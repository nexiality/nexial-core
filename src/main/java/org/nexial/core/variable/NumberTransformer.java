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
import java.text.DecimalFormat;
import java.util.Map;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.utils.ConsoleUtils;

import static java.math.RoundingMode.UP;
import static org.nexial.core.variable.ExpressionConst.REGEX_DEC_NUM;

public class NumberTransformer<T extends NumberDataType> extends Transformer {
    private static final Map<String, Integer> FUNCTION_TO_PARAM_LIST = discoverFunctions(NumberTransformer.class);
    private static final Map<String, Method> FUNCTIONS =
        toFunctionMap(FUNCTION_TO_PARAM_LIST, NumberTransformer.class, NumberDataType.class);
    private static final Random randomHelper = new Random();
    static final int DEC_SCALE = 25;
    static final RoundingMode ROUND = UP;

    public TextDataType text(T data) { return super.text(data); }

    public T round(T data) {
        if (data == null || data.getTextValue() == null) { return data; }

        Number number = data.getValue();
        if (number == null) { return data; }

        if (number instanceof Float) {
            int round = Math.round(((Float) number));
            data.setTextValue(round + "");
            data.setValue(round);
        }

        if (number instanceof Double) {
            long round = Math.round(((Double) number));
            data.setTextValue(round + "");
            data.setValue(round);
        }

        return data;
    }

    public T roundTo(T data, String closestDigit) {
        if (data == null || data.getTextValue() == null) { return data; }

        Number number = data.getValue();
        if (number == null) { return data; }

        double num = number.doubleValue();

        // figure out the specified number of whole numbers and fractional digits
        int wholeNumberCount = StringUtils.length(StringUtils.substringBefore(closestDigit, "."));
        int fractionDigitCount = StringUtils.length(StringUtils.substringAfter(closestDigit, "."));

        // we will only use this divisor if there's a whole number or if closestDigit looks like 0.xxx
        // this means that if user specifies
        //  - closestDigit = 0.00, then we will round number to closest 2-digit decimal
        //  - closestDigit = 1.00, then we will round number to closest 2-digit decimal
        //  - closestDigit =  .00, then we will round number to closest 2-digit decimal
        //  - closestDigit = 1.0,  then we will round number to closest 1-digit decimal
        //  - closestDigit = 9.0,  then we will round number to closest 1-digit decimal
        //  - closestDigit = 5,    then we will round number to closest 1's whole number
        //  - closestDigit = 10.00, then we will round number to closest 10's whole number
        //  - closestDigit = 10.99, then we will round number to closest 10's whole number
        //  - closestDigit = 10.0,  then we will round number to closest 10's whole number
        double closestWhole = 0;
        if (wholeNumberCount > 1) {
            closestWhole = NumberUtils.toDouble("1" + StringUtils.repeat("0", wholeNumberCount - 1));
            // since we are rounding to closest 10's or higher position, decimal places have no meaning here
            fractionDigitCount = 0;
        }

        DecimalFormat df = new DecimalFormat();
        df.setGroupingUsed(false);
        df.setMaximumFractionDigits(fractionDigitCount);
        df.setMinimumFractionDigits(fractionDigitCount);
        String rounded = df.format(closestWhole == 0 ? num : Math.round(num / closestWhole) * closestWhole);
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
        data.setTextValue(data.getValue() + "");
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
        data.setTextValue(data.getValue() + "");
        return data;
    }

    public T minus(T data, String... numbers) {
        if (data == null || data.getTextValue() == null) { return data; }
        if (ArrayUtils.isEmpty(numbers)) {
            ConsoleUtils.log(data.getName() + ".minus(): cannot operate on '" + numbers + "'");
            return data;
        }

        Number numberObject = data.getValue();
        if (numberObject == null) { return data; }

        data.setValue(minusSequentially(numberObject, numbers));
        data.setTextValue(data.getValue() + "");
        return data;
    }

    public T multiply(T data, String... numbers) {
        if (data == null || data.getTextValue() == null) { return data; }
        if (ArrayUtils.isEmpty(numbers)) {
            ConsoleUtils.log(data.getName() + ".multiply(): cannot operate on '" + numbers + "'");
            return data;
        }

        Number numberObject = data.getValue();
        if (numberObject == null) { return data; }

        data.setValue(multiplySequentially(numberObject, numbers));
        data.setTextValue(data.getValue() + "");
        return data;
    }

    public T divide(T data, String... numbers) {
        if (data == null || data.getTextValue() == null) { return data; }
        if (ArrayUtils.isEmpty(numbers)) {
            ConsoleUtils.log(data.getName() + ".divide(): cannot operate on '" + numbers + "'");
            return data;
        }

        Number numberObject = data.getValue();
        if (numberObject == null) { return data; }

        data.setValue(divideSequential(numberObject, numbers));
        data.setTextValue(data.getValue() + "");
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

        if (number instanceof Float) {
            int floor = (int) Math.floor((Float) number);
            data.setTextValue(floor + "");
            data.setValue(floor);
        }

        if (number instanceof Double) {
            long floor = (long) Math.floor((Double) number);
            data.setTextValue(floor + "");
            data.setValue(floor);
        }

        return data;
    }

    public T ceiling(T data) {
        if (data == null || data.getTextValue() == null) { return data; }

        Number number = data.getValue();
        if (number == null) { return data; }

        if (number instanceof Float) {
            int ceiling = (int) Math.ceil((Float) number);
            data.setTextValue(ceiling + "");
            data.setValue(ceiling);
        }

        if (number instanceof Double) {
            long ceiling = (long) Math.ceil((Double) number);
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
        int count = numbers.length + (base == null ? 0 : 1);
        return BigDecimal.valueOf(addSequentially(base, numbers).doubleValue())
                         .divide(BigDecimal.valueOf(count), DEC_SCALE, ROUND)
                         .doubleValue();
    }

    public static Number addSequentially(Number base, String... numbers) {
        for (String number : numbers) {
            number = StringUtils.trim(number);
            if (!NumberUtils.isParsable(number)) {
                ConsoleUtils.log("[" + number + "] is not a number; ignored...");
            } else if (isDecimal(number) || base instanceof Double || base instanceof Float) {
                base = BigDecimal.valueOf(base == null ? 0 : base.doubleValue())
                                 .add(BigDecimal.valueOf(NumberUtils.toDouble(number)))
                                 .doubleValue();
            } else {
                base = base.longValue() + NumberUtils.toLong(number);
            }
        }
        return base;
    }

    public static Number minusSequentially(Number base, String... numbers) {
        for (String number : numbers) {
            number = StringUtils.trim(number);
            if (!NumberUtils.isParsable(number)) {
                ConsoleUtils.log("[" + number + "] is not a number; ignored...");
            } else if (isDecimal(number) || base instanceof Double || base instanceof Float) {
                base = BigDecimal.valueOf(base == null ? 0 : base.doubleValue())
                                 .subtract(BigDecimal.valueOf(NumberUtils.toDouble(number))).doubleValue();
            } else {
                base = (base == null ? 0 : base.longValue()) - NumberUtils.toLong(number);
            }
        }
        return base;
    }

    public static Number multiplySequentially(Number base, String... numbers) {
        for (String number : numbers) {
            number = StringUtils.trim(number);

            if (!NumberUtils.isParsable(number)) {
                ConsoleUtils.log("[" + number + "] is not a number; ignored...");
                continue;
            }

            if (base == null) {
                base = isDecimal(number) ? NumberUtils.toDouble(number) : NumberUtils.toLong(number);
                continue;
            }

            if (isDecimal(number) || base instanceof Double || base instanceof Float) {
                base = BigDecimal.valueOf(base.doubleValue())
                                 .multiply(BigDecimal.valueOf(NumberUtils.toDouble(number))).doubleValue();
            } else {
                base = base.longValue() * NumberUtils.toLong(number);
            }
        }

        return base;
    }

    public static Number divideSequential(Number base, String... numbers) {
        for (String number : numbers) {
            number = StringUtils.trim(number);

            if (!NumberUtils.isParsable(number)) {
                ConsoleUtils.log("[" + number + "] is not a number; ignored...");
                continue;
            }

            if (base == null) {
                base = isDecimal(number) ? NumberUtils.toDouble(number) : NumberUtils.toLong(number);
                continue;
            }

            if (isDecimal(number) || base instanceof Double || base instanceof Float) {
                base = BigDecimal.valueOf(base.doubleValue())
                                 .divide(BigDecimal.valueOf(NumberUtils.toDouble(number)), DEC_SCALE, ROUND)
                                 .doubleValue();
            } else {
                BigDecimal result = BigDecimal.valueOf(base.longValue())
                                              .divide(BigDecimal.valueOf(NumberUtils.toLong(number)), DEC_SCALE, ROUND);
                if (result.doubleValue() == result.longValue()) {
                    // maintain current "whole number" representation
                    base = result.longValue();
                } else {
                    base = result.doubleValue();
                }
            }
        }

        return base;
    }

    @Override
    Map<String, Integer> listSupportedFunctions() { return FUNCTION_TO_PARAM_LIST; }

    @Override
    Map<String, Method> listSupportedMethods() { return FUNCTIONS; }

    protected static boolean isDecimal(String number) {
        return RegexUtils.isExact(number, REGEX_DEC_NUM);
        // && !RegexUtils.isExact(number, REGEX_INT_PRETEND_DOUBLE);
    }
}
