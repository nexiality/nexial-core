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

package org.nexial.core.plugins.aws

import com.amazonaws.regions.Regions
import com.amazonaws.regions.Regions.DEFAULT_REGION
import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.core.IntegrationConfigException
import org.nexial.core.NexialConst.AwsSettings.*
import org.nexial.core.model.ExecutionContext
import org.nexial.core.utils.ConsoleUtils

open class AwsSettings constructor(val accessKey: String, val secretKey: String, val region: Regions) {
    lateinit var assumeRoleArn: String
    lateinit var assumeRoleSession: String
    var assumeRoleDuration = 900
}

class AwsSesSettings constructor(accessKey: String, secretKey: String, region: Regions, val from: String) :
        AwsSettings(accessKey, secretKey, region) {

    lateinit var replyTo: String
    lateinit var cc: String
    lateinit var bcc: String
    lateinit var configurationSetName: String
    lateinit var xmailer: String
}

class AwsSqsSettings constructor(accessKey: String, secretKey: String, region: Regions) :
        AwsSettings(accessKey, secretKey, region)

class AwsUtils {
    companion object {
        @Throws(IntegrationConfigException::class)
        fun resolveAwsSettings(context: ExecutionContext?, profile: String?): AwsSettings? {
            if (context == null) return null
            if (StringUtils.isBlank(profile)) return null

            val prefix = "$profile.$SUFFIX"
            if (context.hasData(prefix)) {
                val obj = context.getObjectData(prefix)
                if (obj is AwsSettings) {
                    ConsoleUtils.log("reusing established AWS settings '$profile'")
                    return obj
                }

                // nope.. wrong type - toss it away
                context.removeData(prefix)
            }

            val prefix1 = "$prefix."
            val config = context.getDataByPrefix(prefix1)
            if (MapUtils.isEmpty(config)) {
                context.logCurrentStep("No settings configuration found for '$profile'; Unable to connect.")
                return null
            }

            val settings = AwsSettings(getRequiredSetting(config, prefix1, AWS_ACCESS_KEY),
                                       getRequiredSetting(config, prefix1, AWS_SECRET_KEY),
                                       resolveRegion(config))
            configAssumeRole(settings, config)

            context.setData(prefix, settings)
            return settings
        }

        @Throws(IntegrationConfigException::class)
        fun resolveAwsSesSettings(context: ExecutionContext?, profile: String): AwsSesSettings? {
            if (context == null) return null
            if (StringUtils.isBlank(profile)) return null

            val prefix = "$profile.$SUFFIX"
            if (context.hasData(prefix)) {
                val obj = context.getObjectData(prefix)
                if (obj is AwsSesSettings) {
                    ConsoleUtils.log("reusing established AWS SES settings '$profile'")
                    return obj
                }

                // nope.. wrong type - toss it away
                context.removeData(prefix)
            }

            val prefix1 = "$prefix."
            val config = context.getDataByPrefix(prefix1)
            if (MapUtils.isEmpty(config)) {
                context.logCurrentStep("No settings configuration found for '$profile'; Unable to connect.")
                return null
            }

            val settings = AwsSesSettings(accessKey = getRequiredSetting(config, prefix1, AWS_ACCESS_KEY),
                                          secretKey = getRequiredSetting(config, prefix1, AWS_SECRET_KEY),
                                          region = resolveRegion(config),
                                          from = getRequiredSetting(config, prefix1, AWS_SES_FROM))
            configAssumeRole(settings, config)

            settings.cc = config[AWS_SES_CC] ?: ""
            settings.bcc = config[AWS_SES_BCC] ?: ""
            settings.replyTo = config[AWS_SES_REPLY_TO] ?: ""
            settings.configurationSetName = config[AWS_SES_CONFIG_SET] ?: ""
            settings.xmailer = config[AWS_XMAILER] ?: ""

            context.setData(prefix, settings)
            return settings
        }

        @Throws(IntegrationConfigException::class)
        fun resolveAwSqsSettings(context: ExecutionContext, profile: String): AwsSqsSettings {
            val prefix = "$profile.$SUFFIX"
            if (context.hasData(prefix)) {
                val obj = context.getObjectData(prefix)
                if (obj is AwsSqsSettings) {
                    ConsoleUtils.log("reusing established AWS SQS settings '$profile'")
                    return obj
                }

                context.removeData(prefix)
            }

            val config = context.getDataByPrefix("$prefix.")
            if (MapUtils.isEmpty(config)) {
                throw IntegrationConfigException("No setting configuration found for '$profile'; Unable to connect.")
            }

            val settings = AwsSqsSettings(accessKey = getRequiredSetting(config, "$prefix.", AWS_ACCESS_KEY),
                                          secretKey = getRequiredSetting(config, "$prefix.", AWS_SECRET_KEY),
                                          region = resolveRegion(config))
            configAssumeRole(settings, config)
            context.setData(prefix, settings)
            return settings
        }

        private fun <T : AwsSettings> configAssumeRole(settings: T, config: MutableMap<String, String>) {
            settings.assumeRoleArn = config[AWS_STS_ROLE_ARN] ?: ""
            settings.assumeRoleSession = config[AWS_STS_ROLE_SESSION] ?: ""
            settings.assumeRoleDuration = NumberUtils.toInt(config[AWS_STS_ROLE_DURATION], 900)
        }

        private fun resolveRegion(config: Map<String, String>) = if (StringUtils.isBlank(config[AWS_REGION])) {
            DEFAULT_REGION
        } else {
            Regions.fromName(config[AWS_REGION])
        }

        @Throws(IntegrationConfigException::class)
        fun getRequiredSetting(config: Map<String, String>, prefix: String, key: String) =
                if (StringUtils.isBlank(config[key]))
                    throw IntegrationConfigException.missingConfig(prefix + key)
                else
                    config.getValue(key)
    }
}