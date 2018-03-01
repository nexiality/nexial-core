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
    protected String defaultMetadata;
    protected String defaultDestination;
    protected String defaultSheetName;
    protected String defaultTargetsFile;
    protected String defaultCmdSuffix;

    // bucket + folder
    protected String s3path;

    protected S3BoundCLI() {
        super();
        defaultMetadata = springContext.getBean("defaultMetadataFileName", String.class);
        defaultDestination = springContext.getBean("defaultMetadataDestination", String.class);
        defaultSheetName = springContext.getBean("defaultSheetName", String.class);
        defaultTargetsFile = springContext.getBean("defaultTargetsFile", String.class);
        defaultCmdSuffix = springContext.getBean("defaultCmdSuffix", String.class);
    }

    protected void initOptions() {
        cmdOptions = new Options();
        cmdOptions.addOption("v", "verbose", false, "Turn on verbose logging.");
        // cmdOptions.addOption("ak", "accesskey", true, "The access key to use to access S3 bucket. " +
        //                                               "[default]: system default");
        // cmdOptions.addOption("sk", "secretkey", true, "The secret key to use to access S3 bucket. " +
        //                                               "[default]: system default");
        // cmdOptions.addOption("s3", "s3path", true, "The path in AWS S3 to store/retrieve the file in question. " +
        //                                            "[default]: " + defaultDestination);
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
        s3path = StringUtils.replace(cmd.getOptionValue("s3", defaultDestination), "\\", "/");

        parseCLIOptions(cmd);
    }

    protected String getS3path() { return s3path; }

    protected abstract void parseCLIOptions(CommandLine cmd);
}
