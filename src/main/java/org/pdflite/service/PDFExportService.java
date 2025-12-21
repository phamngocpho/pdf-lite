package org.pdflite.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for exporting PDF documents to various formats.
 * Supports exporting to images (PNG, JPG) and text files.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class PDFExportService {
    private static final Logger logger = LoggerFactory.getLogger(PDFExportService.class);

    private static final float DEFAULT_DPI = 300f; // High quality for export

    public enum ImageFormat {
        PNG("png", "PNG Image"),
        JPG("jpg", "JPEG Image");

        private final String extension;
        private final String description;

        ImageFormat(String extension, String description) {
            this.extension = extension;
            this.description = description;
        }

        public String getExtension() {
            return extension;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Exports a single page to an image file.
     *
     * @param pdfDoc     the PDF document
     * @param pageIndex  the zero-based page index
     * @param outputFile the output image file
     * @param format     the image format (PNG or JPG)
     * @param dpi        the DPI for rendering (higher = better quality)
     * @throws IOException if export fails
     */
    public void exportPageToImage(PDFDocument pdfDoc, int pageIndex, File outputFile,
                                  ImageFormat format, float dpi) throws IOException {
        if (pageIndex < 0 || pageIndex >= pdfDoc.getTotalPages()) {
            throw new IllegalArgumentException("Invalid page index: " + pageIndex);
        }

        logger.info("Exporting page {} to {} at {} DPI", pageIndex, format, dpi);

        PDDocument document = pdfDoc.getDocument();
        PDFRenderer renderer = new PDFRenderer(document);

        // Render page to image
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);

        // Save image
        ImageIO.write(image, format.getExtension(), outputFile);

        logger.info("Page exported successfully to: {}", outputFile.getAbsolutePath());
    }

    /**
     * Exports multiple pages to image files.
     *
     * @param pdfDoc          the PDF document
     * @param pageIndices     list of zero-based page indices to export
     * @param outputDirectory the directory to save images
     * @param fileNamePrefix  the prefix for output files (e.g., "page")
     * @param format          the image format
     * @param dpi             the DPI for rendering
     * @return list of created files
     * @throws IOException if export fails
     */
    public List<File> exportPagesToImages(PDFDocument pdfDoc, List<Integer> pageIndices,
                                          File outputDirectory, String fileNamePrefix,
                                          ImageFormat format, float dpi) throws IOException {
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        List<File> exportedFiles = new ArrayList<>();
        PDDocument document = pdfDoc.getDocument();
        PDFRenderer renderer = new PDFRenderer(document);

        logger.info("Exporting {} pages to images", pageIndices.size());

        for (int i = 0; i < pageIndices.size(); i++) {
            int pageIndex = pageIndices.get(i);

            if (pageIndex < 0 || pageIndex >= pdfDoc.getTotalPages()) {
                logger.warn("Skipping invalid page index: {}", pageIndex);
                continue;
            }

            // Create output file name
            String fileName = String.format("%s_%d.%s", fileNamePrefix, pageIndex + 1, format.getExtension());
            File outputFile = new File(outputDirectory, fileName);

            // Render and save
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
            ImageIO.write(image, format.getExtension(), outputFile);

            exportedFiles.add(outputFile);
            logger.debug("Exported page {} to {}", pageIndex + 1, outputFile.getName());
        }

        logger.info("Successfully exported {} pages", exportedFiles.size());
        return exportedFiles;
    }

    /**
     * Exports all pages to image files.
     *
     * @param pdfDoc          the PDF document
     * @param outputDirectory the directory to save images
     * @param fileNamePrefix  the prefix for output files
     * @param format          the image format
     * @param dpi             the DPI for rendering
     * @return list of created files
     * @throws IOException if export fails
     */
    public List<File> exportAllPagesToImages(PDFDocument pdfDoc, File outputDirectory,
                                             String fileNamePrefix, ImageFormat format,
                                             float dpi) throws IOException {
        List<Integer> allPages = new ArrayList<>();
        for (int i = 0; i < pdfDoc.getTotalPages(); i++) {
            allPages.add(i);
        }
        return exportPagesToImages(pdfDoc, allPages, outputDirectory, fileNamePrefix, format, dpi);
    }

    /**
     * Exports current page to image with default DPI.
     */
    public void exportCurrentPageToImage(PDFDocument pdfDoc, File outputFile, ImageFormat format)
            throws IOException {
        exportPageToImage(pdfDoc, pdfDoc.getCurrentPage(), outputFile, format, DEFAULT_DPI);
    }

    /**
     * Exports PDF text content to a text file.
     *
     * @param pdfDoc     the PDF document
     * @param outputFile the output text file
     * @param startPage  the starting page (1-based), or -1 for all pages
     * @param endPage    the ending page (1-based), or -1 for all pages
     * @throws IOException if export fails
     */
    public void exportToText(PDFDocument pdfDoc, File outputFile, int startPage, int endPage)
            throws IOException {
        logger.info("Exporting PDF to text file: {}", outputFile.getAbsolutePath());

        PDDocument document = pdfDoc.getDocument();
        PDFTextStripper stripper = new PDFTextStripper();

        // Set page range if specified
        if (startPage > 0 && endPage > 0) {
            stripper.setStartPage(startPage);
            stripper.setEndPage(Math.min(endPage, document.getNumberOfPages()));
        }

        // Extract text
        String text = stripper.getText(document);

        // Write to file
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(text);
        }

        logger.info("Text exported successfully. {} characters written.", text.length());
    }

    /**
     * Exports all text from PDF to a text file.
     */
    public void exportAllToText(PDFDocument pdfDoc, File outputFile) throws IOException {
        exportToText(pdfDoc, outputFile, -1, -1);
    }

    /**
     * Exports current page text to a text file.
     */
    public void exportCurrentPageToText(PDFDocument pdfDoc, File outputFile) throws IOException {
        int currentPage = pdfDoc.getCurrentPage() + 1; // Convert to 1-based
        exportToText(pdfDoc, outputFile, currentPage, currentPage);
    }

    /**
     * Gets the default DPI for image export.
     */
    public float getDefaultDPI() {
        return DEFAULT_DPI;
    }
}
