package org.pdflite.manager;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationRubberStamp;
import org.pdflite.model.ImagePlacement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Manager for image and stamp operations in PDF documents.
 * <p>
 * This manager provides functionality to:
 * <ul>
 *   <li>Load and validate image files</li>
 *   <li>Create PDImageXObject from image files</li>
 *   <li>Place images on PDF pages</li>
 *   <li>Create rubber stamp annotations</li>
 * </ul>
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record ImageManager(UIStateManager uiStateManager) {
    private static final Logger logger = LoggerFactory.getLogger(ImageManager.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    /**
     * Supported image formats.
     */
    private static final List<String> SUPPORTED_FORMATS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif"
    );

    /**
     * Creates a new ImageManager.
     *
     * @param uiStateManager the UI state manager for status updates
     */
    public ImageManager {
    }

    /**
     * Validates an image file.
     * <p>
     * Checks if the file exists, is readable, and has a supported format.
     * </p>
     *
     * @param imageFile the image file to validate
     * @return true if valid, false otherwise
     */
    public boolean validateImageFile(File imageFile) {
        if (imageFile == null) {
            logger.warn("Image file is null");
            return false;
        }

        if (!imageFile.exists()) {
            logger.warn("Image file does not exist: {}", imageFile);
            return false;
        }

        if (!imageFile.canRead()) {
            logger.warn("Image file is not readable: {}", imageFile);
            return false;
        }

        String fileName = imageFile.getName().toLowerCase();
        boolean hasValidExtension = SUPPORTED_FORMATS.stream()
                .anyMatch(fileName::endsWith);

        if (!hasValidExtension) {
            logger.warn("Image file has unsupported format: {}", imageFile);
            return false;
        }

        // Try to read the image to verify it's valid
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                logger.warn("Failed to read image file: {}", imageFile);
                return false;
            }
            logger.debug("Validated image file: {} ({}x{})", imageFile, image.getWidth(), image.getHeight());
            return true;
        } catch (IOException e) {
            logger.warn("Error reading image file: {}", imageFile, e);
            return false;
        }
    }

    /**
     * Creates a PDImageXObject from an image file.
     *
     * @param document  the PDF document
     * @param imageFile the image file
     * @return the PDImageXObject
     * @throws IOException if the image cannot be loaded
     */
    public PDImageXObject createImageXObject(PDDocument document, File imageFile) throws IOException {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        if (!validateImageFile(imageFile)) {
            throw new IOException("Invalid image file: " + imageFile);
        }

        logger.info("Creating PDImageXObject from file: {}", imageFile);
        PDImageXObject imageXObject = PDImageXObject.createFromFile(imageFile.getAbsolutePath(), document);
        logger.debug("Created PDImageXObject: {}x{}", imageXObject.getWidth(), imageXObject.getHeight());

        return imageXObject;
    }

    /**
     * Places an image on a PDF page.
     *
     * @param document  the PDF document
     * @param placement the image placement information
     * @throws IOException if the operation fails
     */
    public void placeImage(PDDocument document, ImagePlacement placement) throws IOException {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        if (placement == null) {
            throw new IllegalArgumentException("Placement cannot be null");
        }

        int pageIndex = placement.pageIndex();
        if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
            throw new IllegalArgumentException("Invalid page index: " + pageIndex);
        }

        PDPage page = document.getPage(pageIndex);
        PDImageXObject imageXObject = createImageXObject(document, placement.imageFile());

        logger.info("Placing image on page {} at ({}, {}) with size {}x{}",
                pageIndex, placement.x(), placement.y(), placement.width(), placement.height());

        try (PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

            contentStream.drawImage(imageXObject,
                    (float) placement.x(),
                    (float) placement.y(),
                    (float) placement.width(),
                    (float) placement.height());
        }

        uiStateManager.updateStatus(lang().getString("image.placedSuccess"));
        logger.debug("Image placed successfully on page {}", pageIndex);
    }

    /**
     * Creates a rubber stamp annotation on a PDF page.
     *
     * @param document  the PDF document
     * @param placement the stamp placement information
     * @throws IOException if the operation fails
     */
    public void createStampAnnotation(PDDocument document, ImagePlacement placement) throws IOException {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        if (placement == null) {
            throw new IllegalArgumentException("Placement cannot be null");
        }
        if (!placement.isStamp()) {
            throw new IllegalArgumentException("Placement must be for a stamp");
        }

        int pageIndex = placement.pageIndex();
        if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
            throw new IllegalArgumentException("Invalid page index: " + pageIndex);
        }

        PDPage page = document.getPage(pageIndex);

        logger.info("Creating rubber stamp annotation on page {} at ({}, {}) with size {}x{}",
                pageIndex, placement.x(), placement.y(), placement.width(), placement.height());

        // Create the rubber stamp annotation
        PDAnnotationRubberStamp stamp = new PDAnnotationRubberStamp();

        // Set the rectangle for the stamp
        PDRectangle rect = new PDRectangle(
                (float) placement.x(),
                (float) placement.y(),
                (float) placement.width(),
                (float) placement.height()
        );
        stamp.setRectangle(rect);

        // Set stamp name (predefined stamp type)
        stamp.setName(PDAnnotationRubberStamp.NAME_APPROVED);

        // Add the annotation to the page
        List<PDAnnotation> annotations = page.getAnnotations();
        annotations.add(stamp);

        uiStateManager.updateStatus(lang().getString("image.stampSuccess"));
        logger.debug("Stamp annotation created successfully on page {}", pageIndex);
    }

    /**
     * Gets the dimensions of an image file.
     *
     * @param imageFile the image file
     * @return an array with [width, height], or null if the image cannot be read
     */
    public int[] getImageDimensions(File imageFile) {
        if (!validateImageFile(imageFile)) {
            return null;
        }

        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                return null;
            }
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (IOException e) {
            logger.warn("Error reading image dimensions: {}", imageFile, e);
            return null;
        }
    }

    /**
     * Calculates the scaled dimensions to fit within a maximum size while preserving aspect ratio.
     *
     * @param originalWidth  the original width
     * @param originalHeight the original height
     * @param maxWidth       the maximum width
     * @param maxHeight      the maximum height
     * @return an array with [scaledWidth, scaledHeight]
     */
    public double[] calculateScaledDimensions(double originalWidth, double originalHeight,
                                              double maxWidth, double maxHeight) {
        if (originalWidth <= 0 || originalHeight <= 0) {
            throw new IllegalArgumentException("Original dimensions must be positive");
        }
        if (maxWidth <= 0 || maxHeight <= 0) {
            throw new IllegalArgumentException("Max dimensions must be positive");
        }

        double aspectRatio = originalWidth / originalHeight;

        double scaledWidth = maxWidth;
        double scaledHeight = maxWidth / aspectRatio;

        if (scaledHeight > maxHeight) {
            scaledHeight = maxHeight;
            scaledWidth = maxHeight * aspectRatio;
        }

        return new double[]{scaledWidth, scaledHeight};
    }

    /**
     * Gets the list of supported image formats.
     *
     * @return the list of supported formats
     */
    public List<String> getSupportedFormats() {
        return List.copyOf(SUPPORTED_FORMATS);
    }
}
