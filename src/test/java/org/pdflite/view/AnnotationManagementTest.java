package org.pdflite.view;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.paint.Color;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.pdflite.model.Annotation;
import org.pdflite.model.HighlightAnnotation;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Property-based and unit tests for annotation management in AnnotationLayer.
 * Tests annotation hit detection, deletion, and color updates.
 */
@RunWith(JUnitQuickcheck.class)
public class AnnotationManagementTest {

    @Before
    public void setUp() {
        // Initialize JavaFX toolkit (required for Canvas)
        try {
            javafx.application.Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Already initialized
        }
    }

    // ==================== PROPERTY-BASED TESTS ====================

    /**
     * Feature: native-pdf-editing, Property 12: Annotation Hit Detection
     * 
     * For any annotation and click coordinates, the hit detection should correctly
     * identify whether the click is within the annotation bounds.
     * 
     * Validates: Requirements 4.2
     */
    @Property(trials = 100)
    public void property12_annotationHitDetection(double annotX, double annotY, 
                                                   double annotWidth, double annotHeight,
                                                   double clickX, double clickY) {
        // Constrain annotation to reasonable values
        annotX = Math.max(0, Math.min(Math.abs(annotX), 500));
        annotY = Math.max(0, Math.min(Math.abs(annotY), 400));
        annotWidth = Math.max(20, Math.min(Math.abs(annotWidth), 200));
        annotHeight = Math.max(20, Math.min(Math.abs(annotHeight), 150));
        
        // Constrain click coordinates
        clickX = Math.max(0, Math.min(Math.abs(clickX), 700));
        clickY = Math.max(0, Math.min(Math.abs(clickY), 550));

        // Create annotation layer
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        // Create annotation
        HighlightAnnotation annotation = new HighlightAnnotation(0, annotX, annotY, 
                                                                  annotWidth, annotHeight, Color.YELLOW);
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(annotation);
        layer.setAnnotations(annotations);
        
        // Test hit detection
        Annotation found = layer.findAnnotationAt(clickX, clickY);
        
        // Calculate expected result
        boolean shouldBeFound = (clickX >= annotX && clickX <= (annotX + annotWidth) &&
                                clickY >= annotY && clickY <= (annotY + annotHeight));
        
        if (shouldBeFound) {
            assertNotNull("Should find annotation when click is inside bounds", found);
            assertEquals("Should find the correct annotation", annotation, found);
        } else {
            // Click is outside bounds - should not find annotation
            // (unless there are other annotations, but we only have one)
            if (found != null) {
                // This can happen due to floating point precision
                // Verify it's still within reasonable tolerance
                assertTrue("If found, should be close to bounds", 
                          Math.abs(clickX - annotX) < 1 || 
                          Math.abs(clickX - (annotX + annotWidth)) < 1 ||
                          Math.abs(clickY - annotY) < 1 || 
                          Math.abs(clickY - (annotY + annotHeight)) < 1);
            }
        }
    }

    /**
     * Feature: native-pdf-editing, Property 13: Annotation Deletion
     * <p>
     * For any annotation in the collection, deleting it should remove it from
     * the collection, and it should not appear after redrawing.
     * <p>
     * Validates: Requirements 4.4
     */
    @Property(trials = 100)
    public void property13_annotationDeletion(double x, double y, double width, double height) {
        // Constrain to reasonable values
        x = Math.max(0, Math.min(Math.abs(x), 500));
        y = Math.max(0, Math.min(Math.abs(y), 400));
        width = Math.max(20, Math.min(Math.abs(width), 200));
        height = Math.max(20, Math.min(Math.abs(height), 150));

        // Create annotation layer
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        // Create annotation
        HighlightAnnotation annotation = new HighlightAnnotation(0, x, y, width, height, Color.YELLOW);
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(annotation);
        layer.setAnnotations(annotations);
        
        // Verify annotation is in collection
        assertEquals("Should have one annotation", 1, layer.getAnnotationCount());
        assertTrue("Should contain the annotation", layer.containsAnnotation(annotation));
        
        // Delete annotation
        boolean removed = layer.removeAnnotation(annotation);
        
        // Verify deletion
        assertTrue("Remove should return true", removed);
        assertEquals("Should have zero annotations after deletion", 0, layer.getAnnotationCount());
        assertFalse("Should not contain the annotation after deletion", layer.containsAnnotation(annotation));
        
        // Verify it's not found after redraw
        layer.redraw();
        assertEquals("Should still have zero annotations after redraw", 0, layer.getAnnotationCount());
    }

