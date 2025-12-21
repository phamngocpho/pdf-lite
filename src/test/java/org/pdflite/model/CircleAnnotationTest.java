package org.pdflite.model;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CircleAnnotation.
 */
class CircleAnnotationTest {

    @Test
    void testCircleAnnotationCreation() {
        CircleAnnotation circle = new CircleAnnotation(0, 10, 20, 100, 200, Color.BLUE, 2.0);
        
        assertEquals(0, circle.getPageNumber());
        assertEquals(10, circle.getX());
        assertEquals(20, circle.getY());
        assertEquals(100, circle.getEndX());
        assertEquals(200, circle.getEndY());
        assertEquals(Color.BLUE, circle.getColor());
        assertEquals(2.0, circle.getLineWidth());
        assertEquals("Circle", circle.getType());
    }

    @Test
    void testCircleAnnotationWithDifferentColors() {
        CircleAnnotation circle1 = new CircleAnnotation(0, 0, 0, 50, 50, Color.RED, 1.0);
        CircleAnnotation circle2 = new CircleAnnotation(1, 0, 0, 50, 50, Color.YELLOW, 1.0);
        
        assertEquals(Color.RED, circle1.getColor());
        assertEquals(Color.YELLOW, circle2.getColor());
    }

    @Test
    void testCircleAnnotationWithDifferentSizes() {
        CircleAnnotation small = new CircleAnnotation(0, 0, 0, 20, 20, Color.BLACK, 1.0);
        CircleAnnotation large = new CircleAnnotation(0, 0, 0, 200, 200, Color.BLACK, 1.0);
        
        assertEquals(20, small.getEndX());
        assertEquals(200, large.getEndX());
    }

    @Test
    void testCircleAnnotationType() {
        CircleAnnotation circle = new CircleAnnotation(0, 0, 0, 50, 50, Color.BLACK, 1.0);
        assertEquals("Circle", circle.getType());
    }
}
