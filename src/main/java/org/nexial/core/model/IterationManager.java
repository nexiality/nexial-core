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

package org.nexial.core.model;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.utils.ConsoleUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.nexial.core.NexialConst.Iteration.ITERATION_RANGE_SEP;
import static org.nexial.core.NexialConst.Iteration.ITERATION_SEP;

public final class IterationManager {
    private String iterationString;
    private List<Integer> iterations = new ArrayList<>();
    private int lowest;
    private int highest;
    private int first;
    private int last;

    private IterationManager() { }

    public static IterationManager newInstance(String iteration) {
        if (StringUtils.isBlank(iteration)) { iteration = "1"; }

        String[] parts = StringUtils.split(StringUtils.trim(iteration), ITERATION_SEP);
        if (ArrayUtils.isEmpty(parts)) { parts = new String[]{"1"}; }

        IterationManager instance = new IterationManager();
        boolean hasError=false;

        for (String part : parts) {
            // if it's a number, then it's gotta be 1 or greater
            // if it's a number, then it's gotta be integer
            // if it's not number, then it's gotta be ALPHABETs only (represent column)
            if (StringUtils.contains(part, ITERATION_RANGE_SEP)) {
                // internally convert all zero or less to 1. Doesn't make sense to consider a "zero" or "negative one" loop.
                int startNum = toIterationRef(StringUtils.trim(StringUtils.substringBefore(part, ITERATION_RANGE_SEP)));
                if (startNum < 1) {
                    ConsoleUtils.error("Invalid iteration reference: " + iteration + "; ignored...");
                    hasError=true;
                    break;
                }

                int endNum = toIterationRef(StringUtils.trim(StringUtils.substringAfter(part, ITERATION_RANGE_SEP)));
                if (endNum < 1) {
                    ConsoleUtils.error("Invalid iteration reference: " + iteration + "; ignored...");
                    hasError=true;
                    break;
                }

                // if start is greater than end
                if (endNum < startNum) {
                    for (int i = startNum; i >= endNum; i--) { instance.iterations.add(i); }
                } else {
                    for (int i = startNum; i <= endNum; i++) { instance.iterations.add(i); }
                }
            } else {
                int iterationNum = toIterationRef(StringUtils.trim(part));
                if (iterationNum < 1) {
                    ConsoleUtils.error("Invalid iteration reference: " + iteration + "; ignored...");
                    hasError=true;
                    break;
                }
                instance.iterations.add(iterationNum);
            }
        }

        if (hasError) {
            instance.first = 1;
            instance.last = 1;
            instance.lowest = 1;
            instance.highest = 1;
            return instance;
        }

        if (CollectionUtils.isEmpty(instance.iterations)) { instance.iterations.add(1); }

        List<Integer> tmp = new ArrayList<>(instance.iterations);
        instance.first = tmp.get(0);
        instance.last = tmp.get(tmp.size() - 1);
        Collections.sort(tmp);
        instance.lowest = tmp.get(0);
        instance.highest = tmp.get(tmp.size() - 1);

        // reformat for display consistency
        instance.iterationString = "";
        for (int num : instance.iterations) { instance.iterationString += num + ITERATION_SEP; }
        instance.iterationString = StringUtils.removeEnd(instance.iterationString, ITERATION_SEP);

        return instance;
    }

    /**
     * note that {@code iterationIndex} is zero-based, whilst {@link IterationManager#iterations} is one-based.
     * The difference will be resolved internally within this method.
     */
    public boolean skip(int iterationIndex) {
        return !iterations.isEmpty() && !iterations.contains(iterationIndex + 1);
    }

    public int getLowestIteration() { return lowest; }

    public int getHighestIteration() { return highest; }

    public int getFirstIteration() { return first; }

    public int getLastIteration() { return last; }

    public int getIterationCount() { return iterations.isEmpty() ? 1 : iterations.size(); }

    @Override
    public String toString() { return "[" + iterationString + "] total " + getIterationCount(); }

    public int getIterationRef(int index) {
        if (index < 0 || index >= iterations.size()) { return -1; }
        try {
            return IterableUtils.get(iterations, index);
        } catch (IndexOutOfBoundsException e) {
            // invalid index
            return -1;
        }
    }

    protected static int toIterationRef(String iteration) {
        int startNum = -1;
        if (StringUtils.isNumeric(iteration)) { startNum = NumberUtils.toInt(iteration); }
        if (StringUtils.isAlpha(iteration)) { startNum = ExcelAddress.fromColumnLettersToOrdinalNumber(iteration) - 1; }
        return startNum;
    }
}
