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

package org.nexial.core.utils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.NexialConst.*;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.ExecutionDefinition;
import org.nexial.core.model.TestStep;
import org.nexial.core.plugins.web.Browser;
import org.nexial.core.variable.Syspath;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.*;

/**
 * dedicated as an utility to decorate Nexial excel files with additional runtime information.
 * <p/>
 * <b>General format rule:</b>
 * <pre>[TEST_CASE_ID]-[SHORT_DESCRIPTION].[START_DATE_TIME].[BROWSER].[SEQ_NAME]~[ITERATION].[EXTENSION]</pre>
 * <p/>
 * For example, <br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<code>TC1234-Validate Company Profile.20130402_220414.firefox_20_0_1.1~2.xlsx</code><br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<code>Check Profile.20130402_220502.safari_5_1_7.xlsx</code><br/>
 * <p/>
 * Not all the tokens are required.  And since the file name could be partially formatted as above at any given point
 * in time, this class determines the appropriate positioning to apply by examine the filename from the end (i.e.
 * from extension).
 */
public final class OutputFileUtils {
    public static final Comparator<String> CASE_INSENSIVE_SORT = (o1, o2) -> {
        if (o1 == null) {
            return o2 == null ? 0 : -1;
        } else {
            return o2 == null ? 1 : o1.toLowerCase().compareTo(o2.toLowerCase());
        }
    };
    private static final String NAME = OutputFileUtils.class.getSimpleName();
    private static final String DELIM = FILE_PART_SEP;
    private static final String DELIM_ITER = "~";
    private static final String[] UNSUPPORTED_FILE_CHARS =
        new String[]{"\\", "/", ":", "*", ">", "<", "\"", "?", "|", "#", "=", ";", DELIM};
    private static final String[] SUPPORTED_FILE_CHARS =
        new String[]{"_", "_", "_", "_", "_", "_", "_", "_", "_", "_", "_", "_", "_"};
    // web friendliness
    private static final String[] WEB_UNFRIENDLY_CHARS =
        new String[]{"\\", "/", ":", "*", ">", "<", "\"", "?", "|", "#", "=", ";", " ", "+", "&"};
    private static final String[] WEB_FRIENDLY_CHARS =
        new String[]{"_", "_", "_", "_", "_", "_", "_", "_", "_", "_", "_", "_", "_", "_", "_"};
    private static final String REGEX_START_DATE_TIME = "[\\d]{8}\\_[\\d]{6}";
    private static final String REGEX_PREFIX_AND_START_DATE_TIME = "(.+)\\.([\\d]{8}\\_[\\d]{6})";
    private static final String REGEX_PLAN_EXECUTION_OUTPUT_FILE =
        "(.+\\..+\\.[\\d]{3}),(.+\\.[\\d_]+\\.[\\d]{3}\\.xlsx)";

    static final int POS_TESTPLAN_FILE = 0;
    static final int POS_TESTPLAN = 1;
    static final int POS_SEQUENCE = 2;
    static final int POS_TESTPLAN_OFFSET = 3;
    static final int POS_FILENAME = 0;
    static final int POS_START_TS = 1;
    static final int POS_BROWSER = 2;
    static final int POS_STEP_N_ITER = 3;
    static final int POS_EXT = 4;

    private static class FilePart {
        String filename = "";
        String ext = "";
        String stepIter = "";
        String browser = "";
        String startTs = "";
        String prefix = "";
    }

    private OutputFileUtils() { }

    public static String generateScreenCaptureFilename(TestStep testStep) {
        return generateOutputFilename(testStep, SCREENSHOT_EXT);
    }

    public static String generateOutputFilename(TestStep testStep, String ext) {
        if (testStep == null) { return null; }

        ext = StringUtils.isBlank(ext) ? "" : StringUtils.prependIfMissing(StringUtils.trim(ext), ".");

        // make sure we don't have funky characters in the file name
        return OutputFileUtils.webFriendly(testStep.generateFilename(ext));
    }

    /** rename {@code excelFile} to add browser+version information */
    public static String addBrowser(String filename, Browser browser) {
        String browserLabel = browser == null ?
                              "NONE" : browser.getBrowserType().name() + "_" + browser.getBrowserVersion();
        return add(filename, browserLabel, POS_BROWSER);
    }

    public static String addIteration(String filename, String iteration) {
        String stepIter = get(filename, POS_STEP_N_ITER);

        String data = iteration;
        if (StringUtils.isNotBlank(stepIter)) {
            if (StringUtils.contains(stepIter, DELIM_ITER)) {
                data = StringUtils.substringAfter(stepIter, DELIM_ITER);
            }
            if (StringUtils.isNotBlank(data)) { data = iteration + DELIM_ITER + data; }
        }

        return add(filename, data, POS_STEP_N_ITER);
    }

