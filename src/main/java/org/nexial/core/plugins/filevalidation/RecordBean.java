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

package org.nexial.core.plugins.filevalidation;

import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.ListOrderedMap;

public class RecordBean extends ListOrderedMap<String, FieldBean> {

    private transient Map<String, FieldBean> recordFieldsMap;
    private List<FieldBean> fields;
    private transient RecordData recordData;

    public RecordData getRecordData() { return recordData; }

    public void setRecordData(RecordData recordData) { this.recordData = recordData; }

    public List<FieldBean> getFields() { return fields; }

    public void setFields(List<FieldBean> fields) {
        this.fields = fields;
        recordFieldsMap = new ListOrderedMap();

        for (FieldBean fieldBean : fields) {
            recordFieldsMap.put(fieldBean.getConfig().getFieldname(), fieldBean);
            // todo: reuse super's impl
            // put(fieldBean.getConfig().getFieldname(), fieldBean);
        }
    }

    @Override
    public FieldBean get(Object key) { return this.recordFieldsMap.get(key); }

    @Override
    public String toString() { return "RecordBean{fields=" + fields + '}'; }
}
