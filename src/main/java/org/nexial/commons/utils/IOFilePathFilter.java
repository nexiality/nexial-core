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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;

import static java.io.File.separator;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.*;

public class IOFilePathFilter implements FilePathFilter {
    @Override
    public List<String> filterFiles(String pattern) {
        if (isBlank(pattern)) {
            return null;
        }

        File genericFile = new File(pattern);

        // When the pattern is a complete directory like c:/xyz/abc/ or c:/xyz/abc
        if (genericFile.isDirectory()) {
            return Arrays.stream(FileUtils.getFile(pattern).list())
                         .map(str -> join(pattern, separator, str))
                         .collect(toList());
        }

        if (genericFile.isFile()) { // when the pattern is a complete file name like c:/xyz/abc/blah_doc.txt
            return new ArrayList<String>() {{
                add(pattern);
            }};
        }

        final String dir = substringBeforeLast(pattern, separator);
        final String lastElementOfPath = substringAfterLast(pattern, separator);

        if (!new File(dir).isDirectory()) { // If directory belonging to the pattern is not valid.
            return null;
        }

        // If the file pattern contains a path like C:/xyz/abc/REGEX:ab.*[1]{1}
        if (lastElementOfPath.startsWith(REGEX_PREFIX)) {
            String fileRegex = substringAfter(lastElementOfPath, REGEX_PREFIX);
            return Arrays.stream(FileUtils.getFile(dir).list(new RegexFileFilter(fileRegex)))
                         .map(str -> join(dir, separator, str))
                         .collect(toList());
        }

        // When the pattern is like c:/xyz/*
        if (lastElementOfPath.equals("*")) {
            return Arrays.stream(FileUtils.getFile(dir).list())
                         .map(str -> join(dir, separator, str))
                         .collect(toList());
        }

        // When the given pattern is like:- c:/xyz/ab*.pdf
        if (lastElementOfPath.contains("*")) {
            String filePatternForAll = FilePathFilter.getRegexPatternForWildCardCriteria(lastElementOfPath);
            return Arrays.stream(FileUtils.getFile(dir).list(new RegexFileFilter(filePatternForAll)))
                         .map(str -> join(dir, separator, str))
                         .collect(toList());

        }
        return null;
    }
}
