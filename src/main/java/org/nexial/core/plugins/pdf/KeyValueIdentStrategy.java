/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.plugins.pdf;

import java.io.Serializable;

import org.nexial.core.plugins.pdf.CommonKeyValueIdentStrategies.STRATEGY;

import static org.nexial.core.plugins.pdf.CommonKeyValueIdentStrategies.DEF_REGEX_KEY;

public class KeyValueIdentStrategy implements Serializable {
    private boolean keyInHeaderRowOnly;
    private boolean keyValueAlternatingRow;
    private boolean keyValueAlternatingCell;

    // these 2 go hand in hand
    private boolean keyValueShareCell;
    private String keyValueDelimiter;

    // only make sense when the parsing strategy is NOT keyInHeaderRowOnly.
    private boolean keyThenValue = true;

    private String extractKeyPattern = DEF_REGEX_KEY;
    private boolean trimKey;
    private boolean normalizeKey = true;
    // only make sense for alternating_cell or alternating_row
    // use to ignore scenario where no matching key is found for specific form value
    private boolean skipKeyWithoutDelim = false;

    private boolean trimValue;
    private boolean normalizeValue = true;
    private boolean valueAsOneLine;

    // in case one strategy doesn't work
    private STRATEGY fallback;

    // private boolean multiPageHeaderRowTable;

    public static KeyValueIdentStrategy newInstance() { return new KeyValueIdentStrategy(); }

    public String getKeyValueDelimiter() { return keyValueDelimiter; }

    public String getExtractKeyPattern() { return extractKeyPattern; }

    public KeyValueIdentStrategy setExtractKeyPattern(String extractKeyPattern) {
        this.extractKeyPattern = extractKeyPattern;
        return this;
    }

    public boolean isKeyInHeaderRowOnly() { return keyInHeaderRowOnly; }

    // public boolean isMultiPageHeaderRowTable() { return multiPageHeaderRowTable; }

    public boolean isKeyValueAlternatingRow() { return keyValueAlternatingRow; }

    public boolean isKeyValueAlternatingCell() { return keyValueAlternatingCell; }

    public boolean isKeyValueShareCell() { return keyValueShareCell; }

    public boolean isTrimKey() { return trimKey; }

    public void setTrimKey(boolean trimKey) { this.trimKey = trimKey; }

    public boolean isNormalizeKey() { return normalizeKey; }

    public boolean isSkipKeyWithoutDelim() { return skipKeyWithoutDelim; }

    public boolean isNormalizeValue() { return normalizeValue; }

    public boolean isTrimValue() { return trimValue; }

    public void setTrimValue(boolean trimValue) { this.trimValue = trimValue; }

    public boolean isValueAsOneLine() { return valueAsOneLine; }

    public void setValueAsOneLine(boolean valueAsOneLine) { this.valueAsOneLine = valueAsOneLine; }

    public boolean isKeyThenValue() { return keyThenValue; }

    public STRATEGY getFallback() { return fallback; }

    public KeyValueIdentStrategy keyValueShareCell(String keyValueDelimiter) {
        this.keyInHeaderRowOnly = false;
        this.keyValueAlternatingCell = false;
        this.keyValueAlternatingRow = false;
        this.keyValueShareCell = true;
        this.keyValueDelimiter = keyValueDelimiter;
        return this;
    }

    public KeyValueIdentStrategy keyInHeaderRowOnly() {
        this.keyInHeaderRowOnly = true;
        this.keyValueAlternatingCell = false;
        this.keyValueAlternatingRow = false;
        this.keyValueShareCell = false;
        this.keyValueDelimiter = null;
        return this;
    }

    public KeyValueIdentStrategy keyValueAlternatingRow() {
        this.keyInHeaderRowOnly = false;
        this.keyValueAlternatingCell = false;
        this.keyValueAlternatingRow = true;
        this.keyValueShareCell = false;
        this.keyValueDelimiter = null;
        return this;
    }

    public KeyValueIdentStrategy keyValueAlternatingCell() {
        this.keyInHeaderRowOnly = false;
        this.keyValueAlternatingCell = true;
        this.keyValueAlternatingRow = false;
        this.keyValueShareCell = false;
        this.keyValueDelimiter = null;
        return this;
    }

    // public KeyValueIdentStrategy multiPageHeaderRowTable() {
    // 	this.multiPageHeaderRowTable = true;
    // 	return this;
    // }

    public KeyValueIdentStrategy keyThenValue(boolean keyThenValue) {
        if (this.keyInHeaderRowOnly) {
            throw new IllegalArgumentException("Incompatible for a KEY_IN_HEADER_ROW strategy");
        }
        this.keyThenValue = keyThenValue;
        return this;
    }

    public KeyValueIdentStrategy normalizeKey(boolean normalizeKey) {
        this.normalizeKey = normalizeKey;
        return this;
    }

    public KeyValueIdentStrategy normalizeValue(boolean normalizeValue) {
        this.normalizeValue = normalizeValue;
        return this;
    }

    public KeyValueIdentStrategy trimKey() {
        this.trimKey = true;
        return this;
    }

    public KeyValueIdentStrategy trimValue() {
        this.trimValue = true;
        return this;
    }

    public KeyValueIdentStrategy valueAsOneLine() {
        this.valueAsOneLine = true;
        return this;
    }

    // purpose: aggressive key/value harvesting.  When the last column lines up to the key and key contains
    // the delimiter, then we'll split it up as value and value
    public KeyValueIdentStrategy keyValueDelimiter(String keyValueDelimiter) {
        this.keyValueDelimiter = keyValueDelimiter;
        return this;
    }

    public KeyValueIdentStrategy skipKeyWithoutDelim(boolean skipKeyWithoutDelim) {
        this.skipKeyWithoutDelim = skipKeyWithoutDelim;
        return this;
    }

    public KeyValueIdentStrategy deepClone() {
        KeyValueIdentStrategy clone = new KeyValueIdentStrategy();
        clone.keyInHeaderRowOnly = this.keyInHeaderRowOnly;
        // clone.multiPageHeaderRowTable = this.multiPageHeaderRowTable;
        clone.keyValueAlternatingRow = this.keyValueAlternatingRow;
        clone.keyValueAlternatingCell = this.keyValueAlternatingCell;
        clone.keyValueShareCell = this.keyValueShareCell;
        clone.keyValueDelimiter = this.keyValueDelimiter;
        clone.keyThenValue = this.keyThenValue;
        clone.extractKeyPattern = this.extractKeyPattern;
        clone.trimKey = this.trimKey;
        clone.normalizeKey = this.normalizeKey;
        clone.skipKeyWithoutDelim = this.skipKeyWithoutDelim;
        clone.trimValue = this.trimValue;
        clone.normalizeValue = this.normalizeValue;
        clone.valueAsOneLine = this.valueAsOneLine;
        clone.fallback = this.fallback;
        return clone;
    }

    public KeyValueIdentStrategy fallback(STRATEGY alternate) {
        this.fallback = alternate;
        return this;
    }
}
