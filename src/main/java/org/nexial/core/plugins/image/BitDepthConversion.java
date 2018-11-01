package org.nexial.core.plugins.image;

import java.awt.*;
import java.awt.image.*;

import static java.awt.image.BufferedImage.*;

public class BitDepthConversion {

    private static final int[] CMAP_4BIT = new int[]{0x000000, 0x800000, 0x008000, 0x808000,
                                                     0x000080, 0x800080, 0x008080, 0x808080, 0xC0C0C0, 0xFF0000,
                                                     0x00FF00, 0xFFFF00, 0x0000FF, 0xFF00FF, 0x00FFFF, 0xFFFFFF};
    private static final int[] CMAP_2BIT = new int[]{0x000000, 0x808080, 0xC0C0C0, 0xFFFFFF};

    public static BufferedImage changeColorBit(BufferedImage src, int colorBit) {
        if (src.getColorModel().getPixelSize() == colorBit) { return src; }

        BufferedImage dest = src;
        switch (colorBit) {
            case 32:
                dest = convert(src, TYPE_INT_ARGB);
                break;
            case 24:
                dest = convert(src, BufferedImage.TYPE_INT_RGB);
                break;

            case 16:
                dest = convert(src, TYPE_USHORT_565_RGB);
                break;
            case 8:
                dest = convert(src, TYPE_BYTE_INDEXED);
                break;
            case 4:
                dest = convert(src, CMAP_4BIT);
                break;
            case 2:
                dest = convert(src, CMAP_2BIT);
                break;
            case 1:
                dest = convertTo1Bit(src);
                break;
            default:
                throw new RuntimeException("Unsupported image color bit " + colorBit);
        }

        return dest;
    }

    /**
     * Converts the source image to different colour depth(32, 24, 16 & 8-bit) image using the given imageType
     *
     * @param src       the source image to convert
     * @param imageType the imageType depending on required color-bit
     * @return a copy of the source image with a different colour depth(32, 24, 16 & 8-bit), with the
     * custom colour pallette
     */
    private static BufferedImage convert(BufferedImage src, int imageType) {
        BufferedImage dest = new BufferedImage(src.getWidth(), src.getHeight(),
                                               imageType);
        ColorConvertOp cco = new ColorConvertOp(src.getColorModel()
                                                   .getColorSpace(), dest.getColorModel().getColorSpace(), null);
        cco.filter(src, dest);
        return dest;
    }

    /**
     * Converts the source image to 2-bit and 4-bit colour using the given colour map(4 and 16 color pallette resp).
     * No transparency.
     *
     * @param src the source image to convert
     * @return a copy of the source image with a 2-bit or 4-bit colour depth, with the
     * custom colour pallette
     */
    private static BufferedImage convert(BufferedImage src, int[] cmap) {
        IndexColorModel icm = new IndexColorModel(4, cmap.length, cmap, 0,
                                                  false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        BufferedImage dest = new BufferedImage(src.getWidth(), src.getHeight(),
                                               TYPE_BYTE_BINARY, icm);
        ColorConvertOp cco = new ColorConvertOp(src.getColorModel()
                                                   .getColorSpace(), dest.getColorModel().getColorSpace(), null);
        cco.filter(src, dest);

        return dest;
    }

    /**
     * Converts the source to 1-bit colour depth (monochrome). No transparency.
     *
     * @param src the source image to convert
     * @return a copy of the source image with a 1-bit colour depth.
     */
    private static BufferedImage convertTo1Bit(BufferedImage src) {
        IndexColorModel icm = new IndexColorModel(1, 2, new byte[]{(byte) 0,
                                                                   (byte) 0xFF}, new byte[]{(byte) 0, (byte) 0xFF},
                                                  new byte[]{(byte) 0, (byte) 0xFF});

        BufferedImage dest = new BufferedImage(src.getWidth(), src.getHeight(),
                                               TYPE_BYTE_BINARY, icm);

        ColorConvertOp cco = new ColorConvertOp(src.getColorModel()
                                                   .getColorSpace(), dest.getColorModel().getColorSpace(), null);

        cco.filter(src, dest);

        return dest;
    }

}