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

package org.nexial.core.plugins.db;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.OutputFileUtils;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.utils.CheckUtils.*;

public class RdbmsCommand extends BaseCommand {
    private static final int MAX_PRINTABLE_SQL_LENGTH = 200;
    protected DataAccess dataAccess;

    @Override
    public void init(ExecutionContext context) {
        super.init(context);
        dataAccess.setContext(context);
    }

    @Override
    public String getTarget() { return "rdbms"; }

    public void setDataAccess(DataAccess dataAccess) { this.dataAccess = dataAccess; }

    public StepResult runSQL(String var, String db, String sql) throws IOException {
        requiresValidVariableName(var);
        requiresNotBlank(db, "invalid db", db);
        requiresNotBlank(sql, "invalid sql", sql);

        String query = context.replaceTokens(StringUtils.trim(OutputFileUtils.resolveRawContent(sql, context)));
        // we want to support ALL types of SQL, including those vendor-specific
        // so for that reason, we are no longer insisting on the use of standard ANSI sql
        // requires(dataAccess.validSQL(query), "invalid sql", sql);

        SimpleExtractionDao dao = resolveDao(db);

        JdbcResult result = dataAccess.execute(query, dao);
        if (result == null) { return StepResult.fail("FAILED TO EXECUTE SQL '" + sql + "': no result found"); }

        String msg = "executed query in " + result.getElapsedTime() + " ms with " +
                     (result.hasError() ? "ERROR " + result.getError() : result.getRowCount() + " row(s)");
        context.logCurrentCommand(this, msg);
        context.setData(var, result);
        return StepResult.success("executed SQL '" + sql + "'; stored result as ${" + var + "}");
    }

    /**
     * execution one or more queries, which may be specified either as a series of SQL or as a file
     * (containing a series of SQL).
     */
    public StepResult runSQLs(String var, String db, String sqls) {
        requiresValidVariableName(var);
        requiresNotBlank(db, "invalid db", db);
        requiresNotBlank(sqls, "invalid sql statement(s)", sqls);

        String msgPrefix = "executing SQLs";

        try {
            List<SqlComponent> qualifiedSqlList =
                SqlComponent.toList(OutputFileUtils.resolveRawContent(sqls, context));
            requires(CollectionUtils.isNotEmpty(qualifiedSqlList), "No valid SQL statements found", sqls);

            int qualifiedSqlCount = qualifiedSqlList.size();
            context.logCurrentCommand(this, "found " + qualifiedSqlCount + " qualified query(s) to execute");
            msgPrefix = "executed " + qualifiedSqlCount + " SQL(s); ";

            JdbcOutcome outcome = executeSQLs(db, qualifiedSqlList);

            if (CollectionUtils.isNotEmpty(outcome)) {
                context.setData(var, outcome);
                if (MapUtils.isNotEmpty(outcome.getNamedOutcome())) {
                    outcome.getNamedOutcome().forEach((name, result) -> context.setData(name, result));
                    return StepResult.success(msgPrefix + "result saved as " + outcome.getNamedOutcome().keySet());
                }

                if (outcome.size() == 1) {
                    context.setData(var, outcome.get(0));
                    return StepResult.success(msgPrefix + "single result stored as ${" + var + "}");
                }

                return StepResult.success(msgPrefix + outcome.size() + " results stored as ${" + var + "}");
            }

            return StepResult.success(msgPrefix + "no result saved");
        } catch (Exception e) {
            return StepResult.fail("FAIL " + msgPrefix + ": " + e.getMessage());
        }
    }

    /**
     * todo: need to deprecate since runSQLs does the same thing
     */
    public StepResult runFile(String var, String db, String file) {
        requiresValidVariableName(var);
        requiresNotBlank(db, "invalid db", db);
        requiresReadableFile(file);

        try {
            return runSQLs(var, db, OutputFileUtils.resolveRawContent(file, context));
        } catch (IOException e) {
            return StepResult.fail("Unable to read SQL file '" + file + "': " + e.getMessage());
        }
    }

