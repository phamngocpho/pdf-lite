package org.pdflite.manager;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.pdflite.model.PDFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PageDuplicationManager.
 */
class PageDuplicationManagerTest {

    private PageDuplicationManager manager;
    private PDDocument pdDocument;
    private PDFDocument pdfDocument;

    @BeforeEach
    void setUp() {
        manager = new PageDuplicationManager();
        pdDocument = new PDDocument();
        pdfDocument = new PDFDocument(pdDocument, null);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (pdDocument != null) {
            pdDocument.close();
        }
    }

    @Test
    void testDuplicatePageWithNullDocument() {
        boolean result = manager.duplicatePage(null, 0, 1, 1);
        assertFalse(result, "Should return false for null document");
    }

    @Test
    void testDuplicatePageWithInvalidSourceIndex() {
        pdDocument.addPage(new PDPage());
        
        boolean result = manager.duplicatePage(pdfDocument, 5, 1, 1);
        assertFalse(result, "Should return false for invalid source index");
    }

    @Test
    void testDuplicatePageWithInvalidInsertPosition() {
        pdDocument.addPage(new PDPage());
        
        boolean result = manager.duplicatePage(pdfDocument, 0, 10, 1);
        assertFalse(result, "Should return false for invalid insert position");
    }

    @Test
    void testDuplicatePageWithInvalidNumberOfCopies() {
        pdDocument.addPage(new PDPage());
        
        boolean result = manager.duplicatePage(pdfDocument, 0, 1, 0);
        assertFalse(result, "Should return false for invalid number of copies");
    }

    @Test
    void testDuplicateSinglePage() {
        pdDocument.addPage(new PDPage(PDRectangle.A4));
        
        boolean result = manager.duplicatePage(pdfDocument, 0, 1, 1);
        assertTrue(result, "Should successfully duplicate page");
        assertEquals(2, pdDocument.getNumberOfPages(), "Should have 2 pages after duplication");
        assertTrue(pdfDocument.hasUnsavedEdits(), "Document should be marked as modified");
    }

    @Test
    void testDuplicateMultipleCopies() {
        pdDocument.addPage(new PDPage(PDRectangle.A4));
        
        boolean result = manager.duplicatePage(pdfDocument, 0, 1, 3);
        assertTrue(result, "Should successfully duplicate page 3 times");
        assertEquals(4, pdDocument.getNumberOfPages(), "Should have 4 pages after duplication");
    }

    @Test
    void testDuplicatePageAtBeginning() {
        pdDocument.addPage(new PDPage(PDRectangle.A4));
        pdDocument.addPage(new PDPage(PDRectangle.A5));
        
        boolean result = manager.duplicatePage(pdfDocument, 1, 0, 1);
        assertTrue(result, "Should successfully duplicate page at beginning");
        assertEquals(3, pdDocument.getNumberOfPages(), "Should have 3 pages");
    }

    @Test
    void testDuplicatePageAtEnd() {
        pdDocument.addPage(new PDPage(PDRectangle.A4));
        pdDocument.addPage(new PDPage(PDRectangle.A5));
        
        boolean result = manager.duplicatePage(pdfDocument, 0, 2, 1);
        assertTrue(result, "Should successfully duplicate page at end");
        assertEquals(3, pdDocument.getNumberOfPages(), "Should have 3 pages");
    }

    @Test
    void testDuplicateMiddlePage() {
        pdDocument.addPage(new PDPage(PDRectangle.A4));
        pdDocument.addPage(new PDPage(PDRectangle.A5));
        pdDocument.addPage(new PDPage(PDRectangle.LETTER));
        
        boolean result = manager.duplicatePage(pdfDocument, 1, 2, 1);
        assertTrue(result, "Should successfully duplicate middle page");
        assertEquals(4, pdDocument.getNumberOfPages(), "Should have 4 pages");
    }
}
