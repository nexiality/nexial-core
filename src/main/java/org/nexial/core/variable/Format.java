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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.WordUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.utils.ConsoleUtils;

import static java.lang.Double.MIN_VALUE;
import static org.nexial.core.NexialConst.*;

/**
 * Cheatsheet here - http://docs.oracle.com/javase/6/docs/api/java/text/DecimalFormat.html
 */
public class Format {
    private static final NumberFormat FORMAT_INT = NumberFormat.getIntegerInstance();
    private static final NumberFormat FORMAT_PERCENT = NumberFormat.getPercentInstance();
    private static final NumberFormat FORMAT_CURRENCY = NumberFormat.getCurrencyInstance();

    public Format() { init(); }

    public String upper(String text) { return StringUtils.upperCase(text); }

    public String lower(String text) { return StringUtils.lowerCase(text); }

    public String titlecase(String text) { return WordUtils.capitalizeFully(text); }

    public String left(String text, String length) {
        if (!NumberUtils.isDigits(length)) {
            ConsoleUtils.error("Error at Format.left() - length " + length + " is not a number");
            return text;
        }

        return StringUtils.left(text, NumberUtils.toInt(length));
    }

    public String right(String text, String length) {
        if (!NumberUtils.isDigits(length)) {
            ConsoleUtils.error("Error at Format.right() - length " + length + " is not a number");
            return text;
        }

        return StringUtils.right(text, NumberUtils.toInt(length));
    }

    public String integer(String text) { return numberFormat(text, FORMAT_INT); }

    public String number(String text, String format) {
        String onlyNumbers = toNumeric(text);
        if (StringUtils.isBlank(format)) { return onlyNumbers; }

        double converted = NumberUtils.toDouble(onlyNumbers, MIN_VALUE);
        if (converted == MIN_VALUE) {
            ConsoleUtils.error("Format.number(): Unable to convert into number - " + text + "; TEXT UNCHANGED");
            return text;
        }

        return new DecimalFormat(format).format(converted);
    }

    public String percent(String text) { return numberFormat(text, FORMAT_PERCENT); }

    public String dollar(String text) { return numberFormat(text, FORMAT_CURRENCY); }

    /** format text into xxx-xxx-xxxx format */
    public String ssn(String text) {
        if (StringUtils.isBlank(text)) { return text; }
        //return text.replaceAll("(...)(...)(.*)", "$1-$2-$3");

        if (StringUtils.length(text) < 4) { return text; }
        text = StringUtils.substring(text, 0, 3) + "-" + StringUtils.substring(text, 3);

        if (StringUtils.length(text) < 7) { return text; }
        text = StringUtils.substring(text, 0, 6) + "-" + StringUtils.substring(text, 6);

        return text;
    }

    public String phone(String text) {
        if (StringUtils.isBlank(text)) { return text; }

        if (StringUtils.length(text) < 7) { return text; }
        if (StringUtils.length(text) == 7) { return text.replaceAll("(...)(.+)", "$1-$2"); }
        if (StringUtils.length(text) == 10) { return text.replaceAll("(...)(...)(.+)", "($1)$2-$3"); }
        if (StringUtils.length(text) == 11) { return text.replaceAll("(.)(...)(...)(.+)", "$1($2)$3-$4"); }

        String newPhone = TextUtils.insert(StringUtils.reverse(text), 4, "-");
        if (StringUtils.length(newPhone) >= 11) {
            newPhone = TextUtils.insert(newPhone, 8, ")");
            newPhone = TextUtils.insert(newPhone, 12, "(");
        } else {
            newPhone = TextUtils.insert(newPhone, 8, "-");
        }

        return StringUtils.reverse(newPhone);
    }

    public String strip(String text, String omit) {
        if (StringUtils.isEmpty(text)) { return text; }
        if (StringUtils.isEmpty(omit)) { return text; }
        return StringUtils.replaceEach(text, new String[]{omit}, new String[]{""});
    }

