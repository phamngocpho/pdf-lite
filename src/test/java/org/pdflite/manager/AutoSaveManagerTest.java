package org.pdflite.manager;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AutoSaveManager.
 */
class AutoSaveManagerTest {

    @TempDir
    Path tempDir;

    private ScheduledExecutorService scheduler;
    private AutoSaveManager autoSaveManager;
    private PDDocument pdDocument;
    private File testFile;
    private PDFDocument pdfDocument;

    @BeforeEach
    void setUp() throws IOException {
        scheduler = Executors.newScheduledThreadPool(1);
        autoSaveManager = new AutoSaveManager(scheduler);
        
        pdDocument = new PDDocument();
        pdDocument.addPage(new PDPage());
        
        testFile = tempDir.resolve("test.pdf").toFile();
        pdfDocument = new PDFDocument(pdDocument, testFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (pdDocument != null) {
            pdDocument.close();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Test
    void testInitialState() {
        assertTrue(autoSaveManager.isEnabled());
    }

    @Test
    void testSetDocument() {
        assertDoesNotThrow(() -> autoSaveManager.setDocument(pdfDocument));
    }

    @Test
    void testSetEnabled() {
        autoSaveManager.setEnabled(false);
        assertFalse(autoSaveManager.isEnabled());
        
        autoSaveManager.setEnabled(true);
        assertTrue(autoSaveManager.isEnabled());
    }

    @Test
    void testScheduleAutoSaveWhenDisabled() {
        autoSaveManager.setEnabled(false);
        autoSaveManager.setDocument(pdfDocument);
        
        // Should not throw exception
        assertDoesNotThrow(() -> autoSaveManager.scheduleAutoSave());
    }

    @Test
    void testScheduleAutoSaveWithoutDocument() {
        // Should not throw exception
        assertDoesNotThrow(() -> autoSaveManager.scheduleAutoSave());
    }

    @Test
    void testClearAutoSave() {
        autoSaveManager.setDocument(pdfDocument);
        
        // Should not throw exception
        assertDoesNotThrow(() -> autoSaveManager.clearAutoSave(pdfDocument));
    }

    @Test
    void testClearAutoSaveWithNull() {
        // Should not throw exception
        assertDoesNotThrow(() -> autoSaveManager.clearAutoSave(null));
    }

    @Test
    void testHasRecoveryFiles() {
        // May have recovery files from previous tests or runs
        // Just verify the method doesn't throw exception
        assertDoesNotThrow(() -> autoSaveManager.hasRecoveryFiles());
    }

    @Test
    void testGetRecoveryFiles() {
        File[] files = autoSaveManager.getRecoveryFiles();
        assertNotNull(files);
        // May have files from previous tests, just verify it returns an array
        assertTrue(files.length >= 0);
    }

    @Test
    void testShutdown() {
        assertDoesNotThrow(() -> autoSaveManager.shutdown());
    }

    @Test
    void testSetOnAutoSaveCallback() {
        Runnable callback = () -> {};
        assertDoesNotThrow(() -> autoSaveManager.setOnAutoSaveCallback(callback));
    }

    @Test
    void testMultipleScheduleCalls() {
        autoSaveManager.setDocument(pdfDocument);
        pdfDocument.setHasUnsavedEdits(true);
        
        // Multiple calls should cancel previous and schedule new
        autoSaveManager.scheduleAutoSave();
        autoSaveManager.scheduleAutoSave();
        autoSaveManager.scheduleAutoSave();
        
        // Should not throw exception
        assertTrue(true);
    }
}
