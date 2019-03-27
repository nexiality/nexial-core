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

package org.nexial.core.plugins.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.LevenshteinDetailedDistance;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.IOFilePathFilter;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.plugins.filevalidation.RecordData;
import org.nexial.core.plugins.filevalidation.parser.FileParserFactory;
import org.nexial.core.plugins.filevalidation.validators.ErrorReport;
import org.nexial.core.plugins.filevalidation.validators.MasterFileValidator;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.OutputFileUtils;

import static java.io.File.separator;
import static java.io.File.separatorChar;
import static java.lang.System.lineSeparator;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.SystemVariables.getDefault;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.plugins.io.ComparisonResult.*;
import static org.nexial.core.plugins.io.IoAction.copy;
import static org.nexial.core.plugins.io.IoAction.move;
import static org.nexial.core.plugins.io.IoCommand.CompareMode.FAIL_FAST;
import static org.nexial.core.plugins.io.IoCommand.CompareMode.*;
import static org.nexial.core.utils.CheckUtils.*;

public class IoCommand extends BaseCommand {
    protected static final LevenshteinDetailedDistance levenshtein = LevenshteinDetailedDistance.getDefaultInstance();
    private static final String TMP_EOL = "~~~5EntrY.w4z.hErE~~~";
    private static final List<String> MS_OFFICE_FILE_EXT = Arrays.asList("xlsx", "xls", "doc", "docx", "ppt", "pptx");
    private static final NumberFormat PERCENT_FORMAT = NumberFormat.getPercentInstance();

    enum CompareMode {FAIL_FAST, THOROUGH, DIFF}

    @Override
    public String getTarget() { return "io"; }

    /**
     * save matching file list to `var`.  The matching logic is derived from `path` and `filePattern` (e.g. a*.txt)
     *
     * special treatment for MS Office files: office temp files will be ignored and removed from any matches
     */
    public StepResult saveMatches(String var, String path, String filePattern) {
        requiresValidVariableName(var);
        requiresReadableDirectory(path, "invalid path", path);
        requiresNotBlank(filePattern, "invalid file pattern", filePattern);

        // list files
        String slash = StringUtils.contains(path, "\\") ? "\\" : "/";
        String pattern = StringUtils.appendIfMissing(path, slash) + filePattern;
        List<String> files = new IOFilePathFilter().filterFiles(pattern);
        if (files == null) { files = new ArrayList<>(); }

        // filter out office temp files
        files.removeIf(this::isMSOfficeTempFile);

        // save matches
        context.setData(var, files);
        return StepResult.success("saving matching file list to " + var);
    }

    /**
     * search-and-replace routine on the content of {@code file} via the name/value pairs found in {@code config}. The
     * {@code name} will be the search string, and the {@code value} the replacement string. Each name/value pairs will
     * be first scanned for data variable substitution (based on {@code ${...}} syntax) before performing the
     * search-and-replace routine. In order to circumvent the {@code ${...}} substitution - e.g. perhaps one desires
     * to search for {@code ${data}} with {@code data} - one can escape the {@code ${...}} syntax. For example,
     * {@code \$\{data\}}.
     */
    public StepResult searchAndReplace(String file, String config, String saveAs) throws IOException {
        requiresReadableFile(file);
        requiresReadableFile(config);
        requiresNotBlank(saveAs, "invalid saveAs", saveAs);

        Map<String, String> configMap = TextUtils.loadProperties(config);
        if (MapUtils.isEmpty(configMap)) {
            FileUtils.copyFile(new File(file), new File(saveAs));
            return StepResult.success("Config '" + config + "' does not contain any search-and-replace configuration");
        }

        // treat escaped ${...} sequence
        Map<String, String> configMap2 = new HashMap<>();
        configMap.forEach((name, value) ->
                              configMap2.put(ExecutionContext.unescapeToken(context.replaceTokens(name)),
                                             ExecutionContext.unescapeToken(context.replaceTokens(value))));

        AtomicReference<String> content =
            new AtomicReference<>(FileUtils.readFileToString(new File(file), DEF_FILE_ENCODING));
        configMap2.forEach((name, value) -> content.set(StringUtils.replace(content.get(), name, value)));
        FileUtils.writeStringToFile(new File(saveAs), content.get(), DEF_FILE_ENCODING);

        return StepResult.success("search-and-replace completed and saved to '" + saveAs + "'");
    }

    /**
     * "fast" compare between {@code file1} and {@code file2}.
     * </p>
     * The "fast" comparision includes file size and fail-fast byte-by-byte comparison without detailed
     * differential report.
     * </p>
     * This method is different than {@link #compare(String, String, String)} in that
     * {@link #compare(String, String, String)} includes the same "fast" compare, plus: <ol>
     * <li><code>compare()</code> includes line-by-line comparison and differential report</li>
     * </ol>
     *
     * @see #compare(String, String, String)
     */
    public StepResult assertEqual(String expected, String actual) {
        requiresNotBlank(expected, "invalid expected file", expected);
        requiresNotBlank(actual, "invalid actual file", actual);

        Pair<File, File> files = prepCompare(expected, actual);

        FileComparisonReport comparisons = new FileComparisonReport();
        StepResult result = fastCompare(files, comparisons);
        if (result != null) { return result; }

        return StepResult.fail("expected file '" + files.getLeft().getAbsolutePath() + "' and " +
                               "actual file '" + files.getRight().getAbsolutePath() + "' are not the same");
    }

