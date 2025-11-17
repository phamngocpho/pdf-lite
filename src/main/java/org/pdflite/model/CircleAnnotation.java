package org.pdflite.model;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class CircleAnnotation extends ShapeAnnotation {
    public CircleAnnotation(int pageNumber, double startX, double startY, double endX, double endY, Color color, double lineWidth) {
        super(pageNumber, startX, startY, endX, endY, color, lineWidth, "Circle");
    }

    @Override
    public void draw(GraphicsContext gc, double scale) {
        gc.setStroke(color);
        gc.setLineWidth(lineWidth * scale);

        double drawX = Math.min(x, endX) * scale;
        double drawY = Math.min(y, endY) * scale;
        double w = Math.abs(endX - x) * scale;
        double h = Math.abs(endY - y) * scale;

        gc.strokeOval(drawX, drawY, w, h);
    }
}