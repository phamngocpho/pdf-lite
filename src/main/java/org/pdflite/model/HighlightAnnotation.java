package org.pdflite.model;

import javafx.scene.paint.Color;

/**
 * Annotation for highlighting text
 */
public class HighlightAnnotation extends Annotation {
    private final double width;
    private final double height;
    private final Color color;

    public HighlightAnnotation(int pageNumber, double x, double y, double width, double height, Color color) {
        super(pageNumber, x, y, "HIGHLIGHT");
        this.width = width;
        this.height = height;
        this.color = color;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public Color getColor() {
        return color;
    }
}
