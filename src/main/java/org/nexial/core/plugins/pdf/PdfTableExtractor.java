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

package org.nexial.core.plugins.pdf;

import java.io.*;
import java.util.*;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.list.TreeList;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.nexial.commons.utils.CollectionUtil;
import org.nexial.core.plugins.pdf.Table.Cell;
import org.nexial.core.plugins.pdf.Table.Row;
import org.nexial.core.utils.ConsoleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Range;

public class PdfTableExtractor {
    private final Logger logger = LoggerFactory.getLogger(PdfTableExtractor.class);

    private final Map<String, LineRange> lineRanges = new ListOrderedMap<>();

    private File input;
    private InputStream inputStream;
    private PDDocument document;
    private String password;
    private Map<String, Object> existingFormValues;

    public static class LineRange {
        private int pageIdx;
        private int startIdx = -1;
        private int endIdx = -1;
        private String startText;
        private String endText;

        public static LineRange newRange(int pageIdx, int startIdx, int endIdx) {
            LineRange lineRange = new LineRange();
            lineRange.pageIdx = pageIdx;
            lineRange.startIdx = startIdx;
            lineRange.endIdx = endIdx;
            return lineRange;
        }

        public static LineRange newRange(int pageIdx, String startText, String endText) {
            LineRange lineRange = new LineRange();
            lineRange.pageIdx = pageIdx;
            lineRange.startText = startText;
            lineRange.endText = endText;
            return lineRange;
        }

        public int getPageIdx() { return pageIdx; }

        public int getStartIdx() { return startIdx; }

        public int getEndIdx() { return endIdx; }

        public String getStartText() { return startText; }

        public String getEndText() { return endText; }

        public boolean byLineNumber() { return startIdx != -1 && endIdx != -1; }

        @Override
        public String toString() {
            if (byLineNumber()) {
                return "page " + pageIdx + ", from " + startIdx + " to " + endIdx;
            } else {
                return "page " + pageIdx + ", from line with '" + startText + "' to line with '" + endText + "'";
            }
        }
    }

    private static class TextPositionExtractor extends PDFTextStripper {
        private final List<TextPosition> textPositions = new ArrayList<>();
        private final int pageId;

        private TextPositionExtractor(PDDocument document, int pageId) throws IOException {
            super();
            setSortByPosition(false);
            setShouldSeparateByBeads(false);
            setAddMoreFormatting(false);

            super.document = document;
            this.pageId = pageId;
        }

        public void stripPage(int pageId) throws IOException {
            setStartPage(pageId + 1);
            setEndPage(pageId + 1);
            try (Writer writer = new OutputStreamWriter(new ByteArrayOutputStream())) { writeText(document, writer); }
        }

        @Override
        protected void writeString(String string, List<TextPosition> textPositions) {
            this.textPositions.addAll(textPositions);
        }

        /**
         * and order by textPosition.getY() ASC
         */
        private List<TextPosition> extract() throws IOException {
            stripPage(pageId);

            //sort
            textPositions.sort((o1, o2) -> Float.compare(o1.getY(), o2.getY()));

            return textPositions;
        }
    }

    private static class RangeBuilder {
        private final Logger logger = LoggerFactory.getLogger(RangeBuilder.class);
        private final List<Range<Integer>> ranges = new ArrayList<>();

        public RangeBuilder addRange(Range<Integer> range) {
            ranges.add(range);
            return this;
        }

        /**
         * The result will be ordered by lowerEndpoint ASC
         */
        public List<Range<Integer>> build() {
            List<Range<Integer>> retVal = new ArrayList<>();
            //order range by lower Bound
            ranges.sort(Comparator.comparing(Range::lowerEndpoint));

            for (Range<Integer> range : ranges) {
                if (retVal.isEmpty()) {
                    retVal.add(range);
                } else {
                    Range<Integer> lastRange = retVal.get(retVal.size() - 1);
                    if (lastRange.isConnected(range)) {
                        retVal.set(retVal.size() - 1, lastRange.span(range));
                    } else {
                        retVal.add(range);
                    }
                }
            }

            //debug
            logger.debug("Found " + retVal.size() + " range(s) to be trapped for content extraction");

            return retVal;
        }
    }

