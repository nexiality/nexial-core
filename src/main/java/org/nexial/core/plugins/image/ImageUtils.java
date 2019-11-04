package org.nexial.core.plugins.image;

import java.awt.*;
import java.awt.image.*;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.NexialConst.Image.ImageType;
import org.nexial.core.utils.ConsoleUtils;

import static java.awt.AlphaComposite.*;
import static java.awt.RenderingHints.*;
import static java.awt.Transparency.*;
import static java.awt.image.BufferedImage.*;
import static java.awt.image.DataBuffer.*;
import static org.nexial.core.NexialConst.Image.ImageType.jpg;
import static org.nexial.core.NexialConst.Image.MIN_TRIM_SPACES;

public class ImageUtils {
    private static final int[] CMAP_4BIT = new int[]{0x000000, 0x800000, 0x008000, 0x808000,
                                                     0x000080, 0x800080, 0x008080, 0x808080,
                                                     0xC0C0C0, 0xFF0000, 0x00FF00, 0xFFFF00,
                                                     0x0000FF, 0xFF00FF, 0x00FFFF, 0xFFFFFF};
    private static final int[] CMAP_2BIT = new int[]{0x000000, 0x808080, 0xC0C0C0, 0xFFFFFF};

    public static BufferedImage crop(BufferedImage src, int x, int y, int width, int height) {

        x = Math.max(0, x);
        y = Math.max(0, y);
        width = width == -1 ? src.getWidth() - x : width;
        height = height == -1 ? src.getHeight() - y : height;

        BufferedImage newImage = src.getSubimage(x, y, width, height);
        BufferedImage cropped = new BufferedImage(newImage.getWidth(), newImage.getHeight(), TYPE_INT_RGB);
        Graphics g = cropped.createGraphics();
        g.drawImage(newImage, 0, 0, null);

        return cropped;
    }

    public static BufferedImage convertColorBit(BufferedImage src, int colorBit) {
        if (src == null) { throw new IllegalArgumentException("Cannot process a null image"); }

        int imageBits = src.getColorModel().getPixelSize();
        if (imageBits <= colorBit) {
            ConsoleUtils.log("color bit of source image (" + imageBits + ") is equal/less than target bit (" +
                             colorBit + "); no conversion needed");
            return src;
        }

        switch (colorBit) {
            case 32:
                return convertBits(src, TYPE_INT_ARGB);
            case 24:
                return convertBits(src, TYPE_INT_RGB);
            case 16:
                return convertBits(src, TYPE_USHORT_565_RGB);
            case 8:
                return convertBits(src, TYPE_BYTE_INDEXED);
            case 4:
                return convertBits(src, CMAP_4BIT);
            case 2:
                return convertBits(src, CMAP_2BIT);
            case 1:
                return convertTo1Bit(src);
            default:
                throw new RuntimeException("Unsupported image color bit " + colorBit);
        }
    }

