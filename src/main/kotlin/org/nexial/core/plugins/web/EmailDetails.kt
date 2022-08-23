package org.nexial.core.plugins.web

import org.apache.commons.collections4.map.ListOrderedMap
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
 *  * link([EmailDetails.link]) in the Email
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
        internal set

    var html: String? = null
        internal set

    internal var link = ListOrderedMap<String, String>()
    internal var links = listOf<String>()

    fun getAttachments(): List<String> = attachmentMap.keys.toList()
    fun getLinks(): List<String> = links
    fun getLink(): ListOrderedMap<String, String> = link

    override fun toString(): String {
        return "id         =$id\n" +
               "subject    =$subject\n" +
               "from       =$from\n" +
               "to         =$to\n" +
               "time       =$time\n" +
               "content    =$content\n" +
               "html       =$html\n" +
               "link       =$link\n" +
               "links      =$links\n" +
               "attachments=${getAttachments()}"
    }
}