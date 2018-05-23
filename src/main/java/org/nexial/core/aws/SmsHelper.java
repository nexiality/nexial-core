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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.IntegrationConfigException;
import org.nexial.core.utils.ConsoleUtils;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSAsyncClientBuilder;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;

public class SmsHelper extends AwsSupport {
    private AmazonSNS sns;
    private String smsNotReadyMessage;
    private Map<String, MessageAttributeValue> defaultMessageAttributes;

    public void setSmsNotReadyMessage(String smsNotReadyMessage) { this.smsNotReadyMessage = smsNotReadyMessage; }

    public void init() {
        if (!isReadyForUse()) {
            ConsoleUtils.log("missing REQUIRED configuration; sms DISABLED!");
            return;
        }

        if (sns == null) {
            AWSCredentials credential = new BasicAWSCredentials(accessKey, secretKey);
            sns = AmazonSNSAsyncClientBuilder.standard()
                                             .withCredentials(new AWSStaticCredentialsProvider(credential))
                                             .withClientConfiguration(new ClientConfiguration())
                                             .withRegion(region)
                                             .build();
        }

        if (defaultMessageAttributes == null) {
            defaultMessageAttributes = new HashMap<>();
            //The sender ID shown on the device. (shh.. it's 1-NEXIAL-BOTS)
            defaultMessageAttributes.put("AWS.SNS.SMS.SenderID",
                                         new MessageAttributeValue().withStringValue("16394252687")
                                                                    .withDataType("String"));
            defaultMessageAttributes.put("AWS.SNS.SMS.SMSType",
                                         new MessageAttributeValue().withStringValue("Promotional")
                                                                    .withDataType("String"));
        }
    }

    public void send(List<String> phoneNumbers, String text) throws IntegrationConfigException {
        if (StringUtils.isBlank(text)) { throw new IllegalArgumentException("text cannot be empty"); }
        if (sns == null) { throw new IntegrationConfigException(smsNotReadyMessage); }

        List<String> sanitizedPhoneNumbers = new ArrayList<>();
        phoneNumbers.forEach(phoneNumber -> {
            phoneNumber = TextUtils.sanitizePhoneNumber(phoneNumber);
            if (StringUtils.length(phoneNumber) < 10) {
                throw new IllegalArgumentException("Resolved phone number is invalid: " + phoneNumber);
            }
            sanitizedPhoneNumbers.add(phoneNumber);
        });

        ConsoleUtils.log("sending sms to " + sanitizedPhoneNumbers + ": " + text);
        sanitizedPhoneNumbers.forEach(phoneNumber -> sns.publish(
            new PublishRequest().withPhoneNumber(phoneNumber)
                                .withMessage("nexial-bot: " + text)
                                .withMessageAttributes(defaultMessageAttributes)));
    }

}
