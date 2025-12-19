package org.pdflite.manager;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.embed.swing.JFXPanel;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Test class for ContentStreamManager.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 */
@RunWith(JUnitQuickcheck.class)
public class ContentStreamManagerTest {

    private ContentStreamManager contentStreamManager;

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @BeforeClass
    public static void initJavaFX() {
        new JFXPanel();
    }

    @Before
    public void setUp() {
        contentStreamManager = new ContentStreamManager();
    }

    /**
     * Creates a simple test PDF.
     */
    private File createTestPDF() throws IOException {
        File pdfFile = new File(tempDir.getRoot(), "test.pdf");

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            document.save(pdfFile);
        }

        return pdfFile;
    }

    // ==================== Property-Based Tests ====================

    /**
     * Feature: native-pdf-editing, Property 26: Text Addition
     * Validates: Requirements 8.5
     * <p>
     * For any text string and position, adding it to a page using PDPageContentStream
     * should succeed and the text should be present in the saved PDF.
     */
    @Property(trials = 50)
    public void textAdditionProperty(String text, float x, float y, float fontSize) throws IOException {
        // Skip invalid inputs
        if (text == null || text.trim().isEmpty() || text.length() > 50) {
            return;
        }
        if (fontSize <= 0 || fontSize > 72 || Float.isNaN(fontSize) || Float.isInfinite(fontSize)) {
            return;
        }
        if (Float.isNaN(x) || Float.isInfinite(x) || Float.isNaN(y) || Float.isInfinite(y)) {
            return;
        }

        // Filter out non-ASCII characters that Helvetica font doesn't support
        String filteredText = text.trim().replaceAll("[^\\x20-\\x7E]", "");
        if (filteredText.isEmpty()) {
            return;
        }

        // Clamp coordinates to reasonable bounds
        x = Math.max(0, Math.min(500, Math.abs(x)));
        y = Math.max(0, Math.min(700, Math.abs(y)));
        fontSize = Math.max(8, Math.min(48, Math.abs(fontSize)));

        // Create a test PDF
        File pdfFile = createTestPDF();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDPage page = document.getPage(0);

            // Add text to the page
            contentStreamManager.addText(document, page, filteredText, x, y, fontSize);

            // Save to a new file
            File outputFile = new File(tempDir.getRoot(), "output_" + System.nanoTime() + ".pdf");
            document.save(outputFile);
            document.close();

            // Verify the text was added by reading it back
            try (PDDocument savedDoc = Loader.loadPDF(outputFile)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String extractedText = stripper.getText(savedDoc);

                // The text should be present in the extracted content
                assertTrue("Added text '" + filteredText + "' should be present in the PDF",
                        extractedText.contains(filteredText));
            }
        }
    }

    // ==================== Unit Tests ====================

    @Test
    public void testAddText() throws IOException {
        File pdfFile = createTestPDF();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDPage page = document.getPage(0);

            // Add text
            contentStreamManager.addText(document, page, "Hello World", 100, 700, 12);

            // Save and verify
            File outputFile = new File(tempDir.getRoot(), "output_addtext.pdf");
            document.save(outputFile);
            document.close();

            // Read back and verify
            try (PDDocument savedDoc = Loader.loadPDF(outputFile)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(savedDoc);
                assertTrue(text.contains("Hello World"));
            }
        }
    }

    @Test
    public void testAddTextWithFont() throws IOException {
        File pdfFile = createTestPDF();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDPage page = document.getPage(0);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

            // Add text with specific font
            contentStreamManager.addText(document, page, "Test Text", 100, 700, font, 14);

            // Save and verify
            File outputFile = new File(tempDir.getRoot(), "output_font.pdf");
            document.save(outputFile);
            document.close();

            // Read back and verify
            try (PDDocument savedDoc = Loader.loadPDF(outputFile)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(savedDoc);
                assertTrue(text.contains("Test Text"));
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddTextWithNullText() throws IOException {
        File pdfFile = createTestPDF();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDPage page = document.getPage(0);
            contentStreamManager.addText(document, page, null, 100, 700, 12);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddTextWithNullPage() throws IOException {
        File pdfFile = createTestPDF();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            contentStreamManager.addText(document, null, "Test", 100, 700, 12);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddTextWithInvalidFontSize() throws IOException {
        File pdfFile = createTestPDF();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDPage page = document.getPage(0);
            contentStreamManager.addText(document, page, "Test", 100, 700, -12);
        }
    }

    @Test
    public void testValidateContentStream() throws IOException {
        File pdfFile = createTestPDF();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDPage page = document.getPage(0);

            // Empty page should be valid
            assertTrue(contentStreamManager.validateContentStream(page));

            // Add some text
            contentStreamManager.addText(document, page, "Validation Test", 100, 700, 12);

            // Should still be valid
            assertTrue(contentStreamManager.validateContentStream(page));
        }
    }

    @Test
    public void testValidateContentStreamWithNull() {
        assertFalse(contentStreamManager.validateContentStream(null));
    }

    @Test
    public void testHasTextContent() throws IOException {
        File pdfFile = createTestPDF();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDPage page = document.getPage(0);

            // Empty page should have no text
            assertFalse(contentStreamManager.hasTextContent(page));

            // Add text
            contentStreamManager.addText(document, page, "Content Test", 100, 700, 12);

            // Now should have text
            assertTrue(contentStreamManager.hasTextContent(page));
        }
    }

    @Test
    public void testGetContentBounds() throws IOException {
        File pdfFile = createTestPDF();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDPage page = document.getPage(0);

            PDRectangle bounds = contentStreamManager.getContentBounds(page);

            assertNotNull(bounds);
            assertTrue(bounds.getWidth() > 0);
            assertTrue(bounds.getHeight() > 0);
        }
    }

    @Test
    public void testGetContentBoundsWithNull() {
        assertNull(contentStreamManager.getContentBounds(null));
    }

    @Test
    public void testClearContentStream() throws IOException {
        File pdfFile = createTestPDF();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDPage page = document.getPage(0);

            // Add some text
            contentStreamManager.addText(document, page, "To Be Cleared", 100, 700, 12);
            assertTrue(contentStreamManager.hasTextContent(page));

            // Clear the content
            contentStreamManager.clearContentStream(page);

            // Should have no text now
            assertFalse(contentStreamManager.hasTextContent(page));
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemoveTextOperatorsNotImplemented() throws IOException {
        File pdfFile = createTestPDF();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDPage page = document.getPage(0);
            javafx.geometry.Rectangle2D region = new javafx.geometry.Rectangle2D(0, 0, 100, 100);
            contentStreamManager.removeTextOperators(page, region);
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testModifyContentStreamNotImplemented() throws IOException {
        File pdfFile = createTestPDF();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDPage page = document.getPage(0);
            contentStreamManager.modifyContentStream(page, engine -> {
            });
        }
    }
}
