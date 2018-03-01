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

package org.nexial.core.utils;

import org.apache.commons.lang3.ArrayUtils;

import org.nexial.commons.DataServiceRuntimeException;
import org.nexial.commons.InvalidInputRuntimeException;

/**
 * mainly used for testing
 */
public final class AssertUtils {

	private AssertUtils() {}

	/** basic check on a condition.. throws runtime exception ({@link InvalidInputRuntimeException} if condition fails */
	public static boolean requires(boolean condition, String message, Object... params) {
		if (!condition) { throw new InvalidInputRuntimeException(message + ": " + ArrayUtils.toString(params)); }
		return condition;
	}

	public static boolean requiresRowsAffected(int rowsAffected, int mustBe) {
		if (rowsAffected != mustBe) {
			throw new DataServiceRuntimeException("Expected " + mustBe + " row(s) to be affected, " +
			                                      "but found " + rowsAffected + " row(s) instead.");
		}
		return true;
	}

	public static boolean requiresRowsAffected(int rowsAffected, int atLeast, int atMost) {
		if (rowsAffected < atLeast || rowsAffected > atMost) {
			throw new DataServiceRuntimeException("Expected " + atLeast + " to " + atMost + " row(s) to be affected, " +
			                                      "but found " + rowsAffected + " row(s) instead.");
		}
		return true;
	}
}
