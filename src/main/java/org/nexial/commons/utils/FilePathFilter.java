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

package org.nexial.commons.utils;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

/**
 * A functional interface which provides the method declaration to filter the files based on the file path.
 */
@FunctionalInterface
public interface FilePathFilter {
    String REGEX_FOR_ANY = ".*";

    /**
     * Filters the files based on the pattern provided.
     *
     * @param pattern regex pattern.
     * @return list of strings containing file details.
     */
    List<String> filterFiles(@NotNull final String pattern);

    /**
     * Returns regex pattern for files having the * in the path. The String "\\*"  in the file path is considered as
     * a wild card.It is considered as zero or many. However the other symbols in the java regex syntax are considered
     * as normal characters. So when the final regex expression is generated the symbols like +, -, . etc are replaced
     * by [%s]{1} where %s gets replaced by the corresponding character. For example for nexial-text.txt the regex \
     * expression will be nexial[-]{1}.txt.
     * Similarly all the *'s in the file name will be replaced by the value REGEX_FOR_ANY.
     * <p>
     * For example if the criteria passed in is "abc?*" the regex generated is "abc[?]{1}.*".
     * So this will filter all the files starting with the "abc?" and anything that follows like
     * abc?.txt, abc?1.txt abc?something.pdf etc.
     *
     * @param criteria the string for which corresponding regex expression to be generated.
     * @return Regex pattern for the string containing wild card(s) i.e. * in it.
     */
    static String getRegexPatternForWildCardCriteria(final String criteria) {
        String filePattern = criteria;
        char[] chars = filePattern.toCharArray();

        Set<Character> uniqueChars = new LinkedHashSet<>();
        for (char c : chars) { uniqueChars.add(c); }

        HashSet<Character> filter = new HashSet<Character>() {{
            add('*');
            add('^');
        }};
        final Set<Character> nonAlphaNumericAndStar =
            uniqueChars.stream()
                       .filter(x -> !Character.isLetter(x) && !Character.isDigit(x) && !filter.contains(x))
                       .collect(Collectors.toSet());

        for (final Character x : nonAlphaNumericAndStar) {
            filePattern = filePattern.replace(String.valueOf(x),
                                              String.format((x == '[' || x == ']') ? "[\\%s]{1}" : "[%s]{1}", x));
        }

        filePattern = filePattern.replace("^", String.format("[%s]{1}", "\\^"));
        filePattern = filePattern.replaceAll("\\*", REGEX_FOR_ANY);
        return filePattern;
    }
}
