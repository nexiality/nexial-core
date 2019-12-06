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

package org.nexial.core.model;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.nexial.commons.utils.CollectionUtil;
import org.nexial.core.CommandConst;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.InputFileUtils;

import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.Exec.POST_EXEC_MAIL_TO;
import static org.nexial.core.NexialConst.Iteration.*;
import static org.nexial.core.NexialConst.*;

/**
 * This represents a set of data usable for a specific test execution, which may contain multiple scenarios.
 * <p/>
 *
 * Within the context of Nexial, there are 3 types of data definitions:
 * <ol>
 * <li>Predefined data that remains constant throughout an entire test execution (which could be multiple scenarios and
 * or iterations).  Such data names are prefixed with <code>nexial.scope.</code>.  The value of such data, once parsed,
 * will remain unchanged as not to alter the execution context. For example:
 * <pre>
 *     nexial.scope.iteration=4                     # define that nexial should loop the specified test 4 times.
 *     nexial.scope.fallbackToPrevious=true         # define that missing data in current iteration should resolve to previous value.
 *     nexial.mailTo=my_email@mycompany.com         # define that the test result should be sent as an email to the specified address.
 * </pre></li>
 * <li>Predefined data that could be altered between iterations. Such data names are prefixed with <code>nexial.</code>
 * The value of such data could be altered between iteration, either via data sheet or via variable-related commands, and
 * thus alter the behavior of a test iteration. For example:
 * <pre>
 *     nexial.delayBetweenStepsMs=1200              # define the wait time between command execution.
 *     nexial.failFast=true                         # continue or terminate test execution if a failure is observed.
 *     nexial.textDelim                             # define the delimiter character to transform text string into a list.
 * </pre>
 * </li>
 * <li>User-defined data that could be altered between iterations.  Such data names are anything not prefixed with
 * <code>nexial.</code>  Nexial users are free to define any names or values.  Using <code>nexial.scope.iteration</code>
 * and <code>nexial.scope.fallbackToPrevious</code> one can control how Nexial handle data values between iterations.</li>
 * </ol>
 *
 * It is noteworthy that non-scoped data can be altered as a test execution progresses.  The altered data would impacting
 * the subsequent test scenarios/cases/steps.  The progressive data management is by design. The ability to "remember" data
 * states would allow a more meaningful and dynamic way to execute tests, esp. for tests that spread across multiple
 * scenarios. However data changes and the transitive behavior will not persists between test execution.
 * <p/>
 *
 * In addition, it is possible to specify multiple data sheets where the latter overrides the former.  All the data sheets
 * are read and parsed in the beginning of a test execution.  Hence it is possible to alter the behavior of a test without
 * modifying existing test scripts or test data.
 * <p/>
 *
 * <b>REMEMBER: DEFAULTS ARE TRANSITIVE BUT <u>THE LAST ONE WINS!</u></b><br/>
 * <b>REMEMBER: PARSING STOPS WHEN AN EMPTY ROW (actually, just empty cell in Column A) IS ENCOUNTERED</b>
 * <p/>
 */
public class TestData {
    private File dataFile;
    private List<String> dataSheetNames;
    private Map<String, String> scopeSettings = new HashMap<>();
    private Map<String, List<String>> dataMap = new HashMap<>();
    private Map<String, List<String>> defaultDataMap = new HashMap<>();
    private Map<String, List<String>> runtimeDataMap = new HashMap<>();

    public TestData(Excel excel, List<String> dataSheetNames) {
        assert excel != null && excel.getFile() != null && CollectionUtils.isNotEmpty(dataSheetNames);

        dataFile = excel.getFile();
        this.dataSheetNames = dataSheetNames;

        List<Worksheet> validDataSheets = InputFileUtils.filterValidDataSheets(excel);
        if (CollectionUtils.isEmpty(validDataSheets)) { return; }

        // #default is always the first one; default data sheet IS ALWAYS overridden by other data sheets
        validDataSheets.forEach(validDataSheet -> {
            if (StringUtils.equals(validDataSheet.getName(), SHEET_DEFAULT_DATA)) { collectData(validDataSheet); }
        });

        this.dataSheetNames.forEach(targetSheetName -> validDataSheets.forEach(validDataSheet -> {
            if (!StringUtils.equals(targetSheetName, SHEET_DEFAULT_DATA) &&
                StringUtils.equals(validDataSheet.getName(), targetSheetName)) {
                collectData(validDataSheet);
            }
        }));

        // to be added/displayed in execution output #summary
        System.setProperty(SCRIPT_REF_PREFIX + DATA_SHEETS,
                           validDataSheets.stream()
                                          .filter(validSheet -> validSheet.getName().equals(SHEET_DEFAULT_DATA) ||
                                                                dataSheetNames.contains(validSheet.getName()))
                                          .map(Worksheet::getName)
                                          .distinct()
                                          .collect(Collectors.joining(", ")));
        System.setProperty(SCRIPT_REF_PREFIX + DATA_FILE, dataFile.getName());
    }

