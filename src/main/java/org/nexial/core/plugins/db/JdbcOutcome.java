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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static org.apache.commons.lang3.builder.ToStringStyle.SIMPLE_STYLE;
import static org.nexial.core.NexialConst.NL;

public class JdbcOutcome extends ArrayList<JdbcResult> {
    private long startTime;
    private long elapsedTime;
    private Map<String, JdbcResult> namedOutcome = new HashMap<>();
    private String error;
    private int rowsAffected;

    public Map<String, JdbcResult> getNamedOutcome() { return namedOutcome; }

    public JdbcResult get(String name) { return namedOutcome.get(name); }

    public long getStartTime() { return startTime; }

    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getElapsedTime() { return elapsedTime; }

    public void setElapsedTime(long elapsedTime) { this.elapsedTime = elapsedTime; }

    public String getError() { return error; }

    public void setError(String error) { this.error = error; }

    public void addError(String error) {
        if (StringUtils.isBlank(error)) { return; }
        if (StringUtils.isBlank(this.error)) {
            this.error = error;
        } else {
            this.error += NL + error;
        }
    }

    public int getRowsAffected() { return rowsAffected; }

    public int getRowCount() { return getRowsAffected(); }

    public boolean addOutcome(String variable, JdbcResult result) {
        if (result == null) { return false; }
        if (result.getRowCount() != -1) { rowsAffected += result.getRowCount(); }
        addError(result.getError());
        if (StringUtils.isNotBlank(variable)) { namedOutcome.put(variable, result);}
        return add(result);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SIMPLE_STYLE)
                   .append("startTime", startTime)
                   .append("elapsedTime", elapsedTime)
                   .append("rowsAffected", rowsAffected)
                   .append("error", error)
                   .append("names", namedOutcome.keySet())
                   .append(super.toString())
                   .toString();
    }
}
