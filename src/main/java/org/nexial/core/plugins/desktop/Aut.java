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

package org.nexial.core.plugins.desktop;

import java.io.Serializable;

/**
 * aka Application Under Test.
 */
class Aut implements Serializable {
	private String path;
	private String exe;
	private String dotnetConfig;
	private String args;
	private boolean terminateExisting;
	private String workingDirectory;
	private boolean runFromWorkingDirectory;

	public String getPath() { return path; }

	public void setPath(String path) { this.path = path; }

	public String getExe() { return exe; }

	public void setExe(String exe) { this.exe = exe; }

	public String getDotnetConfig() { return dotnetConfig; }

	public void setDotnetConfig(String dotnetConfig) { this.dotnetConfig = dotnetConfig; }

	public String getArgs() { return args; }

	public void setArgs(String args) { this.args = args; }

	public boolean isTerminateExisting() { return terminateExisting; }

	public void setTerminateExisting(boolean terminateExisting) { this.terminateExisting = terminateExisting; }

	public String getWorkingDirectory() { return workingDirectory; }

	public void setWorkingDirectory(String workingDirectory) { this.workingDirectory = workingDirectory; }

	public boolean isRunFromWorkingDirectory() { return runFromWorkingDirectory; }

	public void setRunFromWorkingDirectory(boolean runFromWorkingDirectory) {
		this.runFromWorkingDirectory = runFromWorkingDirectory;
	}

	@Override
	public String toString() {
		return "path='" + path + "', \n" +
		       "exe='" + exe + "', \n" +
		       "dotnetConfig='" + dotnetConfig + "', \n" +
		       "args='" + args + "', \n" +
		       "terminateExisting=" + terminateExisting + ", \n" +
		       "runFromWorkingDirectory=" + runFromWorkingDirectory;
	}
}