    public StepResult assertNotEqual(String expected, String actual) {
        requires(StringUtils.isNotBlank(expected), "invalid expected file", expected);
        requires(StringUtils.isNotBlank(actual), "invalid actual file", actual);

        Pair<File, File> files = prepCompare(expected, actual);

        FileComparisonReport comparisons = new FileComparisonReport();
        StepResult result = fastCompare(files, comparisons);
        String msg = "expected file '" + files.getLeft().getAbsolutePath() + "' and " +
                     "actual file '" + files.getRight().getAbsolutePath() + "' are ";
        return result != null ? StepResult.fail(msg + "the same") : StepResult.success(msg + "not the same");
    }

    //todo: need to consider target as file name, not just dir. but how to recognize this?
    public StepResult copyFiles(String source, String target) { return doAction(copy, source, target); }

    public StepResult makeDirectory(String source) {
        requires(StringUtils.isNotBlank(source), "invalid source", source);
        File dir = new File(source);
        try {
            FileUtils.forceMkdir(dir);
            return StepResult.success("make directory complete on '" + source + "'");
        } catch (IOException e) {
            return StepResult.fail("make directory failed on '" + source + "': " + e.getMessage());
        }
    }

    //todo: need to consider target as file name, not just dir. but how to recognize this?
    public StepResult moveFiles(String source, String target) { return doAction(move, source, target); }

    public StepResult deleteFiles(String location, String recursive) {
        requires(StringUtils.isNotBlank(recursive), "invalid value for recursive", recursive);
        boolean isRecursive = BooleanUtils.toBoolean(recursive);
        return doAction(isRecursive ? IoAction.deleteRecursive : IoAction.delete, location, null);
    }

    public StepResult readFile(String var, String file) {
        requiresValidVariableName(var);

        File input = toFile(file);

        try {
            String content = FileUtils.readFileToString(input, DEF_CHARSET);
            updateDataVariable(var, content);
            return StepResult.success("Content read from '" + file + "' to ${" + var + "}");
        } catch (IOException e) {
            return StepResult.fail("Error when reading content from '" + file + "': " + e.getMessage());
        }
    }

    /**
     * save metadata of `file` to `var`
     */
    public StepResult saveFileMeta(String var, String file) {
        requiresValidVariableName(var);
        requires(StringUtils.isNotBlank(file), "invalid file", file);

        File f = new File(file);
        if (!f.exists()) { return StepResult.fail("invalid or non-existent file '" + file + "'"); }

        try {
            FileMeta fileMeta = new FileMeta(f);
            context.setData(var, fileMeta);
            if (context.isVerbose()) { log(file + " --> \n" + fileMeta); }
            return StepResult.success("file meta for '" + file + "' saved to variable '" + var + "'");
        } catch (IOException e) {
            return StepResult.fail("unable to save file meta for '" + file + "': " + e.getMessage());
        }
    }

    public StepResult readProperty(String var, String file, String property) {
        requires(StringUtils.isNotBlank(property), "invalid property name", property);
        requires(StringUtils.isNotBlank(var), "invalid var", var);

        File input = toFile(file);

        try {
            Properties props = ResourceUtils.loadProperties(input);
            updateDataVariable(var, props.getProperty(property));
            return StepResult.success("Property '" + property + "' read from '" + file + "' to ${" + var + "}");
        } catch (IOException e) {
            return StepResult.fail("Error when reading properties from '" + file + "': " + e.getMessage());
        }
    }

    public StepResult writeFile(String file, String content, String append) {
        return writeFile(file, content, append, true);
    }

    public StepResult writeFileAsIs(String file, String content, String append) {
        return writeFile(file, content, append, false);
    }

    public StepResult writeProperty(String file, String property, String value) {
        requires(StringUtils.isNotBlank(file), "invalid file", file);
        requires(StringUtils.isNotBlank(property), "invalid property name", property);

        File input = new File(StringUtils.trim(file));
        Properties props;
        try {
            props = input.exists() && input.canRead() && input.length() > 1 ?
                    ResourceUtils.loadProperties(input) : new Properties();
        } catch (IOException e) {
            // e.printStackTrace();
            return StepResult.fail("Error when reading properties from '" + file + "': " + e.getMessage());
        }

        if (StringUtils.isBlank(value)) {
            props.remove(property);
        } else {
            props.setProperty(property, value);
        }

        try (FileWriter writer = new FileWriter(input)) {
            props.store(writer, "updated on " + DateUtility.getCurrentDateTime());
            return StepResult.success("Property '" + property + "' updated to " + file);
        } catch (IOException e) {
            return StepResult.fail("Error occurred when writing to " + file + ": " + e.getMessage());
        }
    }

    public StepResult compare(String expected, String actual, String failFast) {
        requires(StringUtils.isNotEmpty(expected), "Invalid 'expected'; neither a file or text", expected);
        requires(StringUtils.isNotEmpty(actual), "Invalid 'actual'; neither a file or text", actual);
        CompareMode compareMode = !StringUtils.isBlank(failFast) && BooleanUtils.toBoolean(failFast) ?
                                  FAIL_FAST : THOROUGH;
        return compare(expected, actual, compareMode, null);
    }