    /**
     * special characters:<ul>
     * <li>` (back tick)- 1 character</li>
     * <li>all other characters - formatting characters</li>
     * </ul>
     * <br/>
     * For example: {@code custom("1234567890ABCDE", "(```) ```-````")} would yield {@code "(123) 456-7890"}.  All
     * extra characters are thrown out
     */
    public String custom(String text, String format) {
        if (StringUtils.isBlank(format)) { return text; }
        if (StringUtils.isEmpty(text)) { return text; }

        int currentPos = 0;
        StringBuilder buffer = new StringBuilder();
        char[] template = format.toCharArray();
        for (Character c : template) {
            if (c != '`') {
                buffer.append(c);
            } else {
                buffer.append(StringUtils.substring(text, currentPos, 1 + currentPos));
                currentPos++;
            }
        }

        return buffer.toString();
    }

    public String mask(String text, String start, String end, String maskChar) {
        if (StringUtils.isBlank(text)) { return text; }
        if (!NumberUtils.isDigits(start)) {
            ConsoleUtils.error("Error at Format.mask() - start " + start + " is not a number");
            return text;
        }
        if (!NumberUtils.isDigits(end)) {
            ConsoleUtils.error("Error at Format.mask() - end " + end + " is not a number");
            return text;
        }

        int startNum = NumberUtils.toInt(start);
        int endNum = NumberUtils.toInt(end);
        if (startNum < 0) {
            ConsoleUtils.error("Error at Format.mask() - start " + start + " is less than zero");
            return text;
        }
        if (endNum < 0) {
            ConsoleUtils.error("Error at Format.mask() - end " + end + " is less than zero");
            return text;
        }
        if (endNum > text.length()) { endNum = text.length(); }
        if (startNum >= endNum) {
            ConsoleUtils.error("Error at Format.mask() - start " + start + " is greater or equal to end " + end);
            return text;
        }

        int maskLength = endNum - startNum;
        if (StringUtils.isEmpty(maskChar)) { maskChar = "#"; }
        return StringUtils.substring(text, 0, startNum) +
               StringUtils.repeat(maskChar.charAt(0), maskLength) +
               StringUtils.substring(text, endNum);
    }

    public String urlencode(String text) {
        try {
            return URLEncoder.encode(text, DEF_CHARSET);
        } catch (UnsupportedEncodingException e) {
            ConsoleUtils.error("Unable to url-encode " + text + ": " + e.getMessage());
            return text;
        }
    }

    public String urldecode(String text) {
        try {
            return URLDecoder.decode(text, DEF_CHARSET);
        } catch (UnsupportedEncodingException e) {
            ConsoleUtils.error("Unable to url-encode " + text + ": " + e.getMessage());
            return text;
        }
    }

    public String base64encode(String text) { return TextUtils.base64encoding(text); }

    public String base64decode(String text) { return TextUtils.base64decoding(text); }

    protected void init() {
        FORMAT_INT.setGroupingUsed(false);
        FORMAT_PERCENT.setMaximumFractionDigits(2);
    }

    protected String numberFormat(String text, NumberFormat formatter) {
        String onlyNumbers = toNumeric(text);

        double converted = NumberUtils.toDouble(onlyNumbers, MIN_VALUE);
        if (converted == MIN_VALUE) {
            ConsoleUtils.error(TOKEN_FUNCTION_START + "format" + TOKEN_FUNCTION_END +
                               ": Unable to convert into number - " + text + "; TEXT UNCHANGED");
            return text;
        }

        return formatter.format(converted);
    }

    protected static String toNumeric(String text) {
        // sanity check
        if (StringUtils.isBlank(text)) { return text; }

        // trim first to avoid false interpretation of negative signs
        String text1 = StringUtils.trim(text);

        // remove non-numeric chars, but include decimal points
        String onlyNumbers = RegexUtils.replace(text1, "([^0-9.]+)*", "");

        // add back the negative sign, if any
        if (StringUtils.startsWithAny(text1, "-")) { onlyNumbers = "-" + onlyNumbers; }

        return onlyNumbers;
    }

}
