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

package org.nexial.commons.conversion;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.nexial.commons.conversion.Tif2PdfConverter.DocumentMetaData;
import org.nexial.commons.utils.FileUtil;

import com.itextpdf.text.DocumentException;

import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;

public class Tif2PdfConverterTest {
    private Tif2PdfConverter converter = new Tif2PdfConverter();

    @Test
    public void testConvert() throws Exception {
        converter.setDeleteWhenComplete(false);

        // todo: use different tiff
        String fixtureDir = new File(this.getClass().getResource("./onepage.tif").getFile()).getParent()
                            + File.separatorChar;

        FileUtil.deleteFiles(fixtureDir, ".+page.+.pdf", false, false);

        _testConvert(fixtureDir + "onepage.tif", fixtureDir + "onepage.pdf");
        _testConvert(fixtureDir + "twopages.tif", fixtureDir + "twopages.pdf");
        _testConvert(fixtureDir + "threepages.tif", fixtureDir + "threepages.pdf");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: Tiff2Pdf file1.tif [file2.tif ... fileN.tif]");
            System.exit(1);
        }

        Tif2PdfConverter converter = new Tif2PdfConverter();
        for (String arg : args) {
            DocumentMetaData metaData = new DocumentMetaData();
            metaData.setTitle(arg);
            converter.convert(arg, NULL_OUTPUT_STREAM, metaData);
        }
    }

    private void _testConvert(String fixture1, String testFileName1) throws IOException, DocumentException {
        DocumentMetaData metaData = new DocumentMetaData();
        metaData.setTitle(fixture1);
        converter.convert(fixture1, new FileOutputStream(testFileName1), metaData);
        File testFile1 = new File(testFileName1);
        Assert.assertTrue(testFile1.exists());
        Assert.assertTrue(testFile1.canRead());
        Assert.assertTrue(testFile1.length() > 0);
    }

}
