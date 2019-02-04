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
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.validation.constraints.NotNull;

import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.excel.ext.CipherHelper;

import static java.io.File.separator;
import static java.util.jar.Attributes.Name.MANIFEST_VERSION;
import static javax.crypto.Cipher.ENCRYPT_MODE;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.ExitStatus.*;
import static org.nexial.core.utils.ExecUtils.BIN_SCRIPT_EXT;

/**
 * This class is a utility for encrypting the setup/data file with the given key file passed as options
 * through command line.
 */
public final class NexialSetup {
    private static final String OPT_DATA_FILE = "f";
    private static final String OPT_SETUP_KEY = "k";

    private static final String SETUP_CLASS_TEMPLATE =
        "package org.nexial.core.config;\n" +
        "import java.io.StringReader;\n" +
        "import java.security.Security;\n" +
        "import java.util.Properties;\n" +
        "import javax.crypto.Cipher;\n" +
        "import javax.crypto.spec.SecretKeySpec;\n" +
        "import org.nexial.core.excel.ext.CipherHelper;\n" +
        "class Setup {\n" +
        "  static {\n" +
        "    try {\n" +
        "      Security.setProperty(\"crypto.policy\", \"unlimited\");\n" +
        "      %s\n" +
        "      %s\n" +
        "      Cipher cipher = Cipher.getInstance(\"AES\");\n" +
        "      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(parm2, \"AES\"));\n" +
        "      Properties properties = new Properties();\n" +
        "      properties.load(new StringReader(new CipherHelper().decrypt(new String(parm1), cipher)));\n" +
        "      properties.forEach((key, value) -> System.setProperty(key.toString(), value.toString()));\n" +
        "    } catch(Exception e) { System.err.println(\"Error is \" + e.getMessage()); }\n" +
        "  }\n" +
        "}";

    private static final String TEMP = StringUtils.appendIfMissing(System.getProperty("java.io.tmpdir"), separator);
    private static final String SETUP_FOLDER = TEMP + "nexial-setup-src/";
    private static final String SETUP_FILE_PATH = SETUP_FOLDER + "org/nexial/core/config/";
    private static final String SETUP_FILE = "Setup.java";
    private static final String CLASS_FOLDER = TEMP + "nexial-setup-class/";
    private static final String SETUP_JAR = "setup.jar";
    private static final String ENCRYPTION_ALGORITHM = "AES";

    private static final String MSG_MISSING_ENV = "Missing environment details. Please be sure to run via " +
                                                  "bin/nexial-setup" + BIN_SCRIPT_EXT + " script";
    private static final String MSG_CANT_WRITE_TEMP = "Unable to read/write directory '" + TEMP + "' to generate " +
                                                      "setup artifacts.  Please fix permission and re-run again.";
    private static Options cmdOptions = initCmdOptions();

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
        // pre-requisite
        if (!ensureReadiness()) { System.exit(RC_BAD_CLI_ARGS); }

        File destination = new File(SETUP_FOLDER);
        if (destination.exists()) { FileUtils.deleteQuietly(destination); }

        boolean dirCreated = new File(SETUP_FILE_PATH).mkdirs();
        if (!dirCreated) {
            System.err.println("Failed to create folder structure " + SETUP_FILE_PATH);
            System.exit(RC_FAILURE_FOUND);
        }

        // happy days
        Security.setProperty("crypto.policy", "unlimited");

        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(cmdOptions, args);

            final String dataFilePath = cmd.getOptionValue(OPT_DATA_FILE);
            checkValidFilePath(dataFilePath);
            String properties = FileUtils.readFileToString(new File(dataFilePath), DEF_CHARSET);

            String key = cmd.getOptionValue(OPT_SETUP_KEY);
            switch ((int) Math.ceil(key.length() / 16.0)) {
                case 1: {
                    key = StringUtils.rightPad(key, 16, " ");
                    break;
                }
                case 2: {
                    key = StringUtils.rightPad(key, 32, " ");
                    break;
                }
                case 3: {
                    key = StringUtils.rightPad(key, 64, " ");
                    break;
                }
                default: {
                    key = StringUtils.rightPad(key, 128, " ");
                    break;
                }
            }
            String encrypted = encryptData(properties, key);

            StringBuilder parm1 = new StringBuilder("byte[] parm1=new byte[").append(encrypted.length()).append("];");

