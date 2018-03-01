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

import javax.validation.constraints.NotNull;

import org.apache.commons.cli.*;
import org.apache.commons.cli.Option.Builder;
import org.apache.commons.lang3.StringUtils;

/**
 * Various utilities classes used all across tools package.
 */
final public class CliUtils {
    /**
     * Creates a {@link Option} with the values passed in i.e. opt, description and longOpt.
     *
     * @param opt         opt value of {@link Option}.
     * @param longOpt     longOpt of {@link Option}.
     * @param description description of {@link Option}.
     * @return {@link Option} value created.
     */
    @NotNull
    public static Option newArgOption(@NotNull final String opt, String longOpt, String description) {
        Builder builder = Option.builder(opt).required(true).hasArg(true);
        if (StringUtils.isNotBlank(longOpt)) { builder = builder.longOpt(longOpt); }
        if (StringUtils.isNotBlank(description)) { builder = builder.desc(description); }
        return builder.build();
    }

    /**
     * Retrieves the {@link CommandLine} class with the options specified as opts.
     *
     * @param args       Command line args passed in main method.
     * @param cmdOptions {@link Options} passed in.
     * @return {@link CommandLine} created.
     */
    public static CommandLine getCommandLine(String name, String[] args, @NotNull Options cmdOptions) {
        try {
            return new DefaultParser().parse(cmdOptions, args);
        } catch (ParseException e) {
            System.err.println("Unable to parse commandline options");

            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(name, cmdOptions, true);

            return null;
        }
    }
}