    public StepResult saveDiff(String var, String expected, String actual) {
        requiresValidVariableName(var);
        requires(StringUtils.isNotEmpty(expected), "Invalid 'expected'; neither a file or text", expected);
        requires(StringUtils.isNotEmpty(actual), "Invalid 'actual'; neither a file or text", actual);
        return compare(expected, actual, DIFF, var);
    }

    public StepResult assertReadableFile(String file, String minByte) {
        requiresNotBlank(file, "invalid file", file);

        boolean passed;
        if (StringUtils.isBlank(minByte) || StringUtils.equals(StringUtils.trim(minByte), "-1")) {
            passed = FileUtil.isFileReadable(file);
        } else {
            requiresPositiveNumber(minByte, "minByte must be a positive integer", minByte);
            passed = FileUtil.isFileReadable(file, NumberUtils.toInt(minByte));
        }

        return new StepResult(passed,
                              "File (" + file + ") is " +
                              (passed ?
                               "readable and meet size requirement" :
                               "NOT readable or less than specified size"
                              ),
                              null);
    }

    public StepResult zip(String filePattern, String zipFile) {
        requires(StringUtils.isNotBlank(filePattern), "invalid file pattern", filePattern);
        requires(StringUtils.isNotBlank(zipFile), "invalid zip file", zipFile);

        try {
            FileUtil.zip(filePattern, zipFile);
            return StepResult.success("zip file '" + zipFile + "' created for '" + filePattern + "'");
        } catch (IOException e) {
            return StepResult.fail("Unable to create zip for '" + filePattern + "' due to " + e.getMessage());
        }
    }

    public StepResult unzip(String zipFile, String target) {
        requires(StringUtils.isNotBlank(zipFile), "invalid zip file", zipFile);

        requires(StringUtils.isNotBlank(target), "invalid target location", target);
        File dir = new File(target);
        if (dir.isFile()) { return StepResult.fail("target exists as a file: " + target); }
        if (!dir.exists()) {
            dir.mkdirs();
            if (!dir.exists()) { return StepResult.fail("Unable to create specific target location: " + target); }
        }

        File zip;
        String tempDest = null;
        if (StringUtils.startsWith(zipFile, PREFIX_JAR)) {
            tempDest = FileUtils.getTempDirectoryPath() + separatorChar + RandomStringUtils.randomAlphabetic(5);
            try {
                FileUtils.forceMkdir(new File(tempDest));
            } catch (IOException e) {
                return StepResult.fail("Error occurred with zip file '" + zipFile + "': " + e.getMessage());
            }

            StepResult result = copyFiles(zipFile, tempDest);
            if (result.failed()) {
                return StepResult.fail("Error occurred with zip file '" + zipFile + "': " + result.getMessage());
            }

            String resourcePath = StringUtils.substringAfter(zipFile, PREFIX_JAR);
            zip = new File(tempDest + separatorChar + StringUtils.substringAfterLast(resourcePath, "/"));
        } else {
            zip = new File(zipFile);
        }

        requires(zip.exists() && zip.canRead() && zip.length() > 10, "invalid zip file", zipFile);

        try {
            List<File> unzipped = FileUtil.unzip(zip, dir);
            return StepResult.success("Unzip " + CollectionUtils.size(unzipped) + " files/directories from " + zip);
        } catch (IOException e) {
            return StepResult.fail("Unable to unzip " + zip + " to " + zip + ": " + e.getMessage());
        } finally {
            if (tempDest != null) { FileUtils.deleteQuietly(new File(tempDest)); }
        }
    }

    public StepResult validate(String var, String profile, String inputFile) {
        requiresValidVariableName(var);
        requiresNotBlank(profile, "Invalid 'profile',", profile);
        requiresReadableFile(inputFile);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(STD_DATE_FORMAT);
        String startTime = dtf.format(LocalDateTime.now());

        Map<String, String> configs = context.getDataByPrefix(profile);
        String jsonConfigFilePath;
        if (configs.containsKey(CONFIG_JSON)) {
            jsonConfigFilePath = configs.get(CONFIG_JSON);
            requiresReadableFile(jsonConfigFilePath);
        } else {
            return StepResult.fail("Invalid profile name '" + profile +
                                   "' or associated variable '.configJson' not found");
        }

        String excelMappingFilePath = null;
        if (configs.containsKey(MAPPING_EXCEL)) {
            excelMappingFilePath = configs.get(MAPPING_EXCEL);
            requiresReadableFile(excelMappingFilePath);
        }

        StopWatch stopWatch = new StopWatch();

        RecordData recordData;
        MasterFileValidator fileParser = FileParserFactory.getFileParser(jsonConfigFilePath, excelMappingFilePath);
        requiresNotNull(fileParser, "Failed to provide suitable file parser");

        stopWatch.start();
        recordData = fileParser.parseAndValidate(inputFile);
        stopWatch.stop();

        recordData.setStartTime(startTime);
        recordData.setProcessTime(DateFormatUtils.format(stopWatch.getTime(), "mm:ss:SSS"));
        recordData.setInputFile(inputFile);
        recordData.setExcelFile(excelMappingFilePath);
        context.setData(var, recordData);

        String caption = "Total " + recordData.getTotalRecordsFailed() +
                         " records failed out of " + recordData.getTotalRecordsProcessed() +
                         " (click link on the right for details)";

        File results = null;
        String reportType = context.getStringData(profile + REPORT_TYPE);
        if (!StringUtils.equalsAnyIgnoreCase(reportType, "Excel", "JSON")) {
            ConsoleUtils.log("validation report can't be generated for invalid report type '" + reportType +
                             "'. Valid reportType can be Excel or JSON");
        }

        if (reportType.equalsIgnoreCase("Excel")) { results = ErrorReport.createExcel(recordData); }
        if (reportType.equalsIgnoreCase("JSON")) { results = ErrorReport.createJSON(recordData); }
        if (results != null) { addLinkRef(caption, reportType + " report", results.getAbsolutePath()); }
        if (recordData.isHasError()) {
            return StepResult.fail("Error in file validation. Refer error report.");
        } else {
            return StepResult.success();
        }

    }

