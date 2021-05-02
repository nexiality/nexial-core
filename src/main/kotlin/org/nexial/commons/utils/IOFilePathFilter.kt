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
package org.nexial.commons.utils

import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.FalseFileFilter
import org.apache.commons.io.filefilter.IOFileFilter
import org.apache.commons.io.filefilter.RegexFileFilter
import org.apache.commons.io.filefilter.TrueFileFilter
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS
import org.nexial.core.NexialConst.PolyMatcher.REGEX
import org.springframework.util.CollectionUtils
import java.io.File
import java.io.File.separator

class IOFilePathFilter @JvmOverloads constructor(private val recurse: Boolean = false,
                                                 private val matchDirectories: Boolean = false) : FilePathFilter {

    private val subDirFilter = if (recurse) TrueFileFilter.INSTANCE else FalseFileFilter.INSTANCE

    override fun filterFiles(pattern: String): List<String> {
        if (StringUtils.isBlank(pattern)) return emptyList()

        var baseDir = File(pattern)

        // when the pattern is a complete file name like c:/xyz/abc/blah_doc.txt
        if (baseDir.isFile) {
            // can't use listOf(pattern) because the underlying implementation is SingletonList, which can't be modified
            // return listOf(pattern)
            val list = ArrayList<String>()
            list.add(pattern)
            return list
        }

        // When the pattern is a complete directory like c:/xyz/abc/ or c:/xyz/abc
        // or When the pattern is like c:/xyz/*
        var fileFilter = TrueFileFilter.INSTANCE
        var dirFilter = subDirFilter
        var regex = ""

        // If directory belonging to the pattern is not valid, then we need some parsing logic...
        if (!baseDir.isDirectory) {
            baseDir = File(StringUtils.appendIfMissing(StringUtils.substringBeforeLast(pattern, separator), separator))
            if (!baseDir.isDirectory) return emptyList()

            val lastElementOfPath = StringUtils.substringAfterLast(pattern, separator)
            if (lastElementOfPath.startsWith(REGEX)) {
                // If the file pattern contains a path like C:/xyz/abc/REGEX:ab.*[1]{1}
                regex = StringUtils.substringAfter(lastElementOfPath, REGEX)
                fileFilter = RegexFileFilter(regex)
                dirFilter = deriveDirFilter(baseDir, regex)
            } else if (lastElementOfPath != "*" && lastElementOfPath.contains("*")) {
                // When the given pattern is like:- c:/xyz/ab*.pdf
                regex = FilePathFilter.getRegexPatternForWildCardCriteria(lastElementOfPath)
                fileFilter = RegexFileFilter(regex)
                dirFilter = deriveDirFilter(baseDir, regex)
            }
        }

        val matches =
                if (matchDirectories) FileUtils.listFilesAndDirs(baseDir, fileFilter, dirFilter)
                else FileUtils.listFiles(baseDir, fileFilter, dirFilter)
        if (CollectionUtils.isEmpty(matches)) return emptyList()

        // not sure why apache io is adding `searchFrom` into the match list... we are taking it out
        if (matchDirectories) matches.remove(baseDir)

        // `FileUtils` likely to return all directories too. Need to filter them down..
        return matches.filter {
            if (StringUtils.isNotBlank(regex)) RegexUtils.match(it.name, regex, false, !IS_OS_WINDOWS) else true
        }
                .filter { if (!recurse) StringUtils.equals(it.parent, baseDir.absolutePath) else true }
                .map { it.absolutePath }
    }

    private fun deriveDirFilter(baseDir: File, fileRegex: String): IOFileFilter {
        // when we don't want to recurse but we want to include subdirectories of current directory...
        return if (matchDirectories && !recurse) {
            object : IOFileFilter {
                override fun accept(file: File): Boolean {
                    // for windows, we want to compare the file name case-insensitively
                    return StringUtils.equals(file.parent, baseDir.absolutePath) &&
                           RegexUtils.match(file.name, fileRegex, false, !IS_OS_WINDOWS)
                }

                override fun accept(dir: File, name: String): Boolean {
                    return StringUtils.equals(dir.parent, baseDir.absolutePath) &&
                           RegexUtils.match(name, fileRegex, false, !IS_OS_WINDOWS) ||
                           RegexUtils.match(dir.name, fileRegex, false, !IS_OS_WINDOWS)
                }
            }
        } else subDirFilter
    }
}