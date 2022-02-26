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

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class CheckUtilsTest {

    @Test
    public void requiresReadableDirectory_on_newly_created_directory() throws Exception {
        String testDir = ExecUtils.newRuntimeTempDir();

        try {
            Assert.assertTrue(new File(testDir).mkdirs());
            Assert.assertTrue(CheckUtils.requiresReadableDirectory(testDir, "directory not created", testDir));
        } finally {
            FileUtils.deleteDirectory(new File(testDir));
        }
    }
}