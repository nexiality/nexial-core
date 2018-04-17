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

import org.junit.Assert;
import org.junit.Test;

public class DateValidatorTest {

    @Test
    public void validateDate() {
        String value = "20160229";
        String format = "yyyyMMdd";
        if (!DateValidator.validateDate(value, format)){
            Assert.fail("Invalid Date '"+value +"' or invalid format '"+format+"'");
        }

        value = "022916";
        format = "MMddyy";
        if (!DateValidator.validateDate(value, format)){
            Assert.fail("Invalid Date '"+value +"' or invalid format '"+format+"'");
        }

        value = "290216:022055";
        format = "ddMMyy:hhmmss";
        if (!DateValidator.validateDate(value, format)){
            Assert.fail("Invalid Date '"+value +"' or invalid format '"+format+"'");
        }

        value = "290216 59-02-55";
        format = "ddMMyy mm-hh-ss";
        if (!DateValidator.validateDate(value, format)){
            Assert.fail("Invalid Date '"+value +"' or invalid format '"+format+"'");
        }
    }
}