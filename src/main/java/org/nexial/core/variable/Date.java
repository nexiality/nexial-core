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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.nexial.core.utils.ConsoleUtils;

import static java.util.Calendar.*;
import static org.nexial.core.NexialConst.EPOCH;
import static org.nexial.core.NexialConst.ONEYEAR;

public class Date {
    public static final String STD_DATE_FORMAT = "MM/dd/yyyy HH:mm:ss";
    public static final String STD_JUST_DATE_FORMAT = "MM/dd/yyyy";

    private abstract class DateTransform {
        abstract Calendar transform(Calendar c);
    }

    public String stdFormat(String date, String fromFormat) { return format(date, fromFormat, STD_DATE_FORMAT); }

    public String format(String date, String fromFormat, String toFormat) {
        try {

            java.util.Date fromDate = StringUtils.equals(fromFormat, EPOCH) ?
                                      new java.util.Date(NumberUtils.toLong(date)) :
                                      new SimpleDateFormat(fromFormat).parse(date);

            if (StringUtils.equals(toFormat, EPOCH)) { return fromDate.getTime() + ""; }
            if (!StringUtils.equals(fromFormat, EPOCH)) { return new SimpleDateFormat(toFormat).format(fromDate); }

            if (StringUtils.containsAny(toFormat, "GYyML") && fromDate.getTime() >= ONEYEAR) {
                return new SimpleDateFormat(toFormat).format(fromDate);
            }

            // epoch to another time or "day" format
            // make sure we don't have "bad" hours
            toFormat = StringUtils.replace(toFormat, "h", "H");
            return DurationFormatUtils.formatDuration(fromDate.getTime(), toFormat);

        } catch (ParseException e) {
            ConsoleUtils.error("Unable to format date '" + date + "' from '" + fromFormat + "' to '" + toFormat +
                               "': " + e.getMessage());
            return date;
        }
    }

    public String addYear(String date, String years) { return modifyDate(date, YEAR, NumberUtils.toInt(years)); }

    public String addYear(String date, int years) { return modifyDate(date, YEAR, years); }

    public String addMonth(String date, String months) { return modifyDate(date, MONTH, NumberUtils.toInt(months)); }

    public String addMonth(String date, int months) { return modifyDate(date, MONTH, months); }

    public String addDay(String date, String days) { return modifyDate(date, DAY_OF_MONTH, NumberUtils.toInt(days)); }

    public String addDay(String date, int days) { return modifyDate(date, DAY_OF_MONTH, days); }

    public String addHour(String date, String hours) { return modifyDate(date, HOUR_OF_DAY, NumberUtils.toInt(hours)); }

    public String addHour(String date, int hours) { return modifyDate(date, HOUR_OF_DAY, hours); }

    public String addMinute(String date, String minutes) {
        return modifyDate(date, MINUTE, NumberUtils.toInt(minutes));
    }

    public String addMinute(String date, int minutes) { return modifyDate(date, MINUTE, minutes); }

    public String addSecond(String date, String seconds) {
        return modifyDate(date, SECOND, NumberUtils.toInt(seconds));
    }

    public String addSecond(String date, int seconds) { return modifyDate(date, SECOND, seconds); }

    public String setYear(String date, String years) { return setDate(date, YEAR, NumberUtils.toInt(years)); }

    public String setYear(String date, int years) { return setDate(date, YEAR, years); }

    public String setMonth(String date, String months) { return setDate(date, MONTH, NumberUtils.toInt(months) - 1); }

    public String setMonth(String date, int months) { return setDate(date, MONTH, months - 1); }

    public String setDay(String date, String days) { return setDate(date, DAY_OF_MONTH, NumberUtils.toInt(days)); }

    public String setDay(String date, int days) { return setDate(date, DAY_OF_MONTH, days); }

    public String setDOW(String date, String days) { return setDate(date, DAY_OF_WEEK, NumberUtils.toInt(days)); }

    public String setDOW(String date, int days) { return setDate(date, DAY_OF_WEEK, days); }

    public String setHour(String date, String hours) { return setDate(date, HOUR_OF_DAY, NumberUtils.toInt(hours)); }

    public String setHour(String date, int hours) { return setDate(date, HOUR_OF_DAY, hours); }

    public String setMinute(String date, String minutes) { return setDate(date, MINUTE, NumberUtils.toInt(minutes)); }

    public String setMinute(String date, int minutes) { return setDate(date, MINUTE, minutes); }

    public String setSecond(String date, String seconds) { return setDate(date, SECOND, NumberUtils.toInt(seconds)); }

    public String setSecond(String date, int seconds) { return setDate(date, SECOND, seconds); }

    protected void init() { }

    private String setDate(String date, final int dateField, final int variant) {
        return transformDate(date, new DateTransform() {
            Calendar transform(Calendar c) {
                c.set(dateField, variant);
                return c;
            }
        });
    }

    private String modifyDate(String date, final int dateField, final int variant) {
        return transformDate(date, new DateTransform() {
            Calendar transform(Calendar c) {
                c.add(dateField, variant);
                return c;
            }
        });
    }

    private String transformDate(String date, DateTransform dateTransform) {
        if (StringUtils.isBlank(date)) { return date; }

        String datePattern = StringUtils.length(date) < 11 ? STD_JUST_DATE_FORMAT : STD_DATE_FORMAT;

        try {
            DateFormat dateFormat = new SimpleDateFormat(datePattern);
            Calendar c = getInstance();
            c.setTime(dateFormat.parse(date));
            c = dateTransform.transform(c);
            return dateFormat.format(c.getTime());
        } catch (ParseException e) {
            ConsoleUtils.error("Unable to parse date '" + date + "' via format '" + datePattern + "'");
            return date;
        }
    }
}