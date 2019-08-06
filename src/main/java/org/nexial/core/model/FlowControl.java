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
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.FlowControls.ARG_PREFIX;
import static org.nexial.core.NexialConst.FlowControls.REGEX_ARGS;
import static org.nexial.core.model.FlowControl.Directive.TimeTrackStart;
import static org.nexial.core.model.NexialFilterComparator.Any;

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
    private NexialFilterList conditions;

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
        ProceedIf(true),
        TimeTrackStart(true),
        TimeTrackEnd(false);

        private boolean conditionRequired;

        Directive(boolean conditionRequired) { this.conditionRequired = conditionRequired; }

        public boolean isConditionRequired() { return conditionRequired; }
    }

    /**
     * it's like saying "create a flow control of specific kind (directrive) that is activated when all
     * the conditions ({@link NexialFilterList}) are met".
     */
    public FlowControl(Directive directive, NexialFilterList conditions) {
        this.directive = directive;
        this.conditions = conditions;
    }

    public Directive getDirective() { return directive; }

    public NexialFilterList getConditions() { return conditions; }

    public boolean hasNoCondition() { return CollectionUtils.isEmpty(conditions); }

    @NotNull
    public static Map<Directive, FlowControl> parse(String flowControlText) {
        if (StringUtils.isBlank(flowControlText)) { return null; }

        flowControlText = StringUtils.remove(StringUtils.trim(flowControlText), "\n");
        flowControlText = StringUtils.remove(flowControlText, "\r");
        flowControlText = StringUtils.remove(flowControlText, "\t");

        Map<Directive, FlowControl> map = new HashMap<>();
        if (StringUtils.isBlank(flowControlText)) { return map; }

        // otherwise let's make a prediction on how many flow controls there would be
        List<String> flowControlGroups =
            RegexUtils.eagerCollectGroups(flowControlText, REGEX_POSSIBLE_FLOW_CONTROLS, false, false);
        if (CollectionUtils.isEmpty(flowControlGroups)) {
            ConsoleUtils.error("Invalid flow controls found (and IGNORED): " + flowControlText);
            return map;
        }

        int expectedFlowControlGroup = flowControlGroups.size();

        // only supports 1 instance of a directive per flow control cell
        // if multiple condition per directive is needed, use `&` to chain conditions
        map = getFlowControls(flowControlText);

        if (expectedFlowControlGroup != map.size()) {
            ConsoleUtils.error("Possibly invalid flow control found (and IGNORED): " + flowControlText);
        }

        return map;
    }

    private static Map<Directive, FlowControl> getFlowControls(String flowControlText) {
        List<Integer> directivePositions = new ArrayList<>();
        for (Directive d : Directive.values()) {
            if (!StringUtils.contains(flowControlText, d.name())) { continue; }
            directivePositions.add(StringUtils.indexOf(flowControlText, d.name()));
        }

        Collections.sort(directivePositions);
        int size = directivePositions.size();

        Map<Directive, FlowControl> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            int startIndex = directivePositions.get(i);
            int endIndex = i + 1 < size ? directivePositions.get(i + 1) : flowControlText.length();

            String flowControls = StringUtils.substring(flowControlText, startIndex, endIndex);
            String dir = StringUtils.trim(StringUtils.substringBefore(flowControls, ARG_PREFIX));

            // handle exception if directive doesn't exist unlikely to happen
            Directive directive;
            try {
                directive = Enum.valueOf(Directive.class, dir);
            } catch (IllegalArgumentException e) {
                ConsoleUtils.error("Invalid Flow Control " + dir + " found (and IGNORED)");
                continue;
            }

            NexialFilterList conditions = parseFlowControl(flowControls, directive);
            if (CollectionUtils.isEmpty(conditions)) { continue; }

            map.put(directive, new FlowControl(directive, conditions));
        }

        return map;
    }

    @Override
    public String toString() { return directive + " -> " + conditions; }

    @NotNull
    private static NexialFilterList parseFlowControl(String flowControl, Directive directive) {
        // e.g. SkipIf ( blah... ) or PauseBefore()
        String regex = directive + REGEX_ARGS;
        // if there's no match, then returns empty map
        if (!RegexUtils.isExact(flowControl, regex)) { return new NexialFilterList(); }

        String conditions = StringUtils.trim(RegexUtils.replace(flowControl, regex, "$1"));
        if (directive.equals(TimeTrackStart)) {
            NexialFilterList filters = new NexialFilterList();
            filters.add(new NexialFilter(conditions, Any, "*"));
            return filters;
        }

        // if there's no condition, then returns empty map
        if (StringUtils.isBlank(conditions)) {
            NexialFilterList filters = new NexialFilterList();

            // no condition specified means ALWAYS TRUE for Pause* directives
            if (!directive.isConditionRequired()) {
                filters.add(new NexialFilter("*", Any, "*"));
            }

            return filters;
        }

        return new NexialFilterList(conditions);
    }
}
