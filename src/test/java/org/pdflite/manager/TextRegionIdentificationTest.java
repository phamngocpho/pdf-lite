package org.pdflite.manager;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.pdflite.model.PDFDocument;
import org.pdflite.model.TextRegion;
import org.pdflite.service.PDFService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Property-based tests for text region identification.
 * <p>
 * Tests Property 25: Text Region Identification
 * Validates Requirements 8.1
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
@RunWith(JUnitQuickcheck.class)
public class TextRegionIdentificationTest {
    private static final Logger logger = LoggerFactory.getLogger(TextRegionIdentificationTest.class);

    /**
     * Property 25: Text Region Identification
     * <p>
     * For any PDF page with text content, text extraction should correctly
     * identify and return all text regions with valid bounds.
     * </p>
     */
    @Property(trials = 100)
    public void textRegionIdentificationProperty(int seed) throws IOException {
        // Use seed to generate deterministic test data
        String testText = "Test" + (seed % 100);
        double x = 100; // Fixed position for reliability
        double y = 700;

        // Create a test PDF with text at known position
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            contentStream.newLineAtOffset((float) x, (float) y);
            contentStream.showText(testText);
            contentStream.endText();
        }

        // Save to temp file
        File tempFile = File.createTempFile("text-region-test-", ".pdf");
        tempFile.deleteOnExit();
        doc.save(tempFile);
        doc.close();

        // Load with PDFService
        PDFService pdfService = new PDFService();
        UIStateManager uiStateManager = new UIStateManager(null, null, null, null, null);
        PDFDocument pdfDoc = pdfService.openPDF(tempFile);

        // Create TextEditManager
        TextEditManager textEditManager = new TextEditManager(pdfService, uiStateManager);

        // Extract all text regions
        List<TextRegion> regions = textEditManager.extractAllTextRegions(pdfDoc, 0);

        // Property: At least one region should be found
        assertFalse("Should find at least one text region", regions.isEmpty());

        // Property: The text should be found in one of the regions
        boolean textFound = regions.stream()
                .anyMatch(region -> region.text().contains(testText));
        assertTrue("Text '" + testText + "' should be found in extracted regions", textFound);

        // Property: Each region should have valid bounds
        for (TextRegion region : regions) {
            assertTrue("Region width should be positive", region.width() > 0);
            assertTrue("Region height should be positive", region.height() > 0);
            // Note: X and Y can be negative in some PDF coordinate systems
            // We just check that they are finite numbers
            assertTrue("Region X should be finite", Double.isFinite(region.x()));
            assertTrue("Region Y should be finite", Double.isFinite(region.y()));
            assertNotNull("Region text should not be null", region.text());
            assertFalse("Region text should not be empty", region.text().isEmpty());
        }

        // Clean up
        pdfDoc.getDocument().close();
        tempFile.delete();

