package org.pdflite.model;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Annotation base class.
 */
class AnnotationTest {

    @Test
    void testAnnotationCreation() {
        CommentAnnotation annotation = new CommentAnnotation(0, 100.0, 200.0, "Test comment", Color.YELLOW);
        
        assertEquals(0, annotation.getPageNumber());
        assertEquals(100.0, annotation.getX());
        assertEquals(200.0, annotation.getY());
        assertEquals("COMMENT", annotation.getType());
    }

    @Test
    void testSetPageNumber() {
        CommentAnnotation annotation = new CommentAnnotation(0, 100.0, 200.0, "Test", Color.YELLOW);
        
        annotation.setPageNumber(5);
        
        assertEquals(5, annotation.getPageNumber());
    }

    @Test
    void testSetX() {
        CommentAnnotation annotation = new CommentAnnotation(0, 100.0, 200.0, "Test", Color.YELLOW);
        
        annotation.setX(300.0);
        
        assertEquals(300.0, annotation.getX());
    }

    @Test
    void testSetY() {
        CommentAnnotation annotation = new CommentAnnotation(0, 100.0, 200.0, "Test", Color.YELLOW);
        
        annotation.setY(400.0);
        
        assertEquals(400.0, annotation.getY());
    }

    @Test
    void testNegativeCoordinates() {
        CommentAnnotation annotation = new CommentAnnotation(0, -50.0, -100.0, "Test", Color.YELLOW);
        
        assertEquals(-50.0, annotation.getX());
        assertEquals(-100.0, annotation.getY());
    }

    @Test
    void testZeroCoordinates() {
        CommentAnnotation annotation = new CommentAnnotation(0, 0.0, 0.0, "Test", Color.YELLOW);
        
        assertEquals(0.0, annotation.getX());
        assertEquals(0.0, annotation.getY());
    }

    @Test
    void testLargeCoordinates() {
        CommentAnnotation annotation = new CommentAnnotation(0, 10000.0, 20000.0, "Test", Color.YELLOW);
        
        assertEquals(10000.0, annotation.getX());
        assertEquals(20000.0, annotation.getY());
    }
}
