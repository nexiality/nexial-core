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

package org.nexial.core.plugins.filevalidation.config;

import java.util.ArrayList;
import java.util.List;

public class MasterConfig {

    private RecordConfig fileHeader;
    private List<SectionConfig> sectionConfigs;
    private RecordConfig fileFooter;

    public List<RecordConfig> getConfigs(MasterConfig masterConfig) {
        List<RecordConfig> configs = new ArrayList<>();
        configs.add(masterConfig.getFileHeader());
        for (SectionConfig sectionConfig : masterConfig.getSectionConfigs()) {
            configs.add(sectionConfig.getHeaderConfig());
            configs.addAll(sectionConfig.getBodyConfigs());
            configs.add(sectionConfig.getFooterConfig());
        }
        configs.add(masterConfig.getFileFooter());
        return configs;
    }

    @Override
    public String toString() {
        return "MasterConfig{fileHeader=" + fileHeader +
               ", sectionConfigs=" + sectionConfigs +
               ", fileFooter=" + fileFooter +
               '}';
    }

    private RecordConfig getFileHeader() {
        return fileHeader;
    }

    public void setFileHeader(RecordConfig fileHeader) {
        this.fileHeader = fileHeader;
    }

    private List<SectionConfig> getSectionConfigs() {
        return sectionConfigs;
    }

    public void setSectionConfigs(List<SectionConfig> sectionConfigs) {
        this.sectionConfigs = sectionConfigs;
    }

    private RecordConfig getFileFooter() {
        return fileFooter;
    }

    public void setFileFooter(RecordConfig fileFooter) {
        this.fileFooter = fileFooter;
    }
}
