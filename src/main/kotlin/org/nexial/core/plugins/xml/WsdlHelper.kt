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

package org.nexial.core.plugins.xml

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.jdom2.JDOMException
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.RegexUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.commons.utils.XmlUtils
import org.nexial.core.NexialConst.DEF_FILE_ENCODING
import org.nexial.core.NexialConst.PRETTY_XML_OUTPUTTER
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.ws.WsCommand
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.OutputFileUtils
import org.nexial.core.variable.Syspath
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.ls.DOMImplementationLS
import org.xml.sax.SAXException
import java.io.File
import java.io.File.separator
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import javax.annotation.Nullable
import javax.validation.constraints.NotNull
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import kotlin.streams.toList

const val XML_NS = "http://www.w3.org/2001/XMLSchema"
const val WSDL_TYPES = "wsdl:types"
const val SCHEMA1 = "schema"
const val SCHEMA2 = ":schema"
const val XSD_EXT = ".xsd"
const val WSDL_EXT = ".wsdl"
const val PARSE_ERROR_PREFIX = "Unable to parse XML correctly: "

class WsdlHelper(val context: ExecutionContext) {

    private var workingDir: String = StringUtils.appendIfMissing(Syspath().out("fullpath"), separator)

    @NotNull
    @Throws(IOException::class)
    fun download(wsdl: String): File {

        val localWsdl: File
        if (StringUtils.startsWith(wsdl.toLowerCase(), "http")) {
            localWsdl = File(workingDir +
                             StringUtils.substringBefore(StringUtils.substringAfterLast(wsdl, "/"), "?") +
                             WSDL_EXT)

            val ws = context.findPlugin("ws") as WsCommand
            val result = ws.download(wsdl, "", localWsdl.absolutePath)
            if (result.failed()) throw IOException(result.message)

            ConsoleUtils.log("successfully downloaded WSDL '$wsdl' to $localWsdl")
            return localWsdl
        }

        // if this is not a URL, then it must be a file
        if (!FileUtil.isFileReadable(wsdl, 512)) throw IOException("Invalid WSDL file '$wsdl'")

        val srcWsdlFile = File(wsdl)
        localWsdl = File(workingDir + StringUtils.substringBeforeLast(srcWsdlFile.name, ".") + WSDL_EXT)
        try {
            FileUtils.copyFile(srcWsdlFile, localWsdl)
            ConsoleUtils.log("successfully copy WSDL file '$wsdl' to $localWsdl")
            return localWsdl
        } catch (e: IOException) {
            throw IOException("Unable to read/copy WSDL file '" + wsdl + "': " + e.message)
        }
    }

    @Throws(IOException::class, JDOMException::class, ParserConfigurationException::class, SAXException::class)
    fun extractSchemas(wsdl: File): List<File> {

        val xsdFiles = ArrayList<File>()

        FileInputStream(wsdl).use { input ->
            val builder = getDocBuilder()

            val wsdlDoc = builder.parse(input)
            if (wsdlDoc == null || !wsdlDoc.hasChildNodes()) return xsdFiles

            val types = wsdlDoc.documentElement.getElementsByTagName(WSDL_TYPES)
            if (types == null || types.length < 1) return xsdFiles

            // only expects one "types" node
            val type = types.item(0) as Element
            val childNodes = type.childNodes
            if (childNodes == null || childNodes.length < 1) return xsdFiles

            for (k in 0 until childNodes.length) {
                val childNode = childNodes.item(k) as? Element ?: continue

                val childNodeName = childNode.nodeName
                // we are only interested in "<schema>" or "<???:schema>" nodes
                if (!StringUtils.equals(childNodeName, SCHEMA1) && !StringUtils.endsWith(childNodeName, SCHEMA2)) {
                    break
                }

                // found a schema node
                val xsdFileName = deriveSchemaFileName(wsdl, childNode)

                val xsdDoc = builder.newDocument()
                val root = newSchemaRoot(xsdDoc, childNodeName)

                xsdDoc.appendChild(root)

                val xsdNodes = childNode.childNodes
                for (j in 0 until xsdNodes.length) root.appendChild(xsdDoc.importNode(xsdNodes.item(j), true))

                val xsdFileLocation = workingDir + xsdFileName
                val xsdFile = File(xsdFileLocation)
                FileUtils.writeStringToFile(xsdFile, deserialize(xsdDoc), DEF_FILE_ENCODING)
                xsdFiles.add(xsdFile)
            }

            return xsdFiles
        }
    }

