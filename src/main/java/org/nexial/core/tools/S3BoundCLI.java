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

package org.nexial.core.tools;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.aws.S3Support;

public abstract class S3BoundCLI extends S3Support {
    protected Options cmdOptions;

    // bucket + folder
    protected String s3path;

    protected void initOptions() {
        cmdOptions = new Options();
        cmdOptions.addOption("v", "verbose", false, "Turn on verbose logging.");
    }

    protected void parseCLIOptions(String[] args) {
        initOptions();

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(cmdOptions, args);
        } catch (ParseException e) {
            throw new RuntimeException("Unable to parse commandline options: " + e.getMessage());
        }

        setVerbose(cmd.hasOption("v"));
        if (cmd.hasOption("ak")) { setAccessKey(cmd.getOptionValue("ak")); }
        if (cmd.hasOption("sk")) { setSecretKey(cmd.getOptionValue("sk")); }
        s3path = StringUtils.replace(cmd.getOptionValue("s3"), "\\", "/");

        parseCLIOptions(cmd);
    }

    protected String getS3path() { return s3path; }

    protected abstract void parseCLIOptions(CommandLine cmd);
}
