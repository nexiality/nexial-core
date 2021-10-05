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

package org.nexial.core.plugins.desktop;

import org.apache.commons.lang3.StringUtils;

import static org.nexial.core.plugins.desktop.DesktopConst.*;

public class FormLayout {
    public static final int LAYOUT_LEFT_TO_RIGHT = 1;
    public static final int LAYOUT_TWO_LINES = 2;

    private int layoutType;
    private int labelToInputTolerance;

    public static FormLayout newLeftToRightLayout(int labelToInputTolerance) {
        FormLayout layout = new FormLayout();
        layout.layoutType = LAYOUT_LEFT_TO_RIGHT;
        layout.labelToInputTolerance = labelToInputTolerance;
        return layout;
    }

    public static FormLayout newTwoLineLayout(int labelToInputTolerance) {
        FormLayout layout = new FormLayout();
        layout.layoutType = LAYOUT_TWO_LINES;
        layout.labelToInputTolerance = labelToInputTolerance;
        return layout;
    }

    public boolean isLeftToRight() { return layoutType == LAYOUT_LEFT_TO_RIGHT; }

    public boolean isTwoLines() { return layoutType == LAYOUT_TWO_LINES; }

    public static FormLayout toLayout(String hint) {
        if (StringUtils.equals(hint, LAYOUT_LEFT_2_RIGHT)) { return newLeftToRightLayout(BOUND_GROUP_TOLERANCE); }
        if (StringUtils.equals(hint, LAYOUT_2LINES)) { return newTwoLineLayout(BOUND_GROUP_TOLERANCE); }
        throw new IllegalArgumentException("Unknown layout hint: " + hint);
    }

    public int getLabelToInputTolerance() { return labelToInputTolerance; }

    @Override
    public String toString() {
        return isLeftToRight() ? LAYOUT_LEFT_2_RIGHT : isTwoLines() ? LAYOUT_2LINES : "UNKNOWN";
    }
}
