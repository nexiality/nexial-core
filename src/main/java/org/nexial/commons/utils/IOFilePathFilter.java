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
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

import static java.io.File.separator;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.*;
import static org.nexial.core.NexialConst.REGEX_PREFIX;

public class IOFilePathFilter implements FilePathFilter {
    private final IOFileFilter subDirFilter;

    public IOFilePathFilter() { this(false); }

    public IOFilePathFilter(boolean recurse) {
        subDirFilter = recurse ? TrueFileFilter.INSTANCE : FalseFileFilter.INSTANCE;
    }

    @Override
    public List<String> filterFiles(String pattern) {
        if (isBlank(pattern)) { return null;}

        File genericFile = new File(pattern);

        // When the pattern is a complete directory like c:/xyz/abc/ or c:/xyz/abc
        if (genericFile.isDirectory()) {
            return FileUtils.listFiles(new File(pattern), TrueFileFilter.INSTANCE, subDirFilter)
                            .stream().map(File::getAbsolutePath).collect(toList());
        }

        // when the pattern is a complete file name like c:/xyz/abc/blah_doc.txt
        if (genericFile.isFile()) { return new ArrayList<String>() {{ add(pattern); }}; }

        final String dir = substringBeforeLast(pattern, separator);
        final String lastElementOfPath = substringAfterLast(pattern, separator);

        // If directory belonging to the pattern is not valid...
        if (!new File(dir).isDirectory()) { return null; }

        // If the file pattern contains a path like C:/xyz/abc/REGEX:ab.*[1]{1}
        if (lastElementOfPath.startsWith(REGEX_PREFIX)) {
            String fileRegex = substringAfter(lastElementOfPath, REGEX_PREFIX);
            return FileUtils.listFiles(new File(dir), new RegexFileFilter(fileRegex), subDirFilter)
                            .stream().map(File::getAbsolutePath).collect(toList());
        }

        // When the pattern is like c:/xyz/*
        if (lastElementOfPath.equals("*")) {
            return FileUtils.listFiles(new File(dir), TrueFileFilter.INSTANCE, subDirFilter)
                            .stream().map(File::getAbsolutePath).collect(toList());
        }

        // When the given pattern is like:- c:/xyz/ab*.pdf
        if (lastElementOfPath.contains("*")) {
            String filePatternForAll = FilePathFilter.getRegexPatternForWildCardCriteria(lastElementOfPath);
            return FileUtils.listFiles(new File(dir), new RegexFileFilter(filePatternForAll), subDirFilter)
                            .stream().map(File::getAbsolutePath).collect(toList());
        }

        return null;
    }
}
