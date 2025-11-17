package org.pdflite.service;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.pdflite.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Service class for handling PDF operations and document management.
 * <p>
 * This service provides core functionality for working with PDF documents,
 * including:
 * <ul>
 * <li>Opening and closing PDF files</li>
 * <li>Rendering PDF pages as images with configurable DPI and scaling</li>
 * <li>Caching rendered images for improved performance</li>
 * <li>Text extraction and search functionality</li>
 * </ul>
 * </p>
 * <p>
 * This class uses Apache PDFBox library for PDF processing and JavaFX
 * for image rendering in the UI.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 * @see PDFDocument
 * @see org.apache.pdfbox.pdmodel.PDDocument
 */
public class PDFService {
    private static final Logger logger = LoggerFactory.getLogger(PDFService.class);

    /**
     * Default DPI (Dots Per Inch) for rendering PDF pages.
     * <p>
     * This value is used as the base DPI before applying any scaling factor.
     * </p>
     */
    private static final float DEFAULT_DPI = 150f;

    /**
     * Opens a PDF file and creates a PDFDocument wrapper.
     * <p>
     * This method loads the PDF file using Apache PDFBox and wraps it
     * in a {@link PDFDocument} object that provides additional functionality
     * such as page navigation, zoom control, and image caching.
     * </p>
     *
     * @param file the PDF file to open
     * @return a PDFDocument object representing the opened PDF
     * @throws IOException              if the file cannot be read or is not a valid
     *                                  PDF
     * @throws IllegalArgumentException if the file is null
     */
    public PDFDocument openPDF(File file) throws IOException {
        logger.info("Opening PDF file: {}", file.getAbsolutePath());
        PDDocument document = Loader.loadPDF(file);
        return new PDFDocument(document, file);
    }

    /**
     * Renders a specific page of the PDF as a JavaFX Image with optimized settings.
     * <p>
     * This method renders the specified page at the given scale with RGB image type
     * for better performance. It first checks the document's cache for a previously
     * rendered version of the page at the same scale. If a cached version exists,
     * it is
     * returned immediately. Otherwise, the page is rendered using Apache PDFBox and
     * the result is cached for future use.
     * </p>
     * <p>
     * The actual DPI used for rendering is calculated as:
     * {@code DEFAULT_DPI * scale}.
     * Higher scale values produce higher quality images but require more memory.
     * </p>
     *
     * @param pdfDoc    the PDF document containing the page to render
     * @param pageIndex the zero-based index of the page to render
     * @param scale     the scaling factor to apply (1.0 = 100%)
     * @return a JavaFX Image object containing the rendered page
     * @throws IOException              if an error occurs during rendering
     * @throws IllegalArgumentException if the page index is invalid
     * @see PDFDocument#getCachedImage(int, float)
     * @see PDFDocument#cacheImage(int, float, Image)
     */
    public Image renderPage(PDFDocument pdfDoc, int pageIndex, float scale) throws IOException {
        if (pageIndex < 0 || pageIndex >= pdfDoc.getTotalPages()) {
            throw new IllegalArgumentException("Invalid page index: " + pageIndex);
        }

        // Check cache first
        Image cachedImage = pdfDoc.getCachedImage(pageIndex, scale);
        if (cachedImage != null) {
            logger.debug("Using cached image for page {}", pageIndex);
            return cachedImage;
        }

        // Create renderer with optimized settings
        PDFRenderer renderer = new PDFRenderer(pdfDoc.getDocument());
        float dpi = DEFAULT_DPI * scale;

        logger.debug("Rendering page {} with DPI {}", pageIndex, dpi);

        // Render with RGB image type for better performance (no alpha channel overhead)
        PDPage page = pdfDoc.getDocument().getPage(pageIndex);

        // 1. Lấy góc xoay gốc của file PDF
        int originalRotation = page.getRotation();
        // 2. Lấy góc xoay người dùng chọn từ Model
        int userRotation = pdfDoc.getRotation();
        // 3. Tính tổng góc xoay (cộng dồn)
        int finalRotation = (originalRotation + userRotation) % 360;
        // 4. Set góc xoay tạm thời để render
        page.setRotation(finalRotation);
        BufferedImage bufferedImage;
        try {
            bufferedImage = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
        } finally {
            page.setRotation(originalRotation);
        }

        Image image = SwingFXUtils.toFXImage(bufferedImage, null);
        pdfDoc.cacheImage(pageIndex, scale, image);

        return image;
    }

