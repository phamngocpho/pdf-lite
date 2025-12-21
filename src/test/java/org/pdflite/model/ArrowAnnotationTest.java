package org.pdflite.model;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ArrowAnnotation.
 */
class ArrowAnnotationTest {

    @Test
    void testArrowAnnotationCreation() {
        ArrowAnnotation arrow = new ArrowAnnotation(0, 10, 20, 100, 200, Color.RED, 2.0);
        
        assertEquals(0, arrow.getPageNumber());
        assertEquals(10, arrow.getX());
        assertEquals(20, arrow.getY());
        assertEquals(100, arrow.getEndX());
        assertEquals(200, arrow.getEndY());
        assertEquals(Color.RED, arrow.getColor());
        assertEquals(2.0, arrow.getLineWidth());
        assertEquals("Arrow", arrow.getType());
    }

    @Test
    void testArrowAnnotationWithDifferentColors() {
        ArrowAnnotation arrow1 = new ArrowAnnotation(0, 0, 0, 50, 50, Color.BLUE, 1.0);
        ArrowAnnotation arrow2 = new ArrowAnnotation(1, 0, 0, 50, 50, Color.GREEN, 1.0);
        
        assertEquals(Color.BLUE, arrow1.getColor());
        assertEquals(Color.GREEN, arrow2.getColor());
    }

    @Test
    void testArrowAnnotationWithDifferentLineWidths() {
        ArrowAnnotation thin = new ArrowAnnotation(0, 0, 0, 50, 50, Color.BLACK, 1.0);
        ArrowAnnotation thick = new ArrowAnnotation(0, 0, 0, 50, 50, Color.BLACK, 5.0);
        
        assertEquals(1.0, thin.getLineWidth());
        assertEquals(5.0, thick.getLineWidth());
    }

    @Test
    void testArrowAnnotationOnDifferentPages() {
        ArrowAnnotation arrow1 = new ArrowAnnotation(0, 0, 0, 50, 50, Color.BLACK, 1.0);
        ArrowAnnotation arrow2 = new ArrowAnnotation(5, 0, 0, 50, 50, Color.BLACK, 1.0);
        
        assertEquals(0, arrow1.getPageNumber());
        assertEquals(5, arrow2.getPageNumber());
    }

    @Test
    void testArrowAnnotationType() {
        ArrowAnnotation arrow = new ArrowAnnotation(0, 0, 0, 50, 50, Color.BLACK, 1.0);
        assertEquals("Arrow", arrow.getType());
    }
}
