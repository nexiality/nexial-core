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

package org.nexial.commons.conversion;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.InvalidTiffContentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.ExceptionConverter;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.io.RandomAccessSourceFactory;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.RandomAccessFileOrArray;
import com.itextpdf.text.pdf.codec.TiffImage;

import static com.itextpdf.text.PageSize.LETTER;

/**
 *
 */
public class Tif2PdfConverter {
    private static final Logger LOGGER = LoggerFactory.getLogger(Tif2PdfConverter.class);

    private Rectangle pageSize = LETTER;
    private String pdfExt = "pdf";
    private boolean deleteWhenComplete;

    public static class DocumentMetaData {
        private String author;
        private String title;
        private String creator;
        private String producer;
        private String keywords;
        private String subject;
        private String language;
        private boolean includeCreateDate;
        private Map<String, String> headers = new HashMap<>();

        public String getAuthor() { return author; }

        public void setAuthor(String author) { this.author = author; }

        public String getTitle() { return title; }

        public void setTitle(String title) { this.title = title; }

        public String getCreator() { return creator; }

        public void setCreator(String creator) { this.creator = creator; }

        public boolean isIncludeCreateDate() { return includeCreateDate; }

        public void setIncludeCreateDate(boolean includeCreateDate) { this.includeCreateDate = includeCreateDate; }

        public String getProducer() { return producer; }

        public void setProducer(String producer) { this.producer = producer; }

        public String getKeywords() { return keywords; }

        public void setKeywords(String keywords) { this.keywords = keywords; }

        public String getSubject() { return subject; }

        public void setSubject(String subject) { this.subject = subject; }

        public String getLanguage() { return language; }

        public void setLanguage(String language) { this.language = language; }

        public void addHeader(String name, String value) { headers.put(name, value); }

        public void removeHeader(String name) { headers.remove(name); }

        public Map<String, String> getHeaders() { return headers; }
    }

    public void setPageSize(Rectangle pageSize) { this.pageSize = pageSize; }

    public void setPdfExt(String pdfExt) { this.pdfExt = pdfExt; }

    public void setDeleteWhenComplete(boolean deleteWhenComplete) { this.deleteWhenComplete = deleteWhenComplete; }

    public void convert(String tiff, OutputStream pdfOut, DocumentMetaData metaData)
        throws IOException, DocumentException {
        if (LOGGER.isDebugEnabled()) { LOGGER.debug("Tif2PdfConverter.convert(): tiff=" + tiff); }

        // sanity check
        if (StringUtils.isBlank(tiff)) { throw new IOException("Unable to convert; no tiff file specified."); }

        File tiffFile = new File(tiff);
        if (!tiffFile.exists()) {
            throw new IOException("Unable to convert; specified tiff file does not exists - " + tiff);
        }
        if (!tiffFile.canRead()) {
            throw new IOException("Unable to convert; specified tiff file cannot be read - " + tiff);
        }

        boolean jitPdfOut = false;
        if (pdfOut == null) {
            String pdf = tiff.substring(0, tiff.lastIndexOf('.') + 1) + pdfExt;
            pdfOut = new FileOutputStream(pdf);
            jitPdfOut = true;
            if (LOGGER.isDebugEnabled()) { LOGGER.debug("new FileOutStream created - " + pdf); }
        }

        byte[] tiffBytes = FileUtils.readFileToByteArray(tiffFile);
        byte[] pdfBytes = convert(tiffBytes, metaData);
        pdfOut.write(pdfBytes, 0, pdfBytes.length);
        if (jitPdfOut) { pdfOut.close(); }

        if (deleteWhenComplete) { FileUtils.deleteQuietly(tiffFile); }
    }

    public byte[] convert(byte[] tiff, DocumentMetaData metaData) throws DocumentException, IOException {
        // rough est. size of pdf... abt the same as tiff
        ByteArrayOutputStream pdf = new ByteArrayOutputStream(tiff.length);

        convert(tiff, pdf, metaData);
        if (LOGGER.isDebugEnabled()) { LOGGER.debug("conversion completes; output size: " + pdf.size()); }
        return pdf.toByteArray();
    }