    public String getMailTo() { return getSetting(POST_EXEC_MAIL_TO); }

    public int getIteration() {
        String iteration = getSetting(ITERATION);
        return NumberUtils.isDigits(iteration) ?
               NumberUtils.toInt(iteration) :
               NumberUtils.toInt(StringUtils.substringBefore(iteration, "-"));
    }

    public IterationManager getIterationManager() { return IterationManager.newInstance(getSetting(ITERATION)); }

    public boolean isFallbackToPrevious() { return getSettingAsBoolean(FALLBACK_TO_PREVIOUS); }

    public int getSettingAsInt(String name) { return NumberUtils.toInt(getSetting(name)); }

    public double getSettingAsDouble(String name) { return NumberUtils.toDouble(getSetting(name)); }

    public boolean getSettingAsBoolean(String name) { return BooleanUtils.toBoolean(getSetting(name)); }

    public List<String> getSettingAsList(String name) { return Arrays.asList(StringUtils.split(getSetting(name))); }

    /**
     * favor system property over scope settings.  that way we can create just-in-time override from commandline.
     *
     * Order:
     * <ol>
     * <li>System property</li>
     * <li>scope setting (i.e. <code>nexial.scope.*</code>, <code>nexial.project</code>,
     * <code>nexial.projectBase</code>, <code>nexial.outBase</code>, <code>nexial.scriptBase</code>,
     * <code>nexial.dataBase</code>, <code>nexial.planBase</code>)</li>
     * <li>default scope settings (@link #SCOPE_SETTING_DEFAULTS)</li>
     * </ol>
     */
    public String getSetting(String name) {
        String value = System.getProperty(name);
        if (value == null) {
            value = scopeSettings.computeIfAbsent(name, k -> SCOPE_SETTING_DEFAULTS.get(name));
        } else {
            scopeSettings.put(name, value);
        }

        return value;
    }

    public Map<String, String> getAllSettings() { return scopeSettings; }

    public Map<String, String> getAllValue(int iteration) {
        Map<String, String> data = new HashMap<>();

        // not sure why... can't make lambda work in this case
        // dataMap.forEach((name, values) -> data.put(name, getValue(iteration, name)));

        List<String> dataKeys = CollectionUtil.toList(dataMap.keySet());
        for (String dataKey : dataKeys) { data.put(dataKey, getValue(iteration, dataKey)); }
        return data;
    }

    public int getIntValue(int iteration, String name) { return NumberUtils.toInt(getValue(iteration, name)); }

    public double getDoubleValue(int iteration, String name) { return NumberUtils.toDouble(getValue(iteration, name)); }

    public boolean getBooleanValue(int iteration, String name) {
        return BooleanUtils.toBoolean(getValue(iteration, name));
    }

    public List<String> getListValue(int iteration, String name) {
        return Arrays.asList(StringUtils.split(getValue(iteration, name)));
    }

    public List<String> getAllValue(String name) {
        // no longer consider the system property when retrieving values,
        // that way we can define data at any iteration without being overshadowed by system prop.
        //String sysPropValue = System.getProperty(name);
        //if (sysPropValue == null) { return dataMap.computeIfAbsent(name, s -> new ArrayList<>()); }

        //List<String> values = TextUtils.toList(sysPropValue, DEF_TEXT_DELIM, false);
        //dataMap.put(name, values);
        //return values;

        return dataMap.computeIfAbsent(name, s -> new ArrayList<>());
    }

    public boolean hasSetting(String name) { return scopeSettings.containsKey(name); }

    public boolean isDefinedAsDefault(String name) { return defaultDataMap.containsKey(name); }

    public boolean has(int iteration, String name) {
        // data name must exists, data value must exist or else fallback must be set to true and iteration is not the 1st
        return dataMap.containsKey(name) &&
               (StringUtils.isNotEmpty(CollectionUtil.getOrDefault(dataMap.get(name), (iteration - 1), "")) ||
                getSettingAsBoolean(FALLBACK_TO_PREVIOUS) && iteration > 1);
    }

