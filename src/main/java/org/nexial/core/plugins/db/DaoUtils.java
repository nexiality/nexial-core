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

import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * DAO/SQL related utilities.  No database interactions are coded here.
 *
 * @author Mike Liu
 */
public final class DaoUtils {
    private static final String NULL = "NULL";

    private DaoUtils() { }

    /** @see #toSqlInClause(List) */
    public static String toSqlInClause(String[] backupTypes) {
        if (backupTypes == null) { return NULL; }
        return toSqlInClause(Arrays.asList(backupTypes));
    }

    /**
     * convert <code>backupTypes</code> into a list suitable for SQL IN clause within a prepared
     * statement.  Note that to make it suitable to be used for prepared statement, the first and
     * last single quote are omitted.  In other words, only the single quotes between the indices
     * of <code>backupTypes</code> will be rendered.
     */
    public static String toSqlInClause(List<String> backupTypes) {
        if (backupTypes == null) { return NULL; }
        String inClause = "";
        for (int i = 0; i < backupTypes.size(); i++) {
            String backupType = backupTypes.get(i);
            if (i == 0) {
                // special treatment for null
                inClause = backupType == null ? NULL : StringUtils.replace(backupType, "'", "''");
                continue;
            }

            // special treatment for null
            inClause += (StringUtils.endsWith(inClause, NULL) ? "," : "',") +
                        ((backupType == null) ?
                         NULL : "'" + StringUtils.replace(backupType, "'", "''"));
        }

        return inClause;
    }

    /**
     * replace the first '?' token from <code>sql</code> with <code>value</code>.  Note that
     * <code>value</code> is not assumed to be used as a VARCHAR/CHAR parameter, hence no
     * additional single-quote is added.
     * <p/>
     * <b>DESIGN CONSIDERATION</b>: While it is possible to refactor all similar methods as this
     * one into one with varargs, it proves to be better to keep these methods separated since this
     * approach forces the calling methods to pass the intended SQL-equivalent data type.  For
     * example,<br/>
     * <pre>    setParams("select ... ?, ?, ?, ?", "a", 'a', 12, true);</pre><br/>
     * might result in returning something like<br/>
     * <pre>    "select ... 'a', 'a', 12, true"</pre><br/>
     * while the intention was to derive something more like<br/>
     * <pre>    "select ... 'a', 'a', '12', '0'"</pre><br/>
     * Hence the better approach is to design separate methods with separate intended data type
     * for parameters.
     *
     * @see #setStringParam(String, String)
     */
    public static String setParam(String sql, Object value) {
        return StringUtils.replaceOnce(sql, "?", value == null ? NULL : "" + value);
    }

    /**
     * replace the first '?' token from <code>sql</code> with <code >value</code>, with
     * additional single-quote added so that the resulting string would remain as a valid SQL.
     *
     * @see #setParam(String, Object)
     * @see #setStringParams(String, String...)
     */
    public static String setStringParam(String sql, String value) {
        return StringUtils.replaceOnce(sql, "?", value == null ?
                                                 NULL :
                                                 "'" + StringUtils.replace(value, "'", "''") + "'");
    }

    /**
     * replace the '?' token successively from <code>sql</code> with <code>values</code>, with
     * additional single-quote added so that the resulting string would remain as a valid SQL.
     *
     * @see #setParam(String, Object)
     */
    public static String setStringParams(String sql, String... values) {
        if (values == null) { return StringUtils.replace(sql, "?", NULL); }

        for (String value : values) { sql = setStringParam(sql, value); }
        return sql;
    }

    /**
     * special case for manipulating SQL construction for IN clause.  For example, calling <br/>
     * <code>DaoUtils.setStringParamsForInClause("select * from table1 where col1 in (?)", "a", "b", "c")</code><br/>
     * would return<br/>
     * <code>"select * from table1 where col1 in ('a', 'b', 'c')</code>
     * <p/>
     * For more flexibility, use {@link #setStringParamsForInClause(String, String, String...)} instead.
     *
     * @see #setParam(String, Object)
     * @see #toSqlInClause(String[])
     * @see #setStringParamsForInClause(String, String, String...)
     */
    public static String setStringParamsForInClause(String sql, String... values) {
        return setStringParamsForInClause(sql, "?", values);
    }

    /**
     * special case for manipulating SQL construction for IN clause.  For example, calling <br/>
     * <code>DaoUtils.setStringParamsForInClause("select * from table1 where col1 in (%token%)", "%token%", "a", "b", "c")</code><br/>
     * would return<br/>
     * <code>"select * from table1 where col1 in ('a', 'b', 'c')</code>
     *
     * @see #setParam(String, Object)
     * @see #toSqlInClause(String[])
     * @see #setStringParamsForInClause(String, String, String...)
     */
    public static String setStringParamsForInClause(String sql, String token, String... values) {
        if (values == null) { return StringUtils.replaceOnce(sql, token, NULL); }
        String inParam = toSqlInClause(values);
        return StringUtils.replaceOnce(
            sql,
            token,
            (StringUtils.startsWith(inParam, NULL) ? "" : "'") + inParam +
            (StringUtils.endsWith(inParam, NULL) ? "" : "'"));
    }

    /** calculate the total affected rows - most useful when updating db via batch sql. */
    public static int sumUpRowsAffected(int[] affected) {
        int rowsAffected = 0;
        for (int result : affected) {
            // according to JDK documentation, a value greater than zero or equals to
            // Statement.SUCCESS_NO_INFO would constitute a success.  But since we cannot rightly
            // know the exact rows updated for those represented by Statement.SUCCESS_NO_INFO,
            // we simply +1 for such cases.
            if (result == Statement.SUCCESS_NO_INFO) {
                rowsAffected++;
            } else if (result > 0) { rowsAffected += result; }
        }
        return rowsAffected;
    }

    /** insists that all the "rows affected" count from each SQL in a batch represent successfully execution. */
    public static boolean assertRowsSuccessfullyAffected(int[] affected) {
        for (int result : affected) { if (result != Statement.SUCCESS_NO_INFO && result < 1) { return false; } }
        return true;
    }

    public static String csvFriendly(String text, String delim, boolean useQuote) {
        if (StringUtils.isBlank(text)) { return text; }

        text = StringUtils.replace(text, "\"", "\\\"");
        if (!StringUtils.contains(text, delim)) { return text; }
        if (useQuote) {
            return StringUtils.wrapIfMissing(text, "\"");
        } else {
            return StringUtils.replace(text, delim, "\\" + delim);
        }
    }
}

