
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

package org.nexial.core.utils;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

public final class ClipboardUtils implements ClipboardOwner {

    private static final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    private static ClipboardUtils clipboardUtils = null;

    public static ClipboardUtils getInstance() {
        if (clipboardUtils == null) { clipboardUtils = new ClipboardUtils(); }

        return clipboardUtils;
    }

    private ClipboardUtils() {
    }

    public String getClipboardContents() throws IOException, UnsupportedFlavorException {
        String result = "";
        Transferable contents = clipboard.getContents(null);
        boolean hasTransferableText = (contents != null) && contents.isDataFlavorSupported(DataFlavor.stringFlavor);
        if (hasTransferableText) {
            result = (String) contents.getTransferData(DataFlavor.stringFlavor);
        }
        return result;
    }

    public void setClipboardContents(String value) {
        if (StringUtils.isNotBlank(value)) {
            StringSelection stringSelection = new StringSelection(value);
            clipboard.setContents(stringSelection, this);
        } else {
            clipboard.setContents(new Transferable() {
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[0];
                }

                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return false;
                }

                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                    throw new UnsupportedFlavorException(flavor);
                }
            }, this);
        }
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        // do nothing
    }
}
