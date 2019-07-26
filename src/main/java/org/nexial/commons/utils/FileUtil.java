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

package org.nexial.commons.utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.io.File.separator;
import static org.apache.commons.io.comparator.LastModifiedFileComparator.LASTMODIFIED_COMPARATOR;
import static org.apache.commons.io.comparator.LastModifiedFileComparator.LASTMODIFIED_REVERSE;
import static org.apache.commons.io.comparator.NameFileComparator.NAME_COMPARATOR;
import static org.apache.commons.io.comparator.NameFileComparator.NAME_REVERSE;
import static org.apache.commons.io.comparator.PathFileComparator.PATH_COMPARATOR;
import static org.apache.commons.io.comparator.PathFileComparator.PATH_REVERSE;
import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;

/**
 *
 */
public final class FileUtil {
    /**
     * options to sort files in different ways
     *
     * @see FileUtil#listFiles(String, String, boolean, SortBy)
     */
    public enum SortBy {
        PATH_AND_FILE_ASC(PATH_COMPARATOR),
        PATH_AND_FILE_DESC(PATH_REVERSE),
        FILENAME_ASC(NAME_COMPARATOR),
        FILENAME_DESC(NAME_REVERSE),
        LASTMODIFIED_ASC(LASTMODIFIED_COMPARATOR),
        LASTMODIFIED_DESC(LASTMODIFIED_REVERSE);
        private Comparator<File> comparator;

        SortBy(Comparator<File> comparator) { this.comparator = comparator; }

        Comparator<File> getComparator() { return comparator; }
    }

    public interface LineTransformer<T> {
        T transform(T line);
    }

    public interface LineFilter<T> {
        boolean filter(T line);
    }

    private FileUtil() {}

    // Remove the created temporary File.
    //public static void removeTempFile(File file) { file.delete(); }

    /** Create a new file with the content retrieved from the database. */
    public static void createNewFile(File file, String content) throws Exception {
        FileOutputStream fop = FileUtils.openOutputStream(file);
        if (file.exists()) {
            fop.write(content.getBytes());
            fop.flush();
            fop.close();
        }
    }

    public static int deleteFiles(String directory, String filenameRegex, boolean recursive, boolean failFast)
        throws IOException {
        if (StringUtils.isBlank(directory)) { return 0; }

        Collection matched = listFiles(directory, filenameRegex, recursive);
        int deleted = 0;
        int matchedCnt = 0;
        StringBuilder msgs = new StringBuilder();

        Logger logger = LoggerFactory.getLogger(FileUtil.class);

        for (Object matchedFile : matched) {
            if (logger.isDebugEnabled()) { logger.debug("deleting " + matchedFile + "..."); }
            matchedCnt++;
            try {
                FileUtils.forceDelete((File) matchedFile);
                deleted++;
            } catch (IOException e) {
                logger.warn(matchedFile + " could not be deleted", e);
                msgs.append(matchedFile).append(" could not be deleted");
                if (failFast) {throw e;}
            }
        }
        if (matchedCnt != deleted) {
            msgs.insert(0, matchedCnt + " files matched but only " + deleted + " files were deleted");
            throw new IOException(msgs.toString());
        }

        return deleted;
    }

    public static int deleteFilesQuietly(String directory, String filenameRegex, boolean recursive, boolean tryRename) {
        if (StringUtils.isBlank(directory)) { return 0; }

        Collection matched = listFiles(directory, filenameRegex, recursive);
        int deleted = 0;

        Logger logger = LoggerFactory.getLogger(FileUtil.class);

        for (Object matchedFile : matched) {
            if (logger.isDebugEnabled()) { logger.debug("deleting " + matchedFile + "..."); }
            deleted += FileUtils.deleteQuietly((File) matchedFile) ? 1 : 0;
            if (deleted == 0 && tryRename) {
                try {
                    FileUtils.moveFile((File) matchedFile,
                                       new File(((File) matchedFile).getAbsolutePath() + "_canceled"));
                } catch (Exception e) {
                    logger.warn("Both file delete and file rename operations have failed", e);
                }
            }
            if (logger.isDebugEnabled()) { logger.debug("File delete count " + deleted); }
        }

        return deleted;
    }

