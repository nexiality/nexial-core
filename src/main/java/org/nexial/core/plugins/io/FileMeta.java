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

package org.nexial.core.plugins.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.NL;

public class FileMeta implements Serializable {
    protected static final String REGEX_FILE_META = "(name|size|lastMod)\\s+";
    private File fileObject;
    private byte[] rawContent;

    private enum filter {size, lastMod, name}

    protected FileMeta() { }

    public FileMeta(File f) throws IOException {
        if (f == null) { throw new FileNotFoundException("cannot evaluate null as file object"); }
        if (!f.exists()) {
            throw new FileNotFoundException("cannot evaluate non-existent file: " + f.getAbsolutePath());
        }

        this.fileObject = f;
        rawContent = FileUtils.readFileToByteArray(f);
    }

    public String getFullpath() { return fileObject.getAbsolutePath(); }

    public String getName() { return fileObject.getName(); }

    public long getLastmod() { return fileObject.lastModified(); }

    public long getSize() { return fileObject.length(); }

    public boolean isDir() { return fileObject.isDirectory(); }

    public boolean canRead() { return fileObject.canRead(); }

    public boolean isReadable() { return canRead(); }

    public boolean canWrite() { return fileObject.canWrite(); }

    public boolean isWritable() { return canWrite(); }

    public boolean executable() { return fileObject.canExecute(); }

    public boolean isExecutable() { return executable(); }

    public String getPerm() { return canRead() + "," + canWrite() + "," + executable(); }

    public byte[] getBytes() { return rawContent; }

    public String getText() { return new String(rawContent); }

    @Override
    public String toString() {
        return "fullpath='" + getFullpath() + "'," + NL +
               "isDir=" + isDir() + "," + NL +
               "size=" + getSize() + "," + NL +
               "lastmod=" + getLastmod() + "," + NL +
               "perm(rwe)=" + getPerm() + NL +
               "text=" + (StringUtils.isEmpty(getText()) ? "<EMPTY>" : StringUtils.left(getText(), 500) + "..." + NL);
    }

    protected static String findFileMetaData(String subject, File file) {
        try {
            switch (filter.valueOf(subject)) {
                case size:
                    return Long.toString(file.length());
                case lastMod:
                    return Long.toString(file.lastModified());
                case name:
                    return file.getName();
                default:
                    return null;
            }
        } catch (IllegalArgumentException e) {
            ConsoleUtils.error("Unsupported file meta: " + subject);
            return null;
        }
    }
}
