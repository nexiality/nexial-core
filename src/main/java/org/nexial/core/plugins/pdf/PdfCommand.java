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

package org.nexial.core.plugins.pdf;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.AdobePDFSchema;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.xml.DomXmpParser;
import org.apache.xmpbox.xml.XmpParsingException;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.PdfOutputProfile;
import org.nexial.core.model.ServiceProfile;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.plugins.io.IoCommand;
import org.nexial.core.plugins.pdf.PdfTableExtractor.LineRange;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.OutputFileUtils;
import org.thymeleaf.util.ArrayUtils;
import org.thymeleaf.util.ListUtils;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.BadPdfFormatException;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSmartCopy;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.tool.xml.XMLWorkerHelper;

import static java.io.File.separator;
import static java.io.File.separatorChar;
import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.MULTILINE;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Pdf.*;
import static org.nexial.core.utils.CheckUtils.*;

public class PdfCommand extends BaseCommand {
    protected static final DateFormat PDF_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    protected IoCommand io;

    @Override
    public void init(ExecutionContext context) {
        super.init(context);
        io = (IoCommand) context.findPlugin("io");
    }

    @Override
    public String getTarget() { return "pdf"; }

    public StepResult assertPatternPresent(String pdf, String regex) {
        requires(StringUtils.isNotBlank(regex), "invalid text", regex);

        regex = PdfTextExtractor.normalizeContent(regex, context);

        try {
            Pattern pattern = Pattern.compile(regex, MULTILINE | DOTALL);
            Matcher matcher = pattern.matcher(extractText(pdf));
            boolean found = matcher.find();

            String reason = "EXPECTED pattern '" + regex + "' " + (found ? "" : "NOT ") + "found in '" + pdf + "'";
            return new StepResult(found, reason, null);
        } catch (IOException e) {
            return StepResult.fail("unable to extract content from " + pdf, e);
        }
    }

    public StepResult assertContentEqual(String actualPdf, String expectedPdf) {
        requiresNotBlank(actualPdf, "blank baseline");
        requiresNotBlank(expectedPdf, "blank current");

        String outPath = syspath.out("fullpath");

        String actualExt = StringUtils.lowerCase(FilenameUtils.getExtension(actualPdf));
        String newBaselinePath;
        if (StringUtils.equals(actualExt, "pdf")) {
            newBaselinePath = outPath + separator + "actualPDF.txt";
            saveAsText(actualPdf, newBaselinePath);
        } else if (StringUtils.equals(actualExt, "txt")) {
            newBaselinePath = actualPdf;
        } else {
            return StepResult.fail("Unrecognized file format (" + actualPdf + ")");
        }

        String currentExt = StringUtils.lowerCase(FilenameUtils.getExtension(expectedPdf));
        String newCurrentPath;
        if (StringUtils.equals(currentExt, "pdf")) {
            newCurrentPath = outPath + separator + "currentPDF.txt";
            saveAsText(expectedPdf, newCurrentPath);
        } else if (StringUtils.equals(currentExt, "txt")) {
            newCurrentPath = expectedPdf;
        } else {
            return StepResult.fail("Unrecognized file format (" + expectedPdf + ")");
        }

        String resultVar = RandomStringUtils.randomAlphabetic(5);
        return io.saveDiff(resultVar, newBaselinePath, newCurrentPath);
    }

    public StepResult assertPatternNotPresent(String pdf, String regex) {
        requires(StringUtils.isNotBlank(regex), "invalid text", regex);
        try {
            String content = extractText(pdf);
            Pattern pattern = Pattern.compile(regex, MULTILINE | DOTALL);
            Matcher matcher = pattern.matcher(content);
            boolean notFound = !matcher.find();
            return new StepResult(notFound,
                                  "pattern '" + regex + "' " + (notFound ? " NOT " : "") + "found in '" + pdf + "'",
                                  null);
        } catch (IOException e) {
            return StepResult.fail("unable to extract content from " + pdf, e);
        }
    }

