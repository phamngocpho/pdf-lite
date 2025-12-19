package org.pdflite.manager;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.pdflite.model.ImagePlacement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Property-based tests for ImageManager.
 * <p>
 * Tests Properties 18-21: Image operations
 * Validates Requirements 6.2, 6.3, 6.4, 6.5
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
@RunWith(JUnitQuickcheck.class)
public class ImageManagerTest {
    private static final Logger logger = LoggerFactory.getLogger(ImageManagerTest.class);

    /**
     * Property 18: Image Object Creation
     * <p>
     * For any valid image file, creating a PDImageXObject should succeed
     * and the resulting object should have valid dimensions.
     * </p>
     */
    @Property(trials = 100)
    public void imageObjectCreationProperty(int seed) throws IOException {
        // Create a test image with deterministic dimensions
        int width = 100 + (Math.abs(seed) % 400);
        int height = 100 + (Math.abs(seed) % 300);

        File imageFile = createTestImage(width, height, "test-image-" + seed + ".png");

        try {
            UIStateManager uiStateManager = new UIStateManager(null, null, null, null, null);
            ImageManager imageManager = new ImageManager(uiStateManager);

            // Property: Image file should be valid
            assertTrue("Image file should be valid", imageManager.validateImageFile(imageFile));

            // Create a test PDF document
            PDDocument document = new PDDocument();

            try (document) {
                document.addPage(new PDPage(PDRectangle.A4));
                // Property: Should be able to create PDImageXObject
                var imageXObject = imageManager.createImageXObject(document, imageFile);
                assertNotNull("PDImageXObject should not be null", imageXObject);

                // Property: Image dimensions should match
                assertEquals("Image width should match", width, imageXObject.getWidth());
                assertEquals("Image height should match", height, imageXObject.getHeight());

                logger.debug("Image object creation property verified for {}x{}", width, height);
            }
        } finally {
            imageFile.delete();
        }
    }

    /**
     * Property 19: Image Placement
     * <p>
     * For any valid image placement, the image should be successfully
     * placed on the PDF page without errors.
     * </p>
     */
    @Property(trials = 100)
    public void imagePlacementProperty(int seed) throws IOException {
        // Create a test image
        File imageFile = createTestImage(100, 100, "placement-test-" + seed + ".png");

        try {
            // Create placement with deterministic coordinates
            double x = 50 + (Math.abs(seed) % 400);
            double y = 50 + (Math.abs(seed) % 600);
            double width = 50 + (Math.abs(seed) % 100);
            double height = 50 + (Math.abs(seed) % 100);

            ImagePlacement placement = ImagePlacement.forImage(0, x, y, width, height, imageFile);

            // Property: Placement should have correct properties
            assertEquals("Page index should match", 0, placement.pageIndex());
            assertEquals("X should match", x, placement.x(), 0.001);
            assertEquals("Y should match", y, placement.y(), 0.001);
            assertEquals("Width should match", width, placement.width(), 0.001);
            assertEquals("Height should match", height, placement.height(), 0.001);
            assertFalse("Should not be a stamp", placement.isStamp());

            // Create a test PDF document
            PDDocument document = new PDDocument();

            try (document) {
                document.addPage(new PDPage(PDRectangle.A4));
                UIStateManager uiStateManager = new UIStateManager(null, null, null, null, null);
                ImageManager imageManager = new ImageManager(uiStateManager);

                // Property: Should be able to place image without errors
                imageManager.placeImage(document, placement);

                // Property: Document should still be valid after placement
                assertEquals("Document should have 1 page", 1, document.getNumberOfPages());

                logger.debug("Image placement property verified at ({}, {}) with size {}x{}",
                        x, y, width, height);
            }
        } finally {
            imageFile.delete();
        }
    }

