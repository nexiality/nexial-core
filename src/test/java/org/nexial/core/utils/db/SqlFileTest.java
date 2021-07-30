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

package org.nexial.core.utils.db;

import java.io.File;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import org.nexial.commons.utils.TextUtils;

import static org.nexial.core.NexialConst.DEF_CHARSET;

public class SqlFileTest {
	@Test
	public void testGetStatements() throws Exception {
		String testFile = "SELECT\n"
		                  + "\tTSN, CENTURY, YR, QUARTER, STATE,\n\n"
		                  + "\tsubstr(LOCALX, 1, 1) || lpad(cast(INT(substr(LOCALX, 2, 3)) + 1 AS CHAR), 3, '0'),\n\n"
		                  + "\tCHECK_YEAR, CHECK_MONTH, CHECK_DAY, PAYROLL_NO, CONTROL, SEQUENCEX, TRAN_SUB_CODE,\n" +
		                  "\tDEP_DISP__WEEK_TYPE, TRANSFER_IND, FUTURE_QTR_IND, GROSS, WAGE_CIT, TAX_CIT, CIT_LOS,\n"
		                  + "\tEMP_COUNT_FEM, EMP_COUNT_MALE, DISPOSITION, ERR_WARN_FLAG, CIT_STACHG, FILLER0001,\n" +
		                  "\tPROC_YEAR, PROC_MONTH, PROC_DAY, CIT_ACCR, STATUS, DEL_YEAR, DEL_MONTH, DEL_DAY,\n"
		                  + "\tCOMMENT_CODE, COMMENT_DESC\n"
		                  + "FROM\n"
		                  + "\tDB20Q07.A09 A\n"
		                  + "WHERE\n"
		                  + "\tA.TSN = '0000061'\n"
		                  + "\tAND A.STATE = '55'\n"
		                  + "\tAND A.CHECK_YEAR = '13'\n"
		                  + "\tAND A.CHECK_MONTH = '03'\n"
		                  + "\tAND A.CHECK_DAY = '01'\n"
		                  + "\tAND INT(substr(LOCALX, 2, 3)) = (\n"
		                  + "\t\tSELECT MAX(INT(substr(LOCALX, 2, 3)))\n"
		                  + "\t\tFROM DB20Q07.A09 B\n"
		                  + "\t\tWHERE\n"
		                  + "\t\tB.TSN = A.TSN\n"
		                  + "\t\tAND B.STATE = A.STATE\n"
		                  + "\t\tAND B.CHECK_YEAR = A.CHECK_YEAR\n"
		                  + "\t\tAND B.CHECK_MONTH = A.CHECK_MONTH\n"
		                  + "\t\tAND B.CHECK_DAY = A.CHECK_DAY\n"
		                  + ")\n"
		                  + ";\n\n"
		                  + "SELECT\n"
		                  + "\tnvl((SELECT PARAM_VALUE FROM CFO_APP_PARAMS WHERE PARAM_NM = 'RECENT_ACTIVITY_SHORT_VERSION'), 15) AS \"RECENT_ACTIVITY_SHORT_VERSION\",\n"
		                  + "\tnvl((SELECT PARAM_VALUE FROM CFO_APP_PARAMS WHERE PARAM_NM = 'RECENT_ACTIVITY_LONG_VERSION'), 5)   AS \"RECENT_ACTIVITY_LONG_VERSION\",\n"
		                  + "\tnvl((SELECT PARAM_VALUE FROM CFO_APP_PARAMS WHERE PARAM_NM = 'CACHE_INTERVAL_MINUTES'), 10)        AS \"CACHE_INTERVAL_MINUTES\"\n"
		                  + "FROM DUAL\n"
		                  + ";\n\n"
		                  + "UPDATE SSA_ADM.CFO_APP_PARAMS\n"
		                  + "SET PARAM_VALUE = ?, CREATION_DT = SYSDATE, CREATED_BY_USR = 'SYSTEM'\n"
		                  + "WHERE PARAM_NM = ?\n" +
		                  ";";

		File f = new File(getClass().getResource(".").getFile() + File.separator + "test1.sql");
		FileUtils.writeStringToFile(f, testFile, DEF_CHARSET);

		SqlFile subject = SqlFile.newInstance(f);
		List<String> statements = subject.getStatements();

		Assert.assertEquals(CollectionUtils.size(statements), 3);
		System.out.println(TextUtils.toString(statements, "\n"));
		Assert.assertTrue(StringUtils.startsWith(statements.get(0), "SELECT TSN, CENTURY, YR, QUARTER, STATE, "));
		Assert.assertTrue(StringUtils.startsWith(statements.get(1),
		                                         "SELECT nvl((SELECT PARAM_VALUE FROM CFO_APP_PARAMS WHERE PARAM_NM = 'RECENT_ACTIVITY_SHORT_VERSION'), 15) AS \"RECENT_ACTIVITY_SHORT_VERSION\""));
		Assert.assertTrue(StringUtils.startsWith(statements.get(2),
		                                         "UPDATE SSA_ADM.CFO_APP_PARAMS SET PARAM_VALUE = ?, CREATION_DT = SYSDATE, CREATED_BY_USR = 'SYSTEM'"));
	}

