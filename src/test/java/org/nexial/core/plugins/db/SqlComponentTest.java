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

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.nexial.core.plugins.db.SqlComponent;
import org.nexial.core.plugins.db.SqlComponent.Type;


public class SqlComponentTest {
    @Test
    public void testToList() {
        String fixture =
            "-- $Id: SqlComponentTest.java 729 1995-12-13 06:51:47Z Lium $\n"
            + "\n"
            + "-- nexial:r1\n"
            + "INSERT INTO REF_PROPS (PROP_TYPE, PROP_NAME, PROP_VAL, USAGE, UPDATED_BY, UPDATED_ON)\n"
            + "\tVALUES ('JUNK', 'TMP1', 'VAL1', '', 'SYSTEM', current timestamp)\n"
            + ";\n"
            + "\n"
            + " --nexial:insert2 \n"
            + "INSERT INTO REF_PROPS (PROP_TYPE, PROP_NAME, PROP_VAL, USAGE, UPDATED_BY, UPDATED_ON)\n"
            + "\tVALUES ('JUNK', 'TMP2', 'VAL2', '', 'SYSTEM', current timestamp)\n"
            + ";\n"
            + "\n"
            + " -- this should work as nexial:insert3 junk \n"
            + "INSERT INTO REF_PROPS (PROP_TYPE, PROP_NAME, PROP_VAL, USAGE, UPDATED_BY, UPDATED_ON)\n"
            + "\tVALUES ('JUNK', 'TMP3', 'VAL3', '', 'SYSTEM', current timestamp)\n"
            + ";\n"
            + "\n"
            + "\t\t-- nothing to se here... move on...\n"
            + "INSERT INTO REF_PROPS (PROP_TYPE, PROP_NAME, PROP_VAL, USAGE, UPDATED_BY, UPDATED_ON)\n"
            + "\tVALUES ('JUNK', 'TMP4', 'VAL4', '', 'SYSTEM', current timestamp)\n"
            + ";\n"
            + "\n"
            + "-- nexial:insert_junk_5\n"
            + "INSERT INTO REF_PROPS (PROP_TYPE, PROP_NAME, PROP_VAL, USAGE, UPDATED_BY, UPDATED_ON)\n"
            + "\tVALUES ('JUNK', 'TMP5', 'VAL5', '', 'SYSTEM', current timestamp)\n"
            + ";\n"
            + "\n"
            + "-- get all junk records to nexial:nrs020 variable\n"
            + "SELECT PROP_NAME \"name\", PROP_VAL \"value\" FROM REF_PROPS WHERE PROP_TYPE = 'JUNK'\n"
            + ";\n"
            + "\n";

        List<SqlComponent> sqls = SqlComponent.toList(fixture);
        Assert.assertNotNull(sqls);
        Assert.assertEquals(sqls.size(), 6);

        SqlComponent sql1 = sqls.get(0);
        Assert.assertNotNull(sql1);
        Assert.assertEquals("$Id: SqlComponentTest.java 729 1995-12-13 06:51:47Z Lium $ nexial:r1", sql1.getComments());
        Assert.assertEquals("INSERT INTO REF_PROPS (PROP_TYPE, PROP_NAME, PROP_VAL, USAGE, UPDATED_BY, UPDATED_ON) " +
                            "VALUES ('JUNK', 'TMP1', 'VAL1', '', 'SYSTEM', current timestamp)",
                            sql1.getSql());
        Assert.assertEquals(Type.INSERT, sql1.getType());
        Assert.assertEquals("r1", sql1.getVarName());

        SqlComponent sql2 = sqls.get(1);
        Assert.assertNotNull(sql2);
        Assert.assertEquals("nexial:insert2", sql2.getComments());
        Assert.assertEquals("INSERT INTO REF_PROPS (PROP_TYPE, PROP_NAME, PROP_VAL, USAGE, UPDATED_BY, UPDATED_ON) " +
                            "VALUES ('JUNK', 'TMP2', 'VAL2', '', 'SYSTEM', current timestamp)",
                            sql2.getSql());
        Assert.assertEquals(Type.INSERT, sql2.getType());
        Assert.assertEquals("insert2", sql2.getVarName());

        SqlComponent sql3 = sqls.get(2);
        Assert.assertNotNull(sql3);
        Assert.assertEquals("this should work as nexial:insert3 junk", sql3.getComments());
        Assert.assertEquals("INSERT INTO REF_PROPS (PROP_TYPE, PROP_NAME, PROP_VAL, USAGE, UPDATED_BY, UPDATED_ON) " +
                            "VALUES ('JUNK', 'TMP3', 'VAL3', '', 'SYSTEM', current timestamp)",
                            sql3.getSql());
        Assert.assertEquals(Type.INSERT, sql3.getType());
        Assert.assertEquals("insert3 junk", sql3.getVarName());

        SqlComponent sql4 = sqls.get(3);
        Assert.assertNotNull(sql4);
        Assert.assertEquals("nothing to se here... move on...", sql4.getComments());
        Assert.assertEquals("INSERT INTO REF_PROPS (PROP_TYPE, PROP_NAME, PROP_VAL, USAGE, UPDATED_BY, UPDATED_ON) " +
                            "VALUES ('JUNK', 'TMP4', 'VAL4', '', 'SYSTEM', current timestamp)",
                            sql4.getSql());
        Assert.assertEquals(Type.INSERT, sql4.getType());
        Assert.assertNull(sql4.getVarName());

        SqlComponent sql5 = sqls.get(4);
        Assert.assertNotNull(sql5);
        Assert.assertEquals("nexial:insert_junk_5", sql5.getComments());
        Assert.assertEquals("INSERT INTO REF_PROPS (PROP_TYPE, PROP_NAME, PROP_VAL, USAGE, UPDATED_BY, UPDATED_ON) " +
                            "VALUES ('JUNK', 'TMP5', 'VAL5', '', 'SYSTEM', current timestamp)",
                            sql5.getSql());
        Assert.assertEquals(Type.INSERT, sql5.getType());
        Assert.assertEquals("insert_junk_5", sql5.getVarName());

        SqlComponent sql6 = sqls.get(5);
        Assert.assertNotNull(sql6);
        Assert.assertEquals("get all junk records to nexial:nrs020 variable", sql6.getComments());
        Assert.assertEquals("SELECT PROP_NAME \"name\", PROP_VAL \"value\" FROM REF_PROPS WHERE PROP_TYPE = 'JUNK'",
                            sql6.getSql());
        Assert.assertEquals(Type.SELECT, sql6.getType());
        Assert.assertEquals("nrs020 variable", sql6.getVarName());
    }
}
