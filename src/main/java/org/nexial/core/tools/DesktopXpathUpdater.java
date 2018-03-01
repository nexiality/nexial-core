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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.CharUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.nexial.commons.utils.DateUtility;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.ConsoleUtils;
import com.google.gson.JsonObject;

import static org.nexial.core.plugins.desktop.DesktopConst.GSON;
import static java.io.File.separator;

/**
 * utility to update XPaths of one or more Desktop JSON configuration files
 */
public class DesktopXpathUpdater {

	protected Logger logger = LoggerFactory.getLogger(getClass());
	protected boolean verbose;
	protected Options cmdOptions;
	private List<File> targetFiles;

	public static void main(String[] args) {
		DesktopXpathUpdater updater = newInstance(args);
		if (updater == null) { System.exit(-1); }
		updater.update();
	}

	protected void initOptions() {
		cmdOptions = new Options();
		cmdOptions.addOption("v", "verbose", false, "Turn on verbose logging.");
		cmdOptions.addOption("t", "target", true, "[REQUIRED] Location of a single JSON test script or a " +
		                                          "directory to update.");
	}

	protected void parseCLIOptions(CommandLine cmd) {
		if (!cmd.hasOption("t")) { throw new RuntimeException("[target] is a required argument and is missing"); }

		String target = cmd.getOptionValue("t");
		File targetFile = new File(target);
		if (!targetFile.exists() || !targetFile.canRead()) {
			throw new RuntimeException("specified target - " + target + " is not accessible");
		}

		targetFiles = new ArrayList<>();
		if (targetFile.isFile()) {
			if (verbose && logger.isInfoEnabled()) {
				logger.info("resolved target as a single JSON file " + targetFile);
			}
			targetFiles.add(targetFile);
		} else {
			targetFiles.addAll(
				FileUtils.listFiles(targetFile, new String[]{"json"}, true).stream()
				         .filter(file -> !file.getName().startsWith("~") &&
				                         !file.getAbsolutePath().contains(separator + "output" + separator))
				         .collect(Collectors.toList()));

			if (verbose && logger.isInfoEnabled()) {
				logger.info("resolved target as a set of " + targetFiles.size() + " JSON files");
			}
		}
	}

	protected String treatXpath(String xpath) {

		xpath = StringUtils.removeEnd(StringUtils.removeStart(xpath, CharUtils.toString('"')), CharUtils.toString('"'));
		String[] xpaths = StringUtils.split(xpath, "/*");
		List<String> newXpaths = new ArrayList<>();

		for (int i = 0; i < xpaths.length; i++) {
			String newXpath = xpaths[i];
			// considering the attribute @AutomationId could be the unique identifier, so removing @ControlType
			if (newXpath.contains("@AutomationId")) {
				String regex1 = "\\[.*?(@AutomationId='.+')(\\s+and\\s+@ControlType='.+').*?]";
				Pattern pattern1 = Pattern.compile(regex1);
				Matcher matcher1 = pattern1.matcher(newXpath);

				while (matcher1.find()) {
					newXpath = StringUtils.remove(newXpath, matcher1.group(2));
				}

				String regex2 = "\\[.*?(@ControlType='.+'\\s+and\\s+)(@AutomationId='.+').*?]";
				Pattern pattern2 = Pattern.compile(regex2);
				Matcher matcher2 = pattern2.matcher(newXpath);

				while (matcher2.find()) {
					newXpath = StringUtils.remove(newXpath, matcher2.group(1));
				}
			}

			newXpaths.add(StringUtils.prependIfMissing(newXpath, "/*"));
		}

		if (xpaths.length == newXpaths.size()) {
			StringBuilder builder = new StringBuilder();
			newXpaths.forEach(s -> builder.append(s));
			return builder.toString();
		}

		return xpath;
	}

	protected void parseCLIOptions(String[] args) {
		initOptions();

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd;
		try {
			cmd = parser.parse(cmdOptions, args);
		} catch (ParseException e) {
			throw new RuntimeException("Unable to parse commandline options: " + e.getMessage());
		}

		verbose = (cmd.hasOption("v"));

		parseCLIOptions(cmd);
	}

	protected void update() {
		int counter = 0;
		for (File targetFile : targetFiles) {
			try {
				if (backupTargetFile(targetFile)) {
					ConsoleUtils.log("Backup taken and processing the target file'" + targetFile.getName());
					JsonObject jsonObject = searchForKeyAndReplaceValue(
						GSON.fromJson(FileUtils.readFileToString(targetFile, "UTF-8"), JsonObject.class), "xpath");
					FileUtils.writeStringToFile(targetFile, GSON.toJson(jsonObject), "UTF-8");
					if (targetFile.canRead()) { counter++; } else {
						ConsoleUtils.error("Target file '" + targetFile.getName() + " not processed successfully");
					}
				} else {
					ConsoleUtils.error("Backup could not be taken. Skipped processing the target file '" +
					                 targetFile.getName() + "'");
				}

			} catch (IOException e) {
				CheckUtils.fail("Unable to process the target file '" + targetFile + "'");
			}
		}
		ConsoleUtils.log("Total " + counter + " files processed successfully.");
	}

	private static DesktopXpathUpdater newInstance(String[] args) {
		DesktopXpathUpdater updater = new DesktopXpathUpdater();
		try {
			updater.parseCLIOptions(args);
			return updater;
		} catch (Exception e) {
			System.err.println("\nERROR: " + e.getMessage() + "\n");
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(DesktopXpathUpdater.class.getName(), updater.cmdOptions, true);
			return null;
		}
	}

	private boolean backupTargetFile(File targetFile) {

		File backupFile =
			new File(targetFile.getParentFile().getAbsolutePath() + "_backup_" + DateUtility.format(
				Timestamp.from(Instant.now()), "yyyyMMdd_hhmmss") + "\\" + targetFile.getName());

		try {
			FileUtils.copyFile(targetFile, backupFile);
			return backupFile.canRead();
		} catch (IOException e) {
			CheckUtils.fail("Unable to backup the target file '" + targetFile + "'");
			return false;
		}
	}

	private JsonObject searchForKeyAndReplaceValue(JsonObject jsonObject, String searchKey) {
		Set<String> keySet = jsonObject.keySet();
		for (String key : keySet) {
			if (jsonObject.get(key).isJsonObject()) {
				jsonObject.add(key, searchForKeyAndReplaceValue((JsonObject) jsonObject.get(key), searchKey));
				continue;
			}

			if (key.equals(searchKey) && jsonObject.get(searchKey).isJsonPrimitive()) {
				jsonObject.addProperty(key, treatXpath(jsonObject.get(key).toString()));
			}
		}

		return jsonObject;
	}
}
