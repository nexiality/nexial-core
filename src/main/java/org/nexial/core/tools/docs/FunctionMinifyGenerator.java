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
import static org.nexial.core.NexialConst.Doc.FUNCTIONS_DOCS_URL;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.tools.docs.MinifyGenerator.*;

public class FunctionMinifyGenerator {

    private static final String FUNCTION_PREFIX = "$(";
    private static final String FUNCTION_SUFFIX = ").md";
    private static final String AVAILABLE_FUNCTIONS = "### Available Functions";

    private final String functionsLocation;

    public FunctionMinifyGenerator(String functionsLocation) {
        this.functionsLocation = functionsLocation;
    }

    public void processFiles() {
        List<File> mdFiles = getMdFiles();
        if (mdFiles == null) { return; }

        if (mdFiles.isEmpty()) {
            System.err.println("There are no markdown files in the target location " + functionsLocation);
            return;
        }

        for (File file : mdFiles) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line = reader.readLine();

                while (line != null && !line.trim().equals(AVAILABLE_FUNCTIONS)) { line = reader.readLine(); }
                while (line != null && !line.trim().startsWith(H4)) { line = reader.readLine(); }

                System.out.println("\nCreating markdown file for " + file.getName() + " operations\n");
                String pageName = substringBefore(file.getName(), MD_EXTENSION);

                StringBuilder sb = new StringBuilder();
                String fileName;
                String operationName;
                boolean endOfFile = false;

                while (line != null) {
                    if (line.startsWith(H4)) {
                        operationName = getOperationName(line);
                        fileName      = functionsLocation + "/" + operationName + MD_EXTENSION;
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
                            sb.append(line).append(NL);
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
        String imageName = substringBetween(line, "image/", ".png)");
        String uiImageName = UI_IMAGE_PREFIX + imageName;
        String imageLocation = functionsLocation + "/image";
        line = line.replace("](", "](" + FUNCTIONS_DOCS_URL);
        // todo: listFiles() may return null list; NPE alert
        if (new File(imageLocation).listFiles(pathname -> pathname.getName().equals(uiImageName + ".png")).length > 0) {
            line = line.replace(imageName, uiImageName);
        }
        return line;
    }

    private String fixLinks(String line, String pageName) {
        // todo: essentially the same code as  ExpressionMinifyGenerator; THINK REFACTOR!
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
                    link = link.replace("](../", "](" + DOCUMENTATION_URL + "/)");
                } else if (link.contains("](#")) {
                    link = link.replace(substringAfter(link, "#"), "");
                    link = link.replace("](#",
                                        "](" + FUNCTIONS_DOCS_URL +
                                        substringBefore(pageName, "expression") + "_" + substringBetween(link, "`"));
                } else {
                    link = link.replace("](", "](" + FUNCTIONS_DOCS_URL);
                }
                line = line.replace(element, link);
            }
        }
        return line;
    }

    private String getOperationName(String line) {
        List<String> lineContents = Arrays.asList(substringBetween(line, "(", ")").split("\\|"));
        String function = TOKEN_FUNCTION_START + lineContents.get(0) + TOKEN_FUNCTION_END;
        String operation = lineContents.get(1);
        StringBuilder param = new StringBuilder(lineContents.get(2));
        if (lineContents.size() > 3) {
            for (int i = 3; i < lineContents.size(); i++) {
                param.append(",").append(lineContents.get(i));
            }
        }
        return function + "_" + operation + "(" + param.toString() + ")";
    }

    @Nullable
    private List<File> getMdFiles() {
        // todo: essentially the same code as ExpressionMinifyGenerator; THINK REFACTOR!
        File directory = new File(functionsLocation);
        if (isEmpty(functionsLocation) || !directory.exists()) {
            System.err.println("Invalid target path " + functionsLocation);
        }
        File[] files = directory.listFiles();
        if (files == null || files.length == 0) {
            System.err.println("There are no files in the target location " + functionsLocation);
            return null;
        }
        return Arrays.stream(files).filter(file -> file.getName().endsWith(FUNCTION_SUFFIX) &&
                                                   file.getName().startsWith(FUNCTION_PREFIX) &&
                                                   !file.getName().contains(")_"))
                     .collect(Collectors.toList());
    }
}