    public static BufferedImage resize(BufferedImage src, int width, int height) {
        int newWidth = width == -1 ? src.getWidth() : width;
        int newHeight = height == -1 ? src.getHeight() : height;

        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, TYPE_INT_RGB);

        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(src, 0, 0, newWidth, newHeight, null);
        g.dispose();
        g.setComposite(Src);
        g.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);
        g.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);

        return resizedImage;
    }

    public static BufferedImage trimOffOuterColor(BufferedImage image, Color color) {
        // Trimming left side white spaces
        int num = 0;
        int height = image.getHeight();
        int width = image.getWidth();
        for (int i = 0; i < width; i++) {
            if (!isMatched1(image, i, color)) { break; }
            num++;
        }
        int x = num < MIN_TRIM_SPACES ? 0 : num;

        // Trimming top side white spaces
        num = 0;
        for (int j = 0; j < height; j++) {
            if (!isMatched2(image, j, color)) { break; }
            num++;
        }
        int y = num < MIN_TRIM_SPACES ? 0 : num;
        if (x == width && y == height) { return image; }

        // Trimming right side white spaces
        num = 0;
        for (int i = width - 1; i >= 0; i--) {
            if (!isMatched1(image, i, color)) { break; }
            num++;
        }
        width = width - (num < MIN_TRIM_SPACES ? 0 : num) - x;

        // Trimming bottom side white spaces
        num = 0;
        for (int j = height - 1; j >= 0; j--) {
            if (!isMatched2(image, j, color)) { break; }
            num++;
        }
        height = height - (num < MIN_TRIM_SPACES ? 0 : num) - y;

        // return cropped image using dimensions
        return image.getSubimage(x, y, width, height);
    }

    public static BufferedImage convert(BufferedImage src, String fromFormat, String toFormat) {
        if (StringUtils.equalsIgnoreCase(fromFormat, toFormat)) {
            ConsoleUtils.log("source and target format are the same; no conversion needed");
            return src;
        }

        ImageType type = ImageType.valueOf(StringUtils.lowerCase(toFormat));

        // create a blank, RGB, same width and height, and a white background
        BufferedImage dest = new BufferedImage(src.getWidth(), src.getHeight(), type.getImageType());
        dest.createGraphics().drawImage(src, 0, 0, null);
        return dest;
    }

    public static ImageType toSupportedFormat(String format) throws IOException {
        if (StringUtils.isBlank(format)) { throw new IOException("Empty/blank format not supported"); }

        String imageFormat = format.trim().toLowerCase();
        if (StringUtils.equals(imageFormat, "jpeg")) { imageFormat = "jpg"; }

        try {
            ImageType imageType = ImageType.valueOf(imageFormat);
            if (imageType != jpg || StringUtils.containsIgnoreCase(System.getProperty("java.vendor"), "Oracle")) {
                return imageType;
            }

            // jpg is not supported by open jdk
            throw new IOException("Image format " + format + " not supported by current Java runtime; " +
                                  "consider switching to Oracle Java to process this type of image.");
        } catch (IllegalArgumentException e) {
            // invalid/supported image type
            throw new IOException("Image format " + format + " not supported.");
        }
    }

    /**
     * Converts the source image to different colour depth (32, 24, 16 & 8-bit) image using the given imageType
     *
     * @param src       the source image to convert
     * @param imageType the imageType depending on required color-bit
     * @return a copy of the source image with a different colour depth(32, 24, 16 & 8-bit), with the
     * custom colour palette
     */
    private static BufferedImage convertBits(BufferedImage src, int imageType) {
        return filter(src, new BufferedImage(src.getWidth(), src.getHeight(), imageType));
    }

    /**
     * Converts the source image to 2-bit and 4-bit colour using the given colour map (4 and 16 color palette resp).
     * No transparency.
     *
     * @param src the source image to convert
     * @return a copy of the source image with a 2-bit or 4-bit colour depth, with the
     * custom colour palette
     */
    private static BufferedImage convertBits(BufferedImage src, int[] cmap) {
        return filter(src, new BufferedImage(src.getWidth(), src.getHeight(), TYPE_BYTE_BINARY,
                                             new IndexColorModel(4, cmap.length, cmap, 0, false, OPAQUE, TYPE_BYTE)));
    }

    /**
     * Converts the source to 1-bit colour depth (monochrome). No transparency.
     *
     * @param src the source image to convert
     * @return a copy of the source image with a 1-bit colour depth.
     */
    private static BufferedImage convertTo1Bit(BufferedImage src) {
        return filter(src, new BufferedImage(src.getWidth(), src.getHeight(), TYPE_BYTE_BINARY,
                                             new IndexColorModel(1, 2,
                                                                 new byte[]{(byte) 0, (byte) 0xFF},
                                                                 new byte[]{(byte) 0, (byte) 0xFF},
                                                                 new byte[]{(byte) 0, (byte) 0xFF})));
    }

    private static BufferedImage filter(BufferedImage src, BufferedImage dest) {
        ColorConvertOp cco = new ColorConvertOp(src.getColorModel().getColorSpace(),
                                                dest.getColorModel().getColorSpace(),
                                                null);
        cco.filter(src, dest);
        return dest;
    }

    private static boolean isMatched1(BufferedImage image, int i, Color color) {
        boolean isMatched = true;
        for (int j = 0; j < image.getHeight(); j++) {
            if (image.getRGB(i, j) != color.getRGB()) {
                isMatched = false;
                break;
            }
        }
        return isMatched;
    }

    private static boolean isMatched2(BufferedImage image, int j, Color color) {
        boolean isMatched = true;
        for (int i = 0; i < image.getWidth(); i++) {
            if (image.getRGB(i, j) != color.getRGB()) {
                isMatched = false;
                break;
            }
        }
        return isMatched;
    }
}