    public StepResult count(String var, String path, String pattern) {
        requires(StringUtils.isNotBlank(path), "invalid path", path);
        File base = new File(path);
        requires(base.exists() && base.isDirectory() && base.canRead(), "unreadable/non-existent path", path);

        requires(StringUtils.isNotBlank(pattern), "invalid pattern", pattern);
        requiresValidVariableName(var);

        List<File> matches = FileUtil.listFiles(path, pattern, true);
        int matchCount = CollectionUtils.isEmpty(matches) ? 0 : matches.size();
        context.setData(var, matchCount);

        return StepResult.success("matched " + matchCount + " and saved to ${" + var + "}");
    }

    public StepResult filter(String source, String target, String matchPattern) {
        requiresReadableFile(source);
        requiresNotBlank(target, "Invalid target", target);
        requiresNotBlank(matchPattern, "Invalid match pattern", matchPattern);

        File sourceFile = new File(source);

        if (FileUtil.isDirectoryReadable(target)) { target += separator + sourceFile.getName(); }
        File targetFile = new File(target);

        try (
            LineIterator sourceIterator = FileUtils.lineIterator(sourceFile, "UTF-8");
            FileOutputStream outputStream = FileUtils.openOutputStream(targetFile)
        ) {
            boolean found = false;
            Pattern pattern = Pattern.compile(matchPattern);
            while (sourceIterator.hasNext()) {
                String line = sourceIterator.nextLine();
                if (pattern.matcher(line).matches()) {
                    if (!found) {
                        outputStream.write(line.getBytes());
                        found = true;
                    } else {
                        outputStream.write((System.lineSeparator() + line).getBytes());
                    }
                }
            }

            outputStream.flush();
            outputStream.close();
            return StepResult.success("source '" + source + "' filtered and saved to '" + target + "'");
        } catch (Throwable e) {
            return StepResult.fail(e.getMessage(), e);
        }
    }

    /**
     * rename a file or directory to the new name. {@code newName} is expected to be just the new file name or new
     * directory name ONLY. This command is not a means to move a file or directory to another directory.
     */
    public StepResult rename(String target, String newName) {
        requiresNotBlank(target, "Invalid target", target);
        requiresNotBlank(newName, "Invalid new name", newName);

        // is target a file or directory
        String msgPrefix = "Unable to rename '" + target + "' to '" + newName + "' ";
        if (!FileUtil.isFileReadable(target) && !FileUtil.isDirectoryReadable(target)) {
            return StepResult.fail(msgPrefix + "because it cannot be resolved either as a file or a directory");
        }

        File targetFile = new File(target);

        File newTarget = new File(targetFile.getParent(), newName);
        if (newTarget.exists() && newTarget.canRead()) {
            return StepResult.fail(msgPrefix + "because '" + newTarget + "' already exists.");
        }

        if (!targetFile.renameTo(newTarget)) {
            return StepResult.fail("Unable to rename '" + target + "' to '" + newTarget + "': " +
                                   "check on access and the feasibility of the new name '" + newName + "'");
        }

        return StepResult.success("'" + target + "' renamed to '" + newTarget + "'");
    }

    /**
     * read binary content of {@code file} as base64 string and story it to {@code var}.
     */
    public StepResult base64(String var, String file) throws IOException {
        requiresValidVariableName(var);
        requiresReadableFile(file);

        byte[] content = FileUtils.readFileToByteArray(new File(file));
        updateDataVariable(var, Base64.getEncoder().encodeToString(content));

        return StepResult.success("File content converted to BASE64 and saved to '" + var + "'");
    }

    public static String formatPercent(double number) { return PERCENT_FORMAT.format(number); }

    @NotNull
    protected StepResult writeFile(String file, String content, String append, boolean replaceTokens) {
        requires(StringUtils.isNotBlank(file), "invalid file", file);
        // allow user to write empty content to file (instead of throwing error)
        // requires(StringUtils.isNotBlank(content), "invalid content", content);
        requires(StringUtils.isNotBlank(append), "invalid value for append", append);

        boolean isAppend = BooleanUtils.toBoolean(append);
        File output = new File(file);
        if (output.isDirectory()) {
            return StepResult.fail("'" + file + "' is EXPECTED as file, but found directory instead.");
        }

        try {
            content = OutputFileUtils.resolveContent(content, context, false, replaceTokens);
            content = adjustEol(content);
            FileUtils.writeStringToFile(output, StringUtils.defaultString(content), DEF_CHARSET, isAppend);
            return StepResult.success("Content " + (isAppend ? "appended" : "written") + " to " + file);
        } catch (IOException e) {
            return StepResult.fail("Error occurred when writing to " + file + ": " + e.getMessage());
        }
    }

