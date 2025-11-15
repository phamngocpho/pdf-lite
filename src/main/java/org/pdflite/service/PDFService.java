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
    private PDFRenderer pdfRenderer;
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
    
    // Tạo lại PDFRenderer mỗi khi mở file mới
    this.pdfRenderer = new PDFRenderer(document);
    
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

    float dpi = DEFAULT_DPI * scale;
    logger.debug("Rendering page {} with DPI {}", pageIndex, dpi);
    
    // Dùng pdfRenderer đã được tạo lại
    BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);

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
 * <p>
 * This method creates a new PDDocument with only the pages that should be kept,
 * rather than removing pages directly. This ensures proper cleanup of PDF objects
 * and prevents corruption when saving large PDF files.
 * </p>
 */
public void deletePages(PDFDocument pdfDoc, Collection<Integer> pageIndices) throws IOException {
    if (pdfDoc == null || pageIndices == null || pageIndices.isEmpty()) {
        return;
    }

    PDDocument oldDoc = pdfDoc.getDocument();
    int total = oldDoc.getNumberOfPages();

    // Create a set of pages to delete for quick lookup
    java.util.Set<Integer> pagesToDelete = new java.util.HashSet<>(
        pageIndices.stream()
            .filter(i -> i >= 0 && i < total)
            .distinct()
            .toList()
    );

    // Prevent deleting all pages
    if (pagesToDelete.size() >= total) {
        throw new IllegalArgumentException("Cannot delete all pages of a PDF document.");
    }

    if (pagesToDelete.isEmpty()) {
        return; // No pages to delete
    }

    // Create a new document and copy only the pages we want to keep
    PDDocument newDoc = new PDDocument();
    PDDocument reloadedDoc = null;

    try {
        // Copy document information
        if (oldDoc.getDocumentInformation() != null) {
            newDoc.getDocumentInformation().setTitle(oldDoc.getDocumentInformation().getTitle());
            newDoc.getDocumentInformation().setAuthor(oldDoc.getDocumentInformation().getAuthor());
            newDoc.getDocumentInformation().setSubject(oldDoc.getDocumentInformation().getSubject());
            newDoc.getDocumentInformation().setCreator(oldDoc.getDocumentInformation().getCreator());
            newDoc.getDocumentInformation().setProducer(oldDoc.getDocumentInformation().getProducer());
        }

        // Copy pages that should be kept
        int pagesCopied = 0;
        for (int i = 0; i < total; i++) {
            if (!pagesToDelete.contains(i)) {
                newDoc.importPage(oldDoc.getPage(i));
                pagesCopied++;
                logger.debug("Copied page {} to new document", i + 1);
            }
        }

        logger.info("Deleted {} page(s), copied {} page(s). New total pages: {}", 
                   pagesToDelete.size(), pagesCopied, newDoc.getNumberOfPages());
        
        // Save to a temporary file first
        File originalFile = pdfDoc.getFile();
        File tempFile = File.createTempFile("pdf_lite_delete_", ".pdf", originalFile.getParentFile());
        
        try {
            // Save new document to temp file
            newDoc.save(tempFile);
            newDoc.close();
            newDoc = null; // Prevent double close
            
            // Close old document
            oldDoc.close();
            oldDoc = null;
            
            // Copy temp file to original location
            Files.copy(tempFile.toPath(), originalFile.toPath(), 
                      java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            
            // Reload document from file to ensure it's in a valid state
            reloadedDoc = Loader.loadPDF(originalFile);
            
            // Update PDFDocument's internal document reference using reflection
            try {
                java.lang.reflect.Field docField = PDFDocument.class.getDeclaredField("document");
                docField.setAccessible(true);
                docField.set(pdfDoc, reloadedDoc);
                logger.debug("Updated PDFDocument with reloaded PDDocument");
            } catch (Exception e) {
                logger.error("Failed to update document reference, closing reloaded document", e);
                if (reloadedDoc != null) {
                    reloadedDoc.close();
                }
                throw new IOException("Failed to update document reference: " + e.getMessage(), e);
            }
            
            // CRITICAL: Recreate PDFRenderer with the NEW reloaded document
            this.pdfRenderer = new PDFRenderer(reloadedDoc);
            logger.debug("Recreated PDFRenderer for updated document");

        } finally {
            // Clean up temp file
            Files.deleteIfExists(tempFile.toPath());
        }

        // Clear render cache since page indices/images changed
        pdfDoc.clearCache();

        // Clamp current page to valid range
        int newTotal = pdfDoc.getTotalPages();
        int current = pdfDoc.getCurrentPage();
        if (current >= newTotal) {
            pdfDoc.setCurrentPage(Math.max(0, newTotal - 1));
        }

    } catch (Exception e) {
        // Ensure resources are closed
        if (newDoc != null) {
            try { newDoc.close(); } catch (IOException ex) { logger.error("Error closing newDoc", ex); }
        }
        if (reloadedDoc != null) {
            try { reloadedDoc.close(); } catch (IOException ex) { logger.error("Error closing reloadedDoc", ex); }
        }
        throw new IOException("Error deleting pages: " + e.getMessage(), e);
    }
}
}
