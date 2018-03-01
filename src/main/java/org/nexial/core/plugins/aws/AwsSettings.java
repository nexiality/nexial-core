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

package org.nexial.core.plugins.aws;

import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import com.amazonaws.regions.Regions;
import org.nexial.core.IntegrationConfigException;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;

import static com.amazonaws.regions.Regions.DEFAULT_REGION;
import static org.nexial.core.NexialConst.AwsSettings.*;

public class AwsSettings {
    private String accessKey;
    private String secretKey;
    private Regions region;

    public static AwsSettings resolveFrom(ExecutionContext context, String profile)
        throws IntegrationConfigException {
        if (context == null) { return null; }
        if (StringUtils.isBlank(profile)) { return null; }

        String prefix = StringUtils.joinWith(".", profile, SUFFIX);
        if (context.hasData(prefix)) {
            Object obj = context.getObjectData(prefix);
            if (obj instanceof AwsSettings) {
                ConsoleUtils.log("reusing established AWS settings '" + profile + "'");
                return (AwsSettings) obj;
            }

            // nope.. wrong type - toss it away
            context.removeData(prefix);
        }

        String prefix1 = prefix + ".";
        Map<String, String> config = context.getDataByPrefix(prefix1);
        if (MapUtils.isEmpty(config)) {
            context.logCurrentStep("No settings configuration found for '" + profile + "'; Unable to connect.");
            return null;
        }

        String accessKey = config.get(AWS_ACCESS_KEY);
        if (StringUtils.isBlank(accessKey)) { throw IntegrationConfigException.missingConfig(prefix1 + AWS_ACCESS_KEY);}

        String secretKey = config.get(AWS_SECRET_KEY);
        if (StringUtils.isBlank(secretKey)) { throw IntegrationConfigException.missingConfig(prefix1 + AWS_SECRET_KEY);}

        String regionName = config.get(AWS_REGION);
        if (StringUtils.isBlank(regionName)) { regionName = DEFAULT_REGION.getName(); }

        AwsSettings settings = new AwsSettings();
        settings.accessKey = accessKey;
        settings.secretKey = secretKey;
        settings.region = Regions.fromName(regionName);

        context.setData(prefix, settings);
        return settings;
    }

    public String getAccessKey() { return accessKey; }

    public String getSecretKey() { return secretKey; }

    public Regions getRegion() { return region; }
}
