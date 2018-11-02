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
import javax.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.CollectionUtil;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.tools.ScriptMetadata.Commands;
import org.nexial.core.tools.ScriptMetadata.NamedRange;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.Project.NEXIAL_HOME;
import static org.nexial.core.NexialConst.Project.appendCommandJson;

public final class CommandDiscovery {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting()
                                                     .disableInnerClassSerialization()
                                                     .setLenient()
                                                     .create();

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

    public String printDiscoveredCommands() { return GSON.toJson(toScriptMetadata()); }

    public File persistDiscoveredCommands() throws IOException {
        String nexialHome = System.getProperty(NEXIAL_HOME);
        if (StringUtils.isBlank(nexialHome)) {
            throw new IOException("Unable to persist commands: System property " + NEXIAL_HOME + " not defined");
        }

        File commandJsonFile = new File(appendCommandJson(nexialHome));
        FileUtils.write(commandJsonFile, GSON.toJson(toScriptMetadata()), DEF_FILE_ENCODING);
        return commandJsonFile;
    }

    @NotNull
    protected ScriptMetadata toScriptMetadata() {
        // prepare metadata object
        String referencePrefix = "'" + SHEET_SYSTEM + "'!$";

        List<String> targets = CollectionUtil.toList(discoveredCommands.keySet());
        Collections.sort(targets);

        ScriptMetadata metadata = new ScriptMetadata();
        metadata.setTargets(targets);
        metadata.addName(new NamedRange("target", referencePrefix + "A$2:$A$" + (targets.size() + 1)));

        for (int i = 0; i < targets.size(); i++) {
            String commandType = targets.get(i);

            List<String> commands = discoveredCommands.get(commandType);
            Collections.sort(commands);

            // add a new command set (type+commands)
            metadata.addCommands(new Commands(commandType, commands));

            // resolve [excel] named range based on command list size
            int commandSize = commands.size();
            // char columnName = (char) ('B' + i);
            String columnName = ExcelAddress.toLetterCellRef(i + 2);
            String reference = referencePrefix + columnName + "$2:$" + columnName + "$" + (commandSize + 1);
            metadata.addName(new NamedRange(commandType, reference));
        }

        return metadata;
    }
}
