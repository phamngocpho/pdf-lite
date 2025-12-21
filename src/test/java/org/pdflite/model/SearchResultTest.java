package org.pdflite.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SearchResult record.
 */
class SearchResultTest {

    @Test
    void testSearchResultCreation() {
        SearchResult result = new SearchResult(
            0, 10, 15, "hello", "Say ", " world", 100.0, 200.0, 50.0, 20.0
        );
        
        assertEquals(0, result.pageNumber());
        assertEquals(10, result.startIndex());
        assertEquals(15, result.endIndex());
        assertEquals("hello", result.matchedText());
        assertEquals("Say ", result.contextBefore());
        assertEquals(" world", result.contextAfter());
        assertEquals(100.0, result.x());
        assertEquals(200.0, result.y());
        assertEquals(50.0, result.width());
        assertEquals(20.0, result.height());
    }

    @Test
    void testGetFullContext() {
        SearchResult result = new SearchResult(
            0, 10, 15, "hello", "Say ", " world", 100.0, 200.0, 50.0, 20.0
        );
        
        assertEquals("Say hello world", result.getFullContext());
    }

    @Test
    void testGetDisplayText() {
        SearchResult result = new SearchResult(
            2, 10, 15, "test", "This is a ", " string", 100.0, 200.0, 50.0, 20.0
        );
        
        String displayText = result.getDisplayText();
        
        assertTrue(displayText.contains("Page 2"));
        assertTrue(displayText.contains("This is a test string"));
    }

    @Test
    void testEqualsWithSameValues() {
        SearchResult result1 = new SearchResult(
            0, 10, 15, "hello", "Say ", " world", 100.0, 200.0, 50.0, 20.0
        );
        SearchResult result2 = new SearchResult(
            0, 10, 15, "hello", "Say ", " world", 100.0, 200.0, 50.0, 20.0
        );
        
        assertEquals(result1, result2);
    }

    @Test
    void testEqualsWithDifferentPageNumber() {
        SearchResult result1 = new SearchResult(
            0, 10, 15, "hello", "Say ", " world", 100.0, 200.0, 50.0, 20.0
        );
        SearchResult result2 = new SearchResult(
            1, 10, 15, "hello", "Say ", " world", 100.0, 200.0, 50.0, 20.0
        );
        
        assertNotEquals(result1, result2);
    }

    @Test
    void testEqualsWithDifferentIndices() {
        SearchResult result1 = new SearchResult(
            0, 10, 15, "hello", "Say ", " world", 100.0, 200.0, 50.0, 20.0
        );
        SearchResult result2 = new SearchResult(
            0, 11, 16, "hello", "Say ", " world", 100.0, 200.0, 50.0, 20.0
        );
        
        assertNotEquals(result1, result2);
    }

    @Test
    void testEqualsWithDifferentText() {
        SearchResult result1 = new SearchResult(
            0, 10, 15, "hello", "Say ", " world", 100.0, 200.0, 50.0, 20.0
        );
        SearchResult result2 = new SearchResult(
            0, 10, 15, "world", "Say ", " world", 100.0, 200.0, 50.0, 20.0
        );
        
        assertNotEquals(result1, result2);
    }

    @Test
    void testEqualsWithPositionTolerance() {
        SearchResult result1 = new SearchResult(
            0, 10, 15, "hello", "Say ", " world", 100.0, 200.0, 50.0, 20.0
        );
        SearchResult result2 = new SearchResult(
            0, 10, 15, "hello", "Say ", " world", 100.5, 200.5, 50.0, 20.0
        );
        
        assertEquals(result1, result2); // Within 1 pixel tolerance
    }

    @Test
    void testEqualsWithPositionOutsideTolerance() {
        SearchResult result1 = new SearchResult(
            0, 10, 15, "hello", "Say ", " world", 100.0, 200.0, 50.0, 20.0
        );
        SearchResult result2 = new SearchResult(
            0, 10, 15, "hello", "Say ", " world", 102.0, 200.0, 50.0, 20.0
        );
        
        assertNotEquals(result1, result2); // Outside 1 pixel tolerance
    }

    @Test
    void testHashCode() {
        SearchResult result1 = new SearchResult(
            0, 10, 15, "hello", "Say ", " world", 100.0, 200.0, 50.0, 20.0
        );
        SearchResult result2 = new SearchResult(
            0, 10, 15, "hello", "Say ", " world", 100.0, 200.0, 50.0, 20.0
        );
        
        assertEquals(result1.hashCode(), result2.hashCode());
    }

    @Test
    void testToString() {
        SearchResult result = new SearchResult(
            2, 10, 15, "test", "This is a ", " string", 100.5, 200.7, 50.0, 20.0
        );
        
        String str = result.toString();
        
        assertTrue(str.contains("page=2"));
        assertTrue(str.contains("start=10"));
        assertTrue(str.contains("end=15"));
        assertTrue(str.contains("text='test'"));
        assertTrue(str.contains("100.5"));
        assertTrue(str.contains("200.7"));
    }

    @Test
    void testEmptyContext() {
        SearchResult result = new SearchResult(
            0, 10, 15, "hello", "", "", 100.0, 200.0, 50.0, 20.0
        );
        
        assertEquals("hello", result.getFullContext());
    }

    @Test
    void testNullMatchedText() {
        SearchResult result = new SearchResult(
            0, 10, 15, null, "Say ", " world", 100.0, 200.0, 50.0, 20.0
        );
        
        assertNull(result.matchedText());
    }
}
