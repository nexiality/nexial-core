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
import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst
import org.nexial.core.NexialConst.Data.THIRD_PARTY_LOG_PATH
import org.nexial.core.NexialConst.Project.resolveStandardPaths
import org.nexial.core.NexialConst.SUBDIR_LOGS
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.ExecutionDefinition
import org.nexial.core.model.TestProject
import org.nexial.core.utils.ExecUtil
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.support.SpringBootServletInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import java.io.File
import java.io.File.separator
import java.util.*

@SpringBootApplication
@EnableAutoConfiguration(exclude = [
    JacksonAutoConfiguration::class,
    DataSourceAutoConfiguration::class,
    DataSourceTransactionManagerAutoConfiguration::class])
@ComponentScan(basePackages = ["org.nexial.core.service"])
@Configuration
open class ServiceLauncher : SpringBootServletInitializer() {
    companion object {
        private const val DEF_PROJECT_NAME = "nexial-services"

        val args = ArrayList<String>()
        lateinit var springContext: ConfigurableApplicationContext
        lateinit var application: SpringApplication
        private lateinit var context: ExecutionContext

        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) = start(args)

        fun start(args: Array<String>) {
            Companion.args += args

            // 1. create `service` project
            val runId = ExecUtil.deriveRunId()
            val project = resolveProject()

            // 2. register log directory and system properties
            System.setProperty(THIRD_PARTY_LOG_PATH, NexialConst.Project.appendLog(project.outPath) + SUBDIR_LOGS)

            // 3. new context
            val execDef = ExecutionDefinition()
            execDef.project = project
            execDef.runId = runId
            context = ExecutionContext(execDef)

            // 4. start spring boot
            val builder = SpringApplicationBuilder(ServiceLauncher::class.java)
            application = builder.application()
            springContext = builder.build().run(*args)
            springContext.registerShutdownHook()
        }

        fun context() = context

        private fun resolveProject(): TestProject {
            var project = TestProject()
            project.projectHome = StringUtils.appendIfMissing(File("").absoluteFile.parent, separator) +
                DEF_PROJECT_NAME
            project.isStandardStructure = true
            project = resolveStandardPaths(project)
            FileUtils.forceMkdir(File(project.scriptPath))
            FileUtils.forceMkdir(File(project.dataPath))
            FileUtils.forceMkdir(File(project.planPath))
            FileUtils.forceMkdir(File(project.outPath + SUBDIR_LOGS))
            return project
        }

//        fun refresh() {
//            requireNotNull(springContext)
//
//            springContext.stop()
//            try {
//                Thread.sleep(1500)
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//            springContext.refresh()
//            springContext.start()
//        }

        fun restart() {
            requireNotNull(springContext)
            requireNotNull(application)

            shutdown()

            val temp = args.toArray(Array(
                args.size, { "" }))
            args.clear()
            start(temp)
        }

        fun shutdown() {
            requireNotNull(springContext)

            SpringApplication.exit(springContext, ExitCodeGenerator { 0 })
            springContext.close()
        }
    }

    override fun configure(app: SpringApplicationBuilder): SpringApplicationBuilder =
        app.sources(ServiceLauncher::class.java)

//    open fun fowardToIndex(): WebMvcAutoConfigurationAdapter {
//
//    }
}