    /**
     * Closes the PDF document and releases resources.
     * <p>
     * This method closes the underlying PDFBox document and releases any
     * system resources associated with it. It should be called when the
     * document is no longer needed to prevent resource leaks.
     * </p>
     * <p>
     * This method is null-safe and will not throw an exception if the
     * document or its internal PDDocument is null.
     * </p>
     *
     * @param pdfDoc the PDF document to close, may be null
     */
    public void closePDF(PDFDocument pdfDoc) {
        if (pdfDoc != null && pdfDoc.getDocument() != null) {
            try {
                pdfDoc.getDocument().close();
                logger.info("PDF document closed");
            } catch (IOException e) {
                logger.error("Error closing PDF document", e);
            }
        }
    }

    /**
     * Searches for text in the entire PDF document.
     * <p>
     * This method extracts all text from the PDF and performs a case-insensitive
     * search for the specified term. It returns true if the term is found anywhere
     * in the document.
     * </p>
     * <p>
     * Note: For finding specific page numbers where text appears, use
     * {@link #searchTextInPages(PDFDocument, String)} instead.
     * </p>
     *
     * @param pdfDoc     the PDF document to search
     * @param searchTerm the text to search for (case-insensitive)
     * @return true if the search term is found, false otherwise
     * @throws IOException if an error occurs while extracting text
     * @see #searchTextInPages(PDFDocument, String)
     */
    public boolean searchText(PDFDocument pdfDoc, String searchTerm) throws IOException {
        if (searchTerm == null || searchTerm.isEmpty()) {
            return false;
        }

        logger.info("Searching for text: {}", searchTerm);

        PDFTextStripper stripper = new PDFTextStripper();
        String allText = stripper.getText(pdfDoc.getDocument());

        return allText.toLowerCase().contains(searchTerm.toLowerCase());
    }

    /**
     * Extracts text content from a specific page of the PDF.
     * <p>
     * This method uses PDFBox's text extraction capabilities to retrieve
     * all text content from the specified page. The text is returned as a
     * single string, preserving the layout as much as possible.
     * </p>
     *
     * @param pdfDoc    the PDF document
     * @param pageIndex the zero-based index of the page
     * @return the extracted text from the page
     * @throws IOException              if an error occurs during text extraction
     * @throws IllegalArgumentException if the page index is invalid
     */
    public String extractTextFromPage(PDFDocument pdfDoc, int pageIndex) throws IOException {
        if (pageIndex < 0 || pageIndex >= pdfDoc.getTotalPages()) {
            throw new IllegalArgumentException("Invalid page index: " + pageIndex);
        }

        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        return stripper.getText(pdfDoc.getDocument());
    }

