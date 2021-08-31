/*
 * Copyright 2012-2021 the original author or authors.
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

package org.nexial.core.plugins;

import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;

import javax.validation.constraints.NotNull;
import java.lang.reflect.InvocationTargetException;

/**
 * base definition of every Nexial plugin.  This interface deinfes the basic lifecycle method of a plugin.
 */
public interface NexialCommand {

    /** called by Nexial when plugin is initialized to provide context */
    void init(@NotNull ExecutionContext context);

    ExecutionContext getContext();

    /** invoked by Nexial when shutting down; no guarantee of shutdown sequence */
    void destroy();

    String getTarget();

    /**
     * a "profile" allows multiple instances of the same command to reside in context so that they can be swapped
     * during execution to serve specific target.
     */
    String getProfile();

    void setProfile(String profile);

    boolean isValidCommand(String command, String... params);

    StepResult execute(String command, String... params) throws InvocationTargetException, IllegalAccessException;
}