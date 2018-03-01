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
import java.util.regex.Pattern;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.uptospeed.seeknow.*;

import org.nexial.commons.utils.TextUtils;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.plugins.desktop.DesktopConst.*;

public final class SeeknowHelper {
    private static final Map<String, Seeknow> INSTANCES = new HashMap<>();

    public static class CriteriaBasedProcessor extends AcceptAllProcessor {
        private SeeknowCriteria criteria;

        public CriteriaBasedProcessor(SeeknowCriteria criteria) {
            this.criteria = criteria;
            this.stopOnEmptyText = criteria.isStopOnEmptyText();
        }

        @Override
        public boolean processMatch(SeeknowData match) {
            // match 'stopOnEmptyText'
            if (match == null) { return !this.stopOnEmptyText; }

            String matchText = match.getText();
            if (StringUtils.isBlank(matchText)) { return !this.stopOnEmptyText; }

            // match 'rows'
            int lineNumber = match.getLineNumber();
            boolean isLastRow = criteria.isLastRow(lineNumber);
            if (!criteria.isValidRow(lineNumber)) { return !isLastRow; }

            // match 'color'
            if (criteria.getColor() != null) {
                boolean colorFound = match.getColors().contains(criteria.getColor());

                // don't want this line, but we don't need to stop either
                if (!colorFound) { return !isLastRow; }
            }

            // match 'regex' or match 'contains'
            String regex = criteria.getRegex();
            if (StringUtils.isNotBlank(regex)) {
                if (criteria.matchByRegex(matchText)) {
                    this.data.add(match);
                    return !criteria.isStopOnMatch() && !isLastRow;
                } else {
                    return !isLastRow;
                }
            }

            String contains = criteria.getContains();
            if (StringUtils.isNotBlank(contains)) {
                if (StringUtils.contains(matchText, contains)) {
                    this.data.add(match);
                    return !criteria.isStopOnMatch() && !isLastRow;
                } else {
                    return !isLastRow;
                }
            }

            this.data.add(match);
            return !isLastRow;
        }
    }

    public static class SeeknowCriteria {
        /** if true, stop scanning when first match is found */
        private boolean stopOnMatch;

        /** specify the "substring" to match by */
        private String contains;

        /** if true, then only match the characters specified in contains.  DOES NOT WORK WHEN REGEX IS SPECIFIED */
        private boolean limitMatch;

        /** specify the regex to match by; takes precedence over "contains" match */
        private String regex;
        private Pattern pattern;

        /** if true, seeknow stops upon finding a "blank" row (rows with only white color) */
        private boolean stopOnEmptyText;

        /** only scanned the specified rows, which are specified as zero-based, single numer or range */
        private String limitRows;
        private Set<Integer> rows;

        /** match only when a row contains the specified color */
        private SeeknowColor color;

        public boolean isStopOnMatch() { return stopOnMatch;}

        public void setStopOnMatch(boolean stopOnMatch) { this.stopOnMatch = stopOnMatch;}

        public String getContains() { return contains;}

        public void setContains(String contains) { this.contains = contains;}

        public String getRegex() { return regex;}

        public void setRegex(String regex) {
            this.regex = regex;
            this.pattern = Pattern.compile(regex);
        }

        public boolean matchByRegex(String text) {
            return !StringUtils.isEmpty(regex) && !StringUtils.isBlank(text) && this.pattern.matcher(text).find();
        }

        public boolean isStopOnEmptyText() { return stopOnEmptyText;}

        public void setStopOnEmptyText(boolean stopOnEmptyText) { this.stopOnEmptyText = stopOnEmptyText;}

        public String getLimitRows() { return limitRows;}

        public void setLimitRows(String limitRows) {
            this.limitRows = StringUtils.trim(limitRows);

            rows = new TreeSet<>();
            if (StringUtils.equals(this.limitRows, "*")) { return; }
            if (NumberUtils.isDigits(this.limitRows)) {
                rows.add(NumberUtils.toInt(limitRows));
                return;
            }

            String[] rowsArray = StringUtils.split(this.limitRows, ",");
            if (ArrayUtils.isEmpty(rowsArray)) { return; }

            Arrays.stream(rowsArray).forEach(r -> {
                r = StringUtils.trim(r);
                if (NumberUtils.isDigits(r)) {
                    rows.add(NumberUtils.toInt(r));
                } else {
                    throw new IllegalArgumentException("Invalid row index specified: " + r);
                }
            });
        }

        public boolean isValidRow(int rowIndex) { return CollectionUtils.isEmpty(rows) || rows.contains(rowIndex); }

        public boolean isLastRow(int rowIndex) {
            if (CollectionUtils.isEmpty(rows)) { return false; }

            Integer[] rowIndices = rows.toArray(new Integer[rows.size()]);
            int lastRow = rowIndices[rowIndices.length - 1];
            return rowIndex >= lastRow;
        }

        public boolean isLimitMatch() { return limitMatch; }

        public void setLimitMatch(boolean limitMatch) { this.limitMatch = limitMatch; }

        public SeeknowColor getColor() { return color;}

        public void setColor(SeeknowColor color) { this.color = color;}

        @Override
        public String toString() {
            return "contains=" + contains +
                   "regex=" + regex +
                   "stopOnEmptyText=" + stopOnEmptyText +
                   "limitRows=" + limitRows +
                   "limitMatch=" + limitMatch +
                   "color=" + color;
        }
    }

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
