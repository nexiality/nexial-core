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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.utils.ConsoleUtils;

import static java.io.File.separator;
import static org.apache.commons.io.filefilter.DirectoryFileFilter.DIRECTORY;
import static org.nexial.core.NexialConst.DF_TIMESTAMP;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.PREFIX_JAR;

enum IoAction {
    copy(true, true), move(true), delete(false), deleteRecursive(false);

    boolean targetRequired;
    boolean jarSupported;
    String copyConfig;

    public void setCopyConfig(String copyConfig) { this.copyConfig = copyConfig; }

    IoAction(boolean targetRequired) { this.targetRequired = targetRequired; }

    IoAction(boolean targetRequired, boolean jarSupported) {
        this.targetRequired = targetRequired;
        this.jarSupported = jarSupported;
    }

    boolean isTargetRequired() { return targetRequired; }

    void doAction(Collection<File> sourceFiles, File targetDir) throws IOException {
        if (targetRequired && targetDir.isFile()) {
            if (CollectionUtils.size(sourceFiles) > 1) {
                throw new IOException("source represents multiple files, hence target must be a directory");
            }
        }

        for (File file : sourceFiles) {
            ConsoleUtils.log(this + " " + file + (targetDir != null ? " to " + targetDir : ""));

            String filepath = file.getAbsolutePath();
            InputStream input = null;
            if (jarSupported && StringUtils.contains(filepath, ".jar!")) {
                String resource = StringUtils.substringAfter(filepath, ".jar!/");
                if (StringUtils.isBlank(resource)) {
                    resource = StringUtils.replace(StringUtils.substringAfter(filepath, ".jar!\\"), "\\", "/");
                }
                input = this.getClass().getResourceAsStream("/" + resource);
                if (input == null) { throw new IOException("Specified jar resource not found: " + filepath); }
            }

            switch (this) {
                case copy:
                    if (input != null) {
                        String filename = StringUtils.substringAfterLast(filepath, "/");
                        if (StringUtils.isBlank(filename)) {
                            filename = StringUtils.substringAfterLast(filepath, "\\");
                        }
                        FileOutputStream out = null;
                        try {
                            out = new FileOutputStream(StringUtils
                                                           .appendIfMissing(targetDir.getAbsolutePath(), separator) +
                                                       filename);
                            int bytesCopied = IOUtils.copy(input, out);
                            ConsoleUtils.log("copied " + bytesCopied + " bytes for " + filepath + " to " + targetDir);
                        } finally {
                            input.close();
                            if (out != null) { out.close(); }
                        }
                    } else {
                        if (targetDir != null && targetDir.isDirectory()) {
                            File targetFile = new File(targetDir.getAbsoluteFile() + separator + file.getName());
                            if (!copyByConfig(targetFile)) { return; }
                            FileUtils.copyFileToDirectory(file, targetDir);
                        } else {
                            if (!copyByConfig(targetDir)) { return; }
                            FileUtils.copyFile(file, targetDir);
                        }
                    }
                    break;
                case move:
                    if (targetDir != null && targetDir.isDirectory()) {
                        File targetFile = new File(targetDir.getAbsoluteFile() + separator + file.getName());
                        if (!copyByConfig(targetFile)) { return; }
                        FileUtils.moveFileToDirectory(file, targetDir, false);
                    } else {
                        if (targetDir != null && copyByConfig(targetDir)) {
                            FileUtils.moveFile(file, targetDir);
                        }
                    }
                    break;
                case delete:
                    FileUtils.forceDelete(file);
                    break;
                case deleteRecursive:
                default:
                    throw new IOException(this + " is not a supported action for multi-file sources");
            }
        }
    }

    void doAction(File sourceDir, File targetDir) throws IOException {
        if (targetRequired && targetDir.isFile()) {
            throw new IOException("EXPECTS target as directory since source is either a directory or a set of files");
        }

        switch (this) {
            case copy:
                if (sourceDir.isFile()) {
                    if (targetDir.isDirectory()) {
                        FileUtils.copyFileToDirectory(sourceDir, targetDir);
                    } else {
                        FileUtils.copyFile(sourceDir, targetDir);
                    }
                } else {
                    FileUtils.copyDirectory(sourceDir, targetDir);
                }
                ConsoleUtils.log("copied directory '" + sourceDir + "' to '" + targetDir + "'");
                break;
            case move:
                if (sourceDir.isFile()) {
                    if (targetDir.isDirectory()) {
                        FileUtils.moveFileToDirectory(sourceDir, targetDir, false);
                    } else {
                        FileUtils.moveFile(sourceDir, targetDir);
                    }
                } else {
                    FileUtils.moveDirectory(sourceDir, targetDir);
                }
                ConsoleUtils.log("moved directory '" + sourceDir + "' to '" + targetDir + "'");
                break;
            case delete:
                Collection<File> deleteTargets = FileUtils.listFiles(sourceDir, null, false);
                for (File target : deleteTargets) {
                    FileUtils.forceDelete(target);
                    ConsoleUtils.log("deleted file '" + target + "'");
                }
                break;
            case deleteRecursive:
                FileUtils.deleteDirectory(sourceDir);
                ConsoleUtils.log("deleted directory '" + sourceDir + "'");
                break;
            default:
                throw new IOException(this + " is not a supported action");
        }
    }

    /** support file patterns */
    Collection<File> listFilesByPattern(String dirAndPattern) {
        if (jarSupported && StringUtils.startsWith(dirAndPattern, PREFIX_JAR)) {
            String resourceName = StringUtils.substringAfter(dirAndPattern, PREFIX_JAR);
            return Collections.singleton(new File(this.getClass().getResource(resourceName).getFile()));
        }

        String sourceDirString = StringUtils.substringBeforeLast(dirAndPattern, separator);
        String pattern = StringUtils.substringAfterLast(dirAndPattern, separator);
        return FileUtils.listFiles(new File(sourceDirString), new RegexFileFilter(pattern), DIRECTORY);
    }

    /*Supports taking backup, overriding files while default is no copy(keep as is)*/
    boolean copyByConfig(File file) throws IOException {
        if (!file.exists()) { return true; }

        switch (copyConfig.toLowerCase()) {
            case COPY_CONFIG_BACKUP:
                String timestamp = DF_TIMESTAMP.format(new Date());
                String path = file.getParent() + separator + FilenameUtils.getBaseName(file.getAbsolutePath())
                              + "- [" + timestamp + "]." + FilenameUtils.getExtension(file.getAbsolutePath());
                File newTarget = new File(path);
                if (newTarget.exists()) { FileUtils.forceDelete(newTarget); }
                file.renameTo(newTarget);
                ConsoleUtils.log("renamed " + file.getAbsolutePath() + " to " + newTarget.getAbsolutePath());
                break;
            case COPY_CONFIG_OVERRIDE:
                FileUtils.forceDelete(file);
                break;
            case COPY_CONFIG_KEEP_ORIGINAL:
            default:
                ConsoleUtils.log("There is already a file with path '" + file.getAbsolutePath() +
                                 "', hence keeping original file");
                return false;
        }
        return true;
    }
}
