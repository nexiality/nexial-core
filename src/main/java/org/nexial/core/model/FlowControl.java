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

package org.nexial.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.FlowControls.*;
import static org.nexial.core.model.FlowControl.Condition.ANY;
import static org.nexial.core.model.NexialFilterComparator.*;

/**
 * Additional control over the associate test step.  Such control can be any of the following:
 * <ul>
 * <li>SkipIf(conditions) - skip this test step if the condition(s) is met.</li>
 * <li>PauseBefore(conditions) - if the condition(s) is met, pause before the test step is executed.  This is only
 * applicable for local execution.  Condition is optional.</li>
 * <li>PauseAfter(conditions) - if the condition(s) is met, pause after the test step is executed.  This is only
 * applicable for local execution.  Condition is optional.</li>
 * <li>EndIf(conditions) - end test execution if condition(s) is met, in which case the associate test step is omitted.</li>
 * <li>FailIf(conditions) - fail the associate test step if the condition(s) is met.  Such test step wil not be executed
 * if the condition is met.</li>
 * <li>EndLoopIf(conditions) - end current iteration if condition(s) is met.</li>
 * </ul>
 *
 * Condition can be expressed as "EQUAL", "NOT EQUAL", or "IS ONE OF".  For example:
 * <ul>
 * <li><code>var1="a"</code> - the associated flow control is true if <code>var1</code> has the literal value <code>"a"</code>.</li>
 * <li><code>var2 != "a"</code> - the associated flow control is true if <code>var2</code> does not have the literal value of <code>"a"</code>.</li>
 * <li><code>var3 is ["a", "b", 14]</code> - the associated flow control is true if <code>var3</code> is not either <code>"a"</code>, <code>"b"</code> or <code>14</code>.</li>
 * </ul>
 *
 * Conditions can be compounded via the <code>&</code> symbol.  All compound conditions are inherently "AND" conditions.
 */
public class FlowControl {
    public static final String REGEX_POSSIBLE_FLOW_CONTROLS = "(\\w+\\s*\\((?:.*?)\\))";
    private Directive directive;
    private Map<String, Condition> conditions;

    /**
     * list of all possition directive -- the control support by nexial over each test step.
     */
    public enum Directive {
        SkipIf(true),
        PauseBefore(false),
        PauseAfter(false),
        EndIf(true),
        FailIf(true),
        EndLoopIf(true),
        ProceedIf(true);

        private boolean conditionRequired;

        Directive(boolean conditionRequired) { this.conditionRequired = conditionRequired; }

        public boolean isConditionRequired() { return conditionRequired; }
    }

    public static class Condition {
        public static final Condition ANY = new Condition(Any, null);
        private NexialFilterComparator operator;
        private List<String> values;

        public Condition(NexialFilterComparator operator, List<String> values) {
            this.operator = operator;
            this.values = values;
        }

        public NexialFilterComparator getOperator() { return operator; }

        public List<String> getValues() { return values; }

        @Override
        public String toString() {
            return StringUtils.defaultString(operator.getSymbol(), "none")
                   + (CollectionUtils.isEmpty(values) ?
                      "" : " " + (CollectionUtils.size(values) == 1 ? values.get(0) : values.toString()));
        }
    }

    public FlowControl(Directive directive, Map<String, Condition> conditions) {
        this.directive = directive;
        this.conditions = conditions;
    }

    public Directive getDirective() { return directive; }

    public Map<String, Condition> getConditions() { return conditions; }

    public static Map<Directive, FlowControl> parseToMap(String flowControlText) {
        if (StringUtils.isBlank(flowControlText)) { return null; }

        flowControlText = StringUtils.remove(StringUtils.trim(flowControlText), "\n");
        flowControlText = StringUtils.remove(flowControlText, "\r");
        flowControlText = StringUtils.remove(flowControlText, "\t");

        Map<Directive, FlowControl> map = new HashMap<>();
        if (StringUtils.isBlank(flowControlText)) { return map; }

        // otherwise let's make a prediction on how many flow controls there would be
        List<String> flowControlGroups = RegexUtils.collectGroups(flowControlText, REGEX_POSSIBLE_FLOW_CONTROLS);
        if (CollectionUtils.isEmpty(flowControlGroups)) {
            ConsoleUtils.error("Invalid flow controls found: " + flowControlText);
            return map;
        }

        int expectedFlowControlGroup = flowControlGroups.size();

        for (Directive d : Directive.values()) {
            Map<String, Condition> conditions = parseFlowControl(flowControlText, d);
            if (MapUtils.isEmpty(conditions)) { continue; }

            map.put(d, new FlowControl(d, conditions));
        }

        if (expectedFlowControlGroup != map.size()) {
            ConsoleUtils.error("Possibly invalid flow control found: " + flowControlText);
        }

        return map;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("directive", directive).append("conditions", conditions).toString();
    }

