package org.pdflite.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PDFExportService.
 */
class PDFExportServiceTest {

    private PDFExportService service;

    @BeforeEach
    void setUp() {
        service = new PDFExportService();
    }

    @Test
    void testImageFormatPNG() {
        PDFExportService.ImageFormat format = PDFExportService.ImageFormat.PNG;
        assertEquals("png", format.getExtension());
        assertEquals("PNG Image", format.getDescription());
    }

    @Test
    void testImageFormatJPG() {
        PDFExportService.ImageFormat format = PDFExportService.ImageFormat.JPG;
        assertEquals("jpg", format.getExtension());
        assertEquals("JPEG Image", format.getDescription());
    }

    @Test
    void testGetDefaultDPI() {
        assertEquals(300f, service.getDefaultDPI());
    }

    @Test
    void testImageFormatValues() {
        PDFExportService.ImageFormat[] formats = PDFExportService.ImageFormat.values();
        assertEquals(2, formats.length);
    }
}