	@Test
	public void testGetStatements2() throws Exception {
		String testFile = "-- $Id: SqlFileTest.java 248 2013-10-11 23:00:14Z lium $\n"
		                  +
		                  "\n"
		                  +
		                  "-- ****************************************************************************\n"
		                  +
		                  "-- usage metrics\n"
		                  +
		                  "-- ****************************************************************************\n"
		                  +
		                  "INSERT INTO CCP_USAGE_SERVICE (SERVICE_ID, SERVICE_NM) VALUES ('CLO', 'Sales Demo Clone Manager');\n"
		                  +
		                  "INSERT INTO CCP_USAGE_TRANSACTION (TRANS_ID, SERVICE_ID, TRANS_NM) VALUES ('CLO_0', 'CLO', 'Landing Page');\n"
		                  +
		                  "INSERT INTO CCP_USAGE_TRANSACTION (TRANS_ID, SERVICE_ID, TRANS_NM) VALUES ('CLO_1', 'CLO', 'Refresh Status');\n"
		                  +
		                  "INSERT INTO CCP_USAGE_TRANSACTION (TRANS_ID, SERVICE_ID, TRANS_NM) VALUES ('CLO_2', 'CLO', 'New Clone');\n"
		                  +
		                  "INSERT INTO CCP_USAGE_TRANSACTION (TRANS_ID, SERVICE_ID, TRANS_NM) VALUES ('CLO_3', 'CLO', 'Reset Clone');\n"
		                  +
		                  "INSERT INTO CCP_USAGE_TRANSACTION (TRANS_ID, SERVICE_ID, TRANS_NM) VALUES ('CLO_4', 'CLO', 'Release Clone');\n"
		                  +
		                  "INSERT INTO CCP_USAGE_TRANSACTION (TRANS_ID, SERVICE_ID, TRANS_NM) VALUES ('CLO_5', 'CLO', 'Retrieve Clone Info');\n"
		                  +
		                  "\n"
		                  +
		                  "INSERT INTO CLO_BUS_KEY_CONFIG (BUS_KEY_NM, BUS_KEY_STRT_VAL) VALUES ('SITE_ID', 1);\n"
		                  +
		                  "INSERT INTO CLO_BUS_KEY_CONFIG (BUS_KEY_NM, BUS_KEY_STRT_VAL) VALUES ('TSN', 800000);\n"
		                  +
		                  "INSERT INTO CLO_BUS_KEY_CONFIG (BUS_KEY_NM, BUS_KEY_STRT_VAL) VALUES ('FED_ID', 970000000);\n"
		                  +
		                  "\n";

		File f = new File(getClass().getResource(".").getFile() + File.separator + "test1.sql");
		FileUtils.writeStringToFile(f, testFile, DEF_CHARSET);

		SqlFile subject = SqlFile.newInstance(f);
		List<String> statements = subject.getStatements();

		Assert.assertEquals(CollectionUtils.size(statements), 10);
		System.out.println(TextUtils.toString(statements, "\n"));
		Assert.assertEquals(
			"INSERT INTO CCP_USAGE_SERVICE (SERVICE_ID, SERVICE_NM) VALUES ('CLO', 'Sales Demo Clone Manager')",
			statements.get(0));
		Assert.assertEquals(
			"INSERT INTO CCP_USAGE_TRANSACTION (TRANS_ID, SERVICE_ID, TRANS_NM) VALUES ('CLO_0', 'CLO', 'Landing Page')",
			statements.get(1));
		Assert.assertEquals(
			"INSERT INTO CCP_USAGE_TRANSACTION (TRANS_ID, SERVICE_ID, TRANS_NM) VALUES ('CLO_1', 'CLO', 'Refresh Status')",
			statements.get(2));
		Assert.assertEquals(
			"INSERT INTO CCP_USAGE_TRANSACTION (TRANS_ID, SERVICE_ID, TRANS_NM) VALUES ('CLO_2', 'CLO', 'New Clone')",
			statements.get(3));
		Assert.assertEquals(
			"INSERT INTO CCP_USAGE_TRANSACTION (TRANS_ID, SERVICE_ID, TRANS_NM) VALUES ('CLO_3', 'CLO', 'Reset Clone')",
			statements.get(4));
		Assert.assertEquals(
			"INSERT INTO CCP_USAGE_TRANSACTION (TRANS_ID, SERVICE_ID, TRANS_NM) VALUES ('CLO_4', 'CLO', 'Release Clone')",
			statements.get(5));
		Assert.assertEquals(
			"INSERT INTO CCP_USAGE_TRANSACTION (TRANS_ID, SERVICE_ID, TRANS_NM) VALUES ('CLO_5', 'CLO', 'Retrieve Clone Info')",
			statements.get(6));
		Assert.assertEquals("INSERT INTO CLO_BUS_KEY_CONFIG (BUS_KEY_NM, BUS_KEY_STRT_VAL) VALUES ('SITE_ID', 1)",
		                    statements.get(7));
		Assert.assertEquals("INSERT INTO CLO_BUS_KEY_CONFIG (BUS_KEY_NM, BUS_KEY_STRT_VAL) VALUES ('TSN', 800000)",
		                    statements.get(8));
		Assert
			.assertEquals("INSERT INTO CLO_BUS_KEY_CONFIG (BUS_KEY_NM, BUS_KEY_STRT_VAL) VALUES ('FED_ID', 970000000)",
			              statements.get(9));
	}