    public PdfTableExtractor setSource(InputStream inputStream) {
        this.inputStream = inputStream;
        return this;
    }

    public PdfTableExtractor setSource(InputStream inputStream, String password) {
        this.inputStream = inputStream;
        this.password = password;
        return this;
    }

    public PdfTableExtractor setSource(File file) {
        this.input = file;
        try {
            return setSource(new FileInputStream(file));
        } catch (FileNotFoundException ex) {
            throw new RuntimeException("Invalid pdf file", ex);
        }
    }

    public PdfTableExtractor setSource(String filePath) { return setSource(new File(filePath)); }

    public PdfTableExtractor setSource(File file, String password) {
        this.input = file;
        this.password = password;
        try {
            return setSource(new FileInputStream(file), password);
        } catch (FileNotFoundException ex) {
            throw new RuntimeException("Invalid pdf file", ex);
        }
    }

    public PdfTableExtractor setSource(String filePath, String password) {
        return setSource(new File(filePath), password);
    }

    /**
     * only meaningful to keyInHeaderRowOnly mode where we want to continue collecting parsed values into existing
     * map - effectively joining previously parsed values with new ones.
     */
    public void setExistingFormValues(Map<String, Object> existingFormValues) {
        this.existingFormValues = existingFormValues;
    }

    public PdfTableExtractor addTextRange(String rangeName, int pageNo, int startLine, int endLine) {
        LineRange lineRange = LineRange.newRange(pageNo, startLine, endLine);
        lineRanges.put(rangeName, lineRange);
        return this;
    }

    public List<Table> extract() {
        List<Table> retVal = new ArrayList<>();
        Multimap<Integer, Range<Integer>> pageIdNLineRangesMap = LinkedListMultimap.create();
        Multimap<Integer, TextPosition> pageIdNTextsMap = LinkedListMultimap.create();

        // absence of lineRanges means extract everything!

        try {
            document = password != null ?
                       PDDocument.load(new FileInputStream(input), password) :
                       PDDocument.load(new FileInputStream(input));

            int pageCount = document.getNumberOfPages();
            for (Map.Entry<String, LineRange> entry : lineRanges.entrySet()) {
                LineRange lineRange = entry.getValue();
                int pageId = lineRange.getPageIdx();
                if (pageId >= 0 && pageId < pageCount) {
                    List<TextPosition> texts = extractTextPositions(pageId);    //sorted by .getY() ASC

                    List<Range<Integer>> lineRanges = getLineRanges(texts, lineRange);

                    // todo: wrong! should be keyed by refName

                    //extract line ranges
                    pageIdNLineRangesMap.putAll(pageId, lineRanges);

                    //extract column ranges
                    pageIdNTextsMap.putAll(pageId, getTextsByLineRanges(lineRanges, texts));
                }
            }

            //Calculate columnRanges
            List<Range<Integer>> columnRanges = getColumnRanges(pageIdNTextsMap.values());
            for (int pageId : pageIdNTextsMap.keySet()) {
                Table table = buildTable(pageId,
                                         (List) pageIdNTextsMap.get(pageId),
                                         (List) pageIdNLineRangesMap.get(pageId),
                                         columnRanges);
                retVal.add(table);
                //debug
                logger.debug("Found " + table.getRows().size() + " row(s) " +
                             "and " + columnRanges.size() + " column(s) of a table in page " + pageId);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Parse pdf file fail", ex);
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException ex) {
                    logger.error(null, ex);
                }
            }
        }

