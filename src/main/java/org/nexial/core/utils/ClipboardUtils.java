
package org.nexial.core.utils;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

public final class ClipboardUtils implements ClipboardOwner {

    private Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

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
        StringSelection stringSelection = new StringSelection(value);
        clipboard.setContents(stringSelection, this);
        if (StringUtils.isBlank(value)) {
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
