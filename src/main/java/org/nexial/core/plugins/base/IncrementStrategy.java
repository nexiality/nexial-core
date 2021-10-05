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

package org.nexial.core.plugins.base;

import org.apache.commons.lang3.StringUtils;

public enum IncrementStrategy {
    ALPHANUM("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"),
    UPPER("ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
    LOWER("abcdefghijklmnopqrstuvwxyz");

    private String range;

    IncrementStrategy(String range) { this.range = range; }

    public String increment(String startVal, int amount) {
        return increment(startVal, 1, amount);
    }

    public String increment(String base, int rightMostPos, int nextIndex) {
        // if (nextIndex == 0) { return base; }

        String leftMost = StringUtils.substring(base, 0, base.length() - rightMostPos);
        String currentChar = StringUtils.substring(base,
                                                   base.length() - rightMostPos,
                                                   base.length() - rightMostPos + 1);
        String rightMost = StringUtils.substring(base, base.length() - rightMostPos + 1);

        int matchingPos = StringUtils.indexOf(range, currentChar);
        if (matchingPos != -1) { nextIndex += matchingPos; }

        if (nextIndex < range.length()) {
            return leftMost + StringUtils.substring(range, nextIndex, nextIndex + 1) + rightMost;
        }

        int currentIndex = nextIndex % range.length();
        nextIndex = nextIndex / range.length() - 1;

        String defaultNewBase = StringUtils.left(range, 1);

        // special case
        if (currentIndex == 0 && nextIndex == 0 && StringUtils.isNotEmpty(leftMost)) { leftMost = defaultNewBase; }

        return increment(StringUtils.defaultIfEmpty(leftMost, defaultNewBase), 1, nextIndex) +
               StringUtils.substring(range, currentIndex, currentIndex + 1) +
               rightMost;

    }
}
