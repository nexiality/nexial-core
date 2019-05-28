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
 *
 */

package org.nexial.core.service

import org.nexial.commons.spring.SpringUtils
import org.nexial.core.NexialConst.Data.MIME_JSON
import org.nexial.core.NexialConst.Data.MIME_PLAIN
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod.POST
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@RestController("LifecycleService")
@RequestMapping(name = "LifecycleService",
                path = ["/lifecycle"],
                produces = [MIME_JSON],
                consumes = [MIME_JSON, MIME_PLAIN],
                method = [POST])
@ResponseBody
class LifecycleService {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @PostConstruct
    fun startup() {
    }

    @PreDestroy
    fun shutdown() {
        if (logger.isInfoEnabled) logger.info("${SpringUtils.getMappedUri(this)} shutting down...")
    }

    //    @RequestMapping(path = ["refresh"])
    //    fun refresh(): Any {
    //        ReadyLauncher.refresh()
    //        return SuccessResponse(message = "nexial-service refresh complete")
    //    }

    @RequestMapping(path = ["restart"])
    fun restart(): Any {
        newWorkerThread { ReadyLauncher.restart() }
        return SuccessResponse(message = "nexial-service restarting...")
    }

    @RequestMapping(path = ["shutdown"])
    fun stop(): Any {
        newWorkerThread { ReadyLauncher.shutdown() }
        return SuccessResponse(message = "nexial-service shutting down...")
    }

    private fun <T> newWorkerThread(body: () -> T): Thread {
        println("starting thread")
        val thread = Thread {
            Thread.sleep(2000)
            println("running thread: $body")
            body()
        }
        thread.isDaemon = false
        thread.start()
        if (logger.isDebugEnabled) logger.debug("thread started")
        return thread
    }
}