package org.nexial.core.tools;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.FileUtil.LineFilter;
import org.nexial.commons.utils.TextUtils;
import org.springframework.util.CollectionUtils;

import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static org.nexial.core.NexialConst.ExitStatus.RC_BAD_CLI_ARGS;

public class LogFileParser {
    private static final String FORMAT_LOG = "yyyy-MM-dd HH:mm:ss,SSS";
    private static final String PIPE_SEP = "|";
    private static final String SPACE_SEP = " ";

    private static Options cmdOptions = new Options();
    private static String targetLogFilePath;
    private static String newLogFilePath;
    private static String regexCriteria = "";
    private static List<String> contentToList;

    // anonymous inner class for sorting.
    private static Comparator<String> comparator = (o1, o2) -> {
        String threadNum1 = StringUtils.substringBetween(o1, "Thread-", PIPE_SEP);
        String threadNum2 = StringUtils.substringBetween(o2, "Thread-", PIPE_SEP);
        return Integer.valueOf(threadNum1).compareTo(Integer.valueOf(threadNum2));
    };

    enum LogRangeRegex {
        DESKTOP("desktop.*-\\sexecuting.*", "desktop.*-\\s(PASS|FAIL).*"),
        RDBMS("rdbms.*-\\sexecuting.*", "rdbms.*-\\s(PASS|FAIL).*"),
        WS("ws.*-\\sExecuting request.*", "ws.*-\\sExecuted request.*"),
        ANY(".*-\\sexecuting.*", ".*-\\s(PASS|FAIL).*");

        private static final String REGEX_PATTERN = "\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2},\\d{3}" +
                                                    "\\|I\\|Thread-.*\\|.*\\|.*\\|.*\\|#\\s{0,2}\\d{1,3}\\|";
        private String regexStart;
        private String regexEnd;

        LogRangeRegex(String regexStart, String regexEnd) {
            this.regexStart = REGEX_PATTERN + regexStart;
            this.regexEnd = REGEX_PATTERN + regexEnd;
        }

        public String getRegexStart() {
            return regexStart;
        }

        public String getRegexEnd() {
            return regexEnd;
        }

        // todo : add more cases depending on required response field
        public String getContent(String logs) {
            String content = "";
            switch (this) {
                case WS:
                    content = getResponseLogContent(logs);
            }
            return content;
        }
    }

    public static void main(String[] args) throws Exception {
        initOptions();
        LogFileParser parser = newInstance(args);
        if (parser == null) { System.exit(RC_BAD_CLI_ARGS); }

        parser.logFileParser();
    }

    private static void initOptions() {
        cmdOptions.addOption("t", true, "[REQUIRED] Location of the log file to be parsed.");
        cmdOptions.addOption("s", true, "[REQUIRED] Location for new log file");
        cmdOptions.addOption("c", true, "[OPTIONAL] Type of logs to track. Possible values are desktop, " +
                                        "rdbms, ws and all(default). Other than these values" +
                                        " considered as default i.e. all");
    }

    private void parseCLIOptions(CommandLine cmd) {
        if (!cmd.hasOption("t")) {
            throw new RuntimeException("[Required] Location of log file to be parsed not given");
        }
        if (!cmd.hasOption("s")) { throw new RuntimeException("[Required] Location for new file not given"); }
        if (cmd.hasOption("c")) { regexCriteria = cmd.getOptionValue("c"); }

        targetLogFilePath = cmd.getOptionValue("t");
        newLogFilePath = cmd.getOptionValue("s");
    }

    private static LogFileParser newInstance(String[] args) {
        try {
            LogFileParser parser = new LogFileParser();
            parser.parseCLIOptions(new DefaultParser().parse(cmdOptions, args));
            return parser;
        } catch (Exception e) {
            System.err.println("\nERROR: " + e.getMessage() + "\n");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(LogFileParser.class.getName(), cmdOptions, true);
            return null;
        }
    }

