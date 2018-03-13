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
 */

package org.nexial.core.tools;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;
import org.nexial.core.excel.Excel;
import org.nexial.core.utils.InputFileUtils;

import static org.nexial.core.NexialTestUtils.getResourcePath;

public class TestScriptUpdaterTest {
    private final Class CLAZZ = this.getClass();

    @Test
    public void updateTemplate() throws Exception {
        String targetPath = getResourcePath(CLAZZ, "v2_script.xlsx");
        Excel targetExcel = new Excel(new File(targetPath));

        // first, target is known to be a v2 script
        Assert.assertTrue(InputFileUtils.isValidScript(targetPath));
        Assert.assertTrue(InputFileUtils.isV15Script(targetExcel.worksheet("Test Scenario")));
        Assert.assertTrue(InputFileUtils.isV2Script(targetExcel.worksheet("Local Testing")));

        // apply updateTemplate() to target
        TestScriptUpdater updater = new TestScriptUpdater();
        Assert.assertTrue(updater.updateTemplate(targetExcel));

        // now, the target is no longer a v2 script
        targetExcel = new Excel(new File(targetPath));
        Assert.assertFalse(InputFileUtils.isV2Script(targetExcel.worksheet("Test Scenario")));
        Assert.assertFalse(InputFileUtils.isV2Script(targetExcel.worksheet("Local Testing")));

        // and yes, it is STILL considered "valid" now - yay!
        Assert.assertTrue(InputFileUtils.isValidScript(targetPath));
    }
}