package org.pdflite.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pdflite.model.PDFDocument;
import org.pdflite.model.WatermarkConfig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WatermarkService.
 */
class WatermarkServiceTest {

    @TempDir
    Path tempDir;

    private WatermarkService watermarkService;
    private PDDocument pdDocument;
    private PDFDocument pdfDocument;
    private File testFile;

    @BeforeEach
    void setUp() throws IOException {
        watermarkService = new WatermarkService();

        pdDocument = new PDDocument();
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
    void testApplyTextWatermark() throws IOException {
        WatermarkConfig config = new WatermarkConfig();
        config.setType(WatermarkConfig.WatermarkType.TEXT);
        config.setText("CONFIDENTIAL");
        config.setFontSize(48);
        config.setColor(new java.awt.Color(255, 0, 0));
        config.setOpacity(0.5f);
        config.setPosition(WatermarkConfig.Position.CENTER);
        config.setRotation(45);
        config.setApplyToAllPages(true);

        assertDoesNotThrow(() -> watermarkService.applyWatermark(pdfDocument, config));
    }

    @Test
    void testApplyImageWatermark() throws IOException {
        // Create test image
        File imageFile = tempDir.resolve("watermark.png").toFile();
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", imageFile);

        WatermarkConfig config = new WatermarkConfig();
        config.setType(WatermarkConfig.WatermarkType.IMAGE);
        config.setImageFile(imageFile);
        config.setScale(0.5f);
        config.setOpacity(0.3f);
        config.setPosition(WatermarkConfig.Position.BOTTOM_RIGHT);
        config.setRotation(0);
        config.setApplyToAllPages(false);
        config.setPageRange("1");

        assertDoesNotThrow(() -> watermarkService.applyWatermark(pdfDocument, config));
    }

    @Test
    void testApplyWatermarkWithNullDocument() {
        WatermarkConfig config = new WatermarkConfig();
        config.setType(WatermarkConfig.WatermarkType.TEXT);
        config.setText("TEST");

        assertThrows(IllegalArgumentException.class,
                () -> watermarkService.applyWatermark(null, config));
    }

    @Test
    void testApplyWatermarkWithNullConfig() {
        assertThrows(IllegalArgumentException.class,
                () -> watermarkService.applyWatermark(pdfDocument, null));
    }

    @Test
    void testApplyWatermarkToSpecificPages() throws IOException {
        WatermarkConfig config = new WatermarkConfig();
        config.setType(WatermarkConfig.WatermarkType.TEXT);
        config.setText("Page 1 Only");
        config.setFontSize(24);
        config.setColor(java.awt.Color.BLUE);
        config.setOpacity(0.7f);
        config.setPosition(WatermarkConfig.Position.TOP_CENTER);
        config.setRotation(0);
        config.setApplyToAllPages(false);
        config.setPageRange("1");

        assertDoesNotThrow(() -> watermarkService.applyWatermark(pdfDocument, config));
    }

    @Test
    void testApplyWatermarkWithPageRange() throws IOException {
        WatermarkConfig config = new WatermarkConfig();
        config.setType(WatermarkConfig.WatermarkType.TEXT);
        config.setText("Multi-page");
        config.setFontSize(36);
        config.setColor(java.awt.Color.GREEN);
        config.setOpacity(0.4f);
        config.setPosition(WatermarkConfig.Position.MIDDLE_LEFT);
        config.setRotation(90);
        config.setApplyToAllPages(false);
        config.setPageRange("1-2");

        assertDoesNotThrow(() -> watermarkService.applyWatermark(pdfDocument, config));
    }

    @Test
    void testApplyWatermarkWithCustomPosition() throws IOException {
        WatermarkConfig config = new WatermarkConfig();
        config.setType(WatermarkConfig.WatermarkType.TEXT);
        config.setText("Custom Position");
        config.setFontSize(20);
        config.setColor(java.awt.Color.BLACK);
        config.setOpacity(0.8f);
        config.setPosition(WatermarkConfig.Position.CUSTOM);
        config.setCustomX(100);
        config.setCustomY(200);
        config.setRotation(0);
        config.setApplyToAllPages(true);

        assertDoesNotThrow(() -> watermarkService.applyWatermark(pdfDocument, config));
    }

    @Test
    void testApplyWatermarkWithDifferentFonts() throws IOException {
        String[] fonts = {"helvetica", "helvetica-bold", "times-roman", "times-bold", "courier", "courier-bold"};

        for (String fontName : fonts) {
            WatermarkConfig config = new WatermarkConfig();
            config.setType(WatermarkConfig.WatermarkType.TEXT);
            config.setText("Font Test");
            config.setFontName(fontName);
            config.setFontSize(24);
            config.setColor(java.awt.Color.BLACK);
            config.setOpacity(0.5f);
            config.setPosition(WatermarkConfig.Position.CENTER);
            config.setRotation(0);
            config.setApplyToAllPages(true);

            assertDoesNotThrow(() -> watermarkService.applyWatermark(pdfDocument, config));
        }
    }

    @Test
    void testApplyImageWatermarkWithNonExistentFile() {
        File nonExistentFile = new File("nonexistent.png");

        WatermarkConfig config = new WatermarkConfig();
        config.setType(WatermarkConfig.WatermarkType.IMAGE);
        config.setImageFile(nonExistentFile);
        config.setScale(1.0f);
        config.setOpacity(0.5f);
        config.setPosition(WatermarkConfig.Position.CENTER);
        config.setRotation(0);
        config.setApplyToAllPages(true);

        assertThrows(IOException.class,
                () -> watermarkService.applyWatermark(pdfDocument, config));
    }
}