    public StepResult assertTextArray(String pdf, String textArray, String ordered) {
        requires(StringUtils.isNotBlank(textArray), "invalid text array", textArray);

        boolean order = CheckUtils.toBoolean(ordered);
        String[] array = StringUtils.splitByWholeSeparator(textArray, context.getTextDelim());

        try {
            String content = extractText(pdf);
            if (StringUtils.isBlank(content)) { return StepResult.fail("NO readable content in '" + pdf + "'"); }

            for (String text : array) {
                String searchFor = StringUtils.trim(text);
                if (!StringUtils.contains(content, searchFor)) {
                    return StepResult.fail("EXPECTED text '" + text + "' not found in '" + pdf + "'");
                }

                if (order) { content = StringUtils.substringAfter(content, searchFor); }
            }

            return StepResult.success("all EXPECTED text found in '" + pdf + "'" + (order ? " as ordered" : ""));
        } catch (IOException e) {
            return StepResult.fail("unable to extract content from " + pdf, e);
        }
    }

    public StepResult assertTextPresent(String pdf, String text) {
        requiresNotBlank(text, "invalid text", text);
        try {
            String content = extractText(pdf);
            boolean found = StringUtils.contains(content, text);
            return new StepResult(found,
                                  "EXPECTED text '" + text + "' " + (found ? "" : "NOT ") + "found in '" + pdf + "'",
                                  null);
        } catch (IOException e) {
            return StepResult.fail("unable to extract content from " + pdf, e);
        }
    }

    public StepResult assertTextNotPresent(String pdf, String text) {
        requiresNotBlank(text, "invalid text", text);
        try {
            String content = extractText(pdf);
            boolean notFound = !StringUtils.contains(content, text);
            return new StepResult(notFound,
                                  "text '" + text + "' " + (notFound ? "NOT " : "") + "found in '" + pdf + "'",
                                  null);
        } catch (IOException e) {
            return StepResult.fail("unable to extract content from " + pdf, e);
        }
    }

    public StepResult count(String pdf, String text, String var) {
        requiresNotBlank(text, "invalid text", text);
        requires(StringUtils.isNotBlank(var) && !StringUtils.startsWith(var, "${"), "invalid variable", var);

        try {
            String content = extractText(pdf);
            int count = StringUtils.countMatches(content, text);
            context.setData(var, count);
            return StepResult.success("occurrence of '" + text + "' in '" + pdf + "' saved to '" + var + "'");
        } catch (IOException e) {
            return StepResult.fail("unable to extract content from " + pdf, e);
        }
    }

