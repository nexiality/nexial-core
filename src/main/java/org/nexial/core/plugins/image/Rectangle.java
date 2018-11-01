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
