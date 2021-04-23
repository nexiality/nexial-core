package org.nexial.core.tools.docs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.ResourceUtils;

import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static org.nexial.core.NexialConst.Doc.*;
import static org.nexial.core.tools.docs.MiniDocConst.*;
import static org.nexial.core.tools.docs.MinifyGenerator.addMappings;
import static org.nexial.core.tools.docs.MinifyGenerator.operationCount;

public class SysVarMinifyGenerator {

    private final String sysvarLocation;

    public SysVarMinifyGenerator(String sysvarLocation) {
        this.sysvarLocation = StringUtils.appendIfMissing(sysvarLocation, "/");
    }

    /**
     * Process the html document containing the system variables
     */
    public void processDocument() {
        try {
            Document document = getDocument();
            if (document != null) { processTables(document); }
        } catch (Exception exception) {
            System.err.println("An error occurred while parsing " + exception.getMessage());
            System.exit(-1);
        }
    }

    /**
     * Process the tables in the current document and create the minified files for each system variables
     *
     * @param document the document to parse
     */
    private void processTables(Document document) throws IOException {
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

    /**
     * Write the contents of the html into a new file
     *
     * @param configurationId the configuration id the current system variable
     * @param headerRow       the header row of the table currently being read
     * @param html            the contents to be written into the file
     */
    private void writeFile(String configurationId, String headerRow, String html) throws IOException {
        File configurationFile = new File(getFileName(configurationId));
        if (html.contains("src=\"image/")) { html = replaceImageLinks(html); }
        if (html.contains(HREF_PREFIX)) {
            html = replaceLinks(html, configurationFile.getPath());
        }
        String htmlTemplatePath = getClass().getPackage().getName().replace(".", "/") + HTML_TEMPLATE;
        String htmlTemplate = ResourceUtils.loadResource(htmlTemplatePath);
        if (htmlTemplate == null) {
            throw new RuntimeException("HTML template could not be resolved");
        }
        String content = htmlTemplate.replace(CONFIGURATION_ROW, html)
                                     .replace(HEADER_ROW, headerRow)
                                     .replace(SYSVAR, configurationId);
        FileUtils.write(configurationFile, content, DEF_FILE_ENCODING);
        System.out.println("Created " + configurationFile.getAbsolutePath());
        operationCount++;

    }

    /**
     * Replace the local documentation link with global links
     *
     * @param html              the contents of the current html under processing
     * @param configurationFile the file in which the links are occurring
     * @return the html with updated links
     */
    private String replaceLinks(String html, String configurationFile) {
        if (html.contains(HREF_PREFIX + "../")) {
            html = StringUtils.replace(html, HREF_PREFIX + "..", HREF_PREFIX + DOCUMENTATION_URL);
        }
        while (html.contains(HREF_LINK_PREFIX)) {
            String pageName = StringUtils.substringBetween(html, HREF_LINK_PREFIX, "\"");
            String fullDocUrl = SYSVAR_DOCS_URL + "#" + pageName;
            String miniDocUrl = SYSVAR_DOCS_URL + StringUtils.replace(pageName, ".*", "") + MINI_HTML;
            String miniDocFile = getFileName(pageName);
            URLMapping urlMapping = new URLMapping(miniDocUrl, miniDocFile, fullDocUrl);
            addMappings(configurationFile, urlMapping);
            html = StringUtils.replace(html, HREF_LINK_PREFIX + pageName, HREF_PREFIX + miniDocUrl);
        }
        return html;
    }

    /**
     * Replace the local links in the html with global links
     *
     * @param html the html containing the image links
     * @return the updated html with global image links
     */
    @NotNull
    private String replaceImageLinks(String html) {
        List<String> imageLinks = RegexUtils.eagerCollectGroups(html, IMAGE_REGEX, true, true);
        for (String imageLink : imageLinks) {
            String imageName = StringUtils.substringBetween(imageLink, "image/", "\"");
            String uiImageName = UI_IMAGE_PREFIX + imageName;
            if (new File(sysvarLocation + IMAGE + "/" + uiImageName).exists()) {
                html = html.replace(imageName, uiImageName);
            }
        }
        html = html.replace("src=\"image/", "src=\"" + SYSVAR_DOCS_URL + "image/");
        return html;
    }


    /**
     * Return the file name of the minified doc for the system variable
     *
     * @param text the text to be written into the new file
     * @return the name of the minified doc
     */
    private String getFileName(String text) { return sysvarLocation + StringUtils.replace(text, ".*", "") + MINI_HTML; }

    /**
     * Get the html specified the location and return the {@link Document} object
     *
     * @return the html {@link Document} object
     */
    private Document getDocument() throws IOException {
        String location = sysvarLocation + CONTENT_HTML;
        File file = new File(location);
        if (!file.exists() || !file.canRead()) {
            throw new FileNotFoundException("File does not exist or cannot be read: " + location);
        }
        return Jsoup.parse(file, DEF_FILE_ENCODING);
    }
}
