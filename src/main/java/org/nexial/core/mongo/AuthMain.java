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

package org.nexial.core.mongo;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.DEF_CHARSET;

/**
 *
 */
public class AuthMain {

	enum Command {reset, show, upsert, count_active}

	@SuppressWarnings("PMD.DoNotCallSystemExit")
	public static void main(String[] args) throws Exception {
		ApplicationContext springContext = new ClassPathXmlApplicationContext("classpath:/nexial-mongo.xml");

		AuthManager authManager = springContext.getBean("authManager", AuthManager.class);

		int argsLength = ArrayUtils.getLength(args);
		if (argsLength != 2) {
			showError();
			return;
		}

		String realm = null;
		Command command = null;
		File inputFile = null;

		String[] params = args;
		for (String param : params) {
			if (StringUtils.startsWith(param, "realm=")) {
				realm = StringUtils.substringAfter(param, "realm=");
				continue;
			}
			if (StringUtils.equals(param, Command.reset.name())) {
				command = Command.reset;
				continue;
			}
			if (StringUtils.equals(param, Command.show.name())) {
				command = Command.show;
				continue;
			}
			if (StringUtils.equals(param, Command.count_active.name())) {
				command = Command.count_active;
				continue;
			}

			inputFile = new File(param);
			if (!inputFile.exists() || !inputFile.canRead()) {
				ConsoleUtils.error("Unable to read input file " + param);
				showError();
				return;
			} else {
				command = Command.upsert;
			}
		}

		if (command != null) {
			authManager.setRealm(realm);
			switch (command) {
				case reset: {
					authManager.removeAllAuth();
					break;
				}
				case show: {
					authManager.printCurrentAuth();
					break;
				}
				case count_active: {
					ConsoleUtils.log("Total active user count: " + authManager.getActiveUserCount());
					break;
				}
				case upsert: {
					String content = FileUtils.readFileToString(inputFile, DEF_CHARSET);
					JSONArray jsonArray = new JSONArray(content);
					authManager.upsertAuth(jsonArray);
					break;
				}
				default: {
					ConsoleUtils.error("Unknown/unsupported command: " + command);
					showError();
					break;
				}
			}
		}

		System.exit(0);
	}

	@SuppressWarnings("PMD.DoNotCallSystemExit")
	protected static void showError() {
		ConsoleUtils.error("Invalid argument found");
		ConsoleUtils.log("USAGE: java ... " + AuthManager.class.getName() +
		                 " realm=[realm] [input_file | reset | show | count_active ]\n");
		System.exit(-1);
	}
}
