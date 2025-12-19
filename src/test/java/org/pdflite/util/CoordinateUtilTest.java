package org.pdflite.util;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for CoordinateUtil.
 * Contains both unit tests and property-based tests.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 */
@RunWith(JUnitQuickcheck.class)
public class CoordinateUtilTest {

    /**
     * Feature: native-pdf-editing, Property 4: Coordinate Conversion Round-Trip
     * Validates: Requirements 1.5, 2.5
     * <p>
     * For any coordinate point and page height, converting from JavaFX to PDF coordinates
     * and back should return the original coordinate (within floating-point tolerance).
     */
    @Property(trials = 100)
    public void coordinateRoundTripProperty(double x, double y, double pageHeight) {
        // Ensure valid inputs
        if (pageHeight <= 0 || Double.isNaN(pageHeight) || Double.isInfinite(pageHeight)) {
            return; // Skip invalid inputs
        }
        if (Double.isNaN(x) || Double.isInfinite(x) || Double.isNaN(y) || Double.isInfinite(y)) {
            return; // Skip invalid inputs
        }

        // Clamp coordinates to reasonable bounds
        x = Math.max(-10000, Math.min(10000, x));
        y = Math.max(-10000, Math.min(10000, y));
        pageHeight = Math.max(1, Math.min(10000, Math.abs(pageHeight)));

        // Test Y coordinate round-trip: JavaFX -> PDF -> JavaFX
        double pdfY = CoordinateUtil.javafxToPdfY(y, pageHeight);
        double javafxY = CoordinateUtil.pdfToJavafxY(pdfY, pageHeight);

        assertTrue(CoordinateUtil.equals(y, javafxY),
                String.format("Y coordinate round-trip failed: original=%.3f, after round-trip=%.3f, pageHeight=%.3f",
                        y, javafxY, pageHeight));

        // Test Y coordinate round-trip: PDF -> JavaFX -> PDF
        double javafxY2 = CoordinateUtil.pdfToJavafxY(y, pageHeight);
        double pdfY2 = CoordinateUtil.javafxToPdfY(javafxY2, pageHeight);

        assertTrue(CoordinateUtil.equals(y, pdfY2),
                String.format("Y coordinate reverse round-trip failed: original=%.3f, after round-trip=%.3f, pageHeight=%.3f",
                        y, pdfY2, pageHeight));
    }

    /**
     * Feature: native-pdf-editing, Property 4: Coordinate Conversion Round-Trip (Point)
     * Validates: Requirements 1.5, 2.5
     * <p>
     * For any point, page height, and scale, converting from JavaFX to PDF and back
     * should return the original point (within tolerance).
     */
    @Property(trials = 100)
    public void pointRoundTripProperty(double x, double y, double pageHeight, double scale) {
        // Ensure valid inputs
        if (pageHeight <= 0 || scale <= 0 || Double.isNaN(pageHeight) || Double.isInfinite(pageHeight) ||
                Double.isNaN(scale) || Double.isInfinite(scale)) {
            return; // Skip invalid inputs
        }
        if (Double.isNaN(x) || Double.isInfinite(x) || Double.isNaN(y) || Double.isInfinite(y)) {
            return; // Skip invalid inputs
        }

        // Clamp to reasonable bounds
        x = Math.max(-10000, Math.min(10000, x));
        y = Math.max(-10000, Math.min(10000, y));
        pageHeight = Math.max(1, Math.min(10000, Math.abs(pageHeight)));
        scale = Math.max(0.1, Math.min(10, Math.abs(scale)));

        Point2D original = new Point2D(x, y);

        // Round-trip: JavaFX -> PDF -> JavaFX
        Point2D pdfPoint = CoordinateUtil.convertPoint(original, pageHeight, scale, true);
        Point2D javafxPoint = CoordinateUtil.convertPoint(pdfPoint, pageHeight, scale, false);

        assertTrue(CoordinateUtil.equals(original, javafxPoint),
                String.format("Point round-trip failed: original=%s, after round-trip=%s, pageHeight=%.3f, scale=%.3f",
                        original, javafxPoint, pageHeight, scale));
    }

