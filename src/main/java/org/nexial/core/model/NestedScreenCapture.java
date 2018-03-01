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

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;

import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelConfig.StyleDecorator;

import static org.nexial.core.excel.ExcelConfig.COL_IDX_CAPTURE_SCREEN;
import static org.nexial.core.excel.ExcelConfig.MSG_SCREENCAPTURE;
import static org.nexial.core.excel.ExcelConfig.StyleConfig.SCREENSHOT;

/**
 * used to capture and render nested screen capture(s) post execution of a test step.
 *
 * @see NestedMessage
 */
public class NestedScreenCapture extends NestedMessage {
	private XSSFCellStyle cellStyle;
	private String link;
	private String label = MSG_SCREENCAPTURE;

	public NestedScreenCapture(Worksheet worksheet, String message, String link) {
		super(worksheet, message);
		// cell style must be associated with specific worksheet... CANNOT BE STATIC
		cellStyle = StyleDecorator.generate(worksheet, SCREENSHOT);
		this.link = link;
	}

	public NestedScreenCapture(Worksheet worksheet, String message, String link, String label) {
		this(worksheet, message, link);
		this.label = label;
	}

	public String getLink() { return link; }

	@Override
	public void printTo(XSSFRow row) {
		super.printTo(row);
		if (StringUtils.isNotBlank(link)) {
			XSSFCell cell = Excel.setHyperlink(row.createCell(COL_IDX_CAPTURE_SCREEN), link, label);
			cell.setCellStyle(cellStyle);
		}
	}
}
