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

package org.nexial.core.model

import com.amazonaws.regions.Regions
import com.itextpdf.text.PageSize
import com.itextpdf.text.PageSize.LETTER
import com.itextpdf.text.Rectangle
import com.itextpdf.text.RectangleReadOnly
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.USER_NAME
import org.nexial.commons.utils.RegexUtils
import org.nexial.core.NexialConst.AwsSettings.*
import org.nexial.core.NexialConst.NAMESPACE
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.CheckUtils.requiresNotNull
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtils.NEXIAL_MANIFEST

abstract class ServiceProfile(val name: String, val profileData: Map<String, String>) {

    companion object {
        @JvmStatic
        fun <ServiceProfiler> resolve(context: ExecutionContext, name: String, expectedType: Class<ServiceProfiler>):
                ServiceProfiler {
            requiresNotBlank(name, "invalid profile", name)

            val profileVar = NAMESPACE + "profile." + name
            if (context.hasData(profileVar)) {
                val profileObj = context.getObjectData(profileVar)
                if (profileObj != null && expectedType.isAssignableFrom(profileObj.javaClass)) {
                    ConsoleUtils.log("found profile '$name' for reuse")
                    return expectedType.cast(profileObj)
                }

                // remove erroneous data or wrong type
                context.removeData(profileVar)
            }

            // create new profile
            val profileData = context.getDataByPrefix("$name.")
            if (profileData.isEmpty()) throw IllegalArgumentException("No profile information found for '$name'")

            val serviceProfilerConstr = expectedType.getConstructor(String::class.java, Map::class.java)
            val serviceProfiler = serviceProfilerConstr.newInstance(name, profileData)
            context.setData(profileVar, serviceProfiler)
            return serviceProfiler
        }

        @JvmStatic
        fun <T> requiresActiveProfile(context: ExecutionContext,
                                      profile: String,
                                      target: String,
                                      expectedClass: Class<T>): Boolean {
            requiresNotNull(context, "Coding error -- Please contact Nexial support: context is missing")
            requiresNotBlank(profile, "Invalid profile", profile)
            requiresNotBlank(target, "Invalid or unknown profile type", target)
            requiresNotNull(expectedClass, "Invalid or unknown profile type", expectedClass)

            val profileKey = "$NAMESPACE$target.$profile"
            if (!context.hasData(profileKey)) throw AssertionError("No active profile '$profile' found")

            val profileObj = context.getObjectData(profileKey)
            if (profileObj == null || !expectedClass.isAssignableFrom(profileObj.javaClass)) {
                throw AssertionError("Invalid or incompatible profile object found for '$profile'")
            }

            return true
        }
    }
}

class AwsServiceProfile(name: String, profileData: Map<String, String>) : ServiceProfile(name, profileData) {
    val accessKey = profileData.getOrElse(AWS_ACCESS_KEY) {
        profileData.getOrElse("aws.${AWS_ACCESS_KEY}") {
            throw IllegalArgumentException("Missing profile data: '${name}.${AWS_ACCESS_KEY}'")
        }
    }
    val secretKey = profileData.getOrElse(AWS_SECRET_KEY) {
        profileData.getOrElse("aws.${AWS_SECRET_KEY}") {
            throw IllegalArgumentException("Missing profile data: '${name}.${AWS_SECRET_KEY}'")
        }
    }
    val regionName = profileData.getOrElse(AWS_REGION) { profileData.get("aws.${AWS_REGION}") }
    val region = if (StringUtils.isBlank(regionName)) Regions.DEFAULT_REGION else {
        Regions.fromName(regionName)
    }
}

class PdfOutputProfile(name: String, profileData: Map<String, String>) : ServiceProfile(name, profileData) {
    val pageSize = initPageSize(profileData.getOrDefault(PAGE_SIZE, "LETTER"))
    val margins = initMargins(profileData.getOrDefault(MARGIN, "0.5,0.5,0.5,0.5"))
    val author = profileData.getOrDefault("author", USER_NAME)
    val creator = NEXIAL_MANIFEST
    val keywords = profileData.getOrDefault(KEYWORDS, "")
    val subject = profileData.getOrDefault(SUBJECT, "")
    val title = profileData.getOrDefault(TITLE, "")
    val monospaced = profileData.getOrDefault(MONOSPACED, "true").toBoolean()
    val customProperties = filterProps(profileData)

    companion object {
        // common PDF properties
        private const val PAGE_SIZE = "pagesize"
        private const val MARGIN = "margins"
        private const val KEYWORDS = "keywords"
        private const val SUBJECT = "subject"
        private const val TITLE = "title"
        private const val MONOSPACED = "monospaced"
        private const val PREFIX_CUSTOM = "custom."

        // user should specify in inches, but here we using pica (72 pica = 1 in)
        private const val PDF_MARGIN_UNIT = 72
        private const val REGEX_CUSTOM_PAGE_SIZE = "([0-9\\.]+)\\s*[Xx]\\s*([0-9\\.]+)"

        private fun initMargins(marginText: String): Array<Float> {
            val marginArray = marginText.split(",")
            return if (marginArray.size != 4) {
                arrayOf(36f, 36f, 36f, 36f)
            } else {
                arrayOf(PDF_MARGIN_UNIT * marginArray[0].trim().toFloat(),
                        PDF_MARGIN_UNIT * marginArray[1].trim().toFloat(),
                        PDF_MARGIN_UNIT * marginArray[2].trim().toFloat(),
                        PDF_MARGIN_UNIT * marginArray[3].trim().toFloat())
            }
        }

        private fun initPageSize(pageSize: String): Rectangle = if (StringUtils.isBlank(pageSize)) {
            LETTER
        } else {
            val dimension = RegexUtils.collectGroups(pageSize, REGEX_CUSTOM_PAGE_SIZE)
            if (CollectionUtils.size(dimension) == 2) {
                RectangleReadOnly(PDF_MARGIN_UNIT * dimension[0].trim().toFloat(),
                                  PDF_MARGIN_UNIT * dimension[1].trim().toFloat())
            } else {
                PageSize.getRectangle(pageSize.toUpperCase())
            }
        }

        private fun filterProps(profileData: Map<String, String>): Map<String, String> {
            return if (MapUtils.isEmpty(profileData)) {
                HashMap()
            } else {
                profileData.filter { StringUtils.isNotBlank(it.value) && it.key.startsWith(PREFIX_CUSTOM) }
                    .mapKeys { StringUtils.substringAfter(it.key, PREFIX_CUSTOM) }
            }
        }
    }
}
