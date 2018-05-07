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

package org.nexial.core.tools;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.validation.constraints.NotNull;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;

/**
 * This class is a utility for encrypting the setup/data file with the given key file passed as options
 * through command line.
 */
public final class NexialSetup {
    private static final String OPT_DATA_FILE = "f";
    private static final String OPT_SETUP_KEY = "k";

    private static final String SETUP_CLASS_TEMPLATE =
        "package org.nexial.core.config;\n" +
        "\n" +
        "import java.util.Map;\n" +
        "import org.nexial.core.tools.EncryptionUtility;\n" +
        "import org.apache.commons.codec.binary.Hex;\n" +
        "\n" +
        "class Setup {\n" +
        "    static {\n" +
        "\t    try {\n" +
        "%s\n\n" +
        "%s\n" +
        "\t\t\tMap<String, String> properties = " +
        "EncryptionUtility.retrieveEncryptedSecrets(new String(encArr), " +
        "new Hex().encode(keyArr));\n" +
        "\t\t\tproperties.keySet().forEach(property -> System.setProperty(property, properties.get(property)));\n" +
        "\t\t} catch(Exception e) {\n" +
        "\t\t\t System.err.println(\"Error is \" + e.getMessage());" +
        "\t\t}\n" +
        "    }\n" +
        "}";

    private static final String SETUP_FOLDER = "org";
    private static final String SETUP_FILE_PATH = SETUP_FOLDER + "/nexial/core/config/";
    private static final String SETUP_JAR = "setup.jar";
    private static final String TEMP = System.getProperty("java.io.tmpdir");
    private static final String ENV_NEXIAL_LIB = "NEXIAL_LIB";
    private static final String ENV_NEXIAL_HOME = "NEXIAL_HOME";

