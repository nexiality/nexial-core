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

package org.nexial.core.plugins.io

import org.apache.commons.lang3.StringUtils
import org.apache.poi.poifs.crypt.HashAlgorithm.sha256
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.CheckUtils.*
import org.nexial.core.utils.OutputResolver
import java.io.FileInputStream
import java.io.FileOutputStream

class WordCommand : BaseCommand() {

    override fun getTarget() = "word"

    fun extractText(`var`: String, file: String): StepResult {
        requiresValidVariableName(`var`)

        val document = resolveWordDocument(file)

        var extractor: XWPFWordExtractor? = null

        return try {
            extractor = XWPFWordExtractor(document)
            context.setData(`var`, extractor.text)
            StepResult.success("Text extracted from '$file' and saved to data variable '$`var`")
        } catch (e: Exception) {
            context.removeData(`var`)
            StepResult.fail("Unable to extract text from '$file': ${e.message}")
        } finally {
            extractor?.close()
            document.close()
        }
    }

    override fun assertContains(file: String, text: String): StepResult {
        requiresNotEmpty(text, "invalid text", text)
        val document = resolveWordDocument(file)
        val extractor = XWPFWordExtractor(document)
        return if (StringUtils.equals(extractor.text, text))
            StepResult.success("validated '$file' contains '$text'")
        else
            StepResult.fail("'$file' does NOT contain '$text'")
    }

    override fun assertNotContain(file: String, text: String): StepResult {
        requiresNotEmpty(text, "invalid text", text)
        val document = resolveWordDocument(file)
        val extractor = XWPFWordExtractor(document)
        return if (StringUtils.equals(extractor.text, text))
            StepResult.fail("'$file' CONTAINS '$text'")
        else
            StepResult.success("validated '$file' does not contain '$text'")
    }

    fun assertPassword(file: String, password: String): StepResult {
        requiresNotBlank(password, "Invalid password", password)
        resolveWordDocument(file).use {
            return if (it.validateProtectionPassword(password))
                StepResult.success("Password validated for '$file'")
            else
                StepResult.fail("Password NOT validated for '$file'")
        }
    }

    fun assertReadOnly(file: String): StepResult {
        val document = resolveWordDocument(file)
        return if (document.isEnforcedProtection) {
            StepResult.success("'$file' is read-only")
        } else {
            StepResult.fail("'$file' is NOT read-only")
        }
    }

    fun assertNotReadOnly(file: String): StepResult {
        val document = resolveWordDocument(file)
        return if (document.isEnforcedReadonlyProtection) {
            StepResult.fail("'$file' IS read-only")
        } else {
            StepResult.success("'$file' is not read-only")
        }
    }

    fun readOnly(file: String, password: String?): StepResult {
        val document = resolveWordDocument(file)

        if (StringUtils.isBlank(password))
            document.enforceReadonlyProtection()
        else
            document.enforceReadonlyProtection(password, sha256)

        FileOutputStream(file).use { document.write(it) }

        return if (StringUtils.isBlank(password))
            StepResult.success("Read-only enforced on '$file'")
        else
            StepResult.success("Read-only enforced with password on '$file'")
    }

    fun removeProtection(file: String): StepResult {
        val document = resolveWordDocument(file)
        document.removeProtectionEnforcement()
        FileOutputStream(file).use { document.write(it) }
        return StepResult.success("Protection enforcement removed for '$file'")
    }

    // not working... need more testing
    // fun replaceText(file: String, search: String, replace: String): StepResult {
    //     val document = resolveWordDocument(file)
    //     var count = 0
    //     document.paragraphsIterator.forEach {
    //         if (RegexUtils.match(it.text, search, true)) {
    //             it.createRun().setText(RegexUtils.replaceMultiLines(it.text, search, replace))
    //             count++
    //         }
    //     }
    //
    //     FileOutputStream(file).use { document.write(it) }
    //
    //     return StepResult.success("text replacement performed $count time(s) on '$file'")
    // }

    protected fun resolveWordDocument(file: String): XWPFDocument {
        requiresReadableFileOrValidUrl(file)
        val wordFile = OutputResolver.resolveFile(file)
                       ?: throw IllegalArgumentException("Unable to reference Word document '$file")
        return XWPFDocument(FileInputStream(wordFile))
    }
}
