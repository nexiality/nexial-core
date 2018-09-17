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

package org.nexial.core.plugins.base;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.JRegexUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.excel.ext.CellTextReader;
import org.nexial.core.model.CommandRepeater;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.model.TestStep;
import org.nexial.core.plugins.CanLogExternally;
import org.nexial.core.plugins.NexialCommand;
import org.nexial.core.tools.CommandDiscovery;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.OutputFileUtils;
import org.nexial.core.variable.Syspath;

import static java.io.File.separator;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.System.lineSeparator;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.NULL;
import static org.nexial.core.excel.ExcelConfig.MSG_PASS;
import static org.nexial.core.excel.ext.CipherHelper.CRYPT_IND;
import static org.nexial.core.plugins.base.IncrementStrategy.ALPHANUM;
import static org.nexial.core.utils.CheckUtils.*;
import static org.nexial.core.utils.OutputFileUtils.CASE_INSENSIVE_SORT;

public class BaseCommand implements NexialCommand {
    public static final List<String> PARAM_AUTO_FILL_COMMANDS = Arrays.asList("desktop.typeTextBox",
                                                                              "desktop.typeAppendTextBox",
                                                                              "desktop.typeAppendTextArea",
                                                                              "desktop.typeTextArea",
                                                                              "desktop.sendKeysToTextBox");
    // "self-derived" means that the command will figure out the appropriate param values for display
    public static final List<String> PARAM_DERIVED_COMMANDS = Collections.singletonList("step.observe");

    protected static final IncrementStrategy STRATEGY_DEFAULT = ALPHANUM;

    protected transient Map<String, Method> commandMethods = new HashMap<>();
    protected transient ExecutionContext context;
    protected long pauseMs;
    protected transient ContextScreenRecorder screenRecorder;
    protected Syspath syspath = new Syspath();

    public BaseCommand() {
        collectCommandMethods();
    }

    @Override
    public void init(ExecutionContext context) {
        this.context = context;
        pauseMs = context.getDelayBetweenStep();
    }

    @Override
    public ExecutionContext getContext() { return context; }

    @Override
    public void destroy() {}

    @Override
    public String getTarget() { return "base"; }

    @Override
    public boolean isValidCommand(String command, String... params) {
        return getCommandMethod(command, params) != null;
    }

    @Override
    public StepResult execute(String command, String... params)
        throws InvocationTargetException, IllegalAccessException {

        Method m = getCommandMethod(command, params);
        if (m == null) {
            return StepResult.fail("Unknown/unsupported command " + getTarget() + "." + command +
                                   " OR mismatched parameters");
        }

        // resolve more values, but not for logging
        Object[] values = resolveParamValues(m, params);

        if (context.isVerbose() && (this instanceof CanLogExternally)) {
            String displayValues = "";
            for (Object value : values) {
                displayValues += (value == null ? NULL : CellTextReader.readValue(value.toString())) + ",";
            }
            displayValues = StringUtils.removeEnd(displayValues, ",");
            ((CanLogExternally) this).logExternally(context.getCurrentTestStep(), command + " (" + displayValues + ")");
        }

        StepResult result = (StepResult) m.invoke(this, values);
        String methodName = StringUtils.substringBefore(StringUtils.substringBefore(command, "("), ".");
        if (!PARAM_DERIVED_COMMANDS.contains(getTarget() + "." + methodName)) { result.setParamValues(values); }
        return result;
    }

    public StepResult startRecording() {
        if (!ContextScreenRecorder.isRecordingEnabled(context)) {
            return StepResult.success("Screen recording is currently disabled.");
        }

        if (screenRecorder != null && screenRecorder.isVideoRunning()) {
            // can't support multiple simultaneous video recording
            StepResult result = stopRecording();
            if (result.failed()) { error("Unable to stop previous recording in progress: " + result.getMessage()); }
        }

        try {
            // Create a instance of ScreenRecorder with the required configurations
            if (screenRecorder == null) { screenRecorder = ContextScreenRecorder.newInstance(context); }
            screenRecorder.start(context.getCurrentTestStep());
            return StepResult.success("video recording started");
        } catch (Exception e) {
            e.printStackTrace();
            return StepResult.fail("Unable to start recording: " + e.getMessage());
        }
    }

    public StepResult stopRecording() {
        if (screenRecorder == null || !screenRecorder.isVideoRunning()) {
            return StepResult.success("video recording already stopped (or never ran)");
        }

        try {
            screenRecorder.setContext(context);
            screenRecorder.stop();
            screenRecorder = null;
            return StepResult.success("previous recording stopped");
        } catch (IOException e) {
            String error = "Unable to stop and/or save screen recording" +
                           (context.isOutputToCloud() ? ", possible due to cloud integration" : "") +
                           ": " + e.getMessage();
            log(error);
            return StepResult.fail(error);
        } catch (Throwable e) {
            return StepResult.fail("Unable to stop previous recording: " + e.getMessage());
        }
    }

