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

import org.apache.commons.lang3.StringUtils;

import static org.nexial.core.NexialConst.Data.DEF_TEXT_DELIM;

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

	@Override
	public String getName() { return "LIST"; }

	@Override
	Transformer getTransformer() { return transformer; }

	public String getDelim() { return delim; }

	public void setDelim(String delim) { this.delim = delim; }

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
		if (StringUtils.isEmpty(delim)) { delim = DEF_TEXT_DELIM; }
		value = Array.toArray(textValue, delim);
		textValue = Array.toString(value);
	}
}
