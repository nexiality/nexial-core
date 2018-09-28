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

package org.nexial.core.plugins.io;

import java.io.File;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.model.TestStep;

import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;

public class ExcelCommandTest {
    private static final String CLASSNAME = ExcelCommandTest.class.getSimpleName();
    private ExecutionContext context = new MockExecutionContext(true) {
        @Override
        public TestStep getCurrentTestStep() {
            return new TestStep() {
                @Override
                public String generateFilename(String ext) {
                    return CLASSNAME + StringUtils.prependIfMissing(StringUtils.trim(ext), ".");
                }
            };
        }
    };
    private final String resourceBasePath = "/" + StringUtils.replace(this.getClass().getPackage().getName(), ".", "/");

    @After
    public void tearDown() {
        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
    }

    @Test
    public void stringTo2dList() {
        ExcelCommand subject = new ExcelCommand();
        subject.init(context);

        // empty test
        Assert.assertTrue(CollectionUtils.isEmpty(subject.stringTo2dList(null)));
        Assert.assertTrue(CollectionUtils.isEmpty(subject.stringTo2dList("")));

        // happy path
        Assert.assertArrayEquals(new String[]{"a", "b", "c", "d", "e"},
                                 subject.stringTo2dList("a,b,c,d,e").get(0).toArray());

        // multi line test
        List<List<String>> twoDlist = subject.stringTo2dList("a,b,c,d,e\r\n1,2,3,4,5");
        Assert.assertArrayEquals(new String[]{"a", "b", "c", "d", "e"}, twoDlist.get(0).toArray());
        Assert.assertArrayEquals(new String[]{"1", "2", "3", "4", "5"}, twoDlist.get(1).toArray());

        // empty token test
        twoDlist = subject.stringTo2dList("a,b,c,,e\r\n1,,,4,5");
        Assert.assertArrayEquals(new String[]{"a", "b", "c", "", "e"}, twoDlist.get(0).toArray());
        Assert.assertArrayEquals(new String[]{"1", "", "", "4", "5"}, twoDlist.get(1).toArray());

        // empty line test
        twoDlist = subject.stringTo2dList(",,,\r\n,,,,5\n\n\n");
        Assert.assertEquals(2, twoDlist.size());
        Assert.assertArrayEquals(new String[]{"", "", "", ""}, twoDlist.get(0).toArray());
        Assert.assertArrayEquals(new String[]{"", "", "", "", "5"}, twoDlist.get(1).toArray());

        // empty line test 2
        twoDlist = subject.stringTo2dList(",,,\r\n,,,,5\n\n\n,,asdf,,$$\n\n.,l,;,',,");
        Assert.assertEquals(4, twoDlist.size());
        Assert.assertArrayEquals(new String[]{"", "", "", ""}, twoDlist.get(0).toArray());
        Assert.assertArrayEquals(new String[]{"", "", "", "", "5"}, twoDlist.get(1).toArray());
        Assert.assertArrayEquals(new String[]{"", "", "asdf", "", "$$"}, twoDlist.get(2).toArray());
        Assert.assertArrayEquals(new String[]{".", "l", ";", "'", "", ""}, twoDlist.get(3).toArray());
    }

    @Test
    public void columnarCsv() throws Exception {
        ExcelCommand command = new ExcelCommand();
        command.init(context);

        String basename = resourceBasePath + "/" + this.getClass().getSimpleName();
        String excel = ResourceUtils.getResourceFilePath(basename + "2.xlsx");
        Assert.assertTrue(FileUtil.isFileReadable(excel));
        String target = new File(excel).getParent() + "/" + this.getClass().getSimpleName() + "2.csv";

        // test 1: 2 areas, same height, no gaps, no quotes
        StepResult result = command.columnarCsv(excel, "Sheet1", "A1:C5,E1:E5", target);
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        File output = new File(target);
        Assert.assertTrue(FileUtil.isFileReadable(output, 10));
        Assert.assertEquals("A1,B1,C1,E1\r\n" +
                            "A2,B2,C2,E2\r\n" +
                            "A3,B3,C3,E3\r\n" +
                            "A4,B4,C4,E4\r\n" +
                            "A5,B5,C5,E5", FileUtils.readFileToString(output, DEF_FILE_ENCODING));

        // test 2: 2 areas, same height, some gaps, no quotes
        result = command.columnarCsv(excel, "Sheet1", "A7:C11,E7:E11", target);
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        output = new File(target);
        Assert.assertTrue(FileUtil.isFileReadable(output, 10));
        Assert.assertEquals("A1,B1,C1,E1\r\n" +
                            "A2,B2,C2,\r\n" +
                            "A3,,C3,\r\n" +
                            "A4,B4,,E4\r\n" +
                            "A5,B5,C5,E5", FileUtils.readFileToString(output, DEF_FILE_ENCODING));

        // test 3: 2 areas, same height, some gaps, some quotes
        result = command.columnarCsv(excel, "Sheet1", "A13:A17,C13:E17", target);
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        output = new File(target);
        Assert.assertTrue(FileUtil.isFileReadable(output, 10));
        Assert.assertEquals("A1,C1,D1,E1\r\n" +
                            "A2,\"\"C2\"\",D2,\r\n" +
                            "A3,C3,D3,\r\n" +
                            "A4,,,E4\r\n" +
                            "A5,C5,,E5", FileUtils.readFileToString(output, DEF_FILE_ENCODING));

        // test 4: 2 areas, uneven height, some gaps, some quotes
        result = command.columnarCsv(excel, "Sheet1", "A19:C21,D19:E23", target);
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        output = new File(target);
        Assert.assertTrue(FileUtil.isFileReadable(output, 10));
        Assert.assertEquals("A1,B1,C1,D1,E1\r\n" +
                            "A2,B2,\"\"C2\"\",D2,\r\n" +
                            "A3,,C3,D3,\r\n" +
                            ",,,,E4\r\n" +
                            ",,,,E5", FileUtils.readFileToString(output, DEF_FILE_ENCODING));

        // test 5: 3 areas, uneven height, overlaps some gaps, some quotes, commas
        result = command.columnarCsv(excel, "Sheet1", "A31:B34,B32:E33,A33:D35", target);
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        output = new File(target);
        Assert.assertTrue(FileUtil.isFileReadable(output, 10));
        Assert.assertEquals(",B1,B2,\"\"C2\"\",D2,E2  32,A3,B3,C3,D3\r\n" +
                            "A2,B2,B3,C3,D3,E3,\"\"A4,34\"\",B4,C4,D4\r\n" +
                            "A3,B3,,,,,A5,B5,C5,\r\n" +
                            "\"\"A4,34\"\",B4", FileUtils.readFileToString(output, DEF_FILE_ENCODING));
    }
}