            byte[] bytes = encrypted.getBytes();
            for (int i = 0; i < bytes.length; i++) {
                parm1.append("parm1[").append(i).append("]=(byte)").append(bytes[i]).append(";");
            }

            StringBuilder parm2 = new StringBuilder("byte[] parm2=new byte[").append(key.length()).append("];");

            bytes = key.getBytes();
            for (int i = 0; i < bytes.length; i++) {
                parm2.append("parm2[").append(i).append("]=(byte)").append(bytes[i]).append(";");
            }

            String setUpCode = String.format(SETUP_CLASS_TEMPLATE, parm2.toString(), parm1.toString());

            String setupFile = SETUP_FILE_PATH + SETUP_FILE;
            Files.write(Paths.get(setupFile), setUpCode.getBytes());
            compileSetupClass(setupFile);
            buildJar();

            File targetJar = new File(System.getenv(ENV_NEXIAL_LIB) + "/" + SETUP_JAR);
            FileUtils.deleteQuietly(targetJar);
            FileUtils.moveFile(new File(CLASS_FOLDER + SETUP_JAR), targetJar);

            System.out.println("\n\n" +
                               "Setup complete.\n\n" +
                               "IMPORTANT NOTE:\n" +
                               "> It is now safe to delete " + dataFilePath + "\n" +
                               "> to keep it out of prying eyes.\n" +
                               "> You can zip up " + System.getenv(ENV_NEXIAL_HOME) + " for distribution.\n" +
                               "\n");
        } catch (ParseException e) {
            System.err.println("\nERROR: " + e.getMessage() + "\n");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(NexialSetup.class.getName(), cmdOptions, true);
            System.exit(RC_BAD_CLI_ARGS);
        } catch (Exception e) {
            displayExceptionDetails(e);
        } finally {
            deleteFoldersRecursively(SETUP_FOLDER);
            deleteFoldersRecursively(CLASS_FOLDER);
        }
    }

    private static boolean ensureReadiness() {
        if (StringUtils.isBlank(System.getenv(ENV_NEXIAL_HOME))) {
            System.err.println(MSG_MISSING_ENV);
            return false;
        }
        if (StringUtils.isBlank(System.getenv(ENV_NEXIAL_LIB))) {
            System.err.println(MSG_MISSING_ENV);
            return false;
        }
        if (!FileUtil.isDirectoryReadWritable(TEMP)) {
            System.err.println(MSG_CANT_WRITE_TEMP);
            return false;
        }

        return true;
    }

    @NotNull
    private static Options initCmdOptions() {
        Options cmdOptions = new Options();
        cmdOptions.addOption(CliUtils.newArgOption(OPT_DATA_FILE, "file", "The file containing key/value pairs."));
        cmdOptions.addOption(CliUtils.newArgOption(OPT_SETUP_KEY, "key", "The key to encrypt data."));
        return cmdOptions;
    }

    private static String encryptData(String properties, String key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        cipher.init(ENCRYPT_MODE, new SecretKeySpec(key.getBytes(), ENCRYPTION_ALGORITHM));
        return new CipherHelper().encrypt(properties, cipher);
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
        String setupJarPath = CLASS_FOLDER + SETUP_JAR;
        File jarFile = new File(setupJarPath);

        if (jarFile.exists()) { jarFile.delete(); }

        File jarDir = new File(CLASS_FOLDER);
        jarDir.mkdirs();

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(MANIFEST_VERSION, "1.0");

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
        String name =
            StringUtils.substringAfter(
                StringUtils.appendIfMissing(source.getPath().replace("\\", "/"), "/").trim(),
                StringUtils.replace(SETUP_FOLDER, "\\", "/"));

        BufferedInputStream in = null;
        try {
            JarEntry entry = new JarEntry(name);
            entry.setTime(source.lastModified());
            target.putNextEntry(entry);

            if (source.isDirectory()) {
                target.closeEntry();
                File[] files = source.listFiles();
                if (files != null && files.length > 0) {
                    for (File nestedFile : files) { add(nestedFile, target); }
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
        System.err.println("Error ocurred during processing: " + e.getMessage());
        System.exit(RC_FAILURE_FOUND);
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
            System.exit(RC_FILE_NOT_FOUND);
        }
    }
}
