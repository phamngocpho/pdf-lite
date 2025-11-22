package org.pdflite.model;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class ArrowAnnotation extends ShapeAnnotation {
    public static final String TYPE = "Arrow";
    private static final double ARROW_HEAD_SIZE = 10; // Kích thước đầu mũi tên mặc định

    public ArrowAnnotation(int pageNumber, double startX, double startY, double endX, double endY, Color color, double lineWidth) {
        super(pageNumber, startX, startY, endX, endY, color, lineWidth, TYPE);
    }

    @Override
    public void draw(GraphicsContext gc, double scale) {
        gc.setStroke(color);
        gc.setLineWidth(lineWidth * scale);

        // x và y của lớp cha là startX, startY.
        // Vẽ đường thẳng
        gc.strokeLine(x * scale, y * scale, endX * scale, endY * scale);

        // Vẽ đầu mũi tên
        double angle = Math.atan2((endY - y), (endX - x));
        double arrowHeadX1 = endX * scale - ARROW_HEAD_SIZE * Math.cos(angle - Math.PI / 6);
        double arrowHeadY1 = endY * scale - ARROW_HEAD_SIZE * Math.sin(angle - Math.PI / 6);
        double arrowHeadX2 = endX * scale - ARROW_HEAD_SIZE * Math.cos(angle + Math.PI / 6);
        double arrowHeadY2 = endY * scale - ARROW_HEAD_SIZE * Math.sin(angle + Math.PI / 6);

        gc.strokeLine(endX * scale, endY * scale, arrowHeadX1, arrowHeadY1);
        gc.strokeLine(endX * scale, endY * scale, arrowHeadX2, arrowHeadY2);
    }
}