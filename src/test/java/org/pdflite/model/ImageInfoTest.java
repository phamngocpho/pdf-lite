package org.pdflite.model;

import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ImageInfo.
 */
class ImageInfoTest {

    @Test
    void testImageInfoCreation() {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ImageInfo info = new ImageInfo(0, 50.0f, 75.0f, 200.0f, 150.0f, image);
        
        assertEquals(0, info.pageIndex());
        assertEquals(50.0f, info.xPosition());
        assertEquals(75.0f, info.yPosition());
        assertEquals(200.0f, info.width());
        assertEquals(150.0f, info.height());
        assertEquals(image, info.image());
    }

    @Test
    void testGetPdfBounds() {
        BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        ImageInfo info = new ImageInfo(1, 100.0f, 200.0f, 300.0f, 400.0f, image);
        
        Rectangle2D.Float bounds = info.getPdfBounds();
        assertEquals(100.0f, bounds.x);
        assertEquals(200.0f, bounds.y);
        assertEquals(300.0f, bounds.width);
        assertEquals(400.0f, bounds.height);
    }

    @Test
    void testGetXPosition() {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ImageInfo info = new ImageInfo(0, 123.45f, 0.0f, 100.0f, 100.0f, image);
        
        assertEquals(123.45f, info.getXPosition());
    }

    @Test
    void testGetYPosition() {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ImageInfo info = new ImageInfo(0, 0.0f, 678.90f, 100.0f, 100.0f, image);
        
        assertEquals(678.90f, info.getYPosition());
    }

    @Test
    void testImageInfoWithDifferentPageIndex() {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        ImageInfo info = new ImageInfo(5, 10.0f, 20.0f, 30.0f, 40.0f, image);
        
        assertEquals(5, info.pageIndex());
    }
}
