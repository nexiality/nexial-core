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
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.variable.Expression.ExpressionFunction;

import static org.nexial.core.NexialConst.Data.NON_DISPLAYABLE_REPLACEMENTS;
import static org.nexial.core.NexialConst.Data.NULL;
import static org.nexial.core.variable.ExpressionConst.*;

class ExpressionParser {
    private static final String EXPRESSION_END_REGEX = "(\\s*)\\].*";
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

        text = typeGrouping.get(3);
        // workaround to avoid character conflicts
        // text = StringUtils.replace(text, "\\]", "]");
        text = preFunctionParsingSubstitution(text);

        String delim = context.getTextDelim();

        List<String> functionGroups = RegexUtils.eagerCollectGroups(text, REGEX_FUNCTION, false, true);

        StringBuilder newText = new StringBuilder();
        for (String group : functionGroups) {
            group = StringUtils.trim(group);
            String functionName = StringUtils.trim(StringUtils.substringBefore(group, DATATYPE_START));
            String paramList = StringUtils.substringAfter(group, DATATYPE_START);
            paramList = StringUtils.substringBeforeLast(paramList, DATATYPE_END);

            // substitute escaped delim with temp char sequence, break paramList into list, and then replace delim back
            List<String> params =
                TextUtils.toList(StringUtils.replace(paramList, "\\" + delim, ESCAPE_DELIM_SUBSTITUTION), delim, false)
                         .stream()
                         .map(param -> StringUtils.replace(postFunctionParsingSubstitution(param, true),
                                                           ESCAPE_DELIM_SUBSTITUTION,
                                                           delim))
                         .collect(Collectors.toList());
            // if (CollectionUtils.isNotEmpty(params)) {
                // List<String> substituted = new ArrayList<>();
                // params.forEach(param -> substituted.add(postFunctionParsingSubstitution(param, delim, true)));
                // expr.addFunction(new ExpressionFunction(functionName, substituted));
            // } else {
                expr.addFunction(new ExpressionFunction(functionName, params));
            // }

            // recollecting all the functions as for textual representation

            // collect anything before function e.g. spaces before function
            String operation = StringUtils.substringBefore(text, group) + group;

            // if function has no parameter given by ()
            // e.g. EXCEL() => csv  is given as EXCEL() => csv()
            operation += StringUtils.startsWith(text, operation + "()") ? "()" : "";

            newText.append(operation);
            text = StringUtils.replaceOnce(text, operation, "");

            if (StringUtils.isBlank(text)) {
                newText.append(text);
                break;
            } else if (RegexUtils.isExact(text, EXPRESSION_END_REGEX, true)) {
                // newText.append(RegexUtils.firstMatches(text, EXPRESSION_END_REGEX));
                break;
            }
        }

        // constructed original expression which will replaced after evaluation
        String fragment = typeGrouping.get(0) + typeGrouping.get(1) + typeGrouping.get(2) +
                          postFunctionParsingSubstitution(newText.toString(), false);
        expr.appendOriginalExpression(fragment);
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

    private static String postFunctionParsingSubstitution(String text, boolean removeEscape) {
        if (StringUtils.isBlank(text)) { return text; }

        for (Map.Entry<String, String> subst : FUNCTION_PARAM_SUBSTITUTIONS.entrySet()) {
            String replacement = removeEscape ? StringUtils.removeStart(subst.getKey(), "\\") : subst.getKey();
            text = StringUtils.replace(text, subst.getValue(), replacement);
        }

        if (NON_DISPLAYABLE_REPLACEMENTS.containsKey(text)) { return NON_DISPLAYABLE_REPLACEMENTS.get(text); }
        if (StringUtils.equals(text, NULL)) { return null; }
        return text;
    }
}