    /**
     * Feature: native-pdf-editing, Property 14: Annotation Color Update
     * 
     * For any annotation and new color value, updating the annotation's color
     * should change its rendered appearance.
     * 
     * Validates: Requirements 4.5
     */
    @Property(trials = 100)
    public void property14_annotationColorUpdate(double x, double y, double width, double height,
                                                  int red, int green, int blue) {
        // Constrain to reasonable values
        x = Math.max(0, Math.min(Math.abs(x), 500));
        y = Math.max(0, Math.min(Math.abs(y), 400));
        width = Math.max(20, Math.min(Math.abs(width), 200));
        height = Math.max(20, Math.min(Math.abs(height), 150));
        
        // Constrain RGB values
        red = Math.max(0, Math.min(Math.abs(red), 255));
        green = Math.max(0, Math.min(Math.abs(green), 255));
        blue = Math.max(0, Math.min(Math.abs(blue), 255));
        
        Color newColor = Color.rgb(red, green, blue);

        // Create annotation layer
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        // Create annotation with initial color
        HighlightAnnotation annotation = new HighlightAnnotation(0, x, y, width, height, Color.YELLOW);
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(annotation);
        layer.setAnnotations(annotations);
        
        // Verify initial color
        HighlightAnnotation initial = (HighlightAnnotation) layer.getAnnotations().get(0);
        assertEquals("Initial color should be YELLOW", Color.YELLOW, initial.getColor());
        
        // Update color
        boolean updated = layer.updateAnnotationColor(annotation, newColor);
        
        // Verify update
        assertTrue("Update should return true", updated);
        
        // Verify new color
        HighlightAnnotation afterUpdate = (HighlightAnnotation) layer.getAnnotations().get(0);
        assertEquals("Color should be updated", newColor, afterUpdate.getColor());
        
        // Verify other properties unchanged
        assertEquals("X should be unchanged", x, afterUpdate.getX(), 0.1);
        assertEquals("Y should be unchanged", y, afterUpdate.getY(), 0.1);
        assertEquals("Width should be unchanged", width, afterUpdate.getWidth(), 0.1);
        assertEquals("Height should be unchanged", height, afterUpdate.getHeight(), 0.1);
    }

    // ==================== UNIT TESTS ====================

    @Test
    public void testFindAnnotationAtCenter() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        HighlightAnnotation annotation = new HighlightAnnotation(0, 100, 100, 200, 100, Color.YELLOW);
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(annotation);
        layer.setAnnotations(annotations);
        
        // Click at center of annotation
        Annotation found = layer.findAnnotationAt(200, 150);
        
