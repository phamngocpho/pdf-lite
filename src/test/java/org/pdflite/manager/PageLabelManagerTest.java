package org.pdflite.manager;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pdflite.model.PDFDocument;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class PageLabelManagerTest {

    @TempDir
    Path tempDir;

    private PDDocument pdDocument;
    private PDFDocument pdfDocument;
    private PageLabelManager manager;

    @BeforeEach
    void setUp() {
        pdDocument = new PDDocument();
        for (int i = 0; i < 30; i++) {
            pdDocument.addPage(new PDPage());
        }
        pdfDocument = new PDFDocument(pdDocument, tempDir.resolve("labels.pdf").toFile());
        manager = new PageLabelManager();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (pdDocument != null) {
            pdDocument.close();
        }
    }

    @Test
    void getPageLabelDefaultsToPhysicalPageNumber() {
        assertEquals("1", manager.getPageLabel(pdfDocument, 0));
        assertEquals("30", manager.getPageLabel(pdfDocument, 29));
    }

    @Test
    void applyCustomRuleSupportsRomanLettersAndPrefixes() {
        manager.applyCustomRule(pdfDocument, 1, PageLabelManager.NumberingStyle.ROMAN_LOWER, "", 1);
        manager.applyCustomRule(pdfDocument, 4, PageLabelManager.NumberingStyle.LETTER_LOWER, "", 1);
        manager.applyCustomRule(pdfDocument, 7, PageLabelManager.NumberingStyle.DECIMAL, "Chapter-", 1);

        assertEquals("i", manager.getPageLabel(pdfDocument, 0));
        assertEquals("iii", manager.getPageLabel(pdfDocument, 2));
        assertEquals("a", manager.getPageLabel(pdfDocument, 3));
        assertEquals("c", manager.getPageLabel(pdfDocument, 5));
        assertEquals("Chapter-1", manager.getPageLabel(pdfDocument, 6));
        assertEquals("Chapter-2", manager.getPageLabel(pdfDocument, 7));
    }

    @Test
    void resolvePageIndexAcceptsPageLabelsAndPhysicalNumbers() {
        manager.applyCustomRule(pdfDocument, 1, PageLabelManager.NumberingStyle.ROMAN_LOWER, "", 1);
        manager.applyCustomRule(pdfDocument, 4, PageLabelManager.NumberingStyle.DECIMAL, "Intro-", 1);

        assertEquals(OptionalInt.of(1), manager.resolvePageIndex(pdfDocument, "ii"));
        assertEquals(OptionalInt.of(3), manager.resolvePageIndex(pdfDocument, "Intro-1"));
        assertEquals(OptionalInt.of(4), manager.resolvePageIndex(pdfDocument, "5"));
        assertTrue(manager.resolvePageIndex(pdfDocument, "missing").isEmpty());
    }

    @Test
    void pageLabelsAreStoredInPdfCatalog() throws IOException {
        manager.applyCustomRule(pdfDocument, 1, PageLabelManager.NumberingStyle.LETTER_UPPER, "Intro-", 1);
        File savedFile = tempDir.resolve("saved-labels.pdf").toFile();
        pdDocument.save(savedFile);

        try (PDDocument loadedDocument = Loader.loadPDF(savedFile)) {
            PDFDocument reloaded = new PDFDocument(loadedDocument, savedFile);
            assertEquals("Intro-A", manager.getPageLabel(reloaded, 0));
            assertEquals("Intro-C", manager.getPageLabel(reloaded, 2));
        }
    }

    @Test
    void resetToDefaultRestoresDecimalLabels() {
        manager.applyCustomRule(pdfDocument, 1, PageLabelManager.NumberingStyle.ROMAN_LOWER, "", 1);
        manager.resetToDefault(pdfDocument);

        assertEquals("1", manager.getPageLabel(pdfDocument, 0));
        assertEquals("30", manager.getPageLabel(pdfDocument, 29));
        assertTrue(pdfDocument.hasUnsavedEdits());
    }
}