    public String getValue(int iteration, String name) {
        List<String> values = getAllValue(name);

        // in case there isn't any iteration, or data is defined outside of iteration (i.e. in project.properties or
        // #default sheet).
        if (CollectionUtils.size(values) == 1) { return values.get(0); }

        for (int i = iteration - 1; i >= 0; i--) {
            String data = CollectionUtil.getOrDefault(values, i, "");
            if (StringUtils.isNotEmpty(data) || !isFallbackToPrevious()) { return data; }
        }

        // nothing means nothing
        return "";
    }

    public List<String> getDataSheetNames() { return dataSheetNames; }

    public void mergeProjectData(TestProject project) {
        assert project != null && StringUtils.isNotBlank(project.getName());

        scopeSettings.put(OPT_PROJECT_NAME, project.getName());
        scopeSettings.put(OPT_PROJECT_BASE, project.getProjectHome());
        scopeSettings.put(OPT_OUT_DIR, project.getOutPath());
        scopeSettings.put(OPT_SCRIPT_DIR, project.getScriptPath());
        scopeSettings.put(OPT_DATA_DIR, project.getDataPath());
        scopeSettings.put(OPT_PLAN_DIR, project.getPlanPath());
    }

    protected void collectData(Worksheet sheet) {
        String sheetName = sheet.getName();
        boolean isDefault = StringUtils.equals(sheetName, SHEET_DEFAULT_DATA);
        String errPrefix = "File (" + sheet.getFile() + "), Worksheet (" + sheetName + "): ";

        int startRowIndex = 1;
        ExcelAddress addr = new ExcelAddress("A" + startRowIndex);

        // make sure we have data def. in A1, since that's where we start
        XSSFCell firstCell = sheet.cell(addr);
        if (StringUtils.isBlank(Excel.getCellValue(firstCell))) {
            throw new IllegalArgumentException(errPrefix + "test data must be defined at " + addr);
        }

        // find the last row
        int endRowIndex = sheet.findLastDataRow(addr) + 1;

        // first pass: read all nexial scope settings
        for (int i = startRowIndex; i < endRowIndex; i++) {
            int endColumnIndex = sheet.findLastDataColumn(new ExcelAddress("A" + i));
            String endColumn = ExcelAddress.toLetterCellRef(endColumnIndex);
            ExcelAddress addrThisRow = new ExcelAddress("A" + i + ":" + endColumn + i);
            List<List<XSSFCell>> row = sheet.cells(addrThisRow);

            // capture only nexial scope data
            if (CollectionUtils.isEmpty(row) || CollectionUtils.isEmpty(row.get(0))) {
                throw new IllegalArgumentException(errPrefix + "no data or no wrong format found at " + addrThisRow);
            }

            if (CollectionUtils.size(row.get(0)) < 2) { continue; }

            // column A must be defined with data name
            XSSFCell headerCell = row.get(0).get(0);
            String name = Excel.getCellValue(headerCell);
            if (StringUtils.isBlank(name)) {
                throw new IllegalArgumentException(errPrefix + "no data name defined at A" + i);
            }

            if (CommandConst.isNonIterableVariable(name)) {
                XSSFCell dataCell = row.get(0).get(1);
                String dataCellValue = Excel.getCellValue(dataCell);
                if (StringUtils.isBlank(dataCellValue)) { continue; }
                scopeSettings.put(name, dataCellValue);
            }
        }

        IterationManager iterationManager = getIterationManager();
        int lastIteration = iterationManager.getHighestIteration();

        // second pass: read all non "nexial scope" data, up to specified iteration
        for (int i = startRowIndex; i < endRowIndex; i++) {
            String endColumn = ExcelAddress.toLetterCellRef(lastIteration + 1);
            ExcelAddress addrThisRow = new ExcelAddress("A" + i + ":" + endColumn + i);

            // no need for empty/bad row checks since we did it in the first pass

            List<XSSFCell> row = sheet.cells(addrThisRow).get(0);
            String name = Excel.getCellValue(row.get(0));
            if (!CommandConst.isNonIterableVariable(name)) {
                collectIterationData(row, lastIteration, name, dataMap);

                // keep track of the data specified in #default sheet so that they can be overriden in
                // cross-execution scenarios (i.e. test plan).
                if (isDefault) {
                    collectIterationData(row, lastIteration, name, defaultDataMap);
                } else {
                    eliminateFromDefaultDataMap(name);
                }
            }
        }
    }

