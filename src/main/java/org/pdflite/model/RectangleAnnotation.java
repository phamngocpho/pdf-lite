package org.pdflite.model;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class RectangleAnnotation extends ShapeAnnotation {
    public RectangleAnnotation(int pageNumber, double startX, double startY, double endX, double endY, Color color, double lineWidth) {
        super(pageNumber, startX, startY, endX, endY, color, lineWidth, "Rectangle");
    }

    @Override
    public void draw(GraphicsContext gc, double scale) {
        gc.setStroke(color);
        gc.setLineWidth(lineWidth * scale);

        // Tính toán toạ độ vẽ (xử lý cả trường hợp kéo ngược chiều)
        double drawX = Math.min(x, endX) * scale;
        double drawY = Math.min(y, endY) * scale;
        double w = Math.abs(endX - x) * scale;
        double h = Math.abs(endY - y) * scale;

        gc.strokeRect(drawX, drawY, w, h);
    }
}