package org.pdflite.model;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ShapeAnnotation subclasses.
 */
class ShapeAnnotationTest {

    @Test
    void testCircleAnnotationCreation() {
        CircleAnnotation circle = new CircleAnnotation(
            0, 100.0, 150.0, 200.0, 250.0, Color.RED, 2.0
        );
        
        assertEquals(0, circle.getPageNumber());
        assertEquals(100.0, circle.getX());
        assertEquals(150.0, circle.getY());
        assertEquals(200.0, circle.getEndX());
        assertEquals(250.0, circle.getEndY());
        assertEquals(Color.RED, circle.getColor());
        assertEquals(2.0, circle.getLineWidth());
        assertEquals("Circle", circle.getType());
    }

    @Test
    void testRectangleAnnotationCreation() {
        RectangleAnnotation rect = new RectangleAnnotation(
            1, 50.0, 75.0, 150.0, 175.0, Color.BLUE, 3.0
        );
        
        assertEquals(1, rect.getPageNumber());
        assertEquals(50.0, rect.getX());
        assertEquals(75.0, rect.getY());
        assertEquals(150.0, rect.getEndX());
        assertEquals(175.0, rect.getEndY());
        assertEquals(Color.BLUE, rect.getColor());
        assertEquals(3.0, rect.getLineWidth());
        assertEquals("Rectangle", rect.getType());
    }

    @Test
    void testArrowAnnotationCreation() {
        ArrowAnnotation arrow = new ArrowAnnotation(
            2, 100.0, 100.0, 300.0, 200.0, Color.GREEN, 1.5
        );
        
        assertEquals(2, arrow.getPageNumber());
        assertEquals(100.0, arrow.getX());
        assertEquals(100.0, arrow.getY());
        assertEquals(300.0, arrow.getEndX());
        assertEquals(200.0, arrow.getEndY());
        assertEquals(Color.GREEN, arrow.getColor());
        assertEquals(1.5, arrow.getLineWidth());
        assertEquals("Arrow", arrow.getType());
    }

    @Test
    void testShapeAnnotationSetters() {
        CircleAnnotation circle = new CircleAnnotation(
            0, 0, 0, 100, 100, Color.BLACK, 1.0
        );
        
        circle.setEndX(200.0);
        circle.setEndY(200.0);
        circle.setColor(Color.WHITE);
        circle.setLineWidth(5.0);
        
        assertEquals(200.0, circle.getEndX());
        assertEquals(200.0, circle.getEndY());
        assertEquals(Color.WHITE, circle.getColor());
        assertEquals(5.0, circle.getLineWidth());
    }

    @Test
    void testCustomShapeStyleCreation() {
        RectangleAnnotation rect = new RectangleAnnotation(
            0, 10, 20, 110, 120, Color.RED, 4.0, AnnotationLineStyle.DASHED, 0.6
        );

        assertEquals(AnnotationLineStyle.DASHED, rect.getLineStyle());
        assertEquals(0.6, rect.getOpacity());
    }

    @Test
    void testShapeOpacityIsClamped() {
        CircleAnnotation transparent = new CircleAnnotation(
            0, 0, 0, 100, 100, Color.BLACK, 1.0, AnnotationLineStyle.DOTTED, -1.0
        );
        CircleAnnotation opaque = new CircleAnnotation(
            0, 0, 0, 100, 100, Color.BLACK, 1.0, AnnotationLineStyle.DOTTED, 2.0
        );

        assertEquals(0.0, transparent.getOpacity());
        assertEquals(1.0, opaque.getOpacity());
    }

    @Test
    void testAnnotationSetters() {
        RectangleAnnotation rect = new RectangleAnnotation(
            0, 0, 0, 100, 100, Color.BLACK, 1.0
        );
        
        rect.setPageNumber(5);
        rect.setX(50.0);
        rect.setY(75.0);
        
        assertEquals(5, rect.getPageNumber());
        assertEquals(50.0, rect.getX());
        assertEquals(75.0, rect.getY());
    }
}
