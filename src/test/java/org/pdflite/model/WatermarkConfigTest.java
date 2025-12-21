package org.pdflite.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WatermarkConfig.
 */
class WatermarkConfigTest {

    private WatermarkConfig config;

    @BeforeEach
    void setUp() {
        config = new WatermarkConfig();
    }

    @Test
    void testDefaultValues() {
        assertEquals(WatermarkConfig.WatermarkType.TEXT, config.getType());
        assertEquals("CONFIDENTIAL", config.getText());
        assertEquals(WatermarkConfig.Position.CENTER, config.getPosition());
        assertEquals(0.3f, config.getOpacity(), 0.001f);
        assertEquals(45f, config.getRotation(), 0.001f);
        assertEquals(72, config.getFontSize());
        assertEquals("Helvetica", config.getFontName());
        assertEquals(Color.GRAY, config.getColor());
        assertEquals(1.0f, config.getScale(), 0.001f);
        assertTrue(config.isApplyToAllPages());
        assertEquals("", config.getPageRange());
        assertEquals(0, config.getCustomX(), 0.001f);
        assertEquals(0, config.getCustomY(), 0.001f);
    }

    @Test
    void testSetType() {
        config.setType(WatermarkConfig.WatermarkType.IMAGE);
        assertEquals(WatermarkConfig.WatermarkType.IMAGE, config.getType());
    }

    @Test
    void testSetText() {
        config.setText("DRAFT");
        assertEquals("DRAFT", config.getText());
    }

    @Test
    void testSetImageFile() {
        File imageFile = new File("logo.png");
        config.setImageFile(imageFile);
        assertEquals(imageFile, config.getImageFile());
    }

    @Test
    void testSetPosition() {
        config.setPosition(WatermarkConfig.Position.TOP_LEFT);
        assertEquals(WatermarkConfig.Position.TOP_LEFT, config.getPosition());
    }

    @Test
    void testSetCustomCoordinates() {
        config.setCustomX(100.5f);
        config.setCustomY(200.75f);
        assertEquals(100.5f, config.getCustomX(), 0.001f);
        assertEquals(200.75f, config.getCustomY(), 0.001f);
    }

    @Test
    void testSetOpacityWithinRange() {
        config.setOpacity(0.5f);
        assertEquals(0.5f, config.getOpacity(), 0.001f);
    }

    @Test
    void testSetOpacityBelowZero() {
        config.setOpacity(-0.5f);
        assertEquals(0.0f, config.getOpacity(), 0.001f);
    }

    @Test
    void testSetOpacityAboveOne() {
        config.setOpacity(1.5f);
        assertEquals(1.0f, config.getOpacity(), 0.001f);
    }

    @Test
    void testSetRotation() {
        config.setRotation(90f);
        assertEquals(90f, config.getRotation(), 0.001f);
    }

    @Test
    void testSetFontSize() {
        config.setFontSize(48);
        assertEquals(48, config.getFontSize());
    }

    @Test
    void testSetFontName() {
        config.setFontName("Arial");
        assertEquals("Arial", config.getFontName());
    }

    @Test
    void testSetColor() {
        config.setColor(Color.RED);
        assertEquals(Color.RED, config.getColor());
    }

    @Test
    void testSetScale() {
        config.setScale(2.0f);
        assertEquals(2.0f, config.getScale(), 0.001f);
    }

    @Test
    void testSetApplyToAllPages() {
        config.setApplyToAllPages(false);
        assertFalse(config.isApplyToAllPages());
    }

    @Test
    void testSetPageRange() {
        config.setPageRange("1-5,10");
        assertEquals("1-5,10", config.getPageRange());
    }

    @Test
    void testPositionDisplayNames() {
        assertEquals("Center", WatermarkConfig.Position.CENTER.getDisplayName());
        assertEquals("Top Left", WatermarkConfig.Position.TOP_LEFT.getDisplayName());
        assertEquals("Bottom Right", WatermarkConfig.Position.BOTTOM_RIGHT.getDisplayName());
        assertEquals("Custom", WatermarkConfig.Position.CUSTOM.getDisplayName());
    }
}