    /**
     * Property 20: Stamp Creation
     * <p>
     * For any valid stamp placement, a rubber stamp annotation should be
     * successfully created on the PDF page.
     * </p>
     */
    @Property(trials = 100)
    public void stampCreationProperty(int seed) throws IOException {
        // Create a test image
        File imageFile = createTestImage(100, 100, "stamp-test-" + seed + ".png");

        try {
            // Create stamp placement with deterministic coordinates
            double x = 50 + (Math.abs(seed) % 400);
            double y = 50 + (Math.abs(seed) % 600);
            double width = 50 + (Math.abs(seed) % 100);
            double height = 50 + (Math.abs(seed) % 100);

            ImagePlacement placement = ImagePlacement.forStamp(0, x, y, width, height, imageFile);

            // Property: Placement should be marked as stamp
            assertTrue("Should be a stamp", placement.isStamp());

            // Create a test PDF document
            PDDocument document = new PDDocument();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try {
                UIStateManager uiStateManager = new UIStateManager(null, null, null, null, null);
                ImageManager imageManager = new ImageManager(uiStateManager);

                int annotationsBefore = page.getAnnotations().size();

                // Property: Should be able to create stamp annotation
                imageManager.createStampAnnotation(document, placement);

                // Property: Annotation should be added to page
                int annotationsAfter = page.getAnnotations().size();
                assertEquals("Should have one more annotation", annotationsBefore + 1, annotationsAfter);

                logger.debug("Stamp creation property verified at ({}, {}) with size {}x{}",
                        x, y, width, height);
            } finally {
                document.close();
            }
        } finally {
            imageFile.delete();
        }
    }

    /**
     * Property 21: Image Coordinate Conversion
     * <p>
     * For any image dimensions and maximum size, the scaled dimensions
     * should preserve aspect ratio and fit within the maximum size.
     * </p>
     */
    @Property(trials = 100)
    public void imageCoordinateConversionProperty(int seed) {
        // Generate deterministic dimensions
        double originalWidth = 100 + (Math.abs(seed) % 900);
        double originalHeight = 100 + (Math.abs(seed) % 900);
        double maxWidth = 200;
        double maxHeight = 200;

        UIStateManager uiStateManager = new UIStateManager(null, null, null, null, null);
        ImageManager imageManager = new ImageManager(uiStateManager);

        // Calculate scaled dimensions
        double[] scaled = imageManager.calculateScaledDimensions(
                originalWidth, originalHeight, maxWidth, maxHeight);

        double scaledWidth = scaled[0];
        double scaledHeight = scaled[1];

        // Property: Scaled dimensions should fit within max size
        assertTrue("Scaled width should be <= max width",
                scaledWidth <= maxWidth + 0.001);
        assertTrue("Scaled height should be <= max height",
                scaledHeight <= maxHeight + 0.001);

        // Property: Aspect ratio should be preserved
        double originalAspect = originalWidth / originalHeight;
        double scaledAspect = scaledWidth / scaledHeight;
        assertEquals("Aspect ratio should be preserved",
                originalAspect, scaledAspect, 0.01);

        // Property: At least one dimension should be at max
        boolean widthAtMax = Math.abs(scaledWidth - maxWidth) < 0.001;
        boolean heightAtMax = Math.abs(scaledHeight - maxHeight) < 0.001;
        assertTrue("At least one dimension should be at max", widthAtMax || heightAtMax);

        logger.debug("Image coordinate conversion property verified: {}x{} -> {}x{}",
                originalWidth, originalHeight, scaledWidth, scaledHeight);
    }

    /**
     * Unit test: Image file validation
     */
    @Test
    public void testImageFileValidation() throws IOException {
        UIStateManager uiStateManager = new UIStateManager(null, null, null, null, null);
        ImageManager imageManager = new ImageManager(uiStateManager);

        // Test with valid image
        File validImage = createTestImage(100, 100, "valid-test.png");
        try {
            assertTrue("Valid image should pass validation",
                    imageManager.validateImageFile(validImage));
        } finally {
            validImage.delete();
        }

        // Test with null
        assertFalse("Null file should fail validation",
                imageManager.validateImageFile(null));

        // Test with non-existent file
        File nonExistent = new File("non-existent-image.png");
        assertFalse("Non-existent file should fail validation",
                imageManager.validateImageFile(nonExistent));
    }

