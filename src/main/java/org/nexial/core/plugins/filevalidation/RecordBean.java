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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.nexial.core.plugins.filevalidation.validators.Error;
import org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.Severity;

public class RecordBean extends ListOrderedMap<String, FieldBean> {

    private int recordNumber;
    private transient Map<String, FieldBean> recordFieldsMap;
    private List<FieldBean> fields;
    private transient RecordData recordData;
    private List<Error> errors;
    private String skippedMsg;
    private boolean isSkipped;
    private boolean isFailed;

    public boolean isSkipped() {
        return isSkipped;
    }

    public void setSkipped(boolean skipped) {
        isSkipped = skipped;
    }

    public boolean isFailed() {
        return isFailed;
    }

    public void setFailed(boolean failed) {
        isFailed = failed;
    }

    public String getSkippedMsg() {
        return skippedMsg;
    }

    public void collectErrors(){
        List<Error> allErrors = new ArrayList<>();

        int totalWarnings = recordData.getTotalRecordsWarning();
        for (FieldBean recordField : fields) {

            List<Error> errors = recordField.getErrors();
            if (CollectionUtils.isNotEmpty(errors)) {
                for (Error error : errors) {
                    // todo: optimize reading error severity
                    error.setRecordLine(String.valueOf(recordNumber + 1));
                    if (error.getSeverity().equals(Severity.ERROR.toString())) {
                        isFailed = true;
                        recordData.setHasError(true);
                    }
                    if (error.getSeverity().equals(Severity.WARNING.toString())) {
                        totalWarnings++;
                    }
                    allErrors.add(error);
                }
            }
        }
        recordData.setTotalRecordsWarning(totalWarnings);
        setErrors(allErrors);
    }

    public void setSkippedMsg(String skippedMsg) {
        this.skippedMsg = skippedMsg;
    }

    public List<Error> getErrors() {
        return errors;
    }

    public void setErrors(List<Error> errors) {
        this.errors = errors;
    }

    public RecordData getRecordData() { return recordData; }

    public int getRecordNumber() {
        return recordNumber;
    }

    public void setRecordNumber(int recordNumber) {
        this.recordNumber = recordNumber;
    }

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
