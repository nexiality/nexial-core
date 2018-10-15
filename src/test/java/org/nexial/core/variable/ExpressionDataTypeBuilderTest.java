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
 */

package org.nexial.core.variable;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.core.model.MockExecutionContext;

public class ExpressionDataTypeBuilderTest {
    private MockExecutionContext context;

    @Before
    public void setUp() throws Exception {
        context = new MockExecutionContext();
    }

    @After
    public void tearDown() throws Exception {
        if (context != null) { context.cleanProject(); }
    }

    @Test
    public void parseExpressionGroups() {
        ExpressionDataTypeBuilder builder = new ExpressionDataTypeBuilder(context);

        List<String> groups = builder.parseExpressionGroups("[TEXT(Hello, there)=>length multiply(19)]");
        Assert.assertNotNull(groups);
        Assert.assertEquals(4, groups.size());
    }
}