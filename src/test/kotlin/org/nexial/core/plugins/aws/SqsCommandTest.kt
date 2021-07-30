/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.plugins.aws

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.StringUtils
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.nexial.core.model.MockExecutionContext
import org.nexial.core.model.TestStep

private const val TEST_QUEUE_NAME = "nexial-test-queue"
private const val PROFILE = "MySQS"
private const val SYSPROP_ACCESS_KEY = "SqsCommandTest.accessKey"
private const val SYSPROP_SECRET_KEY = "SqsCommandTest.secretKey"

class SqsCommandTest {

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
    }

    @Test
    fun sendAndReceiveMessage() {
        if (isSysPropsNotFound()) return

        initContext(PROFILE)

        val sqs = SqsCommand()
        sqs.init(context)

        // sent to queue
        val messageBody = RandomStringUtils.randomAlphanumeric(1024)
        val result = sqs.sendMessage(PROFILE, TEST_QUEUE_NAME, messageBody, "receipt")
        println("message sent: $result")
        Assert.assertNotNull(result)
        Assert.assertTrue(result.isSuccess)

        val receipt = context.getObjectData("receipt")
        println(receipt)
        Assert.assertNotNull(receipt)
        Assert.assertTrue(receipt is QueueReceipt)
        Assert.assertNotNull((receipt as QueueReceipt).id)
        val sentTimestamp = receipt.sendTimestamp

        // wait a while...
        Thread.sleep(2500)

        // receive from queue
        val result2 = sqs.receiveMessage(PROFILE, TEST_QUEUE_NAME, "message")
        println("message received: $result2")
        Assert.assertNotNull(result2)
        Assert.assertTrue(result2.isSuccess)

        val message = context.getObjectData("message")
        println(message)
        Assert.assertNotNull(message)
        Assert.assertNotNull(context.replaceTokens("$" + "{message}.id"))
        Assert.assertNotNull(context.replaceTokens("$" + "{message}.attributes"))
        Assert.assertNotNull(context.replaceTokens("$" + "{message}.[attributes].[nexial.version]"))
        Assert.assertNotNull(context.replaceTokens("$" + "{message}.[attributes].[nexial.os.user]"))
        Assert.assertNotNull(context.replaceTokens("$" + "{message}.[attributes].[nexial.os.hostname]"))
        Assert.assertEquals("$sentTimestamp",
                            context.replaceTokens("$" + "{message}.[attributes].[nexial.sendTimestamp]"))
        Assert.assertEquals(messageBody, context.replaceTokens("$" + "{message}.body"))
    }

    @Test
    fun sendAndReceiveMessages() {
        if (isSysPropsNotFound()) return

        initContext(PROFILE)

        val sqs = SqsCommand()
        sqs.init(context)

        val testData = mutableMapOf<String, String>()

        // sent to queue 3 times
        sendMessage(sqs, PROFILE, testData)
        sendMessage(sqs, PROFILE, testData)
        sendMessage(sqs, PROFILE, testData)

        // wait a while...
        Thread.sleep(2000)
        println()
        println()

        // receive from queue
        var messages = receiveAndDeleteMessages(sqs, PROFILE, testData)
        while (messages.isNotEmpty()) messages = receiveAndDeleteMessages(sqs, PROFILE, testData)
    }

    @Test
    fun sendMessage() {
        if (isSysPropsNotFound()) return

        initContext(PROFILE)

        val sqs = SqsCommand()
        sqs.init(context)

        val result = sqs.sendMessage(PROFILE, TEST_QUEUE_NAME, "This is a test. Do not be alarmed", "message")
        println(result)
        Assert.assertNotNull(result)
        Assert.assertTrue(result.isSuccess)

        val message = context.getObjectData("message")
        println(message)
        println(context.replaceTokens("$" + "{message}.messageId"))
        println(context.replaceTokens("$" + "{message}.sequenceNumber"))
    }

    @Test
    fun sendAyncMessages() {
        if (isSysPropsNotFound()) return

        initContext(PROFILE, async = false)

        val sqs = SqsCommand()
        sqs.init(context)

        val testData = mutableMapOf<String, String>()

        // sent to queue 3 times
        val numberOfMessages = 100
        val startTs = System.currentTimeMillis()
        for (i in 1..numberOfMessages) sendMessage(sqs, PROFILE, testData)
        val endTs = System.currentTimeMillis()
        println("sent $numberOfMessages messages to queue asynchronously in ${endTs - startTs} milliseconds")

        // wait a while...
        Thread.sleep(1000)
        println()
        println()

        // receive from queue
        var receivedAndDeleted = 0
        val startTs2 = System.currentTimeMillis()
        var messages = receiveAndDeleteMessages(sqs, PROFILE, testData)
        receivedAndDeleted += messages.size
        while (messages.isNotEmpty()) {
            messages = receiveAndDeleteMessages(sqs, PROFILE, testData)
            receivedAndDeleted += messages.size
        }
        val endTs2 = System.currentTimeMillis()
        println("received and deleted $receivedAndDeleted messages asynchronously in ${endTs2 - startTs2} milliseconds")
    }

    private fun isSysPropsNotFound(): Boolean {
        if (!System.getProperties().containsKey(SYSPROP_ACCESS_KEY)) {
            println("REQUIRED SYSTEM PROPERTIES '$SYSPROP_ACCESS_KEY' NOT FOUND; TEST SKIPPED")
            return true
        }

        if (!System.getProperties().containsKey(SYSPROP_SECRET_KEY)) {
            println("REQUIRED SYSTEM PROPERTIES '$SYSPROP_SECRET_KEY' NOT FOUND; TEST SKIPPED")
            return true
        }

        return false
    }

    private fun sendMessage(sqs: SqsCommand, profile: String, testData: MutableMap<String, String>) {
        val messageBody = RandomStringUtils.randomAlphanumeric(1024)

        val result1 = sqs.sendMessage(profile, TEST_QUEUE_NAME, messageBody, "receipt")
        println("message sent: $result1")
        Assert.assertNotNull(result1)
        Assert.assertTrue(result1.isSuccess)

        val receipt = context.getObjectData("receipt")
        println(receipt)
        Assert.assertNotNull(receipt)
        Assert.assertTrue(receipt is QueueReceipt)
        Assert.assertNotNull((receipt as QueueReceipt).id)

        testData[messageBody] = receipt.sendTimestamp.toString()
    }

    private fun receiveAndDeleteMessages(sqs: SqsCommand, profile: String, testData: MutableMap<String, String>):
            List<QueueMessage> {

        val result = sqs.receiveMessages(profile, TEST_QUEUE_NAME, "messages")
        println("message received: $result")
        Assert.assertNotNull(result)

        if (result.failed()) return listOf()

        val messages = context.getObjectData("messages")
        println(messages)

        Assert.assertNotNull(messages)
        Assert.assertTrue(messages is List<*>)

        if (CollectionUtils.isNotEmpty(messages as List<*>)) {
            println()
            println("${messages.size} message(s) to process...")

            messages.forEachIndexed { index, item ->
                val message = item as QueueMessage
                Assert.assertNotNull(message)

                val messageId = context.replaceTokens("$" + "{messages}[$index].id")
                val receiptHandle = context.replaceTokens("$" + "{messages}[$index].receiptHandle")
                val version = context.replaceTokens("$" + "{messages}[$index].[attributes].[nexial.version]")
                val user = context.replaceTokens("$" + "{messages}[$index].[attributes].[nexial.os.user]")
                val hostname = context.replaceTokens("$" + "{messages}[$index].[attributes].[nexial.os.hostname]")
                val sendTimestamp = context
                    .replaceTokens("$" + "{messages}[$index].[attributes].[nexial.sendTimestamp]")
                val body = context.replaceTokens("$" + "{messages}[$index].body")

                assertNotEmptyAndNotEnding(messageId, ".id")
                assertNotEmptyAndNotEnding(receiptHandle, ".receiptHandle")
                assertNotEmptyAndNotEnding(version, ".version")
                assertNotEmptyAndNotEnding(user, ".user")
                assertNotEmptyAndNotEnding(hostname, ".hostname")
                assertNotEmptyAndNotEnding(sendTimestamp, ".sendTimestamp")
                assertNotEmptyAndNotEnding(body, ".body")

                if (testData.containsKey(body))
                    Assert.assertEquals(testData[body], sendTimestamp)
                else
                    println("Found unrecognized messages: $message")

                sqs.deleteMessage(profile, TEST_QUEUE_NAME, receiptHandle)
            }
        }

        return messages as List<QueueMessage>
    }

    private fun assertNotEmptyAndNotEnding(content: String, endsWith: String) {
        Assert.assertTrue(StringUtils.isNotEmpty(content))
        Assert.assertFalse(StringUtils.endsWith(content, endsWith))
    }

    private fun initContext(profile: String, async: Boolean = false) {
        context.setData("$profile.aws.accessKey", System.getProperty(SYSPROP_ACCESS_KEY))
        context.setData("$profile.aws.secretKey", System.getProperty(SYSPROP_SECRET_KEY))
        context.setData("$profile.aws.region", "us-west-1")
        context.setData("$profile.aws.async", if (async) "true" else "false")
        context.setData("$profile.aws.waitTimeMs", "1500")
        context.setData("$profile.aws.visibilityTimeoutMs", "1000")
    }

    companion object {
        internal val CLASSNAME = SqsCommandTest::class.java.simpleName
        internal val context = object : MockExecutionContext(true) {
            override fun getCurrentTestStep(): TestStep {
                return object : TestStep() {
                    override fun generateFilename(ext: String): String {
                        return CLASSNAME + StringUtils.prependIfMissing(StringUtils.trim(ext), ".")
                    }
                }
            }
        }
    }
}

//    @Test
//    fun receiveMessage() {
//        val PROFILE = "MySQS"
//        initContext(PROFILE)
//
//        val sqs = SqsCommand()
//        sqs.init(context)
//
//        val result = sqs.receiveMessage(PROFILE, TEST_QUEUE_NAME, "message")
//        println(result)
//        Assert.assertNotNull(result)
//        Assert.assertTrue(result.isSuccess)
//
//        val message = context.getObjectData("message")
//        println(message)
//        println(context.replaceTokens("$" + "{message}.id"))
//        println(context.replaceTokens("$" + "{message}.body"))
//        println(context.replaceTokens("$" + "{message}.attributes"))
//        println()
//        println(context.replaceTokens("$" + "{message}.attributes.c"))
//        println()
//        println(context.replaceTokens("$" + "{message}.[attributes].a"))
//        println()
//        println(context.replaceTokens("$" + "{message}.[attributes].[a]"))
//    }

