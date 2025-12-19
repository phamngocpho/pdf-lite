package org.pdflite.view;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.paint.Color;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.pdflite.model.Annotation;
import org.pdflite.model.HighlightAnnotation;
import org.pdflite.view.AnnotationLayer.TextRegionHighlight;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Property-based and unit tests for AnnotationLayer.
 * Tests annotation layer creation, mouse event capture, annotation management, and redraw functionality.
 */
@RunWith(JUnitQuickcheck.class)
public class AnnotationLayerTest {

    private AnnotationLayer annotationLayer;

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
     * Feature: native-pdf-editing, Property 5: Annotation Layer Creation
     * 
     * For any valid canvas dimensions, creating an AnnotationLayer should produce
     * a non-null Canvas with the specified dimensions (or clamped to MAX_CANVAS_SIZE).
     * 
     * Validates: Requirements 2.1
     */
    @Property(trials = 100)
    public void property5_annotationLayerCreation(int width, int height) {
        // Constrain to reasonable values (1 to 10000)
        width = Math.max(1, Math.min(Math.abs(width), 10000));
        height = Math.max(1, Math.min(Math.abs(height), 10000));

        // Create annotation layer
        AnnotationLayer layer = new AnnotationLayer(width, height);

        // Verify layer is created
        assertNotNull("AnnotationLayer should not be null", layer);
        
        // Verify dimensions (may be clamped to MAX_CANVAS_SIZE)
        double expectedWidth = Math.min(width, org.pdflite.util.Constants.MAX_CANVAS_SIZE);
        double expectedHeight = Math.min(height, org.pdflite.util.Constants.MAX_CANVAS_SIZE);
        
        assertEquals("Width should match (or be clamped)", expectedWidth, layer.getWidth(), 0.1);
        assertEquals("Height should match (or be clamped)", expectedHeight, layer.getHeight(), 0.1);
        
        // Verify initial state
        assertEquals("Initial mode should be NONE", AnnotationLayer.AnnotationMode.NONE, layer.getCurrentMode());
        assertNotNull("Annotations list should not be null", layer.getAnnotations());
        assertTrue("Annotations list should be empty initially", layer.getAnnotations().isEmpty());
    }

    /**
     * Feature: native-pdf-editing, Property 6: Mouse Event Capture
     * 
     * For any annotation layer, mouse event handlers should be set up and
     * the layer should be able to capture mouse events.
     * 
     * Validates: Requirements 2.2
     */
    @Property(trials = 100)
    public void property6_mouseEventCapture(int width, int height) {
        // Constrain to reasonable values
        width = Math.max(100, Math.min(Math.abs(width), 1000));
        height = Math.max(100, Math.min(Math.abs(height), 1000));

        // Create annotation layer
        AnnotationLayer layer = new AnnotationLayer(width, height);

        // Verify mouse event handlers are set up
        assertNotNull("Mouse pressed handler should be set", layer.getOnMousePressed());
        assertNotNull("Mouse dragged handler should be set", layer.getOnMouseDragged());
        assertNotNull("Mouse released handler should be set", layer.getOnMouseReleased());
        
        // Verify layer can receive mouse events (handlers are not null)
        assertTrue("Layer should have mouse event handlers", 
                   layer.getOnMousePressed() != null && 
                   layer.getOnMouseDragged() != null && 
                   layer.getOnMouseReleased() != null);
    }