    // todo: reconsider command name.  this is not intuitive
    public StepResult incrementChar(String var, String amount, String config) {
        requiresValidVariableName(var);
        int amountInt = toInt(amount, "amount");

        String current = StringUtils.defaultString(context.getStringData(var));
        requires(StringUtils.isNotBlank(current), "var " + var + " is not valid.", current);

        IncrementStrategy strategy = STRATEGY_DEFAULT;
        if (StringUtils.isNotBlank(config)) {
            try {
                strategy = IncrementStrategy.valueOf(config);
            } catch (IllegalArgumentException e) {
                // don't worry.. we'll just ue default strategy
            }
        }

        String newVal = strategy.increment(current, amountInt);
        context.setData(var, newVal);

        return StepResult.success("incremented ${" + var + "} by " + amountInt + " to " + newVal);
    }

    public StepResult save(String var, String value) {
        requiresValidVariableName(var);
        context.setData(var, value);
        return StepResult.success("stored '" + CellTextReader.readValue(value) + "' as ${" + var + "}");
    }

    /**
     * clear data variables by name
     */
    public StepResult clear(String vars) {
        requiresNotBlank(vars, "invalid variable(s)", vars);

        List<String> ignoredVars = new ArrayList<>();
        List<String> removedVars = new ArrayList<>();

        TextUtils.toList(vars, context.getTextDelim(), true).forEach(var -> {
            if (context.isReadOnlyData(var)) {
                ignoredVars.add(var);
            } else if (StringUtils.isNotEmpty(context.removeData(var))) {
                removedVars.add(var);
            }
        });

        StringBuilder message = new StringBuilder();
        if (CollectionUtils.isNotEmpty(ignoredVars)) {
            message.append("The following data variable(s) are READ ONLY and ignored: ")
                   .append(TextUtils.toString(ignoredVars, ",")).append(" ");
        }
        if (CollectionUtils.isNotEmpty(removedVars)) {
            message.append("The following data variable(s) are removed from execution: ")
                   .append(TextUtils.toString(removedVars, ",")).append(" ");
        }
        if (CollectionUtils.isEmpty(ignoredVars) && CollectionUtils.isEmpty(removedVars)) {
            message.append("None of the specified variables are removed since they either are READ-ONLY or not exist");
        }

        return StepResult.success(message.toString());
    }

    public StepResult substringAfter(String text, String delim, String saveVar) {
        return saveSubstring(text, delim, null, saveVar);
    }

    public StepResult substringBefore(String text, String delim, String saveVar) {
        return saveSubstring(text, null, delim, saveVar);
    }

    public StepResult substringBetween(String text, String start, String end, String saveVar) {
        return saveSubstring(text, start, end, saveVar);
    }

    public StepResult split(String text, String delim, String saveVar) {
        requires(StringUtils.isNotEmpty(text), "invalid source", text);
        requiresValidVariableName(saveVar);

        if (StringUtils.isEmpty(delim) || context.isNullValue(delim)) { delim = context.getTextDelim(); }

        String targetText = context.hasData(text) ? context.getStringData(text) : text;

        List<String> array = Arrays.asList(StringUtils.splitByWholeSeparatorPreserveAllTokens(targetText, delim));
        if (context.hasData(saveVar)) { log("overwrite variable named as " + saveVar); }
        context.setData(saveVar, array);

        return StepResult.success("stored transformed array as ${" + saveVar + "}");
    }

    public StepResult appendText(String var, String appendWith) {
        requiresValidVariableName(var);
        String newValue = StringUtils.defaultString(context.getStringData(var)) + appendWith;
        context.setData(var, context.isNullValue(newValue) ? null : newValue);
        return StepResult.success("appended '" + appendWith + "' to ${" + var + "}");
    }

    public StepResult prependText(String var, String prependWith) {
        requiresValidVariableName(var);
        context.setData(var, prependWith + StringUtils.defaultString(context.getStringData(var)));
        return StepResult.success("prepend '" + prependWith + " to ${" + var + "}");
    }

    public StepResult saveCount(String text, String regex, String saveVar) {
        requiresValidVariableName(saveVar);
        List<String> groups = findTextMatches(text, regex);
        int count = CollectionUtils.size(groups);
        context.setData(saveVar, count);
        return StepResult.success("match count (" + count + ") stored to variable ${" + saveVar + "}");
    }

    public StepResult saveMatches(String text, String regex, String saveVar) {
        requiresValidVariableName(saveVar);
        List<String> groups = findTextMatches(text, regex);
        if (CollectionUtils.isEmpty(groups)) {
            context.removeData(saveVar);
            return StepResult.success("No matches found on text '" + text + "'");
        } else {
            context.setData(saveVar, groups);
            return StepResult.success("matches stored to variable ${" + saveVar + "}");
        }
    }

    public StepResult saveReplace(String text, String regex, String replace, String saveVar) {
        requiresValidVariableName(saveVar);
        if (replace == null || context.isNullValue(replace)) { replace = ""; }

        // run regex routine
        List<String> groups = findTextMatches(text, regex);
        if (CollectionUtils.isEmpty(groups)) {
            context.removeData(saveVar);
            return StepResult.success("No matches found on text '" + text + "'");
        } else {
            context.setData(saveVar, JRegexUtils.replace(text, regex, replace));
            return StepResult.success("matches replaced and stored to variable " + saveVar);
        }
    }