    public StepResult saveFormValues(String pdf, String var, String pageAndLineStartEnd, String strategy) {
        requiresValidAndNotReadOnlyVariableName(var);

        requiresNotBlank(pageAndLineStartEnd,
                         "Invalid page number, starting line number and ending line number",
                         pageAndLineStartEnd);
        String[] pageStartEnd = StringUtils.splitByWholeSeparator(pageAndLineStartEnd, context.getTextDelim());
        if (ArrayUtils.length(pageStartEnd) != 3) {
            fail("Invalid page number, starting line number and ending line number: " + pageAndLineStartEnd);
        }

        String pageNumString = pageStartEnd[0];
        requiresPositiveNumber(pageNumString, "page number not a positive integer", pageNumString);
        int pageNum = NumberUtils.toInt(pageNumString);

        String startLine = pageStartEnd[1];
        requiresPositiveNumber(startLine, "starting line number not a positive integer", startLine);
        int startsFrom = NumberUtils.toInt(startLine);

        String endLine = pageStartEnd[2];
        requiresPositiveNumber(endLine, "ending line number not a positive integer", endLine);
        int endsOn = NumberUtils.toInt(endLine);

        requiresValidStrategy(strategy);

        KeyValueIdentStrategy identStrategy = CommonKeyValueIdentStrategies.toStrategy(strategy);
        requiresNotNull(identStrategy, "Unknown/unsupported form extraction strategy", strategy);

        Map formValues = context.getMapData(var);
        if (formValues == null) { formValues = new LinkedHashMap(); }

        // if strategy is keyInHeaderRowOnly and we have previously parsed data, then we should join
        // previously parsed data with new ones.
        boolean joinKeyInHeaderRowMapping = identStrategy.isKeyInHeaderRowOnly() && MapUtils.isNotEmpty(formValues);

        try {
            LineRange lineRange = LineRange.newRange(pageNum, startsFrom, endsOn);
            PdfTableExtractor extractor = new PdfTableExtractor().setSource(pdf);
            if (joinKeyInHeaderRowMapping) { extractor.setExistingFormValues(formValues); }

            Map<String, Object> extracted = extractor.extractRangeFromMap(lineRange, identStrategy);

            if (joinKeyInHeaderRowMapping) {
                formValues = extracted;
            } else {
                // internally save to variable of type map
                MapFormatter.resolveUmatchedList(extracted).addAll(MapFormatter.resolveUmatchedList(formValues));

                // `extracted` may contain name/value already exists in `formValues`
                String[] extractedKeys = extracted.keySet().toArray(new String[extracted.size()]);
                for (String key : extractedKeys) {
                    Object value = extracted.get(key);

                    if (StringUtils.equals(key, PDFFORM_UNMATCHED_TEXT)) {
                        formValues.put(key, value);
                        continue;
                    }

                    if (formValues.containsKey(key)) {
                        // since this key already exists, we need to rename `key` to `key_#`
                        if (RegexUtils.isExact(key, "^.+\\.\\d+$")) {
                            // this is not the first occurrence of the dup/repeated key
                            String keyPrefix = StringUtils.substringBeforeLast(key, ".");
                            int newIndex = NumberUtils.toInt(StringUtils.substringAfterLast(key, ".")) + 1;

                            // we shouldn't have more than a thousand dup. keys, right?
                            for (int j = newIndex; j < 1000; j++) {
                                String newKey = keyPrefix + j;
                                if (!formValues.containsKey(newKey)) {
                                    formValues.put(newKey, value);
                                    break;
                                }
                            }
                        } else {
                            // this is the first occurrence of the dup/repeated key
                            formValues.put(key + ".1", value);
                        }
                    } else {
                        formValues.put(key, value);
                    }
                }
            }

            context.setData(var, formValues);
            if (context.isVerbose()) { log("PDF Form Elements mapped to '" + var + "':" + NL + formValues); }
            return StepResult.success("PDF form extracted as specified");
        } catch (IOException e) {
            return StepResult.fail("Unable to extract PDF form: " + e.getMessage());
        }
    }

    public StepResult assertFormValue(String var, String name, String expected) {
        requiresNotNull(expected, "Invalid expected value", expected);

        Object valueObject = getFormValue(var, name);
        if (valueObject == null) {
            return StepResult.fail("Specified variable (" + var + ") not found or specified form element (" +
                                   name + ") not found");
        }

        boolean matched;
        if (valueObject instanceof List) {
            matched = ListUtils.contains((List) valueObject, expected);
        } else {
            matched = StringUtils.equals(Objects.toString(valueObject), expected);
        }
        return new StepResult(matched,
                              "Form element (" + name + ") " +
                              (matched ? "contains" : "DOES NOT contain") +
                              " expected value '" + expected + "'",
                              null);
    }

    public StepResult assertFormValues(String var, String name, String expectedValues, String exactOrder) {
        requiresNotNull(expectedValues, "Invalid expected value", expectedValues);

        Object valueObject = getFormValue(var, name);
        if (valueObject == null) {
            return StepResult.fail("Specified variable (" + var + ") not found or specified form element (" +
                                   name + ") not found");
        }

        String value;
        if (valueObject instanceof List) {
            value = TextUtils.toString((List) valueObject, context.getTextDelim());
        } else if (valueObject.getClass().isArray()) {
            // taking a chance... it's unlikely that this array is not an array of string
            value = TextUtils.toString((String[]) valueObject, context.getTextDelim(), "", "");
        } else {
            value = Objects.toString(valueObject);
        }

        return assertArrayEqual(value, expectedValues, exactOrder);
    }

    public StepResult assertFormElementPresent(String var, String name) {
        requiresNotBlank(name, "Invalid name", name);

        boolean found = getFormValue(var, name) != null;
        return new StepResult(found,
                              "form element (" + name + ") was " + (found ? "found" : "not found") + " as EXPECTED",
                              null);
    }

    public StepResult saveAsPages(String pdf, String destination) {
        try {
            List<File> pages = extractPages(pdf, destination);
            if (CollectionUtils.isNotEmpty(pages)) {
                return StepResult.success("content extracted into " + CollectionUtils.size(pages) + " page(s)");
            } else {
                return StepResult.fail("Unable to extract any pages from '" + pdf + "'");
            }
        } catch (IOException e) {
            return StepResult.fail("unable to extract content from " + pdf, e);
        }
    }

