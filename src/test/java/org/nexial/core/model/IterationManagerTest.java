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

package org.nexial.core.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class IterationManagerTest {
    @Test
    public void testSimpleCases() {
        IterationManager subject = IterationManager.newInstance("");
        assertEquals(subject.getFirstIteration(), 1);
        assertEquals(subject.getLastIteration(), 1);
        assertEquals(subject.getLowestIteration(), 1);
        assertEquals(subject.getHighestIteration(), 1);
        assertEquals(subject.getIterationCount(), 1);
        assertFalse(subject.skip(0));
        for (int i = 1; i < 200; i++) { assertTrue(subject.skip(i)); }

        subject = IterationManager.newInstance("1");
        assertEquals(subject.getFirstIteration(), 1);
        assertEquals(subject.getLastIteration(), 1);
        assertEquals(subject.getLowestIteration(), 1);
        assertEquals(subject.getHighestIteration(), 1);
        assertEquals(subject.getIterationCount(), 1);
        assertFalse(subject.skip(0));
        for (int i = 1; i < 200; i++) { assertTrue(subject.skip(i)); }

        subject = IterationManager.newInstance("0");
        assertEquals(subject.getFirstIteration(), 1);
        assertEquals(subject.getLastIteration(), 1);
        assertEquals(subject.getLowestIteration(), 1);
        assertEquals(subject.getHighestIteration(), 1);
        assertEquals(subject.getIterationCount(), 1);

        subject = IterationManager.newInstance("1,2,3,4,5");
        assertEquals(subject.getFirstIteration(), 1);
        assertEquals(subject.getLastIteration(), 5);
        assertEquals(subject.getLowestIteration(), 1);
        assertEquals(subject.getHighestIteration(), 5);
        assertEquals(subject.getIterationCount(), 5);
        assertFalse(subject.skip(0));
        assertFalse(subject.skip(1));
        assertFalse(subject.skip(2));
        assertFalse(subject.skip(3));
        assertFalse(subject.skip(4));
        for (int i = 5; i < 200; i++) { assertTrue(subject.skip(i)); }
    }

    @Test
    public void testComplexCases() {
        IterationManager subject = IterationManager.newInstance("1-5,17,99,6");
        assertEquals(subject.getFirstIteration(), 1);
        assertEquals(subject.getLastIteration(), 6);
        assertEquals(subject.getLowestIteration(), 1);
        assertEquals(subject.getHighestIteration(), 99);
        assertEquals(subject.getIterationCount(), 8);
        for (int i = 1; i < 200; i++) {
            if (i == 0 ||
                i == 1 ||
                i == 2 ||
                i == 3 ||
                i == 4 ||
                i == 5 ||
                i == 16 ||
                i == 98) {
                assertFalse(subject.skip(i));
            } else {
                assertTrue(subject.skip(i));
            }
        }

        subject = IterationManager.newInstance("17,45,10-15,6-12");
        assertEquals(subject.getFirstIteration(), 17);
        assertEquals(subject.getLastIteration(), 12);
        assertEquals(subject.getLowestIteration(), 6);
        assertEquals(subject.getHighestIteration(), 45);
        assertEquals(subject.getIterationCount(), 15);
        for (int i = 1; i < 200; i++) {
            if (i == 5 ||
                i == 6 ||
                i == 7 ||
                i == 8 ||
                i == 9 ||
                i == 10 ||
                i == 11 ||
                i == 12 ||
                i == 13 ||
                i == 14 ||
                i == 16 ||
                i == 44) {
                assertFalse(subject.skip(i));
            } else {
                assertTrue(subject.skip(i));
            }
        }

        subject = IterationManager.newInstance("1-5,1 - 5, 001  -      05.00");
        assertEquals(subject.getFirstIteration(), 1);
        assertEquals(subject.getLastIteration(), 1);
        assertEquals(subject.getLowestIteration(), 1);
        assertEquals(subject.getHighestIteration(), 1);
        assertEquals(subject.getIterationCount(), 1);

    }

    @Test
    public void testParsing() {
        IterationManager subject = IterationManager.newInstance("1-5,17,99,6");
        assertEquals(subject.getFirstIteration(), 1);
        assertEquals(subject.getLastIteration(), 6);
        assertEquals(subject.getIterationCount(), 8);
        assertEquals("[1,2,3,4,5,17,99,6] total 8", subject.toString());

        subject = IterationManager.newInstance("1,2,1,2,1");
        assertEquals(subject.getFirstIteration(), 1);
        assertEquals(subject.getLastIteration(), 1);
        assertEquals(subject.getLowestIteration(), 1);
        assertEquals(subject.getHighestIteration(), 2);
        assertEquals(subject.getIterationCount(), 5);
        assertEquals("[1,2,1,2,1] total 5", subject.toString());

        subject = IterationManager.newInstance("1,1,1,2-5,5-3");
        assertEquals(subject.getFirstIteration(), 1);
        assertEquals(subject.getLastIteration(), 3);
        assertEquals(subject.getLowestIteration(), 1);
        assertEquals(subject.getHighestIteration(), 5);
        assertEquals(subject.getIterationCount(), 10);
        assertEquals("[1,1,1,2,3,4,5,5,4,3] total 10", subject.toString());

        subject = IterationManager.newInstance("B-D,Z-AA,C-19");
        assertEquals(subject.getFirstIteration(), 1);
        assertEquals(subject.getLastIteration(), 19);
        assertEquals(subject.getLowestIteration(), 1);
        assertEquals(subject.getHighestIteration(), 26);
        assertEquals(subject.getIterationCount(), 23);
        assertEquals("[1,2,3,25,26,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19] total 23", subject.toString());
    }

    @Test
    public void testNewInstance() {

        IterationManager subject = IterationManager.newInstance("1");
        assertEquals(1, subject.getFirstIteration());
        assertEquals(1, subject.getLastIteration());

        IterationManager subject1 = IterationManager.newInstance("B");
        assertEquals(1, subject1.getFirstIteration());
        assertEquals(1, subject1.getLastIteration());

        IterationManager subject2 = IterationManager.newInstance("B-D");
        assertEquals(1, subject2.getFirstIteration());
        assertEquals(3, subject2.getLastIteration());

        IterationManager subject3 = IterationManager.newInstance("B-AH");
        assertEquals(1, subject3.getFirstIteration());
        assertEquals(33, subject3.getLastIteration());

        IterationManager subject4 = IterationManager.newInstance("B-14");
        assertEquals(1, subject4.getFirstIteration());
        assertEquals(14, subject4.getLastIteration());

        IterationManager subject5 = IterationManager.newInstance("22-Z");
        assertEquals(22, subject5.getFirstIteration());
        assertEquals(25, subject5.getLastIteration());

        // any error would default to iteration 1 only
        IterationManager subject5a = IterationManager.newInstance("22-");
        assertEquals(1, subject5a.getFirstIteration());
        assertEquals(1, subject5a.getLastIteration());

        // any error would default to iteration 1 only
        IterationManager subject6 = IterationManager.newInstance("A-J");
        assertEquals(1, subject6.getFirstIteration());
        assertEquals(1, subject6.getLastIteration());

        // any error would default to iteration 1 only
        IterationManager subject7 = IterationManager.newInstance("0-AO");
        assertEquals(1, subject7.getFirstIteration());
        assertEquals(1, subject7.getLastIteration());

        // any error would default to iteration 1 only
        IterationManager subject8 = IterationManager.newInstance("2-4,B,0-P");
        assertEquals(1, subject8.getFirstIteration());
        assertEquals(1, subject8.getLastIteration());

        // any error would default to iteration 1 only
        IterationManager subject9 = IterationManager.newInstance("0-");
        assertEquals(1, subject9.getFirstIteration());
        assertEquals(1, subject9.getLastIteration());

    }
}