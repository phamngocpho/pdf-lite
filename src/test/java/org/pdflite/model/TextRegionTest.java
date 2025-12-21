package org.pdflite.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TextRegion.
 */
class TextRegionTest {

    @Test
    void testTextRegionCreation() {
        TextRegion region = new TextRegion(
            0, 100.0, 150.0, 200.0, 50.0, "Test text", new ArrayList<>()
        );
        
        assertEquals(0, region.pageIndex());
        assertEquals(100.0, region.x());
        assertEquals(150.0, region.y());
        assertEquals(200.0, region.width());
        assertEquals(50.0, region.height());
        assertEquals("Test text", region.text());
        assertNotNull(region.positions());
    }

    @Test
    void testTextRegionContains() {
        TextRegion region = new TextRegion(
            0, 100.0, 100.0, 200.0, 100.0, "Text", Collections.emptyList()
        );
        
        assertTrue(region.contains(150.0, 150.0));
        assertTrue(region.contains(100.0, 100.0)); // Edge
        assertTrue(region.contains(300.0, 200.0)); // Edge
        assertFalse(region.contains(50.0, 150.0)); // Outside left
        assertFalse(region.contains(350.0, 150.0)); // Outside right
        assertFalse(region.contains(150.0, 50.0)); // Outside bottom
        assertFalse(region.contains(150.0, 250.0)); // Outside top
    }

    @Test
    void testTextRegionGetMaxX() {
        TextRegion region = new TextRegion(
            0, 100.0, 100.0, 200.0, 50.0, "Text", Collections.emptyList()
        );
        
        assertEquals(300.0, region.getMaxX());
    }

    @Test
    void testTextRegionGetMaxY() {
        TextRegion region = new TextRegion(
            0, 100.0, 100.0, 200.0, 50.0, "Text", Collections.emptyList()
        );
        
        assertEquals(150.0, region.getMaxY());
    }

    @Test
    void testTextRegionIsEmpty() {
        TextRegion empty = new TextRegion(
            0, 0, 0, 100, 20, "", Collections.emptyList()
        );
        TextRegion whitespace = new TextRegion(
            0, 0, 0, 100, 20, "   ", Collections.emptyList()
        );
        TextRegion notEmpty = new TextRegion(
            0, 0, 0, 100, 20, "Text", Collections.emptyList()
        );
        
        assertTrue(empty.isEmpty());
        assertTrue(whitespace.isEmpty());
        assertFalse(notEmpty.isEmpty());
    }

    @Test
    void testTextRegionLength() {
        TextRegion region = new TextRegion(
            0, 0, 0, 100, 20, "Hello World", Collections.emptyList()
        );
        
        assertEquals(11, region.length());
    }

    @Test
    void testTextRegionValidationNegativePageIndex() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TextRegion(-1, 0, 0, 100, 20, "Text", Collections.emptyList());
        });
    }

    @Test
    void testTextRegionValidationZeroWidth() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TextRegion(0, 0, 0, 0, 20, "Text", Collections.emptyList());
        });
    }

    @Test
    void testTextRegionValidationNegativeHeight() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TextRegion(0, 0, 0, 100, -10, "Text", Collections.emptyList());
        });
    }

    @Test
    void testTextRegionValidationNullText() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TextRegion(0, 0, 0, 100, 20, null, Collections.emptyList());
        });
    }

    @Test
    void testTextRegionValidationNullPositions() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TextRegion(0, 0, 0, 100, 20, "Text", null);
        });
    }
}
