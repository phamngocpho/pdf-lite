package org.pdflite.model;

import org.apache.pdfbox.text.TextPosition;

import java.util.List;

/**
 * Represents a region of text in a PDF page with its position and content.
 * <p>
 * This record encapsulates information about a text region including its
 * page location, bounding box coordinates, actual text content, and the
 * individual TextPosition objects that make up the text.
 * </p>
 * <p>
 * TextRegion is used for text selection, editing, and manipulation operations.
 * The coordinates are in PDF coordinate space (bottom-left origin).
 * </p>
 *
 * @param pageIndex the zero-based page index where this text region is located
 * @param x         the X coordinate of the region's left edge (PDF space)
 * @param y         the Y coordinate of the region's bottom edge (PDF space)
 * @param width     the width of the region in points
 * @param height    the height of the region in points
 * @param text      the actual text content of this region
 * @param positions the list of TextPosition objects that make up this region
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record TextRegion(
        int pageIndex,
        double x,
        double y,
        double width,
        double height,
        String text,
        List<TextPosition> positions
) {
    /**
     * Creates a TextRegion with validation.
     *
     * @throws IllegalArgumentException if pageIndex is negative, dimensions are non-positive,
     *                                  text is null, or positions is null
     */
    public TextRegion {
        if (pageIndex < 0) {
            throw new IllegalArgumentException("Page index must be non-negative");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }
        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }
        if (positions == null) {
            throw new IllegalArgumentException("Positions cannot be null");
        }
    }

    /**
     * Checks if this text region contains the given point (in PDF coordinates).
     *
     * @param pointX the X coordinate to check
     * @param pointY the Y coordinate to check
     * @return true if the point is within this region's bounds
     */
    public boolean contains(double pointX, double pointY) {
        return pointX >= x && pointX <= (x + width) &&
                pointY >= y && pointY <= (y + height);
    }

    /**
     * Gets the right edge X coordinate of this region.
     *
     * @return the X coordinate of the right edge
     */
    public double getMaxX() {
        return x + width;
    }

    /**
     * Gets the top edge Y coordinate of this region.
     *
     * @return the Y coordinate of the top edge
     */
    public double getMaxY() {
        return y + height;
    }

    /**
     * Checks if this region is empty (no text content).
     *
     * @return true if the text is empty or contains only whitespace
     */
    public boolean isEmpty() {
        return text.trim().isEmpty();
    }

    /**
     * Gets the number of characters in this region.
     *
     * @return the character count
     */
    public int length() {
        return text.length();
    }
}
