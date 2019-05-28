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

package org.nexial.core.service

import org.nexial.commons.utils.EnvUtils
import org.nexial.core.utils.ConsoleUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.*
import kotlin.concurrent.thread

/**
 * wrapper for server-to-client announcements
 * @property details List<Announcement>
 * @constructor
 */
data class Announcements(var details: List<Announcement> = arrayListOf()) {

    constructor() : this(arrayListOf())
}

/**
 * announcement from server-to-client
 * @property timestamp Long
 * @property message String
 * @constructor
 */
data class Announcement(val timestamp: Long, val message: String)

@Component
@ConfigurationProperties("nexial-ready.announcement")
class ScheduledAnnouncer {

    lateinit var destination: String
    lateinit var bufferSize: Integer

    @Autowired
    lateinit var messenger: SimpMessagingTemplate

    // todo: remove ; test code
    init {
        // TestAnnouncementInjector.run { }
    }

    @Scheduled(fixedDelay = 5000)
    fun announce() {
        if (hasAnnouncements(bufferSize)) {
            val announcements = flushAnnouncements()
            ConsoleUtils.log("sending announcements: ${announcements.details.size}")
            messenger.convertAndSend("$destination/messages", announcements)
        }
    }

    companion object {
        private val announcements = Announcements()

        fun addAnnouncements(vararg announcement: Announcement) {
            if (announcement.isNotEmpty()) announcement.forEach { announcements.details += it }
        }

        fun hasAnnouncements() = announcements.details.isNotEmpty()
        fun hasAnnouncements(atLeast: Integer) = announcements.details.size >= atLeast.toInt()

        fun flushAnnouncements(): Announcements {
            val messages = announcements.copy()
            announcements.details = arrayListOf()
            return messages
        }
    }
}

// todo: remove ; test code
object TestAnnouncementInjector {
    private var counter = 0

    init {
        thread(start = true, name = TestAnnouncementInjector::class.java.simpleName) {
            while (true) {
                Thread.sleep(5000)
                counter++
                if (counter % 10 == 0) {
                    ConsoleUtils.log("adding sample announcements...")
                    ScheduledAnnouncer.addAnnouncements(
                            Announcement(System.currentTimeMillis(), "The time's now ${Date()}"),
                            Announcement(System.currentTimeMillis(), "The host is ${EnvUtils.getHostName()}"))
                }
            }
        }
    }
}
