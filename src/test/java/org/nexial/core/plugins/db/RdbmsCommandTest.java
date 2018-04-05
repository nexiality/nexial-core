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

package org.nexial.core.plugins.db;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class RdbmsCommandTest {
	@Test
	public void rowToString() throws Exception {
		RdbmsCommand subject = new RdbmsCommand();

		List<String> columnNames = Arrays.asList("code", "description", "address");
		String delim = ",";

		Map<String, String> fixture = new HashMap<>();
		fixture.put("code", "BRK");
		fixture.put("description", "Burbank");
		fixture.put("address", " ,     ");
		String csv = subject.rowToString(fixture, columnNames, delim);
		Assert.assertEquals("BRK,Burbank,\" ,     \"", csv);

		fixture = new HashMap<>();
		fixture.put("code", "PHX");
		fixture.put("description", "Haxonwak, AZ office");
		fixture.put("address", "1921 Alphine st #525, Haxonwak AZ 25382 USA");
		csv = subject.rowToString(fixture, columnNames, delim);
		Assert.assertEquals("PHX,\"Haxonwak, AZ office\",\"1921 Alphine st #525, Haxonwak AZ 25382 USA\"", csv);

		fixture = new HashMap<>();
		fixture.put("code", "007 Location");
		fixture.put("description", "De\"s,\"c");
		fixture.put("address", "Phoenix , Tempe AZ 20001 US");
		csv = subject.rowToString(fixture, columnNames, delim);
		Assert.assertEquals("007 Location,\"De\"\"s,\"\"c\",\"Phoenix , Tempe AZ 20001 US\"", csv);

	}

}