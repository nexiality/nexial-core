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

package org.nexial.commons.logging;

import java.io.PrintStream;

import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

/**
 *
 */
public final class PrintStreamLogProxy {
	public static final class ProxyPrintStream extends PrintStream {
		private Logger logger;
		private Priority priority;
		private PrintStream ps;

		public ProxyPrintStream(PrintStream ps, Logger logger, Priority priority) {
			super(ps);
			this.ps = ps;
			this.logger = logger;
			this.priority = priority;
		}

		@Override
		public void print(boolean b) {
			ps.print(b);
			logger.log(priority, b);
		}

		@Override
		public void print(char b) {
			ps.print(b);
			logger.log(priority, b);
		}

		@Override
		public void print(int b) {
			ps.print(b);
			logger.log(priority, b);
		}

		@Override
		public void print(long b) {
			ps.print(b);
			logger.log(priority, b);
		}

		@Override
		public void print(float b) {
			ps.print(b);
			logger.log(priority, b);
		}

		@Override
		public void print(double b) {
			ps.print(b);
			logger.log(priority, b);
		}

		@Override
		public void print(char[] b) {
			ps.print(b);
			logger.log(priority, String.valueOf(b));
		}

		@Override
		public void print(String b) {
			ps.print(b);
			logger.log(priority, b);
		}

		@Override
		public void print(Object b) {
			ps.print(b);
			logger.log(priority, b);
		}

		@Override
		public void println() {
			ps.println();
			logger.log(priority, "");
		}

		@Override
		public void println(boolean b) {
			ps.println(b);
			logger.log(priority, b);
		}

		@Override
		public void println(char b) {
			ps.println(b);
			logger.log(priority, b);
		}

		@Override
		public void println(int b) {
			ps.println(b);
			logger.log(priority, b);
		}

		@Override
		public void println(long b) {
			ps.println(b);
			logger.log(priority, b);
		}

		@Override
		public void println(float b) {
			ps.println(b);
			logger.log(priority, b);
		}

		@Override
		public void println(double b) {
			ps.println(b);
			logger.log(priority, b);
		}

		@Override
		public void println(char[] b) {
			ps.println(b);
			logger.log(priority, String.valueOf(b));
		}

		@Override
		public void println(String b) {
			ps.println(b);
			logger.log(priority, b);
		}

		@Override
		public void println(Object b) {
			ps.println(b);
			logger.log(priority, b);
		}
	}

	private PrintStreamLogProxy() {}

	public static PrintStream createLoggingProxy(final PrintStream ps, final Logger logger, final Priority priority) {
		if (ps == null) { throw new RuntimeException("underlying print stream cannot be null!"); }
		if (logger == null) { throw new RuntimeException("underlying logger cannot be null!"); }
		if (priority == null) { throw new RuntimeException("priority must be explicitly defined!"); }
		return new ProxyPrintStream(ps, logger, priority);
	}
}
