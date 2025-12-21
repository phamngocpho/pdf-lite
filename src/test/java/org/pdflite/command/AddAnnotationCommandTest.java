package org.pdflite.command;

import javafx.scene.paint.Color;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pdflite.model.CommentAnnotation;
import org.pdflite.model.PDFDocument;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AddAnnotationCommand.
 */
class AddAnnotationCommandTest {

    @TempDir
    Path tempDir;

    private PDDocument pdDocument;
    private File testFile;
    private PDFDocument pdfDocument;
    private CommentAnnotation annotation;

    @BeforeEach
    void setUp() throws IOException {
        pdDocument = new PDDocument();
        pdDocument.addPage(new PDPage());
        
        testFile = tempDir.resolve("test.pdf").toFile();
        pdfDocument = new PDFDocument(pdDocument, testFile);
        
        annotation = new CommentAnnotation(0, 100, 200, "Test comment", Color.YELLOW);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (pdDocument != null) {
            pdDocument.close();
        }
    }

    @Test
    void testExecuteAddsAnnotation() {
        AddAnnotationCommand command = new AddAnnotationCommand(pdfDocument, annotation, null);
        
        assertEquals(0, pdfDocument.getAnnotations().size());
        
        command.execute();
        
        assertEquals(1, pdfDocument.getAnnotations().size());
        assertTrue(pdfDocument.getAnnotations().contains(annotation));
        assertTrue(pdfDocument.hasUnsavedEdits());
    }

    @Test
    void testUndoRemovesAnnotation() {
        AddAnnotationCommand command = new AddAnnotationCommand(pdfDocument, annotation, null);
        
        command.execute();
        assertEquals(1, pdfDocument.getAnnotations().size());
        
        command.undo();
        
        assertEquals(0, pdfDocument.getAnnotations().size());
        assertFalse(pdfDocument.getAnnotations().contains(annotation));
    }

    @Test
    void testExecuteWithCallback() {
        AtomicInteger callbackPageIndex = new AtomicInteger(-1);
        
        AddAnnotationCommand command = new AddAnnotationCommand(
            pdfDocument, 
            annotation, 
            pageIndex -> callbackPageIndex.set(pageIndex)
        );
        
        command.execute();
        
        assertEquals(0, callbackPageIndex.get());
    }

    @Test
    void testUndoWithCallback() {
        AtomicInteger callbackPageIndex = new AtomicInteger(-1);
        
        AddAnnotationCommand command = new AddAnnotationCommand(
            pdfDocument, 
            annotation, 
            pageIndex -> callbackPageIndex.set(pageIndex)
        );
        
        command.execute();
        callbackPageIndex.set(-1); // Reset
        
        command.undo();
        
        assertEquals(0, callbackPageIndex.get());
    }

    @Test
    void testGetDescription() {
        AddAnnotationCommand command = new AddAnnotationCommand(pdfDocument, annotation, null);
        
        String description = command.getDescription();
        
        assertNotNull(description);
        assertTrue(description.contains("CommentAnnotation"));
    }

    @Test
    void testExecuteWithNullCallback() {
        AddAnnotationCommand command = new AddAnnotationCommand(pdfDocument, annotation, null);
        
        // Should not throw exception
        assertDoesNotThrow(() -> command.execute());
    }

    @Test
    void testUndoWithNullCallback() {
        AddAnnotationCommand command = new AddAnnotationCommand(pdfDocument, annotation, null);
        
        command.execute();
        
        // Should not throw exception
        assertDoesNotThrow(() -> command.undo());
    }

    @Test
    void testMultipleExecuteUndo() {
        AddAnnotationCommand command = new AddAnnotationCommand(pdfDocument, annotation, null);
        
        // Execute
        command.execute();
        assertEquals(1, pdfDocument.getAnnotations().size());
        
        // Undo
        command.undo();
        assertEquals(0, pdfDocument.getAnnotations().size());
        
        // Execute again
        command.execute();
        assertEquals(1, pdfDocument.getAnnotations().size());
    }
}
