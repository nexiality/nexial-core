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

package org.nexial.core.plugins.image;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ImageDifferenceTools {
    // The threshold which means the max distance between non-equal pixels.
    private static int threshold = 5;

    //The number which marks how many rectangles. Beginning from 2.
    private int counter = 2;

    // The number of the marking specific rectangle.
    private int regionCount = counter;

    private int[][] matrix;
    private List<Difference> differences = new ArrayList<>();

    public void setMatrix(int[][] matrix) { this.matrix = matrix; }

    public List<Difference> getDifferences() { return differences; }

    /**
     * Group rectangle regions in binary matrix.
     */
    public void groupRegions() {
        for (int row = 0; row < matrix.length; row++) {
            for (int col = 0; col < matrix[row].length; col++) {
                if (matrix[row][col] == 1) {
                    joinToRegion(row, col);
                    regionCount++;
                }
            }
        }
    }

    /**
     * Draw rectangles with the differences pixels.
     *
     * @param graphics the Graphics2D object for drawing rectangles.
     */
    public void drawRectangles(Graphics2D graphics) {
        if (counter > regionCount) { return; }

        Rectangle rectangle = createRectangle(counter);

        int x = rectangle.getMinY();
        int y = rectangle.getMinX();
        int width = rectangle.getWidth();
        int height = rectangle.getHeight();
        // return if x or y has not been changed
        if (x == Integer.MAX_VALUE || y == Integer.MAX_VALUE) { return; }

        graphics.drawRect(x, y, width, height);
        differences.add(new Difference(x, y, width, height));
        counter++;
        drawRectangles(graphics);
    }

    /**
     * The recursive method which go to all directions and finds difference
     * in binary matrix using {@code threshold} for setting max distance between values which equal "1".
     * and set the {@code groupCount} to matrix.
     *
     * @param row the value of the row.
     * @param col the value of the column.
     */
    private void joinToRegion(int row, int col) {
        if (row < 0 || row >= matrix.length || col < 0 || col >= matrix[row].length || matrix[row][col] != 1) {
            return;
        }

        matrix[row][col] = regionCount;

        for (int i = 0; i < threshold; i++) {
            // goes to all directions.
            joinToRegion(row - 1 - i, col);
            joinToRegion(row + 1 + i, col);
            joinToRegion(row, col - 1 - i);
            joinToRegion(row, col + 1 + i);

            joinToRegion(row - 1 - i, col - 1 - i);
            joinToRegion(row + 1 + i, col - 1 - i);
            joinToRegion(row - 1 - i, col + 1 + i);
            joinToRegion(row + 1 + i, col + 1 + i);
        }
    }

    /**
     * Create a {@link Rectangle} object.
     *
     * @param counter the number from marks regions.
     * @return the {@link Rectangle} object.
     */
    private Rectangle createRectangle(int counter) {
        Rectangle rect = new Rectangle();

        for (int y = 0; y < matrix.length; y++) {
            for (int x = 0; x < matrix[0].length; x++) {
                if (matrix[y][x] == counter) {
                    if (x < rect.getMinX()) { rect.setMinX(x); }
                    if (x > rect.getMaxX()) { rect.setMaxX(x); }
                    if (y < rect.getMinY()) { rect.setMinY(y); }
                    if (y > rect.getMaxY()) { rect.setMaxY(y); }
                }
            }
        }

        return rect;
    }
}