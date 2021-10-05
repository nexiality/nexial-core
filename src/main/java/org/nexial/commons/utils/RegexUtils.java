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

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.*;

/**
 * @author Mike Liu
 */
public final class RegexUtils {
    private static final int REGEX_FLAGS = MULTILINE | UNIX_LINES | DOTALL;

    private RegexUtils() { }

    /**
     * general utility to search on <code >text</code> based on <code >regex</code> and substitute the matches with
     * <code >replace</code>.  The added feature is the multiline consideration for <code >text</code>.
     * <code >replace</code> can be expressed with regex group.
     */
    public static String replace(String text, String regex, String replace) {
        if (StringUtils.isEmpty(text)) { return text; }
        if (StringUtils.isEmpty(regex)) { return text; }

        replace = StringUtils.defaultString(replace);
        Pattern p = Pattern.compile(regex, REGEX_FLAGS);

        StringBuilder sb = new StringBuilder();
        String[] lines = StringUtils.splitPreserveAllTokens(text, '\n');
        for (String line : lines) {
            if (sb.length() > 0) { sb.append('\n'); }
            Matcher matcher = p.matcher(line);
            if (matcher.find()) {
                sb.append(matcher.replaceAll(replace));
            } else {
                sb.append(line);
            }
        }

        return sb.toString();
    }

    /**
     * general utility to search on <code >text</code> based on <code >regex</code> and substitute the matches with
     * <code >replace</code>.  The added feature is the multiline consideration for <code >text</code>.
     * <code >replace</code> can be expressed with regex group.
     */
    public static String replaceMultiLines(String text, String regex, String replace) {
        return replaceMultiLines(text, regex, replace, true);
    }

    public static String replaceMultiLines(String text, String regex, String replace, boolean caseSensitive) {
        if (StringUtils.isEmpty(text)) { return text; }
        if (StringUtils.isEmpty(regex)) { return text; }

        Matcher matcher = Pattern.compile(regex, deriveRegexFlags(true, caseSensitive)).matcher(text);
        if (matcher.find()) { return matcher.replaceAll(replace); }
        return text;
    }

    /**
     * test to see if {@code text} matches {@code regex} exactly.  Empty/null {@code text} will always return false,
     * and empty/null {@code regex} will always return true.
     */
    public static boolean isExact(String text, String regex) { return isExact(text, regex, false); }

    public static boolean isExact(String text, String regex, boolean multiline) {
        return isExact(text, regex, multiline, true);
    }

    public static boolean isExact(String text, String regex, boolean multiline, boolean caseSensitive) {
        if (StringUtils.isEmpty(regex)) { return true; }
        if (StringUtils.isEmpty(text)) { return false; }

        return Pattern.compile(regex, deriveRegexFlags(multiline, caseSensitive)).matcher(text).matches();
    }

    /** "contain" match (instead of exact) */
    public static boolean match(String text, String regex) { return match(text, regex, false); }

    /**
     * "contain" match (instead of exact).  Use {@code multiline} to handle {@code text} that might contain
     * multiple lines
     */
    public static boolean match(String text, String regex, boolean multiline) {
        return match(text, regex, multiline, true);
    }

    /**
     * "contain" match (instead of exact).  Use {@code multiline} to handle {@code text} that might contain multiple
     * lines and possibly case insensitively
     */
    public static boolean match(String text, String regex, boolean multiline, boolean caseSensitive) {
        if (StringUtils.isEmpty(regex)) { return false; }
        // commented out so that we can match empty string via ^$
//        if (StringUtils.isEmpty(text)) { return false; }
        if (StringUtils.isEmpty(text)) { return StringUtils.equals(regex, "^$"); }
        return Pattern.compile(regex, deriveRegexFlags(multiline, caseSensitive)).matcher(text).find();
    }

    /**
     * same as {@link #collectGroups(String, String, boolean)} except that empty/blank {@code text} is treated as
     * "no-match".
     *
     * @see #collectGroups(String, String, boolean)
     */
    public static List<String> collectGroups(String text, String regex) { return collectGroups(text, regex, false); }

    /**
     * return a collection of matches to {@code text} based on the regular expression denoted by {@code regex}.
     * Optionally, one can determine if any matching should be exercised if {@code text} is blank (containing
     * only whitespace).
     */
    public static List<String> collectGroups(String text, String regex, boolean acceptBlank) {
        return collectGroups(text, regex, acceptBlank, false);
    }

