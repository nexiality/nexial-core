package org.nexial.core.tools.docs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.apache.commons.lang3.StringUtils.*;
import static org.nexial.core.NexialConst.Doc.DOCUMENTATION_URL;
import static org.nexial.core.NexialConst.Doc.EXPRESSIONS_DOCS_URL;
import static org.nexial.core.tools.docs.MinifyGenerator.*;

public class ExpressionMinifyGenerator {

    // todo: constants over hardcoding
    private static final String EXPRESSION_SUFFIX = "expression.md";
    private static final String OPERATIONS_STARTED = "### Operations";

    private final String expressionsLocation;

    public ExpressionMinifyGenerator(String expressionsLocation) {
        this.expressionsLocation = expressionsLocation;
    }

    public void processFiles() {
        List<File> mdFiles = getMdFiles();
        if (mdFiles == null) { return; }

        if (mdFiles.isEmpty()) {
            System.err.println("There are no markdown files in the target location " + expressionsLocation);
            return;
        }

        for (File file : mdFiles) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                // todo: essentially the same code as FunctionMinifyGenerator; NEED TO REFACTOR!
                String line = reader.readLine();

                while (line != null && !line.trim().equals(OPERATIONS_STARTED)) { line = reader.readLine(); }
                while (line != null && !line.trim().startsWith(H4)) { line = reader.readLine(); }

                System.out.println("\nCreating markdown file for " + file.getName() + " operations\n");
                String pageName = substringBefore(file.getName(), MD_EXTENSION);

                StringBuilder sb = new StringBuilder();
                String fileName;
                String operationFileName;
                boolean endOfFile = false;

                while (line != null) {
                    if (line.startsWith(H4)) {
                        operationFileName = getOperationFileName(file, line);
                        fileName          = expressionsLocation + "/" + operationFileName + MD_EXTENSION;
                        sb.append(getFrontMatter(substringBetween(line, "`"), pageName));
                        line = reader.readLine();
                        while (!line.startsWith(H5) && !line.startsWith(H4)) {
                            if (line.startsWith(SCRIPT) || line.startsWith(SEE_ALSO)) {
                                endOfFile = true;
                                break;
                            } else if (line.startsWith("![")) {
                                line = replaceImageLinks(line);
                            } else {
                                line = fixLinks(line, pageName);
                            }
                            sb.append(line).append("\n");
                            line = reader.readLine();
                        }
                        writeToFile(sb, fileName);
                    }
                    line = endOfFile ? null : line.startsWith(H4) ? line : reader.readLine();
                }
                System.out.println("\nOperation count for " + file.getName() + " is " + operationCount);
                operationCount = 0;
            } catch (IOException exception) {
                System.out.println("Error occurred while processing file " + file.getName());
                // todo: what is the actual error? any reason to hide that?
            }
        }
    }

    @NotNull
    private String replaceImageLinks(String line) {
        line = line.replace("](", "](" + EXPRESSIONS_DOCS_URL);

        // todo: essentially the same code as SysVarMinifyGenerator; THINK REFACTOR!
        String imageName = substringBetween(line, "image/", ")");
        String amplifyImageName = UI_IMAGE_PREFIX + imageName;
        String imageLocation = expressionsLocation + "/image";

        // todo: listFiles() may return null list; NPE alert
        if (new File(imageLocation).listFiles(pathname -> pathname.getName().equals(amplifyImageName)).length > 0) {
            line = line.replace(imageName, amplifyImageName);
        }
        return line;
    }

    @NotNull
    private String fixLinks(String line, String pageName) {
        Pattern pattern = Pattern.compile(RELATIVE_LINK_REGEX);
        Matcher matcher = pattern.matcher(line);
        List<String> linkMatches = new ArrayList<>();
        while (matcher.find()) {
            String match = matcher.group();
            linkMatches.add(match);
        }
        if (!linkMatches.isEmpty()) {
            for (String link : linkMatches) {
                String element = link;
                if (link.contains("](../")) {
                    link = link.replace("](../", "](" + DOCUMENTATION_URL + "/");
                } else if (link.contains("](#")) {
                    link = link.replace(substringAfter(link, "#"), "");
                    link = link.replace("](#",
                                        "](" + EXPRESSIONS_DOCS_URL +
                                        substringBefore(pageName, "expression") + "_" + substringBetween(link, "`"));
                } else {
                    link = replaceImageLinks(link);
                }
                line = line.replace(element, link);
            }
        }
        return line;
    }

    @Nullable
    private List<File> getMdFiles() {
        // todo: essentially the same code as FunctionMinifyGenerator; THINK REFACTOR!
        File directory = new File(expressionsLocation);
        if (isEmpty(expressionsLocation) || !directory.exists()) {
            System.err.println("Invalid target path " + expressionsLocation);
        }
        File[] files = directory.listFiles();
        if (files == null || files.length == 0) {
            System.err.println("There are no files in the target location " + expressionsLocation);
            return null;
        }
        return Arrays.stream(files).filter(file -> file.getName().endsWith(EXPRESSION_SUFFIX))
                     .collect(Collectors.toList());
    }

    private static String getOperationFileName(File file, String line) {
        String prefix = substringBefore(file.getName(), EXPRESSION_SUFFIX);
        if (substringBetween(line, "`").contains("(") && line.contains(")")) {
            return prefix + "_" + substringBetween(line, "`", "(").trim();
        } else {
            return prefix + "_" + substringBetween(line, "`").trim();
        }
    }
}