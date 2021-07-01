package org.nexial.core.tms.tools;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.nexial.core.tms.model.TestFile;
import org.nexial.core.tms.model.TmsTestFile;
import org.nexial.core.tms.spi.testrail.TestRailOperations;
import org.nexial.core.utils.InputFileUtils;

import static org.nexial.core.tms.TmsConst.*;
import static org.nexial.core.tms.spi.TmsMetaJson.*;

/**
 * Closes all test runs for the suite associated with the Nexial test file passed in as the argument
 */
public class CloseTestRun {

    public static void main(String[] args) throws ParseException {
        CommandLine cmd = new DefaultParser().parse(initCmdOptions(), args);

        String filepath = "";
        String subplan = "";

        if (cmd.hasOption(SCRIPT) && cmd.hasOption(PLAN)) {
            System.out.println("Only one type of test file is allowed per input");
            System.exit(-1);
        }
        if (cmd.hasOption(SCRIPT)) {
            filepath = cmd.getOptionValue(SCRIPT);
            if (InputFileUtils.isValidScript(filepath)) {
                System.out.println("Invalid test script - " + filepath);
                System.exit(-1);
            }
        } else if (cmd.hasOption(PLAN) && cmd.hasOption(SUBPLAN)) {
            filepath = cmd.getOptionValue(PLAN);
            subplan  = cmd.getOptionValue(SUBPLAN);
            if (!InputFileUtils.isValidPlanFile(filepath)) {
                System.out.println(
                        "specified test plan (" + filepath + ") is not readable or does not contain valid format.");
            }
        } else {
            System.out.println(
                    "The location of the Nexial script/plan file is required. In case of Plan file, subplan is required");
            System.exit(-1);
        }

        if (StringUtils.isNotEmpty(subplan)) {
            System.out.println("Input Plan test file: " + filepath);
            System.out.println("Subplan Name :" + subplan);
        } else {
            System.out.println("Input Script test file: " + filepath);
        }

        // retrieve the project.tms meta file
        TmsTestFile tmsTestFile = getJsonEntryForFile(filepath, subplan);
        TestFile file = tmsTestFile.getFile();
        String suiteId = file.getSuiteId();
        CloseTestRun closeTestRun = new CloseTestRun();
        closeTestRun.closeActiveRuns(suiteId, tmsTestFile.getProjectId());
    }


    /**
     * Command line options
     */
    private static Options initCmdOptions() {
        Options cmdOptions = new Options();
        cmdOptions.addOption(SCRIPT, true, "[REQUIRED] if -" + PLAN + " is missing] The fully qualified " +
                                           "path of the test script.");
        cmdOptions.addOption(PLAN, true, "[REQUIRED if -" + SCRIPT + " is missing] The fully qualified path of a " +
                                         "test plan.");
        cmdOptions.addOption(SUBPLAN, true, "[REQUIRED] if -" + PLAN + "is present.The name of the test plan");
        return cmdOptions;
    }

    /**
     * Close any active test runs associated with the provided suite id
     *
     * @param suiteId the suite id associated with the passed in file
     * @param projectId the project id of the current project
     */
    public void closeActiveRuns(String suiteId, String projectId) {
        TestRailOperations testRail = new TestRailOperations(projectId);
        List<String> activeRunIds = getActiveTestRuns(suiteId, testRail);
        for (String activeRun : activeRunIds) {
            testRail.closeRun(activeRun);
        }
    }

    /**
     * Retrieve the ids of the active test runs associated with the provided suite id
     *
     * @param suiteId the suite id associated with the passed in file
     * @param testRail object of {@link TestRailOperations} class containing the API client
     * @return List of ids of active test runs for the current suite
     */
    @NotNull
    private List<String> getActiveTestRuns(String suiteId, TestRailOperations testRail) {
        List<String> activeRunIds = new ArrayList<>();
        JSONArray activeRuns = testRail.getExistingActiveRuns(suiteId);
        if(CollectionUtils.isEmpty(activeRuns)){
            System.out.println("No active test runs found");
            System.exit(-1);
        }
        System.out.println("Active runs found: ");
        for (Object activeRun : activeRuns) {
            if (activeRun instanceof JSONObject) {
                System.out.println(
                        "Test Run name: " + ((JSONObject) activeRun).get(NAME) + "\tTest Run id: " +
                        ((JSONObject) activeRun).get(ID));
                activeRunIds.add(((JSONObject) activeRun).get(ID).toString());
            }
        }
        return activeRunIds;
    }
}
