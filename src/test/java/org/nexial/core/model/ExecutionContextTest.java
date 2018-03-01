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

package org.nexial.core.model;

import java.util.*;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.nexial.core.variable.Date;
import org.nexial.core.variable.Sysdate;

import static org.nexial.core.NexialConst.Data.TEXT_DELIM;

public class ExecutionContextTest {
	@Before
	public void setUp() { }

	@After
	public void tearDown() { }

	@Test
	public void replaceTokens() {
		ExecutionContext subject = new MockExecutionContext();
		subject.setData(TEXT_DELIM, ",");
		subject.setData("a", new String[]{"Hello", "World", "Johnny boy"});

		Assert.assertEquals("Hello", subject.replaceTokens("${a}[0]"));
		Assert.assertEquals("World", subject.replaceTokens("${a}[1]"));
		Assert.assertEquals("Johnny boy", subject.replaceTokens("${a}[2]"));
		Assert.assertEquals("Hello,World,Johnny boy", subject.replaceTokens("${a}"));

		subject.setData("b", new String[][]{new String[]{"Otto", "Light", "Years Ahead"},
		                                    new String[]{"Jixma", "Out", "Standing"}});
		Assert.assertEquals("Years Ahead", subject.replaceTokens("${b}[0][2]"));
		Assert.assertEquals("Otto,Light,Years Ahead,Jixma,Out,Standing", subject.replaceTokens("${b}"));

		HashSet<String> set = new LinkedHashSet<>();
		set.add("patty");
		set.add("petty");
		set.add("phatty");
		set.add("fatie");
		subject.setData("set", set);
		Assert.assertEquals("patty", subject.replaceTokens("${set}[0]"));
		Assert.assertEquals("petty", subject.replaceTokens("${set}[1]"));
		Assert.assertEquals("phatty", subject.replaceTokens("${set}[2]"));
		Assert.assertEquals("fatie", subject.replaceTokens("${set}[3]"));
		Assert.assertEquals("patty,petty,phatty,fatie", subject.replaceTokens("${set}"));

		Map<String, Integer> ages = new LinkedHashMap<>();
		ages.put("John", 14);
		ages.put("Sam", 19);
		ages.put("Johnny", 41);
		ages.put("Sammy", 91);
		subject.setData("ages", ages);
		Assert.assertEquals("14", subject.replaceTokens("${ages}.John"));
		Assert.assertEquals("19", subject.replaceTokens("${ages}.[Sam]"));
		Assert.assertEquals("19[my]", subject.replaceTokens("${ages}.Sam[my]"));
		Assert.assertEquals("91", subject.replaceTokens("${ages}.Sammy"));
		Assert.assertEquals("19 my", subject.replaceTokens("${ages}.Sam my"));
		Assert.assertEquals("19.my", subject.replaceTokens("${ages}.Sam.my"));
		Assert.assertEquals("19.Sammy", subject.replaceTokens("${ages}.Sam.Sammy"));
		Assert.assertEquals("41", subject.replaceTokens("${ages}.[Johnny]"));

		List<Map<String, List<Number>>> stuff = new ArrayList<>();
		Map<String, List<Number>> stuff1 = new LinkedHashMap<>();
		stuff1.put("Los Angeles", Arrays.asList(91, 55, 43, 20));
		stuff1.put("Chicago", Arrays.asList(55, 45, 91, 11));
		stuff1.put("New York", Arrays.asList(123, 11, 1, 0));
		stuff.add(stuff1);
		Map<String, List<Number>> stuff2 = new LinkedHashMap<>();
		stuff2.put("Banana", Arrays.asList(14.59, 15.01, 15.02));
		stuff2.put("Apple", Arrays.asList(11.55, 12, 12.31));
		stuff2.put("Chocolate", Arrays.asList(8.50));
		stuff.add(stuff2);
		subject.setData("stuff", stuff);

		Assert.assertEquals("55", subject.replaceTokens("${stuff}[0].[Los Angeles][1]"));
		Assert.assertEquals("11", subject.replaceTokens("${stuff}[0].Chicago[3]"));
		Assert.assertEquals("14.59,15.01,15.02", subject.replaceTokens("${stuff}[1].Banana"));
		Assert.assertEquals("8.5", subject.replaceTokens("${stuff}[1].Chocolate"));
		Assert.assertEquals("8.5", subject.replaceTokens("${stuff}[1].Chocolate[0]"));
		// chocolate doesn't have 3 items, so we get empty string
		Assert.assertEquals("", subject.replaceTokens("${stuff}[1].Chocolate[2]"));

		// since there's index 2 of stuff, but this object doesn't contain Cinnamon, we'll get print-out of index 2
		Assert.assertEquals("[2]", subject.replaceTokens("${stuff}[1].Cinnamon[2]"));

		// since there's not index 15 of stuff, we'll get empty string
		Assert.assertEquals("", subject.replaceTokens("${stuff}[14]"));
	}

	@Test
	public void findTokens() { }

	@Test
	public void mergeProperty() { }

	@Test
	public void encodeForUrl() { }

	@Test
	public void handleFunction() {
		ExecutionContext subject = new MockExecutionContext();
		subject.setData(TEXT_DELIM, ",");
		subject.setData("firstDOW", "04/30/2017");
		subject.builtinFunctions = new HashMap<>();
		subject.builtinFunctions.put("date", new Date());
		subject.builtinFunctions.put("sysdate", new Sysdate());

		Assert.assertEquals("05/01/2017", subject.handleFunction("$(date|addDay|${firstDOW}|1)"));
		Assert.assertEquals("04/30/2017", subject.handleFunction("$(date|addDay|$(date|addDay|${firstDOW}|-1)|1)"));
		Assert.assertEquals("05/01/17",
		                    subject.handleFunction("$(date|format|$(date|addDay|${firstDOW}|1)|MM/dd/yyyy|MM/dd/yy)"));

		System.out.println(subject.handleFunction(
			"$(date|format|$(date|addDay|$(sysdate|firstDOW|MM/dd/yyyy)|1)|MM/dd/yyyy|MM/dd/yy)"));
	}

}