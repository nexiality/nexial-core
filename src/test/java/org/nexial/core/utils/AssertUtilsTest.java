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

package org.nexial.core.utils;

import org.junit.Assert;
import org.junit.Test;

public class AssertUtilsTest {
	@Test
	public void requires() {
		try {
			AssertUtils.requires(false, null, (Object) null);
			Assert.fail("should have thrown exception");
		} catch (Exception e) {
			// it's ok
		}

		Assert.assertTrue(AssertUtils.requires(Boolean.TRUE, "This is true", "true", "TRUE", "YES"));
	}

	@Test
	public void requiresRowsAffected() {
		Assert.assertTrue(AssertUtils.requiresRowsAffected(5, 5));

		try {
			AssertUtils.requiresRowsAffected(5, 4);
			Assert.fail("should have thrown exception");
		} catch (Exception e) {
			// it's ok
		}
	}

	@Test
	public void requiresRowsAffected1() {
		Assert.assertTrue(AssertUtils.requiresRowsAffected(5, 3, 5));
		Assert.assertTrue(AssertUtils.requiresRowsAffected(5, 5, 5));
		Assert.assertTrue(AssertUtils.requiresRowsAffected(5, 5, 6));
		Assert.assertTrue(AssertUtils.requiresRowsAffected(5, 4, 6));

		try {
			Assert.assertTrue(AssertUtils.requiresRowsAffected(5, 2, 3));
			Assert.fail("should have thrown exception");
		} catch (Exception e) {
			// it's ok
		}

		try {
			Assert.assertTrue(AssertUtils.requiresRowsAffected(5, 3, 2));
			Assert.fail("should have thrown exception");
		} catch (Exception e) {
			// it's ok
		}
	}

}