    /**
     * Searches for text across all pages and returns the page numbers where it's
     * found.
     * <p>
     * This method performs a case-insensitive search across all pages of the
     * document.
     * It returns a list of zero-based page indices where the search term appears.
     * The search is performed sequentially on each page.
     * </p>
     * <p>
     * Example usage:
     * 
     * <pre>
     * List&lt;Integer&gt; pages = pdfService.searchTextInPages(document, "important");
     * // pages contains [0, 5, 12] if the term appears on pages 1, 6, and 13
     * </pre>
     * </p>
     *
     * @param pdfDoc     the PDF document to search
     * @param searchTerm the text to search for (case-insensitive)
     * @return a list of zero-based page indices where the search term is found
     * @throws IOException if an error occurs while extracting text from any page
     * @see #extractTextFromPage(PDFDocument, int)
     */
    public List<Integer> searchTextInPages(PDFDocument pdfDoc, String searchTerm) throws IOException {
        List<Integer> matchingPages = new ArrayList<>();

        if (searchTerm == null || searchTerm.isEmpty()) {
            return matchingPages;
        }

        String lowerSearchTerm = searchTerm.toLowerCase();

        for (int i = 0; i < pdfDoc.getTotalPages(); i++) {
            String pageText = extractTextFromPage(pdfDoc, i);
            if (pageText.toLowerCase().contains(lowerSearchTerm)) {
                matchingPages.add(i);
            }
        }

        logger.info("Found '{}' on {} page(s)", searchTerm, matchingPages.size());
        return matchingPages;
    }

