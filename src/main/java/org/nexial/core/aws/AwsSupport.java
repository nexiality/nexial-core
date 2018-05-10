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

package org.nexial.core.aws;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.regions.Regions;

public abstract class AwsSupport {
    protected Logger logger = LoggerFactory.getLogger(getClass());
    protected boolean verbose;
    protected String accessKey;
    protected String secretKey;
    protected Regions region;

    public void setVerbose(boolean verbose) { this.verbose = verbose;}

    public void setAccessKey(String accessKey) { this.accessKey = accessKey;}

    public void setSecretKey(String secretKey) { this.secretKey = secretKey;}

    public void setRegion(Regions region) { this.region = region;}

    public boolean isReadyForUse() {
        return StringUtils.isNotBlank(accessKey) && StringUtils.isNotBlank(secretKey) && region != null;
    }
}
