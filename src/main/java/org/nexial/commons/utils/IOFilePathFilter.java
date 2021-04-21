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
import java.util.Collection;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

import static java.io.File.separator;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.*;
import static org.nexial.core.NexialConst.PolyMatcher.REGEX;

public class IOFilePathFilter implements FilePathFilter {
    private final IOFileFilter subDirFilter;
    private final boolean matchDirectories;

    public IOFilePathFilter() { this(false); }

    public IOFilePathFilter(boolean recurse) { this(recurse, false); }

    public IOFilePathFilter(boolean recurse, boolean includeSubdirectories) {
        subDirFilter     = recurse ? TrueFileFilter.INSTANCE : FalseFileFilter.INSTANCE;
        matchDirectories = includeSubdirectories;
    }

    @Override
    public List<String> filterFiles(String pattern) {
        if (isBlank(pattern)) { return null;}

        File baseDir = new File(pattern);

        // When the pattern is a complete directory like c:/xyz/abc/ or c:/xyz/abc
        IOFileFilter allFiles = TrueFileFilter.INSTANCE;
        if (baseDir.isDirectory()) {
            Collection<File> matches = matchDirectories ?
                                       FileUtils.listFilesAndDirs(baseDir, allFiles, subDirFilter) :
                                       FileUtils.listFiles(baseDir, allFiles, subDirFilter);
            return matches.stream().map(File::getAbsolutePath).collect(toList());
        }

        // when the pattern is a complete file name like c:/xyz/abc/blah_doc.txt
        if (baseDir.isFile()) { return new ArrayList<String>() {{ add(pattern); }}; }

        final String dir = substringBeforeLast(pattern, separator);
        final String lastElementOfPath = substringAfterLast(pattern, separator);

        // If directory belonging to the pattern is not valid...
        File searchFrom = new File(dir);
        if (!searchFrom.isDirectory()) { return null; }

        // If the file pattern contains a path like C:/xyz/abc/REGEX:ab.*[1]{1}
        if (lastElementOfPath.startsWith(REGEX)) {
            String fileRegex = substringAfter(lastElementOfPath, REGEX);
            RegexFileFilter filter = new RegexFileFilter(fileRegex);
            Collection<File> matches = matchDirectories ?
                                       FileUtils.listFilesAndDirs(searchFrom, filter, subDirFilter) :
                                       FileUtils.listFiles(searchFrom, filter, subDirFilter);
            // not sure why apache io is adding `searchFrom` into the match list... we are taking it out
            if (matchDirectories) { matches.remove(searchFrom); }
            return matches.stream().map(File::getAbsolutePath).collect(toList());
        }

        // When the pattern is like c:/xyz/*
        if (lastElementOfPath.equals("*")) {
            Collection<File> matches = matchDirectories ?
                                       FileUtils.listFilesAndDirs(searchFrom, allFiles, subDirFilter) :
                                       FileUtils.listFiles(searchFrom, allFiles, subDirFilter);
            return matches.stream().map(File::getAbsolutePath).collect(toList());
        }

        // When the given pattern is like:- c:/xyz/ab*.pdf
        if (lastElementOfPath.contains("*")) {
            String filePatternForAll = FilePathFilter.getRegexPatternForWildCardCriteria(lastElementOfPath);
            RegexFileFilter filter = new RegexFileFilter(filePatternForAll);
            Collection<File> matches = matchDirectories ?
                                       FileUtils.listFilesAndDirs(searchFrom, filter, subDirFilter) :
                                       FileUtils.listFiles(searchFrom, filter, subDirFilter);
            return matches.stream().map(File::getAbsolutePath).collect(toList());
        }

        return null;
    }
}