    public static List<File> listFiles(String directory, final String filenameRegex, final boolean recursive) {
        if (StringUtils.isBlank(directory)) { return new ArrayList<>(); }

        final File path = new File(directory);
        final boolean detectAllFiles = StringUtils.isBlank(filenameRegex);

        IOFileFilter fileFilter = new IOFileFilter() {
            public boolean accept(File file) {
                return file.isFile() && (detectAllFiles || file.getName().matches(filenameRegex));
            }

            public boolean accept(File dir, String name) {
                boolean isSamePath = dir.getAbsolutePath().equals(path.getAbsolutePath());
                if (detectAllFiles) {
                    return recursive ?
                           StringUtils.startsWith(dir.getAbsolutePath(), path.getAbsolutePath()) :
                           isSamePath;
                }

                boolean matched = name.matches(filenameRegex);
                return recursive ? matched : isSamePath && matched;
            }
        };

        IOFileFilter dirFilter =
            recursive ?
            DirectoryFileFilter.INSTANCE :
            new IOFileFilter() {
                public boolean accept(File file) {
                    return file.isAbsolute() && file.getAbsoluteFile().equals(path.getAbsoluteFile());
                }

                public boolean accept(File dir, String name) { return name.equals(path.getName()); }
            };

        Collection<File> matched = FileUtils.listFiles(path, fileFilter, dirFilter);
        List<File> files = new ArrayList<>();
        matched.stream().filter(Objects::nonNull).forEach(files::add);
        return files;
    }

    public static List<File> listFiles(String directory, final String filenameRegex, final boolean recursive,
                                       SortBy sortBy) {
        List<File> files = listFiles(directory, filenameRegex, recursive);
        if (sortBy != null) { files.sort(sortBy.getComparator()); }
        return files;
    }

    /**
     * append {@code appendWith} to the end of {@code filename}, but before the file extension. If {@code filename}
     * does not contain extension, then {@code appendWith} will be added directly to the end of {@code filename}.
     */
    public static String appendToFileName(String filename, String appendWith) {
        if (StringUtils.isBlank(filename)) { return filename; }
        if (StringUtils.isBlank(appendWith)) { return filename; }
        if (StringUtils.contains(filename, ".")) {
            return StringUtils.substringBeforeLast(filename, ".") + appendWith + "." +
                   StringUtils.substringAfterLast(filename, ".");
        } else {
            return filename + appendWith;
        }
    }

    public static void appendFile(File file, List<String> lines, String lineSep) throws IOException {
        if (file == null) { return; }
        if (CollectionUtils.isEmpty(lines)) { return; }
        if (lineSep == null) { lineSep = "\n"; }

        StringBuilder buffer = new StringBuilder();
        for (String line : lines) { buffer.append(line).append(lineSep); }
        FileUtils.writeStringToFile(file, buffer.toString(), DEF_CHARSET, true);
    }

    /**
     * poor man's method to preprend {@code lines} to {@code file}.  The implementation reads content from {@code file}
     * and add it to the end of {@code lines} before writing the whole thing back to the {@code file}.
     */
    public static void prependFile(File file, List<String> lines, String lineSep) throws IOException {
        if (file == null) { return; }
        if (CollectionUtils.isEmpty(lines)) { return; }
        if (lineSep == null) { lineSep = "\n"; }

        StringBuilder buffer = new StringBuilder();
        for (String line : lines) { buffer.append(line).append(lineSep); }

        buffer.append(file.length() > 0 ? FileUtils.readFileToString(file, DEF_CHARSET) : "");
        FileUtils.writeStringToFile(file, buffer.toString(), DEF_CHARSET, false);
    }

    /** return true if {@code path} is a valid directory and readable by the current run user. */
    public static boolean isDirectoryReadable(String path) {
        return !StringUtils.isBlank(path) && isDirectoryReadable(new File(path));
    }

    public static boolean isDirectoryReadable(File dir) {
        return dir != null && dir.exists() && dir.isDirectory() && dir.canRead();
    }

    /** return true if {@code path} is a valid directory and read/writable by the current run user. */
    public static boolean isDirectoryReadWritable(String path) {
        return !StringUtils.isBlank(path) && isDirectoryReadWritable(new File(path));
    }

    public static boolean isDirectoryReadWritable(File dir) {
        return dir != null && dir.exists() && dir.isDirectory() && dir.canRead() && dir.canWrite();
    }

    public static boolean isFileReadable(File file, long minFileSize) {
        return file != null &&
               file.exists() &&
               file.isFile() &&
               file.canRead() &&
               (minFileSize < 0 || file.length() >= minFileSize);
    }

    public static boolean isFileReadable(File file) { return isFileReadable(file, -1); }

    /**
     * return true if {@code file} is readable and larger than {@code minFileSize} bytes.  If {@code minFileSize}
     * is -1, then it's ignored.
     */
    public static boolean isFileReadable(String file, long minFileSize) {
        return StringUtils.isNotBlank(file) && isFileReadable(new File(file), minFileSize);
    }

    /** return true if {@code file} is readable. */
    public static boolean isFileReadable(String file) { return isFileReadable(file, -1); }

    public static boolean isFileExecutable(String file) {
        return StringUtils.isNotBlank(file) && isFileExecutable(new File(file));
    }

