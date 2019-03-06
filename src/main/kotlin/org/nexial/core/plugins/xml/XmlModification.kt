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

import org.apache.commons.lang3.StringUtils
import org.jdom2.Attribute
import org.jdom2.Element
import org.jdom2.JDOMException
import org.jdom2.Text
import org.nexial.commons.utils.XmlUtils
import org.nexial.core.plugins.xml.XmlCommand.cleanXmlContent
import org.nexial.core.utils.ConsoleUtils
import java.io.IOException

internal val append = Append()
internal val prepend = Prepend()
internal val replace = Replace()
internal val delete = Delete()
internal val clear = Clear()

internal abstract class Modification(val action: String,  val requireInput: Boolean) {

    fun modify(candidates: List<Any>, content: String?): Int {
        if (requireInput && StringUtils.isEmpty(content)) {
            ConsoleUtils.log("Unable to $action to target XML since no content was specified")
            return 0
        }

        val childNode = if (requireInput) deriveXmlDocument(content!!)?.detachRootElement() else null
        var edits = 0

        candidates.forEach { match ->
            when (match.javaClass) {
                Element::class.java   -> {
                    val element = match as Element
                    if (childNode != null) {
                        handleModification(element, childNode.clone())
                    } else {
                        // add as text
                        handleModification(element, content)
                    }
                    edits += 1
                }

                Attribute::class.java -> {
                    handleModification(match as Attribute, content)
                    edits += 1
                }

                else                  -> {
                    // err for now
                    ConsoleUtils.log("$action cannot be executed on unsupported node: " + match.javaClass.simpleName)
                }
            }
        }

        return edits
    }

    abstract fun handleModification(target: Element, childElement: Element)
    abstract fun handleModification(target: Element, content: String?)
    abstract fun handleModification(target: Attribute, content: String?)

    private fun deriveXmlDocument(xml: String) = try {
        // support path-based content specification
        val cleanXml = cleanXmlContent(xml)
        if (!StringUtils.startsWith(cleanXml, "<") || !StringUtils.endsWith(cleanXml, ">")) {
            null
        } else XmlUtils.parse(cleanXml)
    } catch (e: IOException) {
        // shh.. exit quietly...
        null
    } catch (e: JDOMException) {
        // shh.. exit quietly...
        null
    }

    companion object {
        fun getAppend() = append
        fun getPrepend() = prepend
        fun getReplace() = replace
        fun getDelete() = delete
        fun getClear() = clear
    }
}

internal class Append : Modification("append", true) {
    override fun handleModification(target: Element, childElement: Element) {
        target.addContent(childElement)
    }

    override fun handleModification(target: Element, content: String?) {
        target.addContent(content)
    }

    override fun handleModification(target: Attribute, content: String?) {
        target.value = target.value + content
    }
}

internal class Prepend : Modification("prepend", true) {
    override fun handleModification(target: Element, childElement: Element) {
        target.addContent(0, childElement)
    }

    override fun handleModification(target: Element, content: String?) {
        target.addContent(0, Text(content))
    }

    override fun handleModification(target: Attribute, content: String?) {
        target.value = content + target.value
    }
}

internal class Replace : Modification("replace", true) {
    override fun handleModification(target: Element, childElement: Element) {
        val index = target.parent.content.indexOf(target)
        target.parent.addContent(index, childElement)
        target.parent.removeContent(target)
    }

    override fun handleModification(target: Element, content: String?) {
        target.setContent(Text(content))
    }

    override fun handleModification(target: Attribute, content: String?) {
        target.value = content
    }
}

internal class Delete : Modification("delete", false) {
    override fun handleModification(target: Element, childElement: Element) {
        delete(target)
    }

    override fun handleModification(target: Element, content: String?) {
        delete(target)
    }

    override fun handleModification(target: Attribute, content: String?) {
        target.parent.removeAttribute(target)
    }

    private fun delete(target: Element) {
        target.parent.removeContent(target)
    }
}

internal class Clear : Modification("clear", false) {
    override fun handleModification(target: Element, childElement: Element) {
        clear(target)
    }

    override fun handleModification(target: Element, content: String?) {
        clear(target)
    }

    override fun handleModification(target: Attribute, content: String?) {
        target.detach()
    }

    private fun clear(target: Element) {
        target.removeContent()
    }
}
