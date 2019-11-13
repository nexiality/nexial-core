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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.IntegrationConfigException;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.OutputFileUtils;
import org.nexial.core.utils.OutputResolver;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import static org.nexial.core.plugins.io.CsvExtendedComparison.CSV_EXT_COMP_HEADER;
import static org.nexial.core.plugins.io.IoCommand.CompareMode.FAIL_FAST;
import static org.nexial.core.plugins.io.IoCommand.CompareMode.THOROUGH;
import static org.nexial.core.utils.CheckUtils.*;

public class CsvCommand extends IoCommand {
    private ExcelHelper excelHelper;

    @Override
    public String getTarget() { return "csv"; }

    @Override
    public void init(ExecutionContext context) {
        super.init(context);
        if (excelHelper == null) { excelHelper = new ExcelHelper(context); }
    }

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

    public StepResult compareExtended(String var, String profile, String expected, String actual) {
        requiresValidAndNotReadOnlyVariableName(var);
        requiresNotBlank(profile, "Missing profile for compareExtended");

        String configKey = profile + ".compareExt.";

        CsvExtendedComparison comparison = new CsvExtendedComparison();
        comparison.setDelimiter(context.getTextDelim());

        // expected can either be a file or content
        boolean expectedAsIs = BooleanUtils.toBoolean(collectConfig(configKey + "expected.readAsIs").orElse("false"));
        if (expectedAsIs) {
            File file = OutputResolver.resolveFile(expected);
            requiresNotNull(file, "Unable to resolve 'expected' as a file");
            ConsoleUtils.log(CSV_EXT_COMP_HEADER + "read from expected: " + file.length() + " bytes read");
            comparison.setExpectedFile(file);
        } else {
            try {
                String content = new OutputResolver(expected, context).getContent();
                requiresNotBlank(content, "No content found for expected", expected);
                ConsoleUtils.log(CSV_EXT_COMP_HEADER + "read from expected: " + content.length() + " bytes read");
                comparison.setExpectedField(content);
            } catch (Throwable e) {
                return StepResult.fail("Unable to retrieve content from " + expected + ": " + e.getMessage());
            }
        }

        // actual can either be a file or content
        boolean actualAsIs = BooleanUtils.toBoolean(collectConfig(configKey + "actual.readAsIs").orElse("false"));
        if (actualAsIs) {
            File file = OutputResolver.resolveFile(actual);
            ConsoleUtils.log(CSV_EXT_COMP_HEADER + "read from actual:   " + file.length() + " bytes read");
            comparison.setActualFile(file);
        } else {
            try {
                String content = new OutputResolver(actual, context).getContent();
                requiresNotBlank(content, "No content found for actual", actual);
                ConsoleUtils.log(CSV_EXT_COMP_HEADER + "read from actual:   " + content.length() + " bytes read");
                comparison.setActualContent(content);
            } catch (Throwable e) {
                return StepResult.fail("Unable to retrieve content from " + actual + ":   " + e.getMessage());
            }
        }

        // identity column(s) is used to identity the records on both expected and actual CSV file
        // this means that even if the content aren't matching line by line, we can use the identity column(s)
        // to "line them up".. good feature when the expected and actual content aren't sorted the same way.
        collectFields(configKey + "expected.identity").ifPresent(comparison::setExpectedIdentityColumns);
        collectFields(configKey + "actual.identity").ifPresent(comparison::setActualIdentityColumns);

        // define what fields to display in case mismatches are found.  The display fields provide additional
        // context to the mismatch event so that reader can correctly and easily associate the mismatched records.
        collectFields(configKey + "output.display").ifPresent(comparison::setDisplayFields);

        // labels of the mismatched information
        collectConfig(configKey + "output.MISMATCHED").ifPresent(comparison::setMismatchedField);
        collectConfig(configKey + "output.EXPECTED").ifPresent(comparison::setExpectedField);
        collectConfig(configKey + "output.ACTUAL").ifPresent(comparison::setActualField);

        // defines which field(s) to match on.  If none specified, then match on all fields.
        Map<String, String> mapping = context.getDataByPrefix(configKey + "match.");
        if (MapUtils.isEmpty(mapping)) {
            ConsoleUtils.log(CSV_EXT_COMP_HEADER + "No mapping found; ASSUME SAME COLUMNS FOR EXPECTED AND ACTUAL");
        } else {
            comparison.setFieldMapping(mapping);
        }

        // define the fields is ignore
        // this can be helpful when there are many fields to map between expected and actual -
        // instead of declaring the mapping of each and every field between expected and actual CSV file, one can
        // define just the few fields that are to be ignored.
        // the 'ignore' fields are assumed to be found on expected CSV.
        collectFields(configKey + "ignore").ifPresent(comparison::setIgnoreFields);
        collectFields(configKey + "matchAsNumber").ifPresent(comparison::setNumberFields);
        collectFields(configKey + "matchCaseInsensitive").ifPresent(comparison::setCaseInsensitiveFields);
        collectFields(configKey + "matchAutoTrim").ifPresent(comparison::setAutoTrimFields);

        // specifies the delimiter used for identity fields for BOTH the expected and actual CSV files
        // if not specified, then '^' is assumed.
        collectConfig(configKey + "identity.delim").ifPresent(comparison::setIdentSeparator);

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
        logDeprecated(getTarget() + " » convertExcel(excel,worksheet,csvFile)",
                      getTarget() + " » fromExcel(excel,worksheet,csvFile)");
        return fromExcel(excel, worksheet, csvFile);
    }

