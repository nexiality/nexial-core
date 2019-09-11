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
import org.apache.commons.collections4.map.ListOrderedMap
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.ResourceUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.DEF_FILE_ENCODING
import org.nexial.core.NexialConst.Data.SaveGridAsCSV.*
import org.nexial.core.SystemVariables.getDefault
import org.nexial.core.SystemVariables.getDefaultBool
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.StepResult
import org.nexial.core.utils.CheckUtils.*
import org.nexial.core.utils.ConsoleUtils
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.Select
import java.io.File
import java.util.*
import javax.validation.constraints.NotNull
import kotlin.collections.ArrayList

class TableHelper(private val webCommand: WebCommand) {
    private val tableHeaderLocators = listOf("./thead//*[ name()='th' or name()='td' ]",
                                             "./thead//*[ name()='TH' or name()='TD' ]",
                                             "./tr/th")
    private val formElementLocator = ".//*[" +
                                     " name()='input' or" +
                                     " name()='submit' or" +
                                     " name()='button' or" +
                                     " name()='textarea' or" +
                                     " name()='select' or" +
                                     " name()='img'" +
                                     "]"
    private val csvSafeReplacement = ListOrderedMap.listOrderedMap(mapOf(" \n " to " ",
                                                                         " \r\n " to " ",
                                                                         " \r " to " ",
                                                                         " \t " to " ",
                                                                         " \n" to " ",
                                                                         " \r\n" to " ",
                                                                         " \r" to " ",
                                                                         " \t" to " ",
                                                                         "\n " to " ",
                                                                         "\r\n " to " ",
                                                                         "\r " to " ",
                                                                         "\t " to " "))
    private val gridDataMeta = ResourceUtils.loadResource("/org/nexial/core/plugins/web/GridDataMeta.js")
    private val collectInfiniteGrid = ResourceUtils.loadResource("/org/nexial/core/plugins/web/CollectInfiniteGrid.js")
    private val metaRecSep = "#$#"

