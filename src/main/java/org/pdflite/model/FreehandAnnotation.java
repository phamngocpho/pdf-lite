package org.pdflite.model;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a freehand drawing annotation on a PDF page.
 * <p>
 * This annotation stores a list of points that form a continuous path,
 * allowing users to draw freehand lines/curves on the PDF.
 * </p>
 */
public class FreehandAnnotation extends Annotation {

    public static final String TYPE = "Freehand";

    private final List<Double> pointsX;
    private final List<Double> pointsY;
    private Color color;
    private double lineWidth;
    private AnnotationLineStyle lineStyle;
    private double opacity;

    public FreehandAnnotation(int pageNumber, List<Double> pointsX, List<Double> pointsY, Color color, double lineWidth) {
        this(pageNumber, pointsX, pointsY, color, lineWidth, AnnotationLineStyle.SOLID, 1.0);
    }

    public FreehandAnnotation(int pageNumber, List<Double> pointsX, List<Double> pointsY, Color color,
                              double lineWidth, AnnotationLineStyle lineStyle, double opacity) {
        super(pageNumber,
              pointsX.isEmpty() ? 0 : pointsX.get(0),
              pointsY.isEmpty() ? 0 : pointsY.get(0),
              TYPE);
        this.pointsX = new ArrayList<>(pointsX);
        this.pointsY = new ArrayList<>(pointsY);
        this.color = color;
        this.lineWidth = lineWidth;
        this.lineStyle = lineStyle == null ? AnnotationLineStyle.SOLID : lineStyle;
        this.opacity = clampOpacity(opacity);
    }

    public List<Double> getPointsX() {
        return new ArrayList<>(pointsX);
    }

    public List<Double> getPointsY() {
        return new ArrayList<>(pointsY);
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public double getLineWidth() {
        return lineWidth;
    }

    public void setLineWidth(double lineWidth) {
        this.lineWidth = lineWidth;
    }

    public AnnotationLineStyle getLineStyle() {
        return lineStyle;
    }

    public void setLineStyle(AnnotationLineStyle lineStyle) {
        this.lineStyle = lineStyle == null ? AnnotationLineStyle.SOLID : lineStyle;
    }

    public double getOpacity() {
        return opacity;
    }

    public void setOpacity(double opacity) {
        this.opacity = clampOpacity(opacity);
    }

    public int getPointCount() {
        return Math.min(pointsX.size(), pointsY.size());
    }

    /**
     * Draws the freehand path on the given graphics context.
     *
     * @param gc    the graphics context to draw on
     * @param scale the scale factor to apply to coordinates
     */
    public void draw(GraphicsContext gc, double scale) {
        if (pointsX.isEmpty() || pointsY.isEmpty()) {
            return;
        }

        int pointCount = getPointCount();
        if (pointCount < 2) {
            return;
        }

        gc.setStroke(Color.color(color.getRed(), color.getGreen(), color.getBlue(), opacity));
        gc.setLineWidth(lineWidth * scale);
        gc.setLineDashes(lineStyle.getDashPattern(lineWidth, scale));
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        gc.beginPath();

        // Move to the first point
        gc.moveTo(pointsX.get(0) * scale, pointsY.get(0) * scale);

        // Draw lines through all points
        for (int i = 1; i < pointCount; i++) {
            gc.lineTo(pointsX.get(i) * scale, pointsY.get(i) * scale);
        }

        gc.stroke();
        gc.setLineDashes(0);
    }

    private double clampOpacity(double opacity) {
        return Math.max(0.0, Math.min(1.0, opacity));
    }
}
