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

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

// named as manual test since "sysdate" isn't active/defined by default
public class SysdateManualTest {
	Sysdate sysdate = new Sysdate();

	@Before
	public void init() {
		// EPTestUtils.setupCommonProps();

		System.setProperty("selenium.host", "localhost");
		System.setProperty("testsuite.startTs", new SimpleDateFormat("yyyyMMddHHmmssS").format(new Date()));

		ClassPathXmlApplicationContext springContext = new ClassPathXmlApplicationContext("classpath:/nexial.xml");
		sysdate = springContext.getBean("sysdate", Sysdate.class);
	}

	@Test
	public void testNow() {
		System.out.println("sysdate.now = " + sysdate.now("yyyy-MM-dd HH:mm:ss"));
		Assert.assertEquals("2018", sysdate.now("yyyy"));
	}

	@Test
	public void testYesterday() {
		Assert.assertEquals(DateFormatUtils.format(DateUtils.addDays(new Date(), -1), "yyyy-MM-dd"),
		                    sysdate.yesterday("yyyy-MM-dd"));
	}

	@Test
	public void testTomorrow() {
		Assert.assertEquals(DateFormatUtils.format(DateUtils.addDays(new Date(), 1), "yyyy-MM-dd"),
		                    sysdate.tomorrow("yyyy-MM-dd"));
	}

	@Test
	public void testFirstDOM() {
		Assert.assertEquals(DateFormatUtils.format(DateUtils.setDays(new Date(), 1), "dd"), sysdate.firstDOM("dd"));
	}

	@Test
	public void testLastDOM() {
		Assert.assertEquals(
			DateFormatUtils.format(
				// last day of this month
				DateUtils.addDays(
					// first day of next month
					DateUtils.addMonths(
						// first day of this month
						DateUtils.setDays(new Date(), 1),
						1),
					-1),
				"dd"),
			sysdate.lastDOM("dd"));
	}

	@Test
	public void testFirstDOW() {
		// manual update needed
		Assert.assertEquals("006", sysdate.firstDOW("ddd"));
	}

	@Test
	public void testLastDOW() {
		// manual update needed
		Assert.assertEquals("12", sysdate.lastDOW("dd"));
	}

	@Test
	public void testLastQtr() {
		Assert.assertEquals("2018-1", sysdate.lastQtr("yyyy-q"));
		Assert.assertEquals("2018-1", sysdate.lastQtr("yyyy-qqq"));
		Assert.assertEquals("1", sysdate.lastQtr("qqq"));
	}

	@Test
	public void testCurrentQtr() {
		Assert.assertEquals("2018-2", sysdate.currentQtr("yyyy-q"));
		Assert.assertEquals("2018-2", sysdate.currentQtr("yyyy-qqq"));
		Assert.assertEquals("2", sysdate.currentQtr("qqq"));
	}

	@Test
	public void testNextQtr() {
		Assert.assertEquals("2018-3", sysdate.nextQtr("yyyy-q"));
		Assert.assertEquals("2018-3", sysdate.nextQtr("yyyy-qqq"));
		Assert.assertEquals("3", sysdate.nextQtr("qqq"));
	}

	@Test
	public void testLastQtr2() {
		Sysdate sysdate2 = new Sysdate() {
			@Override
			protected void init() {
				currentYear = 2010;
				currentQtr = 1;
				derivePrevNextQtr();
			}
		};
		sysdate2.init();

		Assert.assertEquals("2009-4", sysdate2.lastQtr("yyyy-q"));
		Assert.assertEquals("2009-4", sysdate2.lastQtr("yyyy-qqq"));
		Assert.assertEquals("4", sysdate2.lastQtr("qqq"));
	}

	@Test
	public void testNextQtr2() {
		Sysdate sysdate2 = new Sysdate() {
			@Override
			protected void init() {
				currentYear = 2009;
				currentQtr = 4;
				derivePrevNextQtr();
			}
		};
		sysdate2.init();

		Assert.assertEquals("2010-1", sysdate2.nextQtr("yyyy-q"));
		Assert.assertEquals("2010-1", sysdate2.nextQtr("yyyy-qqq"));
		Assert.assertEquals("1", sysdate2.nextQtr("qqq"));
	}
}