    /**
     * Feature: native-pdf-editing, Property 7: Annotation Collection Management
     * 
     * For any annotation added to an AnnotationLayer, the annotation should
     * appear in the layer's annotation collection.
     * 
     * Validates: Requirements 2.3
     */
    @Property(trials = 100)
    public void property7_annotationCollectionManagement(double x, double y, double width, double height) {
        // Constrain to reasonable values
        x = Math.max(0, Math.min(Math.abs(x), 500));
        y = Math.max(0, Math.min(Math.abs(y), 500));
        width = Math.max(10, Math.min(Math.abs(width), 200));
        height = Math.max(10, Math.min(Math.abs(height), 200));

        // Create annotation layer
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        // Create and add annotation
        HighlightAnnotation annotation = new HighlightAnnotation(0, x, y, width, height, Color.YELLOW);
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(annotation);
        
        // Set annotations
        layer.setAnnotations(annotations);
        
        // Verify annotation is in collection
        List<Annotation> retrievedAnnotations = layer.getAnnotations();
        assertNotNull("Annotations list should not be null", retrievedAnnotations);
        assertEquals("Should have one annotation", 1, retrievedAnnotations.size());
        assertTrue("Annotation should be in collection", retrievedAnnotations.contains(annotation));
        
        // Verify annotation properties
        Annotation retrieved = retrievedAnnotations.get(0);
        assertTrue("Retrieved annotation should be HighlightAnnotation", retrieved instanceof HighlightAnnotation);
        HighlightAnnotation highlight = (HighlightAnnotation) retrieved;
        assertEquals("X coordinate should match", x, highlight.getX(), 0.1);
        assertEquals("Y coordinate should match", y, highlight.getY(), 0.1);
        assertEquals("Width should match", width, highlight.getWidth(), 0.1);
        assertEquals("Height should match", height, highlight.getHeight(), 0.1);
    }

    /**
     * Feature: native-pdf-editing, Property 8: Annotation Redraw
     * 
     * For any AnnotationLayer with annotations, calling redraw should not throw
     * exceptions and should maintain the annotation collection.
     * 
     * Validates: Requirements 2.4
     */
    @Property(trials = 100)
    public void property8_annotationRedraw(int annotationCount) {
        // Constrain to reasonable values (0 to 10 annotations)
        annotationCount = Math.max(0, Math.min(Math.abs(annotationCount), 10));

        // Create annotation layer
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        // Create multiple annotations
        List<Annotation> annotations = new ArrayList<>();
        for (int i = 0; i < annotationCount; i++) {
            double x = 50 + (i * 60);
            double y = 50 + (i * 40);
            HighlightAnnotation annotation = new HighlightAnnotation(0, x, y, 50, 30, Color.YELLOW);
            annotations.add(annotation);
        }
        
        // Set annotations
        layer.setAnnotations(annotations);
        
        // Verify annotations are set
        assertEquals("Should have correct number of annotations", annotationCount, layer.getAnnotations().size());
        
        // Call redraw - should not throw exception
        try {
            layer.redraw();
        } catch (Exception e) {
            fail("Redraw should not throw exception: " + e.getMessage());
        }
        
        // Verify annotations are still present after redraw
        assertEquals("Annotations should persist after redraw", annotationCount, layer.getAnnotations().size());
        
        // Verify annotation properties are unchanged
        List<Annotation> afterRedraw = layer.getAnnotations();
        for (int i = 0; i < annotationCount; i++) {
            Annotation original = annotations.get(i);
            Annotation after = afterRedraw.get(i);
            
            assertTrue("Annotation should be HighlightAnnotation", after instanceof HighlightAnnotation);
            HighlightAnnotation origHighlight = (HighlightAnnotation) original;
            HighlightAnnotation afterHighlight = (HighlightAnnotation) after;
            
            assertEquals("X should be unchanged", origHighlight.getX(), afterHighlight.getX(), 0.1);
            assertEquals("Y should be unchanged", origHighlight.getY(), afterHighlight.getY(), 0.1);
            assertEquals("Width should be unchanged", origHighlight.getWidth(), afterHighlight.getWidth(), 0.1);
            assertEquals("Height should be unchanged", origHighlight.getHeight(), afterHighlight.getHeight(), 0.1);
        }
    }

    // ==================== UNIT TESTS ====================

