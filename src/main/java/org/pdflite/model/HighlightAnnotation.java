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
     * Groups multiple highlight rectangles created in a single user action
     * (e.g., highlighting multiple lines of text).
     */
    private final String batchId;

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
    private final double opacity;

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
        this(pageNumber, x, y, width, height, color, null);
    }

    /**
     * Creates a new highlight annotation with an optional batch id.
     *
     * @param batchId identifier shared by highlight segments created in one action; may be null
     */
    public HighlightAnnotation(int pageNumber, double x, double y, double width, double height, Color color, String batchId) {
        this(pageNumber, x, y, width, height, color, 0.4, batchId);
    }

    public HighlightAnnotation(int pageNumber, double x, double y, double width, double height,
                               Color color, double opacity) {
        this(pageNumber, x, y, width, height, color, opacity, null);
    }

    public HighlightAnnotation(int pageNumber, double x, double y, double width, double height,
                               Color color, double opacity, String batchId) {
        super(pageNumber, x, y, "HIGHLIGHT");
        this.width = width;
        this.height = height;
        this.color = color;
        this.opacity = clampOpacity(opacity);
        this.batchId = batchId;
    }

    public String getBatchId() {
        return batchId;
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

    public double getOpacity() {
        return opacity;
    }

    private double clampOpacity(double opacity) {
        return Math.max(0.0, Math.min(1.0, opacity));
    }
}