    public StepResult saveMetadata(String pdf, String var) {
        requires(StringUtils.isNotBlank(var) && !StringUtils.startsWith(var, "${"), "invalid variable", var);

        try {
            Map<String, String> metadata = extractMetadata(pdf);
            log("extracted metadata from '" + pdf + "': " + metadata);
            context.setData(var, metadata);
            return StepResult.success("metadata extracted from '" + pdf + "'");
        } catch (Exception e) {
            return StepResult.fail("error occurred while extracting metadata from '" + pdf + "': " + e.getMessage());
        }
    }

    public StepResult saveAsText(String pdf, String destination) {
        requiresNotBlank(destination, "invalid destination", destination);

        File dest = new File(destination);
        if (dest.exists()) {
            requires(dest.isFile() && dest.canRead(), "destination exists but is not readable", destination);
            log("destination '" + destination + "' exists and will be overwritten");
        }

        try {
            String content = extractText(pdf);
            FileUtils.write(dest, content, DEF_CHARSET);
            return StepResult.success("content extracted from '" + pdf + "' to '" + destination + "'");
        } catch (IOException e) {
            return StepResult.fail("unable to extract content from " + pdf, e);
        }
    }

    public StepResult saveToVar(String pdf, String var) {
        requires(StringUtils.isNotBlank(var) && !StringUtils.startsWith(var, "${"), "invalid variable", var);
        try {
            String content = extractText(pdf);
            updateDataVariable(var, content);
            return StepResult.success("content extracted from '" + pdf + "'");
        } catch (IOException e) {
            return StepResult.fail("unable to extract content from " + pdf, e);
        }
    }

    public StepResult saveAsPdf(String profile, String content, String file) {
        requiresNotBlank(profile, "Invalid profile", profile);
        requiresNotBlank(content, "Invalid content", profile);
        requiresNotBlank(file, "Invalid file", profile);

        content = OutputFileUtils.resolveContent(content, context, false, true);

        File target = new File(StringUtils.appendIfMissingIgnoreCase(file, ".pdf"));
        File targetPath = target.getParentFile();
        if (targetPath != null) { targetPath.mkdirs(); }

        // we don't really want to save this in context.. it would not allow for stepwise override/customization
        PdfOutputProfile outputProfile = ServiceProfile.resolve(context, profile, PdfOutputProfile.class);
        context.removeData(NAMESPACE + "profile." + profile);

        // support page size and custom margins
        // change page size to simulate orientation
        Float[] margins = outputProfile.getMargins();
        Document document = margins.length == 4 ?
                            new Document(outputProfile.getPageSize(), margins[0], margins[1], margins[2], margins[3]) :
                            new Document(outputProfile.getPageSize());

        PdfWriter writer;
        try {
            writer = PdfWriter.getInstance(document, new FileOutputStream(target));
        } catch (DocumentException | IOException e) {
            if (context.isVerbose()) { e.printStackTrace(); }
            String error = "Unable to save content as PDF ('" + target + "'): " + ExceptionUtils.getRootCauseMessage(e);
            ConsoleUtils.error(error);
            return StepResult.fail(error);
        }

        document.open();
        document.addAuthor(outputProfile.getAuthor());
        document.addCreator(outputProfile.getCreator());
        document.addCreationDate();

        String title = outputProfile.getTitle();
        if (!context.isNullOrEmptyOrBlankValue(title)) { document.addTitle(title); }

        String subject = outputProfile.getSubject();
        if (!context.isNullOrEmptyOrBlankValue(subject)) { document.addSubject(subject); }

        String keywords = outputProfile.getKeywords();
        if (!context.isNullOrEmptyOrBlankValue(keywords)) { document.addKeywords(keywords); }

        Map<String, String> customProps = outputProfile.getCustomProperties();
        if (MapUtils.isNotEmpty(customProps)) { customProps.forEach(document::addHeader); }

        StringBuilder buffer = new StringBuilder();
        if (outputProfile.getMonospaced()) {
            buffer.append("<html>")
                  .append("<head>").append(!context.isNullOrEmptyOrBlankValue(title) ? title : "").append("</head>")
                  .append("<body>").append("<pre>").append(content).append("</pre>")
                  .append("</body></html>");
        } else {
            buffer.append(content);
        }

        try {
            XMLWorkerHelper worker = XMLWorkerHelper.getInstance();
            worker.parseXHtml(writer, document, new StringReader(buffer.toString()));
            return StepResult.success("content saved to '%s'", target);
        } catch (IOException e) {
            if (context.isVerbose()) { e.printStackTrace(); }
            String error = "Unable to save content as PDF ('" + target + "'): " + ExceptionUtils.getRootCauseMessage(e);
            ConsoleUtils.error(error);
            return StepResult.fail(error);
        } finally {
            document.close();
        }
    }