        return retVal;
    }

    public Map<String, Object> extractRangeFromMap(LineRange lineRange, KeyValueIdentStrategy keyValueIdentStrategy)
        throws IOException {
        try {
            document = password != null ?
                       PDDocument.load(new FileInputStream(input), password) :
                       PDDocument.load(new FileInputStream(input));

            int pageCount = document.getNumberOfPages();
            int pageId = lineRange.getPageIdx();
            if (pageId < 0 || pageId >= pageCount) {
                ConsoleUtils.error("requested page > available page");
                return new LinkedHashMap<>();
            }

            // List<TextPosition> texts = extractTextPositions(pageId);    //sorted by .getY() ASC
            // List<Range<Integer>> lineRanges = getLineRanges(texts, lineRange);

            Map<Range<Integer>, Set<TextPosition>> pageContent = sortContent(extractTextPositions(pageId));
            List<Range<Integer>> lineRanges = getLineRanges(pageContent, lineRange);

            //extract line ranges
            Multimap<Integer, Range<Integer>> pageIdNLineRangesMap = LinkedListMultimap.create();
            pageIdNLineRangesMap.putAll(pageId, lineRanges);

            //extract column ranges
            Multimap<Integer, TextPosition> pageIdNTextsMap = LinkedListMultimap.create();
            // pageIdNTextsMap.putAll(pageId, getTextsByLineRanges(lineRanges, texts));
            pageIdNTextsMap.putAll(pageId, getTextsByLineRanges(lineRanges, pageContent));

            // limit to specified line ranges
            Map<Range<Integer>, Set<TextPosition>> limitedContent = limitContent(pageContent, lineRanges);

            //Calculate columnRanges
            // List<Range<Integer>> columnRanges = getColumnRanges(pageIdNTextsMap.values());
            List<Range<Integer>> columnRanges = getColumnRanges(limitedContent);

            // Table table = buildTable(pageId, (List) pageIdNTextsMap.get(pageId), (List) pageIdNLineRangesMap.get(pageId), columnRanges);
            Table table = buildTable(pageId, limitedContent, columnRanges);

            //debug
            logger.debug("Found " + table.getRows().size() + " row(s) " +
                         "and " + columnRanges.size() + " column(s) of a table in page " + pageId);

            MapFormatter formatter = TableFormatter.newMapFormatter(keyValueIdentStrategy);
            if (MapUtils.isNotEmpty(existingFormValues)) { formatter.setExistingFormValues(existingFormValues); }
            return formatter.format(table);
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException ex) {
                    logger.error(null, ex);
                }
            }
        }
    }

    /**
     * Texts in tableContent have been ordered by .getY() ASC
     */
    private Table buildTable(int pageIdx,
                             List<TextPosition> tableContent,
                             List<Range<Integer>> rowTrapRanges,
                             List<Range<Integer>> columnTrapRanges) {
        Table retVal = new Table(pageIdx, columnTrapRanges.size());

        int idx = 0;
        int rowIdx = 0;

        List<TextPosition> rowContent = new ArrayList<>();
        while (idx < tableContent.size()) {
            TextPosition textPosition = tableContent.get(idx);
            Range<Integer> rowTrapRange = rowTrapRanges.get(rowIdx);
            Range<Integer> textRange = Range.closed((int) textPosition.getY(),
                                                    (int) (textPosition.getY() + textPosition.getHeight()));
            if (rowTrapRange.encloses(textRange)) {
                rowContent.add(textPosition);
                idx++;
            } else {
                Row row = buildRow(rowIdx, rowContent, columnTrapRanges);
                retVal.getRows().add(row);
                //next row: clear rowContent
                rowContent.clear();
                rowIdx++;
            }
        }

        //last row
        if (!rowContent.isEmpty() && rowIdx < rowTrapRanges.size()) {
            Row row = buildRow(rowIdx, rowContent, columnTrapRanges);
            retVal.getRows().add(row);
        }

        return retVal;
    }

    private Row buildRow(int rowIdx, List<TextPosition> rowContent, List<Range<Integer>> columnTrapRanges) {
        //Sort rowContent
        sortRowContent(rowContent);

        Row retVal = new Row(rowIdx);
        int idx = 0;
        int columnIdx = 0;
        List<TextPosition> cellContent = new ArrayList<>();
        while (idx < rowContent.size()) {
            TextPosition textPosition = rowContent.get(idx);
            Range<Integer> columnTrapRange = columnTrapRanges.get(columnIdx);
            Range<Integer> textRange = Range.closed((int) textPosition.getX(),
                                                    (int) (textPosition.getX() + textPosition.getWidth()));
            if (columnTrapRange.encloses(textRange)) {
                cellContent.add(textPosition);
                idx++;
            } else {
                Cell cell = buildCell(columnIdx, cellContent);
                retVal.getCells().add(cell);
                //next column: clear cell content
                cellContent.clear();
                columnIdx++;
            }
        }

        if (!cellContent.isEmpty() && columnIdx < columnTrapRanges.size()) {
            Cell cell = buildCell(columnIdx, cellContent);
            retVal.getCells().add(cell);
        }

        return retVal;
    }

    private void sortRowContent(List<TextPosition> rowContent) {
        rowContent.sort((o1, o2) -> Float.compare(o1.getX(), o2.getX()));
    }

    private void sortCellContent(List<TextPosition> rowContent) {
        rowContent.sort((o1, o2) -> {
            if (o1.getY() < o2.getY()) { return -1; }
            if (o1.getY() > o2.getY()) { return 1; }
            if (o1.getX() < o2.getX()) { return -1; }
            if (o1.getX() > o2.getX()) { return 1; }
            return 0;
        });
    }

    private Cell buildCell(int columnIdx, List<TextPosition> cellContent) {
        sortCellContent(cellContent);

        StringBuilder cellContentBuilder = new StringBuilder();
        for (TextPosition textPosition : cellContent) { cellContentBuilder.append(textPosition.getUnicode()); }

        return new Cell(columnIdx, cellContentBuilder.toString());
    }

    private List<TextPosition> extractTextPositions(int pageId) throws IOException {
        TextPositionExtractor extractor = new TextPositionExtractor(document, pageId);
        return extractor.extract();
    }

    /**
     * Remove all texts in excepted lines
     *
     * TexPositions are sorted by .getY() ASC
     */
    private List<TextPosition> getTextsByLineRanges(List<Range<Integer>> lineRanges, List<TextPosition> textPositions) {
        List<TextPosition> retVal = new ArrayList<>();
        int idx = 0;
        int lineIdx = 0;
        while (idx < textPositions.size() && lineIdx < lineRanges.size()) {
            TextPosition textPosition = textPositions.get(idx);
            Range<Integer> textRange = Range.closed((int) textPosition.getY(),
                                                    (int) (textPosition.getY() + textPosition.getHeight()));
            Range<Integer> lineRange = lineRanges.get(lineIdx);
            if (lineRange.encloses(textRange)) {
                retVal.add(textPosition);
                idx++;
            } else if (lineRange.upperEndpoint() < textRange.lowerEndpoint()) {
                lineIdx++;
            } else {
                idx++;
            }
        }

        return retVal;
    }

    private List<Range<Integer>> getColumnRanges(Collection<TextPosition> texts) {
        RangeBuilder rangesBuilder = new RangeBuilder();
        for (TextPosition text : texts) {
            rangesBuilder.addRange(Range.closed((int) text.getX(), (int) (text.getX() + text.getWidth())));
        }
        return rangesBuilder.build();
    }

    private List<Range<Integer>> getLineRanges(List<TextPosition> pageContent, LineRange lineRange) {
        RangeBuilder rangeBuilder = new RangeBuilder();
        for (TextPosition textPosition : pageContent) {
            Range<Integer> range = Range.closed((int) textPosition.getY(),
                                                (int) (textPosition.getY() + textPosition.getHeight()));
            rangeBuilder.addRange(range);
        }

        return limitRanges(rangeBuilder.build(), lineRange);
    }

    private Map<Range<Integer>, Set<TextPosition>> sortContent(List<TextPosition> pageContent) {
        // use treemap to ensure ascending sort for line
        Map<Range<Integer>, Set<TextPosition>> textByLine = new TreeMap<>(Comparator.comparing(Range::lowerEndpoint));
        pageContent.forEach(textPosition -> {
            Range<Integer> range = Range.closed(Math.round(textPosition.getY()),
                                                Math.round(textPosition.getY()) + Math.round(textPosition.getHeight()));
            if (textByLine.containsKey(range)) {
                textByLine.get(range).add(textPosition);
            } else {
                // use treeset to ensure ascending sort for text (left to right ordering)
                Set<TextPosition> list = new TreeSet<>(Comparator.comparing(TextPosition::getX));
                list.add(textPosition);
                textByLine.put(range, list);
            }
        });

        List<Range<Integer>> lineRanges = CollectionUtil.toList(textByLine.keySet());

        // always start from line 2, since line 1 is ALWAYS the first line
        for (int i = 1; i < lineRanges.size(); i++) {
            // get current, prev and next line for y-coord comparison
            Range<Integer> currentLine = lineRanges.get(i);
            Range<Integer> prevLine = lineRanges.get(i - 1);
            Range<Integer> nextLine = i < lineRanges.size() - 1 ? lineRanges.get(i + 1) : null;

            boolean combineCurrentAndPrev = false;
            boolean combineCurrentAndNext = false;
            int newLowerEndpoint = currentLine.lowerEndpoint();
            int newUpperEndpoint = currentLine.upperEndpoint();

            if (prevLine != null && Math.abs(currentLine.lowerEndpoint() - prevLine.lowerEndpoint()) <= 4) {
                combineCurrentAndPrev = true;
                newLowerEndpoint = Math.round((currentLine.lowerEndpoint() + prevLine.lowerEndpoint()) / 2);
                newUpperEndpoint = Math.max(newUpperEndpoint, prevLine.upperEndpoint());
            }

            if (nextLine != null && Math.abs(currentLine.lowerEndpoint() - nextLine.lowerEndpoint()) <= 4) {
                combineCurrentAndNext = true;
                if (combineCurrentAndPrev) {
                    newLowerEndpoint = Math.round((currentLine.lowerEndpoint() +
                                                   prevLine.lowerEndpoint() +
                                                   nextLine.lowerEndpoint()) / 3);
                } else {
                    newLowerEndpoint = Math.round((currentLine.lowerEndpoint() + nextLine.lowerEndpoint()) / 2);
                }
                newUpperEndpoint = Math.max(newUpperEndpoint, nextLine.upperEndpoint());
            }

            if (combineCurrentAndPrev || combineCurrentAndNext) {
                Range<Integer> mergedLine = Range.closed(newLowerEndpoint, newUpperEndpoint);

                Set<TextPosition> text = textByLine.remove(currentLine);
                lineRanges.remove(currentLine);

                if (combineCurrentAndPrev) {
                    Set<TextPosition> prevText = textByLine.remove(prevLine);
                    lineRanges.remove(prevLine);
                    text.addAll(prevText);
                }
                if (combineCurrentAndNext) {
                    Set<TextPosition> nextText = textByLine.remove(nextLine);
                    lineRanges.remove(nextLine);
                    text.addAll(nextText);
                }

                // current, prev and next line removed (as appropriate)... all merged to new mergedLine
                lineRanges.add(i, mergedLine);
                i--;
                textByLine.put(mergedLine, text);
            }
        }

        return textByLine;
    }

    private List<Range<Integer>> getLineRanges(Map<Range<Integer>, Set<TextPosition>> pageContent,
                                               LineRange lineRange) {
        RangeBuilder rangeBuilder = new RangeBuilder();
        pageContent.keySet().forEach(rangeBuilder::addRange);
        return limitRanges(rangeBuilder.build(), lineRange);
    }

    private List<Range<Integer>> limitRanges(List<Range<Integer>> availableRanges, LineRange limit) {
        List<Range<Integer>> retVal = new ArrayList<>();
        for (int lineIdx = 0; lineIdx < availableRanges.size(); lineIdx++) {
            boolean included = false;
            if (limit.byLineNumber()) {
                included = lineIdx >= limit.getStartIdx() && lineIdx <= limit.getEndIdx();
            }

            if (included) { retVal.add(availableRanges.get(lineIdx)); }
        }

        return retVal;
    }

    private List<TextPosition> getTextsByLineRanges(List<Range<Integer>> lineRanges,
                                                    Map<Range<Integer>, Set<TextPosition>> pageContent) {
        List<TextPosition> retVal = new ArrayList<>();

        int idx = 0;
        int lineIdx = 0;

        List<Range<Integer>> textRanges = CollectionUtil.toList(pageContent.keySet());
        while (idx < textRanges.size() && lineIdx < lineRanges.size()) {
            Range<Integer> lineRange = lineRanges.get(lineIdx);

            Range<Integer> textRange = textRanges.get(idx);
            if (lineRange.encloses(textRange)) {
                retVal.addAll(pageContent.get(textRange));
                idx++;
            } else if (lineRange.upperEndpoint() < textRange.lowerEndpoint()) {
                lineIdx++;
            } else {
                idx++;
            }
        }

        return retVal;
    }

    private Map<Range<Integer>, Set<TextPosition>> limitContent(Map<Range<Integer>, Set<TextPosition>> pageContent,
                                                                List<Range<Integer>> lineRanges) {
        Map<Range<Integer>, Set<TextPosition>> limited = new TreeMap<>(Comparator.comparing(Range::lowerEndpoint));

        if (MapUtils.isEmpty(pageContent) || CollectionUtils.isEmpty(lineRanges)) { return limited; }

        pageContent.forEach((line, text) -> lineRanges.forEach(range -> {
            if (line == range || range.encloses(line)) { limited.put(line, text); }
        }));

        return limited;
    }

    private List<Range<Integer>> getColumnRanges(Map<Range<Integer>, Set<TextPosition>> content) {
        List<TextPosition> texts = new TreeList<>();
        content.forEach((line, rowContent) -> texts.addAll(rowContent));

        RangeBuilder rangesBuilder = new RangeBuilder();
        for (TextPosition text : texts) {
            rangesBuilder.addRange(Range.closed((int) text.getX(), (int) (text.getX() + text.getWidth())));
        }
        return rangesBuilder.build();
    }

    private Table buildTable(int pageIdx,
                             Map<Range<Integer>, Set<TextPosition>> content,
                             List<Range<Integer>> columnRanges) {
        Table retVal = new Table(pageIdx, columnRanges.size());

        final int[] rowIdx = {0};

        // List<TextPosition> rowContent = new ArrayList<>();

        content.forEach((line, rowContent) -> {
            Row row = buildRow(rowIdx[0], CollectionUtil.toList(rowContent), columnRanges);
            retVal.getRows().add(row);
            rowIdx[0]++;
        });

        return retVal;
    }

    ///** This page will be analyze and extract its table content */
    //public PdfTableExtractor addPage(int pageIdx) {
    //	extractedPages.add(pageIdx);
    //	return this;
    //}
    //public PdfTableExtractor exceptPage(int pageIdx) {
    //	exceptedPages.add(pageIdx);
    //	return this;
    //}
    ///** Avoid a specific line in a specific page. LineIdx can be negative number, -1 is the last line */
    //public PdfTableExtractor exceptLine(int pageIdx, int[] lineIdxs) {
    //	for (int lineIdx : lineIdxs) { pageNExceptedLinesMap.put(pageIdx, lineIdx); }
    //	return this;
    //}
    ///** Avoid this line in all extracted pages. LineIdx can be negative number, -1 is the last line */
    //public PdfTableExtractor exceptLine(int[] lineIdxs) {
    //	exceptLine(-1, lineIdxs);
    //	return this;
    //}
    //private boolean isExceptedLine(int pageIdx, int lineIdx) {
    //	return pageNExceptedLinesMap.containsEntry(pageIdx, lineIdx) ||
    //	       pageNExceptedLinesMap.containsEntry(-1, lineIdx);
    //}
    //
    //private List<Range<Integer>> getLineRanges(int pageId, List<TextPosition> pageContent) {
    //	RangeBuilder lineRangeBuilder = new RangeBuilder();
    //	for (TextPosition textPosition : pageContent) {
    //		Range<Integer> lineRange = Range.closed((int) textPosition.getY(),
    //		                                        (int) (textPosition.getY() + textPosition.getHeight()));
    //		lineRangeBuilder.addRange(lineRange);
    //	}
    //
    //	List<Range<Integer>> lineTrapRanges = lineRangeBuilder.build();
    //	return removeExceptedLines(pageId, lineTrapRanges);
    //}

    //private List<Range<Integer>> removeExceptedLines(int pageIdx, List<Range<Integer>> lineTrapRanges) {
    //	List<Range<Integer>> retVal = new ArrayList<>();
    //	for (int lineIdx = 0; lineIdx < lineTrapRanges.size(); lineIdx++) {
    //		boolean isExceptedLine = isExceptedLine(pageIdx, lineIdx) ||
    //		                         isExceptedLine(pageIdx, lineIdx - lineTrapRanges.size());
    //		if (!isExceptedLine) { retVal.add(lineTrapRanges.get(lineIdx)); }
    //	}
    //
    //	return retVal;
    //}
}
