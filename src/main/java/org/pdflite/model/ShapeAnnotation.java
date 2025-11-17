// File: src/main/java/org/pdflite/model/ShapeAnnotation.java
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
    public double getEndX() { return endX; }
    public double getEndY() { return endY; }
    public Color getColor() { return color; }
    public double getLineWidth() { return lineWidth; }

    // Setters (nếu cần chỉnh sửa sau khi vẽ)
    // setX(), setY(), setPageNumber() nếu muốn thay đổi thông qua lớp cha
    public void setEndX(double endX) { this.endX = endX; }
    public void setEndY(double endY) { this.endY = endY; }
    public void setColor(Color color) { this.color = color; }
    public void setLineWidth(double lineWidth) { this.lineWidth = lineWidth; }

    // Phương thức abstract để AnnotationLayer biết cách vẽ
    public abstract void draw(javafx.scene.canvas.GraphicsContext gc, double scale);
}