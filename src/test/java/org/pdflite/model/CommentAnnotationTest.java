package org.pdflite.model;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CommentAnnotation.
 */
class CommentAnnotationTest {

    @Test
    void testCommentAnnotationCreation() {
        CommentAnnotation annotation = new CommentAnnotation(
            0, 100.0, 150.0, "Test comment", Color.YELLOW
        );
        
        assertEquals(0, annotation.getPageNumber());
        assertEquals(100.0, annotation.getX());
        assertEquals(150.0, annotation.getY());
        assertEquals("Test comment", annotation.getComment());
        assertEquals(Color.YELLOW, annotation.getColor());
        assertEquals("COMMENT", annotation.getType());
    }

    @Test
    void testCommentAnnotationWithDifferentColors() {
        CommentAnnotation red = new CommentAnnotation(1, 50.0, 75.0, "Red", Color.RED);
        CommentAnnotation blue = new CommentAnnotation(1, 50.0, 75.0, "Blue", Color.BLUE);
        
        assertEquals(Color.RED, red.getColor());
        assertEquals(Color.BLUE, blue.getColor());
    }

    @Test
    void testCommentAnnotationWithEmptyText() {
        CommentAnnotation annotation = new CommentAnnotation(
            0, 100.0, 150.0, "", Color.YELLOW
        );
        
        assertEquals("", annotation.getComment());
    }

    @Test
    void testCommentAnnotationWithLongText() {
        String longComment = "This is a very long comment that contains multiple sentences. " +
                           "It should be stored correctly without any truncation.";
        CommentAnnotation annotation = new CommentAnnotation(
            2, 200.0, 300.0, longComment, Color.GREEN
        );
        
        assertEquals(longComment, annotation.getComment());
    }
}
