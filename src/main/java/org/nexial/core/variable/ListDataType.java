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

package org.nexial.core.variable;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;

import static org.nexial.core.NexialConst.Data.TEXT_DELIM;
import static org.nexial.core.NexialConst.getDefault;

public class ListDataType extends ExpressionDataType<String[]> {
    private ListTransformer transformer = new ListTransformer();
    private String delim;

    public ListDataType(String textValue) throws TypeConversionException { super(textValue); }

    protected ListDataType() { super(); }

    public ListDataType(String textValue, String delim) {
        super();
        this.delim = delim;
        this.textValue = textValue;
        init();
    }

    @NotNull
    @Override
    public String getName() { return "LIST"; }

    @NotNull
    public String getDelim() { return delim; }

    @NotNull
    @Override
    Transformer getTransformer() { return transformer; }

    @NotNull
    @Override
    ListDataType snapshot() {
        ListDataType snapshot = new ListDataType();
        snapshot.transformer = transformer;
        snapshot.value = value;
        snapshot.textValue = textValue;
        snapshot.delim = delim;
        return snapshot;
    }

    @Override
    protected void init() { parse(); }

    protected void parse() {
        if (StringUtils.isEmpty(delim)) {
            ExecutionContext context = ExecutionThread.get();
            delim = context == null ? getDefault(TEXT_DELIM) : context.getTextDelim();
        }
        value = Array.toArray(textValue, delim);
        textValue = Array.toString(value);
    }
}
