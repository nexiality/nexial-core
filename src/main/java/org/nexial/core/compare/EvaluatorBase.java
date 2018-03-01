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

package org.nexial.core.compare;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;

import static org.nexial.core.NexialConst.OPT_EASY_STRING_COMPARE;

/**
 *
 */
public abstract class EvaluatorBase implements Evaluator {
	@Override
	public boolean proceed(String lhs, String rhs) throws IncompatibleTypeException {
		if (NumberUtils.isCreatable(lhs) && NumberUtils.isCreatable(rhs)) {
			return evaluate(NumberUtils.toDouble(lhs), NumberUtils.toDouble(rhs));
		} else {
			boolean lenientStringCompare = isLenientStringCompare();
			return evaluate(lenientStringCompare ? StringUtils.trim(lhs) : lhs,
			                lenientStringCompare ? StringUtils.trim(rhs) : rhs);
		}
	}

	protected boolean isLenientStringCompare() {
		ExecutionContext context = ExecutionThread.get();
		return context != null && context.getBooleanData(OPT_EASY_STRING_COMPARE, false);
	}

	protected abstract boolean evaluate(double lhs, double rhs) throws IncompatibleTypeException;

	protected abstract boolean evaluate(String lhs, String rhs) throws IncompatibleTypeException;
}
