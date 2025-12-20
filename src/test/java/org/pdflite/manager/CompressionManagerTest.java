package org.pdflite.manager;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.pdflite.model.PDFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CompressionManager.
 */
class CompressionManagerTest {

    private CompressionManager compressionManager;
    private PDDocument pdDocument;
    private PDFDocument pdfDocument;

    @BeforeEach
    void setUp() {
        compressionManager = new CompressionManager();
        pdDocument = new PDDocument();
        pdfDocument = new PDFDocument(pdDocument, null);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (pdDocument != null) {
            pdDocument.close();
        }
    }

    @Test
    void testCompressPDFWithNullDocument() {
        boolean result = compressionManager.compressPDF(null, CompressionManager.CompressionLevel.MEDIUM);
        assertFalse(result, "Should return false for null document");
    }

    @Test
    void testCompressPDFWithEmptyDocument() {
        pdDocument.addPage(new PDPage());
        
        boolean result = compressionManager.compressPDF(pdfDocument, CompressionManager.CompressionLevel.MEDIUM);
        assertFalse(result, "Should return false for document without images");
    }

    @Test
    void testCompressPDFWithImage() throws IOException {
        // Create a page with a larger image
        PDPage page = new PDPage(PDRectangle.A4);
        pdDocument.addPage(page);

        // Create a larger test image (500x500)
        BufferedImage bufferedImage = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bufferedImage.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 500, 500);
        g.dispose();

        // Convert to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", baos);
        
        PDImageXObject image = PDImageXObject.createFromByteArray(
            pdDocument, baos.toByteArray(), "test");

        // Add image to page
        try (PDPageContentStream contentStream = new PDPageContentStream(pdDocument, page)) {
            contentStream.drawImage(image, 100, 100, 200, 200);
        }

        // Compression should succeed with a large image
        boolean result = compressionManager.compressPDF(pdfDocument, CompressionManager.CompressionLevel.MEDIUM);
        assertTrue(result, "Compression should succeed with image");
    }

    @Test
    void testCompressionLevels() throws IOException {
        // Create document with larger image
        PDPage page = new PDPage(PDRectangle.A4);
        pdDocument.addPage(page);

        BufferedImage bufferedImage = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bufferedImage.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 400, 400);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", baos);
        
        PDImageXObject image = PDImageXObject.createFromByteArray(
            pdDocument, baos.toByteArray(), "test");

        try (PDPageContentStream contentStream = new PDPageContentStream(pdDocument, page)) {
            contentStream.drawImage(image, 50, 50, 300, 300);
        }

        // Test all compression levels - they should complete without error
        for (CompressionManager.CompressionLevel level : CompressionManager.CompressionLevel.values()) {
            try {
                compressionManager.compressPDF(pdfDocument, level);
                // Success - compression completed
                assertTrue(true);
            } catch (Exception e) {
                fail("Compression with " + level + " should not throw exception: " + e.getMessage());
            }
        }
    }

    @Test
    void testEstimateCompressionWithNullDocument() {
        int estimate = compressionManager.estimateCompression(null, CompressionManager.CompressionLevel.MEDIUM);
        assertEquals(0, estimate, "Should return 0 for null document");
    }

    @Test
    void testEstimateCompressionWithEmptyDocument() {
        pdDocument.addPage(new PDPage());
        
        int estimate = compressionManager.estimateCompression(pdfDocument, CompressionManager.CompressionLevel.MEDIUM);
        assertEquals(0, estimate, "Should return 0 for document without images");
    }

    @Test
    void testEstimateCompressionWithImage() throws IOException {
        // Create document with image
        PDPage page = new PDPage(PDRectangle.A4);
        pdDocument.addPage(page);

        BufferedImage bufferedImage = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bufferedImage.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, 300, 300);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", baos);
        
        PDImageXObject image = PDImageXObject.createFromByteArray(
            pdDocument, baos.toByteArray(), "test");

        try (PDPageContentStream contentStream = new PDPageContentStream(pdDocument, page)) {
            contentStream.drawImage(image, 100, 100, 150, 150);
        }

        int estimate = compressionManager.estimateCompression(pdfDocument, CompressionManager.CompressionLevel.MEDIUM);
        assertTrue(estimate > 0, "Should return positive estimate for document with images");
        assertTrue(estimate <= 100, "Estimate should not exceed 100%");
    }

    @Test
    void testCompressionLevelQuality() {
        assertEquals(0.9f, CompressionManager.CompressionLevel.LOW.getQuality());
        assertEquals(0.7f, CompressionManager.CompressionLevel.MEDIUM.getQuality());
        assertEquals(0.5f, CompressionManager.CompressionLevel.HIGH.getQuality());
        assertEquals(0.3f, CompressionManager.CompressionLevel.MAXIMUM.getQuality());
    }

    @Test
    void testCompressionLevelDescription() {
        assertNotNull(CompressionManager.CompressionLevel.LOW.getDescription());
        assertNotNull(CompressionManager.CompressionLevel.MEDIUM.getDescription());
        assertNotNull(CompressionManager.CompressionLevel.HIGH.getDescription());
        assertNotNull(CompressionManager.CompressionLevel.MAXIMUM.getDescription());
    }
}
