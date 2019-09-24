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

package org.nexial.core.plugins.pdf

import org.apache.commons.lang3.StringUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.nexial.core.NexialConst.Data.PDF_USE_ASCII
import org.nexial.core.NexialConst.PdfMeta.MIME_PDF
import org.nexial.core.SystemVariables.getDefaultBool
import org.nexial.core.model.ExecutionContext
import org.nexial.core.utils.CheckUtils.requiresReadableFile
import org.nexial.core.utils.ConsoleUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer

object PdfTextExtractor {

    @JvmStatic
    @Throws(IOException::class)
    fun extractText(file: String, context: ExecutionContext): String {
        requiresReadableFile(file)

        val pdfFile = File(file)
        val out = ByteArrayOutputStream()
        val output = OutputStreamWriter(out)

        var document: PDDocument? = null
        try {
            document = PDDocument.load(pdfFile)

            //use default encoding
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            //stripper.setShouldSeparateByBeads(true);
            //stripper.setAddMoreFormatting(false);

            // Extract text for main document:
            stripper.writeText(document, output)

            // ... also for any embedded PDFs:
            extractEmbeddedPDFs(document, stripper, output)
        } finally {
            try {
                output.close()
            } catch (e: IOException) {
                ConsoleUtils.error("Unable to close output stream: ${e.message}")
            }

            try {
                out.close()
            } catch (e: IOException) {
                ConsoleUtils.error("Unable to close underlying output stream: ${e.message}")
            }

            if (document != null) {
                try {
                    document.close()
                } catch (e: IOException) {
                    ConsoleUtils.error("Unable to close PDF document: ${e.message}")
                }
            }
        }

        val content = normalizePdfText(out.toString(), context)
        ConsoleUtils.log("extracted " + StringUtils.length(content) + " bytes from '" + file + "'")
        return content
    }

    @JvmStatic
    fun normalizePdfText(output: String, context: ExecutionContext) = normalizeContent(output, context)

    @JvmStatic
    fun normalizeContent(content: String, context: ExecutionContext): String {
        var normalized = StringUtils.trim(content)

        // let's make sure the test can be platform independent by favoring newline or carriage return
        normalized = StringUtils.replace(normalized, "\r\n", "\n")
        normalized = StringUtils.replace(normalized, "\r", "\n")

        if (context.getBooleanData(PDF_USE_ASCII, getDefaultBool(PDF_USE_ASCII))) {
            // ‘ (U+2018) LEFT SINGLE QUOTATION MARK
            // ’ (U+2019) RIGHT SINGLE QUOTATION MARK
            // “ (U+201C) LEFT DOUBLE QUOTATION MARK
            // ” (U+201D) RIGHT DOUBLE QUOTATION MARK

            normalized = StringUtils.replace(normalized, "\u2013", "-")
            normalized = StringUtils.replace(normalized, "\u2014", "-")
            normalized = StringUtils.replace(normalized, "\u2015", "-")

            normalized = StringUtils.replace(normalized, "\u2017", "_")

            normalized = StringUtils.replace(normalized, "\u0092", "\"")
            normalized = StringUtils.replace(normalized, "\u0093", "\"")
            normalized = StringUtils.replace(normalized, "\u0094", "\"")
            normalized = StringUtils.replace(normalized, "\u201b", "\"")
            normalized = StringUtils.replace(normalized, "\u201c", "\"")
            normalized = StringUtils.replace(normalized, "\u201d", "\"")
            normalized = StringUtils.replace(normalized, "\u201e", "\"")
            normalized = StringUtils.replace(normalized, "\u2032", "\"")
            normalized = StringUtils.replace(normalized, "\u2033", "\"")

            normalized = StringUtils.replace(normalized, "\u201a", "\'")
            normalized = StringUtils.replace(normalized, "\u2018", "\'")
            normalized = StringUtils.replace(normalized, "\u2019", "\'")
            normalized = StringUtils.replace(normalized, "\u2039", "\'")
            normalized = StringUtils.replace(normalized, "\u203a", "\'")

            normalized = StringUtils.replace(normalized, "\u201a", ",")

            normalized = StringUtils.replace(normalized, "\u2026", "...")
        }

        return normalized
    }

    @Throws(IOException::class)
    private fun extractEmbeddedPDFs(document: PDDocument?, stripper: PDFTextStripper?, output: Writer?) {
        if (document == null) return
        if (stripper == null) return
        if (output == null) return

        val catalog = document.documentCatalog
        val names = catalog.names ?: return
        val embeddedFiles = names.embeddedFiles ?: return
        val embeddedFileNames = embeddedFiles.names ?: return

        for ((_, spec) in embeddedFileNames) {
            val file = spec.embeddedFile
            if (file != null && StringUtils.equals(file.subtype, MIME_PDF)) {
                ConsoleUtils.log("Found embed PDF: '" + spec.filename + "', size: " + file.size)
                var subDoc: PDDocument? = null
                file.createInputStream().use { fis -> subDoc = PDDocument.load(fis) }
                try {
                    stripper.writeText(subDoc, output)
                } finally {
                    subDoc?.close()
                }
            }
        }
    }
}