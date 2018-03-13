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

import java.io.File;

import org.junit.Assert;
import org.junit.Test;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;

import static org.nexial.core.NexialTestUtils.getResourcePath;

public class InputFileUtilsTest {
    private final Class CLAZZ = InputFileUtilsTest.class;

    @Test
    public void isValidScript() throws Exception {
        Assert.assertFalse(InputFileUtils.isValidScript(getResourcePath(CLAZZ, "script_not_xlsx.xlsx")));
        Assert.assertFalse(InputFileUtils.isValidScript(getResourcePath(CLAZZ, "script_blank.xlsx")));
        Assert.assertFalse(InputFileUtils.isValidScript(getResourcePath(CLAZZ, "script_no_system.xlsx")));
        Assert.assertFalse(InputFileUtils.isValidScript(getResourcePath(CLAZZ, "script_no_system2.xlsx")));
        Assert.assertFalse(InputFileUtils.isValidScript(getResourcePath(CLAZZ, "script_no_system_bad_format.xlsx")));
        Assert.assertFalse(InputFileUtils.isValidScript(getResourcePath(CLAZZ, "script_no_system_no_commands.xlsx")));
        Assert.assertFalse(InputFileUtils.isValidScript(getResourcePath(CLAZZ,
                                                                        "script_no_system_mismatch_commands.xlsx")));
        Assert.assertFalse(InputFileUtils.isValidScript(getResourcePath(CLAZZ, "script_bad_format.xlsx")));
        Assert.assertTrue(InputFileUtils.isValidScript(getResourcePath(CLAZZ, "script_good.xlsx")));
        Assert.assertTrue(InputFileUtils.isValidScript(getResourcePath(CLAZZ, "script_good_2_scenarios.xlsx")));
    }

    @Test
    public void isValidDataFile() throws Exception {
        Assert.assertFalse(InputFileUtils.isValidDataFile(getResourcePath(CLAZZ, "data_not_excel_2007.xls")));
        Assert.assertFalse(InputFileUtils.isValidDataFile(getResourcePath(CLAZZ, "data_bad_format.xlsx")));
        Assert.assertFalse(InputFileUtils.isValidDataFile(getResourcePath(CLAZZ, "data_blank.xlsx")));
        Assert.assertTrue(InputFileUtils.isValidDataFile(getResourcePath(CLAZZ, "data_good.xlsx")));
        Assert.assertTrue(InputFileUtils.isValidDataFile(getResourcePath(CLAZZ, "data_good_2_sheets.xlsx")));
    }

    @Test
    public void isValidV2Script() throws Exception {
        Excel excel = new Excel(new File(getResourcePath(CLAZZ, "script_good_v2.xlsx")));

        Worksheet worksheet1 = excel.worksheet("Test Scenario");
        Assert.assertTrue(InputFileUtils.isV2Script(worksheet1));

        Worksheet worksheet2 = excel.worksheet("Local Testing");
        Assert.assertTrue(InputFileUtils.isV2Script(worksheet2));
    }
}