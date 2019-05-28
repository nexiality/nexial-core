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

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.variable.Expression.ExpressionFunction;

public class ExpressionProcessor {
    private ExpressionParser parser;

    // support mock test and ioc
    public ExpressionProcessor() { }

    public ExpressionProcessor(ExecutionContext context) { this.parser = new ExpressionParser(context); }

    public String process(String text) throws ExpressionException {
        // in case `text` contains expression that contains escaped "close angled bracket" as operation parameter
        // text = StringUtils.replace(text, "\\]", ExpressionConst.ALT_CLOSE_ANGLED_BRACKET);

        Expression expr = parser.parse(text);
        if (expr == null) { return text; }

        ExpressionDataType data = expr.getDataType();
        List<ExpressionFunction> functions = expr.getFunctions();
        for (ExpressionFunction function : functions) {
            data = evaluate(data, function);
            if (data == null) { return text; }
        }

        String asString = data.stringify();
        if (asString == null) {
            if (StringUtils.equals(text, expr.getOriginalExpression())) { return null; }
            asString = "";
        }
        text = StringUtils.replace(text, expr.getOriginalExpression(), asString);
        return parser.getTypeBuilder().isValidType(text) ? process(text) : text;
    }

    protected ExpressionDataType evaluate(ExpressionDataType data, ExpressionFunction function)
        throws ExpressionException {
        Transformer transformer = data.getTransformer();
        if (!transformer.isValidFunction(function)) {
            ConsoleUtils.error(data.getName() + "." + function.getFunctionName() +
                               "() is not valid or incorrectly specified");
            return null;
        }
        return transformer.transform(data, function);
    }

    protected String stringify(ExpressionDataType data) { return data == null ? null : data.stringify(); }
}
