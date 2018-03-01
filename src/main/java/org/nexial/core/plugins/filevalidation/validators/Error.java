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

package org.nexial.core.plugins.filevalidation.validators;

public class Error {

    private String recordLine;
    private String fieldName;
    private String validationType;
    private String severity;
    private String errorMessage;

    public static class ErrorBuilder {
        private String fieldName;
        private String validationType;
        private String severity;
        private String errorMessage;

        public ErrorBuilder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public ErrorBuilder validationType(String validationType) {
            this.validationType = validationType;
            return this;
        }

        public ErrorBuilder severity(String severity) {
            this.severity = severity;
            return this;
        }

        public ErrorBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Error build() {
            return new Error(this);
        }
    }

    private Error() {}

    public Error(ErrorBuilder errorBuilder) {
        this.fieldName = errorBuilder.fieldName;
        this.validationType = errorBuilder.validationType;
        this.severity = errorBuilder.severity;
        this.errorMessage = errorBuilder.errorMessage;
    }

    public String getRecordLine() {
        return recordLine;
    }

    public void setRecordLine(String recordLine) {
        this.recordLine = recordLine;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getValidationType() {
        return validationType;
    }

    public String getSeverity() {
        return severity;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "Error{recordLine='" + recordLine + '\'' +
               ", fieldName='" + fieldName + '\'' +
               ", validationType='" + validationType + '\'' +
               ", severity='" + severity + '\'' +
               ", errorMessage=" + errorMessage +
               '}';
    }
}
