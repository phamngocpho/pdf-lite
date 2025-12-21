package org.pdflite.model;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TextEdit.
 */
class TextEditTest {

    @Test
    void testTextEditCreation() {
        TextRegion region = new TextRegion(0, 100, 150, 200, 50, "test text", Collections.emptyList());
        TextEdit edit = new TextEdit(
            0, 
            System.currentTimeMillis(), 
            "Test edit", 
            region, 
            "old", 
            "new"
        );
        
        assertEquals(0, edit.pageIndex());
        assertTrue(edit.timestamp() > 0);
        assertEquals("Test edit", edit.description());
        assertEquals(region, edit.region());
        assertEquals("old", edit.oldText());
        assertEquals("new", edit.newText());
    }

    @Test
    void testTextEditCreateMethod() {
        TextRegion region = new TextRegion(1, 50, 75, 150, 30, "original text", Collections.emptyList());
        TextEdit edit = TextEdit.create(1, region, "original", "modified");
        
        assertEquals(1, edit.pageIndex());
        assertTrue(edit.timestamp() > 0);
        assertNotNull(edit.description());
        assertTrue(edit.description().contains("page 2"));
        assertTrue(edit.description().contains("original"));
        assertTrue(edit.description().contains("modified"));
        assertEquals(region, edit.region());
        assertEquals("original", edit.oldText());
        assertEquals("modified", edit.newText());
    }

    @Test
    void testTextEditWithEmptyStrings() {
        TextRegion region = new TextRegion(0, 0, 0, 100, 20, "new text", Collections.emptyList());
        TextEdit edit = TextEdit.create(0, region, "", "new text");
        
        assertEquals("", edit.oldText());
        assertEquals("new text", edit.newText());
    }

    @Test
    void testTextEditTimestamp() {
        long before = System.currentTimeMillis();
        TextRegion region = new TextRegion(0, 0, 0, 100, 20, "text", Collections.emptyList());
        TextEdit edit = TextEdit.create(0, region, "a", "b");
        long after = System.currentTimeMillis();
        
        assertTrue(edit.timestamp() >= before);
        assertTrue(edit.timestamp() <= after);
    }

    @Test
    void testTextEditDescription() {
        TextRegion region = new TextRegion(5, 0, 0, 100, 20, "Hello World", Collections.emptyList());
        TextEdit edit = TextEdit.create(5, region, "Hello", "World");
        
        String desc = edit.description();
        assertTrue(desc.contains("page 6")); // 5 + 1
        assertTrue(desc.contains("Hello"));
        assertTrue(desc.contains("World"));
    }
}
