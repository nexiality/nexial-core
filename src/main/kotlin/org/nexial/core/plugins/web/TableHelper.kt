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
 */

package org.nexial.core.plugins.web

import com.univocity.parsers.csv.CsvFormat
import com.univocity.parsers.csv.CsvWriter
import com.univocity.parsers.csv.CsvWriterSettings
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.DEF_FILE_ENCODING
import org.nexial.core.model.StepResult
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.CheckUtils.requiresPositiveNumber
import org.nexial.core.utils.ConsoleUtils
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import java.io.File
import javax.validation.constraints.NotNull

class TableHelper(private val webCommand: WebCommand) {
    private val tableHeaderLocators = listOf(".//thead//*[ name() = 'th' or name() = 'td' ]",
                                             ".//thead//*[ name() = 'TH' or name() = 'TD' ]",
                                             ".//tr/th")

    fun saveDivsAsCsv(headerCellsLoc: String,
                      rowLocator: String,
                      cellLocator: String,
                      nextPageLocator: String,
                      file: String): StepResult {

        requiresNotBlank(rowLocator, "invalid rowLocator", rowLocator)
        requiresNotBlank(cellLocator, "invalid cellLocator", cellLocator)
        requiresNotBlank(file, "invalid file", file)

        val writer = newCsvWriter(file)

        val msgPrefix = "DIV table "

        // header
        if (StringUtils.isNotBlank(headerCellsLoc) && !webCommand.context.isNullValue(headerCellsLoc)) {
            writeCsvHeader(msgPrefix, writer, webCommand.findElements(headerCellsLoc))
        }

        var pageCount = 0
        var firstRow = ""

        while (true) {
            // data rows and cells
            val rows = webCommand.findElements(rowLocator)
            if (rows == null || CollectionUtils.isEmpty(rows)) {
                if (pageCount < 1) ConsoleUtils.log("$msgPrefix does not contain usable data cells")
                break
            }

            ConsoleUtils.log(msgPrefix + " collecting data for page " + (pageCount + 1))
            var hasData = true

            for (i in rows.indices) {
                val cellContent = toCellContent(rows[i], cellLocator)
                if (CollectionUtils.isEmpty(cellContent)) {
                    writer.writeEmptyRow()
                    break
                }

                if (i == 0) {
                    if (pageCount > 0 && StringUtils.equals(firstRow, cellContent.toString())) {
                        // found duplicate... maybe we have reached the end?
                        ConsoleUtils.log("$msgPrefix reached the end of records")
                        hasData = false
                        break
                    }

                    // mark first row for comparison against subsequent pages
                    firstRow = cellContent.toString()
                }

                writer.writeRow(cellContent)
            }

            if (!hasData) break

            pageCount++

            if (!clickNextPage(nextPageLocator)) break
        }

        writer.flush()
        writer.close()

        return StepResult.success("$msgPrefix scanned and written to $file")
    }

