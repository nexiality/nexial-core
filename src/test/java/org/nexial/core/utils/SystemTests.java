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

package org.nexial.core.utils;

import java.util.Arrays;

public class SystemTests {
	public static void main(String[] args) {
		System.out.println("os.name     = " + System.getProperty("os.name"));
		System.out.println("os.arch     = " + System.getProperty("os.arch"));
		System.out.println("os.version  = " + System.getProperty("os.version"));
		System.out.println("user.dir    = " + System.getProperty("user.dir"));

		String[] array = new String[15];
		Arrays.fill(array, "");
		System.out.println(array[0]);
		System.out.println(array[1]);
		System.out.println(array[14]);
	}
}
