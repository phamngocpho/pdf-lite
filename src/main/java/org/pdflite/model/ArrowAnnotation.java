// src/main/java/org/pdflite/model/ArrowAnnotation.java

package org.pdflite.model;

/**
 * Đại diện cho Annotation mũi tên (đường thẳng).
 */
public class ArrowAnnotation extends ShapeAnnotation {

    private double startX;
    private double startY;
    private double endX;
    private double endY;

    public ArrowAnnotation(int pageNumber, double startX, double startY, double endX, double endY, String color, double lineWidth) {
        // Gọi lên constructor của ShapeAnnotation (đã sửa)
        super(pageNumber, "ARROW", color, lineWidth);
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
    }

    // Getters và Setters
    public double getStartX() { return startX; }
    public void setStartX(double startX) { this.startX = startX; }

    public double getStartY() { return startY; }
    public void setStartY(double startY) { this.startY = startY; }

    public double getEndX() { return endX; }
    public void setEndX(double endX) { this.endX = endX; }

    // === HÀM BỊ LỖI NẰM Ở ĐÂY ===
    public double getEndY() { return endY; }
    public void setEndY(double endY) { this.endY = endY; }
    // ============================
}