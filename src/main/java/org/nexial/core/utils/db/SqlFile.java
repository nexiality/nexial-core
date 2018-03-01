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

package org.nexial.core.utils.db;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import static org.nexial.core.NexialConst.DEF_CHARSET;

/**
 * General utility to read/parse a file into a serires of SQL statements.  Current implementation separates
 * statements by {@code STATEMENT_DELIMITER} (must be the last printable character of the line) and consider
 * any line starting with {@code COMMENT} as comments.
 * <p/>
 * Comment lines are ignored while the rest will be captured as a sequential list.
 * <p/>
 * Note that this implementation does not process the SQL statements it parses.  Also, current implementation does
 * not separate statements correctly when {@code STATEMENT_DELIMITER} is found in the middle of a line of text.  The
 * {@code STATEMENT_DELIMITER} is expected as the last character of a line (space ok).
 *
 * $
 */
public final class SqlFile {
	public static final String STATEMENT_DELIMITER = ";";
	public static final String COMMENT = "--";
	private File file;
	private List<String> statements = new ArrayList<>();

	private SqlFile() { }

	public static SqlFile newInstance(File file) throws IOException {
		if (!file.exists() || !file.canRead()) {
			throw new IOException("Cannot read/access SQL file '" + file.getAbsolutePath() + "'.");
		}

		SqlFile f = new SqlFile();
		f.parse(file);
		return f;
	}

	public List<String> getStatements() { return statements; }

	public File getFile() { return file; }

	private void parse(File file) throws IOException {
		this.file = file;

		List<String> lines = FileUtils.readLines(file, DEF_CHARSET);
		if (CollectionUtils.isEmpty(lines)) { return; }

		String statement = "";
		for (String line : lines) {
			line = StringUtils.trim(line);
			if (line.startsWith(COMMENT)) { continue; }

			// too complicated to figure out if delimiter is part of the SQL query or not.. we r taking the ez way out
			if (StringUtils.endsWith(line, STATEMENT_DELIMITER)) {
				statement += StringUtils.trim(StringUtils.substringBefore(line, STATEMENT_DELIMITER)) + " ";
				statements.add(StringUtils.trim(statement));
				statement = "";
			} else {
				statement += line + " ";
			}
		}

		if (StringUtils.isNotBlank(statement)) { statements.add(statement); }
	}
}
