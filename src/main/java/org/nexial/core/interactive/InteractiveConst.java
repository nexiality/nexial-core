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

package org.nexial.core.interactive;

import org.apache.commons.lang3.BooleanUtils;

import org.nexial.core.utils.ExecUtil;

import static org.nexial.core.NexialConst.OPT_INTERACTIVE_DEBUG;

public final class InteractiveConst {
	public static final int HISTORY_SIZE = 20;
	public static final String ARG_SEP = "=";
	public static final String RUN_ARG_SEPARATOR = ",";
	public static final String RUN_ARG_RANGE_SEPARATOR = "-";

	public static final boolean DEBUG = BooleanUtils.toBoolean(System.getProperty(OPT_INTERACTIVE_DEBUG, "false"));

	public enum Command {
		quit, help, read, set, browser, run, recall, history
	}

	public static final class Menu {
		public static final String FEATURE = "Nexial Interactive";
		public static final String TITLE = " " + FEATURE + " (" + ExecUtil.deriveJarManifest() + ") ";
		public static final String PROMPT = FEATURE + " > ";

		public static final String DOT = "~";
		public static final String BANNER_PREFIX = "~";
		public static final String MENU_PREFIX = "~   ";
		public static final int PREFIX_COUNT = 3;

		public static final int BANNER_WIDTH = 80;
		public static final String BANNER_WIDTH_TEXT = BANNER_WIDTH + "";

		public static final String EXT = ".txt";
		public static final String MAN_PAGE = "man.txt";
		public static final String COMMANDS_PAGE = "commands.txt";

		private Menu() {}
	}

	private InteractiveConst() {}
}