    /**
     * The following steps take place.
     * <ul>
     * <li>Validates if the data file path is valid or not. If it is valid the flow continues. Else terminates.</li>
     * <li>The properties in the file are encrypted.</li>
     * <li>A folder structure as mentioned in the {@link NexialSetup#SETUP_FOLDER} is created.</li>
     * <li>A Setup file is created as mentioned in the template {@link NexialSetup#SETUP_CLASS_TEMPLATE}.</li>
     * <li>The class is compiled and the file is deleted. A jar is created out of
     * {@link NexialSetup#SETUP_FOLDER} with the name as in {@link NexialSetup#SETUP_JAR}.</li>
     * <li>The folder {@link NexialSetup#SETUP_FOLDER} is then deleted.</li>
     * <li>The {@link NexialSetup#SETUP_JAR} is then moved to lib directory.</li>
     * </ul>
     *
     * @param args The data file containing the properties and the data file given as -d and -f {@link Options}.
     */
    public static void main(String[] args) {
        Options cmdOptions = new Options();
        cmdOptions.addOption(CliUtils.newArgOption(OPT_DATA_FILE, "file",
                                                   "The file containing key/value pairs."));
        cmdOptions.addOption(CliUtils.newArgOption(OPT_SETUP_KEY, "key",
                                                   "The key to encrypt data."));

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(cmdOptions, args);

            final String dataFilePath = cmd.getOptionValue(OPT_DATA_FILE);
            final String secretKeyValue = cmd.getOptionValue(OPT_SETUP_KEY);
            checkValidFilePath(dataFilePath);

            String properties = new String(Files.readAllBytes(Paths.get(dataFilePath)));
            String encryptedSecrets = EncryptionUtility.encryptContent(properties, secretKeyValue.getBytes());

            File destination = new File(SETUP_FILE_PATH);
            if (destination.exists()) {
                FileUtils.deleteDirectory(destination);
            }

            int counter = 0;
            StringBuilder byteLogic = new StringBuilder();
            byteLogic.append("\n char[] encArr = new char[").append(encryptedSecrets.length()).append("];\n");

            for (char keyChar : encryptedSecrets.toCharArray()) {
                byteLogic.append("\t\t\tencArr[")
                         .append(counter).append("] = ").append("'")
                         .append(keyChar).append("'").append(";\n");
                ++counter;
            }

            String encryptString = byteLogic.toString();

            boolean dirCreated = destination.mkdirs();
            if (dirCreated) {
                String setupFile = destination.getPath() + "/Setup.java";
                byteLogic = new StringBuilder();

                byteLogic.append("\n byte[] keyArr = new byte[").append(secretKeyValue.length()).append("];\n");
                counter = 0;

                for (char keyChar : secretKeyValue.toCharArray()) {
                    byteLogic.append("\t\t\tkeyArr[")
                             .append(counter).append("] = (byte)").append("'")
                             .append(String.valueOf(keyChar)).append("'").append(";\n");
                    ++counter;
                }

                String setUpCode = String.format(SETUP_CLASS_TEMPLATE, byteLogic.toString(), encryptString);
                Files.write(Paths.get(setupFile), setUpCode.getBytes());

                compileSetupClass(setupFile);
                buildJar();

                File jarSource = new File(TEMP + SETUP_JAR);
                File jarDestination = new File(System.getenv(ENV_NEXIAL_LIB) + "/" + SETUP_JAR);

                FileUtils.moveFile(jarSource, jarDestination);

                System.out.println("WARNING: Setup complete. It is now safe to delete " + dataFilePath +
                                   " to keep it out of prying eyes. You can zip up " + System.getenv(ENV_NEXIAL_HOME) +
                                   " and distribute it with your team.");
            } else {
                System.err.println("Failed to create folder structure " + SETUP_FILE_PATH);
            }
        } catch (Exception e) {
            displayExceptionDetails(e);
        } finally {
            deleteFoldersRecursively(SETUP_FOLDER);
        }
    }

    /**
     * Compiles the {@link NexialSetup#SETUP_FILE_PATH}.
     *
     * @param setupFile path to be compiled.
     */
    private static void compileSetupClass(@NotNull final String setupFile) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

        // prepare the source file(s) to compile
        List<File> sourceFileList = new ArrayList<>();
        sourceFileList.add(new File(setupFile));

        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(sourceFileList);
        CompilationTask task = compiler.getTask(null,
                                                fileManager,
                                                null,
                                                Arrays.asList("-classpath", System.getProperty("java.class.path")),
                                                null,
                                                compilationUnits);

        Boolean result = task.call();
        if (!result) {
            System.err.println("Compilation failed for " + setupFile);
            deleteFoldersRecursively(SETUP_FOLDER);
        }

        try {
            fileManager.close();
        } catch (IOException e) {
            System.err.println("Unable to compile the file " + setupFile);
            displayExceptionDetails(e);
        }

        boolean fileDeleted = new File(setupFile).delete();
        if (!fileDeleted) {
            System.err.println("WARNING: " + setupFile + " is not deleted from the folder structure.");
        }
    }

    /**
     * Creates a jar corresponding to the {@link NexialSetup#SETUP_FOLDER} with the name as
     * {@link NexialSetup#SETUP_JAR}.
     *
     * @throws Exception exception occurred.
     */
    private static void buildJar() throws Exception {
        String setupJarPath = TEMP + SETUP_JAR;
        File jarFile = new File(setupJarPath);

        if (jarFile.exists()) { jarFile.delete(); }

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        JarOutputStream target = new JarOutputStream(new FileOutputStream(jarFile), manifest);
        add(new File(SETUP_FOLDER), target);
        target.close();
    }

    /**
     * Adds the <b>source</b> file/folder to the <b>target</b> {@link JarOutputStream}.
     *
     * @param source file/folder passed in to add.
     * @param target the target {@link JarOutputStream} to which the source to be added.
     * @throws IOException exception occurred while adding the file.
     */
    private static void add(@NotNull File source, @NotNull JarOutputStream target) throws IOException {
        BufferedInputStream in = null;
        try {
            if (source == null) {
                System.err.println("File to be added to the jar cannot be null.");
                deleteFoldersRecursively(SETUP_FOLDER);
                System.exit(-1);
            }

            if (source.isDirectory()) {
                String name = StringUtils.appendIfMissing(source.getPath().replace("\\", "/"), "/");

                JarEntry entry = new JarEntry(name);
                entry.setTime(source.lastModified());
                target.putNextEntry(entry);
                target.closeEntry();

                for (File nestedFile : source.listFiles()) { add(nestedFile, target); }
                return;
            }

            JarEntry entry = new JarEntry(source.getPath().replace("\\", "/"));
            entry.setTime(source.lastModified());
            target.putNextEntry(entry);
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

    /**
     * Deletes the folder contents recursively.
     *
     * @param folderPath the path of the folder to be deleted.
     */
    private static void deleteFoldersRecursively(@NotNull final String folderPath) {
        if (StringUtils.isEmpty(folderPath)) {
            System.err.println("Folder path should not be null or empty.");
            System.exit(-1);
        }

        File folder = new File(folderPath);
        try {
            FileUtils.deleteDirectory(folder);
        } catch (IOException e) {
            System.err.println("Problem occurred when deleting the directory : " + folder);
            displayExceptionDetails(e);
        }
    }

    /**
     * Displays the details of the exception on the console.
     *
     * @param e{@link Exception} occurred.
     */
    private static void displayExceptionDetails(Exception e) {
        System.err.println("Exception is " + e.getMessage());
        System.exit(-1);
    }

    /**
     * Checks if the filePath passed in specifies a valid file which exists as well as having write permissions to
     * overwrite. If not the program exits with appropriate message.
     *
     * @param filePath path of the file.
     */
    private static void checkValidFilePath(@NotNull final String filePath) {
        if (!FileUtil.isFileReadable(filePath)) {
            System.err.println("File " + filePath + " is not appropriate." +
                               " Please check if the file is valid and has appropriate permissions");
            System.exit(-1);
        }
    }
}