    /**
     * Save the current document to its original file.
     */
    /**
     * Save the current document to its original file using INCREMENTAL SAVE.
     * This is REQUIRED when saving to the same file that was loaded.
     */
    /**
     * Save the current document to its original file.
     * IMPORTANT: Uses temporary file approach to avoid corruption.
     */
    public void save(PDFDocument pdfDoc) throws IOException {
        if (pdfDoc == null || pdfDoc.getDocument() == null || pdfDoc.getFile() == null) {
            throw new IOException("No document or target file to save.");
        }

        PDDocument pdDoc = pdfDoc.getDocument();
        File originalFile = pdfDoc.getFile();

        // CRITICAL: Save to temporary file first to avoid corruption
        // when overwriting the file we're reading from
        File tempFile = new File(originalFile.getParent(),
                originalFile.getName() + ".tmp_" + System.currentTimeMillis());

        try {
            flattenAnnotationsToPDF(pdfDoc);

            // Save to temp file
            pdDoc.save(tempFile);
            logger.info("Saved to temporary file: {}", tempFile.getName());

            // Close the document to release file locks
            pdDoc.close();
            logger.info("Document closed, ready to replace original file");

            // Delete original file
            if (originalFile.exists()) {
                boolean deleted = originalFile.delete();
                if (!deleted) {
                    throw new IOException("Could not delete original file: " + originalFile.getName());
                }
                logger.info("Original file deleted");
            }

            // Rename temp file to original name
            boolean renamed = tempFile.renameTo(originalFile);
            if (!renamed) {
                // Try copy instead of rename (works better on some systems)
                java.nio.file.Files.copy(
                        tempFile.toPath(),
                        originalFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
                tempFile.delete();
                logger.info("Temp file copied to original location");
            } else {
                logger.info("Temp file renamed to original name");
            }

            logger.info("Saved PDF to {}", originalFile.getAbsolutePath());

        } catch (Exception e) {
            // Cleanup temp file if something goes wrong
            if (tempFile.exists()) {
                tempFile.delete();
            }
            throw new IOException("Failed to save document: " + e.getMessage(), e);
        }
    }

    /**
     * Save the current document to a specific path.
     */
    public void saveAs(PDFDocument pdfDoc, File targetFile) throws IOException {
        if (pdfDoc == null || pdfDoc.getDocument() == null || targetFile == null) {
            throw new IOException("Invalid save parameters.");
        }
        // Ensure directory exists
        Path parent = targetFile.toPath().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        pdfDoc.getDocument().save(targetFile);
        logger.info("Saved PDF as {}", targetFile.getAbsolutePath());
    }

    /**
     * Delete pages from the PDF document. Indices are 0-based.
     * Pages are removed in descending order to keep indices stable.
     */
    public void deletePages(PDFDocument pdfDoc, Collection<Integer> pageIndices) {
        if (pdfDoc == null || pageIndices == null || pageIndices.isEmpty()) {
            return;
        }

        PDDocument doc = pdfDoc.getDocument();
        int total = doc.getNumberOfPages();

        // Prevent deleting all pages
        long toDelete = pageIndices.stream()
                .filter(i -> i >= 0 && i < total)
                .distinct()
                .count();
        if (toDelete >= total) {
            throw new IllegalArgumentException("Cannot delete all pages of a PDF document.");
        }

        // Delete in descending order
        pageIndices.stream()
                .filter(i -> i >= 0 && i < total)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .forEach(doc::removePage);

        // Clear render cache since page indices/images changed
        pdfDoc.clearCache();

        // Clamp current page to valid range
        int newTotal = doc.getNumberOfPages();
        int current = pdfDoc.getCurrentPage();
        if (current >= newTotal) {
            pdfDoc.setCurrentPage(Math.max(0, newTotal - 1));
        }

        logger.info("Deleted {} page(s). New total pages: {}", toDelete, newTotal);
    }
    private void flattenAnnotationsToPDF(PDFDocument pdfDoc) throws IOException {
        PDDocument doc = pdfDoc.getDocument();
        List<Annotation> annotations = pdfDoc.getAnnotations();

        if (annotations.isEmpty()) return;

        for (Annotation ann : annotations) {
            if (ann instanceof ShapeAnnotation) {
                ShapeAnnotation shape = (ShapeAnnotation) ann;
                if (shape.getPageNumber() >= doc.getNumberOfPages()) continue;

                PDPage page = doc.getPage(shape.getPageNumber());

                try (PDPageContentStream contentStream = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                    Color awtColor = new Color(
                            (float) shape.getColor().getRed(),
                            (float) shape.getColor().getGreen(),
                            (float) shape.getColor().getBlue(),
                            (float) shape.getColor().getOpacity()
                    );
                    contentStream.setStrokingColor(awtColor);
                    contentStream.setLineWidth((float) shape.getLineWidth());

                    float pageHeight = page.getMediaBox().getHeight();
                    float x1 = (float) shape.getX();
                    float y1 = pageHeight - (float) shape.getY();
                    float x2 = (float) shape.getEndX();
                    float y2 = pageHeight - (float) shape.getEndY();

                    if (shape instanceof RectangleAnnotation) {
                        float w = Math.abs(x2 - x1);
                        float h = Math.abs(y1 - y2);
                        float rectX = Math.min(x1, x2);
                        float rectY = Math.min(y1, y2);
                        contentStream.addRect(rectX, rectY, w, h);
                        contentStream.stroke();
                    }
                    else if (shape instanceof ArrowAnnotation) {
                        contentStream.moveTo(x1, y1);
                        contentStream.lineTo(x2, y2);
                        contentStream.stroke();
                    }
                    else if (shape instanceof CircleAnnotation) {
                        float w = Math.abs(x2 - x1);
                        float h = Math.abs(y1 - y2);
                        float rectX = Math.min(x1, x2);
                        float rectY = Math.min(y1, y2);

                        final float k = 0.5522847498f;
                        float rx = w / 2;
                        float ry = h / 2;
                        float cx = rectX + rx;
                        float cy = rectY + ry;

                        contentStream.moveTo(cx + rx, cy);
                        contentStream.curveTo(cx + rx, cy + k * ry, cx + k * rx, cy + ry, cx, cy + ry);
                        contentStream.curveTo(cx - k * rx, cy + ry, cx - rx, cy + k * ry, cx - rx, cy);
                        contentStream.curveTo(cx - rx, cy - k * ry, cx - k * rx, cy - ry, cx, cy - ry);
                        contentStream.curveTo(cx + k * rx, cy - ry, cx + rx, cy - k * ry, cx + rx, cy);
                        contentStream.stroke();
                    }
                } catch (Exception e) {
                    logger.error("Error flattening annotation", e);
                }
            }
        }
        pdfDoc.getAnnotations().clear();
    }

}
