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

package org.nexial.core.variable;

import java.util.ArrayList;
import java.util.List;

import static org.nexial.core.NexialConst.NL;

public class WebDataType extends ExpressionDataType<String> {
    private Transformer transformer = new WebTransformer();
    private boolean allPass;
    private List<Result> results;

    public static class Result {
        private String operation;
        private String result;
        private String error;

        public Result(String operation, String result, String error) {
            this.operation = operation;
            this.result = result;
            this.error = error;
        }

        public String getOperation() { return operation; }

        public String getResult() { return result; }

        public String getError() { return error; }

        @Override
        public String toString() {
            return "{" + NL +
                   "    operation=" + operation + "," + NL +
                   "    result=" + result + "," + NL +
                   "    error=" + error + NL +
                   "  }";
        }
    }

    public WebDataType(String textValue) throws TypeConversionException {
        super(textValue);
        allPass = true;
    }

    private WebDataType() { super(); }

    public boolean isAllPass() { return allPass; }

    public void setAllPass(boolean allPass) { this.allPass = allPass; }

    public List<Result> getResults() { return results; }

    public void setResults(List<Result> results) { this.results = results; }

    public void addResults(Result result) {
        if (results == null) {
            results = new ArrayList<>();
        }
        results.add(result);
    }

    @Override
    public String toString() {
        return "{" + NL +
               "  allPass=" + allPass + "," + NL +
               "  results=" + results + NL +
               "}";
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        setTextValue(value);
    }

    @Override
    public String getName() { return "WEB"; }

    @Override
    Transformer getTransformer() { return transformer; }

    @Override
    WebDataType snapshot() {
        WebDataType snapshot = new WebDataType();
        snapshot.transformer = transformer;
        snapshot.allPass = allPass;
        snapshot.results = results;
        snapshot.value = value;
        snapshot.textValue = textValue;
        return snapshot;
    }

    @Override
    protected void init() { this.value = textValue; }
}