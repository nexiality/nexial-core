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

package org.nexial.commons.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Locale;

import org.apache.commons.io.FileUtils;

/**
 * works like a /dev/null
 */
public class NoopPrintStream extends PrintStream {
    public NoopPrintStream() throws FileNotFoundException {
        super(new File(FileUtils.getTempDirectoryPath() + "/" + Thread.currentThread().getName()));
    }

    @Override
    public void flush() { }

    @Override
    public void close() { }

    @Override
    public boolean checkError() { return true; }

    @Override
    public void write(int b) { }

    @Override
    public void write(byte[] buf, int off, int len) { }

    @Override
    public void print(boolean b) { }

    @Override
    public void print(char c) { }

    @Override
    public void print(int i) { }

    @Override
    public void print(long l) { }

    @Override
    public void print(float f) { }

    @Override
    public void print(double d) { }

    @Override
    public void print(char[] s) { }

    @Override
    public void print(String s) { }

    @Override
    public void print(Object obj) { }

    @Override
    public void println() { }

    @Override
    public void println(boolean x) { }

    @Override
    public void println(char x) { }

    @Override
    public void println(int x) { }

    @Override
    public void println(long x) { }

    @Override
    public void println(float x) { }

    @Override
    public void println(double x) { }

    @Override
    public void println(char[] x) { }

    @Override
    public void println(String x) { }

    @Override
    public void println(Object x) { }

    @Override
    public PrintStream printf(String format, Object... args) { return this; }

    @Override
    public PrintStream printf(Locale l, String format, Object... args) { return this; }

    @Override
    public PrintStream format(String format, Object... args) { return this; }

    @Override
    public PrintStream format(Locale l, String format, Object... args) { return this; }

    @Override
    public PrintStream append(CharSequence csq) { return this; }

    @Override
    public PrintStream append(CharSequence csq, int start, int end) { return this; }

    @Override
    public PrintStream append(char c) { return this; }

    @Override
    public void write(byte[] b) { }

    @Override
    protected void setError() { }

    @Override
    protected void clearError() { }
}
