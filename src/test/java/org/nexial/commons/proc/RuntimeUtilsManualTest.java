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

package org.nexial.commons.proc;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import static java.io.File.separator;
import static org.apache.commons.lang3.SystemUtils.JAVA_IO_TMPDIR;
import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;

/**
 * MUST BE EXECUTED AS MANUAL TEST SINCE MANY OF THESE TESTS NEED TO BE COORDINATED WITH OTHER PROCESSES RUNNING ON OS
 */
public class RuntimeUtilsManualTest {
    private File tempFile1 = new File(StringUtils.appendIfMissing(JAVA_IO_TMPDIR, separator) + "dummy1");

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteQuietly(tempFile1);
    }

    @Test
    public void find_and_kill_pids() throws Throwable {
        // create dummy file to be used for `tail`
        FileUtils.writeStringToFile(tempFile1, RandomStringUtils.randomAlphanumeric(50), DEF_FILE_ENCODING);

        // run tail processes
        ProcessInvoker.invokeNoWait("tail", Arrays.asList("-f", tempFile1.getAbsolutePath()), null);
        ProcessInvoker.invokeNoWait("tail", Arrays.asList("-f", tempFile1.getAbsolutePath()), null);
        ProcessInvoker.invokeNoWait("tail", Arrays.asList("-f", tempFile1.getAbsolutePath()), null);

        scan_and_kill();
    }

    @Test
    public void runAppNoWait() throws Throwable {
        // create dummy file to be used for `tail`
        FileUtils.writeStringToFile(tempFile1, RandomStringUtils.randomAlphanumeric(50), DEF_FILE_ENCODING);

        String tmpFilePath = tempFile1.getAbsolutePath();
        RuntimeUtils.runAppNoWait("/usr/bin", "tail", Arrays.asList("-f", tmpFilePath), new HashMap<>());
        RuntimeUtils.runAppNoWait("/usr/bin", "tail", Arrays.asList("-f", tmpFilePath), new HashMap<>());
        RuntimeUtils.runAppNoWait("/usr/bin", "tail", Arrays.asList("-f", tmpFilePath), new HashMap<>());

        scan_and_kill();
    }

    protected void scan_and_kill() {
        // i should find at least 3 tails
        List<Integer> tailPids = RuntimeUtils.findRunningInstancesOnNIX("tail");
        System.out.println("tailPids = " + tailPids);
        Assert.assertNotNull(tailPids);
        Assert.assertTrue(tailPids.size() >= 3);

        // kill 'em
        Assert.assertTrue(RuntimeUtils.terminateInstanceOnNIX(tailPids.get(0)));
        Assert.assertTrue(RuntimeUtils.terminateInstancesOnNIX("tail"));

        // look for them again.. they should be gone
        List<Integer> newTailPids = RuntimeUtils.findRunningInstancesOnNIX("tail");
        System.out.println("newTailPids = " + newTailPids);
        if (newTailPids != null) {
            Assert.assertTrue(newTailPids.size() <= 0);
            newTailPids.forEach(pid -> {
                if (tailPids.contains(pid)) { Assert.fail("EXPECTS pid " + pid + " to be terminated, but IT IS NOT!");}
            });
        }
    }
}