    /**
     * Unit test: Image dimensions
     */
    @Test
    public void testGetImageDimensions() throws IOException {
        UIStateManager uiStateManager = new UIStateManager(null, null, null, null, null);
        ImageManager imageManager = new ImageManager(uiStateManager);

        File imageFile = createTestImage(200, 150, "dimensions-test.png");
        try {
            int[] dimensions = imageManager.getImageDimensions(imageFile);
            assertNotNull("Dimensions should not be null", dimensions);
            assertEquals("Width should be 200", 200, dimensions[0]);
            assertEquals("Height should be 150", 150, dimensions[1]);
        } finally {
            imageFile.delete();
        }
    }

    /**
     * Unit test: Supported formats
     */
    @Test
    public void testGetSupportedFormats() {
        UIStateManager uiStateManager = new UIStateManager(null, null, null, null, null);
        ImageManager imageManager = new ImageManager(uiStateManager);

        var formats = imageManager.getSupportedFormats();
        assertNotNull("Formats should not be null", formats);
        assertFalse("Formats should not be empty", formats.isEmpty());
        assertTrue("Should support PNG", formats.contains("png"));
        assertTrue("Should support JPG", formats.contains("jpg"));
    }

    /**
     * Unit test: ImagePlacement validation
     */
    @Test(expected = IllegalArgumentException.class)
    public void testImagePlacementInvalidPageIndex() throws IOException {
        File imageFile = createTestImage(100, 100, "invalid-placement.png");
        try {
            // Should throw exception for negative page index
            ImagePlacement.forImage(-1, 0, 0, 100, 100, imageFile);
        } finally {
            imageFile.delete();
        }
    }

    /**
     * Unit test: ImagePlacement overlap detection
     */
    @Test
    public void testImagePlacementOverlap() throws IOException {
        File imageFile = createTestImage(100, 100, "overlap-test.png");
        try {
            ImagePlacement p1 = ImagePlacement.forImage(0, 0, 0, 100, 100, imageFile);
            ImagePlacement p2 = ImagePlacement.forImage(0, 50, 50, 100, 100, imageFile);
            ImagePlacement p3 = ImagePlacement.forImage(0, 200, 200, 100, 100, imageFile);

            assertTrue("p1 and p2 should overlap", p1.overlaps(p2));
            assertTrue("p2 and p1 should overlap", p2.overlaps(p1));
            assertFalse("p1 and p3 should not overlap", p1.overlaps(p3));
            assertFalse("p3 and p1 should not overlap", p3.overlaps(p1));
        } finally {
            imageFile.delete();
        }
    }

    /**
     * Unit test: Scaled dimensions edge cases
     */
    @Test
    public void testScaledDimensionsEdgeCases() {
        UIStateManager uiStateManager = new UIStateManager(null, null, null, null, null);
        ImageManager imageManager = new ImageManager(uiStateManager);

        // Test square image
        double[] scaled = imageManager.calculateScaledDimensions(100, 100, 50, 50);
        assertEquals("Square should scale to 50x50", 50.0, scaled[0], 0.001);
        assertEquals("Square should scale to 50x50", 50.0, scaled[1], 0.001);

        // Test wide image
        scaled = imageManager.calculateScaledDimensions(200, 100, 100, 100);
        assertEquals("Wide image width should be 100", 100.0, scaled[0], 0.001);
        assertEquals("Wide image height should be 50", 50.0, scaled[1], 0.001);

        // Test tall image
        scaled = imageManager.calculateScaledDimensions(100, 200, 100, 100);
        assertEquals("Tall image width should be 50", 50.0, scaled[0], 0.001);
        assertEquals("Tall image height should be 100", 100.0, scaled[1], 0.001);
    }

    /**
     * Helper method to create a test image file.
     */
    private File createTestImage(int width, int height, String filename) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLUE);
        g.fillOval(10, 10, width - 20, height - 20);
        g.dispose();

        File tempFile = File.createTempFile(filename.replace(".png", ""), ".png");
        tempFile.deleteOnExit();
        ImageIO.write(image, "png", tempFile);

        return tempFile;
    }
}
