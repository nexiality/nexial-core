package org.nexial.core.tms.model;

/**
 * Contains the project id of the current TMS project and and a file entry from the json file
 */
public class TmsTestFile {
    private final String projectId;
    private final TestFile file;

    public TmsTestFile(String projectId, TestFile file) {
        this.projectId = projectId;
        this.file      = file;
    }

    public String getProjectId() {
        return projectId;
    }

    public TestFile getFile() {
        return file;
    }
}
