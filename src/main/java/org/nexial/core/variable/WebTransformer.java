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

package org.nexial.core.variable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.CollectionUtil;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.web.Browser;
import org.nexial.core.plugins.web.WebCommand;
import org.nexial.core.plugins.web.WebDriverExceptionHelper;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.variable.WebDataType.Result;
import org.openqa.selenium.WebDriverException;

import static org.nexial.core.NexialConst.MSG_FAIL;
import static org.nexial.core.NexialConst.MSG_PASS;

public class WebTransformer<T extends WebDataType> extends Transformer<T> {
    private static final Map<String, Integer> FUNCTION_TO_PARAM_LIST = discoverFunctions(WebTransformer.class);
    private static final Map<String, Method> FUNCTIONS =
        toFunctionMap(FUNCTION_TO_PARAM_LIST, WebTransformer.class, WebDataType.class);
    private static final String TEMP_VAR = "!234$ssjf%&98!";
    private static final String LABEL_PREFIX = "text=";

    public WebDataType type(T data, String locator, String value) {
        if (data == null || data.getValue() == null) { return null; }

        WebCommand webCommand = resolveWebCommand();

        StepResult stepResult;
        try {
            stepResult = webCommand.type(locator, value);
        } catch (Exception e) {
            // add meaningful and precise error message to result
            stepResult = new StepResult(false, resolveErrorMessage(e), null);
        }
        return saveResult(data, "type(" + locator + "," + value + ")", stepResult);
    }

    public WebDataType typeKeys(T data, String locator, String value) {
        if (data == null || data.getValue() == null) { return null; }

        WebCommand webCommand = resolveWebCommand();

        StepResult stepResult;
        try {
            stepResult = webCommand.typeKeys(locator, value);
        } catch (Exception e) {
            stepResult = new StepResult(false, resolveErrorMessage(e), null);
        }
        return saveResult(data, "typeKeys(" + locator + "," + value + ")", stepResult);
    }

    /* This method supports both click by locator and click by label
     * Need to provide label with label prefix like`text=label`
     * */
    public WebDataType click(T data, String locator) {
        if (data == null || data.getValue() == null) { return null; }

        WebCommand webCommand = resolveWebCommand();

        StepResult stepResult;
        try {
            if (StringUtils.startsWith(locator, LABEL_PREFIX)) {
                stepResult = webCommand.clickByLabel(StringUtils.substringAfter(locator, LABEL_PREFIX));
            } else {
                stepResult = webCommand.click(locator);
            }
        } catch (Exception e) {
            stepResult = new StepResult(false, resolveErrorMessage(e), null);
        }
        return saveResult(data, "click(" + locator + ")", stepResult);
    }

    public WebDataType selectWindow(T data, String winId) {
        if (data == null || data.getValue() == null) { return null; }

        WebCommand webCommand = resolveWebCommand();

        StepResult stepResult;
        try {
            if (NumberUtils.isDigits(winId)) {
                stepResult = webCommand.selectWindowByIndex(winId);
                if (!stepResult.isSuccess()) {
                    stepResult = webCommand.selectWindow(winId);
                }
            } else {
                stepResult = webCommand.selectWindow(winId);
            }
        } catch (Exception e) {
            stepResult = new StepResult(false, resolveErrorMessage(e), null);
        }

        return saveResult(data, "selectWindow(" + winId + ")", stepResult);
    }

    public CsvDataType fetchAsCsv(T data, String... locators) throws TypeConversionException {
        if (data == null || data.getValue() == null) { return null; }

        WebCommand webCommand = resolveWebCommand();

        List<String> list = Arrays.asList(locators);
        ExecutionContext context = ExecutionThread.get();
        String delim = context.getTextDelim();

        StringBuilder output = new StringBuilder();
        output.append("locator,value");
        list.forEach(locator -> {
            String value = "";
            try {
                StepResult stepResult = webCommand.saveValue(TEMP_VAR, locator);
                if (stepResult.isSuccess()) {
                    value = context.getStringData(TEMP_VAR);
                } else {
                    ConsoleUtils.error("Unable to save value for locator '" + locator +
                                       "' due to " + stepResult.getMessage());
                }
            } catch (Exception e) {
                ConsoleUtils.error("Unable locate the element via locator '" + locator + "'");
            }
            output.append("\r\n");
            output.append(locator).append(delim).append(value);
            context.removeData(TEMP_VAR);
        });

        CsvDataType csv = new CsvDataType(output.toString());
        csv.setHeader(false);
        csv.setDelim(delim);
        csv.parse();

        Result res = new Result("fetchAsCsv(" + CollectionUtil.toString(list, delim) + ")", "PASS", null);
        store(data, true, res);

        return csv;
    }