    public StepResult assertCount(String text, String regex, String expects) {
        requiresPositiveNumber(expects, "invalid numeric value as 'expects'", expects);
        return toInt(expects, "expects") == CollectionUtils.size(findTextMatches(text, regex)) ?
               StepResult.success("expected number of matches found") :
               StepResult.fail("expected number of matches was NOT found");
    }

    public StepResult assertTextOrder(String var, String descending) {
        requiresValidVariableName(var);

        Object obj = context.getObjectData(var);
        if (obj == null) { return StepResult.fail("no value stored as ${" + var + "}"); }

        List<String> actual = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        if (obj instanceof List || obj instanceof String) {
            List list = obj instanceof String ?
                        TextUtils.toList((String) obj, context.getTextDelim(), false) : (List) obj;
            list.stream().filter(o -> o != null).forEach(o -> {
                String string = o.toString();
                actual.add(string);
                expected.add(string);
            });
        } else if (obj.getClass().isArray()) {
            int length = ArrayUtils.getLength(obj);
            for (int i = 0; i < length; i++) {
                Object o = Array.get(obj, i);
                if (o != null) {
                    String string = o.toString();
                    actual.add(string);
                    expected.add(string);
                }
            }
        } else {
            return StepResult.fail("order cannot be assert for ${" + var + "} since its value type is " +
                                   obj.getClass().getSimpleName() + "; EXPECTS array type");
        }

        if (CollectionUtils.isEmpty(expected) && CollectionUtils.isEmpty(actual)) {
            return StepResult.fail("No data for comparison, hence no order can be asserted");
        }

        // now make 'expected' organized as expected - but applying case insensitive sort
        expected.sort(CASE_INSENSIVE_SORT);

        if (BooleanUtils.toBoolean(descending)) { Collections.reverse(expected); }

        // string comparison to determine if both the actual (displayed) list and sorted list is the same
        return assertEqual(expected.toString(), actual.toString());
    }

    public StepResult assertEmpty(String text) {
        return StringUtils.isEmpty(text) ?
               StepResult.success() : StepResult.fail("EXPECTS empty but found '" + text + "'");
    }

    public StepResult assertNotEmpty(String text) {
        return StringUtils.isNotEmpty(text) ?
               StepResult.success() : StepResult.fail("EXPECTS non-empty data found empty data instead.");
    }

    public StepResult assertEqual(String expected, String actual) {
        String nullValue = context.getNullValueToken();

        assertEquals(StringUtils.equals(expected, nullValue) ? null : expected,
                     StringUtils.equals(actual, nullValue) ? null : actual);

        return StepResult.success("validated " + StringUtils.defaultString(expected, nullValue) + " = " +
                                  StringUtils.defaultString(actual, nullValue));
    }

    public StepResult assertNotEqual(String expected, String actual) {
        String nullValue = context.getNullValueToken();

        assertNotEquals(StringUtils.equals(expected, nullValue) ? null : expected,
                        StringUtils.equals(actual, nullValue) ? null : actual);

        return StepResult.success("validated " + StringUtils.defaultString(expected, nullValue) + " not equals to " +
                                  StringUtils.defaultString(actual, nullValue));
    }

    public StepResult assertContains(String text, String substring) {
        boolean contains = lenientContains(text, substring, false);
        if (contains) {
            return StepResult.success("validated text '" + text + "' contains '" + substring + "'");
        } else {
            return StepResult.fail("Expects \"" + substring + "\" be contained in \"" + text + "\"");
        }
    }

    public StepResult assertNotContains(String text, String substring) {
        if (context.isLenientStringCompare()) {
            String[] searchFor = {"\r", "\n"};
            String[] replaceWith = {" ", " "};
            text = StringUtils.replaceEach(text, searchFor, replaceWith);
            substring = StringUtils.replaceEach(substring, searchFor, replaceWith);
        }

        if (!StringUtils.contains(text, substring)) {
            return StepResult.success("validated text '" + text + "' does NOT contains '" + substring + "'");
        } else {
            return StepResult.fail("Expects \"" + substring + "\" be NOT to be found in \"" + text + "\"");
        }
    }

    public StepResult assertStartsWith(String text, String prefix) {
        boolean valid = lenientContains(text, prefix, true);
        if (valid) {
            return StepResult.success("'" + text + "' starts with '" + prefix + "' as EXPECTED");
        } else {
            return StepResult.fail("EXPECTS '" + text + "' to start with '" + prefix + "'");
        }
    }

