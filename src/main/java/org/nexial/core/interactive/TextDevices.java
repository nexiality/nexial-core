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

package org.nexial.core.interactive;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * Convenience class for providing {@link TextDevice} implementations.
 */
public final class TextDevices {
	private static final TextDevice DEFAULT = (System.console() == null) ?
	                                          streamDevice(System.in, System.out) :
	                                          new ConsoleDevice(System.console());

	private TextDevices() { }

	/**
	 * The default system text I/O device.
	 *
	 * @return the default device
	 */
	public static TextDevice defaultTextDevice() { return DEFAULT; }

	public static boolean hasConsole() { return System.console() != null; }

	/**
	 * Returns a text I/O device wrapping the given streams. The default system encoding is used to decode/encode data.
	 *
	 * @param in  an input source
	 * @param out an output target
	 * @return a new device
	 */
	public static TextDevice streamDevice(InputStream in, OutputStream out) {
		return new CharacterDevice(new BufferedReader(new InputStreamReader(in)), new PrintWriter(out, true));
	}

	/**
	 * Returns a text I/O device wrapping the given streams.
	 *
	 * @param reader an input source
	 * @param writer an output target
	 * @return a new device
	 */
	public static TextDevice characterDevice(BufferedReader reader, PrintWriter writer) {
		return new CharacterDevice(reader, writer);
	}
}