    /**
     * collect data designated for the specified <code>iteration</code>.
     */
    protected void collectIterationData(List<XSSFCell> row,
                                        int lastIteration,
                                        String dataKey,
                                        Map<String, List<String>> dataMap) {

        // computeIfAbsent(...) will add valid key/value to dataMap
        List<String> data = dataMap.computeIfAbsent(dataKey, s -> new ArrayList<>(lastIteration));

        for (int j = 1; j < row.size(); j++) {
            if (j <= lastIteration) {
                XSSFCell dataCell = row.get(j);
                if (dataCell != null) {
                    try {
                        String value = Excel.getCellValue(dataCell);
                        int dataIndex = j - 1;
                        if (data.size() >= j) {
                            // we only overwrite non-empty values.
                            // if (StringUtils.isNotEmpty(value)) { data.set(dataIndex, value); }

                            // CORRECTION: WE SHOULD OVERWRITE WITH ANY VALUE TO RETAIN THE INTEGRITY OF EACH DATA SHEET
                            data.set(dataIndex, value);
                        } else {
                            data.add(dataIndex, value);
                        }
                    } catch (IllegalStateException e) {
                        ConsoleUtils.error("Unable to read/handle file " + dataFile +
                                           ", worksheet " + dataCell.getSheet().getSheetName() +
                                           ", location " + dataCell.getAddress().formatAsString() + ": " +
                                           e.getMessage());
                        throw e;
                    }
                }
            }
        }
    }

    /**
     * since this {@code dataKey} is found in a non-default data sheet (not #default), we should not track such
     * data as part of the {@link #defaultDataMap}.
     */
    protected void eliminateFromDefaultDataMap(String dataKey) { defaultDataMap.remove(dataKey); }

    protected void infuseIntraExecutionData(Map<String, Object> intraExecutionData) {
        if (MapUtils.isEmpty(intraExecutionData) || !intraExecutionData.containsKey(LAST_ITERATION)) { return; }

        int lastIteration = NumberUtils.toInt(Objects.toString(intraExecutionData.get(LAST_ITERATION)));
        if (lastIteration < 1) { return; }

        intraExecutionData.forEach((name, value) -> {
            // (2018/12/06,automike): no longer limit to just "default" variable so that we can extend the changes from
            // (2018/12/06,automike): one script to another within the same execution plan.
            // (2018/12/06,automike): Use Case 1: modification of an iteration-bound data variable in `script1` should
            // (2018/12/06,automike):             remain effective to the subsequent scripts within same plan.
            // (2018/12/06,automike): Use Case 2: modification of an iteration-bound data variable in `script1` should
            // (2018/12/06,automike):             not preclude the same by subsequent scripts within same plan.
            // (2018/12/06,automike): Use Case 3: data variables defined or created in one script should be read/write-
            // (2018/12/06,automike):             able by all subsequent scripts within same plan.

            // if (isDefinedAsDefault(name)) {
            String stringValue = Objects.toString(value);

            // we should be sync'ing back value to its initial iteration, not the current/latest iteration
            List<String> data = dataMap.get(name);
            if (CollectionUtils.size(data) < lastIteration) {
                if (CollectionUtils.isEmpty(data)) { data = new ArrayList<>(); }
                for (int i = 0; i < lastIteration; i++) { if (data.size() <= i) { data.add(i, null); } }
            }
            data.set((lastIteration - 1), stringValue);
            // }
        });
    }

    Map<String, List<String>> getRuntimeDataMap() { return runtimeDataMap; }

    // add existing runtime data from previous iteration if any
    void addExistingRuntimeData(Map<String, List<String>> runtimeDataMap) {
        if (runtimeDataMap.size() == 0) { return; }

        runtimeDataMap.forEach((key, value) -> {
            if (!dataMap.containsKey(key)) { dataMap.put(key, value); }
            this.runtimeDataMap.put(key, value);
        });
    }

    // add runtime data to dataMap when variable is assigned from console
    void addRuntimeData(String name, String value, int currIteration) {
        if (isFallbackToPrevious() && dataMap.containsKey(name)) { return; }

        // get dataMap if available
        List<String> data = dataMap.containsKey(name) ? dataMap.get(name) : new ArrayList<>();
        int highestIteration = getIterationManager().getHighestIteration();

        data = data.size() == 0 && runtimeDataMap.containsKey(name) ? runtimeDataMap.get(name) : data;
        while (data.size() < highestIteration) { data.add(""); }
        data.set(currIteration - 1, value);

        dataMap.put(name, data);
        // add to runtime data as well to keep track in next iterations
        runtimeDataMap.put(name, data);
    }
}