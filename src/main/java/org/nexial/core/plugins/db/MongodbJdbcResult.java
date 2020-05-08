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
 */

package org.nexial.core.plugins.db;

import org.nexial.core.plugins.db.SqlComponent.Type;

import static org.apache.commons.lang3.StringUtils.rightPad;
import static org.nexial.core.NexialConst.NL;

public class MongodbJdbcResult extends JdbcResult {
    private boolean expanded;
    private boolean acknowledged;
    private long deletedCount;
    private long matchedCount;
    private long modifiedCount;

    public MongodbJdbcResult(String sql) {
        super();
        this.sql = sql;
    }

    public MongodbJdbcResult(String sql, int rowCount) { super(sql, rowCount); }

    public boolean isExpanded() {return expanded;}

    public void setExpanded(boolean expanded) {this.expanded = expanded;}

    public boolean isAcknowledged() {return acknowledged;}

    public void setAcknowledged(boolean acknowledged) {this.acknowledged = acknowledged;}

    public long getDeletedCount() {return deletedCount;}

    public void setDeletedCount(long deletedCount) {this.deletedCount = deletedCount;}

    public long getMatchedCount() {return matchedCount;}

    public void setMatchedCount(long matchedCount) {this.matchedCount = matchedCount;}

    public long getModifiedCount() {return modifiedCount;}

    public void setModifiedCount(long modifiedCount) {this.modifiedCount = modifiedCount;}

    @Override
    public String toString() {
        return super.toString() +
               rightPad("expanded", TO_STRING_KEY_LENGTH) + "=" + expanded + NL +
               rightPad("deletedCount", TO_STRING_KEY_LENGTH) + "=" + deletedCount + NL +
               rightPad("matchedCount", TO_STRING_KEY_LENGTH) + "=" + matchedCount + NL +
               rightPad("modifiedCount", TO_STRING_KEY_LENGTH) + "=" + modifiedCount + NL +
               rightPad("acknowledged", TO_STRING_KEY_LENGTH) + "=" + acknowledged;
    }

    protected void updateSqlType(Type type) { this.sqlType = type; }
}
