// src/main/java/org/pdflite/model/ShapeAnnotation.java
package org.pdflite.model;

public abstract class ShapeAnnotation extends Annotation {
    protected String color;
    protected double lineWidth;

    /**
     * Constructor cơ sở.
     * @param pageNumber Số trang (0-based)
     * @param type       Loại annotation (ví dụ: "RECTANGLE")
     * @param color      Màu sắc dạng chuỗi web (ví dụ: "#FF0000")
     * @param lineWidth  Độ rộng nét vẽ
     */
    public ShapeAnnotation(int pageNumber, String type, String color, double lineWidth) {
        // Gửi pageNumber và type lên lớp cha
        super(pageNumber, 0, 0, type);
        this.color = color;
        this.lineWidth = lineWidth;
    }

    // Getters và Setters
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public double getLineWidth() { return lineWidth; }
    public void setLineWidth(double lineWidth) { this.lineWidth = lineWidth; }
}