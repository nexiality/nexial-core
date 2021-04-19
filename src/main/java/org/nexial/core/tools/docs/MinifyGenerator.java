package org.nexial.core.tools.docs;

import java.io.File;
import java.io.IOException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static org.nexial.core.NexialConst.ExitStatus.RC_BAD_CLI_ARGS;
import static org.nexial.core.NexialConst.NL;
import static org.nexial.core.tools.CliUtils.newArgOption;

public class MinifyGenerator {

    public static final String MD_EXTENSION = ".md";
    public static final String H4 = "#### ";
    public static final String H5 = "#####";
    public static final String UI_IMAGE_PREFIX = "UI.";
    public static final String SEE_ALSO = "### See Also";
    public static final String SCRIPT = "<script>";
    public static final String RELATIVE_LINK_REGEX = "\\*?\\*?((!?\\[[^\\]]*?\\])\\((?:(?!http).)*?\\))\\*?\\*?";
    public static int operationCount = 0;

    private static final Options cmdOptions = new Options();
    private String target;


    public static void main(String[] args) {
        initOptions();

        MinifyGenerator generator = newInstance(args);
        if (generator == null) { System.exit(RC_BAD_CLI_ARGS); }

        new FunctionMinifyGenerator(generator.target + "/functions").processFiles();
        new ExpressionMinifyGenerator(generator.target + "/expressions").processFiles();
        new SysVarMinifyGenerator(generator.target + "/systemvars/").processDocument();

    }

    public static void writeToFile(StringBuilder sb, String fileName) {
        File newFile = new File(fileName);
        try {
            FileUtils.write(newFile, sb.toString().trim(), DEF_FILE_ENCODING);
            System.out.println("Created " + newFile.getName());
        } catch (IOException e) {
            System.out.println("Error writing file " + newFile.getName());
        }
        sb.setLength(0);
        operationCount++;
    }

    public static String getFrontMatter(String operationName, String pageName) {
        return "---\n" +
               "layout: minified" +
               "\n" +
               "title: " +
               operationName +
               "\n" +
               "parent: " +
               pageName +
               "\n---\n\n";
    }

    private static void initOptions() {
        cmdOptions.addOption(newArgOption("t",
                                          "target",
                                          "[REQUIRED] Location of the Nexial Documentation on the local file system.",
                                          true));
    }

    private static MinifyGenerator newInstance(String[] args) {
        try {
            MinifyGenerator generator = new MinifyGenerator();
            generator.parseCLIOptions(new DefaultParser().parse(cmdOptions, args));
            return generator;
        } catch (Exception e) {
            System.err.println(NL + "ERROR: " + e.getMessage() + NL);
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(MinifyGenerator.class.getName(), cmdOptions, true);
            return null;
        }
    }

    private void parseCLIOptions(CommandLine cmd) {
        if (!cmd.hasOption("t")) { throw new RuntimeException("[target] is a required argument and is missing"); }

        target = cmd.getOptionValue("t");
        if (isEmpty(target) || !new File(target).exists()) {
            throw new RuntimeException("specified target - " + target + " is not accessible");
        }
    }
}
