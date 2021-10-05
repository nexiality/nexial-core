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

package org.nexial.core.tms.tools;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.tms.spi.TmsProcessor;
import org.nexial.core.utils.InputFileUtils;

import static org.nexial.core.tms.TmsConst.*;

/**
 * Imports the Nexial Test file provided as the argument to test rail as a suite, if the file has not been imported before
 * creates a new Test Suite, otherwise updates the existing one
 */
public class TmsImporter {

    public static void main(String[] args) throws ParseException {
        CommandLine cmd = new DefaultParser().parse(initCmdOptions(), args);
        String filepath = "";
        String subplan = "";
        List<String> scenarios = new ArrayList<>();
        if (cmd.hasOption(SCRIPT) && cmd.hasOption(PLAN)) {
            System.err.println("Only one type of test file is allowed per input");
            System.exit(-1);
        }
        if (cmd.hasOption(SCRIPT)) {
            filepath = cmd.getOptionValue(SCRIPT);
            if (!InputFileUtils.isValidScript(filepath)) {
                System.err.println("Invalid test script - " + filepath);
                System.exit(-1);
            }
            if (cmd.hasOption(SCENARIO)) {
                scenarios = TextUtils.toList(cmd.getOptionValue(SCENARIO), ",", true);
            }
        } else if (cmd.hasOption(PLAN) && cmd.hasOption(SUBPLAN)) {
            filepath = cmd.getOptionValue(PLAN);
            subplan  = cmd.getOptionValue(SUBPLAN);
            if (!InputFileUtils.isValidPlanFile(filepath)) {
                System.err.println(
                        "specified test plan (" + filepath + ") is not readable or does not contain valid format.");
                System.exit(-1);
            }
        } else {
            System.err.println(
                    "The location of the Nexial script/plan file is required. In case of Plan file, subplan is required");
            System.exit(-1);
        }
        new TmsProcessor().importToTms(filepath, subplan, scenarios);
    }

    /**
     * Command line options
     */
    private static Options initCmdOptions() {
        Options cmdOptions = new Options();
        cmdOptions.addOption(SCRIPT, true, "[REQUIRED] if -" + PLAN + " is missing] The fully qualified " +
                                           "path of the test script.");
        cmdOptions.addOption(PLAN, true, "[REQUIRED if -" + SCRIPT + " is missing] The fully qualified path of a " +
                                         "test plan.");
        cmdOptions.addOption(SUBPLAN, true, "[REQUIRED] if -" + PLAN + "is present.The name of the test plan");
        cmdOptions.addOption(SCENARIO, true, "[OPTIONAL] if -" + SCRIPT +
                                             "is present.The name of the scenarios which need to be updated");
        return cmdOptions;
    }
}
