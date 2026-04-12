package org.pdflite.manager;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PageInfoManager.
 * Note: JavaFX components cannot be mocked, so we test with null components
 * or verify the logic without actual UI updates.
 */
class PageInfoManagerTest {

    @TempDir
    Path tempDir;

    private PDDocument pdDocument;
    private File testFile;
    private PDFDocument pdfDocument;

    @BeforeEach
    void setUp() throws IOException {
        pdDocument = new PDDocument();
        pdDocument.addPage(new PDPage());
        pdDocument.addPage(new PDPage());
        pdDocument.addPage(new PDPage());
        
        testFile = tempDir.resolve("test.pdf").toFile();
        pdfDocument = new PDFDocument(pdDocument, testFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (pdDocument != null) {
            pdDocument.close();
        }
    }

    @Test
    void testPageInfoManagerCreation() {
        PageInfoManager manager = new PageInfoManager(null, null, null, null);
        assertNotNull(manager);
    }

    @Test
    void testUpdatePageInfoWithNullDocument() {
        PageInfoManager manager = new PageInfoManager(null, null, null, null);
        
        // Should not throw exception
        assertDoesNotThrow(() -> manager.updatePageInfo(null));
    }

    @Test
    void testResetPageFieldToCurrentPageWithNullDocument() {
        PageInfoManager manager = new PageInfoManager(null, null, null, null);
        
        // Should not throw exception
        assertDoesNotThrow(() -> manager.resetPageFieldToCurrentPage(null));
    }

    @Test
    void testGetPageNumberFromFieldWithNullField() {
        PageInfoManager manager = new PageInfoManager(null, null, null, null);
        
        assertEquals(-1, manager.getPageNumberFromField());
    }
}
