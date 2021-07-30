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

package org.nexial.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.Test;

import org.nexial.commons.utils.TextUtils;

public class ExecutionThreadTest {

	static class LauncherData {
		private String name;
		private List<String> values;

		public LauncherData(String name, List<String> values) {
			this.name = name;
			this.values = values;
		}

		public String getName() { return name; }

		public List<String> getValues() { return values; }

		@Override
		public String toString() {
			return new ToStringBuilder(this).append("name", name).append("values", values).toString();
		}
	}

	static final class ThreadContext {
		private static final ThreadLocal<LauncherData> threadLocal = new ThreadLocal<>();

		public static LauncherData get() { return threadLocal.get(); }

		public static void set(LauncherData data) { threadLocal.set(data); }

		public static void unset() { threadLocal.remove(); }
	}

	private class TestInner {
		public void testThreadObject() {
			String threadName = Thread.currentThread().getName();

			int sleepMs = RandomUtils.nextInt(100, 2500);
			System.out.println(threadName + ": sleeping for " + sleepMs + " ms");
			try {
				Thread.sleep(sleepMs);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			System.out.println();
			LauncherData data = ThreadContext.get();
			System.out.println(threadName + ": data = " + data);
			data.getValues().clear();

			ThreadContext.unset();
		}
	}

	@Test
	public void testExecutionThreads() throws Exception {
		List<LauncherData> stuff = Arrays.asList(
			new LauncherData("Johnny", TextUtils.toList("Apple,Oracle,IBM", ",", true)),
			new LauncherData("Sam", TextUtils.toList("SAP,Microsoft,HP", ",", true)),
			new LauncherData("Mike", TextUtils.toList("Google,Facebook,Twitter", ",", true)),
			new LauncherData("Adam", TextUtils.toList("Jetbrain,Gynomists", ",", true)),
			new LauncherData("Peter", TextUtils.toList("EP,SAP,ADP,PPP", ",", true))
		);

		System.out.println("tests start");

		List<Thread> threads = new ArrayList<>();
		stuff.forEach(data -> {
			Thread t = new Thread() {
				@Override
				public void run() {
					ThreadContext.set(data);
					TestInner inner = new TestInner();
					inner.testThreadObject();
				}
			};
			threads.add(t);
			t.start();
		});

		System.out.println("test started, waiting for threads to complete...");

		while (true) {
			final boolean[] stillRunning = {false};
			threads.forEach(t -> { if (t.isAlive()) { stillRunning[0] = true; } });
			if (!stillRunning[0]) { break; }
		}

		System.out.println("all done");
	}

}
