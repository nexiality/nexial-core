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

public class EvaluateGreaterOrEqual extends EvaluatorBase {
    @Override
    protected boolean evaluate(double lhs, double rhs) throws IncompatibleTypeException { return lhs >= rhs; }

    @Override
    protected boolean evaluate(String lhs, String rhs) throws IncompatibleTypeException {
        throw new IncompatibleTypeException(lhs, rhs, ">=");
    }
}
