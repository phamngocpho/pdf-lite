package org.pdflite.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Bookmark model.
 */
class BookmarkTest {

    @Test
    void testBookmarkCreation() {
        Bookmark bookmark = new Bookmark(5, "Important Page");
        
        assertEquals(5, bookmark.getPageNumber());
        assertEquals("Important Page", bookmark.getTitle());
        assertNotNull(bookmark.getCreatedAt());
        assertNull(bookmark.getThumbnailPath());
    }

    @Test
    void testBookmarkWithAllFields() {
        LocalDateTime now = LocalDateTime.now();
        Bookmark bookmark = new Bookmark(10, "Test", now, "/path/to/thumb.png");
        
        assertEquals(10, bookmark.getPageNumber());
        assertEquals("Test", bookmark.getTitle());
        assertEquals(now, bookmark.getCreatedAt());
        assertEquals("/path/to/thumb.png", bookmark.getThumbnailPath());
    }

    @Test
    void testSetTitle() {
        Bookmark bookmark = new Bookmark(0, "Original");
        
        bookmark.setTitle("Updated");
        
        assertEquals("Updated", bookmark.getTitle());
    }

    @Test
    void testSetThumbnailPath() {
        Bookmark bookmark = new Bookmark(0, "Test");
        
        bookmark.setThumbnailPath("/new/path.png");
        
        assertEquals("/new/path.png", bookmark.getThumbnailPath());
    }

    @Test
    void testEquals() {
        Bookmark bookmark1 = new Bookmark(5, "Page 5");
        Bookmark bookmark2 = new Bookmark(5, "Different Title");
        Bookmark bookmark3 = new Bookmark(6, "Page 6");
        
        assertEquals(bookmark1, bookmark2); // Same page number
        assertNotEquals(bookmark1, bookmark3); // Different page number
    }

    @Test
    void testHashCode() {
        Bookmark bookmark1 = new Bookmark(5, "Page 5");
        Bookmark bookmark2 = new Bookmark(5, "Different Title");
        
        assertEquals(bookmark1.hashCode(), bookmark2.hashCode());
    }

    @Test
    void testToString() {
        Bookmark bookmark = new Bookmark(5, "Test Page");
        
        String str = bookmark.toString();
        
        assertTrue(str.contains("pageNumber=5"));
        assertTrue(str.contains("title='Test Page'"));
        assertTrue(str.contains("createdAt="));
    }

    @Test
    void testCreatedAtIsAutomatic() {
        LocalDateTime before = LocalDateTime.now();
        Bookmark bookmark = new Bookmark(0, "Test");
        LocalDateTime after = LocalDateTime.now();
        
        assertTrue(bookmark.getCreatedAt().isAfter(before.minusSeconds(1)));
        assertTrue(bookmark.getCreatedAt().isBefore(after.plusSeconds(1)));
    }
}
