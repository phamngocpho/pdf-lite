package org.pdflite.service;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Collection;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service class for handling PDF operations and document management.
 * <p>
 * This service provides core functionality for working with PDF documents, including:
 * <ul>
 *   <li>Opening and closing PDF files</li>
 *   <li>Rendering PDF pages as images with configurable DPI and scaling</li>
 *   <li>Caching rendered images for improved performance</li>
 *   <li>Text extraction and search functionality</li>
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
     * @throws IOException if the file cannot be read or is not a valid PDF
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
     * rendered version of the page at the same scale. If a cached version exists, it is 
     * returned immediately. Otherwise, the page is rendered using Apache PDFBox and 
     * the result is cached for future use.
     * </p>
     * <p>
     * The actual DPI used for rendering is calculated as: {@code DEFAULT_DPI * scale}.
     * Higher scale values produce higher quality images but require more memory.
     * </p>
     *
     * @param pdfDoc the PDF document containing the page to render
     * @param pageIndex the zero-based index of the page to render
     * @param scale the scaling factor to apply (1.0 = 100%)
     * @return a JavaFX Image object containing the rendered page
     * @throws IOException if an error occurs during rendering
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
        BufferedImage bufferedImage = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);

        Image image = SwingFXUtils.toFXImage(bufferedImage, null);

        // Cache the rendered image
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
     * @param pdfDoc the PDF document to search
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
     * @param pdfDoc the PDF document
     * @param pageIndex the zero-based index of the page
     * @return the extracted text from the page
     * @throws IOException if an error occurs during text extraction
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
     * Searches for text across all pages and returns the page numbers where it's found.
     * <p>
     * This method performs a case-insensitive search across all pages of the document.
     * It returns a list of zero-based page indices where the search term appears.
     * The search is performed sequentially on each page.
     * </p>
     * <p>
     * Example usage:
     * <pre>
     * List&lt;Integer&gt; pages = pdfService.searchTextInPages(document, "important");
     * // pages contains [0, 5, 12] if the term appears on pages 1, 6, and 13
     * </pre>
     * </p>
     *
     * @param pdfDoc the PDF document to search
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
    public void save(PDFDocument pdfDoc) throws IOException {
        if (pdfDoc == null || pdfDoc.getDocument() == null || pdfDoc.getFile() == null) {
            throw new IOException("No document or target file to save.");
        }
        pdfDoc.getDocument().save(pdfDoc.getFile());
        logger.info("Saved PDF to {}", pdfDoc.getFile().getAbsolutePath());
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
    // ... (Code của bạn, ví dụ hàm deletePages(...) ) ...

    /**
     * [THÊM HÀM NÀY VÀO]
     * Xoay một trang cụ thể theo một góc.
     */
    public void rotatePage(PDFDocument pdfDoc, int pageIndex, int degrees) {
        if (pdfDoc == null || pageIndex < 0 || pageIndex >= pdfDoc.getTotalPages()) {
            return;
        }
        PDDocument document = pdfDoc.getDocument();
        org.apache.pdfbox.pdmodel.PDPage page = document.getPage(pageIndex);
        int currentRotation = page.getRotation();
        int newRotation = (currentRotation + degrees) % 360;
        if (newRotation < 0) {
            newRotation += 360;
        }
        page.setRotation(newRotation);
        logger.info("Rotated page {} to {} degrees", pageIndex + 1, newRotation);
        pdfDoc.clearCacheForPage(pageIndex);
    }

    /**
     * [THÊM HÀM NÀY VÀO]
     * Xoay tất cả các trang.
     */
    public void rotateAllPages(PDFDocument pdfDoc, int degrees) {
        if (pdfDoc == null) return;
        for (int i = 0; i < pdfDoc.getTotalPages(); i++) {
            org.apache.pdfbox.pdmodel.PDPage page = pdfDoc.getDocument().getPage(i);
            int currentRotation = page.getRotation();
            int newRotation = (currentRotation + degrees) % 360;
            if (newRotation < 0) {
                newRotation += 360;
            }
            page.setRotation(newRotation);
        }
        logger.info("Rotated all pages by {} degrees", degrees);
        pdfDoc.clearCache();
    }
}

