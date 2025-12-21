package org.pdflite.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PDFSplitService.
 */
class PDFSplitServiceTest {

    private PDFSplitService service;

    @BeforeEach
    void setUp() {
        service = new PDFSplitService();
    }

    @Test
    void testPageRangeRecord() {
        PDFSplitService.PageRange range = new PDFSplitService.PageRange(1, 5, "output.pdf");
        
        assertEquals(1, range.startPage());
        assertEquals(5, range.endPage());
        assertEquals("output.pdf", range.outputFileName());
        assertTrue(range.toString().contains("Pages 1-5"));
    }

    @Test
    void testGetPageCountWithNull() {
        assertThrows(IllegalArgumentException.class, () -> service.getPageCount(null));
    }

    @Test
    void testGetPageCountWithNonExistentFile() {
        File nonExistent = new File("non-existent.pdf");
        assertThrows(IllegalArgumentException.class, () -> service.getPageCount(nonExistent));
    }

    @Test
    void testIsValidPageRangeWithNonExistentFile() {
        File nonExistent = new File("non-existent.pdf");
        // The method throws IllegalArgumentException for invalid files
        assertThrows(IllegalArgumentException.class, () -> service.isValidPageRange(nonExistent, 1, 5));
    }
}
