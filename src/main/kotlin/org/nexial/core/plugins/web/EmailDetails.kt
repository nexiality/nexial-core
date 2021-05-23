package org.nexial.core.plugins.web

import org.apache.commons.lang3.StringUtils
import org.jsoup.Jsoup
import org.nexial.commons.utils.RegexUtils
import java.time.LocalDateTime

/**
 * POJO class that represents the email information with the following:-
 *
 *  * Subject([EmailDetails.subject]) of the Email
 *  * Sender([EmailDetails.from]) of the Email
 *  * Receiver([EmailDetails.to]) of the Email
 *  * Content([EmailDetails.content]) of the Email
 *  * HTML content([EmailDetails.html]) of the Email
 *  * links([EmailDetails.links]) in the Email
 *  * Time([EmailDetails.time]) at which the email is received.
 *  * Id ([EmailDetails.id]) of the email. This is generally the Id of the tr element.
 *
 */
data class EmailDetails(
    val id: String,
    val subject: String,
    val from: String,
    val to: String,
    val time: LocalDateTime,
) {

    var content: String? = null
    var html: String? = null
        set(value) {
            field = value
            extractLinks()
        }

    var links: Set<String>? = null

    init {
        extractLinks()
    }

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
}