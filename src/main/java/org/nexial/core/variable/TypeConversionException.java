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

public class TypeConversionException extends ExpressionException {
	private String dataType;
	private String dataValue;

	public TypeConversionException(String dataType, String dataValue) {
		super("Error when converting as '" + dataType + "': " + dataValue);
		this.dataType = dataType;
		this.dataValue = dataValue;
	}

	public TypeConversionException(String dataType, String dataValue, String message) {
		super(message);
		this.dataType = dataType;
		this.dataValue = dataValue;
	}

	public TypeConversionException(String dataType, String dataValue, String message, Throwable cause) {
		super(message, cause);
		this.dataType = dataType;
		this.dataValue = dataValue;
	}

	public String getDataType() { return dataType; }

	public String getDataValue() { return dataValue; }
}