    /**
     * combine all the PDF files found in {@literal path} that matches the specified {@literal filterFilter} filter
     * and save the combined PDF to {@literal saveTo}.
     */
    public StepResult combine(String path, String fileFilter, String saveTo) {
        requiresReadableDirectory(path, "Invalid/Unreadable path", path);
        requiresNotBlank(saveTo, "Invalid destination to save to", saveTo);

        File from = new File(path);
        File to = new File(saveTo);
        to.getParentFile().mkdirs();

        IoCommand io = new IoCommand();
        List<String> pdfFiles = io.listMatchingFiles(from, fileFilter, null)
                                  .stream()
                                  .filter(file -> StringUtils.endsWithIgnoreCase(file, ".pdf"))
                                  .collect(Collectors.toList());
        long matchedFileCount = pdfFiles.size();
        log("Found " + matchedFileCount + " matching PDF files to combine");

        if (matchedFileCount < 1) { return StepResult.success("No suitable PDF files found"); }

        StringBuilder errors = new StringBuilder();
        AtomicInteger totalPagesAdded = new AtomicInteger();

        Document document = new Document();
        try {
            PdfSmartCopy copy = new PdfSmartCopy(document, new FileOutputStream(to));
            document.open();

            pdfFiles.forEach(pdf -> {
                try {
                    PdfReader reader = new PdfReader(pdf);
                    int pages = reader.getNumberOfPages();
                    for (int page = 1; page <= pages; page++) {
                        copy.addPage(copy.getImportedPage(reader, page));
                        totalPagesAdded.getAndIncrement();
                    }
                    copy.freeReader(reader);
                } catch (IOException | BadPdfFormatException e) {
                    errors.append("Error occurred when adding ").append(pdf)
                          .append(" to ").append(to).append(": ").append(e.getMessage());
                }
            });
        } catch (DocumentException | IOException e) {
            errors.append("Error occurred when preparing ").append(to).append(": ").append(e.getMessage());
        } finally {
            if (totalPagesAdded.get() < 1) {
                document = null;
            } else {
                try { document.close();} catch (Exception e) { }
            }
        }

        if (errors.length() < 1) {
            return StepResult.success("%s page(s) added from file in %s to %s", totalPagesAdded, matchedFileCount, to);
        } else {
            return StepResult.fail(errors.toString());
        }
    }

    // public StepResult addPages(String from, String to, String onPage) {
    //     requires(StringUtils.endsWithIgnoreCase(to, ".pdf"), "Invalid 'to' file; must be PDF", to);
    //     requires(StringUtils.endsWithIgnoreCase(from, ".pdf"), "Invalid 'from' file; must be PDF", from);
    //     requiresReadableFile(from);
    //     requiresReadableFile(to);
    //     requiresInteger(onPage, "Must be an integer", onPage);
    //
    //     int onPageNumber = NumberUtils.toInt(onPage);
    //
    //     Document document = new Document();
    //
    //     int totalPagesToAdd = 0;
    //     try {
    //         PdfSmartCopy copy = new PdfSmartCopy(document, new FileOutputStream(to));
    //         document.open();
    //
    //         PdfReader reader = new PdfReader(from);
    //         totalPagesToAdd = reader.getNumberOfPages();
    //         for (int page = 1; page <= totalPagesToAdd; page++) {
    //             copy.addPage(copy.getImportedPage(reader, page));
    //         }
    //         copy.freeReader(reader);
    //     } catch (DocumentException e) {
    //         return StepResult.fail("Error when adding pages from %s to %s: %s", from, to, e.getMessage());
    //     } catch (IOException e) {
    //         return StepResult.fail("Error when adding pages from %s to %s: %s", from, to, e.getMessage());
    //     } finally {
    //         document.close();
    //     }
    //
    //     return StepResult.success("%s page(s) from %s added to %s", totalPagesToAdd, from, to);
    // }

