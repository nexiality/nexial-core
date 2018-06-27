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

package org.nexial.core.compare;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;

public final class Evaluate {
    private Map<String, Evaluator> evaluators;
    private String[] regexes;

    public void setRegexes(String[] regexes) { this.regexes = regexes; }

    public void setEvaluators(Map<String, Evaluator> evaluators) { this.evaluators = evaluators; }

    public boolean evaluate(String condition) throws IncompatibleTypeException {
        sanityCheck(condition);
        condition = replaceTokens(condition);
        return evaluateNested(condition);
    }

    private void sanityCheck(String condition) throws IncompatibleTypeException {
        if (StringUtils.isBlank(condition)) {
            throw new IncompatibleTypeException("Unable to evaulate empty condition");
        }
    }

    private String replaceTokens(String condition) {
        ExecutionContext context = ExecutionThread.get();
        return context != null ? context.replaceTokens(condition) : condition;
    }

    private boolean evaluateNested(String condition) throws IncompatibleTypeException {
        condition = StringUtils.trim(condition);
        if (!StringUtils.startsWith(condition, "(") && !StringUtils.endsWith(condition, ")")) {
            return evaluateOneCondition(condition);
        }

        // remove starting '(' and ending ')'
        //condition = StringUtils.stripEnd(StringUtils.stripStart(condition, "("), ")");

        // todo: nested comparison not yet supported.  do we need to?
        //boolean nested = true;
        //String subCondition = TextUtils.substringBetweenFirstPair(condition, "(", ")");
        //if (StringUtils.contains(subCondition, "(")) {
        //	// catch nested conditions
        //	nested = true;
        //	subCondition = StringUtils.substringAfter(subCondition, "(");
        //}
        //condition = StringUtils.trim(StringUtils.replace(condition,
        //                                                 "(" + subCondition + ")",
        //                                                 nested ? subConditionResult + "" : ""));

        String subCondition = StringUtils.substringBetween(condition, "(", ")");
        boolean subConditionResult = evaluateOneCondition(subCondition);
        condition = StringUtils.trim(StringUtils.replace(condition, "(" + subCondition + ")", ""));

        if (StringUtils.isBlank(condition)) { return subConditionResult; }

        String regexAnd = "and\\ *(\\(.+\\))";
        if (RegexUtils.isExact(condition, regexAnd)) {
            return subConditionResult && evaluateNested(RegexUtils.replace(condition, regexAnd, "$1"));
        }

        String regexOr = "or\\ *(\\(.+\\))";
        if (RegexUtils.isExact(condition, regexOr)) {
            return subConditionResult || evaluateNested(RegexUtils.replace(condition, regexOr, "$1"));
        }

        throw new IncompatibleTypeException("Unsupported condition: " + condition);
    }

    private boolean evaluateOneCondition(String condition) throws IncompatibleTypeException {
        Evaluator evaluator = null;
        String lhs = null;
        String rhs = null;

        for (String regex : regexes) {
            Pattern p = Pattern.compile(regex);
            Matcher matcher = p.matcher(condition);
            if (!matcher.matches()) { continue; }

            lhs = StringUtils.trim(matcher.group(1));
            String compareBy = StringUtils.trim(matcher.group(2));
            if (matcher.groupCount() >= 3) { rhs = StringUtils.trim(matcher.group(3)); }

            evaluator = evaluators.get(compareBy);
            if (evaluator != null) { break; }
        }

        if (evaluator != null) {
            return evaluator.proceed(lhs, rhs);
        } else {
            throw new IncompatibleTypeException("Unable to evaulate condition: " + condition);
        }
    }
}
