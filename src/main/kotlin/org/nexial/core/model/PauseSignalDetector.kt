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
package org.nexial.core.model

import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.FlowControls.OPT_PAUSE_SIGNAL
import org.nexial.core.NexialConst.FlowControls.OPT_PAUSE_SIGNAL_TIMER
import org.nexial.core.ShutdownAdvisor
import org.nexial.core.SystemVariables.getDefault
import org.nexial.core.SystemVariables.getDefaultInt
import org.nexial.core.plugins.ForcefulTerminate
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

class PauseSignalDetector private constructor(private val interval: Long, private val pauseSignal: String) :
        ForcefulTerminate {

    private var stopNow = false
    private val inputs = mutableListOf<String>()
    private var stdinTask: StdinTask? = null
    private var timer = Timer()

    private class StdinTask(private val br: BufferedReader, private val caller: PauseSignalDetector) : TimerTask() {
        override fun run() {
            if (caller.stopNow) {
                cancel()
            } else {
                try { // wait until we have data to complete a readLine()
                    while (!br.ready()) Thread.sleep(100)
                    val input = br.readLine()
                    if (StringUtils.isNotEmpty(input)) caller.inputs.add(input)
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun flushInputs(): List<String> {
        var consoleInputs: List<String>
        synchronized(inputs) {
            consoleInputs = ArrayList(inputs)
            inputs.clear()
        }
        return consoleInputs
    }

    fun detectedPause(): Boolean {
        return flushInputs().find { s -> StringUtils.equals(s, pauseSignal) } != null
    }

    // fun stopNow() {
    //     stopNow = true
    // }

    private fun readStdin() {
        stdinTask = StdinTask(BufferedReader(InputStreamReader(System.`in`)), this)
        timer.scheduleAtFixedRate(stdinTask, 0, interval)
    }

    override fun mustForcefullyTerminate(): Boolean = self != null

    override fun forcefulTerminate() {
        stopNow = true
        timer.cancel()
        inputs.clear()
        stdinTask = null
    }

    companion object {
        private var self: PauseSignalDetector? = null

        @JvmStatic
        fun getInstance(context: ExecutionContext): PauseSignalDetector {
            if (self == null) {
                val interval = context.getIntData(OPT_PAUSE_SIGNAL_TIMER, getDefaultInt(OPT_PAUSE_SIGNAL_TIMER))
                val pauseSignal = context.getStringData(OPT_PAUSE_SIGNAL, getDefault(OPT_PAUSE_SIGNAL))
                self = PauseSignalDetector(interval.toLong(), pauseSignal)
                ShutdownAdvisor.addAdvisor(self)
                self!!.readStdin()
            }
            return self!!
        }
    }

    // public static void main(String[] args) throws Exception {
    //     System.out.println("Enter !!! to stop");
//     ConsoleInput consoleInput = ConsoleInput.getInstance();
//
//     while (true) {
//         System.out.print("Please type something: ");
//
//         Thread.sleep(5000);
//         System.out.println("checking input...");
//         List<String> inputs = consoleInput.flushInputs();
//
//         System.out.println();
//         if (CollectionUtils.isEmpty(inputs)) {
//             System.out.println("No input");
//         } else {
//             if (inputs.stream().anyMatch(s -> StringUtils.startsWith(s, "!!!"))) {
//                 System.out.println("stop signal detected...");
//                 consoleInput.forcefulTerminate();
//                 break;
//             } else {
//                 System.out.println("input = " + inputs);
//             }
//         }
//     }
// }
}