    @Test
    public void testAnnotationLayerCreation() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        assertNotNull("AnnotationLayer should not be null", layer);
        assertEquals("Width should be 800", 800, layer.getWidth(), 0.1);
        assertEquals("Height should be 600", 600, layer.getHeight(), 0.1);
    }

    @Test
    public void testAnnotationLayerClamping() {
        // Test that very large dimensions are clamped
        double largeSize = org.pdflite.util.Constants.MAX_CANVAS_SIZE + 1000;
        AnnotationLayer layer = new AnnotationLayer(largeSize, largeSize);
        
        assertNotNull("AnnotationLayer should not be null", layer);
        assertTrue("Width should be clamped", layer.getWidth() <= org.pdflite.util.Constants.MAX_CANVAS_SIZE);
        assertTrue("Height should be clamped", layer.getHeight() <= org.pdflite.util.Constants.MAX_CANVAS_SIZE);
    }

    @Test
    public void testSetAnnotationMode() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        layer.setAnnotationMode(AnnotationLayer.AnnotationMode.HIGHLIGHT);
        assertEquals("Mode should be HIGHLIGHT", AnnotationLayer.AnnotationMode.HIGHLIGHT, layer.getCurrentMode());
        
        layer.setAnnotationMode(AnnotationLayer.AnnotationMode.TEXT_SELECT_EDIT);
        assertEquals("Mode should be TEXT_SELECT_EDIT", AnnotationLayer.AnnotationMode.TEXT_SELECT_EDIT, layer.getCurrentMode());
        
        layer.setAnnotationMode(AnnotationLayer.AnnotationMode.NONE);
        assertEquals("Mode should be NONE", AnnotationLayer.AnnotationMode.NONE, layer.getCurrentMode());
    }

    @Test
    public void testClearAnnotations() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        // Add annotations
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(new HighlightAnnotation(0, 10, 10, 50, 30, Color.YELLOW));
        annotations.add(new HighlightAnnotation(0, 100, 100, 60, 40, Color.GREEN));
        layer.setAnnotations(annotations);
        
        assertEquals("Should have 2 annotations", 2, layer.getAnnotations().size());
        
        // Clear annotations
        layer.clearAnnotations();
        
        assertEquals("Should have 0 annotations after clear", 0, layer.getAnnotations().size());
    }

    @Test
    public void testTextRegionHighlights() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        // Create text region highlights
        List<TextRegionHighlight> regions = new ArrayList<>();
        regions.add(new TextRegionHighlight(10, 10, 100, 20, "Hello"));
        regions.add(new TextRegionHighlight(10, 40, 150, 20, "World"));
        
        // Set text region highlights
        layer.setTextRegionHighlights(regions);
        
        // Find text region at coordinates
        TextRegionHighlight found = layer.findTextRegionAt(50, 15);
        assertNotNull("Should find text region", found);
        assertEquals("Should find 'Hello' region", "Hello", found.text());
        
        // Test coordinates outside regions
        TextRegionHighlight notFound = layer.findTextRegionAt(500, 500);
        assertNull("Should not find text region", notFound);
    }

    @Test
    public void testSelectedTextRegion() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        // Initially no selection
        assertNull("Initially no selected region", layer.getSelectedTextRegion());
        
        // Set selected region
        TextRegionHighlight region = new TextRegionHighlight(10, 10, 100, 20, "Selected");
        layer.setSelectedTextRegion(region);
        
        assertEquals("Should have selected region", region, layer.getSelectedTextRegion());
        
        // Clear selection
        layer.setSelectedTextRegion(null);
        assertNull("Should have no selected region", layer.getSelectedTextRegion());
    }

    @Test
    public void testClearTextRegionHighlights() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        // Set text region highlights
        List<TextRegionHighlight> regions = new ArrayList<>();
        regions.add(new TextRegionHighlight(10, 10, 100, 20, "Test"));
        layer.setTextRegionHighlights(regions);
        
        // Set selected region
        layer.setSelectedTextRegion(regions.get(0));
        assertNotNull("Should have selected region", layer.getSelectedTextRegion());
        
        // Clear all text region highlights
        layer.clearTextRegionHighlights();
        
        assertNull("Should have no selected region after clear", layer.getSelectedTextRegion());
        assertNull("Should not find any text regions", layer.findTextRegionAt(50, 15));
    }

    @Test
    public void testPageIndexAndNumber() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        layer.setPageIndex(5);
        layer.setPageNumber(6); // Page number is 1-based
        
        assertEquals("Page number should be 6", 6, layer.getPageNumber());
    }

    @Test
    public void testSetScale() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        layer.setScale(1.5);
        // Scale is used internally for rendering, no direct getter
        // Just verify no exception is thrown
    }

    @Test
    public void testSetDrawingColor() {
        AnnotationLayer layer = new AnnotationLayer(800, 600);
        
        layer.setDrawingColor(Color.RED);
        // Color is used internally for new annotations
        // Just verify no exception is thrown
    }
}