    public StepResult resultToCSV(String var, String csvFile, String delim, String showHeader) throws IOException {
        requiresValidVariableName(var);

        requiresNotBlank(csvFile, "invalid target CSV file", csvFile);
        File csv = new File(csvFile);

        if (StringUtils.isBlank(delim)) { delim = ","; }

        boolean printHeader = BooleanUtils.toBoolean(showHeader);

        Object resultObj = context.getObjectData(var);
        requiresNotNull(resultObj, "specified variable not found", var);

        String namespace = this.getTarget();

        // variable could be:
        // (1) exception text           --> error occurred when running stored procedure
        // (2) number                   --> rows affected by previous SQL
        // (3) List<Map<String,String>> --> "rows of map of name and value" by previous SELECT sql

        if (resultObj instanceof String) {
            if (StringUtils.isEmpty((String) resultObj)) {
                return StepResult.success("No result found from previous " + namespace + " command; " +
                                          "no CSV file generated");
            }

            FileUtils.write(csv, (String) resultObj, DEF_CHARSET, false);
            return StepResult.success("error from previous " + namespace + " command written to '" + csvFile + "'");
        }

        if (resultObj instanceof JdbcResult) {

            JdbcResult result = (JdbcResult) resultObj;
            if (CollectionUtils.isNotEmpty(result.getData())) {
                if (CollectionUtils.isEmpty(result.getColumns())) {
                    // no column names means no result (empty list)
                    return StepResult.success("No result found from previous " + namespace + " command; " +
                                              "no CSV file generated");
                }

                String csvContent = toCSV(result, delim, printHeader);
                FileUtils.writeStringToFile(csv, csvContent, DEF_CHARSET, false);
                return StepResult.success("result found in previous db.run*() command saved to '" + csvFile + "'");
            }

            if (result.getRowCount() > 0) {
                FileUtils.write(csv, result.getRowCount() + "", DEF_CHARSET, false);
                return StepResult.success("affected row count from previous " + namespace + " command written to '" +
                                          csvFile + "'");
            }

            if (result.hasError()) {
                FileUtils.write(csv, "ERROR: " + result.getError(), DEF_CHARSET, false);
                return StepResult.success("errors from previous " + namespace + " command written to '" +
                                          csvFile + "'");
            }
        }

        return StepResult.fail("Unknown type found for variable '" + var + "': " + resultObj.getClass().getName());
    }

    /**
     * execute multiple SQL statements and save the corresponding output (as CSV) to the specific {@code outputDir}
     * directory. Note that only SQL with matching Nexial variable will result in its associated output saved to the
     * specific {@code outputDir} directory - the associate variable name will be used as the output CSV file name.
     */
    public StepResult saveResults(String db, String sqls, String outputDir) {
        requiresNotBlank(db, "invalid db", db);
        requiresNotBlank(sqls, "invalid sql", sqls);
        requiresReadableDirectory(outputDir, "invalid output directory", outputDir);

        SimpleExtractionDao dao = resolveDao(db);
        String msgPrefix = "executing SQLs";

        try {
            List<SqlComponent> qualifiedSqlList =
                SqlComponent.toList(OutputFileUtils.resolveRawContent(sqls, context));
            requires(CollectionUtils.isNotEmpty(qualifiedSqlList), "No valid SQL statements found", sqls);

            int qualifiedSqlCount = qualifiedSqlList.size();
            context.logCurrentCommand(this, "found " + qualifiedSqlCount + " qualified query(s) to execute");
            msgPrefix = "executed " + qualifiedSqlCount + " SQL(s); ";

            for (SqlComponent sqlComponent : qualifiedSqlList) {
                String sql = context.replaceTokens(StringUtils.trim(sqlComponent.getSql()));
                String printableSql = StringUtils.length(sql) > MAX_PRINTABLE_SQL_LENGTH ?
                                      StringUtils.right(sql, MAX_PRINTABLE_SQL_LENGTH) + "..." : sql;

                if (StringUtils.isNotBlank(sqlComponent.getVarName())) {
                    String varName = context.replaceTokens(sqlComponent.getVarName());
                    String outFile = StringUtils.appendIfMissing(OutputFileUtils.webFriendly(varName), ".csv");
                    String output = StringUtils.appendIfMissing(new File(outputDir).getAbsolutePath(), separator) +
                                    outFile;
                    File targetFile = new File(output);

                    JdbcResult result = dataAccess.execute(sql, dao, targetFile);

                    if (result == null) {
                        return StepResult.fail("FAILED TO EXECUTE SQL '" + printableSql + "': no result");
                    }
                    if (result.hasError()) {
                        log("ERROR found while executing " + printableSql + ": " + result.getError());
                    }
                    if (FileUtil.isFileReadable(output, 3)) {
                        addLinkRef("Output for " + printableSql, outFile, targetFile.getAbsolutePath());
                    }

                    String msg = "executed " + printableSql + " in " + result.getElapsedTime() + " ms with " +
                                 (result.hasError() ? "ERROR " + result.getError() : result.getRowCount() + " row(s)");
                    context.logCurrentCommand(this, msg);

                    resultToJson(result, StringUtils.substringBeforeLast(output, ".") + ".json");
                } else {
                    // not saving result anywhere since this SQL is not mapped to any variable
                    log("executing " + printableSql + " without saving its result");
                    dao.executeSql(sql, null);
                }
            }

            return StepResult.success(msgPrefix + "output saved to " + outputDir);
        } catch (Exception e) {
            return StepResult.fail("FAIL " + msgPrefix + ": " + e.getMessage());
        }
    }

