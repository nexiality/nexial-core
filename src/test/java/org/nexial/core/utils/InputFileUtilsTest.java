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

package org.nexial.core.utils;

import java.io.FileNotFoundException;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

public class InputFileUtilsTest {
	@Test
	public void isValidScript() throws Exception {

		Assert.assertFalse(InputFileUtils.isValidScript(getPath("script_not_xlsx.xlsx")));
		Assert.assertFalse(InputFileUtils.isValidScript(getPath("script_blank.xlsx")));
		Assert.assertFalse(InputFileUtils.isValidScript(getPath("script_no_system.xlsx")));
		Assert.assertFalse(InputFileUtils.isValidScript(getPath("script_no_system2.xlsx")));
		Assert.assertFalse(InputFileUtils.isValidScript(getPath("script_no_system_bad_format.xlsx")));
		Assert.assertFalse(InputFileUtils.isValidScript(getPath("script_no_system_no_commands.xlsx")));
		Assert.assertFalse(InputFileUtils.isValidScript(getPath("script_no_system_mismatch_commands.xlsx")));
		Assert.assertFalse(InputFileUtils.isValidScript(getPath("script_bad_format.xlsx")));
		Assert.assertTrue(InputFileUtils.isValidScript(getPath("script_good.xlsx")));
		Assert.assertTrue(InputFileUtils.isValidScript(getPath("script_good_2_scenarios.xlsx")));

	}

	@Test
	public void isValidDataFile() throws Exception {

		Assert.assertFalse(InputFileUtils.isValidDataFile(getPath("data_not_excel_2007.xls")));
		Assert.assertFalse(InputFileUtils.isValidDataFile(getPath("data_bad_format.xlsx")));
		Assert.assertFalse(InputFileUtils.isValidDataFile(getPath("data_blank.xlsx")));
		Assert.assertTrue(InputFileUtils.isValidDataFile(getPath("data_good.xlsx")));
		Assert.assertTrue(InputFileUtils.isValidDataFile(getPath("data_good_2_sheets.xlsx")));

	}

	public String getPath(String filename) throws FileNotFoundException {
		return ResourceUtils.getFile("classpath:"
		                             + StringUtils.replace(this.getClass().getPackage().getName(), ".", "/")
		                             + "/" + filename).getAbsolutePath();
	}

}