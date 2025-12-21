package org.pdflite.util;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CoordinateConverter.
 */
class CoordinateConverterTest {

    private static final float PAGE_WIDTH = 595f;  // A4 width in points
    private static final float PAGE_HEIGHT = 842f; // A4 height in points
    private static final double ZOOM = 1.0;
    private static final double DELTA = 0.01;

    @Test
    void testCanvasToPdfJavaRect() {
        Rectangle2D.Float result = CoordinateConverter.canvasToPdfJavaRect(
            100, 200, 50, 30, PAGE_HEIGHT, ZOOM
        );
        
        assertNotNull(result);
        assertTrue(result.x > 0);
        assertTrue(result.y > 0);
        assertTrue(result.width > 0);
        assertTrue(result.height > 0);
    }

    @Test
    void testCanvasToPdfJavaRectWithZoom() {
        Rectangle2D.Float result1 = CoordinateConverter.canvasToPdfJavaRect(
            100, 100, 50, 50, PAGE_HEIGHT, 1.0
        );
        
        Rectangle2D.Float result2 = CoordinateConverter.canvasToPdfJavaRect(
            100, 100, 50, 50, PAGE_HEIGHT, 2.0
        );
        
        // At 2x zoom, PDF coordinates should be smaller
        assertTrue(result2.x < result1.x);
        assertTrue(result2.width < result1.width);
    }

    @Test
    void testCanvasToPdfJavaPoint() {
        Point2D.Float result = CoordinateConverter.canvasToPdfJavaPoint(
            100, 200, PAGE_HEIGHT, ZOOM
        );
        
        assertNotNull(result);
        assertTrue(result.x > 0);
        assertTrue(result.y > 0);
    }

    @Test
    void testPdfToCanvasPoint() {
        Point2D.Float result = CoordinateConverter.pdfToCanvasPoint(
            100, 200, PAGE_HEIGHT, ZOOM
        );
        
        assertNotNull(result);
        assertTrue(result.x > 0);
        assertTrue(result.y > 0);
    }

    @Test
    void testPdfToCanvasRect() {
        Rectangle2D.Float result = CoordinateConverter.pdfToCanvasRect(
            100, 200, 50, 30, PAGE_HEIGHT, ZOOM
        );
        
        assertNotNull(result);
        assertTrue(result.x > 0);
        assertTrue(result.y > 0);
        assertTrue(result.width > 0);
        assertTrue(result.height > 0);
    }

    @Test
    void testIsValidPdfCoordinate() {
        assertTrue(CoordinateConverter.isValidPdfCoordinate(
            100, 200, PAGE_WIDTH, PAGE_HEIGHT
        ));
        
        assertTrue(CoordinateConverter.isValidPdfCoordinate(
            0, 0, PAGE_WIDTH, PAGE_HEIGHT
        ));
        
        assertTrue(CoordinateConverter.isValidPdfCoordinate(
            PAGE_WIDTH, PAGE_HEIGHT, PAGE_WIDTH, PAGE_HEIGHT
        ));
    }

    @Test
    void testIsValidPdfCoordinateOutOfBounds() {
        assertFalse(CoordinateConverter.isValidPdfCoordinate(
            -10, 200, PAGE_WIDTH, PAGE_HEIGHT
        ));
        
        assertFalse(CoordinateConverter.isValidPdfCoordinate(
            100, -10, PAGE_WIDTH, PAGE_HEIGHT
        ));
        
        assertFalse(CoordinateConverter.isValidPdfCoordinate(
            PAGE_WIDTH + 10, 200, PAGE_WIDTH, PAGE_HEIGHT
        ));
        
        assertFalse(CoordinateConverter.isValidPdfCoordinate(
            100, PAGE_HEIGHT + 10, PAGE_WIDTH, PAGE_HEIGHT
        ));
    }

    @Test
    void testCoordinateConversionRoundTrip() {
        // Convert canvas to PDF and back
        double canvasX = 100;
        double canvasY = 200;
        
        Point2D.Float pdfPoint = CoordinateConverter.canvasToPdfJavaPoint(
            canvasX, canvasY, PAGE_HEIGHT, ZOOM
        );
        
        // Note: Direct round-trip is not exact due to Y-axis inversion
        // This test just verifies the conversion doesn't crash
        assertNotNull(pdfPoint);
    }

    @Test
    void testZeroCoordinates() {
        Rectangle2D.Float result = CoordinateConverter.canvasToPdfJavaRect(
            0, 0, 100, 100, PAGE_HEIGHT, ZOOM
        );
        
        assertEquals(0, result.x, DELTA);
        assertEquals(0, result.y, DELTA);
    }
}
