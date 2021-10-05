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

/**
 * Object contained data for a rectangle.
 */
public class Rectangle {

    private int minX = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;

    public int getMinX() {
        return minX;
    }

    public void setMinX(int minX) {
        this.minX = minX;
    }

    public int getMinY() { return minY; }

    public void setMinY(int minY) {
        this.minY = minY;
    }

    public int getMaxX() { return maxX; }

    public void setMaxX(int maxX) {
        this.maxX = maxX;
    }

    public int getMaxY() { return maxY; }

    public void setMaxY(int maxY) {
        this.maxY = maxY;
    }

    public int getWidth() { return maxY - minY; }

    public int getHeight() { return maxX - minX; }
}
