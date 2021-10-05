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