    private static Map<String, Condition> parseFlowControl(String flowControls, Directive directive) {
        // e.g. SkipIf ( blah... ) or PauseBefore()
        String regex = ".*(" + directive.name() + REGEX_ARGS + ").*";

        // if there's no match, then returns empty map
        if (!RegexUtils.isExact(flowControls, regex)) { return new HashMap<>(); }

        // .*(PauseBefore\s*\((.+?)\)).*
        String flowControl = RegexUtils.replace(flowControls, regex, "$1");
        String conditions = StringUtils.trim(RegexUtils.replace(flowControl, directive + REGEX_ARGS, "$1"));

        Map<String, Condition> map = new HashMap<>();

        // if there's no condition, then returns empty map
        if (StringUtils.isBlank(conditions)) {
            // no condition specified means ALWAYS TRUE for Pause* directives
            if (!directive.isConditionRequired()) { map.put("*", ANY); }
            return map;
        }

        String[] pairs = StringUtils.split(conditions, DELIM_ARGS);
        for (String pair : pairs) {
            if (StringUtils.contains(pair, NotEqual.getSymbol())) {
                addEqualityCondition(map, pair, NotEqual);
            } else if (StringUtils.contains(pair, Equal.getSymbol())) {
                addEqualityCondition(map, pair, Equal);
            } else if (StringUtils.contains(pair, OPERATOR_IS_SYNTAX) ||
                       StringUtils.contains(pair, OPERATOR_IS_SYNTAX2)) {
                addInCondition(map, pair);
            } else {
                ConsoleUtils.error("Possibly incomplete/incorrect flow control found: " + pair);
                map.put(pair, null);
            }
        }

        return map;
    }

    private static void addEqualityCondition(Map<String, Condition> map, String pair, NexialFilterComparator operator) {
        String key = StringUtils.trim(StringUtils.substringBefore(pair, operator.getSymbol()));

        // trim before store to compensate for lousy typist
        String value = StringUtils.trim(StringUtils.substringAfter(pair, operator.getSymbol()));
        List<String> values = Collections.singletonList(
            StringUtils.defaultString(NexialFilter.normalizeCondition(value)));
        map.put(key, new Condition(operator, values));
    }

    private static void addInCondition(Map<String, Condition> map, String pair) {
        String key;
        String value;
        if (StringUtils.contains(pair, OPERATOR_IS_SYNTAX)) {
            key = StringUtils.trim(StringUtils.substringBefore(pair, OPERATOR_IS_SYNTAX));
            value = StringUtils.trim(StringUtils.substringAfter(pair, OPERATOR_IS + " "));
        } else if (StringUtils.contains(pair, OPERATOR_IS_SYNTAX2)) {
            key = StringUtils.trim(StringUtils.substringBefore(pair, OPERATOR_IS_SYNTAX2));
            value = StringUtils.trim(StringUtils.substringAfter(pair, OPERATOR_IS));
        } else {
            ConsoleUtils.error("Unknown directive found/ignored: " + pair);
            return;
        }

        // trim before store to compensate for lousy typist
        value = StringUtils.trim(StringUtils.substringBetween(value, IS_OPEN_TAG, IS_CLOSE_TAG));

        // temp. substitute for escaped double quotes
        value = StringUtils.replace(value, "\\\"", "`");

        // get substring between double quotes
        List<String> conditionValues = new ArrayList<>();
        String[] values = StringUtils.split(value, ",");
        for (String v : values) {
            // empty or spaces must be enclosed within quotes; any empty spaces between comma are ignored
            if (StringUtils.isBlank(v)) { continue; }

            conditionValues.add(NexialFilter.normalizeCondition(StringUtils.trim(v)));
        }

        map.put(key, new Condition(Is, conditionValues));
    }
}
