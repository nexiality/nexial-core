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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration variables generated as part of Nexial Swagger script.
 *
 * @author Dhanapathi Marepalli
 */
public class SwaggerDataVariables {
    private String baseUrl;

    private Map<String, List<String>> requestBodyVars;
    private Map<String, List<String>> headerParams;
    private Map<String, List<String>> queryParams;
    private Map<String, List<String>> pathParams;
    private Map<String, List<String>> cookieParams;
    private Map<String, List<String>> statusTextVars;
    private Map<String, List<String>> securityVars;

    public static SwaggerDataVariables getInstance() {
        SwaggerDataVariables data = new SwaggerDataVariables();
        data.requestBodyVars = new LinkedHashMap<>();
        data.headerParams    = new LinkedHashMap<>();
        data.queryParams     = new LinkedHashMap<>();
        data.pathParams      = new LinkedHashMap<>();
        data.cookieParams    = new LinkedHashMap<>();
        data.securityVars    = new LinkedHashMap<>();
        data.statusTextVars  = new LinkedHashMap<>();
        return data;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Map<String, List<String>> getRequestBodyVars() {
        return requestBodyVars;
    }

    public void setRequestBodyVars(Map<String, List<String>> requestBodyVars) {
        this.requestBodyVars = requestBodyVars;
    }

    public Map<String, List<String>> getHeaderParams() {
        return headerParams;
    }

    public void setHeaderParams(Map<String, List<String>> headerParams) {
        this.headerParams = headerParams;
    }

    public Map<String, List<String>> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(Map<String, List<String>> queryParams) {
        this.queryParams = queryParams;
    }

    public Map<String, List<String>> getPathParams() {
        return pathParams;
    }

    public void setPathParams(Map<String, List<String>> pathParams) {
        this.pathParams = pathParams;
    }

    public Map<String, List<String>> getCookieParams() {
        return cookieParams;
    }

    public void setCookieParams(Map<String, List<String>> cookieParams) {
        this.cookieParams = cookieParams;
    }

    public Map<String, List<String>> getStatusTextVars() {
        return statusTextVars;
    }

    public void setStatusTextVars(Map<String, List<String>> statusTextVars) {
        this.statusTextVars = statusTextVars;
    }

    public Map<String, List<String>> getSecurityVars() {
        return securityVars;
    }

    public void setSecurityVars(Map<String, List<String>> securityVars) {
        this.securityVars = securityVars;
    }

    @Override
    public String toString() {
        return "SwaggerTestScriptData{" +
               "baseUrl='" + baseUrl + '\'' +
               ", requestBodyVars=" + requestBodyVars +
               ", headerParams=" + headerParams +
               ", queryParams=" + queryParams +
               ", pathParams=" + pathParams +
               ", cookieParams=" + cookieParams +
               ", statusTextVars=" + statusTextVars +
               ", securityVars=" + securityVars +
               '}';
    }
}