    protected StepResult doAction(IoAction action, String source, String target) {
        // sanity check
        requires(StringUtils.isNotBlank(source), "invalid source", source);
        if (action.isTargetRequired()) { requires(StringUtils.isNotBlank(target), "invalid target", target); }

        File sourceFile = new File(source);
        if (!sourceFile.isDirectory() && StringUtils.endsWith(source, separator)) {
            return StepResult.fail("source '" + source + "' does not exist");
        }

        Collection<File> sourceFiles = new ArrayList<>();
        if (!sourceFile.isDirectory()) {
            if (sourceFile.isFile()) {
                sourceFiles.add(sourceFile);
            } else {
                sourceFiles = action.listFilesByPattern(source);
                if (CollectionUtils.isEmpty(sourceFiles)) {
                    return StepResult.fail(action + " failed since source '" + source + "' does not exists");
                }
            }
        }

        String prolog = action + " done [source '" + source + "', target '" + target + "']";
        String errorProlog = action + " failed [source '" + source + "', target '" + target + "']: ";

        File targetFile = null;
        if (action.isTargetRequired()) {
            targetFile = new File(target);
            if (sourceFile.isDirectory()) {
                try {
                    action.doAction(sourceFile, targetFile);
                    return StepResult.success(prolog);
                } catch (IOException e) {
                    return StepResult.fail(errorProlog + e.getMessage(), e);
                }
            }
        }

        try {
            if (sourceFile.isDirectory()) {
                action.doAction(sourceFile, targetFile);
            } else {
                action.doAction(sourceFiles, targetFile);
            }
            return StepResult.success(prolog);
        } catch (IOException e) {
            return StepResult.fail(errorProlog + e.getMessage(), e);
        }
    }

    /**
     * compare isEmpty
     */
    protected Pair<File, File> prepCompare(String expected, String actual) {
        requires(StringUtils.isNotBlank(expected), "invalid 'expected'", expected);
        requires(StringUtils.isNotBlank(actual), "invalid 'actual'", actual);

        File expectedFile = new File(expected);
        requires(expectedFile.canRead(), "expected file is not readable", expected);
        requires(expectedFile.length() > 0, "expected file is empty", expected);

        File actualFile = new File(actual);
        requires(actualFile.canRead(), "actual file is not readable", actual);
        requires(actualFile.length() > 0, "actual file is empty", actual);

        return new ImmutablePair<>(expectedFile, actualFile);
    }

    /**
     * compare file content without diff details.
     */
    protected StepResult fastCompare(Pair<File, File> files, FileComparisonReport report) {
        File expectedFile = files.getLeft();
        File actualFile = files.getRight();

        try {
            if (FileUtils.contentEquals(expectedFile, actualFile)) {
                report.addFileMismatch(file("files matched exactly"));
                return StepResult.success("files matched exactly");
            }

            long expectedLength = expectedFile.length();
            long actualLength = actualFile.length();
            if (expectedLength != actualLength) {
                report.addFileMismatch(ComparisonResult.fileSizeDiff(expectedLength, actualLength));
            }

            int expectedLines = CollectionUtils.size(FileUtils.readLines(expectedFile, DEF_CHARSET));
            int actualLines = CollectionUtils.size(FileUtils.readLines(actualFile, DEF_CHARSET));
            if (expectedLines != actualLines) { report.addFileMismatch(fileLineDiff(expectedLines, actualLines)); }
        } catch (IOException e) {
            // unlikely since we've already gone through prepCompare()
            String expected = expectedFile.getAbsolutePath();
            String actual = actualFile.getAbsolutePath();
            String error = "Error when reading either '" + expected + "' or '" + actual + "': " + e.getMessage();
            error(error);

            report.addFileMismatch(file(error));
            // keep going
        }

        return null;
    }

    protected File toFile(String file) {
        requires(StringUtils.isNotBlank(file), "invalid file", file);
        File input = new File(file);
        requires(input.canRead(), "unreadable file", file);
        return input;
    }

