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

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.nexial.commons.proc.ProcessInvoker
import org.nexial.commons.proc.ProcessInvoker.WORKING_DIRECTORY
import org.nexial.commons.spring.SpringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst.DEF_FILE_ENCODING
import org.nexial.core.NexialConst.Data.MIME_JSON
import org.nexial.core.NexialConst.Data.MIME_PLAIN
import org.nexial.core.plugins.ws.WsCommand
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod.GET
import org.springframework.web.bind.annotation.RequestMethod.POST
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.io.File.separator
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy


@RestController("DistroService")
@RequestMapping(name = "DistroService",
                path = ["/distro"],
                produces = [MIME_JSON],
                consumes = [MIME_JSON, MIME_PLAIN],
                method = [POST, GET])
@ResponseBody
open class DistroService {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Autowired
    lateinit var config: ServiceConfig

    @PostConstruct
    fun startup() {
    }

    @PreDestroy
    fun shutdown() {
        if (logger.isInfoEnabled) logger.info("${SpringUtils.getMappedUri(this)} shutting down...")
    }

    @RequestMapping(path = ["list"], method = [GET])
    fun list(): Any {

        // 1. call distro url (e.g.: https://s3.us-west-2.amazonaws.com/ep.qm/nexial-distro/index.html)
        val doc = Jsoup.connect(config.distroLocation).get()
        logger.info(doc.title())

        // 2. parse through HTML
        // 3. construct list
        val distros = mutableListOf<Distro>()
        doc.select("table.distribution-listing tbody tr th a")
            .orEmpty()
            .sortedByDescending { list -> list.text() }
            .forEach { link: Element -> distros += Distro(link.text(), link.absUrl("href")) }

        // 4. return
        return distros
    }

    @RequestMapping(path = ["install"], method = [POST])
    fun install(@RequestBody payload: InstallRequest): InstallOutcome {
        // 1. request sanity check
        println("received request to install ${payload.version} to ${payload.home}")

        val outcome = InstallOutcome()
        outcome.info += "installing ${payload.version} to ${payload.home}"

        // 2. determine new target directory
//        val targetDir = determineTargetDirectory(payload)
        val targetDir = "/Users/ml093043/projects/nexial/nexial-core8gYnO"
        FileUtils.forceMkdir(File(targetDir))
        outcome.info += "target install directory is $targetDir"

        // 3. download distro payload to `tmp`
        val distroUrl = determineDistroUrl(payload)
        val distroFilename = StringUtils.substringAfterLast(distroUrl, "/")
        val distroVersion = StringUtils.substringBeforeLast(distroFilename, ".")

        val wsCommand = WsCommand()
        wsCommand.init(ServiceLauncher.context())

        val saveTo = StringUtils.appendIfMissing(targetDir, separator) + distroFilename
        outcome.info += "downloading $distroUrl to $saveTo"

//        val result = wsCommand.download(distroUrl, "", saveTo)
//        if (result.failed()) {
//            outcome.succeeded = false
//            outcome.failure += "ERROR: ${result.message}"
//            return outcome
//        }

//        outcome.info + "downloaded ${File(saveTo).length()} bytes"

        // 4. unzip distro payload to new target directory
        // don't do via Java API -- too slow
//        val unzipped = FileUtil.unzip(File(saveTo), File(targetDir))
//        if (CollectionUtils.isEmpty(unzipped)) {
//            outcome.succeeded = false
//            outcome.failure + "ERROR: unzipping nexial distro failed"
//            return outcome
//        }
        val batchContent: String = "#!/bin/sh\n" +
                                   "cd $targetDir\n" +
                                   "unzip -qo $saveTo\n" +
                                   "mv $saveTo /tmp\n" + // replace with rm -f
                                   "mv $distroVersion nexial-core\n" +
                                   "echo $distroVersion > nexial-core/build.txt\n" +
                                   "#curl -X POST -H \"Content-Type:application/json\" -d '' 'http://localhost:8080/services/lifecycle/shutdown'\n" +
                                   "wget --method=POST --header=\"Content-Type:application/json\" --body-data='' http://localhost:8080/services/lifecycle/shutdown\n" +
                                   "sleep 15\n" +
                                   "mkdir -p ../.archive\n" +
                                   "mv -fR ../nexial-core ../.archive/\n" +
                                   "mv nexial-core ../\n" +
                                   "cd ../nexial-core/bin\n" +
                                   "./nexial.sh -listen 38291 -listenCode catepillar\n" +
                                   "rm -fR $targetDir"
        val installer = File(StringUtils.appendIfMissing(targetDir, separator) + "install.sh")
        FileUtils.write(installer, batchContent, DEF_FILE_ENCODING)
        installer.setExecutable(true)

        val result2 = ProcessInvoker.invoke(installer.absolutePath, listOf(), mapOf(Pair(WORKING_DIRECTORY, targetDir)))
        println(result2)


        // 5. mark distro version in new target directory

        // 6. create restart script

        // 7. restart service (and when I revived from dead, I shall be the new version of me!)

        // 8. return outcome and message to wait
        return outcome
    }

    private fun determineTargetDirectory(payload: InstallRequest) =
        if (FileUtil.isDirectoryReadable(payload.home))
            payload.home + RandomStringUtils.randomAlphanumeric(5)
        else
            payload.home

    private fun determineDistroUrl(payload: InstallRequest) =
        if (StringUtils.startsWithIgnoreCase(payload.version, "http"))
            payload.version
        else StringUtils.substringBeforeLast(config.distroLocation, "/") + "/" +
             if (NumberUtils.isCreatable(payload.version))
                 "nexial-core-dev-0${payload.version}.zip"
             else
                 payload.version


}

data class Distro(val name: String, val link: String)
data class InstallRequest(val version: String, val home: String)
data class InstallOutcome(val info: ArrayList<String> = arrayListOf(),
                          val failure: ArrayList<String> = arrayListOf()) {
    var succeeded: Boolean = true
}