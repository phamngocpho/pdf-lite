package org.pdflite.model;

import javafx.scene.paint.Color;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PDFDocument.
 */
class PDFDocumentTest {

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
    void testInitialState() {
        assertEquals(0, pdfDocument.getCurrentPage());
        assertEquals(1.0, pdfDocument.getZoomLevel());
        assertEquals(0, pdfDocument.getRotation());
        assertEquals(3, pdfDocument.getTotalPages());
        assertFalse(pdfDocument.hasUnsavedEdits());
    }

    @Test
    void testSetCurrentPage() {
        pdfDocument.setCurrentPage(1);
        assertEquals(1, pdfDocument.getCurrentPage());
        
        pdfDocument.setCurrentPage(2);
        assertEquals(2, pdfDocument.getCurrentPage());
    }

    @Test
    void testSetCurrentPageInvalid() {
        pdfDocument.setCurrentPage(-1);
        assertEquals(0, pdfDocument.getCurrentPage()); // Should not change
        
        pdfDocument.setCurrentPage(10);
        assertEquals(0, pdfDocument.getCurrentPage()); // Should not change
    }

    @Test
    void testSetZoomLevel() {
        pdfDocument.setZoomLevel(1.5);
        assertEquals(1.5, pdfDocument.getZoomLevel(), 0.001);
        
        pdfDocument.setZoomLevel(0.5);
        assertEquals(0.5, pdfDocument.getZoomLevel(), 0.001);
    }

    @Test
    void testSetZoomLevelClamping() {
        pdfDocument.setZoomLevel(10.0);
        assertEquals(5.0, pdfDocument.getZoomLevel(), 0.001); // Max 5.0
        
        pdfDocument.setZoomLevel(0.01);
        assertEquals(0.1, pdfDocument.getZoomLevel(), 0.001); // Min 0.1
    }

    @Test
    void testSetRotation() {
        pdfDocument.setRotation(90);
        assertEquals(90, pdfDocument.getRotation());
        
        pdfDocument.setRotation(180);
        assertEquals(180, pdfDocument.getRotation());
    }

    @Test
    void testSetRotationNormalization() {
        pdfDocument.setRotation(450);
        assertEquals(90, pdfDocument.getRotation()); // 450 % 360 = 90
        
        pdfDocument.setRotation(-90);
        assertEquals(270, pdfDocument.getRotation()); // -90 + 360 = 270
    }

    @Test
    void testGetFileName() {
        assertEquals("test.pdf", pdfDocument.getFileName());
    }

    @Test
    void testAnnotations() {
        assertTrue(pdfDocument.getAnnotations().isEmpty());
        
        Annotation annotation = new HighlightAnnotation(0, 10, 20, 100, 50, Color.YELLOW);
        pdfDocument.addAnnotation(annotation);
        
        assertEquals(1, pdfDocument.getAnnotations().size());
        assertTrue(pdfDocument.getAnnotations().contains(annotation));
    }

    @Test
    void testGetAnnotationsForPage() {
        Annotation ann1 = new HighlightAnnotation(0, 10, 20, 100, 50, Color.YELLOW);
        Annotation ann2 = new HighlightAnnotation(1, 30, 40, 80, 60, Color.GREEN);
        Annotation ann3 = new HighlightAnnotation(0, 50, 60, 120, 70, Color.BLUE);
        
        pdfDocument.addAnnotation(ann1);
        pdfDocument.addAnnotation(ann2);
        pdfDocument.addAnnotation(ann3);
        
        assertEquals(2, pdfDocument.getAnnotationsForPage(0).size());
        assertEquals(1, pdfDocument.getAnnotationsForPage(1).size());
        assertEquals(0, pdfDocument.getAnnotationsForPage(2).size());
    }

    @Test
    void testEditHistory() {
        assertTrue(pdfDocument.getEditHistory().isEmpty());
        assertFalse(pdfDocument.hasUnsavedEdits());
    }

    @Test
    void testMarkAsSaved() {
        pdfDocument.setHasUnsavedEdits(true);
        assertTrue(pdfDocument.hasUnsavedEdits());
        
        pdfDocument.markAsSaved();
        assertFalse(pdfDocument.hasUnsavedEdits());
    }

    @Test
    void testClearEditHistory() {
        pdfDocument.setHasUnsavedEdits(true);
        pdfDocument.clearEditHistory();
        
        assertTrue(pdfDocument.getEditHistory().isEmpty());
        assertFalse(pdfDocument.hasUnsavedEdits());
    }
}