    protected StepResult compare(String expected, String actual, CompareMode compareMode, String diffVar) {
        FileComparisonReport report = new FileComparisonReport();

        boolean failfast = compareMode == FAIL_FAST;

        String expectedContent;
        String actualContent;

        try {
            // 1. compare file size and line count as physical files
            if (FileUtil.isFileReadable(expected) && FileUtil.isFileReadable(actual)) {
                File expectedFile = new File(expected);
                File actualFile = new File(actual);
                Pair<File, File> files = new ImmutablePair<>(expectedFile, actualFile);

                StepResult result = fastCompare(files, report);
                if (result != null && result.isSuccess()) {
                    return compareMode == DIFF ?
                           createDiff(diffVar, report) : passContentComparison(result.getMessage(), report);
                }

                if (failfast) {
                    // if I'm here, then fastCompare() did not PASS.
                    String message = "'" + expected + "' and '" + actual + "' differ in size and/or line count";
                    return failContentComparison(message, report);
                }

                // not fail fast, so get content and get ready for line-by-line comparison
                expectedContent = OutputFileUtils.resolveContent(expected, context, false);
                actualContent = OutputFileUtils.resolveContent(actual, context, false);
            } else {
                // 2. compare file size and line counts as string objects
                expectedContent = OutputFileUtils.resolveContent(expected, context, false);
                actualContent = OutputFileUtils.resolveContent(actual, context, false);

                boolean fastCompareSuccess = StringUtils.equals(expectedContent, actualContent);
                if (fastCompareSuccess) {
                    report.addFileMismatch(file("content matched exactly"));
                    return compareMode == DIFF ?
                           createDiff(diffVar, report) : passContentComparison("content matched exactly", report);
                }

                int expectedLength = StringUtils.length(expectedContent);
                int actualLength = StringUtils.length(actualContent);
                if (expectedLength != actualLength) {
                    report.addFileMismatch(fileSizeDiff(expectedLength, actualLength));
                }

                int expectedLines = CollectionUtils.size(TextUtils.toListPreserveEmpty(expectedContent, "\n", false));
                int actualLines = CollectionUtils.size(TextUtils.toListPreserveEmpty(actualContent, "\n", false));
                if (expectedLines != actualLines) { report.addFileMismatch(fileLineDiff(expectedLines, actualLines)); }

                if (failfast && report.hasMismatch()) { return failContentComparison(report); }
            }
        } catch (IOException e) {
            return StepResult.fail("Unable to read content: " + e.getMessage(), e);
        }

        // WON'T WORK: context.replaceToken() will forcefully unified EOL to \n
        // 2. check for difference in EOL
        // int expectedNl = StringUtils.countMatches(expectedContent, '\n');
        // int expectedCr = StringUtils.countMatches(expectedContent, "\r\n");
        // int actualNl = StringUtils.countMatches(actualContent, '\n');
        // int actualCr = StringUtils.countMatches(actualContent, "\r\n");
        // if (expectedNl != actualNl || expectedCr != actualCr) {
        // 	only report if EOL characters used in EXPECTED and ACTUAL are different
        // if ((expectedNl != 0 || actualNl != 0) && (expectedCr != 0 || actualCr != 0)) {
        // 	report.addFileMismatch(fileEolDiff(expectedNl, expectedCr, actualNl, actualCr));
        // }
        // }

        // 3. double check line count
        List<String> expectedRows =
            TextUtils.toListPreserveEmpty(StringUtils.replace(expectedContent, "\r\n", "\n"), "\n", false);
        List<String> actualRows =
            TextUtils.toListPreserveEmpty(StringUtils.replace(actualContent, "\r\n", "\n"), "\n", false);
        int eRowCount = CollectionUtils.size(expectedRows);
        int aRowCount = CollectionUtils.size(actualRows);
        if (CollectionUtils.isEmpty(expectedRows) || CollectionUtils.isEmpty(actualRows)) {
            report.addFileMismatch(contentEmpty(eRowCount, aRowCount));
            return compareMode == DIFF ? createDiff(diffVar, report) : failContentComparison(report);
        }

        // 4. line-by-line compare here we go!
        boolean logMatches = context.getBooleanData(LOG_MATCH, getDefaultBool(LOG_MATCH));
        int currentErrorCounter = report.getMismatchCount();
        int aPos = -1;
        int aLastParsed = aPos;
        for (int ePos = 0; ePos < expectedRows.size(); ePos++) {
            if (report.getMismatchCount() > currentErrorCounter && failfast) { return failContentComparison(report); }

            String eRow = expectedRows.get(ePos);
            int pos = ePos + 1;
            aPos++;

            // 4.1 ACTUAL line not found
            if (actualRows.size() <= aPos) {
                report.addLineMismatch(lineMissing(pos, eRow, null));
                continue;
            }

            String aRow = actualRows.get(aPos);
            aLastParsed = aPos;

            // this could be (1) exact match, (2) partial match, (3) completely off

            // 4.2 test for perfect match
            if (StringUtils.equals(eRow, aRow)) {
                if (logMatches) {
                    report.addLineMatch(lineMatched(pos, "perfect match", eRow).maligned(pos, aPos + 1));
                } else {
                    report.addLineMatch(null);
                }
                continue;
            }

            // 4.3 test for mismatched cases
            if (StringUtils.containsIgnoreCase(eRow, aRow)) {
                report.addLineMismatch(line(pos, "mismatch due to letter case", eRow, aRow).maligned(pos, aPos + 1));
                continue;
            }

            // 4.4 test for leading/trailing spaces
            String eRowTrimmed = StringUtils.trim(eRow);
            String aRowTrimmed = StringUtils.trim(aRow);
            if (StringUtils.equals(eRowTrimmed, aRowTrimmed)) {
                report.addLineMismatch(line(pos, "mismatch due to leading/trailing spaces", eRow, aRow)
                                           .maligned(pos, aPos + 1));
                continue;
            }

            // 4.5 test for between-letter space mismatch
            String eRowNormalized = StringUtils.deleteWhitespace(eRowTrimmed);
            String aRowNormalized = StringUtils.deleteWhitespace(aRowTrimmed);
            if (StringUtils.equals(eRowNormalized, aRowNormalized)) {
                report.addLineMismatch(line(pos, "mismatch due to extra spaces", eRow, aRow).maligned(pos, aPos + 1));
                continue;
            }

            // 4.6 test for case and space mismatch
            if (StringUtils.equalsIgnoreCase(eRowNormalized, aRowNormalized)) {
                report.addLineMismatch(line(pos, "mismatch due to extra spaces and letter cases", eRow, aRow)
                                           .maligned(pos, aPos + 1));
                continue;
            }

            // 4.7 before more testing, let's see if there's another one line here after (actual) that match current (expected)
            int aMatchedPosition = scanForMatchingRow(eRow, actualRows, ePos);
            if (aMatchedPosition > ePos) {
                // match found, but it's after current line
                // consider this as "extra lines found in ACTUAL
                for (int j = ePos; j < aMatchedPosition; j++) {
                    report.addLineMismatch(lineExtraFound((j + 1), null, actualRows.get(j)));
                }
                ePos--;
                aPos = aMatchedPosition - 1;
                continue;
            }

            int eMatchedPosition = scanForMatchingRow(aRow, expectedRows, aPos);
            if (eMatchedPosition > aPos) {
                // match found, but it's after current line
                // consider this as "extra lines found in EXPECTED
                for (int j = aPos; j < eMatchedPosition; j++) {
                    report.addLineMismatch(lineMissing((j + 1), expectedRows.get(j), null));
                }
                ePos = eMatchedPosition - 1;
                aPos--;
                continue;
            }

            // 4.8 test for character distance mismatch
            report.addLineMismatch(lineDiff(pos, levenshtein.apply(eRowNormalized, aRowNormalized), eRow, aRow)
                                       .maligned(pos, aPos + 1));
        }

        // scan all extra lines in ACTUAL
        if (actualRows.size() > expectedRows.size()) {
            for (int i = aLastParsed + 1; i < actualRows.size(); i++) {
                report.addLineMismatch(lineExtraFound(i + 1, null, actualRows.get(i)));
            }
        }

        return compareMode == DIFF ? createDiff(diffVar, report) : failContentComparison(report);
    }

