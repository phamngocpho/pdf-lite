package org.pdflite.manager;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.embed.swing.JFXPanel;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Test class for page deletion functionality.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 */
@RunWith(JUnitQuickcheck.class)
public class PageDeletionTest {

    private PDFService pdfService;
    private FileManager fileManager;

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @BeforeClass
    public static void initJavaFX() {
        new JFXPanel();
    }

    @Before
    public void setUp() {
        pdfService = new PDFService();
        fileManager = new FileManager(pdfService, null, null);
    }

    /**
     * Creates a test PDF with specified number of pages.
     */
    private File createPDFWithPages(int pageCount) throws IOException {
        File pdfFile = new File(tempDir.getRoot(), "test_" + pageCount + "_pages.pdf");

        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
            }
            document.save(pdfFile);
        }

        return pdfFile;
    }

    // ==================== Property-Based Tests ====================

    /**
     * Feature: native-pdf-editing, Property 22: Page Deletion
     * Validates: Requirements 7.2
     * <p>
     * For any PDF document with N pages (N > 1) and valid page index I (0 ≤ I < N),
     * removing page I should result in a document with N-1 pages.
     */
    @Property(trials = 100)
    public void pageDeletionProperty(int pageCount, int pageIndexToDelete) throws IOException {
        // Skip invalid inputs - need at least 2 pages
        if (pageCount < 2 || pageCount > 20) {
            return;
        }

        // Ensure page index is valid
        if (pageIndexToDelete < 0 || pageIndexToDelete >= pageCount) {
            return;
        }

        // Create PDF with specified pages
        File pdfFile = createPDFWithPages(pageCount);
        PDFDocument pdfDoc = pdfService.openPDF(pdfFile);

        try {
            // Verify initial page count
            assertEquals("Initial page count should match", pageCount, pdfDoc.getTotalPages());

            // Delete the page
            fileManager.deletePages(pdfDoc, java.util.List.of(pageIndexToDelete));

            // Verify page count decreased by 1
            assertEquals("Page count should decrease by 1 after deletion",
                    pageCount - 1, pdfDoc.getTotalPages());

        } finally {
            pdfService.closePDF(pdfDoc);
        }
    }

    /**
     * Feature: native-pdf-editing, Property 23: Page Count Update
     * Validates: Requirements 7.3
     * <p>
     * For any page deletion operation, the displayed page count should decrease by one.
     */
    @Property(trials = 50)
    public void pageCountUpdateProperty(int initialPageCount) throws IOException {
        // Skip invalid inputs
        if (initialPageCount < 2 || initialPageCount > 15) {
            return;
        }

        // Create PDF
        File pdfFile = createPDFWithPages(initialPageCount);
        PDFDocument pdfDoc = pdfService.openPDF(pdfFile);

        try {
            int beforeCount = pdfDoc.getTotalPages();

            // Delete first page
            fileManager.deletePages(pdfDoc, java.util.List.of(0));

            int afterCount = pdfDoc.getTotalPages();

            // Verify count decreased by exactly 1
            assertEquals("Page count should decrease by exactly 1",
                    beforeCount - 1, afterCount);

        } finally {
            pdfService.closePDF(pdfDoc);
        }
    }

    // ==================== Unit Tests ====================

    @Test
    public void testDeleteFirstPage() throws IOException {
        File pdfFile = createPDFWithPages(3);
        PDFDocument pdfDoc = pdfService.openPDF(pdfFile);

        try {
            assertEquals(3, pdfDoc.getTotalPages());

            fileManager.deletePages(pdfDoc, java.util.List.of(0));

            assertEquals(2, pdfDoc.getTotalPages());

        } finally {
            pdfService.closePDF(pdfDoc);
        }
    }

    @Test
    public void testDeleteMiddlePage() throws IOException {
        File pdfFile = createPDFWithPages(5);
        PDFDocument pdfDoc = pdfService.openPDF(pdfFile);

        try {
            assertEquals(5, pdfDoc.getTotalPages());

            fileManager.deletePages(pdfDoc, java.util.List.of(2));

            assertEquals(4, pdfDoc.getTotalPages());

        } finally {
            pdfService.closePDF(pdfDoc);
        }
    }

    @Test
    public void testDeleteLastPage() throws IOException {
        File pdfFile = createPDFWithPages(4);
        PDFDocument pdfDoc = pdfService.openPDF(pdfFile);

        try {
            assertEquals(4, pdfDoc.getTotalPages());

            fileManager.deletePages(pdfDoc, java.util.List.of(3));

            assertEquals(3, pdfDoc.getTotalPages());

        } finally {
            pdfService.closePDF(pdfDoc);
        }
    }

    @Test
    public void testDeleteMultiplePages() throws IOException {
        File pdfFile = createPDFWithPages(10);
        PDFDocument pdfDoc = pdfService.openPDF(pdfFile);

        try {
            assertEquals(10, pdfDoc.getTotalPages());

            // Delete pages 2, 5, 7 (in descending order to maintain indices)
            fileManager.deletePages(pdfDoc, java.util.List.of(7, 5, 2));

            assertEquals(7, pdfDoc.getTotalPages());

        } finally {
            pdfService.closePDF(pdfDoc);
        }
    }

    @Test
    public void testCannotDeleteFromSinglePageDocument() throws IOException {
        File pdfFile = createPDFWithPages(1);
        PDFDocument pdfDoc = pdfService.openPDF(pdfFile);

        try {
            assertEquals(1, pdfDoc.getTotalPages());

            // Attempting to delete should not change page count
            // (FileManager should handle this gracefully or throw exception)
            try {
                fileManager.deletePages(pdfDoc, java.util.List.of(0));
                // If no exception, verify page count unchanged
                assertEquals("Should not delete last page", 1, pdfDoc.getTotalPages());
            } catch (Exception e) {
                // Expected - cannot delete last page
                assertEquals(1, pdfDoc.getTotalPages());
            }

        } finally {
            pdfService.closePDF(pdfDoc);
        }
    }

    @Test
    public void testDeleteInvalidPageIndex() throws IOException {
        File pdfFile = createPDFWithPages(3);
        PDFDocument pdfDoc = pdfService.openPDF(pdfFile);

        try {
            int initialCount = pdfDoc.getTotalPages();

            // Try to delete page with invalid index - should be ignored
            fileManager.deletePages(pdfDoc, java.util.List.of(10));

            // Page count should remain unchanged
            assertEquals("Invalid page index should be ignored",
                    initialCount, pdfDoc.getTotalPages());
        } finally {
            pdfService.closePDF(pdfDoc);
        }
    }

    @Test
    public void testDeleteNegativePageIndex() throws IOException {
        File pdfFile = createPDFWithPages(3);
        PDFDocument pdfDoc = pdfService.openPDF(pdfFile);

        try {
            int initialCount = pdfDoc.getTotalPages();

            // Try to delete page with negative index - should be ignored
            fileManager.deletePages(pdfDoc, java.util.List.of(-1));

            // Page count should remain unchanged
            assertEquals("Negative page index should be ignored",
                    initialCount, pdfDoc.getTotalPages());
        } finally {
            pdfService.closePDF(pdfDoc);
        }
    }

    @Test
    public void testPageDeletionPersistence() throws IOException {
        File pdfFile = createPDFWithPages(5);
        PDFDocument pdfDoc = pdfService.openPDF(pdfFile);

        try {
            // Delete a page
            fileManager.deletePages(pdfDoc, java.util.List.of(2));
            assertEquals(4, pdfDoc.getTotalPages());

            // Save the document
            pdfService.save(pdfDoc);
            pdfService.closePDF(pdfDoc);

            // Reopen and verify page count persisted
            pdfDoc = pdfService.openPDF(pdfFile);
            assertEquals("Page deletion should persist after save",
                    4, pdfDoc.getTotalPages());

        } finally {
            if (pdfDoc != null) {
                pdfService.closePDF(pdfDoc);
            }
        }
    }
}
