package org.pdflite.model;

import java.io.File;

/**
 * Record representing an image placement in a PDF document.
 * <p>
 * This record stores information about where an image should be placed
 * on a PDF page, including its position, size, and source file.
 * </p>
 *
 * @param pageIndex the zero-based page index where the image is placed
 * @param x         the X coordinate in PDF space (points)
 * @param y         the Y coordinate in PDF space (points)
 * @param width     the width of the image in points
 * @param height    the height of the image in points
 * @param imageFile the source image file
 * @param isStamp   whether this is a rubber stamp annotation
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record ImagePlacement(
        int pageIndex,
        double x,
        double y,
        double width,
        double height,
        File imageFile,
        boolean isStamp
) {
    /**
     * Creates a new ImagePlacement with validation.
     *
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public ImagePlacement {
        if (pageIndex < 0) {
            throw new IllegalArgumentException("Page index must be non-negative");
        }
        if (width <= 0) {
            throw new IllegalArgumentException("Width must be positive");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("Height must be positive");
        }
        if (imageFile == null) {
            throw new IllegalArgumentException("Image file cannot be null");
        }
        if (!imageFile.exists()) {
            throw new IllegalArgumentException("Image file does not exist: " + imageFile);
        }
    }

    /**
     * Creates a new ImagePlacement for a regular image (not a stamp).
     *
     * @param pageIndex the page index
     * @param x         the X coordinate
     * @param y         the Y coordinate
     * @param width     the width
     * @param height    the height
     * @param imageFile the image file
     * @return a new ImagePlacement
     */
    public static ImagePlacement forImage(int pageIndex, double x, double y,
                                          double width, double height, File imageFile) {
        return new ImagePlacement(pageIndex, x, y, width, height, imageFile, false);
    }

    /**
     * Creates a new ImagePlacement for a rubber stamp annotation.
     *
     * @param pageIndex the page index
     * @param x         the X coordinate
     * @param y         the Y coordinate
     * @param width     the width
     * @param height    the height
     * @param imageFile the image file
     * @return a new ImagePlacement
     */
    public static ImagePlacement forStamp(int pageIndex, double x, double y,
                                          double width, double height, File imageFile) {
        return new ImagePlacement(pageIndex, x, y, width, height, imageFile, true);
    }

    /**
     * Returns the area of this image placement.
     *
     * @return the area in square points
     */
    public double area() {
        return width * height;
    }

    /**
     * Checks if this placement overlaps with another placement.
     *
     * @param other the other placement
     * @return true if they overlap, false otherwise
     */
    public boolean overlaps(ImagePlacement other) {
        if (this.pageIndex != other.pageIndex) {
            return false;
        }

        return !(this.x + this.width < other.x ||
                other.x + other.width < this.x ||
                this.y + this.height < other.y ||
                other.y + other.height < this.y);
    }
}
