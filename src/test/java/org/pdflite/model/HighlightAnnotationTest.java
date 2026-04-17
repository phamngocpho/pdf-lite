package org.pdflite.model;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HighlightAnnotation.
 */
class HighlightAnnotationTest {

    @Test
    void testHighlightAnnotationCreation() {
        HighlightAnnotation annotation = new HighlightAnnotation(
            0, 100.0, 150.0, 200.0, 50.0, Color.YELLOW
        );
        
        assertEquals(0, annotation.getPageNumber());
        assertEquals(100.0, annotation.getX());
        assertEquals(150.0, annotation.getY());
        assertEquals(200.0, annotation.getWidth());
        assertEquals(50.0, annotation.getHeight());
        assertEquals(Color.YELLOW, annotation.getColor());
        assertEquals("HIGHLIGHT", annotation.getType());
        assertNull(annotation.getBatchId());
    }

    @Test
    void testHighlightAnnotationWithBatchId() {
        HighlightAnnotation annotation = new HighlightAnnotation(
            1, 50.0, 75.0, 150.0, 30.0, Color.GREEN, "batch-123"
        );
        
        assertEquals("batch-123", annotation.getBatchId());
    }

    @Test
    void testHighlightAnnotationDimensions() {
        HighlightAnnotation small = new HighlightAnnotation(
            0, 10.0, 20.0, 5.0, 5.0, Color.RED
        );
        HighlightAnnotation large = new HighlightAnnotation(
            0, 10.0, 20.0, 500.0, 300.0, Color.BLUE
        );
        
        assertEquals(5.0, small.getWidth());
        assertEquals(5.0, small.getHeight());
        assertEquals(500.0, large.getWidth());
        assertEquals(300.0, large.getHeight());
    }

    @Test
    void testHighlightAnnotationColors() {
        HighlightAnnotation yellow = new HighlightAnnotation(
            0, 0, 0, 100, 20, Color.YELLOW
        );
        HighlightAnnotation cyan = new HighlightAnnotation(
            0, 0, 0, 100, 20, Color.CYAN
        );
        
        assertEquals(Color.YELLOW, yellow.getColor());
        assertEquals(Color.CYAN, cyan.getColor());
    }

    @Test
    void testHighlightOpacity() {
        HighlightAnnotation annotation = new HighlightAnnotation(
            0, 0, 0, 100, 20, Color.YELLOW, 0.55
        );

        assertEquals(0.55, annotation.getOpacity());
    }
}
