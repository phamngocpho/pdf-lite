package org.pdflite.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ImageInsert.
 */
class ImageInsertTest {

    @TempDir
    Path tempDir;

    private File createTempImageFile(String name) throws IOException {
        Path imagePath = tempDir.resolve(name);
        Files.write(imagePath, new byte[]{1, 2, 3});
        return imagePath.toFile();
    }

    @Test
    void testImageInsertCreation() throws IOException {
        File imageFile = createTempImageFile("test.png");
        ImagePlacement placement = ImagePlacement.forImage(0, 100, 200, 300, 400, imageFile);
        ImageInsert insert = new ImageInsert(0, System.currentTimeMillis(), "Test insert", placement);
        
        assertEquals(0, insert.pageIndex());
        assertTrue(insert.timestamp() > 0);
        assertEquals("Test insert", insert.description());
        assertEquals(placement, insert.placement());
    }

    @Test
    void testImageInsertCreateMethod() throws IOException {
        File imageFile = createTempImageFile("image.jpg");
        ImagePlacement placement = ImagePlacement.forImage(1, 50, 75, 150, 200, imageFile);
        ImageInsert insert = ImageInsert.create(1, placement);
        
        assertEquals(1, insert.pageIndex());
        assertTrue(insert.timestamp() > 0);
        assertNotNull(insert.description());
        assertTrue(insert.description().contains("page 2"));
        assertTrue(insert.description().contains("image.jpg"));
        assertEquals(placement, insert.placement());
    }

    @Test
    void testImageInsertTimestamp() throws IOException {
        long before = System.currentTimeMillis();
        File imageFile = createTempImageFile("test.png");
        ImagePlacement placement = ImagePlacement.forImage(0, 0, 0, 100, 100, imageFile);
        ImageInsert insert = ImageInsert.create(0, placement);
        long after = System.currentTimeMillis();
        
        assertTrue(insert.timestamp() >= before);
        assertTrue(insert.timestamp() <= after);
    }

    @Test
    void testImageInsertWithStamp() throws IOException {
        File imageFile = createTempImageFile("stamp.png");
        ImagePlacement placement = ImagePlacement.forStamp(2, 10, 20, 80, 60, imageFile);
        ImageInsert insert = ImageInsert.create(2, placement);
        
        assertEquals(2, insert.pageIndex());
        assertTrue(insert.placement().isStamp());
    }

    @Test
    void testImageInsertDescription() throws IOException {
        File imageFile = createTempImageFile("my-image.png");
        ImagePlacement placement = ImagePlacement.forImage(5, 0, 0, 100, 100, imageFile);
        ImageInsert insert = ImageInsert.create(5, placement);
        
        String desc = insert.description();
        assertTrue(desc.contains("page 6")); // 5 + 1
        assertTrue(desc.contains("my-image.png"));
    }
}
