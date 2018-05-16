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

package org.nexial.commons.spring;

import java.io.IOException;
import java.io.OutputStream;

/**
 *

 */
public class MonitoredOutputStream extends OutputStream {
    private OutputStream target;
    private OutputStreamListener listener;

    public MonitoredOutputStream(OutputStream target, OutputStreamListener listener) {
        this.target = target;
        this.listener = listener;
        this.listener.start();
    }

    public void write(byte[] b, int off, int len) throws IOException {
        throwCancelException();
        target.write(b, off, len);
        listener.bytesRead(len - off);
    }

    public void write(byte[] b) throws IOException {
        throwCancelException();
        target.write(b);
        listener.bytesRead(b.length);
    }

    public void write(int b) throws IOException {
        throwCancelException();
        target.write(b);
        listener.bytesRead(1);
    }

    public void close() throws IOException {
        target.close();
        listener.done();
    }

    public void flush() throws IOException {
        target.flush();
    }

    private void throwCancelException() {
        if (listener.isCancelled()) {
            //throw new IOException("User canceled upload");
        }
    }
}