    public static boolean isFileExecutable(File file) {
        return file != null && file.exists() && file.isFile() && file.canRead() && file.canExecute();
    }

    public static boolean isFileReadWritable(String file, int minFileSize) {
        return FileUtil.isFileReadable(file, minFileSize) && new File(file).canWrite();
    }

    /**
     * collect the content of {@literal file} via a {@literal filter} and a {@literal transformer}. Internally uses
     * stream instead of full-read.
     */
    public static List<String> filterAndTransform(File file,
                                                  LineFilter<String> filter,
                                                  LineTransformer<String> transformer) throws IOException {

        if (!FileUtil.isFileReadable(file, 1)) { return null; }
        if (filter == null || transformer == null) { return FileUtils.readLines(file, DEF_FILE_ENCODING); }
        return Files.lines(Paths.get(file.getAbsolutePath()))
                    .filter(filter::filter)
                    .map(transformer::transform)
                    .collect(Collectors.toList());
    }

    public static List<File> unzip(File zip, File target) throws IOException {

        // int unzipCount = 0;
        List<File> unzipped = new ArrayList<>();

        //buffer for read and write data to file
        byte[] buffer = new byte[1024 * 8];

        FileInputStream fis = null;
        ZipInputStream zis = null;
        try {
            fis = new FileInputStream(zip);
            zis = new ZipInputStream(fis);

            ZipEntry ze = zis.getNextEntry();
            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(target.getAbsolutePath() + separator + fileName);
                ConsoleUtils.log("[" + zip + "]: Unzipping to " + newFile.getAbsolutePath());

                //create directories for sub directories in zip
                new File(newFile.getParent()).mkdirs();

                if (ze.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    FileOutputStream fos = FileUtils.openOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) { fos.write(buffer, 0, len); }
                    fos.close();
                }

                unzipped.add(newFile);

                //close this ZipEntry
                zis.closeEntry();
                try {
                    ze = zis.getNextEntry();
                } catch (ZipException e) {
                    ConsoleUtils.log("Unable to progress to next entry: " + e.getMessage());
                }
            }

