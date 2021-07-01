package org.nexial.core.tms.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represent the TMS Meta json for the project. Each project will have a single TMS project id and can have multiple file
 * entries associated with different suites
 */
public class TmsMeta {
    private final String projectId;
    private List<TestFile> files;

    public TmsMeta(String projectId, List<TestFile> files) {
        this.projectId = projectId;
        this.files     = files;
    }

    public String getProjectId() { return projectId; }

    public List<TestFile> getFiles() { return files; }

    public void setFiles(List<TestFile> files) {
        this.files = files;
    }

    public void addFile(TestFile file)    {
        if (files == null){
            files = new ArrayList<>();
        }
        files.add(file);
    }

    public void removeFile(TestFile file) {
        if (files == null) { return; }
        files.removeIf(f -> f.getPath().equals(file.getPath()));
    }
}