    public StepResult assertEndsWith(String text, String suffix) {
        boolean contains = StringUtils.endsWith(text, suffix);
        // not so fast.. could be one of those quirky IE issues..
        if (!contains && context.isLenientStringCompare()) {
            String lenientSuffix = StringUtils.replaceEach(StringUtils.trim(suffix),
                                                           new String[]{"\r", "\n"},
                                                           new String[]{" ", " "});
            String lenientText = StringUtils.replaceEach(StringUtils.trim(text),
                                                         new String[]{"\r", "\n"},
                                                         new String[]{" ", " "});
            contains = StringUtils.endsWith(lenientText, lenientSuffix);
        }

        if (contains) {
            return StepResult.success("'" + text + "' ends with '" + suffix + "' as EXPECTED");
        } else {
            return StepResult.fail("EXPECTS '" + text + "' to end with '" + suffix + "'");
        }
    }

    public StepResult assertArrayEqual(String array1, String array2, String exactOrder) {
        requires(StringUtils.isNotEmpty(array1), "first array is empty", array1);
        requires(StringUtils.isNotEmpty(array2), "second array is empty", array2);
        requires(StringUtils.isNotEmpty(exactOrder), "invalid value for exactOrder", exactOrder);

        String nullValue = context.getNullValueToken();

        // null corner case
        if (StringUtils.equals(array1, nullValue)) {
            if (StringUtils.equals(array2, nullValue)) {
                return StepResult.success("validated " + StringUtils.defaultString(array1, nullValue) +
                                          "=" + StringUtils.defaultString(array2, nullValue));
            }
        }

        String delim = context.getTextDelim();
        List<String> expectedList = TextUtils.toList(array1, delim, false);
        if (CollectionUtils.isEmpty(expectedList)) { CheckUtils.fail("EXPECTED array cannot be parsed: " + array1); }

        List<String> actualList = TextUtils.toList(array2, delim, false);
        if (CollectionUtils.isEmpty(actualList)) { CheckUtils.fail("ACTUAL array cannot be parsed: " + array2); }

        if (!BooleanUtils.toBoolean(exactOrder)) {
            Collections.sort(expectedList);
            Collections.sort(actualList);
        }

        assertEquals(expectedList.toString(), actualList.toString());
        return StepResult.success("validated " + array1 + "=" + array2 + " as EXPECTED");
    }

    public StepResult assertVarPresent(String var) {
        requiresValidVariableName(var);
        boolean found = context.hasData(var);
        return new StepResult(found, "Variable '" + var + "' " + (found ? "" : "DOES NOT") + " exist", null);
    }

    public StepResult assertVarNotPresent(String var) {
        requiresValidVariableName(var);
        boolean found = context.hasData(var);
        return new StepResult(!found, "Variable '" + var + "' " + (found ? "INDEED" : "does not") + " exist", null);
    }

    public StepResult failImmediate(String text) {
        context.setFailImmediate(true);
        return StepResult.fail(text);
    }

    public StepResult verbose(String text) {
        if (text == null) { text = context.getNullValueToken(); }
        if (StringUtils.contains(text, CRYPT_IND)) {
            // we should allow to print this.. security violation
            return StepResult.fail("crypto found; CANNOT proceed to complete this command");
        }

        log(text);
        // context.getCurrentTestStep().addNestedMessage(text);
        return StepResult.success(text);
    }

    public StepResult waitFor(String waitMs) {
        requires(NumberUtils.isDigits(waitMs), "invalid waitMs", waitMs);
        waitFor(NumberUtils.toInt(waitMs));
        return StepResult.success();
    }

    // todo: repeatForList(String varList, String maxWaitMs)

    public StepResult repeatUntil(String steps, String maxWaitMs) {
        requiresPositiveNumber(steps, "Invalid step count", steps);
        int stepCount = NumberUtils.toInt(steps);
        requires(stepCount > 0, "At least 1 step is required", steps);

        long maxWait = -1;
        if (StringUtils.isNotBlank(maxWaitMs) && !StringUtils.equals(StringUtils.trim(maxWaitMs), "-1")) {
            requiresPositiveNumber(maxWaitMs, "maxWaitMs must be a positive number", maxWait);
            maxWait = NumberUtils.toLong(maxWaitMs);
            requires(maxWait > 1000, "mininium maxWaitMs is 1 second", maxWaitMs);
        }

        TestStep currentTestStep = context.getCurrentTestStep();
        CommandRepeater commandRepeater = currentTestStep.getCommandRepeater();
        if (commandRepeater == null) {
            return StepResult.fail("Unable to gather the appropriate commands to perform repeat-until execution");
        }

        return commandRepeater.start();
    }

    /**
     * invoke a set of test steps stored in {@code file}, referenced by {@code name}.  {@code file} must be
     * fully qualified, whilst Nexial function may be used (e.g. {@code $(syspath)}).
     *
     * @param file the fullpath of the macro library.
     * @param name the name of the macro to invoke
     * @return pass/fail based on the validity of the referenced macro/file.  If macro {@code name} or library
     * ({@code file}) is not found, a failure is returned with fail-immediate in effect.
     */
    public StepResult macro(String file, String sheet, String name) {
        return failImmediate("Runtime error: Macro reference (" + file + "," + sheet + "," + name + ") not expanded");
    }

    /**
     * identify a set of test steps considered as section which includes number of test steps {@code steps}
     *
     * @param steps the number test steps to be included in the section
     * @return pass
     */
    public StepResult section(String steps) {
        requiresPositiveNumber(steps, "Invalid step count", steps);
        int stepCount = NumberUtils.toInt(steps);
        requires(stepCount > 0, "At least 1 step is required", steps);
        return StepResult.success();
    }

