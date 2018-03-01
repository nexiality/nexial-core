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

package org.nexial.core.plugins.ssh;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class ScpHelper {
    public static final String SCP_FROM = "scp -f ";
    public static final String SCP_TO = "scp -t ";

    protected static void doScpCopyFrom(Session session, String remote, String local)
        throws JSchException, IOException {

        // exec 'scp -f rfile' remotely
        String command = SCP_FROM + remote;

        Channel channel = null;
        try {
            channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);

            // get I/O streams for remote scp
            OutputStream out = channel.getOutputStream();
            InputStream in = channel.getInputStream();

            channel.connect();

            // send '\0'
            send(out, (byte) 0);

            while (true) {
                int c = checkAck(in);
                if (c != 'C') { break; }

                // read '0644 '
                forwardOffset(in, 0, 5);

                long filesize = deriveFileSize(in);
                String filename = deriveFilename(in);
                // System.out.println("filesize=" + filesize + ", file=" + filename);

                // send '\0'
                send(out, (byte) 0);

                // read a content of lfile
                byte[] buf = new byte[(int) filesize];
                IOUtils.readFully(in, buf);
                FileUtils.writeByteArrayToFile(new File(local), buf);

                assertNormalResponse(in);

                // send '\0'
                send(out, (byte) 0);
            }
        } finally {
            if (channel != null) { channel.disconnect(); }
        }
    }

    protected static void doScpCopyTo(Session session, String local, String remote)
        throws JSchException, IOException {

        // exec 'scp -t rfile' remotely
        String command = SCP_TO + remote;

        Channel channel = null;
        try {
            channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);

            // get I/O streams for remote scp
            OutputStream out = channel.getOutputStream();
            InputStream in = channel.getInputStream();

            channel.connect();

            assertNormalResponse(in);

            File localFile = new File(local);
            long localFileSize = localFile.length();
            String filename = StringUtils.replace(local, "\\", "/");
            if (StringUtils.contains(filename, "/")) { filename = StringUtils.substringAfterLast(filename, "/"); }
            send(out, "C0644 " + localFileSize + " " + filename + "\n");

            assertNormalResponse(in);

            byte[] buf = FileUtils.readFileToByteArray(localFile);
            IOUtils.write(buf, out);
            out.flush();

            // send '\0'
            send(out, (byte) 0);

            assertNormalResponse(in);
        } finally {
            if (channel != null) { channel.disconnect(); }
        }

    }

    /**
     * check input stream:
     * 0 for success,
     * 1 for error,
     * 2 for fatal error,
     * -1 for failed (??)
     */
    protected static int checkAck(InputStream in) throws IOException {
        int b = in.read();
        if (b == 0) { return b; }
        if (b == -1) { return b; }

        String errorMessage = null;
        if (b == 1 || b == 2) {
            StringBuilder sb = new StringBuilder();

            int c;
            do {
                c = in.read();
                sb.append((char) c);
            } while (c != '\n');

            // error
            if (b == 1) { errorMessage = sb.toString(); }

            // fatal error
            if (b == 2) { errorMessage = sb.toString(); }

            if (StringUtils.isNotEmpty(errorMessage)) { throw new IOException(errorMessage); }
        }

        return b;
    }

    protected static String deriveFilename(InputStream in) throws IOException {
        int maxFilenameLength = 1024;
        byte[] buf = new byte[maxFilenameLength];

        for (int i = 0; ; i++) {
            in.read(buf, i, 1);

            if (buf[i] < 0) { throw new IOException("Invalid input found: " + buf[i]); }

            if (i >= (buf.length - 1)) {
                throw new IOException("Unable to derive file name after reading " + maxFilenameLength +
                                      " characters from input stream");
            }

            if (buf[i] == (byte) 0x0a) { return new String(buf, 0, i); }
        }
    }

    protected static long deriveFileSize(InputStream in) throws IOException {
        byte[] buf = new byte[10];
        long filesize = 0L;
        while (true) {
            // error
            if (in.read(buf, 0, 1) < 0) { break; }
            if (buf[0] == ' ') { break; }
            filesize = filesize * 10L + (long) (buf[0] - '0');
        }
        return filesize;
    }

    protected static void send(OutputStream out, byte b) throws IOException {
        out.write(new byte[]{b}, 0, 1);
        out.flush();
    }

    protected static void send(OutputStream out, String data) throws IOException {
        out.write(data.getBytes());
        out.flush();
    }

    /**
     * read but throw away...
     */
    protected static void forwardOffset(InputStream in, int forwardFrom, int forwardBy) throws IOException {
        byte[] buffer = new byte[forwardFrom + forwardBy];
        in.read(buffer, forwardFrom, forwardBy);
    }

    private static void assertNormalResponse(InputStream in) throws IOException {
        if (checkAck(in) != 0) { throw new IOException("Unable to fetch expected response from remote hosts"); }
    }
}