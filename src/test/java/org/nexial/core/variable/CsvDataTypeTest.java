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

package org.nexial.core.variable;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import com.univocity.parsers.common.record.Record;

public class CsvDataTypeTest {

    @Test
    public void fetch_and_remove_first_column() throws Exception {
        CsvDataType fixture = new CsvDataType(
            "Column A,Column B,Column C,Column D\n" +
            "1^14^000000290^CA,000000290,261.9700,17.2900\n" +
            "1^14^000001953^CN,000001953,1896.3800,0.0000\n" +
            "1^14^000003868^CN,000003868,769.5800,114.8000\n"
        );
        fixture.setDelim(",");
        fixture.setHeader(true);
        fixture.setIndices(Arrays.asList("Column A", "Column B", "Column C", "Column D"));
        fixture.setRecordDelim("\n");
        fixture.setReadyToParse(true);
        fixture.parse();

        Record record = fixture.retrieveFromCache("Column A", "1^14^000001953^CN");
        Assert.assertNotNull(record);
        Assert.assertEquals("1^14^000001953^CN", record.getString("Column A"));
        Assert.assertEquals("000001953", record.getString("Column B"));
        Assert.assertEquals("1896.3800", record.getString("Column C"));
        Assert.assertEquals("0.0000", record.getString("Column D"));

        record = fixture.remove("Column A", "1^14^000001953^CN");
        Assert.assertNotNull(record);
        Assert.assertEquals("1^14^000001953^CN", record.getString("Column A"));
        Assert.assertEquals("000001953", record.getString("Column B"));
        Assert.assertEquals("1896.3800", record.getString("Column C"));
        Assert.assertEquals("0.0000", record.getString("Column D"));

        Assert.assertEquals(2, fixture.getValue().size());
        Assert.assertEquals("Column A,Column B,Column C,Column D\n" +
                            "1^14^000000290^CA,000000290,261.9700,17.2900\n" +
                            "1^14^000003868^CN,000003868,769.5800,114.8000",
                            fixture.getTextValue());

        record = fixture.retrieveFromCache("Column A", "1^14^000003868^CN");
        Assert.assertNotNull(record);
        Assert.assertEquals("1^14^000003868^CN", record.getString("Column A"));
        Assert.assertEquals("000003868", record.getString("Column B"));
        Assert.assertEquals("769.5800", record.getString("Column C"));
        Assert.assertEquals("114.8000", record.getString("Column D"));

        record = fixture.retrieveFromCache("Column A", "1^14^000000290^CA");
        Assert.assertNotNull(record);
        Assert.assertEquals("1^14^000000290^CA", record.getString("Column A"));
        Assert.assertEquals("000000290", record.getString("Column B"));
        Assert.assertEquals("261.9700", record.getString("Column C"));
        Assert.assertEquals("17.2900", record.getString("Column D"));

        record = fixture.remove("Column A", "1^14^000000290^CA");
        Assert.assertEquals(1, fixture.getValue().size());
        Assert.assertEquals("Column A,Column B,Column C,Column D\n" +
                            "1^14^000003868^CN,000003868,769.5800,114.8000",
                            fixture.getTextValue());

        record = fixture.remove("Column A", "1^14^000003868^CN");
        Assert.assertEquals(0, fixture.getValue().size());
        Assert.assertEquals("Column A,Column B,Column C,Column D", fixture.getTextValue());
    }

    @Test
    public void fetch_and_remove_first_column_CRLF() throws Exception {
        CsvDataType fixture = new CsvDataType(
            "Column A,Column B,Column C,Column D\r\n" +
            "1^14^000000290^CA,000000290,261.9700,17.2900\r\n" +
            "1^14^000001953^CN,000001953,1896.3800,0.0000\r\n" +
            "1^14^000003868^CN,000003868,769.5800,114.8000\r\n"
        );
        fixture.setDelim(",");
        fixture.setHeader(true);
        fixture.setIndices(Arrays.asList("Column A", "Column B", "Column C", "Column D"));
        fixture.setRecordDelim("\r\n");
        fixture.setReadyToParse(true);
        fixture.parse();

        Record record = fixture.retrieveFromCache("Column A", "1^14^000001953^CN");
        Assert.assertNotNull(record);
        Assert.assertEquals("1^14^000001953^CN", record.getString("Column A"));
        Assert.assertEquals("000001953", record.getString("Column B"));
        Assert.assertEquals("1896.3800", record.getString("Column C"));
        Assert.assertEquals("0.0000", record.getString("Column D"));

        record = fixture.remove("Column A", "1^14^000001953^CN");
        Assert.assertNotNull(record);
        Assert.assertEquals("1^14^000001953^CN", record.getString("Column A"));
        Assert.assertEquals("000001953", record.getString("Column B"));
        Assert.assertEquals("1896.3800", record.getString("Column C"));
        Assert.assertEquals("0.0000", record.getString("Column D"));

        Assert.assertEquals(2, fixture.getValue().size());
        Assert.assertEquals("Column A,Column B,Column C,Column D\r\n" +
                            "1^14^000000290^CA,000000290,261.9700,17.2900\r\n" +
                            "1^14^000003868^CN,000003868,769.5800,114.8000",
                            fixture.getTextValue());

        record = fixture.retrieveFromCache("Column A", "1^14^000003868^CN");
        Assert.assertNotNull(record);
        Assert.assertEquals("1^14^000003868^CN", record.getString("Column A"));
        Assert.assertEquals("000003868", record.getString("Column B"));
        Assert.assertEquals("769.5800", record.getString("Column C"));
        Assert.assertEquals("114.8000", record.getString("Column D"));

        record = fixture.retrieveFromCache("Column A", "1^14^000000290^CA");
        Assert.assertNotNull(record);
        Assert.assertEquals("1^14^000000290^CA", record.getString("Column A"));
        Assert.assertEquals("000000290", record.getString("Column B"));
        Assert.assertEquals("261.9700", record.getString("Column C"));
        Assert.assertEquals("17.2900", record.getString("Column D"));

        record = fixture.remove("Column A", "1^14^000000290^CA");
        Assert.assertEquals(1, fixture.getValue().size());
        Assert.assertEquals("Column A,Column B,Column C,Column D\r\n" +
                            "1^14^000003868^CN,000003868,769.5800,114.8000",
                            fixture.getTextValue());

        record = fixture.remove("Column A", "1^14^000003868^CN");
        Assert.assertEquals(0, fixture.getValue().size());
        Assert.assertEquals("Column A,Column B,Column C,Column D", fixture.getTextValue());
    }

