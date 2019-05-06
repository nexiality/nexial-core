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
 */

package org.nexial.core.tools;

import org.apache.commons.cli.Option;

import static org.nexial.core.tools.CliUtils.newArgOption;
import static org.nexial.core.tools.CliUtils.newNonArgOption;

public class CliConst {

    // common
    public static final Option OPT_VERBOSE = newNonArgOption("v", "verbose", "Turn on verbose logging.", false);

    // data variable updater
    public static final Option OPT_PREVIEW =
        newNonArgOption("p", "preview", "Preview actions/changes (will not save to files)", false);

    // public static final Option OPT_DATA = newArgOption("d", "data", "[REQUIRED] Data variables to replace, in the form of old_var=new_var;old_var2=new_var2", true);
    public static final Option OPT_TARGET = newArgOption("t", "target",
                                                         "[REQUIRED] Starting location of update data variable.", true);

    // nexial setup
    // public static final Option OPT_FILE = newArgOption("f", "file", "[REQUIRED] The file containing key/value pairs.", true);
    // public static final Option OPT_KEY = newArgOption("k", "key", "[REQUIRED] The key to encrypt data.", true);

    // public static final Option OPT_VERBOSE = new Option();


}
