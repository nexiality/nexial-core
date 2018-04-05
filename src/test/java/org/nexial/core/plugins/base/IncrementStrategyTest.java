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

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

public class IncrementStrategyTest {

	@Test
	public void testIncrement() {
		IncrementStrategy strategy = IncrementStrategy.UPPER;

		// single char increment
		Assert.assertEquals(strategy.increment("A", 1, 0), "A");
		Assert.assertEquals(strategy.increment("A", 1, 1), "B");
		Assert.assertEquals(strategy.increment("A", 1, 2), "C");
		Assert.assertEquals(strategy.increment("A", 1, 26), "AA");

		// double char increment
		Assert.assertEquals(strategy.increment("A", 1, 27), "AB");
		Assert.assertEquals(strategy.increment("A", 1, 30), "AE");
		Assert.assertEquals(strategy.increment("A", 1, 26 * 2), "BA");
		Assert.assertEquals(strategy.increment("A", 1, 26 * 3), "CA");
		Assert.assertEquals(strategy.increment("A", 1, 26 * 10), "JA");
		Assert.assertEquals(strategy.increment("A", 1, 26 * 26), "ZA");

		// triple char increment
		Assert.assertEquals(strategy.increment("A", 1, 26 * 27), "AAA");
		Assert.assertEquals(strategy.increment("A", 1, 26 * 27 + 2), "AAC");
		Assert.assertEquals(strategy.increment("A", 1, 26 * 27 + 27), "ABB");
		Assert.assertEquals(strategy.increment("A", 1, 26 * 27 + 29), "ABD");
		Assert.assertEquals(strategy.increment("A", 1, 26 * 27 + 27 + 26), "ACB");
		Assert.assertEquals(strategy.increment("A", 1, 26 * 27 + 27 + 27), "ACC");
		Assert.assertEquals(strategy.increment("A", 1, 26 * 27 + 26 * 3), "ADA");
		Assert.assertEquals(strategy.increment("A", 1, 26 * 27 + 26 * 4), "AEA");
		Assert.assertEquals(strategy.increment("A", 1, 26 * 26 + 26 * 25), "AYA");
		Assert.assertEquals(strategy.increment("A", 1, 26 * 26 + 26 * 26 - 1), "AYZ");
		Assert.assertEquals(strategy.increment("A", 1, 26 * 26 + 26 * 26), "AZA");
		Assert.assertEquals(strategy.increment("A", 1, 26 * 26 + 26 * 26 + 26), "BAA");
	}

