package org.pdflite.model;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public abstract class ShapeAnnotation extends Annotation {
    protected double endX;
    protected double endY;
    protected Color color;
    protected double lineWidth;
    protected AnnotationLineStyle lineStyle;
    protected double opacity;

    public ShapeAnnotation(int pageNumber, double startX, double startY, double endX, double endY,
                           Color color, double lineWidth, String type) {
        this(pageNumber, startX, startY, endX, endY, color, lineWidth, AnnotationLineStyle.SOLID, 1.0, type);
    }

    public ShapeAnnotation(int pageNumber, double startX, double startY, double endX, double endY,
                           Color color, double lineWidth, AnnotationLineStyle lineStyle, double opacity,
                           String type) {
        super(pageNumber, startX, startY, type);
        this.endX = endX;
        this.endY = endY;
        this.color = color;
        this.lineWidth = lineWidth;
        this.lineStyle = lineStyle == null ? AnnotationLineStyle.SOLID : lineStyle;
        this.opacity = clampOpacity(opacity);
    }

    public double getEndX() {
        return endX;
    }

    public double getEndY() {
        return endY;
    }

    public Color getColor() {
        return color;
    }

    public double getLineWidth() {
        return lineWidth;
    }

    public AnnotationLineStyle getLineStyle() {
        return lineStyle;
    }

    public double getOpacity() {
        return opacity;
    }

    public void setEndX(double endX) {
        this.endX = endX;
    }

    public void setEndY(double endY) {
        this.endY = endY;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public void setLineWidth(double lineWidth) {
        this.lineWidth = lineWidth;
    }

    public void setLineStyle(AnnotationLineStyle lineStyle) {
        this.lineStyle = lineStyle == null ? AnnotationLineStyle.SOLID : lineStyle;
    }

    public void setOpacity(double opacity) {
        this.opacity = clampOpacity(opacity);
    }

    protected void prepareGraphicsContext(GraphicsContext gc, double scale) {
        gc.setStroke(Color.color(color.getRed(), color.getGreen(), color.getBlue(), opacity));
        gc.setLineWidth(lineWidth * scale);
        gc.setLineDashes(lineStyle.getDashPattern(lineWidth, scale));
    }

    protected void resetGraphicsContext(GraphicsContext gc) {
        gc.setLineDashes(0);
    }

    protected DrawingBounds calculateDrawingBounds(double scale) {
        double drawX = Math.min(x, endX) * scale;
        double drawY = Math.min(y, endY) * scale;
        double w = Math.abs(endX - x) * scale;
        double h = Math.abs(endY - y) * scale;
        return new DrawingBounds(drawX, drawY, w, h);
    }

    private double clampOpacity(double opacity) {
        return Math.max(0.0, Math.min(1.0, opacity));
    }

    protected record DrawingBounds(double drawX, double drawY, double width, double height) {
    }

    public abstract void draw(GraphicsContext gc, double scale);
}
