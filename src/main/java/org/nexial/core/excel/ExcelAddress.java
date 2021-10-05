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

package org.nexial.core.excel;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import static org.nexial.core.excel.ExcelConfig.ALPHABET_COUNT;

/** represent a Excel address or a contiguous address range -- e.g. E2, A7:R44, AB43:ZK3458 */
public class ExcelAddress {
    private String addr;

    private String startAddress;
    private Pair<Integer, Integer> start;

    private String endAddress;
    private Pair<Integer, Integer> end;

    // define all 4 corners
    private Pair<Integer, Integer> topLeft;
    private Pair<Integer, Integer> bottomRight;
    private Pair<Integer, Integer> topRight;
    private Pair<Integer, Integer> bottomLeft;

    private Integer rowStartIndex;
    private Integer rowEndIndex;
    private Integer rowCount;

    private Integer columnStartIndex;
    private Integer columnEndIndex;
    private Integer columnCount;

    /**
     * instance with a single or range address.  For example:
     * <pre>
     * new ExcelAddress("A1:E5");
     * new ExcelAddress("P205");
     * </pre>
     */
    public ExcelAddress(String addr) {
        assert StringUtils.isNotBlank(addr) && StringUtils.length(addr) > 1;
        parse(addr);
    }

    public String getAddr() { return addr; }

    public String getStartAddress() { return startAddress; }

    public Pair<Integer, Integer> getStart() { return start; }

    public String getEndAddress() { return endAddress; }

    public Pair<Integer, Integer> getEnd() { return end; }

    public Pair<Integer, Integer> getTopLeft() { return topLeft; }

    public Pair<Integer, Integer> getBottomRight() { return bottomRight; }

    public Pair<Integer, Integer> getTopRight() { return topRight; }

    public Pair<Integer, Integer> getBottomLeft() { return bottomLeft; }

    public Integer getRowStartIndex() { return rowStartIndex; }

    public Integer getRowEndIndex() { return rowEndIndex; }

    public Integer getRowCount() { return rowCount; }

    public Integer getColumnStartIndex() { return columnStartIndex; }

    public Integer getColumnEndIndex() { return columnEndIndex; }

    public Integer getColumnCount() { return columnCount; }

    @Override
    public String toString() { return addr; }

    public void advanceRow() { advanceRow(1); }

    public void advanceRow(int advances) {
        String address = ExcelAddress.toLetterCellRef(columnStartIndex + 1) + (rowStartIndex + 1 + advances);
        if (columnEndIndex != null && rowEndIndex != null) {
            address += ":" + ExcelAddress.toLetterCellRef(columnEndIndex + 1) + (rowEndIndex + 1 + advances);
        }

        parse(address);
    }

    public void advanceColumn() { advanceColumn(1); }

    public void advanceColumn(int advances) {
        String address = ExcelAddress.toLetterCellRef(columnStartIndex + 1 + advances) + (rowStartIndex + 1);
        if (columnEndIndex != null && rowEndIndex != null) {
            address += ":" + ExcelAddress.toLetterCellRef(columnEndIndex + 1 + advances) + (rowEndIndex + 1);
        }

        parse(address);
    }

    public static String toLetterCellRef(int cellRef) {
        if (cellRef <= ALPHABET_COUNT) { return Character.toString((char) (cellRef - 1 + 'A')); }

        int quotient = cellRef % ALPHABET_COUNT;
        if (quotient == 0) { quotient = ALPHABET_COUNT; }

        int dividend = (int) Math.ceil((double) cellRef / ALPHABET_COUNT);
        if (dividend > ALPHABET_COUNT + 1) {
            int middleLetter = dividend % ALPHABET_COUNT - 2;
            if (middleLetter < 0) { middleLetter = ALPHABET_COUNT - 1; }
            if (middleLetter >= ALPHABET_COUNT) {
                middleLetter = middleLetter / (ALPHABET_COUNT + 1) + middleLetter % (ALPHABET_COUNT + 1);
            }
            middleLetter += 'A';

            int lastLetter = quotient - 1 + 'A';
            int firstLetter = dividend / (ALPHABET_COUNT + 1);

            return toLetterCellRef(firstLetter) +
                   Character.toString((char) middleLetter) +
                   Character.toString((char) lastLetter);
        }

        return Character.toString((char) (dividend - 2 + 'A')) + Character.toString((char) (quotient - 1 + 'A'));
    }

    public static int fromColumnLettersToOrdinalNumber(String letters) {
        int columnNum = 0;
        for (char ch : letters.toCharArray()) { columnNum = columnNum * ALPHABET_COUNT + ch - 'A' + 1; }
        return columnNum;
    }

    protected void parse(String addr) {
        this.addr = addr;

        startAddress = resolveStartAddress();
        if (startAddress == null) {
            start = null;
            topLeft = null;
        } else {
            start = resolvePair(startAddress);
            topLeft = new ImmutablePair<>(start.getLeft(), start.getRight());
        }

        endAddress = resolveEndAddress();
        if (endAddress == null) {
            end = start;
            bottomRight = topLeft;
        } else {
            end = resolvePair(endAddress);
            bottomRight = new ImmutablePair<>(end.getLeft(), end.getRight());
        }

        if (topLeft != null) {
            topRight = new ImmutablePair<>(topLeft.getLeft(), bottomRight.getRight());
            bottomLeft = new ImmutablePair<>(bottomRight.getLeft(), topLeft.getRight());
            rowStartIndex = topLeft.getLeft();
            columnStartIndex = topLeft.getRight();

            rowEndIndex = bottomRight.getLeft();
            columnEndIndex = bottomRight.getRight();
        }

        if (end == null || start == null) {
            rowCount = 1;
            columnCount = 1;
        } else {
            rowCount = end.getLeft() - start.getLeft() + 1;
            columnCount = end.getRight() - start.getRight() + 1;
        }
    }

    protected String resolveStartAddress() {
        int splitPoint = StringUtils.indexOfAny(addr, ":");
        if (splitPoint == -1) {
            return StringUtils.upperCase(addr);
        } else {
            return StringUtils.upperCase(addr.substring(0, splitPoint));
        }
    }

    protected String resolveEndAddress() {
        int splitPoint = StringUtils.indexOfAny(addr, ":");
        if (splitPoint == -1) {
            return StringUtils.upperCase(startAddress);
        } else {
            return StringUtils.upperCase(addr.substring(splitPoint + 1));
        }
    }

    protected Pair<Integer, Integer> resolvePair(String address) {
        String[] pair = StringUtils.splitByCharacterType(address);
        if (pair == null || pair.length < 2) {throw new IllegalArgumentException("Invalid Excel Address: " + address); }
        return new ImmutablePair<>(NumberUtils.toInt(pair[1]) - 1, fromColumnLettersToOrdinalNumber(pair[0]) - 1);
    }
}