    public static String addIteration(String filename, int iteration) {
        String stepIter = get(filename, POS_STEP_N_ITER);

        String data = DELIM_ITER + iteration;
        if (StringUtils.isNotBlank(stepIter)) {
            if (StringUtils.contains(stepIter, DELIM_ITER)) {
                data = StringUtils.substringBefore(stepIter, DELIM_ITER);
            }
            if (StringUtils.isNotBlank(data)) { data += DELIM_ITER + iteration; }
        }

        return add(filename, data, POS_STEP_N_ITER);
    }

    public static String addStartDateTime(String filename, Date startDateTime) {
        return addStartDateTime(filename, DF_TIMESTAMP.format(startDateTime));
    }

    public static String addStartDateTime(String filename, String timestamp) {
        return add(filename, timestamp, POS_START_TS);
    }

    /** Run ID is the same as start date/time */
    public static String addRunId(String filename, String runId) { return add(filename, runId, POS_START_TS); }

    public static String webFriendly(String filename) {
        return StringUtils.replaceEach(filename, WEB_UNFRIENDLY_CHARS, WEB_FRIENDLY_CHARS);
    }

    // todo: test for nested file reference - contentOrFile is a file whose content contains reference to another file
    public static String resolveContent(String contentOrFile, ExecutionContext context, boolean compact) {
        return resolveContent(contentOrFile, context, compact, true);
    }

    public static String resolveRawContent(String contentOrFile, ExecutionContext context) {
        return resolveContent(contentOrFile, context, false, false);
    }

    public static String resolveContent(String contentOrFile,
                                        ExecutionContext context,
                                        boolean compact,
                                        boolean replaceTokens) {
        return new OutputResolver(contentOrFile,
                                  context,
                                  true,
                                  context.isResolveTextAsURL(),
                                  replaceTokens,
                                  false,
                                  compact)
                   .getContent();

        // if (context.isNullOrEmptyValue(contentOrFile)) { return ""; }
        //
        // if (StringUtils.containsNone(contentOrFile, '\n', '\r', '\t') && ResourceUtils.isWebResource(contentOrFile)) {
        //     String content;
        //     if (resolveUrl) {
        //         // must be HTTP-backed file
        //         content = WsCommand.resolveWebContent(contentOrFile);
        //         content = compact ? StringUtils.trim(content) : content;
        //     } else {
        //         content = contentOrFile;
        //     }
        //
        //     return replaceTokens ? context.replaceTokens(content) : content;
        // }
        //
        // if (!isContentReferencedAsFile(contentOrFile, context)) {
        //     // must be just content (not file)
        //     String content = compact ? StringUtils.trim(contentOrFile) : contentOrFile;
        //     return replaceTokens ? context.replaceTokens(content) : content;
        // }
        //
        // File input = new File(contentOrFile);
        // String content;
        // if (compact) {
        //     List<String> list = FileUtils.readLines(input, DEF_CHARSET);
        //     StringBuilder buffer = new StringBuilder();
        //     for (String line : list) { buffer.append(StringUtils.trim(line)); }
        //     content = buffer.toString();
        // } else {
        //     content = FileUtils.readFileToString(input, DEF_CHARSET);
        // }
        //
        // return replaceTokens ? context.replaceTokens(content) : content;
    }

    /**
     * read {@literal contentOrFile} as raw file content (i.e. byte array) or, if it is not a file, just the
     * content of {@code contentOrFile} as byte array.
     */
    public static byte[] resolveContentBytes(String contentOrFile, ExecutionContext context) throws IOException {
        return StringUtils.isEmpty(contentOrFile) ?
               new byte[0] :
               isContentReferencedAsFile(contentOrFile, context) ?
               FileUtils.readFileToByteArray(new File(contentOrFile)) :
               contentOrFile.getBytes();
    }

    /** we can't have NL or CR or TAB character in filename */
    public static boolean isContentReferencedAsFile(String contentOrFile, ExecutionContext context) {
        return !context.isNullOrEmptyOrBlankValue(contentOrFile) &&
               FileUtil.isSuitableAsPath(contentOrFile) &&
               new File(contentOrFile).canRead();
    }

    public static boolean isContentReferencedAsClasspathResource(String resource, ExecutionContext context) {
        if (StringUtils.isBlank(resource) || StringUtils.equals(resource, context.getNullValueToken())) {
            return false;
        }

        // we can't have NL or CR or TAB character in filename
        if (!FileUtil.isSuitableAsPath(resource)) { return false; }

        String classpathResource = (StringUtils.startsWith(resource, "/") ? "" : "/") + resource;
        URL url = OutputFileUtils.class.getResource(classpathResource);
        if (url == null) { return false; }

        File f = new File(url.getFile());
        return f.canRead();
    }

