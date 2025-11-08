package org.pdflite.model;

/**
 * Base class for PDF annotations
 */
public abstract class Annotation {
    protected int pageNumber;
    protected double x;
    protected double y;
    protected String type;

    public Annotation(int pageNumber, double x, double y, String type) {
        this.pageNumber = pageNumber;
        this.x = x;
        this.y = y;
        this.type = type;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public String getType() {
        return type;
    }
}
