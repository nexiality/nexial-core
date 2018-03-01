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

package org.nexial.core.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;

import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;

import static java.lang.System.lineSeparator;

public class TestPlanMeta {
	private static final ExcelAddress ADDR_SUMMARY = new ExcelAddress("A2");
	private static final ExcelAddress ADDR_AUTHOR = new ExcelAddress("F2");
	private static final ExcelAddress ADDR_JIRA = new ExcelAddress("G2");
	private static final ExcelAddress ADDR_ZEPHYR = new ExcelAddress("H2");
	private static final ExcelAddress ADDR_NOTIFICATION = new ExcelAddress("I2");

	private Worksheet worksheet;
	private String summary;
	private String author;
	private String jira;
	private String zephyr;
	private List<String> notifications;

	private TestPlanMeta(Worksheet worksheet) {
		this.worksheet = worksheet;
		parse();
	}

	public static TestPlanMeta toNewInstance(Worksheet worksheet) { return new TestPlanMeta(worksheet); }

	public String getSummary() { return summary; }

	public void setSummary(String summary) { this.summary = summary; }

	public String getAuthor() { return author; }

	public void setAuthor(String author) { this.author = author; }

	public String getJira() { return jira; }

	public void setJira(String jira) { this.jira = jira; }

	public String getZephyr() { return zephyr; }

	public void setZephyr(String zephyr) { this.zephyr = zephyr; }

	public List<String> getNotifications() { return notifications; }

	public void setNotifications(List<String> notifications) { this.notifications = notifications; }

	public void save() throws IOException {
		XSSFCell cell = worksheet.cell(ADDR_SUMMARY);
		if (cell != null) { cell.setCellValue(summary); }

		cell = worksheet.cell(ADDR_AUTHOR);
		if (cell != null) { cell.setCellValue(author); }

		cell = worksheet.cell(ADDR_JIRA);
		if (cell != null) { cell.setCellValue(jira); }

		cell = worksheet.cell(ADDR_ZEPHYR);
		if (cell != null) { cell.setCellValue(zephyr); }

		cell = worksheet.cell(ADDR_NOTIFICATION);
		if (cell != null) {
			StringBuilder buffer = new StringBuilder();
			notifications.forEach(notification -> buffer.append(notification).append(lineSeparator()));
			cell.setCellValue(StringUtils.trim(buffer.toString()));
		}

		worksheet.save();
	}

	private void parse() {
		XSSFCell cell = worksheet.cell(ADDR_SUMMARY);
		if (cell != null) { summary = cell.getStringCellValue(); }

		cell = worksheet.cell(ADDR_AUTHOR);
		if (cell != null) { author = cell.getStringCellValue(); }

		cell = worksheet.cell(ADDR_JIRA);
		if (cell != null) { jira = cell.getStringCellValue(); }

		cell = worksheet.cell(ADDR_ZEPHYR);
		if (cell != null) { zephyr = cell.getStringCellValue(); }

		cell = worksheet.cell(ADDR_NOTIFICATION);
		if (cell != null) {
			String value = cell.getStringCellValue();
			if (StringUtils.isNotBlank(value)) {
				value = StringUtils.replace(value, "\r", "\n");
				String[] array = StringUtils.split(value, "\n");
				if (ArrayUtils.isNotEmpty(array)) {
					notifications = new ArrayList<>();
					Arrays.stream(array).forEach(email -> {
						if (StringUtils.isNotBlank(email)) {
							notifications.add(StringUtils.removeEnd(StringUtils.trim(email), ","));
						}
					});
				}
			}
		}

	}

}
