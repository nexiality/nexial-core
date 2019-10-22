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

package org.nexial.core.plugins.pdf;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.CollectionUtil;
import org.nexial.core.NexialConst.Pdf;
import org.nexial.core.model.ExecutionContext;
import org.thymeleaf.util.ListUtils;

import static org.nexial.core.NexialConst.Pdf.*;
import static org.nexial.core.plugins.pdf.CommonKeyValueIdentStrategies.STRATEGY.*;

public final class CommonKeyValueIdentStrategies implements Serializable {
    static final String DEF_REGEX_KEY = "^([0-9A-Za-z\\\\\\/\\ \\.\\,\\\"\\'\\(\\)\\[\\]\\#\\-]+)\\:?\\s*$";

    private static final Map<String, KeyValueIdentStrategy> STRATEGIES = initCommonStrategies();

    enum STRATEGY {
        ALTERNATING_ROW, ALTERNATING_CELL, SHARE_CELL, HEADER_ONLY, SHARE_CELL_THEN_ALT_CELL
    }

    private CommonKeyValueIdentStrategies() { }

    public static KeyValueIdentStrategy toStrategy(String name) {
        if (StringUtils.isBlank(name)) { return null; }
        return STRATEGIES.get(name);
    }

    public static boolean isValidStrategy(String name) { return STRATEGIES.containsKey(name); }

    public static void harvestStrategy(ExecutionContext context) {
        Map<String, String> customStrategyMapping = context.getDataByPrefix(PDFFORM_PREFIX);
        List<String> customStrategyKeys = CollectionUtil.toList(customStrategyMapping.keySet());
        Collections.sort(customStrategyKeys);

        String currentStrategyName = null;
        KeyValueIdentStrategy currentStrategy = null;
        for (String strategyKey : customStrategyKeys) {
            String strategyName = StringUtils.substringBefore(strategyKey, ".");
            String strategyProp = StringUtils.substringAfter(strategyKey, ".");
            String prop = customStrategyMapping.get(strategyKey);

            // make sure the prop is known/acceptable
            if (!ListUtils.contains(PDFFORM_VALID_OPTIONS, strategyProp)) { continue; }

            if (!StringUtils.equals(currentStrategyName, strategyName)) {
                // save current strategy and start new one
                if (currentStrategy != null) {
                    STRATEGIES.put(currentStrategyName, currentStrategy);
                    currentStrategy = null;
                }
            }

            currentStrategyName = strategyName;

            if (currentStrategy == null) {
                String basedOn = customStrategyMapping.get(strategyName + "." + PDFFORM_BASEDON);
                if (StringUtils.isNotBlank(basedOn) && isValidStrategy(basedOn)) {
                    currentStrategy = STRATEGIES.get(prop).deepClone();
                } else {
                    currentStrategy = KeyValueIdentStrategy.newInstance();
                }
            }

            if (StringUtils.equals(PDFFORM_BASEDON, strategyProp)) { continue; }

            if (StringUtils.equals(PDFFORM_KEY_PATTERN, strategyProp)) {
                currentStrategy.setExtractKeyPattern(prop);
                continue;
            }

            if (StringUtils.equals(PDFFORM_KEY_VALUE_DELIM, strategyProp)) {
                if (currentStrategy.isKeyValueAlternatingCell()) {
                    currentStrategy.keyValueDelimiter(prop);
                } else {
                    currentStrategy.keyValueShareCell(prop);
                }
                continue;
            }

            if (StringUtils.equals(PDFFORM_TRIM_KEY, strategyProp)) {
                currentStrategy.setTrimKey(BooleanUtils.toBoolean(prop));
                continue;
            }

            if (StringUtils.equals(PDFFORM_KEY_THEN_VALUE, strategyProp)) {
                currentStrategy.keyThenValue(BooleanUtils.toBoolean(prop));
                continue;
            }

            if (StringUtils.equals(PDFFORM_NORMALIZE_KEY, strategyProp)) {
                currentStrategy.normalizeKey(BooleanUtils.toBoolean(prop));
                continue;
            }

            if (StringUtils.equals(PDFFORM_SKIP_KEY_WITHOUT_DELIM, strategyProp)) {
                currentStrategy.skipKeyWithoutDelim(BooleanUtils.toBoolean(prop));
                continue;
            }

            if (StringUtils.equals(PDFFORM_TRIM_VALUE, strategyProp)) {
                currentStrategy.setTrimValue(BooleanUtils.toBoolean(prop));
                continue;
            }

            if (StringUtils.equals(PDFFORM_VALUE_AS_ONE_LINE, strategyProp)) {
                currentStrategy.setValueAsOneLine(BooleanUtils.toBoolean(prop));
                continue;
            }

            if (StringUtils.equals(PDFFORM_NORMALIZE_VALUE, strategyProp)) {
                currentStrategy.normalizeValue(BooleanUtils.toBoolean(prop));
                continue;
            }
        }

        if (StringUtils.isNotBlank(currentStrategyName) && currentStrategy != null) {
            STRATEGIES.put(currentStrategyName, currentStrategy);
        }
    }

    public static KeyValueIdentStrategy addStrategy(String name, KeyValueIdentStrategy strategy) {
        if (StringUtils.isNotBlank(name) && strategy != null) { STRATEGIES.put(name, strategy); }
        return strategy;
    }

    private static Map<String, KeyValueIdentStrategy> initCommonStrategies() {
        Map<String, KeyValueIdentStrategy> map = new HashMap<>();
        map.put(ALTERNATING_ROW.name(), KeyValueIdentStrategy.newInstance()
                                                             .setExtractKeyPattern(DEF_REGEX_KEY)
                                                             .keyValueAlternatingRow()
                                                             .trimKey()
                                                             .trimValue()
                                                             .valueAsOneLine());
        map.put(ALTERNATING_CELL.name(), KeyValueIdentStrategy.newInstance()
                                                              .setExtractKeyPattern(DEF_REGEX_KEY)
                                                              .keyValueAlternatingCell()
                                                              .trimKey()
                                                              .trimValue()
                                                              .valueAsOneLine());
        map.put(SHARE_CELL.name(), KeyValueIdentStrategy.newInstance()
                                                        .setExtractKeyPattern(DEF_REGEX_KEY)
                                                        .keyValueShareCell(":")
                                                        .trimKey()
                                                        .trimValue()
                                                        .valueAsOneLine());
        map.put(HEADER_ONLY.name(), KeyValueIdentStrategy.newInstance()
                                                         .setExtractKeyPattern(DEF_REGEX_KEY)
                                                         .keyInHeaderRowOnly()
                                                         .trimKey()
                                                         .trimValue()
                                                         .valueAsOneLine());
        map.put(SHARE_CELL_THEN_ALT_CELL.name(), map.get(SHARE_CELL.name())
                                                    .deepClone()
                                                    .fallback(ALTERNATING_CELL));
        return map;
    }

}