    public StepResult saveResult(String db, String sql, String output) {
        requiresNotBlank(db, "invalid db", db);
        requiresNotBlank(sql, "invalid sql", sql);
        requiresNotBlank(output, "invalid output", output);
        if (!StringUtils.endsWithIgnoreCase(output, ".csv")) { output += ".csv"; }

        SimpleExtractionDao dao = resolveDao(db);

        try {
            String query = context.replaceTokens(StringUtils.trim(OutputFileUtils.resolveRawContent(sql, context)));
            // we want to support ALL types of SQL, including those vendor-specific
            // so for that reason, we are no longer insisting on the use of standard ANSI sql
            // requires(dataAccess.validSQL(query), "invalid sql", sql);

            JdbcResult result = dataAccess.execute(query, dao, new File(output));
            if (result == null) { return StepResult.fail("FAILED TO EXECUTE SQL '" + sql + "': no result found"); }

            String msg = "executed query in " + result.getElapsedTime() + " ms with " +
                         (result.hasError() ? "ERROR " + result.getError() : result.getRowCount() + " row(s)");
            context.logCurrentCommand(this, msg);

            resultToJson(result, StringUtils.substringBeforeLast(output, ".") + ".json");
            return StepResult.success("executed SQL '" + sql + "'; stored result to '" + output + "'");
        } catch (IOException e) {
            return StepResult.fail("Error when executing '" + sql + "': " + e.getMessage());
        }
    }

    public JdbcOutcome executeSQLs(String db, List<SqlComponent> sqls) { return resolveDao(db).executeSqls(sqls); }

    public String toCSV(JdbcResult result, String delim, boolean printHeader) {
        // test along way... good luck
        StringBuilder sb = new StringBuilder();

        // save header
        if (printHeader) { sb.append(TextUtils.toString(result.getColumns(), delim)).append(CSV_ROW_SEP); }

        for (Map<String, String> row : result.getData()) {
            sb.append(rowToString(row, result.getColumns(), delim)).append(CSV_ROW_SEP);
        }

        return sb.toString();
    }

    protected SimpleExtractionDao resolveDao(String db) {
        String dbKey = DAO_PREFIX + db;
        if (context.hasData(dbKey)) {
            Object daoObject = context.getObjectData(dbKey);
            if (daoObject instanceof SimpleExtractionDao) {
                context.logCurrentCommand(this, "reusing established connection '" + db + "'");
                return (SimpleExtractionDao) daoObject;
            }

            // nope.. wrong type - toss it away
            context.removeData(dbKey);
        }

        SimpleExtractionDao dao = dataAccess.resolveDao(db);
        context.setData(dbKey, dao);
        context.logCurrentCommand(this, "new connection established for '" + db + "'");

        return dao;
    }

    protected String rowToString(Map<String, String> row, List<String> columnNames, String delim) {
        StringBuilder sb = new StringBuilder();
        for (String columnName : columnNames) {
            String value = row.get(columnName);
            if (StringUtils.contains(value, "\"")) { value = StringUtils.replace(value, "\"", "\"\""); }
            if (StringUtils.contains(value, delim)) { value = TextUtils.wrapIfMissing(value, "\"", "\""); }
            sb.append(value).append(delim);
        }

        return StringUtils.removeEnd(sb.toString(), delim);
    }

    private void resultToJson(JdbcResult result, String output) throws IOException {
        FileUtils.writeStringToFile(new File(output), GSON.toJson(result), DEF_FILE_ENCODING);
    }
}
