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

package org.nexial.core.tools.docs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.ResourceUtils;

import static org.apache.commons.lang3.StringUtils.*;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Doc.*;
import static org.nexial.core.NexialConst.FlowControls.ARG_PREFIX;
import static org.nexial.core.tools.docs.MiniDocConst.*;

/**
 * Parses the functions, expressions and system variable files from the documentation
 * and create minified files.
 */
public class MinifyGenerator {

    public static int operationCount = 0;

    public static Map<String, Set<URLMapping>> fileUrlMappings = new HashMap<>();
    public static String target;

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("The location of Nexial Documentation on local machine is required");
        }

        target = args[0];
        File docLocation = new File(target);
        if (!docLocation.exists()) { throw new IllegalArgumentException("The path specified does not exist"); }
        MinifyGenerator generator = new MinifyGenerator();
        generator.deletePreExistingFiles();
        generator.processFiles(target + "/" + FUNCTIONS);
        generator.processFiles(target + "/" + EXPRESSIONS);
        new SysVarMinifyGenerator(target + "/" + SYSTEMVARS).processDocument();
        generator.replaceOldLinks();
    }

    /**
     * Add the url mappings corresponding to each fileName
     *
     * @param fileName the name of the file in which links are occurring
     * @param mapping  {@link URLMapping} object corresponding to the fileName passed in
     */
    public static void addMappings(String fileName, URLMapping mapping) {
        Set<URLMapping> urlMappings = new HashSet<>();
        if (fileUrlMappings.containsKey(fileName)) { urlMappings = fileUrlMappings.get(fileName); }
        urlMappings.add(mapping);
        fileUrlMappings.put(fileName, urlMappings);
    }

    /**
     * Delete the pre existing minified files prior to generating new files
     */
    private void deletePreExistingFiles() {
        List<File> existingMinifiedFiles = new ArrayList<>();
        existingMinifiedFiles.addAll(getFiles(target + "/" + FUNCTIONS));
        existingMinifiedFiles.addAll(getFiles(target + "/" + EXPRESSIONS));
        existingMinifiedFiles.addAll(getFiles(target + "/" + SYSTEMVARS + "/"));
        System.out.println("Deleting pre-existing minified docs");
        existingMinifiedFiles.stream()
                             .filter(file -> StringUtils.endsWithAny(file.getName(), MINI + MD_EXTENSION, MINI_HTML))
                             .forEach(file -> {
                                 if (!FileUtils.deleteQuietly(file)) {
                                     System.out.println("Unable to delete file " + file.getName());
                                     System.exit(-1);
                                 }
                             });
        System.out.println("Deleted pre-existing minified docs");
    }

    /**
     * Parse the original documentation files based on the fileLocation passed in and create minified docs
     *
     * @param fileLocation the fileLocation to look for target files
     */
    private void processFiles(String fileLocation) {
        String fileType = fileLocation.substring(lastIndexOf(fileLocation, "/") + 1).trim();
        List<File> mdFiles = getMdFiles(fileType, fileLocation);

        if (mdFiles.isEmpty()) {
            System.err.println("There are no markdown files in the target location " + fileLocation);
            System.exit(-1);
        }

        for (File file : mdFiles) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line = reader.readLine();

                while (line != null && !equalsAny(line.trim(), AVAILABLE_FUNCTIONS, OPERATIONS_STARTED)) {
                    line = reader.readLine();
                }
                while (line != null && !line.trim().startsWith(H4)) {
                    line = reader.readLine();
                }

                System.out.println("\nCreating markdown file for " + file.getName() + " operations\n");
                String pageName = substringBefore(file.getName(), MD_EXTENSION);

                StringBuilder sb = new StringBuilder();
                String fileName;
                String operationFileName;
                boolean endOfFile = false;

                while (line != null) {
                    if (line.startsWith(H4)) {
                        String operation = substringBetween(line, "`").trim();
                        operationFileName = fileType.equals(FUNCTIONS) ? getFunctionOperationName(operation) :
                                            getExpressionOperationFileName(file.getName(), operation);
                        fileName          = fileLocation + "/" + operationFileName + MINI + MD_EXTENSION;
                        sb.append(getFrontMatter(operation, pageName));
                        line = reader.readLine();
                        while (!line.startsWith(H5) && !line.startsWith(H4)) {
                            if (line.startsWith(SCRIPT) || line.startsWith(SEE_ALSO)) {
                                endOfFile = true;
                                break;
                            }

                            if (line.startsWith("![")) {
                                line = replaceImageLinks(fileType, line);
                            } else {
                                line = fixLinks(fileType, line, pageName, fileName);
                            }

                            sb.append(line).append(NL);
                            line = reader.readLine();
                        }

                        writeToFile(sb, fileName);
                        System.out.println("Created " + fileName);
                        operationCount++;
                        sb.setLength(0);
                    }
                    line = endOfFile ? null : line.startsWith(H4) ? line : reader.readLine();
                }
                System.out.println("\nOperation count for " + file.getName() + " is " + operationCount);
                operationCount = 0;
            } catch (Exception exception) {
                System.out.println("Error occurred while processing file " + file + ": " + exception.getMessage());
                System.exit(-1);
            }
        }
    }

    /**
     * Write the contents of the {@link StringBuilder} into a new {@link File}
     *
     * @param sb       the {@link StringBuilder} containing the contents of the new {@link File}
     * @param fileName the name of the new {@link File}
     */
    private static void writeToFile(StringBuilder sb, String fileName) throws IOException {
        FileUtils.write(new File(fileName), sb.toString().trim(), DEF_FILE_ENCODING);
    }

    /**
     * Retrieve the front matter for the markdown files from the template
     *
     * @param operationName the name of the current operation to be written into the front matter
     * @param pageName      the name of the parent page to be written into the front matter
     * @return the updated front matter
     */
    private String getFrontMatter(String operationName, String pageName) throws IOException {
        String frontMatter = ResourceUtils.loadResource(FRONT_MATTER_TEMLATE);
        frontMatter = replace(frontMatter, TITLE, operationName);
        frontMatter = replace(frontMatter, PARENT, pageName);
        return frontMatter;
    }

    /**
     * Check the passed in fileLocation for the passed in fileType and retrieve the required markdown files
     *
     * @param fileType     the type of the file passed in
     * @param fileLocation the location to check for the files
     * @return List of required files
     */
    private static List<File> getMdFiles(String fileType, String fileLocation) {
        List<File> fileList = getFiles(fileLocation);
        if (fileList.isEmpty()) { return fileList; }
        if (fileType.equals(FUNCTIONS)) {
            return fileList.stream().filter(file -> file.getName().startsWith(TOKEN_FUNCTION_START) &&
                                                    file.getName().endsWith(FUNCTION_SUFFIX) &&
                                                    !file.getName().contains(")" + OPERATION_SEP))
                           .collect(Collectors.toList());
        } else {
            return fileList.stream().filter(file -> file.getName().endsWith(EXPRESSION_FILE1_SUFFIX))
                           .collect(Collectors.toList());
        }
    }

    /**
     * Return the files fom the passed in fileLocation
     *
     * @param fileLocation the location to retrieve files from
     * @return return the files from the location
     */
    private static List<File> getFiles(String fileLocation) {
        File directory = new File(fileLocation);
        if (isEmpty(fileLocation) || !directory.exists()) {
            System.err.println("Invalid target path " + fileLocation);
            return new ArrayList<>();
        }
        File[] files = directory.listFiles();
        return files == null || files.length == 0 ? new ArrayList<>() : Arrays.asList(files);
    }

    /**
     * Return the title of the current function operation
     *
     * @param line the line currently under processing
     * @return the title of the current operation
     */
    private static String getFunctionOperationName(String line) {
        List<String> functionElements =
                Arrays.asList(substringBetween(line, TOKEN_FUNCTION_START, TOKEN_FUNCTION_END).split("\\|"));
        if (functionElements.size() < MIN_FUNCTION_SIZE) {
            System.err.println("Function signature is wrong");
            System.exit(-1);
        }

        String function = TOKEN_FUNCTION_START + functionElements.get(0) + TOKEN_FUNCTION_END;
        String operation = functionElements.get(1);
        StringBuilder param = new StringBuilder(functionElements.get(2));

        for (int i = MIN_FUNCTION_SIZE; i < functionElements.size(); i++) {
            param.append(",").append(functionElements.get(i));
        }
        return function + OPERATION_SEP + operation + ARG_PREFIX + param.toString() + TOKEN_FUNCTION_END;
    }

    /**
     * Return the title of the current expression operation
     *
     * @param file      the parent file of the current operation
     * @param operation the operation name
     * @return the title of the current expression operation
     */
    private static String getExpressionOperationFileName(String file, String operation) {
        String expressionName = substringBefore(file, EXPRESSION_FILE1_SUFFIX);
        if (contains(operation, ARG_PREFIX) && contains(operation, TOKEN_FUNCTION_END)) {
            return expressionName + OPERATION_SEP + substringBefore(operation, ARG_PREFIX);
        } else {
            return expressionName + OPERATION_SEP + operation;
        }
    }

    /**
     * Replace the local image links with global image links
     *
     * @param fileType the type of the file currently being processed
     * @param line     the line currently under processing
     * @return the line with updated image links
     */
    private static String replaceImageLinks(String fileType, String line) {
        line = line.replace("](", "](" + DOCUMENTATION_URL + "/" + fileType + "/");
        String imageName = substringBetween(line, IMAGE + "/", SCREENSHOT_EXT + TOKEN_FUNCTION_END);
        String uiImageName = UI_IMAGE_PREFIX + imageName;
        String imageLocation = target + "/" + fileType + "/" + IMAGE;
        File imageDirectory = new File(imageLocation);
        if (!imageDirectory.exists()) {
            throw new RuntimeException("Invalid path for " + IMAGE + " location" + imageLocation);
        }
        File[] imageFiles = imageDirectory.listFiles();
        if (imageFiles == null || imageFiles.length == 0) {
            throw new RuntimeException("No " + IMAGE + " files found in location : " + imageLocation);
        }
        if (Arrays.stream(imageFiles).anyMatch(file -> file.getName().equals(uiImageName + SCREENSHOT_EXT))) {
            line = line.replace(imageName, uiImageName);
        }
        return line;
    }

    /**
     * Substitute the local links in the line with global working links
     *
     * @param fileType the type of the file currently under processing
     * @param line     the line currently under processing
     * @param pageName the name of the page where the link is occurring
     * @param fileName the parent file which is currently being read
     * @return the updated line with replaced links
     */
    private static String fixLinks(String fileType, String line, String pageName, String fileName) {
        String originPageName = pageName;
        Pattern pattern = Pattern.compile(RELATIVE_LINK_REGEX);
        Matcher matcher = pattern.matcher(line);

        List<String> linkMatches = new ArrayList<>();
        while (matcher.find()) { linkMatches.add(matcher.group()); }

        if (CollectionUtils.isNotEmpty(linkMatches)) {
            for (String link : linkMatches) {
                String element = link;
                if (link.contains("](../")) {
                    link = link.replace("](../", "](" + DOCUMENTATION_URL + "/");
                } else if (link.contains("](#")) {
                    link = link.replace(substringAfter(link, "#"), "");

                    link = link.replace("](#", "](" + DOCUMENTATION_URL + "/" + fileType + "/" + pageName + MINI + ")");
                    setUrlMapping(pageName, fileType, substringBetween(element, "#", ")"), link, fileName);
                    pageName = originPageName;

                } else {
                    link = replaceImageLinks(fileType, link);
                }
                line = line.replace(element, link);
            }
        }
        return line;
    }

    /**
     * Add {@link URLMapping} objects for each local link that points to location in the same document
     *
     * @param pageName   the minified document name for the current operation
     * @param fileType   the type of the file currently under processing
     * @param anchor     the location where the link points to
     * @param miniDocUrl the url of the minified document for the current operation
     * @param fileName   the name of minified document in which the link is occurring
     */
    private static void setUrlMapping(String pageName, String fileType, String anchor, String miniDocUrl,
                                      String fileName) {
        String miniDocFile = target + "/" + fileType + "/" + pageName + MINI + MD_EXTENSION;
        pageName = substringBefore(pageName, OPERATION_SEP);
        if (fileType.equals(EXPRESSIONS)) { pageName = pageName + EXPRESSION_SUFFIX; }
        String fullDocUrl = replace(miniDocUrl, substringAfter(miniDocUrl, fileType), "/" + pageName + "#" + anchor) +
                            TOKEN_FUNCTION_END;

        addMappings(fileName, new URLMapping(miniDocUrl, miniDocFile, fullDocUrl));
    }

    /**
     * Correct the local links which point to a different location within the same document. If the minified
     * file for the link exists, leave it be. If the minified file does not exist, replace the current link with
     * a full documentation url pointing to the original documentation
     */
    private void replaceOldLinks() throws IOException {
        Set<String> fileList = fileUrlMappings.keySet();
        for (String fileToFix : fileList) {
            if (!new File(fileToFix).exists()) { continue; }

            Set<URLMapping> mappingSet = fileUrlMappings.get(fileToFix);
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new FileReader(fileToFix));
            String line = br.readLine();
            while (line != null) {
                for (URLMapping mapping : mappingSet) {
                    if (line.contains(mapping.getMiniDocUrl())) {
                        if (!new File(mapping.getMiniDocFile()).exists()) {
                            line = line.replace(mapping.getMiniDocUrl(), mapping.getFullDocUrl());
                        }
                    }
                }
                sb.append(line).append(NL);
                line = br.readLine();
            }
            writeToFile(sb, fileToFix);
        }
    }
}
