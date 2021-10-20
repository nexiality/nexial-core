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

import static org.nexial.core.NexialConst.Data.NON_PRINTABLE_REPLACEMENTS;
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
        String dataValue = StringUtils.trim(typeGrouping.get(1));
        dataValue = StringUtils.removeStart(dataValue, DATATYPE_START);
        dataValue = StringUtils.removeEnd(dataValue, DATATYPE_END);
        if (StringUtils.equals(dataValue, "null")) { dataValue = null; }
        ExpressionDataType dataType = null;
        try {
            dataType = typeBuilder.newDataType(datatype, dataValue);
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
                         .map(ExpressionParser::removeEscapes)
                         .collect(Collectors.toList());

            expr.addFunction(new ExpressionFunction(functionName, params));

            // recollecting all the functions as for textual representation

            // collect anything before function e.g. spaces before function
            String operation = "";
            if (StringUtils.startsWith(StringUtils.trim(text), group)) {
                operation = StringUtils.substringBefore(text, group) + group;
                if (StringUtils.startsWith(text, operation + "()")) { operation += "()"; }
                text = StringUtils.removeStart(text, operation);
            } else {
                if (StringUtils.startsWith(StringUtils.trim(text), functionName)) {
                    // this means that there might be some whitespaces between function name and parameter list
                    operation = StringUtils.substringBefore(text, functionName) + functionName;

                    // is there parameter list after function name?
                    String originalParams = StringUtils.substringAfter(group, functionName);
                    if (StringUtils.isNotBlank(originalParams)) {
                        if (StringUtils.startsWith(StringUtils.trim(StringUtils.substringAfter(text, operation)),
                                                   originalParams)) {
                            // found parameter list after trimming
                            operation +=
                                StringUtils.substringBefore(StringUtils.substringAfter(text, operation),
                                                            originalParams) +
                                originalParams;
                            text = StringUtils.substringAfter(text, originalParams);
                        } else {
                            // oh oh... not good
                            ConsoleUtils.error("POSSIBLE PARSING ERROR around " + text + "; UNABLE TO MAP PARAMETERS");
                            operation = group;
                            // last attempt... probably won't do much good
                            text = StringUtils.substringAfter(text, group);
                        }
                    } else {
                        // no param, but do we have `()` in text?
                        if (StringUtils.startsWith(text, operation + "()")) { operation += "()"; }
                        text = StringUtils.removeStart(text, operation);
                    }
                } else {
                    // oh oh... not good
                    ConsoleUtils.error("POSSIBLE PARSING ERROR around " + text);
                    operation = group;
                    // last attempt... probably won't do much good
                    text = StringUtils.substringAfter(text, group);
                }
            }

            newText.append(operation);

            if (StringUtils.isBlank(text)) {
                newText.append(text);
                break;
            } else if (RegexUtils.isExact(text, EXPRESSION_END_REGEX, true)) {
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
        int posEscapedCloseParen = StringUtils.indexOf(text, "\\)");
        int posEscapedOpenParen = StringUtils.indexOf(text, "\\(");
        if (posEscapedCloseParen != -1 && posEscapedOpenParen != -1) {
            text = StringUtils.remove(text, "\\(");
            text = StringUtils.remove(text, "\\)");
        } else {
            if (StringUtils.endsWith(text, "\\)")) { return false; }
            if (RegexUtils.match(text, "\\(.*\\\\[\\(\\)].*\\)")) { return true; }
        }

        return StringUtils.countMatches(text, '(') == StringUtils.countMatches(text, ')');
    }

    protected ExpressionDataTypeBuilder getTypeBuilder() { return typeBuilder; }

    private static String preFunctionParsingSubstitution(String text) {
        if (StringUtils.isBlank(text)) { return text; }

        for (Map.Entry<String, String> subst : FUNCTION_PARAM_SUBSTITUTIONS.entrySet()) {
            if (subst.getKey().equals("\\(") || subst.getKey().equals("\\)")) { continue; }
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

        if (NON_PRINTABLE_REPLACEMENTS.containsKey(text)) { return NON_PRINTABLE_REPLACEMENTS.get(text); }
        if (StringUtils.equals(text, NULL)) { return null; }
        return text;
    }

    private static String removeEscapes(String text) {
        if (StringUtils.isBlank(text)) { return text; }
        for (Map.Entry<String, String> subst : FUNCTION_PARAM_SUBSTITUTIONS.entrySet()) {
            text = StringUtils.replace(text, subst.getKey(), StringUtils.removeStart(subst.getKey(), "\\"));
        }
        return text;
    }
}
