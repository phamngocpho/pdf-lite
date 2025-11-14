// src/main/java/org/pdflite/manager/DrawingManager.java
package org.pdflite.manager;

import javafx.scene.paint.Color;
import org.pdflite.model.DrawingTool;

/**
 * Quản lý trạng thái công cụ vẽ hiện tại (công cụ, màu sắc, độ rộng nét).
 */
public class DrawingManager {

    private DrawingTool currentTool = DrawingTool.NONE;

    // Mặc định là MÀU ĐEN (Color.BLACK) theo yêu cầu.
    private Color currentColor = Color.BLACK;

    private double currentLineWidth = 1.0;

    // Getters và Setters
    public DrawingTool getCurrentTool() {
        return currentTool;
    }

    public void setCurrentTool(DrawingTool currentTool) {
        this.currentTool = currentTool;
    }

    public Color getCurrentColor() {
        return currentColor;
    }

    public void setCurrentColor(Color currentColor) {
        this.currentColor = currentColor;
    }

    public double getCurrentLineWidth() {
        return currentLineWidth;
    }

    public void setCurrentLineWidth(double currentLineWidth) {
        this.currentLineWidth = currentLineWidth;
    }

    /**
     * Chuyển đổi Color của JavaFX sang chuỗi web (String "#RRGGBB")
     * để lưu vào các Annotation Model (cho mục đích lưu trữ/export).
     */
    public String getCurrentColorAsWebString() {
        return String.format("#%02X%02X%02X",
                (int) (currentColor.getRed() * 255),
                (int) (currentColor.getGreen() * 255),
                (int) (currentColor.getBlue() * 255));
    }
}