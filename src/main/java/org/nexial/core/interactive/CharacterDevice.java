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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;

/**
 * {@link TextDevice} implementation wrapping character streams.
 */
class CharacterDevice extends TextDevice {
    private final BufferedReader reader;
    private final PrintWriter writer;

    public CharacterDevice(BufferedReader reader, PrintWriter writer) {
        this.reader = reader;
        this.writer = writer;
    }

    @Override
    public CharacterDevice printf(String fmt, Object... params) {
        writer.printf(fmt, params);
        return this;
    }

    @Override
    public String readLine() throws IOException { return reader.readLine(); }

    @Override
    public char readChar() throws IOException { return (char) reader.read(); }

    @Override
    public char[] readPassword() throws IOException {
        writer.print("(UNMASKED) ");
        writer.flush();
        return readLine().toCharArray();
    }

    @Override
    public Reader reader() { return reader; }

    @Override
    public PrintWriter writer() { return writer; }
}