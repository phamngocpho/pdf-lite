package org.pdflite.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Constants.
 */
class ConstantsTest {

    @Test
    void testApplicationConstants() {
        assertEquals("PDF Lite", Constants.APP_NAME);
        assertEquals("1.0.0", Constants.APP_VERSION);
    }

    @Test
    void testDefaultValues() {
        assertEquals(150f, Constants.DEFAULT_DPI);
        assertEquals(1.0, Constants.DEFAULT_ZOOM);
    }

    @Test
    void testZoomConstants() {
        assertEquals(0.25, Constants.MIN_ZOOM);
        assertEquals(5.0, Constants.MAX_ZOOM);
        assertEquals(0.25, Constants.ZOOM_STEP);
        
        // Verify zoom range is valid
        assertTrue(Constants.MIN_ZOOM < Constants.MAX_ZOOM);
        assertTrue(Constants.DEFAULT_ZOOM >= Constants.MIN_ZOOM);
        assertTrue(Constants.DEFAULT_ZOOM <= Constants.MAX_ZOOM);
    }

    @Test
    void testFileExtensionConstants() {
        assertEquals("*.pdf", Constants.PDF_EXTENSION);
        assertEquals("PDF Files", Constants.PDF_DESCRIPTION);
    }

    @Test
    void testSearchConstants() {
        assertEquals(50, Constants.SEARCH_CONTEXT_LENGTH);
        assertEquals("#FFFF00", Constants.SEARCH_HIGHLIGHT_COLOR);
        assertEquals(0.4, Constants.SEARCH_HIGHLIGHT_OPACITY);
    }

    @Test
    void testRenderScaleConstants() {
        assertTrue(Constants.LOW_RENDER_SCALE > 0);
        assertTrue(Constants.HIGH_RENDER_SCALE > 0);
        assertTrue(Constants.HIGH_RENDER_SCALE > Constants.LOW_RENDER_SCALE);
    }

    @Test
    void testMaxCanvasSize() {
        assertEquals(4096.0, Constants.MAX_CANVAS_SIZE);
        assertTrue(Constants.MAX_CANVAS_SIZE > 0);
    }

    @Test
    void testTextSelectionThreshold() {
        assertEquals(0.2f, Constants.TEXT_SELECTION_SPACE_THRESHOLD);
        assertTrue(Constants.TEXT_SELECTION_SPACE_THRESHOLD > 0);
        assertTrue(Constants.TEXT_SELECTION_SPACE_THRESHOLD < 1);
    }

    @Test
    void testCannotInstantiate() {
        assertThrows(Exception.class, () -> {
            // Use reflection to try to instantiate
            java.lang.reflect.Constructor<Constants> constructor = 
                Constants.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        });
    }
}