            return unzipped;
        } finally {
            //close last ZipEntry
            if (zis != null) {
                try { zis.closeEntry(); } catch (IOException e) { }
                try {
                    zis.close();
                } catch (IOException e) {
                    ConsoleUtils.error("Error closing zip input stream: " + e.getMessage());
                }
            }

            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    ConsoleUtils.error("Error closing file input stream: " + e.getMessage());
                }
            }
        }
    }

    /**
     * same as {@link #unzip(File, File)}, except this method verifies after unzipping {@code zip} that the
     * {@code expected} exists and are readable (IOW, unsuccessfully unzipped). If any of the {@code expected} files
     * are not found after unzip, {@link IOException} will be thrown
     */
    public static void unzip(File zip, File target, List<File> expected) throws IOException {
        List<File> unzipped = unzip(zip, target);

        if (CollectionUtils.isEmpty(expected)) { return; }

        if (CollectionUtils.isEmpty(unzipped)) { throw new IOException("Unable to unzip any file from " + zip); }

        List<String> expectedFiles = new ArrayList<>();
        expected.forEach(file -> expectedFiles.add(file.getAbsolutePath()));

        for (File uncompressed : unzipped) {
            String uncompressedFile = uncompressed.getAbsolutePath();
            if (expectedFiles.contains(uncompressedFile)) {
                if (!FileUtil.isFileReadable(uncompressedFile)) {
                    throw new IOException("Unable to unzip an usable " + uncompressedFile + " from " + zip);
                }
                expectedFiles.remove(uncompressedFile);
            }
        }

        if (CollectionUtils.isNotEmpty(expectedFiles)) {
            throw new IOException("After unzipping " + zip + " these files still unaccounted for: " + expectedFiles);
        }
    }

    public static void zip(String filePattern, String targetZipFile) throws IOException {
        if (StringUtils.isBlank(filePattern)) { throw new IOException("Invalid filePattern: " + filePattern); }
        if (StringUtils.isBlank(targetZipFile)) { throw new IOException("Invali target ZIP file: " + targetZipFile); }

        String dir;
        String files;

        // file pattern might be a directory, a directory with ending slash, file pattern, single file
        File test = new File(filePattern);
        if (test.isDirectory()) {
            // test for directory
            dir = StringUtils.appendIfMissing(test.getAbsolutePath(), separator);
            files = ".+";
        } else if (test.isFile()) {
            // test for single file
            dir = StringUtils.appendIfMissing(test.getParentFile().getAbsolutePath(), separator);
            files = test.getName();
        } else {
            // test for directory with end slash
            if (StringUtils.endsWith(filePattern, separator)) {
                throw new IOException("Invalid directory specified: " + filePattern);
            }

            // file pattern
            dir = StringUtils.appendIfMissing(StringUtils.substringBeforeLast(filePattern, separator), separator);
            test = new File(dir);
            if (!test.isDirectory()) { throw new IOException("Invalid directory specified in " + filePattern); }

            files = StringUtils.substringAfterLast(filePattern, separator);
        }

        if (StringUtils.isBlank(files)) { files = ".+"; }
        files = StringUtils.replace(files, ".*", "<>");
        files = StringUtils.replace(files, "*", ".*");
        files = StringUtils.replace(files, "<>", ".*");

        List<File> compressCandidates = listFiles(dir, files, true);

        // create byte buffer
        byte[] buffer = new byte[1024 * 8];

        FileOutputStream fos = null;
        ZipOutputStream zos = null;
        FileInputStream fis = null;

        try {
            fos = FileUtils.openOutputStream(new File(targetZipFile));
            zos = new ZipOutputStream(fos);

            for (File candidate : compressCandidates) {
                fis = new FileInputStream(candidate);

                // begin writing a new ZIP entry, positions the stream to the start of the entry data
                String fullpath = candidate.getAbsolutePath();
                String entryName = StringUtils.substringAfter(fullpath, dir);
                zos.putNextEntry(new ZipEntry(entryName));

                int length;
                while ((length = fis.read(buffer)) > 0) { zos.write(buffer, 0, length); }

                zos.closeEntry();

                // close the InputStream
                fis.close();
                fis = null;
            }
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    ConsoleUtils.error("Error closing candidate input stream: " + e.getMessage());
                }
            }

            // close the ZipOutputStream
            if (zos != null) {
                try {
                    zos.close();
                } catch (IOException e) {
                    ConsoleUtils.error("Error closing zip output stream: " + e.getMessage());
                }
            }

            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    ConsoleUtils.error("Error closing file output stream: " + e.getMessage());
                }
            }
        }
    }

    public static void addToJar(File source, JarOutputStream target, String parent)
        throws IOException {
        String name =
            StringUtils.substringAfter(
                StringUtils.appendIfMissing(source.getPath().replace("\\", "/"), "/").trim(),
                StringUtils.replace(parent, "\\", "/"));

        BufferedInputStream in = null;
        try {
            JarEntry entry = new JarEntry(name);
            entry.setTime(source.lastModified());
            target.putNextEntry(entry);

            if (source.isDirectory()) {
                target.closeEntry();
                File[] files = source.listFiles();
                if (files != null && files.length > 0) {
                    for (File nestedFile : files) { addToJar(nestedFile, target, parent); }
                }
                return;
            }

            in = new BufferedInputStream(new FileInputStream(source));

            byte[] buffer = new byte[1024];
            while (true) {
                int count = in.read(buffer);
                if (count == -1) { break; }
                target.write(buffer, 0, count);
            }

            target.closeEntry();
        } finally {
            if (in != null) { in.close(); }
        }
    }

    public static boolean isSuitableAsPath(String contentOrFile) {
        return StringUtils.containsNone(contentOrFile, '\n', '\r', '\t', '{', '}', '?', '<', '>', '|');
    }

    /**
     * extract just the file name portion of a path.
     *
     * The reason for this method is to allow for discovery of the "file name" without going to underlying OS.  The
     * {@code path} in question could represent a remote path, which cannot be validated locally.
     *
     * If {@code path} ends with path separator (/ or \), then an empty string is returned.
     */
    public static String extractFilename(String path) {
        if (StringUtils.isBlank(path)) { return ""; }

        if (StringUtils.contains(path, "\\")) { path = StringUtils.replace(path, "\\", "/"); }
        return !StringUtils.contains(path, "/") ? path : StringUtils.substringAfterLast(path, "/");
    }

    @NotNull
    public static File makeParentDir(String path) {
        File target = new File(path);
        if (!isDirectoryReadable(path)) {
            try {
                FileUtils.forceMkdirParent(target);
            } catch (IOException e) {
                throw new IllegalArgumentException("Unable to create directory for '" + path + "'");
            }
        }
        return target;
    }

    @NotNull
    public static File writeBinaryFile(String path, boolean append, byte[] bytes) {
        File target = makeParentDir(path);

        DataOutputStream dos = null;
        try {
            dos = new DataOutputStream(new FileOutputStream(target, append));
            dos.write(bytes);

            ConsoleUtils.log("content base64 decoded and " + (append ? "appended" : "saved") +
                             " to '" + path + "'");
            return target;
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to write to " + path + ": " + e.getMessage(), e);
        } finally {
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException e) {
                    ConsoleUtils.error("Error occurred while saving '" + path + "': " + e.getMessage());
                }
            }
        }
    }
}
