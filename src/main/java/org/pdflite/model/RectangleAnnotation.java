package org.pdflite.model;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class RectangleAnnotation extends ShapeAnnotation {
    public RectangleAnnotation(int pageNumber, double startX, double startY, double endX, double endY, Color color, double lineWidth) {
        super(pageNumber, startX, startY, endX, endY, color, lineWidth, "Rectangle");
    }

    public RectangleAnnotation(int pageNumber, double startX, double startY, double endX, double endY,
                               Color color, double lineWidth, AnnotationLineStyle lineStyle, double opacity) {
        super(pageNumber, startX, startY, endX, endY, color, lineWidth, lineStyle, opacity, "Rectangle");
    }

    @Override
    public void draw(GraphicsContext gc, double scale) {
        prepareGraphicsContext(gc, scale);
        DrawingBounds bounds = calculateDrawingBounds(scale);
        gc.strokeRect(bounds.drawX(), bounds.drawY(), bounds.width(), bounds.height());
        resetGraphicsContext(gc);
    }
}
