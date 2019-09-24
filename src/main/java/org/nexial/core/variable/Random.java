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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.core.utils.ConsoleUtils;

public class Random {
    public String integer(String length) {
        if (isDigit("random.integer()", length)) { return ""; }
        int len = NumberUtils.toInt(length);
        if (len == 0) { return ""; }
        if (len == 1) { return RandomStringUtils.randomNumeric(len); }
        return RandomStringUtils.random(1, "123456789") + RandomStringUtils.randomNumeric(len - 1);
    }

    public String decimal(String wholeAndFraction) {
        String[] sizes = StringUtils.split(wholeAndFraction, ",");
        if (ArrayUtils.isEmpty(sizes)) {
            ConsoleUtils.error("Error at random.decimal() - wholeAndFraction " + wholeAndFraction + " is not valid");
            return "";
        }

        String whole = integer(sizes[0]);
        if (sizes.length < 2) { return whole; }

        String decimal = integer(sizes[1]);
        if (StringUtils.isNotBlank(decimal)) {
            return whole + "." + decimal;
        } else {
            return whole;
        }
    }

    public String letter(String length) {
        if (isDigit("random.letter()", length)) { return ""; }
        return RandomStringUtils.randomAlphabetic(NumberUtils.toInt(length));
    }

    public String alphanumeric(String length) {
        if (isDigit("random.alphanumeric()", length)) { return ""; }
        return RandomStringUtils.randomAlphanumeric(NumberUtils.toInt(length));
    }

    public String any(String length) {
        if (isDigit("random.any()", length)) { return ""; }
        return RandomStringUtils.randomAscii(NumberUtils.toInt(length));
    }

    public String characters(String characters, String length) {
        if (isDigit("random.characters()", length)) { return ""; }
        return RandomStringUtils.random(NumberUtils.toInt(length), characters.toCharArray());
    }

    public String numeric(String from, String to) {
        if (isDigit("random.numeric()", from)) { return ""; }
        if (isDigit("random.numeric()", to)) { return ""; }
        return RandomUtils.nextInt(NumberUtils.toInt(from), NumberUtils.toInt(to)) + "";
    }

    protected void init() { }

    private boolean isDigit(String funcName, String length) {
        if (NumberUtils.isDigits(length)) { return false; }
        ConsoleUtils.error("Error at " + funcName + ": " + length + " is not a number");
        return true;
    }
}