	@Test
	public void testGetStatements3() throws Exception {
		String testFile =
			"-- $Id: SqlFileTest.java 248 2013-10-11 23:00:14Z lium $\n"
			+
			"\n"
			+
			"DELETE FROM NRS020_REF_PROPERTIES WHERE PROPERTY_TYP LIKE 'JUNK%';\n"
			+
			"\n"
			+
			"--------------------------------------------------------------------------------\n"
			+
			"-- INSERT:NRS020_REF_PROPERTIES\n"
			+
			"--------------------------------------------------------------------------------\n"
			+
			"-- INSERT INTO NRS020_REF_PROPERTIES (PROPERTY_TYP, PROPERTY_NM, PROPERTY_VALUE, USAGE_DESC, LST_UPDT_USERID, LST_UPDT_TS)\n"
			+
			"-- \tVALUES ('JUNK_SENTRY', 'TMP1', 'VAL1', '', 'SYSTEM', current timestamp)\n"
			+
			"-- ;\n"
			+
			"\n";

		File f = new File(getClass().getResource(".").getFile() + File.separator + "test1.sql");
		FileUtils.writeStringToFile(f, testFile, DEF_CHARSET);

		SqlFile subject = SqlFile.newInstance(f);
		List<String> statements = subject.getStatements();

		Assert.assertEquals(CollectionUtils.size(statements), 1);
		System.out.println(TextUtils.toString(statements, "\n"));
		Assert.assertEquals("DELETE FROM NRS020_REF_PROPERTIES WHERE PROPERTY_TYP LIKE 'JUNK%'", statements.get(0));
	}

}
