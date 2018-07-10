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
 */

package org.nexial.core.plugins.web;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class BrowserTest {

    @Test
    public void deriveWind10BuildNumber() {
        int minVersion = 10240;
        Assert.assertEquals("10240", Browser.deriveWind10BuildNumber("10.0.10240", minVersion));
        Assert.assertEquals("17711", Browser.deriveWind10BuildNumber("10.0.17711", minVersion));
        Assert.assertEquals("17134", Browser.deriveWind10BuildNumber("10.0.17134.112", minVersion));
        Assert.assertEquals("17134", Browser.deriveWind10BuildNumber("10.0.17134", minVersion));
        Assert.assertEquals("16299", Browser.deriveWind10BuildNumber("10.0.16299.19", minVersion));
        Assert.assertEquals("10240", Browser.deriveWind10BuildNumber("Windows 10 version 1607", minVersion));
        Assert.assertEquals("10240", Browser.deriveWind10BuildNumber("6.8.9841", minVersion));
    }
}