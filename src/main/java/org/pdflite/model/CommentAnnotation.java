package org.pdflite.model;

import javafx.scene.paint.Color;

/**
 * Annotation for adding text comments to PDF pages.
 * <p>
 * This class represents a comment annotation that displays a text note
 * at a specific position on a PDF page. Comments are shown as icons
 * that can be clicked to view the full text.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @see Annotation
 * @since 1.0.0
 */
public class CommentAnnotation extends Annotation {
    /**
     * The comment text content.
     */
    private final String comment;

    /**
     * The color of the comment icon.
     */
    private final Color color;

    /**
     * Creates a new comment annotation with the specified properties.
     *
     * @param pageNumber the zero-based page number this comment belongs to
     * @param x          the X coordinate of the comment icon
     * @param y          the Y coordinate of the comment icon
     * @param comment    the text content of the comment
     * @param color      the color of the comment icon
     */
    public CommentAnnotation(int pageNumber, double x, double y, String comment, Color color) {
        super(pageNumber, x, y, "COMMENT");
        this.comment = comment;
        this.color = color;
    }

    /**
     * Gets the comment text.
     *
     * @return the comment text
     */
    public String getComment() {
        return comment;
    }

    /**
     * Gets the color of the comment icon.
     *
     * @return the JavaFX Color object
     */
    public Color getColor() {
        return color;
    }
}
