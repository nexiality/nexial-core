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

import java.util.ArrayList;
import java.util.List;

public class ScriptMetadata {
    private List<String> targets = new ArrayList<>();
    private List<Commands> commands = new ArrayList<>();
    private List<NamedRange> names = new ArrayList<>();

    public static class Commands {
        private String name;
        private List<String> commands = new ArrayList<>();

        public Commands() { }

        public Commands(String name, List<String> commands) {
            this.name = name;
            this.commands = commands;
        }

        public String getName() { return name; }

        public void setName(String name) { this.name = name; }

        public List<String> getCommands() { return commands; }

        public void setCommands(List<String> commands) { this.commands = commands; }

        public void addCommand(String command) { this.commands.add(command); }
    }

    public static class NamedRange {
        private String name;
        private String reference;

        public NamedRange() { }

        public NamedRange(String name, String reference) {
            this.name = name;
            this.reference = reference;
        }

        public String getName() { return name; }

        public void setName(String name) { this.name = name; }

        public String getReference() { return reference; }

        public void setReference(String reference) { this.reference = reference; }
    }

    public List<String> getTargets() { return targets; }

    public void setTargets(List<String> targets) { this.targets = targets; }

    public void addTarget(String target) { this.targets.add(target); }

    public List<Commands> getCommands() { return commands; }

    public void setCommands(List<Commands> commands) { this.commands = commands; }

    public void addCommands(Commands commands) { this.commands.add(commands); }

    public List<NamedRange> getNames() { return names; }

    public void setNames(List<NamedRange> names) { this.names = names; }

    public void addName(NamedRange name) { this.names.add(name); }
}
