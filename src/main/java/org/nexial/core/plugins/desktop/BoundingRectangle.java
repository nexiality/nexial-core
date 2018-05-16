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

package org.nexial.core.plugins.desktop;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.openqa.selenium.WebElement;

/**
 * object representation of the coordinates and dimension of a {@link DesktopElement}.
 */
public class BoundingRectangle {
    private int x;
    private int y;
    private int width;
    private int height;
    private WebElement element;
    private DesktopElement desktopElement;

    private BoundingRectangle() { }

    public static BoundingRectangle newInstance(DesktopElement desktopElement) {
        if (desktopElement == null) { return null; }

        WebElement element = desktopElement.element;
        BoundingRectangle instance = newInstance(element);
        if (instance == null) { return null; }

        instance.desktopElement = desktopElement;
        return instance;
    }

    public static BoundingRectangle newInstance(WebElement element) {
        if (element == null) { return null; }

        String bounds = element.getAttribute("BoundingRectangle");
        if (StringUtils.isBlank(bounds) || StringUtils.countMatches(bounds, ",") != 3) { return null; }

        String[] boundArray = StringUtils.split(bounds, ",");

        BoundingRectangle instance = new BoundingRectangle();
        instance.x = NumberUtils.toInt(boundArray[0]);
        instance.y = NumberUtils.toInt(boundArray[1]);
        instance.width = NumberUtils.toInt(boundArray[2]);
        instance.height = NumberUtils.toInt(boundArray[3]);
        instance.element = element;
        return instance;
    }

    public int getX() { return x; }

    public int getY() { return y; }

    public int getWidth() { return width; }

    public int getHeight() { return height; }

    public void adjust(int x, int y, int width, int height) {
        this.x += x;
        this.y += y;
        this.width += width;
        this.height += height;
    }

    public WebElement getElement() { return element; }

    public DesktopElement getDesktopElement() { return desktopElement; }

    @Override
    public String toString() {
        return "(" + x + "," + y + "," + width + "," + height + ") of " +
               (desktopElement);
    }
}
