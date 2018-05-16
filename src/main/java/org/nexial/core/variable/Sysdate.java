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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.nexial.commons.utils.RegexUtils;

import static java.util.Calendar.DATE;
import static java.util.Calendar.DAY_OF_WEEK;
import static org.nexial.core.NexialConst.EPOCH;
import static org.nexial.core.NexialConst.ONEDAY;

/**
 * <b>Legacy code - further use not advisable.</b>
 * <p>
 * This class is only meaningful where the "system" date is to be determined by database (via SQL).  If such is the
 * requirement, then probably subclassing this class might be a good idea to ensure proper handling of DB-based dates.
 */
public class Sysdate {
    protected int previousYear;
    protected int previousQtr;
    protected int currentYear;
    protected int currentQtr;
    protected int nextYear;
    protected int nextQtr;

    public String lastQtr(String format) { return formatYearQuarter(format, previousYear + "", previousQtr + ""); }

    public String currentQtr(String format) { return formatYearQuarter(format, currentYear + "", currentQtr + ""); }

    public String nextQtr(String format) { return formatYearQuarter(format, nextYear + "", nextQtr + ""); }

    public String today(String format) { return now(format); }

    public String now(String format) { return format(System.currentTimeMillis(), format); }

    public String yesterday(String format) {
        return format(System.currentTimeMillis() - ONEDAY, format);
    }

    public String tomorrow(String format) {
        return format(System.currentTimeMillis() + ONEDAY, format);
    }

    public String firstDOM(String format) { return format(DateUtils.setDays(new Date(), 1), format); }

    public String lastDOM(String format) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(DATE, calendar.getActualMaximum(DATE));
        return format(calendar.getTime(), format);
    }

    public String firstDOW(String format) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(DAY_OF_WEEK, calendar.getActualMinimum(DAY_OF_WEEK));
        return format(calendar.getTime(), format);
    }

    public String lastDOW(String format) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(DAY_OF_WEEK, calendar.getActualMaximum(DAY_OF_WEEK));
        return format(calendar.getTime(), format);
    }

    protected void init() {
        DateFormat df = new SimpleDateFormat("yyyy-MM");
        String[] dateParts = StringUtils.split(df.format(new Date()), "-");
        currentYear = NumberUtils.toInt(dateParts[0]);
        currentQtr = (NumberUtils.toInt(dateParts[1]) - 1) / 3 + 1;
        derivePrevNextQtr();
    }

    protected static String format(long timestamp, String format) {
        if (StringUtils.equals(format, EPOCH)) { return timestamp + ""; }
        return DateFormatUtils.format(timestamp, format);
    }

    protected static String format(Date datetime, String format) {
        if (StringUtils.equals(format, EPOCH)) { return datetime.getTime() + ""; }
        return DateFormatUtils.format(datetime, format);
    }

    protected void derivePrevNextQtr() {
        if (currentQtr == 1) {
            previousQtr = 4;
            previousYear = currentYear - 1;
            nextQtr = currentQtr + 1;
            nextYear = currentYear;
            return;
        }

        if (currentQtr == 4) {
            previousQtr = currentQtr - 1;
            previousYear = currentYear;
            nextQtr = 1;
            nextYear = currentYear + 1;
            return;
        }

        previousQtr = currentQtr - 1;
        previousYear = currentYear;
        nextQtr = currentQtr + 1;
        nextYear = currentYear;
    }

    private static String formatYearQuarter(String format, String yr, String qtr) {
        if (StringUtils.isBlank(format)) { return ""; }

        String data = RegexUtils.replace(format, "[q]+", qtr);
        data = RegexUtils.replace(data, "yyyy", yr);
        data = RegexUtils.replace(data, "yy", StringUtils.substring(yr, 2));
        return data;
    }
}
