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

package org.nexial.core.tools.swagger;

import java.util.List;
import java.util.Map;

/**
 * Represents a class which represents the values extracted from the Swagger file used to generated the Nexial script
 * like list of {@link SwaggerScenario} and {@link SwaggerDataVariables}.
 *
 * @author Dhanapathi Marepalli
 */
public class NexialContents {
    private List<SwaggerScenario> scenarios;
    private SwaggerDataVariables dataVariables;
    private Map<String, List<SwaggerScenarioVars>> swaggerScenarioVarsMap;
    private Map<String, String> jsonPayloadFiles;
    private Map<String, String> schemaFiles;

    public List<SwaggerScenario> getScenarios() {
        return scenarios;
    }

    public void setScenarios(List<SwaggerScenario> scenarios) {
        this.scenarios = scenarios;
    }

    public SwaggerDataVariables getDataVariables() {
        return dataVariables;
    }

    public void setDataVariables(SwaggerDataVariables dataVariables) {
        this.dataVariables = dataVariables;
    }

    public Map<String, List<SwaggerScenarioVars>> getSwaggerScenarioVarsMap() {
        return swaggerScenarioVarsMap;
    }

    public void setSwaggerScenarioVarsMap(
            Map<String, List<SwaggerScenarioVars>> swaggerScenarioVarsMap) {
        this.swaggerScenarioVarsMap = swaggerScenarioVarsMap;
    }

    public Map<String, String> getJsonPayloadFiles() {
        return jsonPayloadFiles;
    }

    public void setJsonPayloadFiles(Map<String, String> jsonPayloadFiles) {
        this.jsonPayloadFiles = jsonPayloadFiles;
    }

    public Map<String, String> getSchemaFiles() {
        return schemaFiles;
    }

    public void setSchemaFiles(Map<String, String> schemaFiles) {
        this.schemaFiles = schemaFiles;
    }

    @Override
    public String toString() {
        return "NexialContents{" +
               "scenarios=" + scenarios +
               ", dataVariables=" + dataVariables +
               ", swaggerScenarioVarsMap=" + swaggerScenarioVarsMap +
               ", jsonPayloadFiles=" + jsonPayloadFiles +
               '}';
    }
}
