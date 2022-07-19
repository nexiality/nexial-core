/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.tms.spi;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.tms.model.TmsSuite;
import org.nexial.core.tms.model.TmsTestCase;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.InputFileUtils;

import static org.nexial.core.NexialConst.Data.DEF_OPEN_EXCEL_AS_DUP;
import static org.nexial.core.NexialConst.NL;
import static org.nexial.core.tms.spi.ReadPlan.scriptToStep;

/**
 * Update the nexial plan and nexial script file with test and suite ids after suite operation
 */
public class UpdateTestFiles {

    /**
     * Update the plan file with the suite id and the scenarios in the script file mentioned in the subplan
     * with the corresponding test case ids.
     *
     * @param testPath plan file path to update
     * @param suite    suite details
     * @param subplan  the subplan name
     */
    protected static void updatePlanFile(String testPath, TmsSuite suite, String subplan) throws TmsException{
        deduplicateValues(scriptToStep).forEach((row, scriptFile) -> updateScriptFile(scriptFile, suite, false));
        writeToPlanFile(testPath, subplan, suite);
    }

    /**
     * Update the scenarios in the script file with the suite id and test case ids.
     *
     * @param scriptFile the path of the script file
     * @param suite      suite details
     * @param isScript   Boolean, if true file type of suite import/update is script otherwise plan
     */
    protected static void updateScriptFile(String scriptFile, TmsSuite suite, boolean isScript) {
        Map<String, String> updatedTestIds = new LinkedHashMap<>();
        Excel script = null;
        try {
            ConsoleUtils.log("Updating test file: " + scriptFile);
            script = InputFileUtils.resolveValidScript(scriptFile);
            if (script == null) {
                ConsoleUtils.error("Unable to read script file: " + scriptFile);
                if (MapUtils.isNotEmpty(updatedTestIds)) { logChanges(scriptFile, updatedTestIds); }
                return;
            }
            String scriptName1 = FilenameUtils.removeExtension(script.getFile().getName());

            for (Entry<String, String> entry : suite.getTestCases().entrySet()) {
                String scenario = entry.getKey();
                String newTestId = entry.getValue();
                String scriptName = StringUtils.substringBefore(scenario, "/");
                if (!isScript && !StringUtils.equals(scriptName1, scriptName)) { continue; }
                String worksheetName = isScript ? scenario : StringUtils.substringBetween(scenario, "/");
                Worksheet worksheet = script.worksheet(worksheetName);
                TmsTestCase tmsTestScenario = new TmsTestCase(worksheet);
                String oldId = tmsTestScenario.getTmsIdRef();
                String updatedTestId = getUpdatedTestId(suite.getId(), oldId, newTestId, isScript);
                tmsTestScenario.setTmsIdRef(updatedTestId);
                tmsTestScenario.writeToFile();
                updatedTestIds.put(worksheetName, updatedTestId);
            }
            script.save();
            FileUtils.copyFile(script.getFile(), new File(scriptFile));
            ConsoleUtils.log("Updated test file: " + scriptFile);
            script.close();
        } catch (IOException e) {
            ConsoleUtils.log("");
            ConsoleUtils.error("Unable to update to excel file: " + e.getMessage());
            if (MapUtils.isNotEmpty(updatedTestIds)) { logChanges(scriptFile, updatedTestIds); }
        } finally {
            if (script != null) { try { script.close(); } catch (IOException ex) {
                ConsoleUtils.log("Unable to close script excel");
            } }
        }
    }

    /**
     * Update the current suite id and test id in the scenario with the new suite id and test id and
     * return the updated ids.
     *
     * @param oldId    the current contents of the "test id" cell in the scenario
     * @param suiteId  suite id
     * @param newId    the test case corresponding to the scenario
     * @param isScript boolean true if type of file is "script" otherwise "plan"
     * @return the updated test id
     */
    private static String getUpdatedTestId(String suiteId, String oldId, String newId, boolean isScript) {
        if (StringUtils.contains(oldId, suiteId)) {
            if (isScript) {
                oldId = StringUtils.replace(oldId, StringUtils.substringBetween(suiteId, NL),
                                            StringUtils.join(suiteId, "/", newId,NL));
            }
        } else {
            oldId += StringUtils.join(suiteId, "/", newId,NL);
        }
        return oldId;
    }

    /**
     * Write the suite id into the plan file in the subplan specified.
     *
     * @param subplan the subplan name
     * @param suite   the suite details
     */
    private static void writeToPlanFile(String testPath, String subplan, TmsSuite suite) throws TmsException {
        File testPlanFile = new File(testPath);
        Excel excel = null;
        try {
            excel = new Excel(testPlanFile, DEF_OPEN_EXCEL_AS_DUP, false);
            List<Worksheet> subplans = InputFileUtils.retrieveValidPlanSequence(excel);
            Optional<Worksheet> first = subplans.stream().filter(p -> p.getName().equals(subplan)).findFirst();
            if(!first.isPresent()) {
               /* ConsoleUtils.error("Unable to find subplan '" + subplan + "' inside the plan file '" + testPath + "'.");
                System.exit(-1);*/
                throw new TmsException("Unable to find subplan '" + subplan + "' inside the plan file '" + testPath + "'.");
            }
            Worksheet sheet = first.get();
            TmsTestCase tmsTestScenario = new TmsTestCase(sheet);
            tmsTestScenario.setTmsIdRef(suite.getId());
            tmsTestScenario.writeToFile();
            excel.save();
            FileUtils.copyFile(sheet.getFile(), testPlanFile);
        } catch (IOException e) {
            ConsoleUtils.error("Unable to read/write to the plan file: " + e.getMessage());
            ConsoleUtils.log("");
            ConsoleUtils.error("Please update the suite id in (Column H) of " + NL +
                               " Subplan   :: '" + subplan + "'" + NL +
                               " Plan File ::'" + testPath + "'" + NL +
                               " Suite ID  :: " + suite.getId());
            ConsoleUtils.log("");
        } finally {
            if (excel != null) {
                try { excel.close(); } catch (IOException e) { ConsoleUtils.error("Unable to close the plan excel"); }
            }
        }
    }

    /**
     * Remove duplicate entries of script file in case of plan.
     *
     * @param map Map of duplicated script values
     * @return map of unique scripts values
     */
    private static Map<Integer, String> deduplicateValues(Map<Integer, String> map) {
        Map<String, Integer> inverse = map.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getValue,
            Map.Entry::getKey,
            Math::max)); // take the highest key on duplicate values

        return inverse.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    }

    private static void logChanges(String scriptFile, Map<String, String> updatedTestIds) {
        ConsoleUtils.error("");
        ConsoleUtils.error("Please update 'test id column(H) of " +  scriptFile + "' with following tms ref:");
        ConsoleUtils.error(StringUtils.rightPad("-", 90, "-"));
        ConsoleUtils.error(StringUtils.rightPad("Worksheet", 35, " ") + "| TMS Reference ");
        ConsoleUtils.error(StringUtils.rightPad("-", 90, "-"));

        updatedTestIds.forEach((sheetName, testId) -> {
            ConsoleUtils.error(StringUtils.rightPad(sheetName, 35, " ") + "| " +
                    StringUtils.replace(testId.trim(), NL, "\\n"));
            ConsoleUtils.error(StringUtils.rightPad("-", 90, "-"));
        });
        ConsoleUtils.error("");
    }
}
