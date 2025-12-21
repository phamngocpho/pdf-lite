package org.pdflite.manager;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Manager for PDF compression and optimization.
 * Provides different compression levels to reduce PDF file size.
 */
public class CompressionManager {
    private static final Logger logger = LoggerFactory.getLogger(CompressionManager.class);

    /**
     * Compression quality levels.
     */
    public enum CompressionLevel {
        LOW(0.9f, "Low compression (high quality)"),
        MEDIUM(0.7f, "Medium compression (balanced)"),
        HIGH(0.5f, "High compression (smaller size)"),
        MAXIMUM(0.3f, "Maximum compression (lowest quality)");

        private final float quality;
        private final String description;

        CompressionLevel(float quality, String description) {
            this.quality = quality;
            this.description = description;
        }

        public float getQuality() {
            return quality;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Compresses a PDF document by optimizing images.
     *
     * @param pdfDocument the PDF document to compress
     * @param level       the compression level
     * @return true if compression was successful
     */
    public boolean compressPDF(PDFDocument pdfDocument, CompressionLevel level) {
        if (pdfDocument == null) {
            logger.warn("Cannot compress: document is null");
            return false;
        }

        PDDocument document = pdfDocument.getDocument();
        if (document == null) {
            logger.warn("Cannot compress: underlying PDDocument is null");
            return false;
        }

        try {
            int totalImages = 0;
            int compressedImages = 0;

            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                if (resources == null) {
                    continue;
                }

                for (var name : resources.getXObjectNames()) {
                    PDXObject xObject = resources.getXObject(name);

                    if (xObject instanceof PDImageXObject image) {
                        totalImages++;

                        try {
                            PDImageXObject compressedImage = compressImage(image, level, document);
                            if (compressedImage != null) {
                                resources.put(name, compressedImage);
                                compressedImages++;
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to compress image: {}", name, e);
                        }
                    }
                }
            }

            logger.info("Compressed {} out of {} images with {} level",
                    compressedImages, totalImages, level);
            return compressedImages > 0;

        } catch (Exception e) {
            logger.error("Error compressing PDF", e);
            return false;
        }
    }

    /**
     * Compresses a single image.
     *
     * @param image    the image to compress
     * @param level    the compression level
     * @param document the PDF document
     * @return compressed image or null if compression failed
     */
    private PDImageXObject compressImage(PDImageXObject image, CompressionLevel level, PDDocument document)
            throws IOException {

        // Skip if image is already small
        if (image.getWidth() < 100 || image.getHeight() < 100) {
            return null;
        }

        BufferedImage bufferedImage = image.getImage();
        if (bufferedImage == null) {
            return null;
        }

        // Convert to RGB if needed (JPEG doesn't support all color spaces)
        BufferedImage rgbImage = bufferedImage;
        if (bufferedImage.getType() != BufferedImage.TYPE_INT_RGB) {
            rgbImage = new BufferedImage(
                    bufferedImage.getWidth(),
                    bufferedImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );
            java.awt.Graphics2D g = rgbImage.createGraphics();
            g.drawImage(bufferedImage, 0, 0, null);
            g.dispose();
        }

        // Compress using JPEG
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            return null;
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(level.getQuality());
        }

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(rgbImage, null, null), param);
        } finally {
            writer.dispose();
        }

        // Create new PDImageXObject from compressed data
        byte[] compressedData = baos.toByteArray();

        // Only use compressed version if it's actually smaller
        if (compressedData.length < image.getStream().getLength()) {
            return PDImageXObject.createFromByteArray(
                    document,
                    compressedData,
                    "compressed"
            );
        }

        return null;
    }

    /**
     * Estimates the compression ratio for a document.
     *
     * @param pdfDocument the document to analyze
     * @param level       the compression level
     * @return estimated size reduction percentage (0-100)
     */
    public int estimateCompression(PDFDocument pdfDocument, CompressionLevel level) {
        if (pdfDocument == null) {
            return 0;
        }

        PDDocument document = pdfDocument.getDocument();
        if (document == null) {
            return 0;
        }

        try {
            int totalImages = 0;
            long totalImageSize = 0;
            long totalDocumentSize = 0;

            // Calculate total document size (approximate)
            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                if (resources == null) {
                    continue;
                }

                for (var name : resources.getXObjectNames()) {
                    PDXObject xObject = resources.getXObject(name);

                    if (xObject instanceof PDImageXObject image) {
                        totalImages++;
                        long imageSize = image.getStream().getLength();
                        totalImageSize += imageSize;
                    }
                }
            }

            if (totalImages == 0) {
                return 0;
            }

            // Estimate compression based on quality level
            // JPEG compression typically achieves these ratios:
            // - Quality 0.9 (LOW): ~10-20% reduction
            // - Quality 0.7 (MEDIUM): ~30-50% reduction  
            // - Quality 0.5 (HIGH): ~50-70% reduction
            // - Quality 0.3 (MAXIMUM): ~70-85% reduction

            float estimatedImageReduction = switch (level) {
                case LOW -> 0.15f; // 15% reduction
                case MEDIUM -> 0.40f; // 40% reduction
                case HIGH -> 0.60f; // 60% reduction
                case MAXIMUM -> 0.75f; // 75% reduction
                default -> 0.40f;
            };

            // Calculate estimated reduction percentage
            // Note: This is just an estimate - actual results depend on image content
            return Math.round(estimatedImageReduction * 100);

        } catch (Exception e) {
            logger.error("Error estimating compression", e);
            return 0;
        }
    }
}
