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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.Project.*;

public final class CommandDiscovery {
    private static CommandDiscovery instance;
    private Map<String, List<String>> discoveredCommands;

    public static CommandDiscovery getInstance() {
        if (instance == null) {
            instance = new CommandDiscovery();
            instance.discoveredCommands = new HashMap<>();
        }
        return instance;
    }

    public static boolean isInDiscoveryMode() {
        return BooleanUtils.toBoolean(System.getProperty(COMMAND_DISCOVERY_MODE, DEF_COMMAND_DISCOVERY_MODE));
    }

    public static boolean shouldSaveDiscoveredCommands() {
        return BooleanUtils.toBoolean(System.getProperty(COMMAND_DISCOVERY_WRITE_TO_FILE,
                                                         DEF_COMMAND_DISCOVERY_WRITE_TO_FILE));
    }

    public void addCommand(String target, String commandSignature) {
        List<String> signatures;
        if (discoveredCommands.containsKey(target)) {
            signatures = discoveredCommands.get(target);
        } else {
            signatures = new ArrayList<>();
            discoveredCommands.put(target, signatures);
        }

        signatures.add(commandSignature);
    }

    public void printDiscoveredCommands() {
        StringBuilder buffer = new StringBuilder();

        buffer.append("targets.txt\n");
        discoveredCommands.keySet().forEach(target -> buffer.append(target).append("\n"));

        discoveredCommands.keySet().forEach(target -> {
            buffer.append("\n").append(target).append(".commands.txt\n");
            printCommandDiscovery(target, buffer);
        });

        buffer.append("\n");

        System.out.println(buffer);
    }

    public void persistDiscoveredCommands() {
        String nexialHome = System.getProperty(NEXIAL_HOME);
        if (StringUtils.isBlank(nexialHome)) {
            System.err.println("Unable to save discovered commands - System property " + NEXIAL_HOME + " not defined");
            return;
        }

        // ensure base directory is available
        File baseDir = new File(appendCommands(nexialHome));
        try {
            FileUtils.forceMkdir(baseDir);
        } catch (IOException e) {
            System.err.println("Unable to create directory " + baseDir + ": " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // create target.txt
        StringBuilder buffer = new StringBuilder();
        discoveredCommands.keySet().forEach(target -> buffer.append(target).append("\n"));
        File targetFile = new File(appendCommandTargets(nexialHome));
        try {
            FileUtils.writeStringToFile(targetFile, buffer.toString(), DEF_CHARSET);
        } catch (IOException e) {
            System.err.println("Unable to write to " + targetFile + ": " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // create command files
        discoveredCommands.keySet().forEach(target -> {
            StringBuilder cmdBuffer = new StringBuilder();
            printCommandDiscovery(target, cmdBuffer);

            File commandFile = new File(appendCommandText(nexialHome, target));
            try {
                FileUtils.writeStringToFile(commandFile, cmdBuffer.toString(), DEF_CHARSET);
            } catch (IOException e) {
                System.err.println("Unable to write to " + commandFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    protected void printCommandDiscovery(String target, StringBuilder output) {
        List<String> commands = discoveredCommands.get(target);
        Collections.sort(commands);
        commands.forEach(command -> output.append(command).append("\n"));
    }
}