    /**
     * Feature: native-pdf-editing, Property 4: Coordinate Conversion Round-Trip (Rectangle)
     * Validates: Requirements 1.5, 2.5
     * <p>
     * For any rectangle, page height, and scale, converting from JavaFX to PDF and back
     * should return the original rectangle (within tolerance).
     */
    @Property(trials = 100)
    public void rectangleRoundTripProperty(double x, double y, double width, double height,
                                           double pageHeight, double scale) {
        // Ensure valid inputs
        if (pageHeight <= 0 || scale <= 0 || width <= 0 || height <= 0) {
            return; // Skip invalid inputs
        }
        if (Double.isNaN(x) || Double.isInfinite(x) || Double.isNaN(y) || Double.isInfinite(y) ||
                Double.isNaN(width) || Double.isInfinite(width) || Double.isNaN(height) || Double.isInfinite(height) ||
                Double.isNaN(pageHeight) || Double.isInfinite(pageHeight) || Double.isNaN(scale) || Double.isInfinite(scale)) {
            return; // Skip invalid inputs
        }

        // Clamp to reasonable bounds
        x = Math.max(0, Math.min(10000, Math.abs(x)));
        y = Math.max(0, Math.min(10000, Math.abs(y)));
        width = Math.max(1, Math.min(1000, Math.abs(width)));
        height = Math.max(1, Math.min(1000, Math.abs(height)));
        pageHeight = Math.max(height + y + 1, Math.min(10000, Math.abs(pageHeight)));
        scale = Math.max(0.1, Math.min(10, Math.abs(scale)));

        Rectangle2D original = new Rectangle2D(x, y, width, height);

        // Round-trip: JavaFX -> PDF -> JavaFX
        Rectangle2D pdfRect = CoordinateUtil.convertRectangle(original, pageHeight, scale, true);
        Rectangle2D javafxRect = CoordinateUtil.convertRectangle(pdfRect, pageHeight, scale, false);

        assertTrue(CoordinateUtil.equals(original.getMinX(), javafxRect.getMinX()) &&
                        CoordinateUtil.equals(original.getMinY(), javafxRect.getMinY()) &&
                        CoordinateUtil.equals(original.getWidth(), javafxRect.getWidth()) &&
                        CoordinateUtil.equals(original.getHeight(), javafxRect.getHeight()),
                String.format("Rectangle round-trip failed: original=%s, after round-trip=%s, pageHeight=%.3f, scale=%.3f",
                        original, javafxRect, pageHeight, scale));
    }

    // ==================== Unit Tests ====================

    @Test
    public void testJavafxToPdfY() {
        double pageHeight = 792; // Standard letter size height in points
        assertEquals(792, CoordinateUtil.javafxToPdfY(0, pageHeight), CoordinateUtil.TOLERANCE);
        assertEquals(0, CoordinateUtil.javafxToPdfY(792, pageHeight), CoordinateUtil.TOLERANCE);
        assertEquals(396, CoordinateUtil.javafxToPdfY(396, pageHeight), CoordinateUtil.TOLERANCE);
    }

    @Test
    public void testPdfToJavafxY() {
        double pageHeight = 792;
        assertEquals(0, CoordinateUtil.pdfToJavafxY(792, pageHeight), CoordinateUtil.TOLERANCE);
        assertEquals(792, CoordinateUtil.pdfToJavafxY(0, pageHeight), CoordinateUtil.TOLERANCE);
        assertEquals(396, CoordinateUtil.pdfToJavafxY(396, pageHeight), CoordinateUtil.TOLERANCE);
    }

    @Test
    public void testConvertPointJavafxToPdf() {
        Point2D javafxPoint = new Point2D(100, 100);
        double pageHeight = 792;
        double scale = 1.0;

        Point2D pdfPoint = CoordinateUtil.convertPoint(javafxPoint, pageHeight, scale, true);

        assertEquals(100, pdfPoint.getX(), CoordinateUtil.TOLERANCE);
        assertEquals(692, pdfPoint.getY(), CoordinateUtil.TOLERANCE); // 792 - 100 = 692
    }

    @Test
    public void testConvertPointPdfToJavafx() {
        Point2D pdfPoint = new Point2D(100, 692);
        double pageHeight = 792;
        double scale = 1.0;

        Point2D javafxPoint = CoordinateUtil.convertPoint(pdfPoint, pageHeight, scale, false);

        assertEquals(100, javafxPoint.getX(), CoordinateUtil.TOLERANCE);
        assertEquals(100, javafxPoint.getY(), CoordinateUtil.TOLERANCE); // 792 - 692 = 100
    }

