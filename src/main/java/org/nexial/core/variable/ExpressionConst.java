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

package org.nexial.core.variable;

import org.nexial.commons.utils.TextUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class ExpressionConst {
    static final String DATATYPE_START = "(";
    static final String DATATYPE_END = ")";
    static final String REGEX_DEC_NUM = "^-?[0-9]*\\.[0-9]+$";
    static final String REGEX_VALID_TYPE_PREFIX = ".*\\[(";
    static final String REGEX_VALID_TYPE_SUFFIX = ")(\\(.*?\\))(\\s*\\=\\>\\s*)(.+)\\].*";
    static final Map<String, String> FUNCTION_PARAM_SUBSTITUTIONS = TextUtils.toMap("=",
                                                                                    "\\(=§•9•§",
                                                                                    "\\)=§•0•§",
                                                                                    "\\,=§•<•§",
                                                                                    "\\]=§•>•§");
    static final String ESCAPE_DELIM_SUBSTITUTION = "§•?•§";
    static final List<String> ALIAS_EMPTY = Arrays.asList("", "\"\"", "''");

    private ExpressionConst() { }
}
