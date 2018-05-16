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

import java.util.List;

import org.nexial.core.plugins.pdf.Table.Cell;
import org.nexial.core.plugins.pdf.Table.Row;

class HtmlFormatter extends TableFormatter<String> {

    public String format(Table table) {
        int columnsCount = table.getColumnsCount();
        List<Row> rows = table.getRows();

        StringBuilder buffer = new StringBuilder();
        buffer.append("<!DOCTYPE html><html><head><meta charset='utf-8'></head>")
              .append("<body>")
              .append("<table border='1'>");

        for (Row row : rows) {
            buffer.append("<tr>");

            int cellIdx = 0;    //pointer of row.cells
            int columnIdx = 0;  //pointer of columns
            while (columnIdx < columnsCount) {
                if (cellIdx < row.getCells().size()) {
                    Cell cell = row.getCells().get(cellIdx);
                    if (cell.getIdx() == columnIdx) {
                        buffer.append("<td>").append(cell.getContent()).append("</td>");
                        cellIdx++;
                        columnIdx++;
                    } else if (columnIdx < cellIdx) {
                        buffer.append("<td></td>");
                        columnIdx++;
                    } else {
                        throw new RuntimeException("Invalid state");
                    }
                } else {
                    buffer.append("<td></td>");
                    columnIdx++;
                }
            }

            buffer.append("</tr>");
        }

        buffer.append("</table>")
              .append("</body>")
              .append("</html>");

        return buffer.toString();
    }
}
