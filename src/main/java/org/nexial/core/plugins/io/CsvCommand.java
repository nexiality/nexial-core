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

package org.nexial.core.plugins.io;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import org.nexial.commons.utils.TextUtils;
import org.nexial.core.IntegrationConfigException;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.OutputFileUtils;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import static org.nexial.core.plugins.io.IoCommand.CompareMode.FAIL_FAST;
import static org.nexial.core.plugins.io.IoCommand.CompareMode.THOROUGH;
import static org.nexial.core.utils.CheckUtils.*;

public class CsvCommand extends IoCommand {
    private ExcelHelper excelHelper;

    @Override
    public String getTarget() { return "csv"; }

    /**
     * both {@code expected} and {@code actual} can be file or content. {@code failFast} should be "true" or "false".
     */
    public StepResult compare(String expected, String actual, String failFast) {
        requiresNotBlank(expected, "expected is empty/blank");
        requiresNotBlank(actual, "actual is empty/blank");
        return compare(expected,
                       actual,
                       !StringUtils.isBlank(failFast) && BooleanUtils.toBoolean(failFast) ? FAIL_FAST : THOROUGH,
                       null);
    }

    @Override
    public void init(ExecutionContext context) {
        super.init(context);
        if (excelHelper == null) { excelHelper = new ExcelHelper(context); }
    }

    public StepResult compareExtended(String var, String profile, String expected, String actual) {
        requiresValidVariableName(var);
        requiresNotBlank(profile, "Missing profile for compareExtended");

        // expected can either be a file or content
        String expectedContent;
        try {
            expectedContent = OutputFileUtils.resolveContent(expected, context, false);
        } catch (IOException e) {
            return StepResult.fail("Unable to retrieve content from " + expected + ": " + e.getMessage());
        }
        requiresNotBlank(expectedContent, "No content found for expected", expected);

        // actual can either be a file or content
        String actualContent;
        try {
            actualContent = OutputFileUtils.resolveContent(actual, context, false);
        } catch (IOException e) {
            return StepResult.fail("Unable to retrieve content from " + actual + ": " + e.getMessage());
        }
        requiresNotBlank(actualContent, "No content found for actual", actual);

        String configKey = profile + ".compareExt.";

        CsvExtendedComparison comparison = new CsvExtendedComparison();
        comparison.setExpectedContent(expectedContent);
        String textDelim = context.getTextDelim();
        comparison.setExpectedIdentityColumns(
            TextUtils.toList(context.getStringData(configKey + "expected.identity"), textDelim, true));

        comparison.setActualContent(actualContent);
        comparison.setActualIdentityColumns(
            TextUtils.toList(context.getStringData(configKey + "actual.identity"), textDelim, true));

        comparison.setDisplayFields(
            TextUtils.toList(context.getStringData(configKey + "output.display"), textDelim, true));

        comparison.setMismatchedField(context.getStringData(configKey + "output.MISMATCHED"));
        comparison.setExpectedField(context.getStringData(configKey + "output.EXPECTED"));
        comparison.setActualField(context.getStringData(configKey + "output.ACTUAL"));

        Map<String, String> mapping = context.getDataByPrefix(configKey + "match.");
        if (MapUtils.isEmpty(mapping)) {
            ConsoleUtils.log("No mapping found; ASSUME THE EXACT SAME COLUMNS FOR EXPECTED AND ACTUAL");
        } else {
            comparison.setFieldMapping(mapping);
        }

        if (context.hasData(configKey + "identity.delim")) {
            comparison.setIdentSeparator(context.getStringData(configKey + "identity.delim"));
        }

        try {
            CsvComparisonResult result = comparison.compare();
            if (result == null) { return StepResult.fail("Unable to complete comparision"); }
            context.setData(var, result);
            return StepResult.success("comparison complete, result saved as '" + var + "'");
        } catch (IOException | IntegrationConfigException e) {
            return StepResult.fail("Error when performing comparison: " + e.getMessage(), e);
        }
    }

    public StepResult convertExcel(String excel, String worksheet, String csvFile) {
        requiresNotBlank(excel, "invalid excel file", excel);
        requiresNotBlank(worksheet, "invalid worksheet", worksheet);
        requiresNotBlank(csvFile, "invalid CSV destination", csvFile);

        File excelFile = new File(excel);
        requires(excelFile.canRead(), "unreadable excel file", excel);
        requires(excelFile.length() > 1, "excel file is empty", excel);

        return excelHelper.saveCsvToFile(excelFile, worksheet, csvFile);
    }

    public static CsvParser newCsvParser(String quote,
                                         String delim,
                                         String lineSeparator,
                                         boolean hasHeader,
                                         boolean keepQuote) {
        CsvParserSettings settings = toCsvParserSettings(delim, lineSeparator, hasHeader);

        settings.setQuoteDetectionEnabled(true);
        if (StringUtils.isNotEmpty(quote)) {
            settings.getFormat().setQuote(quote.charAt(0));
            settings.setKeepQuotes(keepQuote);
        }

        return new CsvParser(settings);

    }

    public static CsvParser newCsvParser(String quote, String delim, String lineSeparator, boolean hasHeader) {
        return newCsvParser(quote, delim, lineSeparator, hasHeader, true);
    }

    protected static CsvParserSettings toCsvParserSettings(String delim, String lineSeparator, boolean hasHeader) {
        /*
        withDelimiter(',')
        withQuote('"')
        withRecordSeparator("\r\n")
        withIgnoreEmptyLines(false)
        withAllowMissingColumnNames(true)
        */
        CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(hasHeader);
        settings.setSkipEmptyLines(false);
        settings.setEmptyValue("");
        settings.setNullValue("");
        // settings.setKeepEscapeSequences(true);
        // settings.setEscapeUnquotedValues(true);

        settings.setLineSeparatorDetectionEnabled(true);
        if (StringUtils.isNotEmpty(lineSeparator)) { settings.getFormat().setLineSeparator(lineSeparator); }

        if (StringUtils.isNotEmpty(delim)) {
            settings.getFormat().setDelimiter(delim.charAt(0));
        } else {
            settings.setDelimiterDetectionEnabled(true);
        }
        return settings;
    }

    protected List<String[]> parseAsCSV(File file) {
        CsvParser parser = newCsvParser(null, null, null, false);
        List<String[]> records = parser.parseAll(file);
        log("found " + records.size() + " line(s) from '" + file + "'");
        return records;
    }

    protected List<String[]> parseAsCSV(String content, boolean hasHeader) {
        CsvParser parser = newCsvParser(null, null, null, hasHeader);
        List<String[]> records = parser.parseAll(new StringReader(content));
        log("found " + records.size() + " line(s)");
        return records;
    }
}