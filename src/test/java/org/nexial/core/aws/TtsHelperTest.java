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

package org.nexial.core.aws;

import org.junit.Assert;
import org.junit.Test;

import static org.nexial.core.aws.TtsHelper.SSML_PAUSE;
import static org.nexial.core.aws.TtsHelper.SSML_SHORT_PAUSE;

public class TtsHelperTest {

    @Test
    public void buildSsml_simple() throws Exception {
        TtsHelper target = new TtsHelper();

        Assert.assertEquals("<speak>Hello World!</speak>",
                            target.buildSsml("Hello World!"));

        Assert.assertEquals("<speak>Hello <emphasis>WORLD</emphasis>!</speak>",
                            target.buildSsml("Hello WORLD!"));

        Assert.assertEquals("<speak>Hello…" + SSML_PAUSE + " <emphasis>WORLD</emphasis>!</speak>",
                            target.buildSsml("Hello... WORLD!"));

        Assert.assertEquals("<speak>" +
                            "Hello…" + SSML_PAUSE + " <emphasis>WORLD</emphasis>. " +
                            "And Hi Jimmy" + SSML_SHORT_PAUSE +
                            "Johnny" + SSML_SHORT_PAUSE +
                            "Nobody" + SSML_SHORT_PAUSE +
                            "</speak>",
                            target.buildSsml("Hello... WORLD. And Hi [Jimmy][Johnny][Nobody]"));

        Assert.assertEquals("<speak>" +
                            "<emphasis>OH</emphasis> <emphasis>NO</emphasis>! That crap blew up on " +
                            "showcase" + SSML_SHORT_PAUSE +
                            "scenario_1" + SSML_SHORT_PAUSE +
                            "Row #14" + SSML_SHORT_PAUSE + "!" +
                            "</speak>",
                            target.buildSsml("OH NO! That crap blew up on [showcase][scenario_1][Row #14]!"));

        Assert.assertEquals("<speak>" +
                            "<emphasis>THE</emphasis> " +
                            "<emphasis>SKY</emphasis> " +
                            "<emphasis>IS</emphasis> " +
                            "<emphasis>FALLING</emphasis>!!! " +
                            "<emphasis>KILL</emphasis> " +
                            "<emphasis>THE</emphasis> " +
                            "<emphasis>SWITCH</emphasis>!!!" +
                            "</speak>",
                            target.buildSsml("THE SKY IS FALLING!!! KILL THE SWITCH!!!"));
    }
}