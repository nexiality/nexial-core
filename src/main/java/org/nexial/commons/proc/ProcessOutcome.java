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

package org.nexial.commons.proc;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class encapsulates the 'outcome' of invoking a process outside of the current VM.
 * <p/>
 * The definition of an 'outcome', in this context, is as follows:<ol>
 * <li>content of the STDOUT caused by the invoked process</li>
 * <li>content of the STDERR caused by the invoked process</li>
 * <li>the exit status of the invoked process</li>
 * </ol>
 * <p/>
 * In addition, as a way of reference, this class also includes the original command invoked, along with the arguments
 * passed to the command and any changes made to that process' environment.
 */
public class ProcessOutcome implements Serializable {
    private static final long serialVersionUID = -7278856198840371344L;
    private String stdout;
    private String stderr;
    private int exitStatus;
    private String command;
    private List<String> arguments;
    private Map<String, String> environment = new HashMap<>();

    public String getStdout() { return stdout; }

    public void setStdout(String stdout) { this.stdout = stdout; }

    public String getStderr() { return stderr; }

    public void setStderr(String stderr) { this.stderr = stderr; }

    /** @return exit status - 0 means normal exit, otherwise means abnormal exit. */
    public int getExitStatus() { return exitStatus; }

    public void setExitStatus(int exitStatus) { this.exitStatus = exitStatus; }

    public String getCommand() { return command; }

    public void setCommand(String command) { this.command = command; }

    public List getArguments() { return arguments; }

    public void setArguments(List<String> arguments) { this.arguments = arguments; }

    /** @return a list of the modified/added environment variables to this process prior to invocation. */
    public Map getEnvironment() { return environment; }

    public void setEnvironment(Map<String, String> environment) { this.environment = environment; }

    public String toString() {
        return "ProcessOutcome{" +
               "\n\tcommand    = " + command +
               "\n\targuments  = " + arguments +
               "\n\tenvironment= " + environment +
               "\n\texitStatus = " + exitStatus +
               "\n\tstdout     = " + stdout +
               "\n\tstderr     = " + stderr +
               "\n}";
    }
}
