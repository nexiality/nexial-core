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

import java.util.Date;
import javax.validation.constraints.NotNull;

import static org.nexial.commons.utils.SocialDateText.SocialDate.*;

public final class SocialDateText {
    private static final String JUST_NOW = "just now";
    private static final int LESS_THAN_A_MIN = 30;
    private static final int ABOUT_A_MIN = 60 + 15;
    private static final int ABOUT_5_MIN = 4;
    // private static final long LESS_THAN_7_MIN = 7;
    private static final int ABOUT_10_MIN = 10;
    //private static final int ABOUT_15_MIN = 17;
    //private static final int ABOUT_HALF_AN_HOUR = 40;
    private static final int LESS_THAN_AN_HOUR = 60;
    private static final int LESS_THAN_A_DAY = 23;
    private static final int LESS_THAN_A_MONTH = 29;
    private static final int LESS_THAN_A_YEAR = 11;

    enum SocialDate {
        // < 3 months is considered as "about a year", 5-6 months means half a year
        YEAR("year", "month", 3, 4, 7),
        // < 5 days is considered as "about a month", 14-16 days means half a month
        MONTH("month", "day", 5, 13, 17),
        // < 2 days is considered as "about a week", 3-4 days means half a week
        WEEK("week", "day", 2, 2, 5),
        // < 3 hours is considered as "about a day", 11-13 hours means half a day
        DAY("day", "hour", 3, 10, 14),
        // < 5 minutes is considered as "about a hour", 27-34 minutes means half an hour
        HOUR("hour", "minute", 5, 26, 35),
        // < 10 seconds is considered as "about a minute", 25-34 seconds means half a minute
        MINUTE("minute", "second", 10, 26, 35);

        private String primarySuffix;
        private String secondarySuffix;
        private int minConsiderationAsWhole;
        private int minConsiderationAsHalf;
        private int maxConsiderationAsHalf;

        SocialDate(String primarySuffix,
                   String secondarySuffix,
                   int minConsiderationAsWhole,
                   int minConsiderationAsHalf,
                   int maxConsiderationAsHalf) {
            this.primarySuffix = primarySuffix;
            this.secondarySuffix = secondarySuffix;
            this.minConsiderationAsWhole = minConsiderationAsWhole;
            this.minConsiderationAsHalf = minConsiderationAsHalf;
            this.maxConsiderationAsHalf = maxConsiderationAsHalf;
        }
    }

    private SocialDateText() { }

    public static String since(Date fromDate, Date currentDate) {
        if (fromDate == null) { return JUST_NOW; }
        if (currentDate == null) { return since(fromDate, new Date(System.currentTimeMillis())); }

        long current = currentDate.getTime();
        long from = fromDate.getTime();
        return since(from, current);
    }

    public static String since(long from, long current) {
        long elapsedMills = current - from;

        // could be negative... this means fromDate is in the future
        if (elapsedMills == 0) { return JUST_NOW; }
        if (elapsedMills < 0) { return later(current, from); }

        return stringifyTimeElapsed(Math.abs(elapsedMills), " ago");
    }

    public static String later(Date laterDate, Date currentDate) {
        if (laterDate == null) { return JUST_NOW; }
        if (currentDate == null) { return since(laterDate, new Date(System.currentTimeMillis())); }

        long current = currentDate.getTime();
        long later = laterDate.getTime();
        return later(later, current);
    }

    public static String later(long later, long current) {
        long elapsedMills = later - current;

        // could be negative... this means fromDate is in the future
        if (elapsedMills == 0) { return JUST_NOW; }
        if (elapsedMills > 0) { return since(current, later); }

        return stringifyTimeElapsed(Math.abs(elapsedMills), " later");
    }

    private static String stringifyTimeElapsed(long elapsedMills, String timeUnit) {
        double elapsedSecond = (double) elapsedMills / 1000;
        if (elapsedSecond <= LESS_THAN_A_MIN) { return JUST_NOW; }
        if (between(elapsedSecond, LESS_THAN_A_MIN + 1, ABOUT_A_MIN)) { return "just a minute" + timeUnit; }

        double elapsedMinute = elapsedSecond / 60;
        if (elapsedMinute <= ABOUT_5_MIN) { return "a few minutes" + timeUnit; }
        if (elapsedMinute < ABOUT_10_MIN) { return "about " + (int) elapsedMinute + " minutes" + timeUnit; }

        if (elapsedMinute <= 1) { return (int) elapsedSecond + " seconds"; }

        double elapsedHour = elapsedMinute / 60;
        if (elapsedMinute <= LESS_THAN_AN_HOUR || elapsedHour < 1) {
            return getSocialDateTime(MINUTE, (int) elapsedSecond / 60, (int) Math.round(elapsedSecond % 60), timeUnit);
        }

        double elapsedDays = elapsedHour / 24;
        if (elapsedHour <= LESS_THAN_A_DAY || elapsedDays < 1) {
            return getSocialDateTime(HOUR, (int) elapsedMinute / 60, (int) Math.round(elapsedMinute % 60), timeUnit);
        }

        double elapsedMonth = elapsedDays / 30;
        if (elapsedDays <= LESS_THAN_A_MONTH || elapsedMonth < 1) {
            return getSocialDateTime(DAY, (int) elapsedHour / 24, (int) Math.round(elapsedHour % 24), timeUnit);
        }

        if (elapsedMonth <= LESS_THAN_A_YEAR) {
            return getSocialDateTime(MONTH, (int) (elapsedDays / 30), (int) Math.round(elapsedDays % 30), timeUnit);
        }

        return getSocialDateTime(YEAR, (int) (elapsedMonth / 12), (int) Math.round(elapsedMonth % 12), timeUnit);
    }

    @NotNull
    private static String getSocialDateTime(SocialDate dateType, int primary, int secondary, String timeUnit) {
        String text = primary + "";
        String text2 = " " + dateType.primarySuffix + (primary < 2 ? "" : "s");

        if (secondary < 1) { return text + text2 + timeUnit; }

        if (dateType != MINUTE) { text = "about " + text; }

        if (secondary < dateType.minConsiderationAsWhole) { return text + text2 + timeUnit; }

        if (secondary > dateType.minConsiderationAsHalf && secondary < dateType.maxConsiderationAsHalf) {
            return text + " and a half" + text2 + timeUnit;
        }

        return text + text2 + " and " + secondary + " " + dateType.secondarySuffix + (secondary > 1 ? "s" : "") +
               timeUnit;
    }

    private static boolean between(double number, double low, double high) { return number >= low && number <= high; }
}