    /**
     * Need to handle both types of file naming schemes (single run and test plan run).  The differentiators would
     * be the pattern recognized via the {@code file} parameter.
     * <br/><br/>
     * <b>Single Run:</b><br/>
     * <code>[script filename].[start_time yyyyMMdd_HHmmss].[iteration].xlsx</code><br/><br/>
     *
     * <b>Test Plan Run:</b><br/>
     * <code>[plan filename].[test plan].[sequence],[filename].[start_time yyyyMMdd_HHmmss].[iteration].xlsx</code>
     */
    public static String add(String file, String data, int position) {
        if (StringUtils.isBlank(file)) { return file; }
        if (StringUtils.isBlank(data)) { return file; }
        if (position <= POS_FILENAME || position > POS_EXT) { return file; }

        String path;
        if (StringUtils.contains(file, separator)) {
            path = StringUtils.substringBeforeLast(file, separator) + separator;
            file = StringUtils.substringAfter(file, path);
        } else {
            path = "";
        }

        // todo: need to consider plan metadata
        // e.g. PayrollProcessing.End to End - Simple.001,NGP_regression_prep.20171227_153928.001.xlsx
        boolean hasPlanMetadata = RegexUtils.isExact(file, REGEX_PLAN_EXECUTION_OUTPUT_FILE);
        if (hasPlanMetadata) { file = RegexUtils.collectGroups(file, REGEX_PLAN_EXECUTION_OUTPUT_FILE).get(1); }

        // support AT MOST 5 parts (for now)
        String[] parts = StringUtils.split(file, DELIM);
        if (ArrayUtils.getLength(parts) < 2) { return file; }
        if (ArrayUtils.getLength(parts) > 5) { return file; }

        // since we don't know how many "parts" there are, use the last one as extension
        FilePart fp = new FilePart();
        fp.filename = parts[0];
        fp.ext = DELIM + parts[parts.length - 1];

        if (parts.length > 2) { deriveVaryingData(fp, parts[1]); }
        if (parts.length > 3) { deriveVaryingData(fp, parts[2]); }
        if (parts.length > 4) { deriveVaryingData(fp, parts[3]); }

        switch (position) {
            case POS_START_TS:
                fp.startTs = sanitize(data);
                break;
            case POS_BROWSER:
                fp.browser = sanitize(data);
                break;
            case POS_STEP_N_ITER:
                fp.stepIter = sanitize(data);
                break;
            default:
                throw new RuntimeException("Unsupported position for " + NAME + ": " + position);
        }

        return path +
               fp.filename +
               (StringUtils.isNotBlank(fp.startTs) ? DELIM + fp.startTs : "") +
               (StringUtils.isNotBlank(fp.browser) ? DELIM + fp.browser : "") +
               (StringUtils.isNotBlank(fp.stepIter) ? DELIM + fp.stepIter : "") +
               fp.ext;
    }

    public static String addTestPlan(String outputFileName, ExecutionDefinition execDef) {
        if (StringUtils.isBlank(outputFileName)) { return outputFileName; }
        if (execDef == null) { return outputFileName; }
        if (StringUtils.isBlank(execDef.getPlanFilename()) ||
            StringUtils.isBlank(execDef.getPlanName()) ||
            execDef.getPlanSequence() < 1) { return outputFileName; }

        String path;
        if (StringUtils.contains(outputFileName, separator)) {
            path = StringUtils.substringBeforeLast(outputFileName, separator) + separator;
            outputFileName = StringUtils.substringAfter(outputFileName, path);
        } else {
            path = "";
        }

        return path +
               execDef.getPlanFilename() + DELIM +
               webFriendly(execDef.getPlanName()) + DELIM +
               StringUtils.leftPad(execDef.getPlanSequence() + "", 3, "0") + PLAN_SCRIPT_SEP +
               outputFileName;
    }

    private static String get(String file, int position) {
        if (StringUtils.isBlank(file)) { return file; }
        if (position < POS_FILENAME) { return file; }
        if (position > POS_EXT) { return file; }

        // support AT MOST 5 parts (for now)
        String[] parts = StringUtils.split(file, DELIM);
        if (ArrayUtils.getLength(parts) < 2) { return file; }
        if (ArrayUtils.getLength(parts) > 5) { return file; }

        // since we don't know how many "parts" there are, use the last one as extension
        FilePart fp = new FilePart();
        fp.filename = parts[0];
        fp.ext = DELIM + parts[parts.length - 1];

        if (parts.length > 2) { deriveVaryingData(fp, parts[1]); }
        if (parts.length > 3) { deriveVaryingData(fp, parts[2]); }
        if (parts.length > 4) { deriveVaryingData(fp, parts[3]); }

        switch (position) {
            case POS_FILENAME:
                return fp.filename;
            case POS_START_TS:
                return fp.startTs;
            case POS_BROWSER:
                return fp.browser;
            case POS_STEP_N_ITER:
                return fp.stepIter;
            case POS_EXT:
                return fp.ext;
            default:
                throw new RuntimeException("Unsupported position for " + NAME + ": " + position);
        }
    }