	@Test
	public void testIncrementSpotCheck() {
		IncrementStrategy strategy = IncrementStrategy.UPPER;

		Assert.assertEquals(strategy.increment("A", 1, 0), "A");
		Assert.assertEquals(strategy.increment("A", 1, 1), "B");
		Assert.assertEquals(strategy.increment("A", 1, 25), "Z");
		Assert.assertEquals(strategy.increment("A", 1, 26), "AA");
		Assert.assertEquals(strategy.increment("A", 1, 27), "AB");
		Assert.assertEquals(strategy.increment("A", 1, 51), "AZ");
		Assert.assertEquals(strategy.increment("A", 1, 52), "BA");
		Assert.assertEquals(strategy.increment("A", 1, 53), "BB");
		Assert.assertEquals(strategy.increment("A", 1, 77), "BZ");
		Assert.assertEquals(strategy.increment("A", 1, 78), "CA");
		Assert.assertEquals(strategy.increment("A", 1, 427), "PL");
		Assert.assertEquals(strategy.increment("A", 1, 700), "ZY");
		Assert.assertEquals(strategy.increment("A", 1, 701), "ZZ");
		Assert.assertEquals(strategy.increment("A", 1, 702), "AAA");
		Assert.assertEquals(strategy.increment("A", 1, 737), "ABJ");
		Assert.assertEquals(strategy.increment("A", 1, 913), "AID");
		Assert.assertEquals(strategy.increment("A", 1, 1000), "ALM");
		Assert.assertEquals(strategy.increment("A", 1, 1376), "AZY");
		Assert.assertEquals(strategy.increment("A", 1, 1377), "AZZ");
		Assert.assertEquals(strategy.increment("A", 1, 1378), "BAA");
		Assert.assertEquals(strategy.increment("A", 1, 1499), "BER");

		strategy = IncrementStrategy.LOWER;
		Assert.assertEquals(strategy.increment("A", 1, 0), "A");
		Assert.assertEquals(strategy.increment("A", 1, 1), "b");
		Assert.assertEquals(strategy.increment("A", 1, 25), "z");
		Assert.assertEquals(strategy.increment("A", 1, 26), "aa");
		Assert.assertEquals(strategy.increment("A", 1, 27), "ab");
		Assert.assertEquals(strategy.increment("A", 1, 51), "az");
		Assert.assertEquals(strategy.increment("A", 1, 52), "ba");
		Assert.assertEquals(strategy.increment("A", 1, 1486), "bee");

		strategy = IncrementStrategy.ALPHANUM;
		Assert.assertEquals(strategy.increment("A", 1, 0), "A");
		Assert.assertEquals(strategy.increment("A", 1, 1), "B");
		Assert.assertEquals(strategy.increment("A", 1, 2), "C");
		Assert.assertEquals(strategy.increment("A", 1, 10), "K");
		Assert.assertEquals(strategy.increment("A", 1, 34), "i");
		Assert.assertEquals(strategy.increment("A", 1, 35), "j");
		Assert.assertEquals(strategy.increment("A", 1, 36), "k");
		Assert.assertEquals(strategy.increment("A", 1, 61), "09");
		Assert.assertEquals(strategy.increment("A", 1, 62), "0A");
		Assert.assertEquals(strategy.increment("A", 1, 63), "0B");
		Assert.assertEquals(strategy.increment("A", 1, 72), "0K");
		Assert.assertEquals(strategy.increment("A", 1, 97), "0j");
		Assert.assertEquals(strategy.increment("A", 1, 98), "0k");
		Assert.assertEquals(strategy.increment("A", 1, 123), "19");
		Assert.assertEquals(strategy.increment("A", 1, 124), "1A");
		Assert.assertEquals(strategy.increment("A", 1, 125), "1B");
		Assert.assertEquals(strategy.increment("A", 1, 257), "3J");
		Assert.assertEquals(strategy.increment("A", 1, 258), "3K");
		Assert.assertEquals(strategy.increment("A", 1, 283), "3j");
		Assert.assertEquals(strategy.increment("A", 1, 284), "3k");
		Assert.assertEquals(strategy.increment("A", 1, 309), "49");
		Assert.assertEquals(strategy.increment("A", 1, 310), "4A");
		Assert.assertEquals(strategy.increment("A", 1, 1426), "MA");
		Assert.assertEquals(strategy.increment("A", 1, 1496), "NI");
	}

	@Test
	public void testIncrement2() {
		IncrementStrategy strategy = IncrementStrategy.UPPER;
		for (int i = 0; i < 1500; i++) {
			String a = StringUtils.rightPad(strategy.increment("A", 1, i), 5);
			System.out.println(StringUtils.leftPad(i + "", 5) + " " + a);
		}

		//IncrementStrategy
		strategy = IncrementStrategy.LOWER;
		for (int i = 0; i < 1500; i++) {
			String a = StringUtils.rightPad(strategy.increment("A", 1, i), 5);
			System.out.println(StringUtils.leftPad(i + "", 5) + " " + a);
		}

		//IncrementStrategy
		strategy = IncrementStrategy.ALPHANUM;
		for (int i = 0; i < 1500; i++) {
			String a = StringUtils.rightPad(strategy.increment("A", 1, i), 5);
			System.out.println(StringUtils.leftPad(i + "", 5) + " " + a);
		}

		//IncrementStrategy
		strategy = IncrementStrategy.LOWER;
		for (int i = 18279; i < 18280; i++) {
			String a = StringUtils.rightPad(strategy.increment("P102", 1, i), 5);
			System.out.println(StringUtils.leftPad(i + "", 5) + " " + a);
		}

		for (int i = 35852; i < 35855; i++) {
			String a = StringUtils.rightPad(strategy.increment("p102", 1, i), 5);
			System.out.println(StringUtils.leftPad(i + "", 5) + " " + a);
		}

	}
}