    /**
     * converts {@code tiff} to {@code pdf} where {@code tiff} is represented as the raw byte array of the TIFF content,
     * and {@code pdf} represents the output stream where converted PDF content will be streamed to.
     * <p/>
     * due to the underlying library, converted images will lose some amount of quality.
     */
    public void convert(byte[] tiff, OutputStream pdf, DocumentMetaData metaData)
        throws IOException, DocumentException {

        // sanity check
        if (tiff == null || tiff.length < 1) { throw new IllegalArgumentException("Unable to convert; no content"); }
        if (pdf == null) { throw new IllegalArgumentException("Unable to convert; no PDF output stream"); }

        Document document = new Document(pageSize, 0, 0, 0, 0);

        RandomAccessFileOrArray ra = null;
        int numberOfPages = -1;
        try {
            PdfWriter writer = resolvePdfWriter(pdf, document);

            initPdfDocument(document, metaData);

            PdfContentByte cb = writer.getDirectContent();

            ra = new RandomAccessFileOrArray(new RandomAccessSourceFactory().createSource(tiff));
            numberOfPages = TiffImage.getNumberOfPages(ra);
            if (LOGGER.isDebugEnabled()) { LOGGER.debug("detected TIFF contains " + numberOfPages + " page(s)."); }

            for (int pageIndex = 0; pageIndex < numberOfPages; ++pageIndex) {
                int page = pageIndex + 1;
                Image img = TiffImage.getTiffImage(ra, page);
                if (img == null) {
                    if (LOGGER.isDebugEnabled()) { LOGGER.debug("Null TIFF found on page " + page); }
                    continue;
                }

                if (LOGGER.isDebugEnabled()) { LOGGER.debug("converting page " + page); }

                setPageSize(document, img);

                img.setAbsolutePosition(0, 0);
                cb.addImage(img);
                document.newPage();
            }
        } catch (ExceptionConverter e) {
            if (numberOfPages == -1) {
                // this means we can't parse tiff byte array to figure out the number of pages...
                throw new InvalidTiffContentException(e.getException());
            }
        } finally {
            if (ra != null) { ra.close(); }
            try { document.close(); } catch (Exception e) {
                LOGGER.warn("Error closing document; might not be problem: " + e.getMessage());
            }
        }
    }

    private void initPdfDocument(Document document, DocumentMetaData metaData) {
        if (metaData != null) {
            if (StringUtils.isNotBlank(metaData.getTitle())) { document.addTitle(metaData.getTitle()); }
            if (StringUtils.isNotBlank(metaData.getAuthor())) { document.addAuthor(metaData.getAuthor()); }
            if (StringUtils.isNotBlank(metaData.getKeywords())) { document.addKeywords(metaData.getKeywords()); }
            if (StringUtils.isNotBlank(metaData.getLanguage())) { document.addLanguage(metaData.getLanguage()); }
            if (StringUtils.isNotBlank(metaData.getSubject())) { document.addSubject(metaData.getSubject()); }
            if (StringUtils.isNotBlank(metaData.getCreator())) { document.addCreator(metaData.getCreator()); }
            if (metaData.isIncludeCreateDate()) { document.addCreationDate(); }
            if (MapUtils.isNotEmpty(metaData.getHeaders())) { metaData.getHeaders().forEach(document::addHeader); }
        }
        document.open();
    }

    private PdfWriter resolvePdfWriter(OutputStream pdf, Document document) throws DocumentException {
        PdfWriter writer = PdfWriter.getInstance(document, pdf);
        writer.setCompressionLevel(0);
        writer.setRgbTransparencyBlending(true);
        writer.setCloseStream(true);
        return writer;
    }

    private void setPageSize(Document document, Image img) {
        Rectangle currentPageSize;
        if (img.getScaledWidth() < img.getScaledHeight()) {
            currentPageSize = new Rectangle(img.getScaledWidth(), img.getScaledHeight());
        } else {
            currentPageSize = new Rectangle(img.getScaledHeight(), img.getScaledWidth()).rotate();
        }

        document.setPageSize(currentPageSize);
        document.newPage();
    }
}