    protected Object getFormValue(String var, String name) {
        requiresValidAndNotReadOnlyVariableName(var);
        requiresNotBlank(name, "Invalid form element", name);

        Map formValues = context.getMapData(var);
        return formValues == null || !formValues.containsKey(name) ? null : formValues.get(name);
    }

    protected boolean requiresValidStrategy(String strategy) {
        requiresNotBlank(strategy, "invalid strategy", strategy);
        if (CommonKeyValueIdentStrategies.isValidStrategy(strategy)) { return true; }
        // try again...
        // CommonKeyValueIdentStrategies.harvestStrategy(context);
        // if (!CommonKeyValueIdentStrategies.isValidStrategy(strategy)) {
        fail("Invalid or unknown strategy: " + strategy);
        // }
        return false;
    }

    protected List<File> extractPages(String pdf, String destination) throws IOException {
        requiresNotBlank(pdf, "invalid pdf", pdf);
        requiresReadableFile(pdf);

        File pdfFile = new File(pdf);

        String destinationBaseName = resolvePageBaseName(pdfFile, destination);

        List<File> pages = new ArrayList<>();
        List<Exception> extractionErrors = new ArrayList<>();

        //TestCommand command = context.getCurrentTestStep();
        //boolean verbose = context.isVerbose() && command != null;

        PDDocument document = null;
        try {
            document = PDDocument.load(pdfFile);

            PDDocumentCatalog catalog = document.getDocumentCatalog();
            PDPageTree allPages = catalog.getPages();
            if (allPages == null || allPages.getCount() < 1) { return pages; }
            //List allPages = catalog.getAllPages();
            //if (CollectionUtils.isEmpty(allPages)) { return pages; }

            for (int i = 1; i <= allPages.getCount(); i++) {
                try {
                    String content = extractPage(document, i);
                    File page = new File(destinationBaseName + ".page" + i + ".txt");
                    log("extracted " + StringUtils.length(content) + " bytes from " +
                        "Page " + i + " of '" + pdf + "' as saved as '" + page.getAbsolutePath() + "'");
                    FileUtils.write(page, content, DEF_CHARSET);

                    // todo: added hyperlink to excel output for the generated page files
                    //if (verbose) {
                    //	command.addLinkableParam();
                    //	String param = params.get(i);
                    //	String paramValue = context.replaceTokens(param);
                    //	if (StringUtils.contains(param, "${syspath|")) { command.addLinkableParam(i, paramValue); }
                    //
                    //	log();
                    //}

                    pages.add(page);
                } catch (IOException e) {
                    extractionErrors.add(e);
                }
            }
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    log("Unable to close PDF document", e);
                }
            }
        }

        if (CollectionUtils.isNotEmpty(extractionErrors)) {
            StringBuilder sb = new StringBuilder();
            for (Exception e : extractionErrors) { sb.append(e.getMessage()).append("\n"); }
            throw new IOException("Extraction errors found against '" + pdf + "': " + StringUtils.trim(sb.toString()));
        }

        return pages;
    }

    protected String extractPage(PDDocument document, int pageIndex) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Writer output = new OutputStreamWriter(out);

        try {
            //use default encoding
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            //stripper.setSuppressDuplicateOverlappingText(false);
            //stripper.setAddMoreFormatting(false);
            //stripper.setShouldSeparateByBeads(false);
            stripper.setStartPage(pageIndex);
            stripper.setEndPage(pageIndex);

            // Extract text for main document:
            stripper.writeText(document, output);
        } finally {
            try {
                output.close();
            } catch (IOException e) {
                log("Unable to close output stream", e);
            }

            try {
                out.close();
            } catch (IOException e) {
                log("Unable to close underlying output stream", e);
            }
        }

        return PdfTextExtractor.normalizePdfText(out.toString(), context);
    }

    private String extractText(String pdf) throws IOException { return PdfTextExtractor.extractText(pdf, context); }

    private String resolvePageBaseName(File pdfFile, String destination) {
        requires(pdfFile != null, "invalid/empty PDF file", pdfFile);
        requiresNotBlank(destination, "invalid destination", destination);
        //requiresReadableDirectory(destination, "invalid destination", destination);

        String fileName = StringUtils.substringBeforeLast(pdfFile.getName(), ".");

        File dest = new File(destination);
        if (dest.isDirectory()) { return dest.getAbsolutePath() + separatorChar + fileName; }

        // must be a file.. then we'll overwrite at will
        if (StringUtils.endsWith(destination, ".txt")) { return StringUtils.substringBeforeLast(destination, ".txt"); }
        if (StringUtils.endsWithAny(destination, "/", "\\")) { return destination + fileName; }
        return destination;
    }

    private Map<String, String> extractMetadata(String pdf) throws IOException, XmpParsingException {
        requires(StringUtils.isNotBlank(pdf), "invalid pdf", pdf);

        File pdfFile = new File(pdf);
        requires(pdfFile.exists() && pdfFile.canRead() && pdfFile.length() > 0, "unreadable or empty pdf", pdf);

        String delim = context.getTextDelim();

        Map<String, String> data = new HashMap<>();

        PDDocument document = null;
        try {
            document = PDDocument.load(pdfFile);
            PDDocumentCatalog catalog = document.getDocumentCatalog();

            PDMetadata meta = catalog.getMetadata();
            if (meta != null) {
                DomXmpParser xmpParser = new DomXmpParser();
                XMPMetadata metadata = xmpParser.parse(meta.createInputStream());
                DublinCoreSchema dc = metadata.getDublinCoreSchema();
                if (dc != null) {
                    addMetadata(data, TITLE, dc.getTitle());
                    addMetadata(data, DESCRIPTION, dc.getDescription());
                    addMetadata(data, CREATOR, TextUtils.toString(dc.getCreators(), delim));
                    addMetadata(data, DC_DATES, formatAsDateStrings(dc.getDates()));
                    addMetadata(data, SUBJECTS, TextUtils.toString(dc.getSubjects(), delim));
                }

                AdobePDFSchema pdfSchema = metadata.getAdobePDFSchema();
                if (pdfSchema != null) {
                    addMetadata(data, KEYWORDS, pdfSchema.getKeywords());
                    addMetadata(data, VERSION, pdfSchema.getPDFVersion());
                    addMetadata(data, PRODUCER, pdfSchema.getProducer());
                }

                XMPBasicSchema basic = metadata.getXMPBasicSchema();
                if (basic != null) {
                    addMetadata(data, CREATE_DATE, formatAsDateString(basic.getCreateDate()));
                    addMetadata(data, MODIFY_DATE, formatAsDateString(basic.getModifyDate()));
                    addMetadata(data, CREATOR_TOOL, basic.getCreatorTool());
                }
            } else {
                // The pdf doesn't contain any metadata, try to use the document information instead
                PDDocumentInformation information = document.getDocumentInformation();
                if (information != null) {
                    addMetadata(data, TITLE, information.getTitle());
                    addMetadata(data, SUBJECT, information.getSubject());
                    addMetadata(data, AUTHOR, information.getAuthor());
                    addMetadata(data, CREATOR, information.getCreator());
                    addMetadata(data, PRODUCER, information.getProducer());
                }
            }
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    log("Unable to close PDF document", e);
                }
            }
        }

        return data;
    }

    private void addMetadata(Map<String, String> data, String label, String value) {
        if (StringUtils.isNotBlank(label) && StringUtils.isNotEmpty(value)) { data.put(label, value); }
    }

    private String formatAsDateString(Calendar date) { return PDF_DATE_FORMAT.format(date); }

    private String formatAsDateStrings(List<Calendar> dates) {
        StringBuilder sb = new StringBuilder();
        for (Calendar c : dates) { sb.append(formatAsDateString(c)).append(","); }
        return StringUtils.removeEnd(sb.toString(), ",");
    }

    private void log(String message, Exception e) {
        if (context.isVerbose()) { log(message + ": " + e.getMessage()); }
    }
}
