package org.nexial.core.tms.spi;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.nexial.core.Nexial;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.tms.model.TmsSuite;
import org.nexial.core.tms.model.TmsTestCase;
import org.nexial.core.utils.InputFileUtils;

import static org.nexial.core.NexialConst.Data.DEF_OPEN_EXCEL_AS_DUP;
import static org.nexial.core.tms.TmsConst.*;

/**
 * Update the nexial plan and nexial script file with test and suite ids after suite operation
 */
public class UpdateTestFiles {
    public static Map<String, JSONObject> testCaseMap = new HashMap<>();

    /**
     * Update the plan file with the suite id and the scenarios in the script file mentioned in the subplan with the
     * corresponding test case ids
     *
     * @param testPlanPath the path of the test plan
     * @param suite suite details
     * @param subplan the subplan name
     */
    protected static void updatePlanFile(String testPlanPath, TmsSuite suite, String subplan) {
        for (String step : ReadPlan.scriptToStep.keySet()) {
            String scriptFile = ReadPlan.scriptToStep.get(step);
            updateScriptFile(scriptFile, suite, false);
        }
        writeToPlanFile(testPlanPath, subplan, suite);
    }

    /**
     * Update the scenarios in the script file with the suite id and test case ids
     *
     * @param scriptFile the path of the script file
     * @param suite suite details
     * @param isScript boolean, true if this method is called after script suite import/update, false otherwise
     */
    protected static void updateScriptFile(String scriptFile, TmsSuite suite, boolean isScript) {
        Excel script = InputFileUtils.resolveValidScript(scriptFile);
        if (script == null){
            System.err.println("Unable to read script file: " + scriptFile);
            System.exit(-1);
        }
        for (String scenario : testCaseMap.keySet()) {
            String worksheetName;
            String fileType;
            if(isScript){
                worksheetName = scenario;
                fileType = SCRIPT;
            }else {
                worksheetName = StringUtils.substringBetween(scenario, "/");
                fileType = PLAN;
            }
            Worksheet worksheet = script.worksheet(worksheetName);
            TmsTestCase tmsTestScenario = new TmsTestCase(worksheet);
            String testId = tmsTestScenario.getTmsIdRef();
            String updatedTestId =
                    getUpdatedTestId(testId, suite.getId(), testCaseMap.get(scenario).get("id").toString(), fileType);
            tmsTestScenario.setTmsIdRef(updatedTestId);
            tmsTestScenario.writeToFile();
        }
        try {
            script.save();
            FileUtils.copyFile(script.getFile(), new File(scriptFile));
            script.close();
        } catch (IOException e){
            System.err.println("Unable to write to the excel file: " + e.getMessage());
            System.exit(-1);
        }
    }

    /**
     * Update the current suite id and test id in the scenario with the new suite id and test id and return the updated
     * ids
     *
     * @param testId the current contents of the "test id" cell in the scenario
     * @param suiteId suite id
     * @param newId the test case corresponding to the scenario
     * @param fileType type of file, "plan" or "script"
     * @return the updated test id
     */
    private static String getUpdatedTestId(String testId, String suiteId, String newId, String fileType) {
        if (StringUtils.contains(testId, suiteId)) {
            if (fileType.equals("Script")) {
                testId = StringUtils.replace(testId, StringUtils.substringBetween(suiteId, "\n"),
                                             suiteId + " :: " + newId + "\n");
            }
        } else {
            testId = testId + suiteId + " :: " + newId + "\n";
        }
        return testId;
    }

    /**
     * Write the suite id into the plan file inside the subplan specified
     *
     * @param testPlanPath the path of the test plan
     * @param subplan the subplan name
     * @param suite the suite details
     */
    private static void writeToPlanFile(String testPlanPath, String subplan, TmsSuite suite) {
        Nexial nexial = new Nexial();
        File testPlanFile = new File(testPlanPath);
        Excel excel = null;
        try {
             excel = new Excel(testPlanFile, DEF_OPEN_EXCEL_AS_DUP, false);
        } catch (IOException e){
            System.err.println("Unable to open the excel file: " + e.getMessage());
            System.exit(-1);
        }
        List<Worksheet> plans = nexial.retrieveValidPlans(null, excel);
        Worksheet plan = plans.stream().filter(p -> p.getName().equals(subplan)).findFirst().get();
        TmsTestCase tmsTestScenario = new TmsTestCase(plan);
        tmsTestScenario.setTmsIdRef(suite.getId());
        tmsTestScenario.writeToFile();
        try {
            excel.save();
            FileUtils.copyFile(plan.getFile(), testPlanFile);
            excel.close();
        }catch (IOException e){
            System.err.println("Unable to write to the excel file: " + e.getMessage());
            System.exit(-1);
        }
    }
}
