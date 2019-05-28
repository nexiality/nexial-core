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
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.variable.Expression.ExpressionFunction;

import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.variable.ExpressionConst.*;

class ExpressionParser {
    private ExecutionContext context;
    private ExpressionDataTypeBuilder typeBuilder;

    public ExpressionParser(ExecutionContext context) {
        this.context = context;
        this.typeBuilder = new ExpressionDataTypeBuilder(context);
    }

    Expression parse(String text) throws TypeConversionException {
        if (StringUtils.isBlank(text)) { return null; }
        if (!typeBuilder.isValidType(text)) { return null; }

        List<String> typeGrouping = typeBuilder.parseExpressionGroups(text);
        if (CollectionUtils.size(typeGrouping) != 4) { return null; }

        // create data type
        String datatype = StringUtils.trim(typeGrouping.get(0));
        String datavalue = StringUtils.trim(typeGrouping.get(1));
        datavalue = StringUtils.removeStart(datavalue, DATATYPE_START);
        datavalue = StringUtils.removeEnd(datavalue, DATATYPE_END);
        if (StringUtils.equals(datavalue, "null")) { datavalue = null; }
        ExpressionDataType dataType = typeBuilder.newDataType(datatype, datavalue);

        Expression expr = new Expression();
        expr.setDataType(dataType);

        // text = typeGrouping.get(3);
        // text = StringUtils.replace(text, ALT_CLOSE_ANGLED_BRACKET, "\\]");

        // gather fragment for textual representation reconstruction
        // String fragment = typeGrouping.get(0) + typeGrouping.get(1) + typeGrouping.get(2) + text;
        String fragment = typeGrouping.get(0) + typeGrouping.get(1) + typeGrouping.get(2) + typeGrouping.get(3);
        expr.appendOriginalExpression(fragment);

        text = typeGrouping.get(3);
        // workaround to avoid character conflicts
        // text = StringUtils.replace(text, "\\]", "]");
        text = preFunctionParsingSubstitution(text);

        String delim = context.getTextDelim();

        List<String> functionGroups = RegexUtils.eagerCollectGroups(text, REGEX_FUNCTION, false, true);
        functionGroups.forEach(group -> {
            group = StringUtils.trim(group);
            String functionName = StringUtils.trim(StringUtils.substringBefore(group, DATATYPE_START));
            String paramList = StringUtils.substringAfter(group, DATATYPE_START);
            paramList = StringUtils.substringBeforeLast(paramList, DATATYPE_END);
            List<String> params = TextUtils.toList(paramList, delim, false);
            if (CollectionUtils.isNotEmpty(params)) {
                List<String> substituted = new ArrayList<>();
                params.forEach(param -> substituted.add(postFunctionParsingSubstitution(param)));
                expr.addFunction(new ExpressionFunction(functionName, substituted));
            } else {
                expr.addFunction(new ExpressionFunction(functionName, params));
            }
        });

        return expr;
    }

    protected ExpressionDataTypeBuilder getTypeBuilder() { return typeBuilder; }

    private static String preFunctionParsingSubstitution(String text) {
        if (StringUtils.isBlank(text)) { return text; }

        for (Map.Entry<String, String> subst : FUNCTION_PARAM_SUBSTITUTIONS.entrySet()) {
            text = StringUtils.replace(text, subst.getKey(), subst.getValue());
        }

        return text;
    }

    private static String postFunctionParsingSubstitution(String text) {
        if (StringUtils.isBlank(text)) { return text; }

        for (Map.Entry<String, String> subst : FUNCTION_PARAM_SUBSTITUTIONS.entrySet()) {
            text = StringUtils.replace(text, subst.getValue(), StringUtils.removeStart(subst.getKey(), "\\"));
        }

        if (NON_DISPLAYABLE_REPLACEMENTS.containsKey(text)) { return NON_DISPLAYABLE_REPLACEMENTS.get(text); }
        if (StringUtils.equals(text, NULL)) { return null; }
        return text;
    }
}
