package org.pdflite.util;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

/**
 * Utility class for coordinate system conversion between PDF and JavaFX.
 * <p>
 * PDF coordinate system uses bottom-left origin (0,0) with Y-axis pointing upward.
 * JavaFX coordinate system uses top-left origin (0,0) with Y-axis pointing downward.
 * This class provides methods to convert coordinates between these two systems.
 * </p>
 * <p>
 * Conversion formula:
 * <ul>
 *   <li>PDF to JavaFX: javafxY = pageHeight - pdfY</li>
 *   <li>JavaFX to PDF: pdfY = pageHeight - javafxY</li>
 * </ul>
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class CoordinateUtil {

    /**
     * Tolerance for floating-point comparisons (0.001 points).
     */
    public static final double TOLERANCE = 0.001;

    /**
     * Private constructor to prevent instantiation.
     */
    private CoordinateUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Converts a JavaFX Y coordinate to PDF Y coordinate.
     * <p>
     * PDF uses bottom-left origin, so we need to flip the Y-axis.
     * </p>
     *
     * @param javafxY    the Y coordinate in JavaFX space
     * @param pageHeight the height of the page in points
     * @return the Y coordinate in PDF space
     */
    public static double javafxToPdfY(double javafxY, double pageHeight) {
        return pageHeight - javafxY;
    }

    /**
     * Converts a PDF Y coordinate to JavaFX Y coordinate.
     * <p>
     * JavaFX uses top-left origin, so we need to flip the Y-axis.
     * </p>
     *
     * @param pdfY       the Y coordinate in PDF space
     * @param pageHeight the height of the page in points
     * @return the Y coordinate in JavaFX space
     */
    public static double pdfToJavafxY(double pdfY, double pageHeight) {
        return pageHeight - pdfY;
    }

    /**
     * Converts a point between coordinate systems with optional scaling.
     * <p>
     * The X coordinate is scaled but not flipped.
     * The Y coordinate is both scaled and flipped.
     * </p>
     *
     * @param point      the point to convert
     * @param pageHeight the height of the page in points (unscaled)
     * @param scale      the scaling factor to apply
     * @param toPdf      true to convert from JavaFX to PDF, false for PDF to JavaFX
     * @return the converted point
     * @throws IllegalArgumentException if point is null, pageHeight is non-positive, or scale is non-positive
     */
    public static Point2D convertPoint(Point2D point, double pageHeight, double scale, boolean toPdf) {
        if (point == null) {
            throw new IllegalArgumentException("Point cannot be null");
        }
        if (pageHeight <= 0) {
            throw new IllegalArgumentException("Page height must be positive");
        }
        if (scale <= 0) {
            throw new IllegalArgumentException("Scale must be positive");
        }

        double x = point.getX();
        double y = point.getY();

        if (toPdf) {
            // JavaFX to PDF: divide by scale, then flip Y
            x = x / scale;
            y = javafxToPdfY(y / scale, pageHeight);
        } else {
            // PDF to JavaFX: flip Y, then multiply by scale
            x = x * scale;
            y = pdfToJavafxY(y, pageHeight) * scale;
        }

        return new Point2D(x, y);
    }

    /**
     * Converts a rectangle between coordinate systems with optional scaling.
     * <p>
     * The rectangle's position and size are converted according to the coordinate system.
     * Note that in PDF, the Y coordinate represents the bottom-left corner,
     * while in JavaFX it represents the top-left corner.
     * </p>
     *
     * @param rect       the rectangle to convert
     * @param pageHeight the height of the page in points (unscaled)
     * @param scale      the scaling factor to apply
     * @param toPdf      true to convert from JavaFX to PDF, false for PDF to JavaFX
     * @return the converted rectangle
     * @throws IllegalArgumentException if rect is null, pageHeight is non-positive, or scale is non-positive
     */
    public static Rectangle2D convertRectangle(Rectangle2D rect, double pageHeight, double scale, boolean toPdf) {
        if (rect == null) {
            throw new IllegalArgumentException("Rectangle cannot be null");
        }
        if (pageHeight <= 0) {
            throw new IllegalArgumentException("Page height must be positive");
        }
        if (scale <= 0) {
            throw new IllegalArgumentException("Scale must be positive");
        }

        double x = rect.getMinX();
        double y = rect.getMinY();
        double width = rect.getWidth();
        double height = rect.getHeight();

        if (toPdf) {
            // JavaFX to PDF: divide by scale, flip Y to bottom-left corner
            x = x / scale;
            width = width / scale;
            height = height / scale;
            // In JavaFX, y is top-left; in PDF, y should be bottom-left
            double topY = y / scale;
            y = javafxToPdfY(topY + height, pageHeight);
        } else {
            // PDF to JavaFX: flip Y to top-left corner, multiply by scale
            // In PDF, y is bottom-left; in JavaFX, y should be top-left
            double bottomY = y;
            y = pdfToJavafxY(bottomY + height, pageHeight) * scale;
            x = x * scale;
            width = width * scale;
            height = height * scale;
        }

        return new Rectangle2D(x, y, width, height);
    }

    /**
     * Clamps a coordinate value to be within valid page bounds.
     *
     * @param value the coordinate value to clamp
     * @param min   the minimum valid value (typically 0)
     * @param max   the maximum valid value (typically page width or height)
     * @return the clamped value
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamps a point to be within valid page bounds.
     *
     * @param point      the point to clamp
     * @param pageWidth  the width of the page
     * @param pageHeight the height of the page
     * @return the clamped point
     * @throws IllegalArgumentException if point is null or dimensions are non-positive
     */
    public static Point2D clampPoint(Point2D point, double pageWidth, double pageHeight) {
        if (point == null) {
            throw new IllegalArgumentException("Point cannot be null");
        }
        if (pageWidth <= 0 || pageHeight <= 0) {
            throw new IllegalArgumentException("Page dimensions must be positive");
        }

        double x = clamp(point.getX(), 0, pageWidth);
        double y = clamp(point.getY(), 0, pageHeight);
        return new Point2D(x, y);
    }

    /**
     * Checks if two double values are equal within tolerance.
     *
     * @param a the first value
     * @param b the second value
     * @return true if the values are equal within tolerance
     */
    public static boolean equals(double a, double b) {
        return Math.abs(a - b) < TOLERANCE;
    }

    /**
     * Checks if two points are equal within tolerance.
     *
     * @param p1 the first point
     * @param p2 the second point
     * @return true if the points are equal within tolerance
     */
    public static boolean equals(Point2D p1, Point2D p2) {
        if (p1 == null || p2 == null) {
            return p1 == p2;
        }
        return equals(p1.getX(), p2.getX()) && equals(p1.getY(), p2.getY());
    }
}
