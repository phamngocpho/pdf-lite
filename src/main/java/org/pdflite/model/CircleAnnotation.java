package org.pdflite.model;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class CircleAnnotation extends ShapeAnnotation {
    public CircleAnnotation(int pageNumber, double startX, double startY, double endX, double endY, Color color, double lineWidth) {
        super(pageNumber, startX, startY, endX, endY, color, lineWidth, "Circle");
    }

    public CircleAnnotation(int pageNumber, double startX, double startY, double endX, double endY,
                            Color color, double lineWidth, AnnotationLineStyle lineStyle, double opacity) {
        super(pageNumber, startX, startY, endX, endY, color, lineWidth, lineStyle, opacity, "Circle");
    }

    @Override
    public void draw(GraphicsContext gc, double scale) {
        prepareGraphicsContext(gc, scale);
        DrawingBounds bounds = calculateDrawingBounds(scale);
        gc.strokeOval(bounds.drawX(), bounds.drawY(), bounds.width(), bounds.height());
        resetGraphicsContext(gc);
    }
}