    public WebDataType select(T data, String... array) {
        if (data == null || data.getValue() == null) { return null; }

        WebCommand webCommand = resolveWebCommand();

        ExecutionContext context = ExecutionThread.get();
        String delim = context.getTextDelim();

        StepResult stepResult;
        List<String> list = new ArrayList<>(Arrays.asList(array));
        String operation = "select(" + CollectionUtil.toString(list, delim) + ")";

        if (list.size() < 2) {
            stepResult = new StepResult(false, "locator/options missing", null);
            return saveResult(data, operation, stepResult);
        }

        String locator = list.remove(0);
        String options = CollectionUtil.toString(list, delim);

        try {
            if (list.size() == 1) {
                stepResult = webCommand.select(locator, options);
            } else {
                stepResult = webCommand.selectMulti(locator, options);
            }
        } catch (Exception e) {
            stepResult = new StepResult(false, resolveErrorMessage(e), null);
        }

        return saveResult(data, operation, stepResult);
    }

    public WebDataType deselect(T data, String... array) {
        if (data == null || data.getValue() == null) { return null; }

        WebCommand webCommand = resolveWebCommand();

        ExecutionContext context = ExecutionThread.get();
        String delim = context.getTextDelim();

        StepResult stepResult;
        List<String> list = new ArrayList<>(Arrays.asList(array));
        String operation = "deselect(" + CollectionUtil.toString(list, delim) + ")";

        if (list.size() < 2) {
            stepResult = new StepResult(false, "locator/options missing", null);
            return saveResult(data, operation, stepResult);
        }

        String locator = list.remove(0);
        String options = CollectionUtil.toString(list, delim);
        try {
            if (list.size() == 1) {
                stepResult = webCommand.deselect(locator, options);
            } else {
                stepResult = webCommand.deselectMulti(locator, options);
            }
        } catch (Exception e) {
            stepResult = new StepResult(false, resolveErrorMessage(e), null);
        }

        return saveResult(data, operation, stepResult);
    }

    public WebDataType wait(T data, String waitMs) {
        if (data == null || data.getValue() == null) { return null; }

        WebCommand webCommand = resolveWebCommand();

        StepResult stepResult = webCommand.wait(waitMs);
        return saveResult(data, "wait(" + waitMs + ")", stepResult);
    }

    public WebDataType waitFor(T data, String locator) {
        if (data == null || data.getValue() == null) { return null; }

        WebCommand webCommand = resolveWebCommand();
        ExecutionContext context = ExecutionThread.get();

        StepResult stepResult = webCommand.waitForElementPresent(locator, context.getPollWaitMs() + "");
        return saveResult(data, "waitFor(" + locator + ")", stepResult);
    }

    public TextDataType text(T data) { return super.text(data); }

    public TextDataType allPass(T data) throws TypeConversionException {
        if (data == null || data.getValue() == null) { return new TextDataType("false"); }
        return new TextDataType(data.isAllPass() + "");
    }

    @Override
    Map<String, Integer> listSupportedFunctions() { return FUNCTION_TO_PARAM_LIST; }

    @Override
    Map<String, Method> listSupportedMethods() { return FUNCTIONS; }

    private String resolveErrorMessage(Exception e) {
        return (e instanceof WebDriverException) ?
               WebDriverExceptionHelper.resolveErrorMessage((WebDriverException) e) : e.getMessage();
    }

    private WebCommand resolveWebCommand() {
        ExecutionContext context = ExecutionThread.get();
        WebCommand cmd = (WebCommand) context.findPlugin("web");
        if (cmd != null) {
            Browser browser = cmd.getBrowser();
            if (browser != null) {
                browser.ensureWebDriverReady();
                return cmd;
            }
        }
        return cmd;
    }

    private WebDataType saveResult(WebDataType data, String operation, StepResult stepResult) {
        boolean isPassed = stepResult.isSuccess();
        return store(data, isPassed, new Result(operation,
                                                StringUtils.trim(isPassed ? MSG_PASS : MSG_FAIL),
                                                isPassed ? null : stepResult.getMessage()));
    }

    private WebDataType store(WebDataType data, boolean isPassed, Result result) {
        if (data.isAllPass()) { data.setAllPass(isPassed); }
        data.addResults(result);
        data.setTextValue(data.toString());
        ExecutionThread.get().setData(data.getValue(), data);
        return data;
    }
}
