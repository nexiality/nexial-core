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

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.buffered.QueueBufferConfig.MAX_BATCH_SIZE_DEFAULT
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.MessageAttributeValue
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.amazonaws.services.sqs.model.SendMessageRequest
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.USER_NAME
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.commons.utils.EnvUtils
import org.nexial.core.aws.AwsSupport
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.CheckUtils.*
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtils
import java.io.Serializable

class SqsCommand : BaseCommand() {

    override fun getTarget(): String = "aws.sqs"

    fun receiveMessage(profile: String, queue: String, timeout: String, `var`: String): StepResult {
        requiresNotBlank(profile, "Invalid profile", profile)
        requiresNotBlank(queue, "Invalid queue", queue)
        requiresPositiveNumber(timeout, "Invalid timeout (millisecond)", timeout)
        requiresValidVariableName(`var`)

        val sqs = SqsSupport()
        sqs.setCredentials(resolveSqsSettings(context, profile))
        val message = sqs.receiveMessage(queue, NumberUtils.toLong(timeout))
        return if (message == null) {
            context.removeData(`var`)
            StepResult.fail("No messages found in queue '$queue'")
        } else {
            context.setData(`var`, message)
            StepResult.success("1 message received from queue '$queue'")
        }
    }

    fun receiveMessages(profile: String, queue: String, timeout: String, `var`: String): StepResult {
        requiresNotBlank(profile, "Invalid profile", profile)
        requiresNotBlank(queue, "Invalid queue", queue)
        requiresPositiveNumber(timeout, "Invalid timeout (millisecond)", timeout)
        requiresValidVariableName(`var`)

        val sqs = SqsSupport()
        sqs.setCredentials(resolveSqsSettings(context, profile))
        val message = sqs.receiveMessages(queue, NumberUtils.toLong(timeout))
        return if (message == null) {
            context.removeData(`var`)
            StepResult.fail("No messages found in queue '$queue'")
        } else {
            context.setData(`var`, message)
            StepResult.success("${message.size} message(s) received from queue '$queue'")
        }
    }

    fun sendMessage(profile: String, queue: String, message: String, `var`: String): StepResult {
        requiresNotBlank(profile, "Invalid profile", profile)
        requiresNotBlank(queue, "Invalid queue", queue)
        requiresNotBlank(message, "Invalid message", message)
        requiresValidVariableName(`var`)

        val sqs = SqsSupport()
        sqs.setCredentials(resolveSqsSettings(context, profile))
        val response = sqs.sendMessage(queue, message)
        context.setData(`var`, response)

        val messageId = response.id
        return if (StringUtils.isBlank(messageId)) {
            StepResult.fail("Unable to send message successfully to queue '$queue'")
        } else {
            StepResult.success("Message successfully sent to queue '$queue': $messageId")
        }
    }

    private fun resolveSqsSettings(context: ExecutionContext, profile: String): AwsSqsSettings {
        return AwsUtils.resolveAwSqsSettings(context, profile)
    }

//    fun deleteMessage(profile: String, queue: String, id: String, `var`: String): StepResult {
//        throw RuntimeException("Not yet implemented")
//    }

    // ??? create queue
    // ??? delete queue
    // ??? list queue
}

class SqsSupport : AwsSupport() {

    fun sendMessage(queue: String, message: String): QueueReceipt {

        val sqs = newSQSClient()

        val queueUrl = sqs.getQueueUrl(queue).queueUrl
        ConsoleUtils.log("sending to SQS queue '$queueUrl'")

        val now = System.currentTimeMillis()

        val request = SendMessageRequest(queueUrl, message)
        request
            .addMessageAttributesEntry("nexial.sendTimestamp", newStringAttrValue("$now"))
            .addMessageAttributesEntry("nexial.version", newStringAttrValue(ExecUtils.deriveJarManifest()))
            .addMessageAttributesEntry("nexial.os.user", newStringAttrValue(USER_NAME))
            .addMessageAttributesEntry("nexial.os.hostname", newStringAttrValue(EnvUtils.getHostName().toUpperCase()))

        val response = sqs.sendMessage(request)

        return QueueReceipt(response.messageId, now)
    }

    fun receiveMessages(queue: String, timeout: Long): List<QueueMessage>? =
            receiveMessages(queue, timeout, MAX_BATCH_SIZE_DEFAULT)

    fun receiveMessage(queue: String, timeout: Long): QueueMessage? {
        val messages = receiveMessages(queue, timeout, 1)
        return if (messages == null || CollectionUtils.isEmpty(messages)) null else messages[0]
    }

    private fun receiveMessages(queue: String, timeout: Long, size: Int): List<QueueMessage>? {

        val sqs = newSQSClient()

        val queueUrl = sqs.getQueueUrl(queue).queueUrl
        ConsoleUtils.log("receiving from SQS queue '$queueUrl'")
        val request = ReceiveMessageRequest(queueUrl)
            .withMaxNumberOfMessages(size)
            .withWaitTimeSeconds((timeout / 1000).toInt())
            .withMessageAttributeNames(".*")
            .withAttributeNames(".*")

        val receiveMessage = sqs.receiveMessage(request)
        return if (receiveMessage == null || CollectionUtils.isEmpty(receiveMessage.messages)) {
            null
        } else {
            val messages = mutableListOf<QueueMessage>()
            receiveMessage.messages.forEach { message -> messages.add(toQueueMessage(message)) }
            return messages
        }
    }

    private fun newSQSClient(): AmazonSQS = AmazonSQSClientBuilder.standard()
        .withRegion(region)
        .withCredentials(resolveCredentials(region))
        .build()

    private fun toQueueMessage(message: Message): QueueMessage {
        val attributes = mutableMapOf<String, String>()
        message.messageAttributes.forEach { name, attribute -> attributes[name] = attribute.stringValue }
        return QueueMessage(message.messageId, message.body, attributes)
    }

    private fun newStringAttrValue(value: String) =
            MessageAttributeValue().withStringValue(value).withDataType("String")
}

data class QueueMessage(val id: String, val body: String, val attributes: Map<String, String>) : Serializable
data class QueueReceipt(val id: String, val sendTimestamp: Long) : Serializable