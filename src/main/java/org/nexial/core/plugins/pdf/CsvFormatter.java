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

class CsvFormatter extends TableFormatter<String> {
    private String delimiter;
    private String lineSeparator;

    public String getDelimiter() { return delimiter; }

    public void setDelimiter(String delimiter) { this.delimiter = delimiter; }

    public String getLineSeparator() { return lineSeparator; }

    public void setLineSeparator(String lineSeparator) { this.lineSeparator = lineSeparator; }

    public String format(Table table) {
        StringBuilder buffer = new StringBuilder();

        int columnsCount = table.getColumnsCount();
        List<Row> rows = table.getRows();
        for (Row row : rows) {
            buffer.append(lineSeparator);

            int cellIdx = 0;    //pointer of row.cells
            int columnIdx = 0;  //pointer of columns
            while (columnIdx < columnsCount) {
                if (cellIdx < row.getCells().size()) {
                    Cell cell = row.getCells().get(cellIdx);
                    if (cell.getIdx() == columnIdx) {
                        if (cell.getIdx() != 0) { buffer.append(delimiter); }
                        buffer.append(cell.getContent());
                        cellIdx++;
                        columnIdx++;
                    } else if (columnIdx < cellIdx) {
                        if (columnIdx != 0) { buffer.append(delimiter); }
                        columnIdx++;
                    } else {
                        throw new RuntimeException("Invalid state");
                    }
                } else {
                    if (columnIdx != 0) { buffer.append(delimiter); }
                    columnIdx++;
                }
            }
        }

        return buffer.toString();
    }
}