    private void logFileParser() throws Exception {
        File targetFile = new File(targetLogFilePath);
        if (!FileUtil.isFileReadable(targetFile.getAbsolutePath())) {
            throw new IOException(String.format("file %s is a directory or not readable", targetLogFilePath));
        }

        File newLogFile = new File(newLogFilePath);
        if (newLogFile.exists()) {
            FileUtils.deleteQuietly(newLogFile);
            System.out.println("Deleted existing log file " + newLogFilePath);
        }

        LogRangeRegex logRangeRegex;
        switch (regexCriteria) {
            case "desktop":
                logRangeRegex = LogRangeRegex.DESKTOP;
                break;
            case "rdbms":
                logRangeRegex = LogRangeRegex.RDBMS;
                break;
            case "ws":
                logRangeRegex = LogRangeRegex.WS;
                break;
            default:
                logRangeRegex = LogRangeRegex.ANY;
        }

        LineFilter<String> lineFilter = line -> line.matches(logRangeRegex.getRegexStart()) ||
                                                line.matches(logRangeRegex.getRegexEnd());
        contentToList = FileUtil.filterAndTransform(targetFile, lineFilter, line -> line);

        if (CollectionUtils.isEmpty(contentToList)) {
            System.out.println("There are no logs matching the criteria");
            return;
        }

        contentToList.sort(comparator);

        writeLogFile(newLogFile, logRangeRegex);
        System.out.println("Successfully created new log file " + newLogFile.getAbsolutePath());
    }

    private static void writeLogFile(File newLogFile, LogRangeRegex logRangeRegex) throws IOException {
        for (int i = 0; i < contentToList.size(); i++) {
            String requestLogs = contentToList.get(i);
            List<String> requestLogsToList = TextUtils.toList(requestLogs, PIPE_SEP, true);
            String requestThreadName = requestLogsToList.get(2);
            String requestRowIndex = requestLogsToList.get(6);

            for (int j = i + 1; j < contentToList.size(); j++) {
                String responseLogs = contentToList.get(j);
                List<String> responseLogsToList = TextUtils.toList(responseLogs, PIPE_SEP, true);
                String responseThreadName = responseLogsToList.get(2);
                String responseRowIndex = responseLogsToList.get(6);

                if (requestLogs.matches(logRangeRegex.getRegexStart()) &&
                    responseLogs.matches(logRangeRegex.getRegexEnd()) &&
                    StringUtils.equals(requestThreadName, responseThreadName) &&
                    StringUtils.equals(requestRowIndex, responseRowIndex)) {

                    String startTime = requestLogsToList.get(0);
                    String endTime = responseLogsToList.get(0);
                    Long elapsedTime = DateUtility.formatTo(endTime, FORMAT_LOG) -
                                       DateUtility.formatTo(startTime, FORMAT_LOG);
                    String executionScript = requestLogsToList.get(3);

                    String content = startTime + PIPE_SEP + endTime + PIPE_SEP + elapsedTime + PIPE_SEP +
                                     requestThreadName + PIPE_SEP + executionScript;
                    String extraContent = logRangeRegex.getContent(responseLogsToList.get(7));
                    content = content + (!StringUtils.isEmpty(extraContent) ? PIPE_SEP : "") + extraContent;
                    writeToFile(newLogFile, content);
                    break;
                }
            }
        }
    }

    private static String getResponseLogContent(String logs) {
        String responseMsg = StringUtils.substringAfter(logs, "Executed request");

        String httpMethod = StringUtils.split(responseMsg, SPACE_SEP)[0];
        String url = StringUtils.split(responseMsg, SPACE_SEP)[1];
        String statusCode = StringUtils.split(responseMsg, SPACE_SEP)[4];
        String statusText = StringUtils.split(responseMsg, SPACE_SEP)[5];
        return httpMethod + PIPE_SEP + url + PIPE_SEP + statusCode + SPACE_SEP + statusText;
    }

    private static void writeToFile(File targetFile, String content) throws IOException {
        String lineSeparator = System.lineSeparator();
        FileUtils.writeStringToFile(targetFile, content + lineSeparator, DEF_FILE_ENCODING, true);
    }
}


