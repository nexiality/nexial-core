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

package org.nexial.core;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.sound.SoundMachine;

import static org.nexial.core.NexialConst.Data.*;

public class ExecutionEventListener {
    private static final String EVENT_SCRIPT_START = "onScriptStart()";
    private static final String EVENT_SCRIPT_COMPLETE = "onScriptComplete()";
    private static final String EVENT_ERROR = "onError()";
    private static final String EVENT_PAUSE = "onPause()";
    private static final Map<String, String> EVENT_CONFIGS = TextUtils.toMap(
        "=",
        EVENT_SCRIPT_START + "=" + SOUND_ON_START,
        EVENT_SCRIPT_COMPLETE + "=" + SOUND_ON_COMPLETE,
        EVENT_ERROR + "=" + SOUND_ON_ERROR,
        EVENT_PAUSE + "=" + SOUND_ON_PAUSE);

    private final ExecutionContext context;

    public ExecutionEventListener(ExecutionContext context) { this.context = context; }

    public void onScriptStart() {
        doSound(EVENT_SCRIPT_START);
    }

    public void onScriptComplete() {
        doSound(EVENT_SCRIPT_COMPLETE, true);
    }

    public void onError() {
        doSound(EVENT_ERROR, true);
    }

    public void onPause() {
        doSound(EVENT_PAUSE);
    }

    public void afterPause() {
        // nothing for now
    }

    private void doSound(String event) { doSound(event, false); }

    private void doSound(String event, boolean wait) {
        String sound = context.getStringData(EVENT_CONFIGS.get(event));
        if (StringUtils.isBlank(sound)) { return; }

        SoundMachine dj = context.getDj();
        if (dj == null) { return; }

        dj.playOrSpeak(context, sound, event, wait);
    }
}
