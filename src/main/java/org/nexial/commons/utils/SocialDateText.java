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

public final class SocialDateText {
	private static final String JUST_NOW = "just now";
	private static final int LESS_THAN_A_MIN = 30;
	private static final int ABOUT_A_MIN = 60 + 15;
	private static final int ABOUT_5_MIN = 4;
	private static final long LESS_THAN_7_MIN = 7;
	private static final int ABOUT_10_MIN = 10;
	//private static final int ABOUT_15_MIN = 17;
	//private static final int ABOUT_HALF_AN_HOUR = 40;
	private static final int AN_HOUR = 60;
	private static final int LESS_THAN_A_DAY = 23;
	private static final int LESS_THAN_A_MONTH = 29;
	private static final int LESS_THAN_A_YEAR = 11;

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

		return stringifyTimeElapsed(elapsedMills, " ago");
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

		return stringifyTimeElapsed(elapsedMills, " later");
	}

	private static String stringifyTimeElapsed(long elapsedMills, String timeUnit) {
		long elapsedSecond = elapsedMills / 1000;
		if (elapsedSecond <= LESS_THAN_A_MIN) { return JUST_NOW; }
		if (between(elapsedSecond, LESS_THAN_A_MIN + 1, ABOUT_A_MIN)) { return "just a minute" + timeUnit; }

		long elapsedMinute = elapsedSecond / 60;
		if (elapsedMinute <= ABOUT_5_MIN) { return "a few minutes" + timeUnit; }
		if (between(elapsedMinute, ABOUT_5_MIN, LESS_THAN_7_MIN)) { return "about 5 minutes" + timeUnit; }
		if (between(elapsedMinute, LESS_THAN_7_MIN + 1, ABOUT_10_MIN)) { return "about 10 minutes" + timeUnit; }
		if (between(elapsedMinute, ABOUT_10_MIN + 1, AN_HOUR)) { return elapsedMinute + " minutes" + timeUnit; }
		//if (between(elapsedMinute, ABOUT_15_MIN + 1, ABOUT_HALF_AN_HOUR)) { return "about half an hour" + timeUnit; }
		//if (elapsedMinute <= ABOUT_HALF_AN_HOUR) { return "about half an hour" + timeUnit; }
		//if (elapsedMinute <= AN_HOUR) { return "about an hour" + timeUnit; }

		double elapsedHour = (double) elapsedMinute / 60;
		if (between(elapsedHour, 0.9, LESS_THAN_A_DAY)) {
			int minutes = (int) (elapsedHour * 100 % 100 / 100 * 60);
			if (between(minutes, 0, 16)) {
				return (int) elapsedHour + " hour" + (elapsedHour < 2 ? "" : "s") + timeUnit;
			}
			if (between(minutes, 17, 44)) { return "about " + (int) elapsedHour + ".5 hours" + timeUnit; }
			return "about " + ((int) elapsedHour + 1) + " hours" + timeUnit;
		}

		double elapsedDay = elapsedHour / 24;
		if (between(elapsedDay, 0.9, 1.3)) { return "1 day" + timeUnit; }
		if (between(elapsedDay, 1, LESS_THAN_A_MONTH)) {
			int hours = (int) (elapsedDay * 100 % 100 / 100 * 24);
			if (between(hours, 0, 16)) { return (int) elapsedDay + " days" + timeUnit; }
			if (between(hours, 17, 35)) { return "about " + (int) elapsedDay + ".5 days" + timeUnit; }
			return "about " + ((int) elapsedDay + 1) + " days" + timeUnit;
		}

		double elapsedMonth = elapsedDay / 30;
		if (between(elapsedMonth, 0.8, 1.2)) { return "1 month" + timeUnit; }
		if (between(elapsedMonth, 1, LESS_THAN_A_YEAR)) {
			int days = (int) (elapsedMonth * 100 % 100 / 100 * 30);
			if (between(days, 0, 16)) { return (int) elapsedMonth + " months" + timeUnit; }
			if (between(days, 17, 35)) { return "about " + (int) elapsedMonth + ".5 months" + timeUnit; }
			return "about " + ((int) elapsedMonth + 1) + " months" + timeUnit;
		}

		double elapsedYear = elapsedMonth / 12;
		if (between(elapsedYear, 0.875, 1.125)) { return "1 year" + timeUnit; }

		int months = (int) (elapsedYear * 100 % 100 / 100 * 12);
		if (between(months, 0, 16)) { return (int) elapsedYear + " years" + timeUnit; }
		if (between(months, 17, 35)) { return "about " + (int) elapsedYear + ".5 years" + timeUnit; }
		return elapsedYear + " years" + timeUnit;
	}

	private static boolean between(double number, double low, double high) { return number >= low && number <= high; }
}
