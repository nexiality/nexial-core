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

import java.util.Map;

/**
 * Represents a TMS Test Suite, contains the name, id and the url of the test suite as well as the test cases present inside it
 */
public class TmsSuite {
    private final String name;
    private final String id;
    private String suiteUrl;
    private final Map<String, String> testCases;

    public TmsSuite(String name, String id, Map<String, String> testCases) {
        this.name      = name;
        this.id        = id;
        this.testCases = testCases;
    }

    public TmsSuite(String name, String id, Map<String, String> testCases, String suiteUrl) {
        this.name      = name;
        this.id        = id;
        this.testCases = testCases;
        this.suiteUrl  = suiteUrl;
    }

    public String getSuiteUrl() {
        return suiteUrl;
    }

    public Map<String, String> getTestCases() {
        return testCases;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }
}
