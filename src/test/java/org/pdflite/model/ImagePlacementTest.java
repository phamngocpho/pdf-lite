package org.pdflite.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ImagePlacement.
 */
class ImagePlacementTest {

    @TempDir
    Path tempDir;

    private File createTempImageFile() throws IOException {
        Path imagePath = tempDir.resolve("test-image.png");
        Files.write(imagePath, new byte[]{1, 2, 3});
        return imagePath.toFile();
    }

    @Test
    void testImagePlacementCreation() throws IOException {
        File imageFile = createTempImageFile();
        ImagePlacement placement = new ImagePlacement(0, 100, 200, 300, 400, imageFile, false);
        
        assertEquals(0, placement.pageIndex());
        assertEquals(100, placement.x());
        assertEquals(200, placement.y());
        assertEquals(300, placement.width());
        assertEquals(400, placement.height());
        assertEquals(imageFile, placement.imageFile());
        assertFalse(placement.isStamp());
    }

    @Test
    void testForImage() throws IOException {
        File imageFile = createTempImageFile();
        ImagePlacement placement = ImagePlacement.forImage(1, 50, 75, 150, 200, imageFile);
        
        assertEquals(1, placement.pageIndex());
        assertEquals(50, placement.x());
        assertEquals(75, placement.y());
        assertEquals(150, placement.width());
        assertEquals(200, placement.height());
        assertFalse(placement.isStamp());
    }

    @Test
    void testForStamp() throws IOException {
        File imageFile = createTempImageFile();
        ImagePlacement placement = ImagePlacement.forStamp(2, 10, 20, 100, 80, imageFile);
        
        assertEquals(2, placement.pageIndex());
        assertTrue(placement.isStamp());
    }

    @Test
    void testArea() throws IOException {
        File imageFile = createTempImageFile();
        ImagePlacement placement = new ImagePlacement(0, 0, 0, 100, 50, imageFile, false);
        
        assertEquals(5000, placement.area());
    }

    @Test
    void testOverlaps() throws IOException {
        File imageFile = createTempImageFile();
        ImagePlacement p1 = new ImagePlacement(0, 0, 0, 100, 100, imageFile, false);
        ImagePlacement p2 = new ImagePlacement(0, 50, 50, 100, 100, imageFile, false);
        ImagePlacement p3 = new ImagePlacement(0, 200, 200, 100, 100, imageFile, false);
        ImagePlacement p4 = new ImagePlacement(1, 0, 0, 100, 100, imageFile, false);
        
        assertTrue(p1.overlaps(p2));
        assertTrue(p2.overlaps(p1));
        assertFalse(p1.overlaps(p3));
        assertFalse(p1.overlaps(p4)); // Different pages
    }

    @Test
    void testValidationNegativePageIndex() throws IOException {
        File imageFile = createTempImageFile();
        assertThrows(IllegalArgumentException.class, 
            () -> new ImagePlacement(-1, 0, 0, 100, 100, imageFile, false));
    }

    @Test
    void testValidationNonPositiveWidth() throws IOException {
        File imageFile = createTempImageFile();
        assertThrows(IllegalArgumentException.class, 
            () -> new ImagePlacement(0, 0, 0, 0, 100, imageFile, false));
    }

    @Test
    void testValidationNonPositiveHeight() throws IOException {
        File imageFile = createTempImageFile();
        assertThrows(IllegalArgumentException.class, 
            () -> new ImagePlacement(0, 0, 0, 100, -10, imageFile, false));
    }

    @Test
    void testValidationNullImageFile() {
        assertThrows(IllegalArgumentException.class, 
            () -> new ImagePlacement(0, 0, 0, 100, 100, null, false));
    }

    @Test
    void testValidationNonExistentFile() {
        File nonExistent = new File("non-existent-file.png");
        assertThrows(IllegalArgumentException.class, 
            () -> new ImagePlacement(0, 0, 0, 100, 100, nonExistent, false));
    }
}