    public StepResult fromExcel(String excel, String worksheet, String csvFile) {
        requiresNotBlank(excel, "invalid excel file", excel);
        requiresNotBlank(worksheet, "invalid worksheet", worksheet);
        requiresNotBlank(csvFile, "invalid CSV destination", csvFile);

        File excelFile = new File(excel);
        requires(excelFile.canRead(), "unreadable excel file", excel);
        requires(excelFile.length() > 1, "excel file is empty", excel);

        return excelHelper.saveCsvToFile(excelFile, worksheet, csvFile);
    }

    public StepResult toExcel(String csvFile, String excel, String worksheet, String startCell) {
        requiresNotBlank(excel, "invalid excel file", excel);
        requiresNotBlank(worksheet, "invalid worksheet", worksheet);
        requiresNotBlank(csvFile, "invalid CSV destination", csvFile);

        try {
            String csvContent = OutputFileUtils.resolveContent(csvFile, context, false, true);
            if (StringUtils.isBlank(csvContent)) { return StepResult.fail("No content found in '" + csvFile + "'"); }

            // convert content to list-list-string
            List<List<String>> rowsAndColumns = new ArrayList<>();
            parseAsCSV(csvContent, false).forEach(record -> rowsAndColumns.add(Arrays.asList(record)));
            ExcelHelper.csv2xlsx(excel, worksheet, startCell, rowsAndColumns);
            return StepResult.success("Successfully saved content from '" + csvFile + "' to '" + excel + "'");
        } catch (IOException e) {
            return StepResult.fail("Unable to save '" + csvFile + "' to '" + excel + "': " + e.getMessage());
        }
    }

    public static CsvParser newCsvParser(String quote,
                                         String delim,
                                         String lineSeparator,
                                         boolean hasHeader,
                                         int maxColumns) {
        return newCsvParser(quote, delim, lineSeparator, hasHeader, true, maxColumns);
    }

    public static CsvParser newCsvParser(String quote,
                                         String delim,
                                         String lineSeparator,
                                         boolean hasHeader,
                                         boolean keepQuote,
                                         int maxColumns) {
        return new CsvParser(newCsvParserSetting(quote, delim, lineSeparator, hasHeader, keepQuote, maxColumns));
    }

    public static CsvParserSettings newCsvParserSettings(String delim,
                                                         String lineSeparator,
                                                         boolean hasHeader,
                                                         int maxColumns) {
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
            settings.setDelimiterDetectionEnabled(false);
        } else {
            settings.setDelimiterDetectionEnabled(true);
        }

        if (maxColumns > 0) { settings.setMaxColumns(maxColumns); }
        return settings;
    }

    @NotNull
    public static CsvParserSettings newCsvParserSetting(String quote,
                                                        String delim,
                                                        String lineSeparator,
                                                        boolean hasHeader,
                                                        boolean keepQuote,
                                                        int maxColumns) {
        CsvParserSettings settings = newCsvParserSettings(delim, lineSeparator, hasHeader, maxColumns);

        settings.setQuoteDetectionEnabled(true);
        if (StringUtils.isNotEmpty(quote)) {
            settings.getFormat().setQuote(quote.charAt(0));
            settings.setKeepQuotes(keepQuote);
        }

        return settings;
    }

    protected Optional<List<String>> collectFields(String config) {
        String textDelim = context.getTextDelim();
        List<String> fields = TextUtils.toList(context.getStringData(config), textDelim, true);
        return CollectionUtils.isEmpty(fields) ? Optional.empty() : Optional.of(fields);
    }

    protected Optional<String> collectConfig(String config) {
        return context.hasData(config) ? Optional.of(context.getStringData(config)) : Optional.empty();
    }

    protected List<String[]> parseAsCSV(File file) {
        CsvParser parser = newCsvParser(null, null, null, false, -1);
        List<String[]> records = parser.parseAll(file);
        log("found " + records.size() + " line(s) from '" + file + "'");
        return records;
    }

    protected List<String[]> parseAsCSV(String content, boolean hasHeader) {
        CsvParser parser = newCsvParser(null, null, null, hasHeader, -1);
        List<String[]> records = parser.parseAll(new StringReader(content));
        log("found " + records.size() + " line(s)");
        return records;
    }
}