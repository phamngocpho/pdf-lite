package org.pdflite.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DrawingTool enum.
 */
class DrawingToolTest {

    @Test
    void testAllDrawingToolsExist() {
        DrawingTool[] tools = DrawingTool.values();
        assertEquals(4, tools.length);
    }

    @Test
    void testNoneTool() {
        DrawingTool tool = DrawingTool.NONE;
        assertNotNull(tool);
        assertEquals("NONE", tool.name());
    }

    @Test
    void testRectangleTool() {
        DrawingTool tool = DrawingTool.RECTANGLE;
        assertNotNull(tool);
        assertEquals("RECTANGLE", tool.name());
    }

    @Test
    void testCircleTool() {
        DrawingTool tool = DrawingTool.CIRCLE;
        assertNotNull(tool);
        assertEquals("CIRCLE", tool.name());
    }

    @Test
    void testArrowTool() {
        DrawingTool tool = DrawingTool.ARROW;
        assertNotNull(tool);
        assertEquals("ARROW", tool.name());
    }

    @Test
    void testValueOf() {
        assertEquals(DrawingTool.NONE, DrawingTool.valueOf("NONE"));
        assertEquals(DrawingTool.RECTANGLE, DrawingTool.valueOf("RECTANGLE"));
        assertEquals(DrawingTool.CIRCLE, DrawingTool.valueOf("CIRCLE"));
        assertEquals(DrawingTool.ARROW, DrawingTool.valueOf("ARROW"));
    }

    @Test
    void testValueOfInvalid() {
        assertThrows(IllegalArgumentException.class, () -> DrawingTool.valueOf("INVALID"));
    }
}
