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

package org.nexial.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;
import static org.nexial.core.NexialConst.Data.START_URL;

public class ExecutionTokenReplacementTest {
    private MockExecutionContext context;

    public static class Movie {
        private String id;
        private String name;
        private int minutes;
        private int year;

        public Movie() { }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getMinutes() {
            return minutes;
        }

        public void setMinutes(int minutes) {
            this.minutes = minutes;
        }

        public int getYear() {
            return year;
        }

        public void setYear(int year) {
            this.year = year;
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                       .append("id", id)
                       .append("name", name)
                       .append("minutes", minutes)
                       .append("year", year)
                       .toString();
        }
    }

    @Before
    public void init() {
        List<Map<String, String>> resultset = new ArrayList<>();

        Map<String, String> rowOne = new HashMap<>();
        rowOne.put("col1", "Johnny");
        rowOne.put("col2", "B.");
        rowOne.put("col3", "Good");
        resultset.add(rowOne);

        Map<String, String> rowTwo = new HashMap<>();
        rowTwo.put("col1", "Samuel");
        rowTwo.put("col2", "L.");
        rowTwo.put("col3", "Jackson");
        resultset.add(rowTwo);

        String varName = "myData";

        context = new MockExecutionContext();
        context.setData(START_URL, "http://www.google.com");
        context.setData(varName, resultset);
    }

    @After
    public void tearDown() {
        if (context != null) { context.cleanProject(); }
    }

    @Test
    public void testReplaceTokens4() {
        Assert.assertEquals("R1C2 = Good", context.replaceTokens("R1C2 = ${myData}[0].col3"));
        String actual = context.replaceTokens("${myData}");
        Assert.assertTrue(StringUtils.contains(actual, "col1=Johnny"));
        Assert.assertTrue(StringUtils.contains(actual, "col2=B."));
        Assert.assertTrue(StringUtils.contains(actual, "col3=Good"));
        Assert.assertTrue(StringUtils.contains(actual, "col1=Samuel"));
        Assert.assertTrue(StringUtils.contains(actual, "col2=L."));
        Assert.assertTrue(StringUtils.contains(actual, "col3=Jackson"));

        String actual1 = context.replaceTokens("${myData}[1]");
        Assert.assertTrue(StringUtils.contains(actual1, "col1=Samuel"));
        Assert.assertTrue(StringUtils.contains(actual1, "col2=L."));
        Assert.assertTrue(StringUtils.contains(actual1, "col3=Jackson"));

        Assert.assertEquals("Yo yo Johnny,Samuel yaw!", context.replaceTokens("Yo yo ${myData}.col1 yaw!"));
    }

    @Test
    public void testBeanGetSetter() throws Exception {
        List<Movie> movies = new ArrayList<>();

        Movie movie = new Movie();
        movie.setId("1");
        movie.setName("10,000 Leagues under the sea");
        movie.setMinutes(121);
        movie.setYear(1971);
        movies.add(movie);

        movie = new Movie();
        movie.setId("2");
        movie.setName("Spot On, Ol' Chap");
        movie.setMinutes(112);
        movie.setYear(1981);
        movies.add(movie);

        movie = new Movie();
        movie.setId("12");
        movie.setName("Great Escape");
        movie.setMinutes(152);
        movie.setYear(1991);
        movies.add(movie);

        context.setData("movies", movies);

        Assert.assertNotNull(context.replaceTokens("${movies}[0]"));
        Assert.assertEquals("1", context.replaceTokens("${movies}[0].id"));
        Assert.assertEquals("10,000 Leagues under the sea", context.replaceTokens("${movies}[0].name"));
        Assert.assertEquals("1971", context.replaceTokens("${movies}[0].year"));
        Assert.assertEquals("112", context.replaceTokens("${movies}[1].minutes"));
        Assert.assertEquals("Great Escape", context.replaceTokens("${movies}[2].name"));
        Assert.assertEquals("12", context.replaceTokens("${movies}[2].getId"));
    }
}
