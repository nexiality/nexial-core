package org.nexial.core.plugins.image;

import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import static org.nexial.core.plugins.image.BitDepthConversion.changeColorBit;
import static org.nexial.core.plugins.image.ImageCommand.IMAGE_PERCENT_FORMAT;

public class ImageComparison {

    private static final int DEF_COLOR_BIT = 32;

    private final BufferedImage image1;
    private final BufferedImage image2;
    private ImageDifferenceTools tools;

    private int matchCount = 0;
    private float matchPercent;

    public ImageComparison(File image1, File image2) throws IOException {
        this.image1 = ImageIO.read(image1);
        this.image2 = ImageIO.read(image2);
        this.tools = new ImageDifferenceTools();

        initDiffMatrix();
    }

    public float getMatchPercent() { return matchPercent; }

    /**
     * Draw rectangles which cover the regions of the difference pixels.
     *
     * @param color the color for different pixels.
     * @return the result image of the drawing.
     */
    public BufferedImage compareImages(Color color) {
        // checkCorrectImageSize(image1, image2);
        BufferedImage outputImg = deepCopy(image2);

        Graphics2D graphics = outputImg.createGraphics();
        graphics.setColor(color);

        tools.groupRegions();
        tools.drawRectangles(graphics);

        return outputImg;
    }

    public static boolean isMatched(int rgb1, int rgb2) {
        int a1 = (rgb1 >> 24) & 0xff;
        int red1 = (rgb1 >> 16) & 0xff;
        int green1 = (rgb1 >> 8) & 0xff;
        int blue1 = (rgb1) & 0xff;
        int a2 = (rgb2 >> 24) & 0xff;
        int red2 = (rgb2 >> 16) & 0xff;
        int green2 = (rgb2 >> 8) & 0xff;
        int blue2 = (rgb2) & 0xff;

        // convert RGB to grayscale
        int avg1 = (red1 + green1 + blue1) / 3;
        int avg2 = (red2 + green2 + blue2) / 3;

        int p1 = (a1 << 24) | (avg1 << 16) | (avg1 << 8) | avg1;
        int p2 = (a2 << 24) | (avg2 << 16) | (avg2 << 8) | avg1;
        return p1 == p2;
    }

    /**
     * Make a copy of the {@code BufferedImage} object.
     *
     * @param image the provided image.
     * @return copy of the provided image.
     */
    public static BufferedImage deepCopy(BufferedImage image) {
        ColorModel cm = image.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = image.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }

    private void initDiffMatrix() {
        int colorBit1 = image1.getColorModel().getPixelSize();
        int colorBit2 = image2.getColorModel().getPixelSize();

        int colorBit = colorBit1 > colorBit2 ? colorBit2 : colorBit1;
        colorBit = colorBit > DEF_COLOR_BIT ? DEF_COLOR_BIT : colorBit;

        BufferedImage convertedImage1 = changeColorBit(image1, colorBit);
        BufferedImage convertedImage2 = changeColorBit(image2, colorBit);

        int[][] matrix = populateMatrixOfDifferences(convertedImage1, convertedImage2);
        tools.setMatrix(matrix);

        float dimensionBase = image1.getWidth() * image1.getHeight();
        //Percentage matching calculation
        matchPercent = Float.parseFloat(IMAGE_PERCENT_FORMAT.format(matchCount / dimensionBase * 100));
    }

    /**
     * Populate binary matrix by "0" and "1". If the pixels are difference set it as "1", otherwise "0".
     *
     * @param image1 {@code BufferedImage} object of the first image.
     * @param image2 {@code BufferedImage} object of the second image.
     * @return matrix of different pixels.
     */
    private int[][] populateMatrixOfDifferences(BufferedImage image1, BufferedImage image2) {
        int[][] matrix = new int[image1.getWidth()][image1.getHeight()];
        for (int y = 0; y < image1.getHeight(); y++) {
            for (int x = 0; x < image1.getWidth(); x++) {
                try {
                    if (isMatched(image1.getRGB(x, y), image2.getRGB(x, y))) {
                        matrix[x][y] = 0;
                        matchCount++;
                    } else {
                        matrix[x][y] = 1;
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }
        return matrix;
    }

    /**
     * Checks images for equals their widths and heights.
     *
     * @param image1 {@code BufferedImage} object of the first image.
     * @param image2 {@code BufferedImage} object of the second image.
     */
    private static void checkCorrectImageSize(BufferedImage image1, BufferedImage image2) {
        if (image1.getHeight() != image2.getHeight() || image1.getWidth() != image2.getWidth()) {
            // throw new IllegalArgumentException("Images dimensions mismatch");
            System.out.println("Images dimensions mismatch");
        }
    }
}
