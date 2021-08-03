package org.nexial.core.plugins.web

import org.apache.commons.collections4.map.ListOrderedMap
import org.apache.commons.lang3.StringUtils
import org.jsoup.Jsoup
import org.nexial.commons.utils.RegexUtils
import java.time.LocalDateTime

/**
 * POJO class that represents the email information with the following:-
 *
 *  * Id ([EmailDetails.id]) of the email. This is generally the Id of the tr element.
 *  * Subject([EmailDetails.subject]) of the Email
 *  * Sender([EmailDetails.from]) of the Email
 *  * Receiver([EmailDetails.to]) of the Email
 *  * Time([EmailDetails.time]) at which the email is received.
 *  * Content([EmailDetails.content]) of the Email
 *  * HTML content([EmailDetails.html]) of the Email
 *  * links([EmailDetails.links]) in the Email
 *  * attachments ([EmailDetails.attachmentMap]) of the email.
 *
 */
data class EmailDetails(
    val id: String,
    val subject: String,
    val from: String,
    val to: String,
    val time: LocalDateTime,
) {
    internal val attachmentMap = ListOrderedMap<String, String>()
    var content: String? = null
        internal set(value) {
            field = value
        }
    var html: String? = null
        internal set(value) {
            field = value
            extractLinks()
        }

    var links: Set<String>? = null

    init {
        extractLinks()
    }

    internal fun addAttachment(attachment: String) {
        val attachmentName = attachment.replace('\\', '/').substringAfterLast(delimiter = "/")
        attachmentMap[attachmentName] = attachment
    }

    fun getAttachments(): List<String> = attachmentMap.keys.toList()

    private fun extractLinks() {
        if (StringUtils.isNotEmpty(html))
            links = Jsoup.parse(removeConditionalComments(html!!))
                .getElementsByAttribute("href")
                .map { it.attr("href") }
                .toSet()
    }

    private fun removeConditionalComments(html: String): String {
        var replaced = html
        while (StringUtils.contains(replaced, "<!--[if ") || StringUtils.contains(replaced, "<![endif]-->")) {
            replaced = RegexUtils.removeMatches(replaced, "\\<\\!\\-\\-\\[if\\ .+\\].*\\>")
            replaced = StringUtils.remove(replaced, "<![endif]-->")
        }
        return replaced
    }

    override fun toString(): String {
        return "id         =$id\n" +
               "subject    =$subject\n" +
               "from       =$from\n" +
               "to         =$to\n" +
               "time       =$time\n" +
               "content    =$content\n" +
               "html       =$html\n" +
               "links      =$links\n" +
               "attachments=${getAttachments()}"
    }
}