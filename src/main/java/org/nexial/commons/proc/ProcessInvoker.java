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

package org.nexial.commons.proc;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.nexial.commons.utils.FileUtil;

/**
 * A fairly straight-forward of invoking another process from current JVM process.  This class assumes 3 arguments,
 * namely: <ol>
 * <li><b>command</b> - the command to execute. NOTE: THIS CLASS MAKES NO ASSUMPTION REGARDING OS/PLATFORM, THUS THE
 * COMMAND MUST BE VALID TO THE CURRENT PLATFORM.</li>
 * <li><b>command arguments</b> - the argument to append to the target command.</li>
 * <li><b>environment properties</b> - the environment to use during the execution of the target command.  Only the
 * modification from the existing environment need to be passed in.  Otherwise, all existing environment as per
 * current run-user will be assumed.</li>
 * </ol>
 * In return, this class will process the target command, along with the specified arguments and environment properties,
 * and return an instance of {@link ProcessOutcome}.  This object will contain the output
 * of stdout and stderr, as well as the exit value of the target command.  In addition, the command, argument and
 * environment properties are also stored with the {@link ProcessOutcome}, thus making
 * this class friendly to a multi-step invocation runtime.
 */
public class ProcessInvoker {
	public static final String WORKING_DIRECTORY = "[working.directory]";

	@SuppressWarnings("PMD.DoNotUseThreads")
	/** internally class to trap stdout/stderr output. */
	private static class StreamGobbler extends Thread {
		private static final int DEFAULT_SIZE = 32 * 1024;
		private static final String NL = System.getProperty("line.separator");
		private String output;
		private BufferedReader reader;
		private InputStreamReader streamReader;
		private StringWriter stringWriter;

		StreamGobbler(InputStream is) {
			streamReader = new InputStreamReader(is);
			reader = new BufferedReader(streamReader);
			stringWriter = new StringWriter(DEFAULT_SIZE);
		}

		public void run() {
			try {
				String line;
				while ((line = reader.readLine()) != null) {
					stringWriter.write(line);
					stringWriter.write(NL);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				output = stringWriter.toString();

				// clean up -- no need to catch exception since it's likely that the source process has finished its work
				// and the underlying streams are being released without Java's knowledge.
				try { streamReader.close(); } catch (IOException e) { }
				try { reader.close(); } catch (IOException e) { }
				try { stringWriter.close(); } catch (IOException e) { }
				//try { writer.close(); } catch (IOException e) { }
			}
		}

		public String getOutput() { return output; }
	}

	private ProcessInvoker() {}

	/**
	 * the main method to invoke the external process.
	 *
	 * @param command the command to execute.
	 * @param params  the parameters to be passed to the command.
	 * @param env     the environment variables to be updated/added to the existing one.
	 * @return the outcome of invoking the external process.
	 * @see ProcessOutcome
	 */
	public static ProcessOutcome invoke(String command, List<String> params, Map<String, String> env)
		throws IOException, InterruptedException {
		ProcessOutcome outcome = new ProcessOutcome();
		outcome.setCommand(command);
		outcome.setArguments(params);

		// add command to the beginning of params, so that we can pass the whole thing off to processbuilder
		List<String> processArg = new ArrayList<>(params);
		processArg.add(0, command);

		// create processbuilder and mod. environment, if need be
		ProcessBuilder pb = new ProcessBuilder(processArg);
		String[] envStrings = prepareEnv(pb, env, outcome);

		// here we go...
		// jdk5-specific...
		Process process = pb.start();

		StreamGobbler stderr = new StreamGobbler(process.getErrorStream());
		stderr.start();
		StreamGobbler stdout = new StreamGobbler(process.getInputStream());
		stdout.start();

		int exitValue = 0;
		try {
			// give more time to the spawn process.
			Thread.sleep(1000);

			exitValue = process.waitFor();
		} finally {
			// collect result
			outcome.setStderr(stderr.getOutput());
			outcome.setStdout(stdout.getOutput());
			outcome.setExitStatus(exitValue);

			// be a good java citizen
			stderr = null;
			stdout = null;
			process = null;
		}

		return outcome;
	}

	public static void invokeNoWait(String command, List<String> params, Map<String, String> env)
		throws IOException {
		ProcessOutcome outcome = new ProcessOutcome();
		outcome.setCommand(command);
		outcome.setArguments(params);

		// add command to the beginning of params, so that we can pass the whole thing off to processbuilder
		List<String> processArg = new ArrayList<>(params);
		processArg.add(0, command);

		// create processbuilder and mod. environment, if need be
		ProcessBuilder pb = new ProcessBuilder(processArg);
		prepareEnv(pb, env, outcome);

		// here we go...
		// jdk5-specific...
		pb.start();
	}

	private static String[] prepareEnv(ProcessBuilder pb, Map<String, String> env, ProcessOutcome outcome) {
		if (!MapUtils.isNotEmpty(env)) { return null; }

		if (env.containsKey(WORKING_DIRECTORY)) {
			String workingDirectory = env.get(WORKING_DIRECTORY);
			if (FileUtil.isDirectoryReadable(workingDirectory)) { pb = pb.directory(new File(workingDirectory)); }
		}

		Map<String, String> pEnv = pb.environment();

		String[] envStrings = new String[env.size()];
		Object[] keys = env.keySet().toArray();
		for (int i = 0; i < keys.length; i++) {
			String envName = (String) keys[i];
			String envValue = env.get(envName);
			pEnv.put(envName, envValue);
			envStrings[i] = envName + "=" + envValue;
		}
		outcome.setEnvironment(pEnv);

		return envStrings;
	}
}
