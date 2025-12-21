package org.pdflite.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.pdflite.model.SearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SearchService.
 */
class SearchServiceTest {

    private SearchService searchService;
    private PDDocument document;

    @BeforeEach
    void setUp() throws IOException {
        searchService = new SearchService();
        document = new PDDocument();
        
        // Create a page with some text
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            contentStream.newLineAtOffset(100, 700);
            contentStream.showText("Hello World");
            contentStream.newLineAtOffset(0, -20);
            contentStream.showText("This is a test document");
            contentStream.endText();
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        if (document != null) {
            document.close();
        }
    }

    @Test
    void testSearchInDocumentCaseSensitive() throws IOException {
        List<SearchResult> results = searchService.searchInDocument(
            document, "Hello", true, false
        );
        
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(r -> r.matchedText().equals("Hello")));
    }

    @Test
    void testSearchInDocumentCaseInsensitive() throws IOException {
        List<SearchResult> results = searchService.searchInDocument(
            document, "hello", false, false
        );
        
        assertFalse(results.isEmpty());
    }

    @Test
    void testSearchInDocumentWholeWord() throws IOException {
        List<SearchResult> results = searchService.searchInDocument(
            document, "test", false, true
        );
        
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(r -> r.matchedText().toLowerCase().equals("test")));
    }

    @Test
    void testSearchInDocumentNoResults() throws IOException {
        List<SearchResult> results = searchService.searchInDocument(
            document, "nonexistent", false, false
        );
        
        assertTrue(results.isEmpty());
    }

    @Test
    void testCancelSearch() {
        assertFalse(searchService.isCancelled());
        
        searchService.cancelSearch();
        
        assertTrue(searchService.isCancelled());
    }

    @Test
    void testSearchMultiplePages() throws IOException {
        // Add another page
        PDPage page2 = new PDPage(PDRectangle.A4);
        document.addPage(page2);
        
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page2)) {
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            contentStream.newLineAtOffset(100, 700);
            contentStream.showText("Another test page");
            contentStream.endText();
        }
        
        List<SearchResult> results = searchService.searchInDocument(
            document, "test", false, false
        );
        
        // Should find "test" in both pages
        assertTrue(results.size() >= 2);
    }

    @Test
    void testSearchResultContainsPageNumber() throws IOException {
        List<SearchResult> results = searchService.searchInDocument(
            document, "Hello", false, false
        );
        
        assertFalse(results.isEmpty());
        SearchResult result = results.get(0);
        assertEquals(1, result.pageNumber());
    }

    @Test
    void testSearchResultContainsBoundingBox() throws IOException {
        List<SearchResult> results = searchService.searchInDocument(
            document, "Hello", false, false
        );
        
        assertFalse(results.isEmpty());
        SearchResult result = results.get(0);
        assertTrue(result.x() >= 0);
        assertTrue(result.y() >= 0);
        assertTrue(result.width() > 0);
        assertTrue(result.height() > 0);
    }
}