    /** Like JUnit's Assert.assertEquals, but handles "regexp:" strings like HTML Selenese */
    public void assertEquals(String expected, String actual) {
        String expectedDisplay = expected == null ? null : "\"" + expected + "\"";
        String actualDisplay = actual == null ? null : "\"" + actual + "\"";
        assertTrue("Expected=" + expectedDisplay + ", Actual=" + actualDisplay, assertEqualsInternal(expected, actual));
    }

    public void waitFor(int waitMs) {
        try { Thread.sleep(waitMs); } catch (InterruptedException e) { log("sleep - " + e.getMessage());}
    }

    public int toPositiveInt(String something, String label) {
        int integer = toInt(something, label);
        requires(integer >= 0, "Invalid " + label + "; EXPECTS a number greater than 0", something);
        return integer;
    }

    public long toPositiveLong(String something, String label) {
        long integer = toLong(something, label);
        requires(integer >= 0, "Invalid " + label + "; EXPECTS a number greater than 0", something);
        return integer;
    }

    public long toLong(String something, String label) {
        double number = toDouble(something, label);
        long whole = (long) number;
        if (whole != number) {
            ConsoleUtils.log("convert " + label + " " + something + " to " + whole + "; possible loss of precision");
        }
        return whole;
    }

    /**
     * ensuring we can convert "something" to an integer, even at the expense of precision loss.
     */
    public int toInt(String something, String label) {
        double number = toDouble(something, label);
        int integer = (int) number;
        if (integer != number) {
            ConsoleUtils.log("convert " + label + " " + something + " to " + integer + "; possible loss of precision");
        }
        return integer;
    }

    public double toPositiveDouble(String something, String label) {
        double number = toDouble(something, label);
        requires(number >= 0, "Invalid " + label + "; EXPECTS a number greater than 0", something);
        return number;
    }

    public double toDouble(String something, String label) {
        requires(StringUtils.isNotBlank(something), "invalid " + label, something);

        // check for starting-with-equal (stoopid excel!)
        if (StringUtils.startsWith(something, "=")) { something = something.substring(1); }

        // check for double quotes
        if (StringUtils.startsWith(something, "\"") && StringUtils.endsWith(something, "\"")) {
            something = StringUtils.substringBeforeLast(StringUtils.substringAfter(something, "\""), "\"");
        }

        boolean isNegative = StringUtils.startsWith(something, "-");
        something = StringUtils.removeStart(something, "-");
        while (StringUtils.startsWith(something, "0") && StringUtils.length(something) > 1) {
            something = StringUtils.removeStart(something, "0");
        }

        requires(NumberUtils.isCreatable(something), "invalid " + label, something);
        return NumberUtils.toDouble((isNegative ? "-" : "") + something);
    }

    /**
     * Compares two strings, but handles "regexp:" strings like HTML Selenese
     *
     * @return true if actual matches the expectedPattern, or false otherwise
     */
    protected static boolean seleniumEquals(String expectedPattern, String actual) {
        if (expectedPattern == null || actual == null) { return expectedPattern == null && actual == null; }

        if (actual.startsWith("regexp:") || actual.startsWith("regex:")
            || actual.startsWith("regexpi:") || actual.startsWith("regexi:")) {
            // swap 'em
            String tmp = actual;
            actual = expectedPattern;
            expectedPattern = tmp;
        }

        Boolean b = handleRegex("regexp:", expectedPattern, actual, 0);
        if (b != null) { return b; }

        b = handleRegex("regex:", expectedPattern, actual, 0);
        if (b != null) { return b; }

        b = handleRegex("regexpi:", expectedPattern, actual, CASE_INSENSITIVE);
        if (b != null) { return b; }

        b = handleRegex("regexi:", expectedPattern, actual, CASE_INSENSITIVE);
        if (b != null) { return b; }

        if (expectedPattern.startsWith("exact:")) {
            String expectedExact = expectedPattern.replaceFirst("exact:", "");
            if (!expectedExact.equals(actual)) {
                ConsoleUtils.log("expected " + actual + " to match " + expectedPattern);
                return false;
            }
            return true;
        }

        String expectedGlob = expectedPattern.replaceFirst("glob:", "");
        expectedGlob = expectedGlob.replaceAll("([\\]\\[\\\\{\\}$\\(\\)\\|\\^\\+.])", "\\\\$1");

        expectedGlob = expectedGlob.replaceAll("\\*", ".*");
        expectedGlob = expectedGlob.replaceAll("\\?", ".");
        if (!Pattern.compile(expectedGlob, Pattern.DOTALL).matcher(actual).matches()) {
            ConsoleUtils.log("expected \"" + actual + "\" to match glob \"" + expectedPattern
                             + "\" (had transformed the glob into regexp \"" + expectedGlob + "\"");
            return false;
        }
        return true;
    }

