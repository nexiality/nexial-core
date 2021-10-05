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

package org.nexial.core.variable;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;

public class Expression {
    private ExpressionDataType dataType;
    private List<ExpressionFunction> functions = new ArrayList<>();
    private String originalExpression = "";

    public static class ExpressionFunction {
        private String functionName;
        private List<String> params;

        public ExpressionFunction(String functionName, List<String> params) {
            this.functionName = functionName;
            this.params = params;
        }

        public String getFunctionName() { return functionName; }

        public List<String> getParams() { return params; }

        @Override
        public String toString() { return functionName + "(" + TextUtils.toString(params, ",") + ")"; }
    }

    public ExpressionDataType getDataType() { return dataType; }

    public void setDataType(ExpressionDataType dataType) { this.dataType = dataType; }

    public List<ExpressionFunction> getFunctions() { return functions; }

    public void setFunctions(List<ExpressionFunction> functions) { this.functions = functions; }

    public void addFunction(ExpressionFunction function) { functions.add(function); }

    public String getOriginalExpression() { return "[" + originalExpression + "]"; }

    public void setOriginalExpression(String originalExpression) { this.originalExpression = originalExpression; }

    public void appendOriginalExpression(String fragments) { originalExpression += fragments; }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder(dataType + "=>");
        functions.forEach(f -> buffer.append(f.toString()).append(" "));
        return StringUtils.trim(buffer.toString());
    }
}
