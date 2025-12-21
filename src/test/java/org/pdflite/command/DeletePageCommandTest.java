package org.pdflite.command;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pdflite.model.CommentAnnotation;
import org.pdflite.model.PDFDocument;
import javafx.scene.paint.Color;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeletePageCommand.
 */
class DeletePageCommandTest {

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
    void testExecuteDeletesPage() {
        DeletePageCommand command = new DeletePageCommand(pdfDocument, 1, null);
        
        assertEquals(3, pdfDocument.getTotalPages());
        
        command.execute();
        
        assertEquals(2, pdfDocument.getTotalPages());
        assertTrue(pdfDocument.hasUnsavedEdits());
    }

    @Test
    void testUndoRestoresPage() {
        DeletePageCommand command = new DeletePageCommand(pdfDocument, 1, null);
        
        command.execute();
        assertEquals(2, pdfDocument.getTotalPages());
        
        command.undo();
        
        assertEquals(3, pdfDocument.getTotalPages());
    }

    @Test
    void testExecuteWithCallback() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        
        DeletePageCommand command = new DeletePageCommand(
            pdfDocument, 
            1, 
            () -> callbackCalled.set(true)
        );
        
        command.execute();
        
        assertTrue(callbackCalled.get());
    }

    @Test
    void testUndoWithCallback() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        
        DeletePageCommand command = new DeletePageCommand(
            pdfDocument, 
            1, 
            () -> callbackCalled.set(true)
        );
        
        command.execute();
        callbackCalled.set(false); // Reset
        
        command.undo();
        
        assertTrue(callbackCalled.get());
    }

    @Test
    void testGetDescription() {
        DeletePageCommand command = new DeletePageCommand(pdfDocument, 1, null);
        
        String description = command.getDescription();
        
        assertNotNull(description);
        assertTrue(description.contains("Delete Page"));
        assertTrue(description.contains("2")); // Page 2 (1-based)
    }

    @Test
    void testExecuteWithNullCallback() {
        DeletePageCommand command = new DeletePageCommand(pdfDocument, 1, null);
        
        // Should not throw exception
        assertDoesNotThrow(() -> command.execute());
    }

    @Test
    void testUndoWithNullCallback() {
        DeletePageCommand command = new DeletePageCommand(pdfDocument, 1, null);
        
        command.execute();
        
        // Should not throw exception
        assertDoesNotThrow(() -> command.undo());
    }

    @Test
    void testDeleteFirstPage() {
        DeletePageCommand command = new DeletePageCommand(pdfDocument, 0, null);
        
        command.execute();
        
        assertEquals(2, pdfDocument.getTotalPages());
    }

    @Test
    void testDeleteLastPage() {
        DeletePageCommand command = new DeletePageCommand(pdfDocument, 2, null);
        
        command.execute();
        
        assertEquals(2, pdfDocument.getTotalPages());
    }

    @Test
    void testAnnotationsAreDeleted() {
        // Add annotations to page 1
        CommentAnnotation ann1 = new CommentAnnotation(1, 100, 200, "Comment on page 2", Color.YELLOW);
        pdfDocument.addAnnotation(ann1);
        
        DeletePageCommand command = new DeletePageCommand(pdfDocument, 1, null);
        
        assertEquals(1, pdfDocument.getAnnotations().size());
        
        command.execute();
        
        // Annotation should be removed
        assertEquals(0, pdfDocument.getAnnotations().size());
    }

    @Test
    void testAnnotationsAreRestoredOnUndo() {
        // Add annotations to page 1
        CommentAnnotation ann1 = new CommentAnnotation(1, 100, 200, "Comment on page 2", Color.YELLOW);
        pdfDocument.addAnnotation(ann1);
        
        DeletePageCommand command = new DeletePageCommand(pdfDocument, 1, null);
        
        command.execute();
        assertEquals(0, pdfDocument.getAnnotations().size());
        
        command.undo();
        
        // Annotation should be restored
        assertEquals(1, pdfDocument.getAnnotations().size());
    }

    @Test
    void testAnnotationPageNumbersAreUpdated() {
        // Add annotations to different pages
        CommentAnnotation ann1 = new CommentAnnotation(0, 100, 200, "Page 1", Color.YELLOW);
        CommentAnnotation ann2 = new CommentAnnotation(2, 100, 200, "Page 3", Color.YELLOW);
        pdfDocument.addAnnotation(ann1);
        pdfDocument.addAnnotation(ann2);
        
        // Delete page 1 (index 1)
        DeletePageCommand command = new DeletePageCommand(pdfDocument, 1, null);
        command.execute();
        
        // Page 3 annotation should now be on page 2 (index 1)
        assertEquals(0, ann1.getPageNumber()); // Unchanged
        assertEquals(1, ann2.getPageNumber()); // Decremented
    }
}
