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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.variable.Expression.ExpressionFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.nexial.core.NexialConst.Data.NON_DISPLAYABLE_REPLACEMENTS;
import static org.nexial.core.NexialConst.Data.NULL;
import static org.nexial.core.variable.ExpressionConst.*;

public class ExpressionParser {
    private static final String EXPRESSION_END_REGEX = "(\\s*)\\].*";
    private final ExecutionContext context;
    private final ExpressionDataTypeBuilder typeBuilder;

    public ExpressionParser(ExecutionContext context) {
        this.context = context;
        this.typeBuilder = new ExpressionDataTypeBuilder(context);
    }

    public Expression parse(String text) throws TypeConversionException { return parse(text, false); }

    public Expression parse(String text, boolean syntaxOnly) throws TypeConversionException {
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
        ExpressionDataType dataType = null;
        try {
            dataType = typeBuilder.newDataType(datatype, datavalue);
        } catch (TypeConversionException e) {
            if (!syntaxOnly) { throw e; }
        }

        Expression expr = new Expression();
        expr.setDataType(dataType);

        String delim = context.getTextDelim();
        String EXPR_PARAM_DELIM = " %% ";

        text = preFunctionParsingSubstitution(typeGrouping.get(3));
        // List<String> functionGroups = RegexUtils.eagerCollectGroups(text, REGEX_FUNCTION, false, true);
        List<String> functionGroups = new ArrayList<>();
        collectFunctionGroups(text, functionGroups);

        StringBuilder newText = new StringBuilder();
        for (String group : functionGroups) {
            group = StringUtils.trim(group);
            String functionName = StringUtils.trim(StringUtils.substringBefore(group, DATATYPE_START));
            String paramList = StringUtils.substringAfter(group, DATATYPE_START);
            paramList = StringUtils.substringBeforeLast(paramList, DATATYPE_END);

            // (2021/07/12): special parameter separator
            String paramDelim = paramList.contains(EXPR_PARAM_DELIM) ? EXPR_PARAM_DELIM : delim;

            // substitute escaped delim with temp char sequence, break paramList into list, and then replace delim back
            List<String> params =
                TextUtils.toList(StringUtils.replace(paramList, "\\" + delim, ESCAPE_DELIM_SUBSTITUTION),
                                 paramDelim,
                                 false)
                         .stream()
                         .map(param -> StringUtils.replace(postFunctionParsingSubstitution(param, true),
                                                           ESCAPE_DELIM_SUBSTITUTION,
                                                           delim))
                         .collect(Collectors.toList());

            expr.addFunction(new ExpressionFunction(functionName, params));

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

    protected void collectFunctionGroups(String text, List<String> functionGroups) {
        if (StringUtils.isBlank(text)) { return; }

        text = StringUtils.trim(text);

        // 0. if text contains no spaces and no `(`
        //      then entire text is function name with no parameter
        if (StringUtils.containsNone(text, ' ', '(')) {
            functionGroups.add(text);
            return;
        }

        // 1. if text contains whitespace, check if text before whitespace contains `(`
        if (StringUtils.containsWhitespace(text)) {
            String portion = TextUtils.substringBeforeWhitespace(text);
            // 1a maybe there's whitespace between function name and parameter?
            //  if so, remove such spaces
            String temp = StringUtils.trim(StringUtils.substringAfter(text, portion));
            if (StringUtils.startsWith(temp, "(")) {
                text = portion + temp;
                portion = TextUtils.substringBeforeWhitespace(text);
            }

            if (StringUtils.containsNone(portion, '(')) {
                collectFunctionGroups(portion, functionGroups);
                text = StringUtils.trim(StringUtils.substringAfter(text, portion));
                collectFunctionGroups(text, functionGroups);
                return;
            }

            // 1b if `portion` contains `(`, we need to know it's a complete function or not - as in func(...)
            if (containsMatchingParenthesis(portion)) {
                // found a complete function - as in fun(...)
                // could be? type(locator,text)type(locator2,text2) - NO! NOT SUPPORTED (2021/07/11)
                functionGroups.add(portion);
                text = StringUtils.trim(StringUtils.substringAfter(text, portion));
                collectFunctionGroups(text, functionGroups);
                return;
            }

            // else `portion` does not constitute as a complete function, revert back
        }

        // 2. text contains some number of `(` and whitespace... let's have fun and parse it out 1 character at a time

        // 2a. extract function name
        String funcName = StringUtils.substringBefore(text, '(');
        text = StringUtils.trim(StringUtils.substringAfter(text, funcName));
        funcName = StringUtils.trim(funcName);

        // 2b. look for end of function parameter region - ie. (...)
        int posSearchFrom = 0;
        while (StringUtils.isNotBlank(text) || posSearchFrom >= text.length()) {
            int posCloseParen = StringUtils.indexOf(text, ')', posSearchFrom);
            if (posCloseParen == -1) {
                // throw exception instead
                ConsoleUtils.error("Likely erroneous expression found: missing closing parenthesis: " + text);
                functionGroups.add(funcName + text);
                return;
            }

            String parameters = StringUtils.substring(text, 0, posCloseParen + 1);
            if (containsMatchingParenthesis(parameters)) {
                functionGroups.add(funcName + parameters);
                text = StringUtils.substringAfter(text, parameters);
                collectFunctionGroups(text, functionGroups);
                return;
            } else {
                // 2c. nope, parenthesis not matching; keep looking
                posSearchFrom = posCloseParen + 2;
            }
        }

        if (StringUtils.isNotBlank(text)) { collectFunctionGroups(text, functionGroups); }
    }

    protected boolean containsMatchingParenthesis(String text) {
        return StringUtils.countMatches(text, '(') == StringUtils.countMatches(text, ')');
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
