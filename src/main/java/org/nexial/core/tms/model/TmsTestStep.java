package org.nexial.core.tms.model;

import java.util.ArrayList;
import java.util.List;
import org.nexial.core.excel.Excel.Worksheet;

/**
 * Represents a test step that resides inside a test case. Mapped to an activity in Nexial
 */
public class TmsTestStep {
    private String name;
    private List<TmsCustomStep> tmsCustomSteps = new ArrayList<>();
    private final TmsTestCase testCase;

    public TmsTestStep(TmsTestCase testCase) {
        this.testCase  = testCase;
    }

    public TmsTestCase getTestCase() {
        return testCase;
    }

    public String getName()                                           { return name; }

    public void setName(String name)                                  { this.name = name;}

    public List<TmsCustomStep> getTmsCustomSteps()                    { return tmsCustomSteps; }

    public void setTmsCustomSteps(List<TmsCustomStep> tmsCustomSteps) { this.tmsCustomSteps = tmsCustomSteps; }

    public void addTmsCustomTestStep(TmsCustomStep testStep)          { this.tmsCustomSteps.add(testStep);}

    public String getMessageId() {
        Worksheet worksheet = testCase.getWorksheet();
        return String.format("[%s][%s][%s]",
                             worksheet.getFile().getName(),
                             worksheet.getName(),
                             name);
    }
}