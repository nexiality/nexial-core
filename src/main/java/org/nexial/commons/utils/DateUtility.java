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

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Calendar.*;
import static org.nexial.core.NexialConst.DF_TIMESTAMP;

public final class DateUtility {
    private static final Logger LOGGER = LoggerFactory.getLogger(DateUtility.class);

    private static final String FORMAT_LONG = "MM/dd/yyyy HH:mm:ss";
    private static final String FORMAT_LONG1 = "MM/dd/yyyy hh:mm:ss a";
    private static final String FORMAT_LOG = "yyyy-MM-dd HH:mm:ss,SSS";
    private static final String FORMAT_SHORT = "MM/dd/yyyy";
    private static final String FORMAT_TIME = "HH:mm:ss";
    private static final String FORMAT_TIME_WITH_MS = "HH:mm:ss.SSS";
    private static final String FORMAT_DB2_DATE = "yyyy-MM-dd";
    private static final DateFormat DATE_FORMAT_LONG = new SimpleDateFormat(FORMAT_LONG);
    private static final DateFormat DATE_FORMAT_LONG1 = new SimpleDateFormat(FORMAT_LONG1);
    private static final DateFormat DATE_FORMAT_LOG = new SimpleDateFormat(FORMAT_LOG);
    private static final DateFormat DATE_FORMAT_SHORT = new SimpleDateFormat(FORMAT_SHORT);
    private static final DateFormat DATE_FORMAT_TIME = new SimpleDateFormat(FORMAT_TIME);
    private static final DateFormat DATE_FORMAT_TIME_WITH_MS = new SimpleDateFormat(FORMAT_TIME_WITH_MS);
    private static final DateFormat DATE_FORMAT_DB2_DATE = new SimpleDateFormat(FORMAT_DB2_DATE);

    /**
     * This method formats date given as long in MM/DD/YYYY HH:mm:ss AM/PM format
     *
     * @param timestampMillis <b>long<b> - date as long
     * @return <b>String<b> - formatted string
     */
    public static String format(long timestampMillis) { return DATE_FORMAT_LONG1.format(new Date(timestampMillis)); }

    /**
     * This method formats date, given as long, in the format requested
     *
     * @param timeMillis <b>long<b> - date as long
     * @param pattern    <b>String<b> - date pattern
     * @return <b>String<b> - formatted string
     */
    public static String format(long timeMillis, String pattern) {
        try {
            DateFormat df = new SimpleDateFormat(pattern);
            return df.format(new Date(timeMillis));
        } catch (Exception e) {
            LOGGER.warn("Date [" + timeMillis + "] could not be formatted in [" + pattern + "], returning blank", e);
            return "";
        }
    }

    public static String format(java.sql.Date date, String pattern) {
        if (date == null) { return ""; }
        return format(date.getTime(), pattern);
    }

    public static String format(Timestamp timestamp, String pattern) {
        if (timestamp == null) { return ""; }
        return format(timestamp.getTime(), pattern);
    }

    /**
     * This method converts date, given as string, from one format to the other.
     * <p/>
     * <b>NOTE: according to JDK documentation: Y2K conversion is based on running '80/20' year partitioning.</b>
     *
     * @param date        <b>String<b> - date to be formatted
     * @param patternFrom <b>String<b> - date pattern of the input string
     * @param patternTo   <b>String<b> - date pattern requested
     * @return <b>String<b> - formatted date as string
     */
    public static String formatTo(String date, String patternFrom, String patternTo) {
        if (StringUtils.isBlank(date)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Date [" + date + "] could not be formatted from [" + patternFrom + "] to [" +
                             patternTo + "], because its a blank string. Returning orignial string");
            }
            return date;
        }

        try {
            DateFormat dfFrom = new SimpleDateFormat(patternFrom);
            DateFormat dfTo = new SimpleDateFormat(patternTo);
            return dfTo.format(dfFrom.parse(date));
        } catch (Exception e) {
            LOGGER.warn("Date [" + date + "] could not be formatted from [" + patternFrom + "] to [" + patternTo +
                        "], returning orignal string: " + e.getMessage());
            return date;
        }
    }

    public static String formatCurrentDateTime(String patternFrom, String patternTo) {
        return formatTo(DateUtility.getCurrentDateTime(), patternFrom, patternTo);
    }

    /** Formats a date for ccmmdd to mm/dd/yyyy */
    public static String convertYYMMDDtoMMDDYYYY(String date) {
        date = StringUtils.trim(date);
        if (StringUtils.length(date) >= 6) {
            String sYear = date.substring(0, 2);
            String sMonth = date.substring(2, 4);
            String sDay = date.substring(4);

            if (sYear.compareTo("85") < 0) {
                sYear = "20" + sYear;
            } else {
                sYear = "19" + sYear;
            }
            return sMonth + "/" + sDay + "/" + sYear;
        }

        return "";
    }

    public static String formatLongDate(long timestamp) { return DATE_FORMAT_LONG.format(new Date(timestamp)); }

    public static String formatStopWatchTime(long timestamp) {
        // format just the time values
        return DATE_FORMAT_TIME_WITH_MS.format(addToMidnight(timestamp));
    }

    public static String formatTime(long timestamp) {
        // format just the time values
        return DATE_FORMAT_TIME.format(addToMidnight(timestamp));
    }

    public static Date parseLongDate(String date) throws ParseException { return DATE_FORMAT_LONG.parse(date); }

    public static Date parseShortDate(String date) throws ParseException { return DATE_FORMAT_SHORT.parse(date); }

    public static Date parseDB2Date(String date) throws ParseException { return DATE_FORMAT_DB2_DATE.parse(date); }

    public static Date parseTime(String date) throws ParseException { return DATE_FORMAT_TIME.parse(date); }

    public static String toDB2Date(Date date) { return DATE_FORMAT_DB2_DATE.format(date); }

    public static String getCurrentDateTime() { return DATE_FORMAT_LONG1.format(new Date()); }

    public static String getCurrentTimestampForLogging() { return DATE_FORMAT_LOG.format(new Date()); }

    public static long formatTo(String date, String format) {
        if (StringUtils.isBlank(date)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Date [" + date + "] could not be formatted as '" + format + "'");
            }
            return -1;
        }

        try {
            DateFormat df = new SimpleDateFormat(format);
            Date d = df.parse(date);
            return d.getTime();
        } catch (Exception e) {
            LOGGER.warn("Date [" + date + "] could not be formatted as '" + format + "': " + e.getMessage());
            return -1;
        }

    }

    public static String createTimestampString(Long timestamp) {
        return DF_TIMESTAMP.format(timestamp == null ? new Date() : new Date(timestamp));
    }

    private static Date addToMidnight(long timestamp) {
        // get "right now" and remove reset all time values to zero
        Calendar rightNow = getInstance();
        rightNow.set(HOUR_OF_DAY, 0);
        rightNow.set(MINUTE, 0);
        rightNow.set(SECOND, 0);
        rightNow.set(MILLISECOND, 0);
        // now add the timestamp parameter so that it'll be 00:00:00 + timestamp
        return new Date(rightNow.getTimeInMillis() + timestamp);
    }
}
