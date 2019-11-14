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

package org.nexial.core.plugins.io;

import org.apache.commons.lang3.StringUtils;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

public class CsvParserBuilder {
    private String quote;
    private boolean keepQuote;
    private String delim;
    private String lineSeparator;
    private boolean hasHeader;
    private int maxColumns;
    private int maxColumnWidth;
    private boolean trimValue = true;

    public CsvParserBuilder setQuote(String quote) {
        this.quote = quote;
        return this;
    }

    public CsvParserBuilder setDelim(String delim) {
        this.delim = delim;
        return this;
    }

    public CsvParserBuilder setLineSeparator(String lineSeparator) {
        this.lineSeparator = lineSeparator;
        return this;
    }

    public CsvParserBuilder setHasHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
        return this;
    }

    public CsvParserBuilder setKeepQuote(boolean keepQuote) {
        this.keepQuote = keepQuote;
        return this;
    }

    public CsvParserBuilder setMaxColumns(int maxColumns) {
        this.maxColumns = maxColumns;
        return this;
    }

    public CsvParserBuilder setMaxColumnWidth(int maxColumnWidth) {
        this.maxColumnWidth = maxColumnWidth;
        return this;
    }

    public CsvParserBuilder setTrimValue(boolean trimValue) {
        this.trimValue = trimValue;
        return this;
    }

    public CsvParser build() {
        CsvParserSettings settings = new CsvParserSettings();
        settings.setSkipEmptyLines(false);
        settings.setEmptyValue("");
        settings.setNullValue("");
        settings.setLineSeparatorDetectionEnabled(true);

        // settings.setQuoteDetectionEnabled(false);
        settings.setKeepQuotes(keepQuote);
        if (StringUtils.isNotEmpty(quote)) {
            settings.setQuoteDetectionEnabled(true);
            settings.getFormat().setQuote(quote.charAt(0));
        } else {
            settings.setQuoteDetectionEnabled(false);
        }

        if (StringUtils.isNotEmpty(delim)) {
            settings.getFormat().setDelimiter(delim.charAt(0));
            settings.setDelimiterDetectionEnabled(false);
        } else {
            settings.setDelimiterDetectionEnabled(true);
        }

        if (StringUtils.isNotEmpty(lineSeparator)) { settings.getFormat().setLineSeparator(lineSeparator); }

        settings.setHeaderExtractionEnabled(hasHeader);

        if (maxColumns > 0) { settings.setMaxColumns(maxColumns); }

        if (maxColumnWidth > 0) { settings.setMaxCharsPerColumn(maxColumnWidth); }

        if (!trimValue) {
            settings.setIgnoreLeadingWhitespaces(false);
            settings.setIgnoreLeadingWhitespacesInQuotes(false);
            settings.setIgnoreTrailingWhitespaces(false);
            settings.setIgnoreTrailingWhitespacesInQuotes(false);
        }

        return new CsvParser(settings);
    }
}
