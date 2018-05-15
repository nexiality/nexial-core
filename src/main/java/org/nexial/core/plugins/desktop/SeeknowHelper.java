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

package org.nexial.core.plugins.desktop;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.seeknow.*;
import org.nexial.seeknow.processor.*;

import static org.nexial.core.plugins.desktop.DesktopConst.*;

public final class SeeknowHelper {
    private static final Map<String, Seeknow> INSTANCES = new HashMap<>();


    private SeeknowHelper() { }

    public static Seeknow getInstance(String config) throws IOException {
        if (StringUtils.isBlank(config)) { throw new IllegalArgumentException("config must be specified"); }

        if (INSTANCES.containsKey(config)) { return INSTANCES.get(config); }

        Seeknow seeknow = SeeknowFactory.getInstance(config);
        if (seeknow == null) { throw new IllegalArgumentException("Unable to obtain seeknow instance via " + config); }
        // seeknow.setLineHeight(15);

        INSTANCES.put(config, seeknow);
        try { Thread.sleep(1000); } catch (InterruptedException e) { }

        return seeknow;
    }

    public static List<SeeknowData> findMatches(String fontFamily,
                                                BoundingRectangle bounds,
                                                Map<String, String> criteriaMap) throws IOException {

        SeeknowCriteria criteria = newCriteriaInstance(criteriaMap);
        if (criteria == null) { return null; }

        String config = resolveSeeknowConfig(fontFamily, criteria);
        if (StringUtils.isEmpty(config)) { return null; }

        Seeknow seeknow = SeeknowHelper.getInstance(config);
        if (seeknow == null) {
            throw new IllegalArgumentException("Unable to scan for matches via config '" + config + "'");
        }

        String contains = criteria.getContains();
        if (StringUtils.isBlank(criteria.getRegex()) && StringUtils.isNotBlank(contains) && criteria.isLimitMatch()) {
            Set<String> distinctChars = new HashSet<>();
            char[] matchByChars = contains.toCharArray();
            for (char matchByChar : matchByChars) { distinctChars.add(String.valueOf(matchByChar)); }

            seeknow = seeknow.makeClone();
            List<Glyph> availableGlyphs = seeknow.getGlyphs();
            List<Glyph> useGlyphs = new ArrayList<>();
            for (Glyph glyph : availableGlyphs) {
                if (distinctChars.contains(glyph.getCharacter())) { useGlyphs.add(glyph); }
            }

            seeknow.setGlyphs(useGlyphs);
        }

        CriteriaBasedProcessor processor = new CriteriaBasedProcessor(criteria);
        seeknow.fromScreenSelection(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), processor);

        List<SeeknowData> seeknowData = processor.listMatch();
        if (CollectionUtils.isEmpty(seeknowData)) {
            ConsoleUtils.log("No match found against specified criteria");
            return null;
        }

        return seeknowData;
    }

    public static SeeknowCriteria newCriteriaInstance(Map<String, String> criteria) {
        if (MapUtils.isEmpty(criteria)) { return null; }

        SeeknowCriteria instance = new SeeknowCriteria();
        if (criteria.containsKey("stopOnMatch")) {
            instance.setStopOnMatch(BooleanUtils.toBoolean(criteria.get("stopOnMatch")));
        }

        if (criteria.containsKey("contains")) { instance.setContains(criteria.get("contains")); }

        if (criteria.containsKey("limitMatch")) {
            instance.setLimitMatch(BooleanUtils.toBoolean(criteria.get("limitMatch")));
        }

        if (criteria.containsKey("stopOnEmptyText")) {
            instance.setStopOnEmptyText(BooleanUtils.toBoolean(criteria.get("stopOnEmptyText")));
        }

        if (criteria.containsKey("regex")) { instance.setRegex(criteria.get("regex")); }

        if (criteria.containsKey("contains")) { instance.setContains(criteria.get("contains")); }

        if (criteria.containsKey("limitRows")) { instance.setLimitRows(criteria.get("limitRows")); }

        if (criteria.containsKey("color")) {
            Map<String, String> colorMap = TextUtils.toMap(criteria.get("color"), ";", ":");
            int red = colorMap.containsKey("red") ? NumberUtils.toInt(StringUtils.trim(colorMap.get("red"))) : 0;
            int green = colorMap.containsKey("green") ? NumberUtils.toInt(StringUtils.trim(colorMap.get("green"))) : 0;
            int blue = colorMap.containsKey("blue") ? NumberUtils.toInt(StringUtils.trim(colorMap.get("blue"))) : 0;
            instance.setColor(new SeeknowColor(new Color(red, green, blue)));
        }

        return instance;
    }

    protected static String resolveSeeknowConfig(String fontFamily, SeeknowCriteria criteria) {
        String config = SEEKNOW_CONFIG_PREFIX + StringUtils.defaultIfBlank(fontFamily, DEF_SEEKNOW_FONT);

        if (StringUtils.isNotBlank(criteria.getRegex())) { return config + SEEKNOW_CONFIG_POSTFIX; }

        String contains = criteria.getContains();
        if (NumberUtils.isDigits(contains)) { return config + SEEKNOW_CONFIG_NUM_POSTFIX; }

        for (char c : config.toCharArray()) { if (Character.isLowerCase(c)) { return config + SEEKNOW_CONFIG_POSTFIX;}}

        return config + SEEKNOW_CONFIG_NUM_AND_UPPER_POSTFIX;
    }
}