        logger.debug("Text region identification property verified for text='{}'", testText);
    }

    /**
     * Unit test: Text region identification with empty page
     */
    @Test
    public void testTextRegionIdentificationEmptyPage() throws IOException {
        PDDocument doc = new PDDocument();
        doc.addPage(new PDPage(PDRectangle.A4));

        File tempFile = File.createTempFile("empty-page-test-", ".pdf");
        tempFile.deleteOnExit();
        doc.save(tempFile);
        doc.close();

        PDFService pdfService = new PDFService();
        UIStateManager uiStateManager = new UIStateManager(null, null, null, null, null);
        PDFDocument pdfDoc = pdfService.openPDF(tempFile);

        TextEditManager textEditManager = new TextEditManager(pdfService, uiStateManager);

        // Extract text regions from empty page
        List<TextRegion> regions = textEditManager.extractAllTextRegions(pdfDoc, 0);

        // Should return empty list for empty page
        assertTrue("Empty page should have no text regions", regions.isEmpty());

        // Finding text at any position should return null
        TextRegion foundRegion = textEditManager.findTextAt(pdfDoc, 0, 100, 100);
        assertNull("Should not find text on empty page", foundRegion);

        pdfDoc.getDocument().close();
        tempFile.delete();
    }

    /**
     * Unit test: Text region identification with multiple text blocks
     */
    @Test
    public void testTextRegionIdentificationMultipleBlocks() throws IOException {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        // Add multiple text blocks at different positions
        try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);

            // First block
            contentStream.newLineAtOffset(50, 700);
            contentStream.showText("First block");

            // Second block
            contentStream.newLineAtOffset(0, -50);
            contentStream.showText("Second block");

            // Third block
            contentStream.newLineAtOffset(0, -50);
            contentStream.showText("Third block");

            contentStream.endText();
        }

        File tempFile = File.createTempFile("multi-block-test-", ".pdf");
        tempFile.deleteOnExit();
        doc.save(tempFile);
        doc.close();

        PDFService pdfService = new PDFService();
        UIStateManager uiStateManager = new UIStateManager(null, null, null, null, null);
        PDFDocument pdfDoc = pdfService.openPDF(tempFile);

        TextEditManager textEditManager = new TextEditManager(pdfService, uiStateManager);

        // Extract all text regions
        List<TextRegion> regions = textEditManager.extractAllTextRegions(pdfDoc, 0);

        // Should find multiple regions
        assertTrue("Should find multiple text regions", regions.size() >= 3);

        // Each block should be identifiable
        boolean foundFirst = regions.stream().anyMatch(r -> r.text().contains("First"));
        boolean foundSecond = regions.stream().anyMatch(r -> r.text().contains("Second"));
        boolean foundThird = regions.stream().anyMatch(r -> r.text().contains("Third"));

        assertTrue("Should find 'First block'", foundFirst);
        assertTrue("Should find 'Second block'", foundSecond);
        assertTrue("Should find 'Third block'", foundThird);

        pdfDoc.getDocument().close();
        tempFile.delete();
    }

    /**
     * Unit test: Text region bounds validation
     */
    @Test
    public void testTextRegionBoundsValidation() throws IOException {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            contentStream.newLineAtOffset(100, 700);
            contentStream.showText("Test text");
            contentStream.endText();
        }

        File tempFile = File.createTempFile("bounds-test-", ".pdf");
        tempFile.deleteOnExit();
        doc.save(tempFile);
        doc.close();

        PDFService pdfService = new PDFService();
        UIStateManager uiStateManager = new UIStateManager(null, null, null, null, null);
        PDFDocument pdfDoc = pdfService.openPDF(tempFile);

        TextEditManager textEditManager = new TextEditManager(pdfService, uiStateManager);

        // Extract all text regions
        List<TextRegion> regions = textEditManager.extractAllTextRegions(pdfDoc, 0);

        assertFalse("Should find at least one text region", regions.isEmpty());

        // Find the region containing "Test text"
        TextRegion region = regions.stream()
                .filter(r -> r.text().contains("Test"))
                .findFirst()
                .orElse(null);

        assertNotNull("Should find text region containing 'Test'", region);

        // Validate bounds - be lenient since PDF text extraction can vary
        assertTrue("X should be finite", Double.isFinite(region.x()));
        assertTrue("Y should be finite", Double.isFinite(region.y()));
        assertTrue("Width should be positive and reasonable", region.width() > 0 && region.width() < 200);
        assertTrue("Height should be positive and reasonable", region.height() > 0 && region.height() < 50);

        pdfDoc.getDocument().close();
        tempFile.delete();
    }

    /**
     * Unit test: Invalid parameters
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPageIndex() throws IOException {
        PDDocument doc = new PDDocument();
        doc.addPage(new PDPage(PDRectangle.A4));

        File tempFile = File.createTempFile("invalid-test-", ".pdf");
        tempFile.deleteOnExit();
        doc.save(tempFile);
        doc.close();

        PDFService pdfService = new PDFService();
        UIStateManager uiStateManager = new UIStateManager(null, null, null, null, null);
        PDFDocument pdfDoc = pdfService.openPDF(tempFile);

        TextEditManager textEditManager = new TextEditManager(pdfService, uiStateManager);

        // Should throw IllegalArgumentException for invalid page index
        textEditManager.extractAllTextRegions(pdfDoc, 999);

        pdfDoc.getDocument().close();
        tempFile.delete();
    }

    /**
     * Unit test: Null parameters
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNullDocument() throws IOException {
        PDFService pdfService = new PDFService();
        UIStateManager uiStateManager = new UIStateManager(null, null, null, null, null);
        TextEditManager textEditManager = new TextEditManager(pdfService, uiStateManager);

        // Should throw IllegalArgumentException for null document
        textEditManager.extractAllTextRegions(null, 0);
    }
}
