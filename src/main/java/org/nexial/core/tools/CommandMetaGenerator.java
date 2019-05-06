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

import java.io.File;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.collections4.MapUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.plugins.NexialCommand;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.nexial.core.NexialConst.DEF_SPRING_XML;
import static org.nexial.core.NexialConst.Data.COMMAND_DISCOVERY_MODE;
import static org.nexial.core.NexialConst.ExitStatus.RC_FILE_GEN_FAILED;
import static org.nexial.core.NexialConst.OPT_SPRING_XML;
import static org.nexial.core.tools.VarCommandGenerator.generateVarCommands;

public class CommandMetaGenerator {
    private static final Options cmdlineOptions = new Options();

    private boolean verbose;
    private boolean dryRun;

    public static void main(String[] args) throws Exception {
        cmdlineOptions.addOption("v", "verbose", false, "Turn on verbose logging.");
        cmdlineOptions.addOption("d", "dryrun", false, "Simulate generation by writing output to console ONLY");

        CommandMetaGenerator generator = newInstance(args);
        if (generator == null) { System.exit(-1); }

        generator.generate();
        generateVarCommands();
    }

    public void setVerbose(boolean verbose) { this.verbose = verbose;}

    public void setDryRun(boolean dryRun) { this.dryRun = dryRun;}

    private static CommandMetaGenerator newInstance(String[] args) {
        CommandLine cmd;
        try {
            cmd = new DefaultParser().parse(cmdlineOptions, args);
        } catch (ParseException e) {
            throw new RuntimeException("Unable to parse commandline options: " + e.getMessage());
        }

        CommandMetaGenerator generator = new CommandMetaGenerator();
        generator.setVerbose(cmd.hasOption("v"));
        generator.setDryRun(cmd.hasOption("d"));

        return generator;
    }

    private void generate() throws Exception {
        // turn on discovery mode so that command discovery can be enabled during spring init
        System.setProperty(COMMAND_DISCOVERY_MODE, "true");

        // load spring context in order to discover all plugins (aka commands)
        ClassPathXmlApplicationContext springContext = new ClassPathXmlApplicationContext(
            "classpath:" + System.getProperty(OPT_SPRING_XML, DEF_SPRING_XML));
        if (!springContext.containsBean("plugins")) {
            System.err.println("Unable to generate command meta file since no 'plugins' are defined");
            System.exit(RC_FILE_GEN_FAILED);
        }

        Map<String, NexialCommand> plugins = springContext.getBean("plugins", Map.class);
        if (MapUtils.isEmpty(plugins)) {
            System.err.println("No plugins configured.  No command list generated");
            return;
        }

        if (verbose) { System.out.println("Found " + plugins.size() + " plugin(s)..."); }

        CommandDiscovery discovery = CommandDiscovery.getInstance();

        if (dryRun) {
            System.out.println("DRY-RUN: printing out discovered commands and exit");
            System.out.println(discovery.printDiscoveredCommands());
            return;
        }

        File commandJson = discovery.persistDiscoveredCommands();
        if (commandJson == null) {
            System.err.println("Unable to generate command meta file!");
            System.exit(RC_FILE_GEN_FAILED);
        }

        if (!FileUtil.isFileReadable(commandJson.getAbsolutePath(), 1024)) {
            System.err.println("Generated command meta file is not readable or is invalid: " + commandJson);
            System.exit(RC_FILE_GEN_FAILED);
        }

        if (verbose) { System.out.println("Command meta file generated: " + commandJson.getAbsolutePath()); }
    }
}
