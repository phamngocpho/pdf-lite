package org.pdflite.model;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RectangleAnnotation.
 */
class RectangleAnnotationTest {

    @Test
    void testRectangleAnnotationCreation() {
        RectangleAnnotation rect = new RectangleAnnotation(0, 10, 20, 100, 200, Color.GREEN, 2.0);
        
        assertEquals(0, rect.getPageNumber());
        assertEquals(10, rect.getX());
        assertEquals(20, rect.getY());
        assertEquals(100, rect.getEndX());
        assertEquals(200, rect.getEndY());
        assertEquals(Color.GREEN, rect.getColor());
        assertEquals(2.0, rect.getLineWidth());
        assertEquals("Rectangle", rect.getType());
    }

    @Test
    void testRectangleAnnotationWithDifferentColors() {
        RectangleAnnotation rect1 = new RectangleAnnotation(0, 0, 0, 50, 50, Color.ORANGE, 1.0);
        RectangleAnnotation rect2 = new RectangleAnnotation(1, 0, 0, 50, 50, Color.PURPLE, 1.0);
        
        assertEquals(Color.ORANGE, rect1.getColor());
        assertEquals(Color.PURPLE, rect2.getColor());
    }

    @Test
    void testRectangleAnnotationWithDifferentSizes() {
        RectangleAnnotation small = new RectangleAnnotation(0, 0, 0, 30, 40, Color.BLACK, 1.0);
        RectangleAnnotation large = new RectangleAnnotation(0, 0, 0, 300, 400, Color.BLACK, 1.0);
        
        assertEquals(30, small.getEndX());
        assertEquals(40, small.getEndY());
        assertEquals(300, large.getEndX());
        assertEquals(400, large.getEndY());
    }

    @Test
    void testRectangleAnnotationType() {
        RectangleAnnotation rect = new RectangleAnnotation(0, 0, 0, 50, 50, Color.BLACK, 1.0);
        assertEquals("Rectangle", rect.getType());
    }
}