    @NotNull
    @Throws(JDOMException::class, IOException::class)
    fun deriveSoapBody(xml: String): org.jdom2.Element {
        val doc = XmlUtils.parse(OutputFileUtils.resolveContent(xml, context, false))
        if (doc == null || !doc.hasRootElement()) throw IOException(PARSE_ERROR_PREFIX + "No root node")

        val root = doc.rootElement
        val namespace = root.namespace
        val nsPrefix = namespace.prefix

        val body = root.getChild("Body", namespace)
        if (body == null || body.contentSize < 1) {
            throw IOException(PARSE_ERROR_PREFIX + "No valid <" + nsPrefix + ":Body> node")
        }
        return body
    }

    /** is this a fault xml?  */
    @NotNull
    fun isSoapFault(body: org.jdom2.Element) = body.getChild("Fault", body.namespace) != null

    @NotNull
    @Throws(IOException::class)
    fun extractSoapContent(body: org.jdom2.Element): String {
        val namespace = body.namespace
        val prefix = namespace.prefix

        // is this a fault xml?
        val contentNode: org.jdom2.Element = (if (isSoapFault(body)) {
            val fault = body.getChild("Fault", namespace)
            if (fault.getChild("detail") == null) {
                throw IOException(PARSE_ERROR_PREFIX + "Improper/Invalid <" + prefix + ":Fault> node; " +
                                  "unable to validate content")
            }

            fault.getChild("detail").children[0]
        } else {
            body.children[0]
        }) ?: throw IOException(PARSE_ERROR_PREFIX + "No valid content node under <" + prefix + ":Body>")

        // remove namespace
        var content = PRETTY_XML_OUTPUTTER.outputString(contentNode)
        content = RegexUtils.replace(content, "\\<[0-9A-Za-z]+\\:", "<")
        content = RegexUtils.replace(content, "\\<\\/[0-9A-Za-z]+\\:", "</")

        return content
    }

    @Nullable
    @Throws(JDOMException::class, IOException::class)
    fun deriveSoapFault(xml: String): org.jdom2.Element? {
        val body = deriveSoapBody(xml)
        return if (isSoapFault(body)) {
            val fault = body.getChild("Fault", body.namespace)
            if (fault == null || fault.children.isEmpty()) null else fault
        } else null
    }

    @NotNull
    fun newSchemaRoot(xsdDoc: Document, childNodeName: String): Element {
        val root: Element
        if (StringUtils.contains(childNodeName, ":")) {
            val ns = StringUtils.substringBefore(childNodeName, ":")
            root = xsdDoc.createElement(ns + SCHEMA2)
            root.setAttribute("xmlns:$ns", XML_NS)
        } else {
            root = xsdDoc.createElement(SCHEMA1)
            root.setAttribute("xmlns", XML_NS)
        }
        return root
    }

    @NotNull
    fun deriveSchemaFileName(wsdl: File, schema: Element): String {
        val xsdFileName = StringUtils.defaultIfBlank(schema.getAttribute("targetNamespace"),
                                                     StringUtils.substringBeforeLast(wsdl.name, "."))
        return StringUtils.removeEnd(RegexUtils.removeMatches(xsdFileName, "https?\\:\\/\\/"), "/") + XSD_EXT
    }

    @NotNull
    @Throws(IOException::class, JDOMException::class)
    private fun deserialize(document: Document): String {
        val domImplementationLS = document.implementation as DOMImplementationLS
        val lsSerializer = domImplementationLS.createLSSerializer()
        val xsd = PRETTY_XML_OUTPUTTER.outputString(XmlUtils.parse(lsSerializer.writeToString(document)))
        return TextUtils.toString(TextUtils.toList(xsd, "\n", false)
                                      .stream()
                                      .map {
                                          if (StringUtils.contains(it, " type=\"xs:") ||
                                              StringUtils.contains(it, " type=\"xsd:"))
                                              it
                                          else
                                              RegexUtils.replace(it, " type=\"[0-9A-Za-z]+\\:", " type=\"")
                                      }.toList(), "\n")
    }

    @Throws(ParserConfigurationException::class)
    private fun getDocBuilder() = DocumentBuilderFactory.newInstance().newDocumentBuilder()
}