    @Test
    public void testConvertPointWithScale() {
        Point2D javafxPoint = new Point2D(200, 200);
        double pageHeight = 792;
        double scale = 2.0;

        Point2D pdfPoint = CoordinateUtil.convertPoint(javafxPoint, pageHeight, scale, true);

        assertEquals(100, pdfPoint.getX(), CoordinateUtil.TOLERANCE); // 200 / 2 = 100
        assertEquals(692, pdfPoint.getY(), CoordinateUtil.TOLERANCE); // 792 - (200/2) = 692
    }

    @Test
    public void testConvertRectangle() {
        Rectangle2D javafxRect = new Rectangle2D(50, 50, 100, 100);
        double pageHeight = 792;
        double scale = 1.0;

        Rectangle2D pdfRect = CoordinateUtil.convertRectangle(javafxRect, pageHeight, scale, true);

        assertEquals(50, pdfRect.getMinX(), CoordinateUtil.TOLERANCE);
        assertEquals(642, pdfRect.getMinY(), CoordinateUtil.TOLERANCE); // 792 - (50 + 100) = 642
        assertEquals(100, pdfRect.getWidth(), CoordinateUtil.TOLERANCE);
        assertEquals(100, pdfRect.getHeight(), CoordinateUtil.TOLERANCE);
    }

    @Test
    public void testClamp() {
        assertEquals(5, CoordinateUtil.clamp(5, 0, 10), CoordinateUtil.TOLERANCE);
        assertEquals(0, CoordinateUtil.clamp(-5, 0, 10), CoordinateUtil.TOLERANCE);
        assertEquals(10, CoordinateUtil.clamp(15, 0, 10), CoordinateUtil.TOLERANCE);
    }

    @Test
    public void testClampPoint() {
        Point2D point = new Point2D(150, 150);
        Point2D clamped = CoordinateUtil.clampPoint(point, 100, 100);

        assertEquals(100, clamped.getX(), CoordinateUtil.TOLERANCE);
        assertEquals(100, clamped.getY(), CoordinateUtil.TOLERANCE);
    }

    @Test
    public void testEquals() {
        assertTrue(CoordinateUtil.equals(1.0, 1.0001));
        assertTrue(CoordinateUtil.equals(1.0, 0.9999));
        assertFalse(CoordinateUtil.equals(1.0, 1.1));
    }

    @Test
    public void testPointEquals() {
        Point2D p1 = new Point2D(1.0, 2.0);
        Point2D p2 = new Point2D(1.0001, 2.0001);
        Point2D p3 = new Point2D(1.1, 2.1);

        assertTrue(CoordinateUtil.equals(p1, p2));
        assertFalse(CoordinateUtil.equals(p1, p3));
        assertTrue(CoordinateUtil.equals(null, null));
        assertFalse(CoordinateUtil.equals(p1, null));
    }

    @Test
    public void testInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () ->
                CoordinateUtil.convertPoint(null, 100, 1.0, true));

        assertThrows(IllegalArgumentException.class, () ->
                CoordinateUtil.convertPoint(new Point2D(0, 0), -100, 1.0, true));

        assertThrows(IllegalArgumentException.class, () ->
                CoordinateUtil.convertPoint(new Point2D(0, 0), 100, -1.0, true));

        assertThrows(IllegalArgumentException.class, () ->
                CoordinateUtil.convertRectangle(null, 100, 1.0, true));

        assertThrows(IllegalArgumentException.class, () ->
                CoordinateUtil.clampPoint(null, 100, 100));

        assertThrows(IllegalArgumentException.class, () ->
                CoordinateUtil.clampPoint(new Point2D(0, 0), -100, 100));
    }

    @Test
    public void testUtilityClassCannotBeInstantiated() {
        assertThrows(Exception.class, () -> {
            var constructor = CoordinateUtil.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            try {
                constructor.newInstance();
            } catch (java.lang.reflect.InvocationTargetException e) {
                // Unwrap the actual exception
                throw e.getCause();
            }
        });
    }
}
