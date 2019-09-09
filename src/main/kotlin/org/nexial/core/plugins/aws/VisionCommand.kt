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

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Regions
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder
import com.amazonaws.services.rekognition.model.AmazonRekognitionException
import com.amazonaws.services.rekognition.model.DetectTextRequest
import com.amazonaws.services.rekognition.model.Image
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.IntegrationConfigException
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.CheckUtils.*
import org.nexial.core.utils.ConsoleUtils
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.text.DecimalFormat

class VisionCommand : BaseCommand() {

    override fun getTarget(): String = "aws.vision"

    fun saveText(profile: String, image: String, `var`: String): StepResult {
        requiresNotBlank(profile, "Invalid profile", profile)
        requiresReadableFile(image)
        requiresValidAndNotReadOnlyVariableName(`var`)

        val settings = resolveProfile(context, profile)
        val client = AmazonRekognitionClientBuilder.standard()
            .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(settings.accessKey, settings.secretKey)))
            .withRegion(settings.region)
            .build()

        val imageBytes = FileInputStream(File(image)).use { ByteBuffer.wrap(IOUtils.toByteArray(it)) }
        val request = DetectTextRequest().withImage(Image().withBytes(imageBytes))

        try {
            val result = client.detectText(request)
            // println("result = ${result}")
            val textDetections = result.textDetections
            // println("textDetections = ${textDetections}")

            if (CollectionUtils.isEmpty(textDetections)) {
                context.removeData(`var`)
                return StepResult.success("No text detected from image '$image'")
            }

            ConsoleUtils.log("detected text/words from '$image':")
            val items = RecognizedCollection(image)
            var lastYMarking = 0.0f
            for (text in textDetections) {
                if (text.type == "WORD") {
                    val dimension = text.geometry.boundingBox
                    val currentYMarking = dimension.top + dimension.height
                    val currentHalfHeight = dimension.height / 2
                    if (lastYMarking == 0f) lastYMarking = currentYMarking

                    val item = if (currentYMarking > lastYMarking + currentHalfHeight) {
                        // new line here
                        lastYMarking = currentYMarking
                        RecognizedText(text.detectedText, text.confidence.toDouble(), text.type, true)
                    } else {
                        RecognizedText(text.detectedText, text.confidence.toDouble(), text.type)
                    }

                    // ConsoleUtils.log("\t${printGeom(dimension.top)}\t${printGeom(dimension.left)}\t${printGeom(dimension.width)}\t${printGeom(dimension.height)}\t${item.text}")
                    items.add(item)
                }
            }

            if (items.isNotEmpty()) ConsoleUtils.log("text detection confidence: ${items.getConfidence()}")

            context.setData(`var`, items)
            return StepResult.success("recognized text from '$image' saved to '$`var`'")
        } catch (e: AmazonRekognitionException) {
            return StepResult.fail("Error occurred while scanning '$image': ${e.message}")
        }
    }

    private fun printGeom(geom: Float): String {
        val df = DecimalFormat.getInstance()
        df.maximumFractionDigits = 5
        df.minimumFractionDigits = 5
        return df.format(geom)
    }

    // fun ocr(profile: String, image:String, `var`:String) : StepResult {
    //     requiresNotBlank(profile, "Invalid profile", profile)
    //     requiresReadableFile(image)
    //     requiresValidVariableName(`var`)
    //
    //     val settings = resolveProfile(context, profile)
    //     val client = AmazonTextractClientBuilder.standard()
    //         .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(settings.accessKey, settings.secretKey)))
    //         .withRegion(settings.region)
    //         .build()
    //
    //     val imageBytes = FileInputStream(File(image)).use { ByteBuffer.wrap(IOUtils.toByteArray(it)) }
    //     val request = DetectDocumentTextRequest().withDocument(Document().withBytes(imageBytes))
    //     val result = client.detectDocumentText(request)
    //
    //     // println(result.documentMetadata)
    //     // println()
    //     // println()
    //
    //     val format = DecimalFormat("0.00")
    //     format.roundingMode = HALF_UP
    //     var lastTopPosition = 0.0
    //     result.blocks.forEach {
    //         if (it.blockType == "LINE") {
    //             print(it.text)
    //             val currentTopPosition = format.parse(format.format(it.geometry.boundingBox.top)).toDouble()
    //             if (currentTopPosition != lastTopPosition) {
    //                 println()
    //                 // println("\t-- found new line ${currentTopPosition} != ${lastTopPosition}")
    //                 lastTopPosition = currentTopPosition
    //             }
    //         }
    //         // println("block type   \t: ${it.blockType}")
    //         // println("row index    \t: ${it.rowIndex}")
    //         // println("row span     \t: ${it.rowSpan}")
    //         // println("column index \t: ${it.columnIndex}")
    //         // println("column span  \t: ${it.columnSpan}")
    //         // println("confidence   \t: ${it.confidence}")
    //         // println("page         \t: ${it.page}")
    //         // println("relationships\t: ${it.relationships}")
    //         // println()
    //     }
    //
    // }

    @Throws(IntegrationConfigException::class)
    private fun resolveProfile(context: ExecutionContext, profile: String): AwsSettings {
        val settings = if (profile == "system") {
            val accessKey = System.getProperty("nexial.vision.accessKey")
            if (StringUtils.isBlank(accessKey))
                throw IntegrationConfigException("'system' profile not available or not properly set up")

            val secretKey = System.getProperty("nexial.vision.secretKey")
            if (StringUtils.isBlank(secretKey))
                throw IntegrationConfigException("'system' profile not available or not properly set up")

            AwsSettings(accessKey,
                        secretKey,
                        Regions.fromName(System.getProperty("nexial.vision.region", Regions.DEFAULT_REGION.getName())))
        } else {
            AwsUtils.resolveAwsSettings(context, profile)
        }

        requiresNotNull(settings, "Unable to resolve AWS credentials for AWS Computer Vision services")
        return settings!!
    }
}

data class RecognizedText(val text: String, val confidence: Double, val type: String, val newline: Boolean = false)

data class RecognizedCollection(val source: String) : ArrayList<RecognizedText>() {
    @JvmName("getText")
    fun getText(): String {
        return if (isEmpty())
            ""
        else {
            val buffer = StringBuilder()
            this.forEach { buffer.append(if (it.newline) "\n" else "").append(it.text).append(" ") }
            buffer.toString()
        }
    }

    @JvmName("getConfidence")
    fun getConfidence() = this.sumByDouble { it.confidence } / this.size
}