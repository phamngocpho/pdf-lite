package org.pdflite.model;

import java.io.File;

/**
 * Configuration model for watermark settings.
 * Supports both text and image watermarks with various positioning and styling options.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class WatermarkConfig {

    public enum WatermarkType {
        TEXT, IMAGE
    }

    public enum Position {
        CENTER("Center"),
        TOP_LEFT("Top Left"),
        TOP_CENTER("Top Center"),
        TOP_RIGHT("Top Right"),
        MIDDLE_LEFT("Middle Left"),
        MIDDLE_RIGHT("Middle Right"),
        BOTTOM_LEFT("Bottom Left"),
        BOTTOM_CENTER("Bottom Center"),
        BOTTOM_RIGHT("Bottom Right"),
        CUSTOM("Custom");

        private final String displayName;

        Position(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private WatermarkType type;
    private String text;
    private File imageFile;
    private Position position;
    private float customX;
    private float customY;
    private float opacity;
    private float rotation;
    private int fontSize;
    private String fontName;
    private java.awt.Color color;
    private float scale;
    private boolean applyToAllPages;
    private String pageRange;

    public WatermarkConfig() {
        // Default values
        this.type = WatermarkType.TEXT;
        this.text = "CONFIDENTIAL";
        this.position = Position.CENTER;
        this.opacity = 0.3f;
        this.rotation = 45f;
        this.fontSize = 72;
        this.fontName = "Helvetica";
        this.color = java.awt.Color.GRAY;
        this.scale = 1.0f;
        this.applyToAllPages = true;
        this.pageRange = "";
        this.customX = 0;
        this.customY = 0;
    }

    // Getters and Setters

    public WatermarkType getType() {
        return type;
    }

    public void setType(WatermarkType type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public File getImageFile() {
        return imageFile;
    }

    public void setImageFile(File imageFile) {
        this.imageFile = imageFile;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public float getCustomX() {
        return customX;
    }

    public void setCustomX(float customX) {
        this.customX = customX;
    }

    public float getCustomY() {
        return customY;
    }

    public void setCustomY(float customY) {
        this.customY = customY;
    }

    public float getOpacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = Math.max(0f, Math.min(1f, opacity));
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    public String getFontName() {
        return fontName;
    }

    public void setFontName(String fontName) {
        this.fontName = fontName;
    }

    public java.awt.Color getColor() {
        return color;
    }

    public void setColor(java.awt.Color color) {
        this.color = color;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public boolean isApplyToAllPages() {
        return applyToAllPages;
    }

    public void setApplyToAllPages(boolean applyToAllPages) {
        this.applyToAllPages = applyToAllPages;
    }

    public String getPageRange() {
        return pageRange;
    }

    public void setPageRange(String pageRange) {
        this.pageRange = pageRange;
    }
}