    @NotNull
    public static List<String> collectGroups(String text, String regex, boolean acceptBlank, boolean multiline) {
        return collectGroups(text, regex, acceptBlank, multiline, true);
    }

    @NotNull
    public static List<String> collectGroups(String text,
                                             String regex,
                                             boolean acceptBlank,
                                             boolean multiline,
                                             boolean caseSensitive) {
        List<String> list = new ArrayList<>();
        if (!acceptBlank && StringUtils.isBlank(text)) { return list; }
        if (StringUtils.isBlank(regex)) { return list; }

        Matcher matcher = Pattern.compile(regex, deriveRegexFlags(multiline, caseSensitive)).matcher(text);
        if (matcher.matches() && matcher.groupCount() > 0) {
            // always starts with 1 since group 0 represents the "entire" match
            for (int i = 1; i <= matcher.groupCount(); i++) { list.add(matcher.group(i)); }
        }

        return list;
    }

    public static List<String> eagerCollectGroups(String text, String regex, boolean acceptBlank, boolean multiline) {
        return eagerCollectGroups(text, regex, acceptBlank, multiline, true);
    }

    public static List<String> eagerCollectGroups(String text,
                                                  String regex,
                                                  boolean acceptBlank,
                                                  boolean multiline,
                                                  boolean caseSensitive) {
        List<String> list = new ArrayList<>();
        if (!acceptBlank && StringUtils.isBlank(text)) { return list; }
        if (StringUtils.isBlank(regex)) { return list; }

        Matcher matcher = Pattern.compile(regex, deriveRegexFlags(multiline, caseSensitive)).matcher(text);
        while (matcher.find()) { list.add(matcher.group()); }
        return list;
    }

    public static String removeMatches(String text, String regex) { return removeMatches(text, regex, false, true); }

    public static String removeMatches(String text, String regex, boolean multiline, boolean caseSensitive) {
        if (StringUtils.isEmpty(text)) { return text; }
        if (StringUtils.isBlank(regex)) { return text; }

        Matcher matcher = Pattern.compile(regex, deriveRegexFlags(multiline, caseSensitive)).matcher(text);
        while (matcher.find()) { text = matcher.replaceAll(""); }

        return text;
    }

    /** extract (and join) matched region(s) */
    public static String retainMatches(String text, String regex) { return retainMatches(text, regex, false, true); }

    public static String retainMatches(String text, String regex, boolean multiline, boolean caseSensitive) {
        if (StringUtils.isEmpty(text)) { return text; }
        if (StringUtils.isBlank(regex)) { return text; }

        String retained = "";
        Matcher matcher = Pattern.compile(regex, deriveRegexFlags(multiline, caseSensitive)).matcher(text);
        while (matcher.find()) {
            MatchResult result = matcher.toMatchResult();
            retained += StringUtils.substring(text, result.start(), result.end());
        }

        return retained;
    }

    /** extract first matched region(s) */
    @Nullable
    public static String firstMatches(String text, String regex) { return firstMatches(text, regex, false, true); }

    @Nullable
    public static String firstMatches(String text, String regex, boolean multiline, boolean caseSensitive) {
        if (StringUtils.isEmpty(text)) { return text; }
        if (StringUtils.isBlank(regex)) { return text; }

        Matcher matcher = Pattern.compile(regex, deriveRegexFlags(multiline, caseSensitive)).matcher(text);
        if (matcher.find()) {
            MatchResult result = matcher.toMatchResult();
            return StringUtils.substring(text, result.start(), result.end());
        }

        return null;
    }

    public static List<String> extract(String text, String regex) { return extract(text, regex, false, true); }

    public static List<String> extract(String text, String regex, boolean multiline, boolean caseSensitive) {
        List<String> extracted = new ArrayList<>();

        if (StringUtils.isEmpty(text) || StringUtils.isEmpty(regex)) { return extracted; }

        Matcher matcher = Pattern.compile(regex, deriveRegexFlags(multiline, caseSensitive)).matcher(text);
        while (matcher.find()) {
            MatchResult result = matcher.toMatchResult();
            extracted.add(StringUtils.substring(text, result.start(), result.end()));
        }

        return extracted;
    }

    private static int deriveRegexFlags(boolean multiline, boolean caseSensitive) {
        int flags = multiline ? REGEX_FLAGS : 0;
        if (!caseSensitive) { flags |= CASE_INSENSITIVE; }
        return flags;
    }
}