    protected void logComparisonReport(String caption, FileComparisonReport report, String type) {
        if (StringUtils.isBlank(type) || report == null || !report.hasMismatch()) { return; }

        boolean genLogFile = context.getBooleanData(GEN_COMPARE_LOG, getDefaultBool(GEN_COMPARE_LOG));
        if (StringUtils.equals(type, COMPARE_LOG_PLAIN) && !genLogFile) { return; }

        boolean genJsonFile = context.getBooleanData(GEN_COMPARE_JSON, getDefaultBool(GEN_COMPARE_JSON));
        if (StringUtils.equals(type, COMPARE_LOG_JSON) && !genJsonFile) { return; }

        String stats = null;
        if (report.getMatchCount() != 0 || report.getMismatchCount() != 0) {
            stats = "Lines matched: " + report.getMatchCount() + ", " +
                    "Lines mismatched: " + report.getMismatchCount() + ". " +
                    "Match percentage: " + IoCommand.formatPercent(report.getMatchPercent());
        }

        String output = null;
        if (StringUtils.equals(type, COMPARE_LOG_PLAIN)) { output = report.toPlainTextReport(); }
        if (StringUtils.equals(type, COMPARE_LOG_JSON)) { output = report.toJsonReport(); }
        if (StringUtils.isEmpty(output)) { ConsoleUtils.error("logComparisonReport(): INVALID REPORT TYPE " + type); }

        if (stats != null) { caption += lineSeparator() + stats; }
        caption += " (click link on the right for details)";

        addOutputAsLink(caption, output, type);
    }

    protected int scanForMatchingRow(String matchTo, List<String> matchFrom, int startFrom) {
        // hopeless
        if (CollectionUtils.isEmpty(matchFrom) || CollectionUtils.size(matchFrom) <= startFrom) { return -1; }
        if (matchTo == null) { matchTo = ""; }

        // first we match forward
        for (int i = startFrom; i < matchFrom.size(); i++) {
            if (simpleMatch(matchTo, matchFrom.get(i))) { return i; }
        }
        return startFrom;
    }

    protected static boolean simpleMatch(String expected, String actual) {
        return StringUtils.equals(expected, actual) ||
               StringUtils.equalsIgnoreCase(StringUtils.deleteWhitespace(expected),
                                            StringUtils.deleteWhitespace(actual));
    }

    protected StepResult failContentComparison(FileComparisonReport results) {
        return failContentComparison("The EXPECTED and ACTUAL differ in size and/or line count", results);
    }

    protected StepResult failContentComparison(String message, FileComparisonReport results) {
        if (results.hasMismatch()) {
            logComparisonReport(message, results, "log");
            logComparisonReport(message, results, "json");
        }

        return StepResult.fail(message);
    }

    protected StepResult passContentComparison(String message, FileComparisonReport results) {
        if (results != null) {
            logComparisonReport(message, results, "log");
            logComparisonReport(message, results, "json");
        }

        return StepResult.success(message);
    }