    /**
     * Compares two objects, but handles "regexp:" strings like HTML Selenese
     *
     * @return true if actual matches the expectedPattern, or false otherwise
     * @see #seleniumEquals(String, String)
     */
    protected static boolean seleniumEquals(Object expected, Object actual) {
        if (expected == null) { return actual == null; }
        if (expected instanceof String && actual instanceof String) {
            return seleniumEquals((String) expected, (String) actual);
        }
        return expected.equals(actual);
    }

    protected static String stringArrayToString(String[] sa) {
        StringBuilder sb = new StringBuilder("{");
        for (String aSa : sa) { sb.append(" ").append("\"").append(aSa).append("\""); }
        sb.append(" }");
        return sb.toString();
    }

    protected static String join(String[] sa, char c) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < sa.length; j++) {
            sb.append(sa[j]);
            if (j < sa.length - 1) { sb.append(c); }
        }
        return sb.toString();
    }

    protected boolean lenientContains(String text, String prefix, boolean startsWith) {
        boolean valid = startsWith ? StringUtils.startsWith(text, prefix) : StringUtils.contains(text, prefix);
        // not so fast.. could be one of those quarky IE issues..
        if (!valid && context.isLenientStringCompare()) {
            String[] searchFor = {"\r", "\n"};
            String[] replaceWith = {" ", " "};
            String lenientPrefix = StringUtils.replaceEach(StringUtils.trim(prefix), searchFor, replaceWith);
            String lenientText = StringUtils.replaceEach(StringUtils.trim(text), searchFor, replaceWith);
            valid = startsWith ?
                    StringUtils.startsWith(lenientText, lenientPrefix) :
                    StringUtils.contains(lenientText, lenientPrefix);
        }

        return valid;
    }

    /** Like JUnit's Assert.assertEquals, but knows how to compare string arrays */
    protected void assertEquals(Object expected, Object actual) {
        if (expected == null) {
            assertTrue("Expected=null, Actual=\"" + actual + "\"", actual == null);
        } else if (expected instanceof String && actual instanceof String) {
            assertEquals((String) expected, (String) actual);
        } else if (expected instanceof String && actual instanceof String[]) {
            assertEquals((String) expected, (String[]) actual);
        } else if (expected instanceof String && actual instanceof Number) {
            assertEquals((String) expected, actual.toString());
        } else if (expected instanceof Number && actual instanceof String) {
            assertEquals(expected.toString(), (String) actual);
        } else if (expected instanceof String[] && actual instanceof String[]) {
            assertEquals((String[]) expected, (String[]) actual);
        } else {
            assertTrue("Expected=\"" + expected + "\", Actual=\"" + actual + "\"", expected.equals(actual));
        }
    }

    /**
     * Like JUnit's Assert.assertEquals, but joins the string array with commas, and handles "regexp:"
     * strings like HTML Selenese
     */
    protected void assertEquals(String expected, String[] actual) { assertEquals(expected, join(actual, ',')); }

    /** Asserts that two string arrays have identical string contents */
    protected void assertEquals(String[] expected, String[] actual) {
        String comparisonDumpIfNotEqual = verifyEqualsAndReturnComparisonDumpIfNot(expected, actual);
        if (comparisonDumpIfNotEqual != null) {
            error(MSG_FAIL + comparisonDumpIfNotEqual);
            throw new AssertionError(comparisonDumpIfNotEqual);
        }
    }

    protected void assertTrue(String message, boolean condition) {
        if (!condition) {
            CheckUtils.fail(message);
        } else {
            log(MSG_PASS + message);
        }
    }

    /** Asserts that two objects are not the same (compares using .equals()) */
    protected void assertNotEquals(Object expected, Object actual) {
        String expectedDisplay = expected == null ? null : "\"" + expected + "\"";
        String actualDisplay = actual == null ? null : "\"" + actual + "\"";

        if (expected == null) {
            // both should be null
            assertFalse("Expected=null, Actual=" + actualDisplay, actual == null);
            return;
        }

        if (expected.equals(actual)) { CheckUtils.fail("Expected=" + expectedDisplay + ", Actual=" + actualDisplay); }
    }

    protected void assertTrue(boolean condition) { assertTrue(null, condition); }

    protected void assertFalse(String message, boolean condition) { assertTrue(message, !condition); }

    protected void assertFalse(boolean condition) { assertTrue(null, !condition); }

    /** Asserts that two booleans are not the same */
    protected void assertNotEquals(boolean expected, boolean actual) {
        assertNotEquals(Boolean.valueOf(expected), Boolean.valueOf(actual));
    }

    protected void verifyFalse(String description, boolean b) {
        String result = !b ? MSG_PASS : MSG_FAIL;
        log(result + (StringUtils.isNotBlank(description) ? description : ""));
        //if (b) { verificationErrors.append(description); }
        verifyFalse(b);
    }

    protected void verifyFalse(boolean b) {
        try {
            assertFalse(b);
        } catch (Error e) {
            error(throwableToString(e));
        }
    }

    /** Like assertTrue, but fails at the end of the test (during tearDown) */
    protected void verifyTrue(boolean b) {
        try {
            assertTrue(b);
        } catch (Error e) {
            error(throwableToString(e));
        }
    }

    protected void verifyTrue(String description, boolean b) {
        String result = b ? MSG_PASS : MSG_FAIL;
        log(result + (StringUtils.isNotBlank(description) ? description : ""));
        verifyTrue(b);
    }

    protected Method getCommandMethod(String command, String... params) {
        if (StringUtils.isBlank(command)) {
            CheckUtils.fail("Unknown command " + command);
            return null;
        }

        String methodName = StringUtils.substringBefore(StringUtils.substringBefore(command, "("), ".");

        if (!commandMethods.containsKey(methodName)) {
            CheckUtils.fail("Unknown command " + command);
            return null;
        }

        Method m = commandMethods.get(methodName);
        int actualParamCount = ArrayUtils.getLength(params);
        int expectedParamCount = m.getParameterCount();

        if (actualParamCount == expectedParamCount) { return m; }

        // fill in null for missing params later
        if (PARAM_AUTO_FILL_COMMANDS.contains(getTarget() + "." + methodName)) { return m; }

        CheckUtils.fail("MISMATCHED parameters - " + getTarget() + "." + methodName +
                        " EXPECTS " + expectedParamCount + " but found " + actualParamCount);
        return null;
    }

    protected void shutdown() throws IOException {
        if (screenRecorder != null) {
            screenRecorder.stop();
            screenRecorder = null;
        }
    }

    protected List<String> findMatches(String var, String regex) {
        return findTextMatches(getContextValueAsString(var), regex);
    }

    protected List<String> findTextMatches(String text, String regex) {
        requiresNotBlank(text, "invalid text", text);
        requires(StringUtils.isNotBlank(regex), "invalid regular expression", regex);
        return RegexUtils.eagerCollectGroups(text, regex, true, true);
    }

    protected String getContextValueAsString(String var) {
        requiresValidVariableName(var);

        // convert stored variable into text
        Object value = context.getObjectData(var);
        if (value == null) { return null; }

        if (value.getClass().isArray()) {
            int length = ArrayUtils.getLength(value);
            String text = "";
            for (int i = 0; i < length; i++) {
                text += Objects.toString(Array.get(value, i));
                if (i < length - 1) { text += lineSeparator(); }
            }
            return text;
        }

        if (value instanceof List) { return TextUtils.toString((List) value, lineSeparator()); }
        if (value instanceof Map) { return TextUtils.toString((Map) value, lineSeparator(), "="); }
        return Objects.toString(value);
    }

    protected StepResult saveSubstring(List<String> textArray, String delimStart, String delimEnd, String var) {
        requires(StringUtils.isNotEmpty(delimStart) || StringUtils.isNotEmpty(delimEnd), "invalid delimiter");
        requiresValidVariableName(var);

        boolean before = StringUtils.isNotEmpty(delimEnd) && StringUtils.isEmpty(delimStart);
        boolean between = StringUtils.isNotEmpty(delimEnd) && StringUtils.isNotEmpty(delimStart);

        List<String> substrings =
            textArray.stream()
                     .map(text -> between ?
                                  StringUtils.substringBetween(text, delimStart, delimEnd) :
                                  before ? StringUtils.substringBefore(text, delimEnd) :
                                  StringUtils.substringAfter(text, delimStart)).collect(Collectors.toList());

        context.setData(var, substrings);
        return StepResult.success(textArray.size() + " substring(s) stored to ${" + var + "}");
    }

    protected StepResult saveSubstring(String text, String delimStart, String delimEnd, String var) {
        requiresNotBlank(text, "invalid text", text);
        requires(StringUtils.isNotEmpty(delimStart) || StringUtils.isNotEmpty(delimEnd), "invalid delimiter");
        requiresValidVariableName(var);

        boolean before = StringUtils.isNotEmpty(delimEnd) && StringUtils.isEmpty(delimStart);
        boolean between = StringUtils.isNotEmpty(delimEnd) && StringUtils.isNotEmpty(delimStart);

        String substr = between ? StringUtils.substringBetween(text, delimStart, delimEnd) :
                        before ? StringUtils.substringBefore(text, delimEnd) :
                        StringUtils.substringAfter(text, delimStart);
        context.setData(var, substr);
        return StepResult.success("substring '" + substr + "' stored to ${" + var + "}");
    }

    protected void collectCommandMethods() {
        CommandDiscovery discovery = CommandDiscovery.getInstance();

        Method[] allMethods = this.getClass().getDeclaredMethods();
        Arrays.stream(allMethods).forEach(m -> {
            if (Modifier.isPublic(m.getModifiers()) &&
                !Modifier.isStatic(m.getModifiers()) &&
                StepResult.class.isAssignableFrom(m.getReturnType()) &&
                !StringUtils.equals(m.getName(), "execute")) {

                commandMethods.put(m.getName(), m);

                if (CommandDiscovery.isInDiscoveryMode()) {
                    String command = m.getName() + "(";

                    Parameter[] parameters = m.getParameters();
                    for (Parameter param : parameters) {
                        // workaround for kotlin (var is reserved in kotlin)
                        String paramName = StringUtils.equals(param.getName(), "Var") ? "var" : param.getName();
                        command += paramName + ",";
                    }

                    discovery.addCommand(getTarget(), StringUtils.removeEnd(command, ",") + ")");
                }
            }
        });
    }

    protected static Boolean handleRegex(String prefix, String expectedPattern, String actual, int flags) {
        if (!expectedPattern.startsWith(prefix)) { return null; }

        String expectedRegEx = expectedPattern.replaceFirst(prefix, ".*") + ".*";
        Pattern p = Pattern.compile(expectedRegEx, flags);
        if (p.matcher(actual).matches()) { return TRUE; }

        ConsoleUtils.log("expected " + actual + " to match regexp " + expectedPattern);
        return FALSE;
    }

    protected static boolean assertEqualsInternal(String expected, String actual) {
        if (expected == null && actual == null) { return true; }

        if (NumberUtils.isCreatable(expected) && NumberUtils.isCreatable(actual)) {
            // both are number, then we should assert by double
            return seleniumEquals(NumberUtils.toDouble(expected), NumberUtils.toDouble(actual));
        }

        ExecutionContext context = ExecutionThread.get();

        boolean equals = seleniumEquals(expected, actual);
        if (!equals && ExecutionContext.getSystemThenContextBooleanData(OPT_EASY_STRING_COMPARE, context, false)) {
            // not so fast.. could be one of those quarky IE issues..
            String lenientExpected = TextUtils.toOneLine(expected, true);
            String lenientActual = TextUtils.toOneLine(actual, true);
            equals = StringUtils.equals(lenientExpected, lenientActual);
        }
        return equals;
    }

    protected static String verifyEqualsAndReturnComparisonDumpIfNot(String[] expected, String[] actual) {
        boolean misMatch = false;
        if (expected.length != actual.length) { misMatch = true; }
        for (int j = 0; j < expected.length; j++) {
            if (!seleniumEquals(expected[j], actual[j])) {
                misMatch = true;
                break;
            }
        }

        if (misMatch) {
            return "Expected\"" + stringArrayToString(expected) + "\", Actual=\"" + stringArrayToString(actual) + "\"";
        }

        return null;
    }

    protected static String throwableToString(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    protected void log(String message) {
        if (StringUtils.isBlank(message)) { return; }
        if (context != null && context.getLogger() != null) { context.getLogger().log(this, message); }
    }

    public void addLinkRef(String message, String label, String link) {
        if (StringUtils.isBlank(link)) { return; }

        if (context.isOutputToCloud()) {
                try {
                    link = context.getOtc().importMedia(new File(link));
                } catch (IOException e) {
                    log("Unable to save " + link + " to cloud storage due to " + e.getMessage());
                }
        }

        if (context != null && context.getLogger() != null) {
            TestStep testStep = context.getCurrentTestStep();
            if (testStep != null && testStep.getWorksheet() != null) {
                // test step undefined could mean that we are in interactive mode, or we are running unit testing
                context.setData(OPT_LAST_OUTCOME, link);
                testStep.addNestedScreenCapture(link, message, label);
            }
        }
    }

    // protected void addLinkToOutputFile(File outputFile, String label, String linkCaption) {
    //     if (outputFile == null) { return; }
    //
    //     String outFile = outputFile.getPath();
    //     if (context.isOutputToCloud()) {
    //         try {
    //             outFile = context.getOtc().importMedia(outputFile);
    //         } catch (IOException e) {
    //             log("Unable to save " + outFile + " to cloud storage due to " + e.getMessage());
    //         }
    //     }
    //
    //     addLinkRef(linkCaption, label, outFile);
    // }

    protected void error(String message) { error(message, null); }

    protected void error(String message, Throwable e) {
        if (StringUtils.isNotBlank(message)) { context.getLogger().error(this, message, e); }
    }

    /**
     * create a file with {@code output} as its text content and its name based on current step and {@code extension}.
     *
     * to improve readability and user experience, use {@code caption} to describe such file on the execution output.
     */
    protected void addOutputAsLink(String caption, String output, String extension) {
        String outFile = syspath.out("fullpath") + separator +
                         OutputFileUtils.generateOutputFilename(context.getCurrentTestStep(), extension);
        File outputFile = new File(outFile);

        try {
            FileUtils.writeStringToFile(outputFile, output, DEF_FILE_ENCODING);
            addLinkRef(caption, extension + " report", outFile);
        } catch (IOException e) {
            error("Unable to write log file to '" + outFile + "': " + e.getMessage(), e);
        }
    }

    protected Object[] resolveParamValues(Method m, String... params) {
        int numOfParamSpecified = ArrayUtils.getLength(params);
        int numOfParamExpected = ArrayUtils.getLength(m.getParameterTypes());

        Object[] args = new Object[numOfParamExpected];
        for (int i = 0; i < args.length; i++) {
            if (i >= numOfParamSpecified) {
                args[i] = "";
            } else {
                args[i] = context.replaceTokens(params[i]);
            }
        }

        return args;
    }
}
