/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.model;

import org.junit.Assert;
import org.junit.Test;

public class IterationManagerTest {

	@Test
	public void testSimpleCases() {
		IterationManager subject = IterationManager.newInstance("");
		Assert.assertEquals(subject.getFirstIteration(), 1);
		Assert.assertEquals(subject.getLastIteration(), 1);
		Assert.assertEquals(subject.getLowestIteration(), 1);
		Assert.assertEquals(subject.getHighestIteration(), 1);
		Assert.assertEquals(subject.getIterationCount(), 1);
		Assert.assertFalse(subject.skip(0));
		for (int i = 1; i < 200; i++) { Assert.assertTrue(subject.skip(i)); }

		subject = IterationManager.newInstance("1");
		Assert.assertEquals(subject.getFirstIteration(), 1);
		Assert.assertEquals(subject.getLastIteration(), 1);
		Assert.assertEquals(subject.getLowestIteration(), 1);
		Assert.assertEquals(subject.getHighestIteration(), 1);
		Assert.assertEquals(subject.getIterationCount(), 1);
		Assert.assertFalse(subject.skip(0));
		for (int i = 1; i < 200; i++) { Assert.assertTrue(subject.skip(i)); }

		subject = IterationManager.newInstance("0");
		Assert.assertEquals(subject.getFirstIteration(), 1);
		Assert.assertEquals(subject.getLastIteration(), 1);
		Assert.assertEquals(subject.getLowestIteration(), 1);
		Assert.assertEquals(subject.getHighestIteration(), 1);
		Assert.assertEquals(subject.getIterationCount(), 1);
		Assert.assertFalse(subject.skip(0));
		for (int i = 1; i < 200; i++) { Assert.assertTrue(subject.skip(i)); }

		subject = IterationManager.newInstance("1,2,3,4,5");
		Assert.assertEquals(subject.getFirstIteration(), 1);
		Assert.assertEquals(subject.getLastIteration(), 5);
		Assert.assertEquals(subject.getLowestIteration(), 1);
		Assert.assertEquals(subject.getHighestIteration(), 5);
		Assert.assertEquals(subject.getIterationCount(), 5);
		Assert.assertFalse(subject.skip(0));
		Assert.assertFalse(subject.skip(1));
		Assert.assertFalse(subject.skip(2));
		Assert.assertFalse(subject.skip(3));
		Assert.assertFalse(subject.skip(4));
		for (int i = 5; i < 200; i++) { Assert.assertTrue(subject.skip(i)); }
	}

	@Test
	public void testComplexCases() {
		IterationManager subject = IterationManager.newInstance("1-5,17,99,6");
		Assert.assertEquals(subject.getFirstIteration(), 1);
		Assert.assertEquals(subject.getLastIteration(), 6);
		Assert.assertEquals(subject.getLowestIteration(), 1);
		Assert.assertEquals(subject.getHighestIteration(), 99);
		Assert.assertEquals(subject.getIterationCount(), 8);
		for (int i = 1; i < 200; i++) {
			if (i == 0 ||
			    i == 1 ||
			    i == 2 ||
			    i == 3 ||
			    i == 4 ||
			    i == 5 ||
			    i == 16 ||
			    i == 98) {
				Assert.assertFalse(subject.skip(i));
			} else {
				Assert.assertTrue(subject.skip(i));
			}
		}

		subject = IterationManager.newInstance("17,45,10-15,6-12");
		Assert.assertEquals(subject.getFirstIteration(), 17);
		Assert.assertEquals(subject.getLastIteration(), 12);
		Assert.assertEquals(subject.getLowestIteration(), 6);
		Assert.assertEquals(subject.getHighestIteration(), 45);
		Assert.assertEquals(subject.getIterationCount(), 15);
		for (int i = 1; i < 200; i++) {
			if (i == 5 ||
			    i == 6 ||
			    i == 7 ||
			    i == 8 ||
			    i == 9 ||
			    i == 10 ||
			    i == 11 ||
			    i == 12 ||
			    i == 13 ||
			    i == 14 ||
			    i == 16 ||
			    i == 44) {
				Assert.assertFalse(subject.skip(i));
			} else {
				Assert.assertTrue(subject.skip(i));
			}
		}

		subject = IterationManager.newInstance("1-5,1 - 5, 001  -      05.00");
		Assert.assertEquals(subject.getFirstIteration(), 1);
		Assert.assertEquals(subject.getLastIteration(), 5);
		Assert.assertEquals(subject.getLowestIteration(), 1);
		Assert.assertEquals(subject.getHighestIteration(), 5);
		Assert.assertEquals(subject.getIterationCount(), 10);
		for (int i = 1; i < 200; i++) {
			if (i == 0 ||
			    i == 1 ||
			    i == 2 ||
			    i == 3 ||
			    i == 4) {
				Assert.assertFalse(subject.skip(i));
			} else {
				Assert.assertTrue(subject.skip(i));
			}
		}

	}

	@Test
	public void testParsing() {
		IterationManager subject = IterationManager.newInstance("1-5,17,99,6");
		Assert.assertEquals(subject.getFirstIteration(), 1);
		Assert.assertEquals(subject.getLastIteration(), 6);
		Assert.assertEquals(subject.getIterationCount(), 8);
		Assert.assertEquals("[1,2,3,4,5,17,99,6] total 8", subject.toString());

		subject = IterationManager.newInstance("1,2,1,2,1");
		Assert.assertEquals(subject.getFirstIteration(), 1);
		Assert.assertEquals(subject.getLastIteration(), 1);
		Assert.assertEquals(subject.getLowestIteration(), 1);
		Assert.assertEquals(subject.getHighestIteration(), 2);
		Assert.assertEquals(subject.getIterationCount(), 5);
		Assert.assertEquals("[1,2,1,2,1] total 5", subject.toString());

		subject = IterationManager.newInstance("1,1,1,2-5,5-3");
		Assert.assertEquals(subject.getFirstIteration(), 1);
		Assert.assertEquals(subject.getLastIteration(), 3);
		Assert.assertEquals(subject.getLowestIteration(), 1);
		Assert.assertEquals(subject.getHighestIteration(), 5);
		Assert.assertEquals(subject.getIterationCount(), 10);
		Assert.assertEquals("[1,1,1,2,3,4,5,5,4,3] total 10", subject.toString());

		subject = IterationManager.newInstance("B-D,Z-AA,C-19");
		Assert.assertEquals(subject.getFirstIteration(), 1);
		Assert.assertEquals(subject.getLastIteration(), 19);
		Assert.assertEquals(subject.getLowestIteration(), 1);
		Assert.assertEquals(subject.getHighestIteration(), 26);
		Assert.assertEquals(subject.getIterationCount(), 23);
		Assert.assertEquals("[1,2,3,25,26,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19] total 23", subject.toString());
	}
}