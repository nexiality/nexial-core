package org.nexial.core.tools.docs;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static org.nexial.core.NexialConst.Doc.DOCUMENTATION_URL;
import static org.nexial.core.NexialConst.Doc.SYSVAR_DOCS_URL;
import static org.nexial.core.tools.docs.MinifyGenerator.UI_IMAGE_PREFIX;
import static org.nexial.core.tools.docs.MinifyGenerator.operationCount;

public class SysVarMinifyGenerator {
    private static final String HEADER_ROW = "{header-row}";
    private static final String CONFIGURATION_ROW = "{configuration-row}";
    private static final String SYSVAR = "{sysvar}";
    // todo: move to external files to simplify maintenance
    private static final String HTML_TEMPLATE = "<table>\n" +
                                                "    " + HEADER_ROW + "\n" +
                                                "    " + CONFIGURATION_ROW + "\n" +
                                                "</table>\n" +
                                                "<footer>\n" +
                                                "    <ul>\n" +
                                                "        <li class=\"doc_home\" title=\"Click here to view Nexial Documentation\"><a href=\"" + DOCUMENTATION_URL +
                                                "\" target=\"_blank\">Nexial</a></li>\n" +
                                                "        <li class=\"doc_parent\" title=\"Click here to view System Variable Documentation\"><a href=\"" + SYSVAR_DOCS_URL + "#" + SYSVAR +
                                                "\" target=\"_blank\">" + SYSVAR + "</a></li>\n" +
                                                "    </ul>\n" +
                                                "</footer>\n";

    private final String sysvarLocation;
    private final String targetFile;
    private final String imageLocation;

    public SysVarMinifyGenerator(String sysvarLocation) {
        this.sysvarLocation = sysvarLocation;
        // todo: constants over hardcoding
        targetFile    = sysvarLocation + "content.html";
        imageLocation = sysvarLocation + "image";
    }


    public void processDocument() {
        Document document = getDocument();
        if (document != null) { processTables(document); }
    }

    private void processTables(Document document) {
        Elements tables = document.select("table.sysvar");
        Element headerRow = tables.first().children().first().child(0);
        for (Element table : tables) {
            System.out.println("\nProcessing table : ");
            Elements rows = table.children().first().children();
            for (Element row : rows) {
                if (!row.child(0).tag().getName().equals("th")) {
                    String configurationId = row.child(0).child(0).id();
                    if (!configurationId.equals("")) {
                        writeFile(configurationId,
                                  headerRow.outerHtml(),
                                  document.getElementById(configurationId).parent().parent().outerHtml());
                    }
                }
            }
            System.out.println("\nOperation count for current table is " + operationCount);
            operationCount = 0;
        }
    }

    private void writeFile(String configurationId, String headerRow, String html) {
        File configurationFile = new File(getFileName(configurationId));
        if (html.contains("src=\"image/")) { html = replaceImageLinks(html); }
        String content = HTML_TEMPLATE.replace(CONFIGURATION_ROW, html)
                                      .replace(HEADER_ROW, headerRow)
                                      .replace(SYSVAR, configurationId);

        try {
            FileUtils.write(configurationFile, content, DEF_FILE_ENCODING);
            System.out.println("Successfully wrote file " + configurationFile.getName());
            operationCount++;
        } catch (IOException e) {
            System.out.println("Error occurred during writing file : " + configurationFile.getName());
            // todo: what is the actual error? any reason to hide that?
        }
    }

    @NotNull
    private String replaceImageLinks(String html) {
        html = html.replace("src=\"image/", "src=\"" + SYSVAR_DOCS_URL + "image/");

        String imageName = StringUtils.substringBetween(html, "image/", "\"/>");
        String uiImageName = UI_IMAGE_PREFIX + imageName;

        // todo: listFiles() may return null list; NPE alert
        if (new File(imageLocation).listFiles(pathname -> pathname.getName().equals(uiImageName)).length > 0) {
            html = html.replace(imageName, uiImageName);
        }

        return html;
    }

    private String getFileName(String text) {
        return sysvarLocation + StringUtils.replace(text, ".*", "") + ".html";
    }

    private Document getDocument() {
        try {
            File file = new File(targetFile);
            if (!file.exists()) {
                System.err.println("File does not exist");
                return null;
            }
            return Jsoup.parse(file, DEF_FILE_ENCODING);
        } catch (IOException exception) {
            System.err.println("Error occurred during parsing of file " + targetFile);
            // todo: what is the actual error? any reason to hide that?
            return null;
        }
    }
}