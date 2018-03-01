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

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.tools.ScriptMetadata.Commands;
import org.nexial.core.tools.ScriptMetadata.NamedRange;

import com.amazonaws.services.s3.model.PutObjectResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static java.io.File.separator;

/**
 * utility to create Nexial commands as a JSON file (metadata) and optionally update it to AWS S3.
 */
public class ScriptMetadataUpdater extends S3BoundCLI {
    private String commandPath;
    private String targetFile;
    private String outputFile;
    private String commandFileSuffix;
    private String systemSheetName;
    private boolean disableCopyToS3;

    public void setCommandPath(String commandPath) {
        this.commandPath = StringUtils.appendIfMissing(commandPath, separator);
    }

    public void setTargetFile(String targetFile) { this.targetFile = targetFile; }

    public void setOutputFile(String outputFile) { this.outputFile = outputFile; }

    public void setCommandFileSuffix(String commandFileSuffix) { this.commandFileSuffix = commandFileSuffix; }

    public void setSystemSheetName(String systemSheetName) { this.systemSheetName = systemSheetName; }

    public void setDisableCopyToS3(boolean disableCopyToS3) { this.disableCopyToS3 = disableCopyToS3; }

    public static void main(String[] args) throws Exception {
        ScriptMetadataUpdater updater = newInstance(args);
        if (updater == null) { System.exit(-1); }
        updater.copyToS3(updater.createMetadata(), updater.getS3path());
    }

    @Override
    protected void initOptions() {
        super.initOptions();
        cmdOptions.addOption("p", "path", true, "[REQUIRED] The directory that contains all the command files. ");
        cmdOptions.addOption("t", "targets", true, "The file name that contains the available test targets. " +
                                                   "[default]: " + defaultTargetsFile);
        cmdOptions.addOption("o", "out", true, "The JSON output that combines all available targets, the " +
                                               "associated commands and named ranges. [default]: " + defaultMetadata);
        cmdOptions.addOption("x", "suffix", true, "The suffix of all command files. [default]: " + defaultCmdSuffix);
        cmdOptions.addOption("s", "sheet", true, "The Excel sheet name to save all the targets and commands. " +
                                                 "[default]: " + defaultSheetName);
        cmdOptions.addOption("nc", "nocopy", false, "Turn off the copying of generated output file to S3.");
    }

    @Override
    protected void parseCLIOptions(CommandLine cmd) {
        if (cmd.hasOption("p")) {
            setCommandPath(cmd.getOptionValue("p"));
        } else {
            throw new RuntimeException("[path] is a required argument and is missing");
        }

        setTargetFile(cmd.getOptionValue("t", defaultTargetsFile));
        setOutputFile(cmd.getOptionValue("o", defaultMetadata));
        setCommandFileSuffix(cmd.getOptionValue("x", defaultCmdSuffix));
        setSystemSheetName(cmd.getOptionValue("s", defaultSheetName));
        setDisableCopyToS3(cmd.hasOption("nc"));
    }

    @Override
    protected PutObjectResult copyToS3(File from, String to) throws IOException {
        if (disableCopyToS3) {
            if (verbose && logger.isInfoEnabled()) { logger.info("copy-to-s3 disabled as per specified."); }
            return null;
        }

        return super.copyToS3(from, to);
    }

    private static ScriptMetadataUpdater newInstance(String[] args) {
        ScriptMetadataUpdater updater = new ScriptMetadataUpdater();
        try {
            updater.parseCLIOptions(args);
            return updater;
        } catch (Exception e) {
            System.err.println("\nERROR: " + e.getMessage() + "\n");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(ScriptMetadataUpdater.class.getName(), updater.cmdOptions, true);
            return null;
        }
    }

    private File createMetadata() throws IOException {
        String targetFullpath = commandPath + targetFile;
        if (verbose && logger.isInfoEnabled()) { logger.info("target-file resolved as " + targetFullpath); }

        if (!FileUtil.isFileReadable(targetFullpath)) {
            throw new IOException("String command target '" + targetFullpath + "' is not accessible");
        }

        List<String> targets = FileUtils.readLines(new File(targetFullpath), encoding);
        Collections.sort(targets);
        int targetCount = targets.size();

        String referencePrefix = "'" + systemSheetName + "'!$";

        ScriptMetadata metadata = new ScriptMetadata();
        metadata.setTargets(targets);
        metadata.addName(new NamedRange("target", referencePrefix + "A$2:$A$" + (targetCount + 1)));

        if (verbose && logger.isInfoEnabled()) {
            logger.info("found " + targetCount + " targets in " + targetFullpath);
        }

        for (int i = 0; i < targetCount; i++) {
            String target = targets.get(i);

            String commandFile = commandPath + target + commandFileSuffix;
            if (!FileUtil.isFileReadable(commandFile)) {
                if (logger.isInfoEnabled()) { logger.info("command file " + commandFile + " not accessible, skipping");}
                continue;
            }

            try {
                List<String> commands = FileUtils.readLines(new File(commandFile), encoding);
                Collections.sort(commands);
                metadata.addCommands(new Commands(target, commands));

                int commandSize = commands.size();
                char columnName = (char) ('B' + i);
                String reference = referencePrefix + columnName + "$2:$" + columnName + "$" + (commandSize + 1);
                metadata.addName(new NamedRange(target, reference));
                if (logger.isInfoEnabled()) { logger.info("found " + commandSize + " commands in " + commandFile); }
            } catch (IOException e) {
                logger.error("command file '" + commandFile + "' failed to read: " + e.getMessage(), e);
            }
        }

        return writeJson(metadata);
    }

    private File writeJson(ScriptMetadata metadata) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(metadata);
        File metadataFile = new File(commandPath + separator + outputFile);
        FileUtils.write(metadataFile, json, encoding);
        if (logger.isInfoEnabled()) { logger.info("write targets and commands to " + metadataFile.getAbsolutePath()); }
        return metadataFile;
    }
}