    fun saveDivsAsCsv(headerCellsLoc: String,
                      rowLocator: String,
                      cellLocator: String,
                      nextPageLocator: String,
                      file: String): StepResult {
        requiresNotBlank(rowLocator, "invalid rowLocator", rowLocator)
        requiresNotBlank(cellLocator, "invalid cellLocator", cellLocator)
        requiresNotBlank(file, "invalid file", file)

        val writer = newCsvWriter(file)

        val msgPrefix = "DIV table"

        // header
        if (!webCommand.context.isNullOrEmptyOrBlankValue(headerCellsLoc)) {
            val deepScan = webCommand.context.getBooleanData(DEEP_SCAN, getDefaultBool(DEEP_SCAN))
            writeCsvHeader(msgPrefix, writer, webCommand.findElements(headerCellsLoc), deepScan)
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

            ConsoleUtils.log("$msgPrefix collecting data for page ${pageCount + 1}")
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
                if (CollectionUtils.isEmpty(headers))
                    headers = table.findElements(webCommand.locatorHelper.findBy(locator, true))
            }
        })

        val deepScan = webCommand.context.getBooleanData(DEEP_SCAN, getDefaultBool(DEEP_SCAN))
        writeCsvHeader(msgPrefix, writer, headers, deepScan)

        var pageCount = 0
        var firstRow = ""

        while (true) {
            // table has body?
            var rows: List<WebElement> = table.findElements(By.xpath(".//tbody/tr"))

            // table has rows not trapped within tbody?
            // but we are not considering TH here because we are not under TBODY. The TH in Table might be header
            if (CollectionUtils.isEmpty(rows))
                rows = table.findElements(By.xpath(".//tr/*[name()='TD' or name()='td']"))
            if (CollectionUtils.isEmpty(rows)) {
                if (pageCount < 1) ConsoleUtils.log("$msgPrefix does not contain usable data cells")
                break
            }

            ConsoleUtils.log(msgPrefix + " collecting data for page " + (pageCount + 1))
            var hasData = true

            for (i in rows.indices) {
                // ConsoleUtils.log("$msgPrefix scanning row $i...")

                // cell can be TD or TH under TBODY
                val cells = toCellContent(rows[i], "./*[name()='TD' or name()='td' or name()='TH' or name()='th']")
                if (CollectionUtils.isEmpty(cells)) {
                    writer.writeEmptyRow()
                    break
                }

                if (i == 0) {
                    // compare the first row of every page after the 1st page
                    if (pageCount > 0 && StringUtils.equals(firstRow, cells.toString())) {
                        // found duplicate... maybe we have reached the end?
                        ConsoleUtils.log("$msgPrefix reached the end of records.")
                        hasData = false
                        break
                    }

                    // mark first row for comparison against next page
                    firstRow = cells.toString()
                }

                writer.writeRow(cells)
            }

            if (!hasData) break

            pageCount++

            if (!clickNextPage(nextPageLocator)) break
        }

        // table has footer via tfoot? we are ignoring it...

        // write to target file
        writer.flush()
        writer.close()

        val targetFile = File(file)
        if (webCommand.context.getBooleanData(END_TRIM, getDefaultBool(END_TRIM))) {
            ConsoleUtils.log("$msgPrefix trimming off end-of-file line feed...")
            FileUtil.removeFileEndLineFeed(targetFile)
        }

        return StepResult.success("$msgPrefix scanned and written to $file")
    }

    /**
     * `config` contains:
     * - data-viewport: locator to the scrolling viewport
     * - data-row-xpath: relative XPATH to each row from its parent grid
     * - data-cell-xpath: relative XPATH to each cell from its parent row
     * - header-cell: locator to identify individual header cells
     * - limit (optional): maximum number of rows to collect. -1 means unlimited
     * - waitBetweenScroll (optional): milliseconds to wait between each scroll action
     * - nexial.web.saveGrid.deepScan (optional): override existing setting in context
     * - nexial.web.saveGrid.header.input (optional): override existing setting in context
     * - nexial.web.saveGrid.header.image (optional): override existing setting in context
     * - nexial.web.saveGrid.data.input (optional): override existing setting in context
     * - nexial.web.saveGrid.data.image (optional): override existing setting in context
     * - nexial.web.saveGrid.end.trim (optional): override existing setting in context
     *
     * @param config String
     * @param file String
     * @return StepResult
     */
    fun saveInfiniteDivsAsCsv(config: String, file: String): StepResult {
        requiresNotBlank(config, "Invalid config", config)
        requiresNotBlank(file, "Invalid file", file)

        val configMap = TextUtils.toMap(StringUtils.remove(config, "\r"), "\n", "=")

        // check required configs
        requires(configMap.isNotEmpty(), "No valid config found", config)
        requires(configMap.containsKey("data-viewport"), "Invalid config; data-viewport not found", config)
        requires(configMap.containsKey("data-row-xpath"), "Invalid config; data-row not found", config)
        requires(configMap.containsKey("data-cell-xpath"), "Invalid config; data-cell not found", config)

        val msgPrefix = "Infinite Scrolling DIV"
        val writer = newCsvWriter(file)
        val context = webCommand.context
        val jsExec = webCommand.jsExecutor
        val deepScan = resolveConfigBoolean(configMap, context, DEEP_SCAN)

        // header (not required)
        val headerCellsLoc = configMap["header-cell"]
        if (!context.isNullOrEmptyOrBlankValue(headerCellsLoc))
            writeCsvHeader(msgPrefix, writer, webCommand.findElements(headerCellsLoc), deepScan)

        // viewport
        val viewportLoc = configMap["data-viewport"]
        val viewport = webCommand.findElements(viewportLoc).firstOrNull()
                       ?: return StepResult.fail("Unable to resolve data viewport via '$viewportLoc'")

        // reusable configs
        val rowLocator = configMap["data-row-xpath"]
        val cellLocator = configMap["data-cell-xpath"]
        val limit = (configMap["limit"] ?: "-1").toInt()
        val waitBetweenScroll = (configMap["waitBetweenScroll"] ?: "600").toInt()

        ConsoleUtils.log("$msgPrefix collecting data")
        jsExec.executeScript(collectInfiniteGrid, viewport, rowLocator, cellLocator, deepScan, limit, waitBetweenScroll)

        // wait for script to finish
        var collectionInProgress: Any = "true"
        while (Objects.toString(collectionInProgress) == "true") {
            Thread.sleep(500)
            collectionInProgress = jsExec.executeScript("return collectionInProgress;")
        }

        val gridData = jsExec.executeScript("return collectionResults;")
                       ?: return StepResult.fail("Unable to retrieve any grid data")

        val gridDataList = mutableListOf<List<Map<String, String>>>()

        val gridDataMap: Map<*, *> = gridData as Map<*, *>
        if (gridDataMap.isEmpty() || !gridDataMap.containsKey("data") || gridDataMap["data"] !is List<*>)
            return StepResult.fail("No grid data found")

        val gridDataArray = gridDataMap["data"] as List<*>
        gridDataArray.forEach { rowData ->
            if (rowData is List<*>) gridDataList.add(rowData.map { it as Map<String, String> }.toList())
        }

        val dataImage = resolveConfig(configMap, context, DATA_IMAGE)
        val dataInput = resolveConfig(configMap, context, DATA_INPUT)
        var rowCount = 0

        gridDataList.forEach {
            val cellContent = ArrayList<String>()
            it.forEach { row ->
                run {
                    val cellText = row["text"] ?: ""
                    if (!deepScan) cellContent.add(csvSafe(cellText))
                    else if (StringUtils.isNotEmpty(cellText) &&
                             (!StringUtils.contains(cellText, "\n") || row["tag"] != "select"))
                        cellContent.add(csvSafe(cellText))
                    else {
                        if (row["tag"] == "img") {
                            cellContent.add(extractImageData(row, ImageOptions.valueOf(dataImage)))
                        } else {
                            cellContent.add(extractInputData(context, row, InputOptions.valueOf(dataInput)))
                        }
                    }
                }
            }

            if (cellContent.isEmpty())
                writer.writeEmptyRow()
            else {
                rowCount++
                writer.writeRow(cellContent)
            }
        }

        writer.flush()
        writer.close()

        val targetFile = File(file)
        if (resolveConfigBoolean(configMap, context, END_TRIM)) {
            ConsoleUtils.log("$msgPrefix trimming off end-of-file line feed...")
            FileUtil.removeFileEndLineFeed(targetFile)
        }

        return StepResult.success("$msgPrefix $rowCount scanned and written to $file")
    }

    /**
     * assume the conventional <pre>&lt;TABLE>, &lt;TR>, &lt;TD></pre> constructs.
     *
     * @param config String
     * @param file String
     * @return StepResult
     * @see #saveInfiniteDivsAsCsv
     */
    fun saveInfiniteTableAsCsv(config: String, file: String): StepResult = saveInfiniteDivsAsCsv(config, file)

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

    private fun writeCsvHeader(msgPrefix: String, writer: CsvWriter, headers: List<WebElement>?, deepScan: Boolean) {
        if (headers == null || CollectionUtils.isEmpty(headers)) {
            ConsoleUtils.log("$msgPrefix does not contain usable headers")
        } else {
            val headerNames = ArrayList<String>()
            headers.forEach { header ->
                webCommand.scrollIntoView(header)
                if (header.isDisplayed) headerNames.add(if (deepScan) deepScan(header, true) else csvSafe(header.text))
            }
            ConsoleUtils.log("$msgPrefix - collected headers: $headerNames")
            writer.writeHeaders(headerNames)
        }
    }

    @NotNull
    private fun toCellContent(row: WebElement, cellLocator: String): List<String> {
        val cells: List<WebElement> = row.findElements(webCommand.locatorHelper.findBy(cellLocator, true))

        val cellContent = ArrayList<String>()
        if (CollectionUtils.isEmpty(cells)) return cellContent

        val deepScan = webCommand.context.getBooleanData(DEEP_SCAN, getDefaultBool(DEEP_SCAN))

        cells.forEach { cell ->
            run {
                webCommand.scrollIntoView(cell)
                if (cell.isDisplayed) cellContent.add(if (deepScan) deepScan(cell, false) else csvSafe(cell.text))
            }
        }
        return cellContent
    }

    /**
     * determine the text for checkbox, radio button, image, button, select, text box, textarea, etc.
     *
     * Note that this method only checks for 1 element type.
     * @param cell WebElement
     * @return String
     */
    private fun deepScan(cell: WebElement, isHeader: Boolean): String {
        val cellText = cell.text

        // no newline means the cell probably doesn't contain <SELECT> or <TEXTAREA>
        if (StringUtils.isNotEmpty(cellText) && !StringUtils.contains(cellText, "\n")) return csvSafe(cellText)

        // cover cases for checkbox, radio, submit, button, text box, password, email, upload, input-image, text area, select
        val inputs = cell.findElements(By.xpath(formElementLocator))
        if (inputs.isEmpty()) return csvSafe(cellText)

        val jsExec = webCommand.jsExecutor
        val context = webCommand.context
        val headerImage = context.getStringData(HEADER_IMAGE, getDefault(HEADER_IMAGE))
        val dataImage = context.getStringData(DATA_IMAGE, getDefault(DATA_IMAGE))
        val headerInput = context.getStringData(HEADER_INPUT, getDefault(HEADER_INPUT))
        val dataInput = context.getStringData(DATA_INPUT, getDefault(DATA_INPUT))

        val metaMap = jsElementMeta(jsExec, gridDataMeta, inputs[0])
        // <SELECT> element will exhibit newline in it's text representation. So if we are not dealing with
        // <SELECT> then `cellText` should be returned as this point
        if (metaMap.isEmpty() || (StringUtils.isNotEmpty(cellText) && metaMap["tag"] != "select"))
            return csvSafe(cellText)

        return csvSafe(
                if (metaMap["tag"] == "img") {
                    extractImageData(metaMap, ImageOptions.valueOf(if (isHeader) headerImage else dataImage))
                } else {
                    extractInputData(context, metaMap, InputOptions.valueOf(if (isHeader) headerInput else dataInput))
                })
    }

    private fun extractImageData(metaMap: Map<String, String>, imageOption: ImageOptions) =
            when (imageOption) {
                ImageOptions.filename -> {
                    val src = metaMap["src"] ?: ""
                    if (StringUtils.contains(src, "/")) StringUtils.substringAfterLast(src, "/") else src
                }

                ImageOptions.type     -> "image"
                else                  -> StringUtils.defaultString(metaMap[imageOption.toString()])
            }

    private fun extractInputData(context: ExecutionContext, row: Map<String, String>, dataOption: InputOptions) =
            if (row["tag"] == "select")
                if (dataOption == InputOptions.state || dataOption == InputOptions.value)
                    TextUtils.toString(StringUtils.split(row["selected"], "\n"), context.textDelim, "", "")
                else
                    StringUtils.defaultString(row[dataOption.toString()])
            else if (dataOption == InputOptions.state)
                when (row["type"]) {
                    "checkbox" -> if (StringUtils.equals(Objects.toString(row["checked"]),
                                                         "true")) "checked" else "unchecked"
                    "radio"    -> if (StringUtils.equals(Objects.toString(row["checked"]),
                                                         "true")) "selected" else "unselected"
                    else       -> StringUtils.defaultString(row["value"])
                }
            else
                StringUtils.defaultString(row[dataOption.toString()])

    private fun resolveConfigBoolean(configMap: Map<String, String>, context: ExecutionContext, key: String) =
            if (configMap.containsKey(key)) {
                BooleanUtils.toBoolean(configMap[key])
            } else {
                context.getBooleanData(key, getDefaultBool(key))
            }

    private fun resolveConfig(configMap: Map<String, String>, context: ExecutionContext, key: String) =
            configMap[key] ?: context.getStringData(key, getDefault(key))

    private fun jsElementMeta(jsExec: JavascriptExecutor, script: String, element: WebElement): Map<String, String> =
            TextUtils.toMap(Objects.toString(jsExec.executeScript(script, element, metaRecSep), ""), metaRecSep, "=")

    /**
     * determine the text for checkbox, radio button, image, button, select, text box, textarea, etc.
     *
     * Note that this method only checks for 1 element type.
     * @param cell WebElement
     * @return String
     */
    private fun resolveDisplayText(cell: WebElement, isHeader: Boolean): String {
        val cellText = cell.text

        // no newline means the cell probably doesn't contain <SELECT>
        if (!StringUtils.contains(cellText, "\n") && StringUtils.isNotEmpty(cellText)) return csvSafe(cellText)

        // now we're not sure.. maybe we need to capture data from <SELECT>
        val context = webCommand.context

        val dataOption = InputOptions.valueOf(
                if (isHeader) context.getStringData(HEADER_INPUT, getDefault(HEADER_INPUT))
                else context.getStringData(DATA_INPUT, getDefault(DATA_INPUT)))

        val selects = cell.findElements(By.xpath(".//select"))
        if (selects.isNotEmpty()) {
            // yep... definitely has <SELECT>
            val firstElement = selects[0]
            return if (firstElement.isDisplayed)
                if (dataOption == InputOptions.state || dataOption == InputOptions.value)
                    Select(firstElement).allSelectedOptions.joinToString(separator = context.textDelim,
                                                                         transform = { it.text })
                else StringUtils.defaultString(firstElement.getAttribute(dataOption.toString()))
            else ""
        } else if (StringUtils.isNotEmpty(cellText)) return csvSafe(cellText)
        // nope, we don't have <SELECT>

        // cover cases for checkbox, radio, submit, button, text box, password, email, upload, input-image
        val locatorInputs = ".//*[ name()='input' or name()='submit' or name()='button' or name()='textarea' ]"
        val inputs = cell.findElements(By.xpath(locatorInputs))
        if (inputs.isNotEmpty()) {
            val firstElement = inputs[0]
            return if (firstElement.isDisplayed)
                if (dataOption == InputOptions.state)
                    when (firstElement.getAttribute("type")) {
                        "checkbox" -> if (StringUtils.equals(firstElement.getAttribute("checked"),
                                                             "true")) "checked" else "unchecked"
                        "radio"    -> if (StringUtils.equals(firstElement.getAttribute("checked"),
                                                             "true")) "selected" else "unselected"
                        else       -> StringUtils.defaultString(firstElement.getAttribute("value"))
                    }
                else StringUtils.defaultString(firstElement.getAttribute(dataOption.toString()))
            else ""
        }

        val images = cell.findElements(By.xpath(".//img"))
        if (images.isNotEmpty()) {
            val imageOption = ImageOptions.valueOf(
                    if (isHeader) context.getStringData(HEADER_IMAGE, getDefault(HEADER_IMAGE))
                    else context.getStringData(DATA_IMAGE, getDefault(DATA_IMAGE)))
            val firstElement = images[0]
            return if (firstElement.isDisplayed)
                if (imageOption == ImageOptions.filename) {
                    val src = firstElement.getAttribute("src")
                    if (StringUtils.contains(src, "/")) StringUtils.substringAfterLast(src, "/") else src
                } else if (imageOption == ImageOptions.type)
                    "image"
                else
                    StringUtils.defaultString(firstElement.getAttribute(imageOption.toString()))
            else ""
        }

        // finally / give up
        return ""
    }

    @NotNull
    internal fun csvSafe(text: String): String {
        var safe = text
        csvSafeReplacement.forEach { (search, replace) -> safe = StringUtils.replace(safe, search, replace) }
        safe = StringUtils.replace(safe, "\r", "")
        safe = StringUtils.replace(safe, "\n", " ")
        safe = StringUtils.replace(safe, "\t", " ")

        val trim = webCommand.context.getBooleanData(DATA_TRIM, getDefaultBool(DATA_TRIM))
        if (trim) safe = StringUtils.trim(safe)

        return safe
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
        return if (webCommand.context.isNullOrEmptyOrBlankValue(nextPageLocator)) false else {
            val nextPage = webCommand.findElement(nextPageLocator)
            if (nextPage == null || !nextPage.isDisplayed || !nextPage.isEnabled) false else {
                nextPage.click()
                true
            }
        }
    }
}
