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

import java.util.*;

public class SwaggerScenarioVars {
    private String activityName;
    private String url;
    private String json;
    private String schema;

    private List<String> headerParams = new ArrayList<>();
    private List<String> cookieParams = new ArrayList<>();
    private List<String> pathParams = new ArrayList<>();
    private List<String> queryParams = new ArrayList<>();

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<String> getHeaderParams() {
        return headerParams;
    }

    public void setHeaderParams(List<String> headerParams) {
        this.headerParams = headerParams;
    }

    public List<String> getCookieParams() {
        return cookieParams;
    }

    public void setCookieParams(List<String> cookieParams) {
        this.cookieParams = cookieParams;
    }

    public List<String> getPathParams() {
        return pathParams;
    }

    public void setPathParams(List<String> pathParams) {
        this.pathParams = pathParams;
    }

    public List<String> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(List<String> queryParams) {
        this.queryParams = queryParams;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    @Override
    public String toString() {
        return "SwaggerScenarioDetails{" +
               "prefix='" + activityName + '\'' +
               ", url='" + url + '\'' +
               ", json=" + json +
               ", schema=" + schema +
               ", headers=" + headerParams +
               ", cookies=" + cookieParams +
               ", pathParams=" + pathParams +
               ", queryParams=" + queryParams +
               '}';
    }
}
