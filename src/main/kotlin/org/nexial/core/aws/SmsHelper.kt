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

package org.nexial.core.aws

import com.amazonaws.ClientConfiguration
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.AmazonSNSAsyncClientBuilder
import com.amazonaws.services.sns.model.MessageAttributeValue
import com.amazonaws.services.sns.model.PublishRequest
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.IntegrationConfigException
import org.nexial.core.NexialConst.RB
import org.nexial.core.utils.ConsoleUtils

class SmsHelper : AwsSupport() {
    private var sns: AmazonSNS? = null
    private var defaultMessageAttributes: MutableMap<String, MessageAttributeValue> = HashMap()

    var prefix = ""
    var senderId = "12345"

    fun init() {
        if (!isReadyForUse) {
            ConsoleUtils.log(RB.Tools.text("sms.config.missing"))
            return
        }

        if (sns == null) {
            sns = AmazonSNSAsyncClientBuilder.standard()
                .withCredentials(resolveCredentials(region))
                .withClientConfiguration(ClientConfiguration())
                .withRegion(region)
                .build()
        }

        val msgAttr = MessageAttributeValue()
        defaultMessageAttributes["AWS.SNS.SMS.SenderID"] = msgAttr.withStringValue(senderId).withDataType("String")
        defaultMessageAttributes["AWS.SNS.SMS.SMSType"] = msgAttr.withStringValue("Promotional").withDataType("String")
    }

    @Throws(IntegrationConfigException::class)
    fun send(phoneNumbers: List<String>, text: String) {
        if (sns == null) throw IntegrationConfigException(RB.Tools.text("sms.not.ready"))
        if (StringUtils.isBlank(text)) throw IllegalArgumentException(RB.Tools.text("sms.text.missing"))

        phoneNumbers.forEach { phoneNumber ->
            val phone = TextUtils.sanitizePhoneNumber(phoneNumber)
            if (StringUtils.length(phone) < 10) throw IllegalArgumentException(RB.Tools.text("sms.text.bad", phone))
            sns!!.publish(PublishRequest().withPhoneNumber(phone)
                              .withMessage("$prefix$text")
                              .withMessageAttributes(defaultMessageAttributes))
        }
    }

}
