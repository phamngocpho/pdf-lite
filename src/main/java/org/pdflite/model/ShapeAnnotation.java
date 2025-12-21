package org.pdflite.model;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public abstract class ShapeAnnotation extends Annotation {
    // startX, startY sẽ được lưu trong x, y của lớp cha Annotation
    protected double endX, endY;     // Tọa độ kết thúc
    protected Color color;           // Màu sắc đường viền
    protected double lineWidth;      // Độ dày đường viền

    // Constructor
    public ShapeAnnotation(int pageNumber, double startX, double startY, double endX, double endY, Color color, double lineWidth, String type) {
        super(pageNumber, startX, startY, type); // Gọi constructor của lớp cha
        this.endX = endX;
        this.endY = endY;
        this.color = color;
        this.lineWidth = lineWidth;
    }

    // Getters
    // getX(), getY(), getPageNumber(), getType() đã có từ lớp cha Annotation
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

    // Setters (nếu cần chỉnh sửa sau khi vẽ)
    // setX(), setY(), setPageNumber() nếu muốn thay đổi thông qua lớp cha
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

    /**
     * Prepares the graphics context for drawing by setting stroke color and line width.
     *
     * @param gc    the graphics context to prepare
     * @param scale the scale factor to apply to the line width
     */
    protected void prepareGraphicsContext(GraphicsContext gc, double scale) {
        gc.setStroke(color);
        gc.setLineWidth(lineWidth * scale);
    }

    /**
     * Calculates the drawing bounds for rectangular shapes (handles reverse drag cases).
     * <p>
     * This method calculates the top-left corner position and dimensions
     * for shapes that need to be drawn as rectangles, ensuring correct rendering
     * regardless of drag direction.
     * </p>
     *
     * @param scale the scale factor to apply to coordinates
     * @return a DrawingBounds object containing drawX, drawY, width, and height
     */
    protected DrawingBounds calculateDrawingBounds(double scale) {
        double drawX = Math.min(x, endX) * scale;
        double drawY = Math.min(y, endY) * scale;
        double w = Math.abs(endX - x) * scale;
        double h = Math.abs(endY - y) * scale;
        return new DrawingBounds(drawX, drawY, w, h);
    }

    /**
     * Helper class to hold drawing bounds information.
     */
    protected record DrawingBounds(double drawX, double drawY, double width, double height) {
    }

    // Phương thức abstract để AnnotationLayer biết cách vẽ
    public abstract void draw(javafx.scene.canvas.GraphicsContext gc, double scale);
}