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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
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
            System.err.println("An error occurred while parsing: " + exception.getMessage());
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
        for (Element table : tables) {

            Elements headers = table.child(0).child(0).children();
            int headerSize = headers.size();
            headers.remove(0);

            System.out.println("\nProcessing table");
            Elements rows = table.children().first().children();
            Elements prevDataRow = new Elements();

            for (Element row : rows) {
                if (!row.child(0).tag().getName().equals("th")) {

                    String configurationId = row.child(0).child(0).id();

                    if (!configurationId.equals("")) {
                        Elements rowChildren = document.getElementById(configurationId).parent().parent().children();
                        fixColumns(rowChildren, prevDataRow, headerSize);
                        addTargetToLinks(rowChildren);
                        rowChildren.remove(0);
                        writeFile(configurationId,
                                  TABLE_ROW_START + headers.outerHtml() + TABLE_ROW_END,
                                  TABLE_ROW_START + rowChildren.outerHtml() + TABLE_ROW_END);
                    }
                }
            }
            System.out.println("\nOperation count for current table is " + operationCount);
            operationCount = 0;
        }
    }

    /**
     * Some table rows have missing columns. This method fixes this by adding the columns from the last row to have
     * full number of columns.
     *
     * @param rowChildren the contents of the current row
     * @param prevDataRow the contents of the last row to have all columns
     * @param headerSize the nymber of headers for the current table
     */
    private void fixColumns(Elements rowChildren, Elements prevDataRow, int headerSize) {
        int columns = CollectionUtils.size(rowChildren);
        if (columns == headerSize) {
            prevDataRow.clear();
            prevDataRow.addAll(rowChildren);
        } else if (columns < headerSize && CollectionUtils.size(prevDataRow) == headerSize) {
            for (int i = columns; i < headerSize; i++) {
                rowChildren.add(prevDataRow.get(i));
            }
        }
    }

    /**
     * Adds a target attribute to the links in the current row if they do not have a target attribute defined already
     * @param rowChildren the contents of the current row
     */
    private void addTargetToLinks(Elements rowChildren) {
        List<Element> hrefs = new ArrayList<>();
        for (Element child : rowChildren){
            if(child.hasAttr("href"))
                hrefs.add(child);
        }
        if (CollectionUtils.isNotEmpty(hrefs)) {
            hrefs.forEach(element -> {
                if (!element.hasAttr(TARGET_ATTR)) { element.attr(TARGET_ATTR, "_nexial_link"); }
            });
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
        html = StringUtils.replace(html, " href=", " target=\"_nexial_link\" href=");
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
