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

package org.nexial.core.plugins.base;

import java.math.BigDecimal;
import java.text.DecimalFormat;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

public class NumberFormatHelper {
    private boolean comma;
    private boolean decimalPoint;
    private int decimalPlaces;
    private boolean leadingZero = true;
    private NegativeIndicator negativeIndicator;

    private DecimalFormat formatter;
    private String originalFormat;
    private String normalFormat;
    private BigDecimal value;

    public enum NegativeIndicator {left, right, parenthesis}

    private NumberFormatHelper(String numberString) {
        originalFormat = StringUtils.trim(numberString);

        if (StringUtils.isEmpty(originalFormat)) { return; }

        normalFormat = originalFormat;

        // is negative?
        if (StringUtils.startsWith(normalFormat, "(") && StringUtils.endsWith(normalFormat, ")")) {
            negativeIndicator = NegativeIndicator.parenthesis;
            normalFormat = "-" + StringUtils.trim(StringUtils.substring(normalFormat, 1, normalFormat.length() - 1));
        } else if (StringUtils.endsWith(normalFormat, "-")) {
            negativeIndicator = NegativeIndicator.right;
            normalFormat = "-" + StringUtils.trim(StringUtils.removeEnd(normalFormat, "-"));
        } else if (StringUtils.startsWith(normalFormat, "-")) {
            negativeIndicator = NegativeIndicator.left;
        }

        String wholeNumbers;
        String decimalNumbers;

        // is all digits for decimal?
        if (StringUtils.contains(normalFormat, ".")) {
            decimalPoint = true;
            decimalNumbers = StringUtils.substringAfter(normalFormat, ".");
            if (!NumberUtils.isDigits(decimalNumbers)) {
                // not all digits in decimal places
                throw new NumberFormatException("Expects digits for decimals, but instead found " + decimalNumbers);
            }

            decimalPlaces = decimalNumbers.length();
            wholeNumbers = StringUtils.substringBefore(normalFormat, ".");
        } else {
            wholeNumbers = normalFormat;
            decimalNumbers = "";
        }

        // is whole number portion all digits?
        wholeNumbers = StringUtils.removeStart(wholeNumbers, "-");
        if (StringUtils.contains(wholeNumbers, ",")) {
            comma = true;
            wholeNumbers = StringUtils.remove(wholeNumbers, ",");
        }

        // is it a decimal numeric value without leading zero?
        if (StringUtils.isEmpty(wholeNumbers)) {
            leadingZero = false;
            wholeNumbers = "0";
        } else {
            if (!NumberUtils.isDigits(wholeNumbers)) {
                throw new NumberFormatException("Found unexpected characters: " + originalFormat);
            }
        }

        formatter = new DecimalFormat();
        formatter.setGroupingUsed(comma);
        if (decimalPoint) {
            formatter.setMinimumFractionDigits(decimalPlaces);
        } else {
            formatter.setMinimumFractionDigits(0);
        }
        if (negativeIndicator == NegativeIndicator.parenthesis) {
            formatter.setNegativePrefix("(");
            formatter.setNegativeSuffix(")");
        }
        if (negativeIndicator == NegativeIndicator.right) {
            formatter.setNegativePrefix("");
            formatter.setNegativeSuffix("-");
        }
        if (negativeIndicator == NegativeIndicator.left) {
            formatter.setNegativePrefix("-");
            formatter.setNegativeSuffix("");
        }

        // final normalFormat form
        normalFormat = (StringUtils.startsWith(normalFormat, "-") ? "-" : "") +
                       NumberUtils.toLong(wholeNumbers) +
                       (decimalPoint ? "." + decimalNumbers : "");

        // derived numeric value
        value = BigDecimal.valueOf(NumberUtils.toDouble(normalFormat));
    }

    public static NumberFormatHelper newInstance(String numberString) {
        return new NumberFormatHelper(numberString);
    }

    public String getOriginalFormat() { return originalFormat; }

    public String getNormalFormat() { return normalFormat; }

    public BigDecimal getValue() { return value; }

    public NumberFormatHelper addValue(String numberString) {
        NumberFormatHelper additiveFormatHelper = NumberFormatHelper.newInstance(numberString);
        value = value.add(additiveFormatHelper.value);
        normalFormat = value.toString();
        originalFormat = formatter.format(value.doubleValue());
        if (!leadingZero) { originalFormat = StringUtils.replace(originalFormat, "0.", "."); }
        return this;
    }

    public NumberFormatHelper subtractValue(String numberString) {
        NumberFormatHelper additiveFormatHelper = NumberFormatHelper.newInstance(numberString);
        value = value.subtract(additiveFormatHelper.value);
        normalFormat = value.toString();
        originalFormat = formatter.format(value.doubleValue());
        if (!leadingZero) { originalFormat = StringUtils.replace(originalFormat, "0.", "."); }
        return this;
    }
}