    fun saveTableAsCsv(locator: String, nextPageLocator: String, file: String): StepResult {
        requiresNotBlank(file, "Invalid file", file)

        // exception thrown if locator doesn't resolve to element
        val table = webCommand.toElement(locator)

        val writer = newCsvWriter(file)

        val msgPrefix = "Table '$locator'"

        var headers: List<WebElement> = ArrayList()
        tableHeaderLocators.forEach(fun(locator: String?) {
            run {
                if (CollectionUtils.isEmpty(headers)) headers = table.findElements(By.xpath(locator))
            }
        })

        writeCsvHeader(msgPrefix, writer, headers)

        var pageCount = 0
        var firstRow = ""

        while (true) {
            // table has body?
            var rows: List<WebElement> = table.findElements(By.xpath(".//tbody/tr"))
            // table has rows not trapped within tbody?
            if (CollectionUtils.isEmpty(rows)) rows = table.findElements(By.xpath(".//tr/*[ .[name() = 'TD'] ]"))
            if (CollectionUtils.isEmpty(rows)) {
                if (pageCount < 1) ConsoleUtils.log("$msgPrefix does not contain usable data cells")
                break
            }

            ConsoleUtils.log(msgPrefix + " collecting data for page " + (pageCount + 1))
            var hasData = true

            for (i in rows.indices) {
                val cellContent = toCellContent(rows[i], "tag=td")
                if (CollectionUtils.isEmpty(cellContent)) {
                    writer.writeEmptyRow()
                    break
                }

                if (i == 0) {
                    // compare the first row of every page after the 1st page
                    if (pageCount > 0 && StringUtils.equals(firstRow, cellContent.toString())) {
                        // found duplicate... maybe we have reached the end?
                        ConsoleUtils.log("$msgPrefix reached the end of records.")
                        hasData = false
                        break
                    }

                    // mark first row for comparison against next page
                    firstRow = cellContent.toString()
                }

                writer.writeRow(cellContent)
            }

            if (!hasData) break

            pageCount++

            if (!clickNextPage(nextPageLocator)) break
        }

        // table has footer via tfoot? we are ignoring it...

        // write to target file
        writer.flush()
        writer.close()

        val msg = "$msgPrefix scanned and written to $file"
        val targetFile = File(file)
        val outputPath = targetFile.absolutePath
        webCommand.addLinkRef(msg, targetFile.name, outputPath)
        return StepResult.success(msg)
    }

    fun assertTable(locator: String, row: String, column: String, text: String): StepResult {
        requiresPositiveNumber(row, "invalid row number", row)
        requiresPositiveNumber(column, "invalid column number", column)

        val table = webCommand.toElement(locator)

        val cell: WebElement = table.findElement(By.xpath(".//tr[$row]/td[$column]"))
                               ?: return StepResult.fail("EXPECTED cell at Row $row Column $column of " +
                                                         "table '$locator' NOT found")

        val actual = cell.text
        if (StringUtils.isBlank(text)) {
            return if (StringUtils.isBlank(actual)) {
                StepResult.success("found empty value in table '$locator'")
            } else {
                StepResult.fail("EXPECTED empty value but found '$actual' instead.")
            }
        }

        if (StringUtils.isBlank(actual)) return StepResult.fail("EXPECTED '$text' but found empty value instead.")

        val msgPrefix = "EXPECTED '$text' in table '$locator'"
        return if (StringUtils.equals(text, actual)) {
            StepResult.success(msgPrefix)
        } else {
            StepResult.fail("$msgPrefix but found '$actual' instead.")
        }
    }

    @NotNull
    private fun toCellContent(row: WebElement, cellLocator: String): List<String> {
        val cells: List<WebElement> = row.findElements(webCommand.locatorHelper.findBy(cellLocator))

        val cellContent = ArrayList<String>()
        if (CollectionUtils.isEmpty(cells)) return cellContent

        cells.forEach { cell -> cellContent.add(cell.text) }
        return cellContent
    }

    private fun writeCsvHeader(msgPrefix: String, writer: CsvWriter, headers: List<WebElement>?) {
        if (headers == null || CollectionUtils.isEmpty(headers)) {
            ConsoleUtils.log("$msgPrefix does not contain usable headers")
        } else {
            val headerNames = ArrayList<String>()
            headers.forEach { header -> headerNames.add(header.text) }
            ConsoleUtils.log("$msgPrefix - collected headers: $headerNames")
            writer.writeHeaders(headerNames)
        }
    }

    @NotNull
    private fun newCsvWriter(file: String): CsvWriter {
        val format = CsvFormat()
        format.delimiter = webCommand.context.textDelim[0]
        format.setLineSeparator("\n")
        val settings = CsvWriterSettings()
        settings.format = format
        return CsvWriter(File(file), DEF_FILE_ENCODING, settings)
    }

    private fun clickNextPage(nextPageLocator: String): Boolean {
        return if (StringUtils.isNotBlank(nextPageLocator) && !webCommand.context.isNullValue(nextPageLocator)) {
            val nextPage = webCommand.findElement(nextPageLocator)
            if (nextPage != null && nextPage.isDisplayed && nextPage.isEnabled) {
                nextPage.click()
                true
            } else {
                false
            }
        } else {
            false
        }
    }
}
