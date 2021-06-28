package org.nexial.core.plugins.web

/**
 * @author Dhanapathi.Marepalli
 * Mail Provider profile class which contains various profile related information.
 */
class WebMailProfile(val profileName: String,
                     val provider: String,
                     val inbox: String,
                     val domain: String,
                     val mailer: WebMailer)