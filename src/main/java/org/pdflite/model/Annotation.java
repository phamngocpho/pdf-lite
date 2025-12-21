package org.pdflite.model;

/**
 * Abstract base class for PDF annotations.
 * <p>
 * This class provides the common properties and behavior for all types of
 * annotations that can be added to PDF pages. Concrete annotation types
 * should extend this class and add their specific properties.
 * </p>
 * <p>
 * All annotations have:
 * <ul>
 *   <li>A page number indicating which page they belong to</li>
 *   <li>X and Y coordinates for their position on the page</li>
 *   <li>A type identifier string</li>
 * </ul>
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @see HighlightAnnotation
 * @since 1.0.0
 */
public abstract class Annotation {
    /**
     * The page number this annotation belongs to (zero-based).
     */
    protected int pageNumber;

    /**
     * The X coordinate of the annotation on the page.
     */
    protected double x;

    /**
     * The Y coordinate of the annotation on the page.
     */
    protected double y;

    /**
     * The type identifier for this annotation.
     */
    protected String type;

    /**
     * Creates a new annotation with the specified properties.
     *
     * @param pageNumber the zero-based page number
     * @param x          the X coordinate on the page
     * @param y          the Y coordinate on the page
     * @param type       the type identifier for this annotation
     */
    public Annotation(int pageNumber, double x, double y, String type) {
        this.pageNumber = pageNumber;
        this.x = x;
        this.y = y;
        this.type = type;
    }

    /**
     * Gets the page number this annotation belongs to.
     *
     * @return the zero-based page number
     */
    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    /**
     * Gets the X coordinate of this annotation.
     *
     * @return the X coordinate
     */
    public double getX() {
        return x;
    }

    /**
     * Gets the Y coordinate of this annotation.
     *
     * @return the Y coordinate
     */
    public double getY() {
        return y;
    }

    /**
     * Gets the type identifier of this annotation.
     *
     * @return the type string
     */
    public String getType() {
        return type;
    }
}
