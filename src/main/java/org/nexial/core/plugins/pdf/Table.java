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

package org.nexial.core.plugins.pdf;

import java.util.ArrayList;
import java.util.List;

public class Table {
	private final int pageIdx;
	private final List<Row> rows = new ArrayList<>();
	private final int columnsCount;

	public static class Row {
		private final int idx;
		private final List<Cell> cells = new ArrayList<>();

		public Row(int idx) { this.idx = idx; }

		public int getIdx() { return idx; }

		public List<Cell> getCells() { return cells; }

		@Override
		public String toString() {
			StringBuilder retVal = new StringBuilder();
			int lastCellIdx = 0;
			for (Cell cell : cells) {
				for (int idx2 = lastCellIdx; idx2 < cell.getIdx() - 1; idx2++) { retVal.append("|"); }
				if (cell.getIdx() > 0) { retVal.append("|"); }
				retVal.append(cell.getContent());
				lastCellIdx = cell.getIdx();
			}

			return retVal.toString();
		}
	}

	public static class Cell {
		private final String content;
		private final int idx;

		public Cell(int idx, String content) {
			this.idx = idx;
			this.content = content;
		}

		public String getContent() { return content; }

		public int getIdx() { return idx; }
	}

	public Table(int idx, int columnsCount) {
		this.pageIdx = idx;
		this.columnsCount = columnsCount;
	}

	public int getPageIdx() { return pageIdx; }

	public int getColumnsCount() { return columnsCount; }

	public List<Row> getRows() { return rows; }

	//public String toHtml() { return toString(true); }

	//@Override
	//public String toString() { return toString(false); }

	//private String toString(boolean inHtmlFormat) {
	//	StringBuilder retVal = new StringBuilder();
	//	if (inHtmlFormat) {
	//		retVal.append("<!DOCTYPE html><html><head><meta charset='utf-8'></head><body>")
	//		      .append("<table border='1'>");
	//	}
	//
	//	for (Row row : rows) {
	//		if (inHtmlFormat) {
	//			retVal.append("<tr>");
	//		} else if (retVal.length() > 0) {
	//			retVal.append("\n");
	//		}
	//
	//		int cellIdx = 0;//pointer of row.cells
	//		int columnIdx = 0;//pointer of columns
	//		while (columnIdx < columnsCount) {
	//			if (cellIdx < row.getCells().size()) {
	//				Cell cell = row.getCells().get(cellIdx);
	//				if (cell.getIdx() == columnIdx) {
	//					if (inHtmlFormat) {
	//						retVal.append("<td>").append(cell.getContent()).append("</td>");
	//					} else {
	//						if (cell.getIdx() != 0) { retVal.append(";"); }
	//						retVal.append(cell.getContent());
	//					}
	//					cellIdx++;
	//					columnIdx++;
	//				} else if (columnIdx < cellIdx) {
	//					if (inHtmlFormat) {
	//						retVal.append("<td>").append("</td>");
	//					} else if (columnIdx != 0) {
	//						retVal.append(";");
	//					}
	//					columnIdx++;
	//				} else {
	//					throw new RuntimeException("Invalid state");
	//				}
	//			} else {
	//				if (inHtmlFormat) {
	//					retVal.append("<td>").append("</td>");
	//				} else if (columnIdx != 0) {
	//					retVal.append(";");
	//				}
	//				columnIdx++;
	//			}
	//		}
	//
	//		if (inHtmlFormat) { retVal.append("</tr>"); }
	//	}
	//
	//	if (inHtmlFormat) { retVal.append("</table>").append("</body>").append("</html>"); }
	//
	//	return retVal.toString();
	//}
}