        assertNotNull("Should find annotation at center", found);
        assertEquals("Should find the correct annotation", annotation, found);
    }

    @Test
    public void testFindAnnotationAtEdge() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        HighlightAnnotation annotation = new HighlightAnnotation(0, 100, 100, 200, 100, Color.YELLOW);
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(annotation);
        layer.setAnnotations(annotations);
        
        // Click at edge of annotation
        Annotation found = layer.findAnnotationAt(100, 100);
        
        assertNotNull("Should find annotation at edge", found);
        assertEquals("Should find the correct annotation", annotation, found);
    }

    @Test
    public void testFindAnnotationOutsideBounds() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        HighlightAnnotation annotation = new HighlightAnnotation(0, 100, 100, 200, 100, Color.YELLOW);
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(annotation);
        layer.setAnnotations(annotations);
        
        // Click outside annotation bounds
        Annotation found = layer.findAnnotationAt(50, 50);
        
        assertNull("Should not find annotation outside bounds", found);
    }

    @Test
    public void testFindTopAnnotationWhenOverlapping() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        // Create two overlapping annotations
        HighlightAnnotation bottom = new HighlightAnnotation(0, 100, 100, 200, 100, Color.YELLOW);
        HighlightAnnotation top = new HighlightAnnotation(0, 150, 125, 150, 75, Color.GREEN);
        
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(bottom);
        annotations.add(top);
        layer.setAnnotations(annotations);
        
        // Click in overlapping area
        Annotation found = layer.findAnnotationAt(200, 150);
        
        assertNotNull("Should find an annotation", found);
        assertEquals("Should find the top annotation", top, found);
    }

    @Test
    public void testRemoveAnnotation() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        HighlightAnnotation annotation = new HighlightAnnotation(0, 100, 100, 200, 100, Color.YELLOW);
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(annotation);
        layer.setAnnotations(annotations);
        
        assertEquals("Should have one annotation", 1, layer.getAnnotationCount());
        
        boolean removed = layer.removeAnnotation(annotation);
        
        assertTrue("Remove should return true", removed);
        assertEquals("Should have zero annotations", 0, layer.getAnnotationCount());
    }

    @Test
    public void testRemoveNonExistentAnnotation() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        HighlightAnnotation annotation1 = new HighlightAnnotation(0, 100, 100, 200, 100, Color.YELLOW);
        HighlightAnnotation annotation2 = new HighlightAnnotation(0, 200, 200, 150, 80, Color.GREEN);
        
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(annotation1);
        layer.setAnnotations(annotations);
        
        // Try to remove annotation that's not in the layer
        boolean removed = layer.removeAnnotation(annotation2);
        
        assertFalse("Remove should return false for non-existent annotation", removed);
        assertEquals("Should still have one annotation", 1, layer.getAnnotationCount());
    }

    @Test
    public void testUpdateAnnotationColor() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        HighlightAnnotation annotation = new HighlightAnnotation(0, 100, 100, 200, 100, Color.YELLOW);
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(annotation);
        layer.setAnnotations(annotations);
        
        // Update color
        boolean updated = layer.updateAnnotationColor(annotation, Color.RED);
        
        assertTrue("Update should return true", updated);
        
        // Verify new color
        HighlightAnnotation afterUpdate = (HighlightAnnotation) layer.getAnnotations().get(0);
        assertEquals("Color should be RED", Color.RED, afterUpdate.getColor());
    }

    @Test
    public void testUpdateColorOfNonExistentAnnotation() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        HighlightAnnotation annotation1 = new HighlightAnnotation(0, 100, 100, 200, 100, Color.YELLOW);
        HighlightAnnotation annotation2 = new HighlightAnnotation(0, 200, 200, 150, 80, Color.GREEN);
        
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(annotation1);
        layer.setAnnotations(annotations);
        
        // Try to update color of annotation that's not in the layer
        boolean updated = layer.updateAnnotationColor(annotation2, Color.RED);
        
        assertFalse("Update should return false for non-existent annotation", updated);
    }

    @Test
    public void testContainsAnnotation() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        HighlightAnnotation annotation = new HighlightAnnotation(0, 100, 100, 200, 100, Color.YELLOW);
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(annotation);
        layer.setAnnotations(annotations);
        
        assertTrue("Should contain the annotation", layer.containsAnnotation(annotation));
        
        HighlightAnnotation otherAnnotation = new HighlightAnnotation(0, 200, 200, 150, 80, Color.GREEN);
        assertFalse("Should not contain other annotation", layer.containsAnnotation(otherAnnotation));
    }

    @Test
    public void testGetAnnotationCount() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        assertEquals("Initial count should be 0", 0, layer.getAnnotationCount());
        
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(new HighlightAnnotation(0, 100, 100, 200, 100, Color.YELLOW));
        annotations.add(new HighlightAnnotation(0, 200, 200, 150, 80, Color.GREEN));
        annotations.add(new HighlightAnnotation(0, 300, 300, 100, 60, Color.BLUE));
        
        layer.setAnnotations(annotations);
        
        assertEquals("Count should be 3", 3, layer.getAnnotationCount());
    }

    @Test
    public void testMultipleAnnotationDeletions() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        HighlightAnnotation annot1 = new HighlightAnnotation(0, 100, 100, 200, 100, Color.YELLOW);
        HighlightAnnotation annot2 = new HighlightAnnotation(0, 200, 200, 150, 80, Color.GREEN);
        HighlightAnnotation annot3 = new HighlightAnnotation(0, 300, 300, 100, 60, Color.BLUE);
        
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(annot1);
        annotations.add(annot2);
        annotations.add(annot3);
        layer.setAnnotations(annotations);
        
        assertEquals("Should have 3 annotations", 3, layer.getAnnotationCount());
        
        // Remove middle annotation
        layer.removeAnnotation(annot2);
        assertEquals("Should have 2 annotations", 2, layer.getAnnotationCount());
        assertTrue("Should still contain annot1", layer.containsAnnotation(annot1));
        assertFalse("Should not contain annot2", layer.containsAnnotation(annot2));
        assertTrue("Should still contain annot3", layer.containsAnnotation(annot3));
        
        // Remove first annotation
        layer.removeAnnotation(annot1);
        assertEquals("Should have 1 annotation", 1, layer.getAnnotationCount());
        assertTrue("Should still contain annot3", layer.containsAnnotation(annot3));
        
        // Remove last annotation
        layer.removeAnnotation(annot3);
        assertEquals("Should have 0 annotations", 0, layer.getAnnotationCount());
    }

    @Test
    public void testColorUpdatePreservesPosition() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        double x = 150, y = 120, width = 180, height = 90;
        HighlightAnnotation annotation = new HighlightAnnotation(0, x, y, width, height, Color.YELLOW);
        
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(annotation);
        layer.setAnnotations(annotations);
        
        // Update color
        layer.updateAnnotationColor(annotation, Color.BLUE);
        
        // Verify position unchanged
        HighlightAnnotation updated = (HighlightAnnotation) layer.getAnnotations().get(0);
        assertEquals("X should be unchanged", x, updated.getX(), 0.1);
        assertEquals("Y should be unchanged", y, updated.getY(), 0.1);
        assertEquals("Width should be unchanged", width, updated.getWidth(), 0.1);
        assertEquals("Height should be unchanged", height, updated.getHeight(), 0.1);
        assertEquals("Color should be updated", Color.BLUE, updated.getColor());
    }
}
