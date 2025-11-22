package org.pdflite.model;

import javafx.scene.paint.Color;

/**
 * Annotation for highlighting rectangular regions of text or content.
 * <p>
 * This class represents a highlight annotation that covers a rectangular area
 * on a PDF page. Highlights have a position (x, y), dimensions (width, height),
 * and a color that determines their appearance.
 * </p>
 * <p>
 * Highlight annotations are typically semi-transparent so that the underlying
 * content remains visible.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @see Annotation
 * @since 1.0.0
 */
public class HighlightAnnotation extends Annotation {
    /**
     * The width of the highlight rectangle.
     */
    private final double width;

    /**
     * The height of the highlight rectangle.
     */
    private final double height;

    /**
     * The color of the highlight.
     */
    private final Color color;

    /**
     * Creates a new highlight annotation with the specified properties.
     * <p>
     * The type is automatically set to "HIGHLIGHT".
     * </p>
     *
     * @param pageNumber the zero-based page number this highlight belongs to
     * @param x          the X coordinate of the top-left corner
     * @param y          the Y coordinate of the top-left corner
     * @param width      the width of the highlight rectangle
     * @param height     the height of the highlight rectangle
     * @param color      the color to use for the highlight
     */
    public HighlightAnnotation(int pageNumber, double x, double y, double width, double height, Color color) {
        super(pageNumber, x, y, "HIGHLIGHT");
        this.width = width;
        this.height = height;
        this.color = color;
    }

    /**
     * Gets the width of the highlight rectangle.
     *
     * @return the width in points
     */
    public double getWidth() {
        return width;
    }

    /**
     * Gets the height of the highlight rectangle.
     *
     * @return the height in points
     */
    public double getHeight() {
        return height;
    }

    /**
     * Gets the color of the highlight.
     *
     * @return the JavaFX Color object
     */
    public Color getColor() {
        return color;
    }
}
