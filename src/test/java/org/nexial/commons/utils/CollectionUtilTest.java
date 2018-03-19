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

import java.util.*;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class CollectionUtilTest {

	@Before
	public void setUp() {

	}

	@Test
	public void testToList() {
		Set<String> fixture = new HashSet<>();
		fixture.add("a");
		fixture.add("b");
		fixture.add("c");
		List<String> list = CollectionUtil.toList(fixture);
		Assert.assertTrue(3 == list.size());
		Assert.assertTrue(list.contains("a"));
		Assert.assertTrue(list.contains("b"));
		Assert.assertTrue(list.contains("c"));
	}

	@Test
	public void testGenerifyList() {
		List fixture = new ArrayList();
		Assert.assertEquals("[]", CollectionUtil.generifyList(fixture, String.class).toString());

		fixture.add("a");
		fixture.add("b");
		fixture.add("c");
		Assert.assertEquals("[a, b, c]", CollectionUtil.generifyList(fixture, String.class).toString());

		fixture = new ArrayList();
		fixture.add(1);
		fixture.add(new Integer(14));
		fixture.add(99);
		Assert.assertEquals("[1, 14, 99]", CollectionUtil.generifyList(fixture, Integer.class).toString());

		try {
			fixture = new ArrayList();
			fixture.add(true);
			fixture.add(new Object());
			fixture.add("Testing");
			fixture.add(144.22);
			fixture.add(new String[]{"a", "b"});
			fixture.add(new JSONObject());
			CollectionUtil.generifyList(fixture, Object.class);
		} catch (Throwable e) {
			Assert.fail("unexpected error: " + e);
		}

		try {
			CollectionUtil.generifyList(fixture, String.class);
			Assert.fail("expected error but not thrown");
		} catch (Exception e) {
			// it's ok, expected..
		}
	}

	@Test
	public void testToString() {
		Assert.assertEquals("", CollectionUtil.toString((List)null, null));
		Assert.assertEquals("", CollectionUtil.toString((List)null, "blah"));
		Assert.assertEquals("", CollectionUtil.toString(new ArrayList<String>(), "blah"));

		List<String> subject = new ArrayList<>();
		subject.add("a");
		subject.add("b");
		subject.add("c");
		Assert.assertEquals("a-b-c", CollectionUtil.toString(subject, "-"));
		Assert.assertEquals("abc", CollectionUtil.toString(subject, null));

		List subject2 = new ArrayList();
		subject2.add("a");
		subject2.add(17);
		subject2.add(true);
		subject2.add(new HashMap());
		Assert.assertEquals("a<>17<>true<>{}", CollectionUtil.toString(subject2, "<>"));
	}

	@Test
	public void testGetOrDefault() {
		Assert.assertEquals(CollectionUtil.getOrDefault(null, 5, "Hello"), "Hello");
		Assert.assertEquals(CollectionUtil.getOrDefault(new ArrayList<>(), 5, "Hello"), "Hello");
		Assert.assertEquals(CollectionUtil.getOrDefault(Arrays.asList("a"), 5, "Hello"), "Hello");
		Assert.assertEquals(CollectionUtil.getOrDefault(Arrays.asList("a", "b", "c"), 5, "Hello"), "Hello");
		Assert.assertEquals(CollectionUtil.getOrDefault(Arrays.asList("a", "b", "c", "d", "e"), 5, "Hello"), "Hello");
		Assert.assertEquals(CollectionUtil.getOrDefault(Arrays.asList("a", "b", "c", "d", "e", "f"), 5, "Hello"), "f");
		Assert.assertEquals(CollectionUtil.getOrDefault(Arrays.asList("a", "b", "c", "d", "e", "f"), 0, "Hello"), "a");
		Assert.assertEquals(CollectionUtil.getOrDefault(Arrays.asList("a", "b", "c", "d", "e", "f"), -1, "Hello"),
		                    "Hello");
		Assert.assertEquals(CollectionUtil.getOrDefault(Arrays.asList("a", "b", "c", "d", null), 4, "Hello"), "Hello");
		Assert.assertEquals(CollectionUtil.getOrDefault(Arrays.asList("a", "b", "f", ""), 3, "Hello"), "");
		Assert.assertEquals(CollectionUtil.getOrDefault(Arrays.asList("a"), 2, null), null);

	}
}
