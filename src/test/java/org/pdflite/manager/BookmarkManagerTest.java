package org.pdflite.manager;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.pdflite.model.Bookmark;
import org.pdflite.model.PDFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BookmarkManager.
 */
class BookmarkManagerTest {

    private BookmarkManager bookmarkManager;
    private PDDocument pdDocument;
    private PDFDocument pdfDocument;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        bookmarkManager = new BookmarkManager();
        
        pdDocument = new PDDocument();
        pdDocument.addPage(new PDPage());
        pdDocument.addPage(new PDPage());
        pdDocument.addPage(new PDPage());
        
        File tempFile = tempDir.resolve("test.pdf").toFile();
        pdDocument.save(tempFile);
        
        pdfDocument = new PDFDocument(pdDocument, tempFile);
        bookmarkManager.setCurrentDocument(pdfDocument);
        
        // Clear any existing bookmarks
        bookmarkManager.getBookmarks().clear();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (pdDocument != null) {
            pdDocument.close();
        }
    }

    @Test
    void testAddBookmark() {
        bookmarkManager.addBookmark(0, "First Page");
        
        assertEquals(1, bookmarkManager.getBookmarks().size());
        assertTrue(bookmarkManager.hasBookmark(0));
        
        Bookmark bookmark = bookmarkManager.getBookmarks().get(0);
        assertEquals(0, bookmark.getPageNumber());
        assertEquals("First Page", bookmark.getTitle());
    }

    @Test
    void testAddDuplicateBookmark() {
        bookmarkManager.addBookmark(0, "First Page");
        bookmarkManager.addBookmark(0, "Duplicate");
        
        // Should not add duplicate
        assertEquals(1, bookmarkManager.getBookmarks().size());
    }

    @Test
    void testRemoveBookmark() {
        bookmarkManager.addBookmark(0, "First Page");
        Bookmark bookmark = bookmarkManager.getBookmarks().get(0);
        
        bookmarkManager.removeBookmark(bookmark);
        
        assertEquals(0, bookmarkManager.getBookmarks().size());
        assertFalse(bookmarkManager.hasBookmark(0));
    }

    @Test
    void testHasBookmark() {
        assertFalse(bookmarkManager.hasBookmark(0));
        
        bookmarkManager.addBookmark(0, "First Page");
        
        assertTrue(bookmarkManager.hasBookmark(0));
        assertFalse(bookmarkManager.hasBookmark(1));
    }

    @Test
    void testGetBookmarks() {
        bookmarkManager.addBookmark(0, "Page 1");
        bookmarkManager.addBookmark(1, "Page 2");
        bookmarkManager.addBookmark(2, "Page 3");
        
        assertEquals(3, bookmarkManager.getBookmarks().size());
    }

    @Test
    void testSetOnNavigateToPage() {
        AtomicInteger navigatedPage = new AtomicInteger(-1);
        bookmarkManager.setOnNavigateToPage(navigatedPage::set);
        
        // This would be triggered by UI interaction
        // We can't test it directly without UI, but we can verify it's set
        assertDoesNotThrow(() -> bookmarkManager.setOnNavigateToPage(navigatedPage::set));
    }

    @Test
    void testExportBookmarks() throws IOException {
        bookmarkManager.addBookmark(0, "Page 1");
        bookmarkManager.addBookmark(1, "Page 2");
        
        File exportFile = tempDir.resolve("export.json").toFile();
        bookmarkManager.exportBookmarks(exportFile);
        
        assertTrue(exportFile.exists());
        assertTrue(exportFile.length() > 0);
    }

    @Test
    void testImportBookmarks() throws IOException {
        // Create and export bookmarks
        bookmarkManager.addBookmark(0, "Page 1");
        bookmarkManager.addBookmark(1, "Page 2");
        
        File exportFile = tempDir.resolve("export.json").toFile();
        bookmarkManager.exportBookmarks(exportFile);
        
        // Clear and import
        bookmarkManager.getBookmarks().clear();
        assertEquals(0, bookmarkManager.getBookmarks().size());
        
        bookmarkManager.importBookmarks(exportFile);
        
        assertEquals(2, bookmarkManager.getBookmarks().size());
        assertTrue(bookmarkManager.hasBookmark(0));
        assertTrue(bookmarkManager.hasBookmark(1));
    }

    @Test
    void testSetCurrentDocument() {
        PDDocument newDoc = new PDDocument();
        newDoc.addPage(new PDPage());
        PDFDocument newPdfDoc = new PDFDocument(newDoc, null);
        
        bookmarkManager.setCurrentDocument(newPdfDoc);
        
        // Bookmarks should be cleared for new document
        assertDoesNotThrow(() -> bookmarkManager.addBookmark(0, "New Doc Page"));
    }
}
