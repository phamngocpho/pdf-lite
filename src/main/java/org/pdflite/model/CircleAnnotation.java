// src/main/java/org/pdflite/model/CircleAnnotation.java
package org.pdflite.model;

public class CircleAnnotation extends ShapeAnnotation {
    private double centerX, centerY, radius;
    public CircleAnnotation(int pageNumber, double centerX, double centerY, double radius, String color, double lineWidth) {
        super(pageNumber, "CIRCLE", color, lineWidth);
        this.centerX = centerX; this.centerY = centerY; this.radius = radius;
    }
    // Getters/Setters
    public double getCenterX() { return centerX; }
    public void setCenterX(double centerX) { this.centerX = centerX; }
    public double getCenterY() { return centerY; }
    public void setCenterY(double centerY) { this.centerY = centerY; }
    public double getRadius() { return radius; }
    public void setRadius(double radius) { this.radius = radius; }
}