    @NotNull
    public static String generateErrorLog(TestStep testStep, Throwable e) {
        File log = OutputFileUtils.generateErrorFile(testStep, e);
        String logFqn = log.getAbsolutePath();
        ExecutionContext context = testStep.getContext();
        if (context.isOutputToCloud() && FileUtil.isFileReadable(log, 1)) {
            try {
                ConsoleUtils.log("output-to-cloud enabled; copying " + logFqn + " cloud...");
                logFqn = context.getOtc().importFile(log, true);
            } catch (IOException ex) {
                // unable to send log to cloud...
                ConsoleUtils.log("Unable to copy resource to cloud: " + e.getMessage());
                context.getLogger().log(testStep, toCloudIntegrationNotReadyMessage(logFqn + ": " + e.getMessage()));
            }
        }
        return logFqn;
    }

    @NotNull
    public static File generateErrorFile(TestStep testStep, Throwable e) {
        Syspath syspath = new Syspath();
        String logPath = syspath.log("fullpath") + separator + generateOutputFilename(testStep, ".log");
        File log = new File(logPath);
        int currentCounter = 1;
        do {
            if (log.exists() && log.canRead() && log.length() > 5) {
                if (currentCounter == 1) {
                    logPath = StringUtils.replace(logPath, ".log", currentCounter + ".log");
                } else {
                    logPath = StringUtils.replace(logPath, (currentCounter - 1) + ".log", currentCounter + ".log");
                }
                currentCounter++;
                log = new File(logPath);
            }
        } while (currentCounter > 10);

        // build error content
        StringBuilder buffer = new StringBuilder();
        buffer.append("Nexial Version:    ").append(ExecUtils.NEXIAL_MANIFEST).append(NL)
              .append("Current Timestamp: ").append(DateUtility.formatLogDate(System.currentTimeMillis())).append(NL)
              .append("Test Step:         ").append(testStep.getMessageId()).append(NL)
              .append(StringUtils.repeat("-", 80)).append(NL);

        if (e instanceof AssertionError || e instanceof IllegalArgumentException) {
            buffer.append(e.getMessage()).append(NL);
        } else {
            StringWriter writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            buffer.append(writer.getBuffer().toString()).append(NL);
            printWriter.close();
        }

        try {
            FileUtils.writeStringToFile(log, buffer.toString(), DEF_FILE_ENCODING);
        } catch (IOException ex) {
            ConsoleUtils.error("Unable to create log file (" + log + ") for the exception thrown: " + ex.getMessage());
        }
        return log;
    }

    @NotNull
    public static File prependRandomizedTempDirectory(String filename) {
        if (StringUtils.isBlank(filename)) { throw new IllegalArgumentException("Invalid filename '" + filename + "'");}

        File tmpDir = SystemUtils.getJavaIoTmpDir();
        // use random alphabetic to avoid collision in parallel processing
        return new File((tmpDir.getAbsolutePath() + separator + RandomStringUtils.randomAlphabetic(5)) + separator +
                        filename);
    }

    /** could be timestamp, browser or step-n-iteration */
    private static void deriveVaryingData(FilePart fp, String varData) {
        if (StringUtils.contains(varData, DELIM_ITER) || NumberUtils.isDigits(varData)) {
            fp.stepIter = varData;
            return;
        }

        if (RegexUtils.isExact(varData, REGEX_START_DATE_TIME)) {
            fp.startTs = varData;
            return;
        }

        if (RegexUtils.isExact(varData, REGEX_PREFIX_AND_START_DATE_TIME)) {
            List<String> groups = RegexUtils.collectGroups(varData, REGEX_PREFIX_AND_START_DATE_TIME);
            if (CollectionUtils.size(groups) >= 2) {
                fp.prefix = groups.get(0);
                fp.startTs = groups.get(1);
            }
            return;
        }

        for (BrowserType browserType : BrowserType.values()) {
            if (StringUtils.startsWith(varData, browserType.toString())) {
                fp.browser = varData;
                return;
            }
        }

        // can't recognize... assume it's step name
        fp.stepIter = varData;
    }

    private static String sanitize(String data) {
        return StringUtils.replaceEach(data, UNSUPPORTED_FILE_CHARS, SUPPORTED_FILE_CHARS);
    }
}