    protected StepResult createDiff(String var, FileComparisonReport report) {
        requiresValidVariableName(var);
        if (report == null || !report.hasMismatch()) { return StepResult.success("No diff found"); }
        updateDataVariable(var, report.showDiffs());
        return StepResult.success("File diffs are saved to '" + var + "'");
    }

    @Nullable
    private String adjustEol(String content) {
        if (StringUtils.isEmpty(content)) { return content; }

        String eol;
        String eolConfig = context.getStringData(OPT_IO_EOL_CONFIG, getDefault(OPT_IO_EOL_CONFIG));
        switch (eolConfig) {
            case EOL_CONFIG_AS_IS: {
                eol = "";
                break;
            }
            case EOL_CONFIG_PLATFORM: {
                eol = lineSeparator();
                break;
            }
            case EOL_CONFIG_UNIX: {
                eol = "\n";
                break;
            }
            case EOL_CONFIG_WINDOWS: {
                eol = "\r\n";
                break;
            }
            default: {
                eol = "";
                break;
            }
        }

        ConsoleUtils.log("setting end-of-line character as " +
                         (StringUtils.isEmpty(eol) ?
                          "is" :
                          StringUtils.replace(StringUtils.replace(eol, "\n", "\\n"), "\r", "\\r")));

        if (StringUtils.isNotEmpty(eol)) {
            content = StringUtils.replace(content, "\r\n", TMP_EOL);
            content = StringUtils.replace(content, "\n", TMP_EOL);
            content = StringUtils.replace(content, TMP_EOL, eol);
        }

        return content;
    }

    private boolean isMSOfficeTempFile(String filepath) {
        if (StringUtils.isBlank(filepath)) { return true; }

        String filename = StringUtils.substringAfterLast(filepath, separator);
        if (!StringUtils.startsWith(filename, "~")) { return false; }

        String ext = StringUtils.substringAfterLast(filename, ".");
        return MS_OFFICE_FILE_EXT.contains(StringUtils.lowerCase(ext));
    }

    private StepResult _saveDiff(String var, String expected, String actual) {
        requiresValidVariableName(var);
        requires(FileUtil.isFileReadable(expected, 1), "invalid 'expected' file", expected);
        requires(FileUtil.isFileReadable(actual, 1), "invalid 'actual' file", actual);

        File expectedFile = new File(expected);
        File actualFile = new File(actual);

        boolean includeMoved = Boolean.parseBoolean(context.getStringData(COMPARE_INCLUDE_MOVED));
        boolean includeDeleted = Boolean.parseBoolean(context.getStringData(COMPARE_INCLUDE_DELETED));
        boolean includeAdded = Boolean.parseBoolean(context.getStringData(COMPARE_INCLUDE_ADDED));

        // since we are logging line number, order is important
        Map<String, String> logs = new TreeMap<>();
        boolean match = true;
        boolean found = false;

        try {
            String[] expectedLines = StringUtils.split(FileUtils.readFileToString(expectedFile, DEF_CHARSET), "\n");
            String[] actualLines = StringUtils.split(FileUtils.readFileToString(actualFile, DEF_CHARSET), "\n");

            // todo: need tests.. what if current file has less lines? NPE? IOBE?
            for (int i = 0; i < expectedLines.length; i++) {
                if (actualLines.length > i) {
                    if (expectedLines[i].equals(actualLines[i])) {
                        found = true;
                    } else {
                        for (int x = 0; x < actualLines.length; x++) {
                            if (expectedLines[i].equals(actualLines[x])) {
                                String message =
                                    "Moved (From line " + (i + 1) + " to " + (x + 1) + "): " + expectedLines[i];
                                logs.put((i + 1) + "", message);
                                if (includeMoved) { error(message); }
                                match = false;
                                found = true;
                                break;
                            }
                        }
                    }
                }

                if (!found) {
                    if (includeDeleted) {
                        String message = "Removed from baseline (line " + (i + 1) + "): " + expectedLines[i];
                        logs.put((i + 1) + "", message);
                        error(message);
                        match = false;
                    }
                }

                // reset
                found = false;
            }

            for (int i = 0; i < actualLines.length; i++) {
                if (expectedLines.length > i) {
                    if (actualLines[i].equals(expectedLines[i])) {
                        found = true;
                    } else {
                        for (String ln : expectedLines) {
                            if (actualLines[i].equals(ln)) {
                                // todo: need test. why don't we log such mismatch?
                                found = true;
                                break;
                            }
                        }
                    }
                } else {
                    for (String ln : expectedLines) {
                        if (actualLines[i].equals(ln)) {
                            found = true;
                            break;
                        }
                    }
                }

                if (!found) {
                    if (includeAdded) {
                        String message = "Inserted into current (line " + (i + 1) + "): " + actualLines[i];
                        logs.put((i + 1) + "", message);
                        error(message);
                    }
                    match = false;
                }

                // reset
                found = false;
            }

            log("Files are " + (match ? "identical" : "different"));
        } catch (Exception e) {
            return StepResult.fail(e.getMessage());
        }

        context.setData(var, logs);
        return match ? StepResult.success() : StepResult.fail("Files are different");
    }

    static {
        PERCENT_FORMAT.setMaximumFractionDigits(2);
        PERCENT_FORMAT.setMinimumFractionDigits(2);
    }
}
