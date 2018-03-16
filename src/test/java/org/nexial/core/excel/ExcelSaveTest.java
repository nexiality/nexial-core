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

package org.nexial.core.excel;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.nexial.core.NexialTestUtils;
import org.nexial.core.excel.Excel.Worksheet;

import static java.io.File.separator;
import static org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK;

public class ExcelSaveTest {
	private File fixture;

	@Before
	public void init() throws Exception {
		File fixtureOrig = NexialTestUtils.getResourceFile(this.getClass(), this.getClass().getSimpleName() + ".xlsx");
		fixture = new File(SystemUtils.getJavaIoTmpDir().getAbsolutePath() + separator + fixtureOrig.getName());

		FileUtils.copyFile(fixtureOrig, fixture);
		Assert.assertTrue(fixture.canRead());
		Assert.assertTrue(fixture.length() > 1);
		System.out.println("created temp fixture at " + fixture);
	}

	@After
	public void tearDown() throws Exception {
		FileUtils.forceDelete(fixture);
	}

	@Test
	public void testSaveWorksheet() throws Exception {

		// update content in sheet 1
		System.out.println("before first set of changes, fixture.length() = " + fixture.length());
		Excel excel = new Excel(fixture);
		Worksheet scenario1 = excel.worksheet("Scenario1");
		scenario1.getSheet().getRow(4).getCell(13, CREATE_NULL_AS_BLANK).setCellValue("PASS");
		scenario1.getSheet().getRow(5).getCell(13, CREATE_NULL_AS_BLANK).setCellValue("PASS");
		scenario1.getSheet().getRow(6).getCell(13, CREATE_NULL_AS_BLANK).setCellValue("PASS");
		scenario1.save();

		System.out.println("after second set of changes, fixture.length() = " + fixture.length());
		excel = new Excel(fixture);
		scenario1 = excel.worksheet("Scenario1");
		Assert.assertEquals("PASS", scenario1.getSheet().getRow(4).getCell(13).getStringCellValue());
		Assert.assertEquals("PASS", scenario1.getSheet().getRow(5).getCell(13).getStringCellValue());
		Assert.assertEquals("PASS", scenario1.getSheet().getRow(6).getCell(13).getStringCellValue());

		// update content in sheet 2
		Worksheet scenario2 = excel.worksheet("Scenario2");
		scenario2.getSheet().getRow(4).getCell(13, CREATE_NULL_AS_BLANK).setCellValue("FAIL");
		scenario2.getSheet().getRow(5).getCell(13, CREATE_NULL_AS_BLANK).setCellValue("FAIL");
		scenario2.getSheet().getRow(6).getCell(13, CREATE_NULL_AS_BLANK).setCellValue("FAIL");
		scenario2.save();

		System.out.println("after second set of changes, fixture.length() = " + fixture.length());
		excel = new Excel(fixture);
		scenario2 = excel.worksheet("Scenario2");
		Assert.assertEquals("FAIL", scenario2.getSheet().getRow(4).getCell(13).getStringCellValue());
		Assert.assertEquals("FAIL", scenario2.getSheet().getRow(5).getCell(13).getStringCellValue());
		Assert.assertEquals("FAIL", scenario2.getSheet().getRow(6).getCell(13).getStringCellValue());
	}
}
