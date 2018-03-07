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

package org.nexial.core.plugins.step;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.utils.CheckUtils.requiresNotBlank;

public class StepCommand extends BaseCommand {

    @Override
    public String getTarget() { return "step"; }

    public StepResult perform(String instructions) {
        requiresNotBlank(instructions, "Invalid instruction(s)", instructions);
        ConsoleUtils.pauseForStep(context, instructions);
        return StepResult.success("Step(s) performed");
    }

    public StepResult validate(String prompt, String responses, String passResponses) {
        requiresNotBlank(prompt, "Invalid prompt(s)", prompt);

        String response = ConsoleUtils.pauseToValidate(context, prompt, responses);
        if (StringUtils.isBlank(response)) {
            if (StringUtils.isBlank(passResponses)) {
                return StepResult.success("Empty response accepted as PASS");
            } else {
                return StepResult.fail("Empty response NOT acceptable as PASS");
            }
        }

        String delim = context.getTextDelim();
        List<String> possiblePasses = new ArrayList<>();
        if (!StringUtils.contains(passResponses, delim)) {
            // default behavior: each character is one possible response
            Arrays.stream(ArrayUtils.toObject(response.toCharArray()))
                  .forEach(ch -> possiblePasses.add(ch.toString()));
        } else {
            possiblePasses.addAll(Arrays.asList(StringUtils.split(passResponses, delim)));
        }

        boolean pass = passResponses.contains(response);
        return new StepResult(pass, "Response '" + response + "' considered as " + (pass ? "PASS" : "FAILED"), null);
    }

    public StepResult observe(String prompt) {
        requiresNotBlank(prompt, "Invalid prompt(s)", prompt);

        String response = ConsoleUtils.pauseForInput(context, prompt);

        StepResult result = StepResult.success("Response received as '" + response + "'");
        result.setParamValues(new Object[]{prompt, response});
        return result;
    }
}