    @Test
    public void fetch_and_remove_some_column() throws Exception {
        CsvDataType fixture = new CsvDataType(
            "Column A,Column B,Column C,Column D\n" +
            "1^14^000000290^CA,000000290,261.9700,17.2900\n" +
            "1^14^000001953^CN,000001953,1896.3800,0.0000\n" +
            "1^14^000003868^CN,000003868,769.5800,114.8000\n"
        );
        fixture.setDelim(",");
        fixture.setHeader(true);
        fixture.setIndices(Arrays.asList("Column B", "Column D"));
        fixture.setRecordDelim("\n");
        fixture.setReadyToParse(true);
        fixture.parse();

        Record record = fixture.retrieveFromCache("Column B", "000001953");
        Assert.assertNotNull(record);
        Assert.assertEquals("1^14^000001953^CN", record.getString("Column A"));
        Assert.assertEquals("000001953", record.getString("Column B"));
        Assert.assertEquals("1896.3800", record.getString("Column C"));
        Assert.assertEquals("0.0000", record.getString("Column D"));

        record = fixture.remove("Column B", "000001953");
        Assert.assertNotNull(record);
        Assert.assertEquals("1^14^000001953^CN", record.getString("Column A"));
        Assert.assertEquals("000001953", record.getString("Column B"));
        Assert.assertEquals("1896.3800", record.getString("Column C"));
        Assert.assertEquals("0.0000", record.getString("Column D"));

        Assert.assertEquals(2, fixture.getValue().size());
        Assert.assertEquals("Column A,Column B,Column C,Column D\n" +
                            "1^14^000000290^CA,000000290,261.9700,17.2900\n" +
                            "1^14^000003868^CN,000003868,769.5800,114.8000",
                            fixture.getTextValue());
    }

    @Test
    public void fetch_and_remove_last_column() throws Exception {
        CsvDataType fixture = new CsvDataType(
            "Column A,Column B,Column C,Column D\n" +
            "1^14^000000290^CA,000000290,261.9700,17.2900\n" +
            "1^14^000001953^CN,000001953,1896.3800,0.0000\n" +
            "1^14^000003868^CN,000003868,769.5800,114.8000\n"
        );
        fixture.setDelim(",");
        fixture.setHeader(true);
        fixture.setIndices(Collections.singletonList("Column D"));
        fixture.setRecordDelim("\n");
        fixture.setReadyToParse(true);
        fixture.parse();

        Record record = fixture.retrieveFromCache("Column D", "0.0000");
        Assert.assertNotNull(record);
        Assert.assertEquals("1^14^000001953^CN", record.getString("Column A"));
        Assert.assertEquals("000001953", record.getString("Column B"));
        Assert.assertEquals("1896.3800", record.getString("Column C"));
        Assert.assertEquals("0.0000", record.getString("Column D"));

        record = fixture.remove("Column D", "0.0000");
        Assert.assertNotNull(record);
        Assert.assertEquals("1^14^000001953^CN", record.getString("Column A"));
        Assert.assertEquals("000001953", record.getString("Column B"));
        Assert.assertEquals("1896.3800", record.getString("Column C"));
        Assert.assertEquals("0.0000", record.getString("Column D"));

        Assert.assertEquals(2, fixture.getValue().size());
        Assert.assertEquals("Column A,Column B,Column C,Column D\n" +
                            "1^14^000000290^CA,000000290,261.9700,17.2900\n" +
                            "1^14^000003868^CN,000003868,769.5800,114.8000",
                            fixture.getTextValue());
    }
}