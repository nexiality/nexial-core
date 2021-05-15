package org.nexial.core.plugins.web

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
        var content: String? = null,
        var html: String? = null,
        var links: Set<String>? = null,
)