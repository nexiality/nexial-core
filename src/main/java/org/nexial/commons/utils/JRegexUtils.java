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

package org.nexial.commons.utils;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import jregex.Matcher;
import jregex.Pattern;
import jregex.Replacer;

public final class JRegexUtils {
    private JRegexUtils() {}

    /**
     * general utility to search on <code >text</code> based on <code >regex</code> and substitute the matches with
     * <code >replace</code>.  The added feature is the multiline consideration for <code >text</code>.
     * <code >replace</code> can be expressed with regex group.
     */
    public static String replace(String text, String regex, String replace) {
        if (StringUtils.isEmpty(text)) { return text; }
        if (StringUtils.isEmpty(regex)) { return text; }

        Pattern p = new Pattern(regex);

        StringBuilder sb = new StringBuilder();
        String[] lines = StringUtils.split(text, '\n');
        for (String line : lines) {
            if (sb.length() > 0) { sb.append('\n'); }
            Matcher matcher = p.matcher(line);
            if (matcher.find()) {
                Replacer r = p.replacer(replace);
                sb.append(r.replace(line));
            } else {
                sb.append(line);
            }
        }

        return sb.toString();
    }

    public static List<String> collectGroups(String text, String regex) {
        List<String> list = new ArrayList<>();
        if (StringUtils.isBlank(text)) { return list; }
        if (StringUtils.isBlank(regex)) { return list; }

        Pattern p = new Pattern(regex);
        Matcher m = p.matcher(text);

        // some regex would produce matches and groups
        if (m.matches() && m.groupCount() > 0) {
            for (int i = 1; i < m.groupCount(); i++) { list.add(m.group(i)); }
            return list;
        }

        // some regex (Perl-style) would produce repeating finds
        while (m.find()) { list.add(m.toString()); }
        return list;
    }
}
