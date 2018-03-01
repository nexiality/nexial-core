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

package org.nexial.core.plugins.filevalidation.validators;

import org.nexial.core.plugins.filevalidation.RecordBean;

public class AverageFunction implements MathFunction {

    RecordBean recordBean;

    public AverageFunction(RecordBean recordBean) {
        this.recordBean = recordBean;
    }

    @Override
    public void doMathFunction() {

    }

    // todo: implement
    // double value;
    // if (signField != null) {
    //     value = NumberUtils.createDouble(signField.getFieldValue() +
    //                                      recordFields.get(i).getFieldValue());
    // } else { value = NumberUtils.createDouble(recordFields.get(i).getFieldValue()); }
    // if (mapValues.containsKey(mapTo)) {
    //
    //     create constants for counter and sum
    // int counter = (Integer) mapValues.get(mapTo + "#Counter");
    // counter = ++counter;
    // double sum = value + (Double) mapValues.get(mapTo + "#Sum");
    // mapValues.put(mapTo, sum / counter);
    // mapValues.put(mapTo + "#Counter", counter);
    // mapValues.put(mapTo + "#Sum", sum);
    //
    // } else {
    //
    //     mapValues.put(mapTo, value);
    //     mapValues.put(mapTo + "#Counter", 1);
    //     mapValues.put(mapTo + "#Sum", value);
}

// }
