package org.pdflite.service;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.pdflite.model.*;
import org.pdflite.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
 * This class uses the Apache PDFBox library for PDF processing and JavaFX
 * for image rendering in the UI.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @see PDFDocument
 * @see org.apache.pdfbox.pdmodel.PDDocument
 * @since 1.0.0
 */
public class PDFService {
    private static final Logger logger = LoggerFactory.getLogger(PDFService.class);

    /**
     * Default DPI (Dots Per Inch) for rendering PDF pages.
     * <p>
     * This value is used as the base DPI before applying any scaling factor.
     * </p>
     */
    private static final float DEFAULT_DPI = Constants.DEFAULT_DPI;

    /**
     * Cache of PDFRenderer instances for each document to improve performance
     * and ensure consistent font rendering across multiple page renders.
     * <p>
     * Reusing renderers prevents recreation of font caches and reduces
     * "No glyph for code X" warnings.
     * </p>
     */
    private final Map<PDDocument, PDFRenderer> rendererCache = new ConcurrentHashMap<>();

    /**
     * Invalidates the cached PDFRenderer for a document.
     * <p>
     * This should be called after modifying the PDF content (e.g., inserting images,
     * stamps, or editing text) to ensure the next render reflects the changes.
     * </p>
     *
     * @param pdfDoc the PDF document whose renderer should be invalidated
     */
    public void invalidateRenderer(PDFDocument pdfDoc) {
        if (pdfDoc != null && pdfDoc.getDocument() != null) {
            PDFRenderer removed = rendererCache.remove(pdfDoc.getDocument());
            if (removed != null) {
                logger.info("Invalidated PDFRenderer cache for document");
            }
        }
    }

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
     * Opens a password-protected PDF file.
     * <p>
     * This method loads an encrypted PDF file using the provided password.
     * If the password is incorrect, an IOException will be thrown.
     * </p>
     *
     * @param file     the PDF file to open
     * @param password the password to decrypt the PDF
     * @return a PDFDocument object representing the opened PDF
     * @throws IOException if the file cannot be read, the password is incorrect,
     *                     or the file is not a valid PDF
     */
    public PDFDocument openPDF(File file, String password) throws IOException {
        logger.info("Opening password-protected PDF file: {}", file.getAbsolutePath());
        PDDocument document = Loader.loadPDF(file, password);

        if (document.isEncrypted()) {
            logger.info("Successfully decrypted PDF");
        }

        return new PDFDocument(document, file);
    }

    /**
     * Checks if a PDF file is encrypted/password-protected.
     *
     * @param file the PDF file to check
     * @return true if the file is encrypted, false otherwise
     * @throws IOException if the file cannot be read
     */
    public boolean isPDFEncrypted(File file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file)) {
            return document.isEncrypted();
        } catch (IOException e) {
            // If we can't load without a password, it's encrypted
            return true;
        }
    }

    /**
     * Gets the access permissions of a PDF document.
     *
     * @param pdfDoc the PDF document
     * @return AccessPermission object, or null if not encrypted
     */
    public AccessPermission getPDFPermissions(PDFDocument pdfDoc) {
        if (pdfDoc != null && pdfDoc.getDocument().isEncrypted()) {
            return pdfDoc.getDocument().getCurrentAccessPermission();
        }
        return null;
    }

    /**
     * Encrypts a PDF file with password protection.
     * <p>
     * This method creates an encrypted copy of the PDF with owner and user passwords.
     * Owner password provides full access, while user password provides restricted access
     * based on the specified permissions.
     * </p>
     *
     * @param inputFile     the source PDF file
     * @param outputFile    the destination for encrypted PDF
     * @param ownerPassword the owner password (full access)
     * @param userPassword  the user password (restricted access)
     * @param permissions   access permissions for user password
     * @throws IOException if encryption fails or file operations fail
     */
    public void encryptPDF(File inputFile, File outputFile, String ownerPassword,
                           String userPassword, AccessPermission permissions) throws IOException {
        logger.info("Encrypting PDF: {}", inputFile.getName());

        try (PDDocument document = Loader.loadPDF(inputFile)) {
            // Create a protection policy with 256-bit AES encryption
            StandardProtectionPolicy protectionPolicy = new StandardProtectionPolicy(
                    ownerPassword,
                    userPassword,
                    permissions
            );

            // Use 256-bit encryption (most secure)
            protectionPolicy.setEncryptionKeyLength(256);

            // Apply encryption
            document.protect(protectionPolicy);

            // Save encrypted document
            document.save(outputFile);

            logger.info("PDF encrypted successfully: {}", outputFile.getName());
        }
    }

    /**
     * Encrypts a PDF with default permissions (allow printing, deny everything else).
     *
     * @param inputFile     the source PDF file
     * @param outputFile    the destination for encrypted PDF
     * @param ownerPassword the owner password (full access)
     * @param userPassword  the user password (restricted access)
     * @throws IOException if encryption fails
     */
    public void encryptPDF(File inputFile, File outputFile, String ownerPassword,
                           String userPassword) throws IOException {
        AccessPermission permissions = new AccessPermission();
        permissions.setCanPrint(true);
        permissions.setCanModify(false);
        permissions.setCanExtractContent(false);
        permissions.setCanModifyAnnotations(false);

        encryptPDF(inputFile, outputFile, ownerPassword, userPassword, permissions);
    }

    /**
     * Removes password protection from a PDF file.
     *
     * @param inputFile  the encrypted PDF file
     * @param outputFile the destination for decrypted PDF
     * @param password   the owner password
     * @throws IOException if decryption fails or the password is incorrect
     */
    public void decryptPDF(File inputFile, File outputFile, String password) throws IOException {
        logger.info("Decrypting PDF: {}", inputFile.getName());

        try (PDDocument document = Loader.loadPDF(inputFile, password)) {
            if (!document.isEncrypted()) {
                throw new IOException("PDF is not encrypted");
            }

            // Check if we have permission to decrypt
            AccessPermission ap = document.getCurrentAccessPermission();
            if (!ap.isOwnerPermission()) {
                throw new IOException("Owner password required to remove encryption");
            }

            // Remove encryption by setting all permissions
            document.setAllSecurityToBeRemoved(true);

            // Save decrypted document
            document.save(outputFile);

            logger.info("PDF decrypted successfully: {}", outputFile.getName());
        }
    }

    /**
     * Renders a specific page of the PDF as a JavaFX Image with optimized settings.
     * <p>
     * This method renders the specified page at the given scale with RGB image types
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

        // Reuse renderer from cache to maintain consistent font rendering
        // This prevents recreation of font caches and reduces glyph warnings
        PDFRenderer renderer = rendererCache.computeIfAbsent(
                pdfDoc.getDocument(),
                PDFRenderer::new
        );
        float dpi = DEFAULT_DPI * scale;

        logger.debug("Rendering page {} with DPI {}", pageIndex, dpi);

        // Synchronize on documents to ensure thread-safe rendering
        // This prevents concurrent modification of page rotation and font resources
        BufferedImage bufferedImage;
        synchronized (pdfDoc.getDocument()) {
            // Render with RGB image types for better performance (no alpha channel overhead)
            PDPage page = pdfDoc.getDocument().getPage(pageIndex);

            // 1. Lấy góc xoay gốc của file PDF
            int originalRotation = page.getRotation();
            // 2. Lấy góc xoay người dùng chọn từ Model
            int userRotation = pdfDoc.getRotation();
            // 3. Tính tổng góc xoay (cộng dồn)
            int finalRotation = (originalRotation + userRotation) % 360;
            // 4. Set góc xoay tạm thời để render
            page.setRotation(finalRotation);
            try {
                bufferedImage = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
            } finally {
                page.setRotation(originalRotation);
            }
        }

        Image image = SwingFXUtils.toFXImage(bufferedImage, null);
        pdfDoc.cacheImage(pageIndex, scale, image);

        return image;
    }

    /**
     * Gets the dimensions of a PDF page at a given scale without rendering the full image.
     * This is more efficient than rendering when you only need the size.
     *
     * @param pdfDoc    the PDF document
     * @param pageIndex the zero-based index of the page
     * @param scale     the scaling factor to apply (1.0 = 100%)
     * @return a double array with [width, height] of the page at the given scale
     * @throws IllegalArgumentException if the page index is invalid
     */
    public double[] getPageDimensions(PDFDocument pdfDoc, int pageIndex, float scale) {
        if (pageIndex < 0 || pageIndex >= pdfDoc.getTotalPages()) {
            throw new IllegalArgumentException("Invalid page index: " + pageIndex);
        }

        PDPage page = pdfDoc.getDocument().getPage(pageIndex);

        // Get rotation-aware dimensions
        int originalRotation = page.getRotation();
        int userRotation = pdfDoc.getRotation();
        int finalRotation = (originalRotation + userRotation) % 360;

        // Get page media box (dimensions)
        org.apache.pdfbox.pdmodel.common.PDRectangle mediaBox = page.getMediaBox();

        // Calculate dimensions based on DPI and scale
        float dpi = DEFAULT_DPI * scale;
        double widthInInches = mediaBox.getWidth() / 72.0; // PDF uses 72 points per inch
        double heightInInches = mediaBox.getHeight() / 72.0;

        double width = widthInInches * dpi;
        double height = heightInInches * dpi;

        // Return swapped dimensions if rotated 90 or 270 degrees
        boolean isRotated = (finalRotation == 90 || finalRotation == 270);
        return isRotated ? new double[]{height, width} : new double[]{width, height};
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
                // Remove renderer from cache before closing
                rendererCache.remove(pdfDoc.getDocument());
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
     * // pages contain [0, 5, 12] if the term appears on pages 1, 6, and 13
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
     * IMPORTANT: Uses temporary file approach to avoid corruption.
     * WARNING: Saving a signed PDF will invalidate all digital signatures!
     */
    public void save(PDFDocument pdfDoc) throws IOException {
        if (pdfDoc == null || pdfDoc.getDocument() == null || pdfDoc.getFile() == null) {
            throw new IOException("No document or target file to save.");
        }

        PDDocument pdDoc = pdfDoc.getDocument();
        File originalFile = pdfDoc.getFile();

        // Check if document has digital signatures
        List<org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature> signatures =
                pdDoc.getSignatureDictionaries();
        if (signatures != null && !signatures.isEmpty()) {
            logger.warn("Document has {} digital signature(s). Saving will invalidate them!", signatures.size());
            // Throw exception to prevent accidental signature invalidation
            throw new IOException("Cannot save: Document contains digital signatures. " +
                    "Saving would invalidate all signatures. Use 'Save As' to create a new unsigned copy.");
        }

        // CRITICAL: Save to a temporary file first to avoid corruption
        // when overwriting the file we're reading from
        File tempFile = new File(originalFile.getParent(),
                originalFile.getName() + ".tmp_" + System.currentTimeMillis());

        try {
            flattenAnnotationsToPDF(pdfDoc);

            // If the document was encrypted, remove encryption before saving
            // (User has already had access since they opened the document)
            if (pdDoc.isEncrypted()) {
                pdDoc.setAllSecurityToBeRemoved(true);
                logger.info("Removing encryption for save operation");
            }

            // Save to a temp file
            pdDoc.save(tempFile);
            logger.info("Saved to temporary file: {}", tempFile.getName());

            // Close the document to release file locks
            pdDoc.close();
            logger.info("Document closed, ready to replace original file");

            // Delete the original file
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
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    logger.warn("Could not delete temporary file: {}", tempFile.getName());
                }
                logger.info("Temp file copied to original location");
            } else {
                logger.info("Temp file renamed to original name");
            }
            PDDocument newDoc = Loader.loadPDF(originalFile);
            pdfDoc.updateDocument(newDoc);

            logger.info("Saved PDF to {}", originalFile.getAbsolutePath());

        } catch (Exception e) {
            // Cleanup temp file if something goes wrong
            if (tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    logger.warn("Could not delete temporary file during cleanup: {}", tempFile.getName());
                }
            }
            throw new IOException("Failed to save document: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a PDF document has digital signatures.
     *
     * @param pdfDoc the PDF document to check
     * @return true if the document has signatures, false otherwise
     */
    public boolean hasSignatures(PDFDocument pdfDoc) {
        if (pdfDoc == null || pdfDoc.getDocument() == null) {
            return false;
        }
        try {
            List<org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature> signatures =
                    pdfDoc.getDocument().getSignatureDictionaries();
            return signatures != null && !signatures.isEmpty();
        } catch (Exception e) {
            logger.error("Error checking for signatures", e);
            return false;
        }
    }

    /**
     * Save the current document to a specific path.
     */
    public void saveAs(PDFDocument pdfDoc, File targetFile) throws IOException {
        if (pdfDoc == null || pdfDoc.getDocument() == null || targetFile == null) {
            throw new IOException("Invalid save parameters.");
        }

        PDDocument pdDoc = pdfDoc.getDocument();

        // Ensure directory exists
        Path parent = targetFile.toPath().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        // If the document was encrypted, remove encryption before saving
        // (User has already had access since they opened the document)
        if (pdDoc.isEncrypted()) {
            pdDoc.setAllSecurityToBeRemoved(true);
            logger.info("Removing encryption for saveAs operation");
        }

        pdDoc.save(targetFile);
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

        // Collect valid indices at once
        List<Integer> validIndices = pageIndices.stream()
                .filter(i -> i >= 0 && i < total)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        if (validIndices.isEmpty()) {
            logger.warn("No valid page indices to delete");
            return;
        }

        if (validIndices.size() >= total) {
            throw new IllegalArgumentException(
                    "Cannot delete all pages. Attempting to delete " +
                            validIndices.size() + " out of " + total + " pages.");
        }

        try {
            // Delete pages
            for (Integer pageIndex : validIndices) {
                doc.removePage(pageIndex);
            }

            // Update annotations efficiently
            updateAnnotations(pdfDoc, validIndices, total);

            // Clear render cache
            pdfDoc.clearCache();

            // Clamp current page
            int newTotal = doc.getNumberOfPages();
            int current = pdfDoc.getCurrentPage();
            if (current >= newTotal) {
                pdfDoc.setCurrentPage(Math.max(0, newTotal - 1));
            }

            logger.info("Successfully deleted {} page(s). Pages: {}. New total: {}",
                    validIndices.size(), validIndices, newTotal);

        } catch (Exception e) {
            logger.error("Failed to delete pages", e);
            throw new RuntimeException("Error deleting pages from PDF", e);
        }
    }

    /**
     * Updates the annotations within a PDF document by removing annotations belonging
     * to deleted pages and adjusting the page numbers of the remaining annotations
     * to account for the removal of the deleted pages.
     *
     * @param pdfDoc        the PDF document whose annotations are to be updated
     * @param deletedPages  a list of page numbers that have been deleted
     * @param originalTotal the original total number of pages in the PDF document
     */
    private void updateAnnotations(PDFDocument pdfDoc, List<Integer> deletedPages,
                                   int originalTotal) {
        Set<Integer> deletedSet = new HashSet<>(deletedPages);

        pdfDoc.getAnnotations().removeIf(a -> deletedSet.contains(a.getPageNumber()));

        // Calculate offset for each remaining page
        pdfDoc.getAnnotations().forEach(a -> {
            int originalPage = a.getPageNumber();
            long offset = deletedPages.stream()
                    .filter(p -> p < originalPage)
                    .count();
            a.setPageNumber(originalPage - (int) offset);
        });
    }

    private void flattenAnnotationsToPDF(PDFDocument pdfDoc) {
        PDDocument doc = pdfDoc.getDocument();
        List<Annotation> annotations = pdfDoc.getAnnotations();

        if (annotations.isEmpty()) return;

        final float scaleFactor = 72f / DEFAULT_DPI;

        for (Annotation ann : annotations) {
            if (ann instanceof ShapeAnnotation shape) {
                if (shape.getPageNumber() >= doc.getNumberOfPages()) continue;

                PDPage page = doc.getPage(shape.getPageNumber());
                PDRectangle pageSize = page.getCropBox();
                float pageHeight = pageSize.getHeight();

                try (PDPageContentStream contentStream = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                    contentStream.transform(new Matrix(scaleFactor, 0, 0, -scaleFactor, 0, pageHeight));
                    Color awtColor = new Color(
                            (float) shape.getColor().getRed(),
                            (float) shape.getColor().getGreen(),
                            (float) shape.getColor().getBlue(),
                            (float) shape.getColor().getOpacity()
                    );
                    contentStream.setStrokingColor(awtColor);
                    contentStream.setLineWidth((float) shape.getLineWidth());

                    float x1 = (float) shape.getX();
                    float y1 = (float) shape.getY();
                    float x2 = (float) shape.getEndX();
                    float y2 = (float) shape.getEndY();

                    switch (shape) {
                        case RectangleAnnotation rectangleAnnotation -> {
                            float w = Math.abs(x2 - x1);
                            float h = Math.abs(y1 - y2);
                            float rectX = Math.min(x1, x2);
                            float rectY = Math.min(y1, y2);
                            contentStream.addRect(rectX, rectY, w, h);
                            contentStream.stroke();
                        }
                        case ArrowAnnotation arrowAnnotation -> {
                            csDrawArrow(contentStream, x1, y1, x2, y2);
                        }
                        case CircleAnnotation circleAnnotation -> {
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
                        default -> {
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error flattening annotation", e);
                }
            }
        }
        pdfDoc.getAnnotations().clear();
    }

    private void csDrawArrow(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws IOException {
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);

        double angle = Math.atan2(y2 - y1, x2 - x1);
        double arrowSize = 15.0;

        float x3 = (float) (x2 - arrowSize * Math.cos(angle - Math.PI / 6));
        float y3 = (float) (y2 - arrowSize * Math.sin(angle - Math.PI / 6));
        float x4 = (float) (x2 - arrowSize * Math.cos(angle + Math.PI / 6));
        float y4 = (float) (y2 - arrowSize * Math.sin(angle + Math.PI / 6));

        cs.lineTo(x3, y3);
        cs.moveTo(x2, y2);
        cs.lineTo(x4, y4);
        cs.stroke();
    }

    /**
     * Chèn trang trắng vào tài liệu PDF.
     */
    public void insertBlankPage(PDFDocument pdfDoc, int index, float width, float height, int count) {
        if (pdfDoc == null || pdfDoc.getDocument() == null || count <= 0) return;

        PDDocument doc = pdfDoc.getDocument();

        try {
            int totalPages = doc.getNumberOfPages();
            // Validate index
            if (index < 0) index = 0;
            if (index > totalPages) index = totalPages;

            for (int i = 0; i < count; i++) {
                // Tạo trang mới với kích thước tùy chọn
                PDPage newPage = new PDPage(new PDRectangle(width, height));

                if (index >= doc.getNumberOfPages()) {
                    // Chèn vào cuối cùng
                    doc.addPage(newPage);
                } else {
                    // Chèn vào giữa (Trước trang tại vị trí index)
                    PDPage targetPage = doc.getPage(index);
                    doc.getPages().insertBefore(newPage, targetPage);
                }
                // Tăng index để các trang tiếp theo nằm sau trang vừa tạo
                index++;
            }

            // Xóa cache để UI biết đường vẽ lại ảnh mới
            pdfDoc.clearCache();

            logger.info("Inserted {} blank page(s) at index {}", count, index);

        } catch (Exception e) {
            logger.error("Error inserting blank pages", e);
        }
    }

    /**
     * Signs a PDF file with a digital signature using a keystore certificate.
     *
     * @param inputFile        the source PDF file
     * @param outputFile       the destination for signed PDF
     * @param keystorePath     path to the keystore file (.p12, .pfx, .jks)
     * @param keystorePassword password for the keystore
     * @param alias            certificate alias in the keystore
     * @param keyPassword      password for the private key (can be empty if same as keystore password)
     * @param reason           reason for signing (optional)
     * @param location         location of signing (optional)
     * @param contact          contact info (optional)
     * @param visibleSignature whether to show visible signature on document
     * @param page             page number for visible signature (1-based)
     * @param x                x coordinate for visible signature
     * @param y                y coordinate for visible signature
     * @param width            width of visible signature
     * @param height           height of visible signature
     * @throws Exception if signing fails
     */
    public void signPDF(File inputFile, File outputFile, String keystorePath,
                        String keystorePassword, String alias, String keyPassword,
                        String reason, String location, String contact,
                        boolean visibleSignature, int page, int x, int y,
                        int width, int height) throws Exception {
        logger.info("Signing PDF: {}", inputFile.getName());

        // Load keystore
        java.security.KeyStore keyStore;
        String keystoreType = keystorePath.toLowerCase().endsWith(".jks") ? "JKS" : "PKCS12";

        try (java.io.FileInputStream fis = new java.io.FileInputStream(keystorePath)) {
            keyStore = java.security.KeyStore.getInstance(keystoreType);
            keyStore.load(fis, keystorePassword.toCharArray());
        }

        // Get private key and certificate chain
        String effectiveKeyPassword = (keyPassword == null || keyPassword.isEmpty())
                ? keystorePassword : keyPassword;

        java.security.PrivateKey privateKey = (java.security.PrivateKey)
                keyStore.getKey(alias, effectiveKeyPassword.toCharArray());

        if (privateKey == null) {
            throw new Exception("Private key not found for alias: " + alias);
        }

        java.security.cert.Certificate[] certChain = keyStore.getCertificateChain(alias);
        if (certChain == null || certChain.length == 0) {
            throw new Exception("Certificate chain not found for alias: " + alias);
        }

        // Load PDF document
        try (PDDocument document = Loader.loadPDF(inputFile);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {

            // Create signature
            org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature signature =
                    new org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature();

            signature.setFilter(org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName(((java.security.cert.X509Certificate) certChain[0]).getSubjectX500Principal().getName());

            if (reason != null && !reason.isEmpty()) {
                signature.setReason(reason);
            }
            if (location != null && !location.isEmpty()) {
                signature.setLocation(location);
            }
            if (contact != null && !contact.isEmpty()) {
                signature.setContactInfo(contact);
            }

            signature.setSignDate(java.util.Calendar.getInstance());

            // Create signature options for visible signature
            org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions signatureOptions =
                    new org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions();

            if (visibleSignature) {
                // Create visible signature rectangle
                signatureOptions.setVisualSignature(
                        createVisibleSignature(document, page - 1, x, y, width, height,
                                ((java.security.cert.X509Certificate) certChain[0]).getSubjectX500Principal().getName(),
                                reason, location)
                );
                signatureOptions.setPage(page - 1);
            }

            // Add signature to document
            document.addSignature(signature, new SigningSupport(privateKey, certChain), signatureOptions);

            // Save signed document
            document.saveIncremental(fos);

            logger.info("PDF signed successfully: {}", outputFile.getName());
        }
    }

    /**
     * Creates a visible signature appearance stream.
     */
    private java.io.InputStream createVisibleSignature(PDDocument document, int pageIndex,
                                                        int x, int y, int width, int height,
                                                        String signerName, String reason, String location) throws IOException {
        PDPage page = document.getPage(pageIndex);
        PDRectangle pageRect = page.getMediaBox();

        // Create signature appearance
        try (PDDocument sigDoc = new PDDocument()) {
            PDPage sigPage = new PDPage(new PDRectangle(width, height));
            sigDoc.addPage(sigPage);

            try (PDPageContentStream cs = new PDPageContentStream(sigDoc, sigPage)) {
                // Draw border
                cs.setStrokingColor(0, 0, 0);
                cs.setLineWidth(1);
                cs.addRect(1, 1, width - 2, height - 2);
                cs.stroke();

                // Add text using PDFBox 3.x API
                org.apache.pdfbox.pdmodel.font.PDFont font = new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);

                cs.beginText();
                cs.setFont(font, 8);
                cs.setLeading(10f);
                cs.newLineAtOffset(5, height - 12);

                cs.showText("Digitally signed by:");
                cs.newLine();

                // Truncate signer name if too long
                String displayName = signerName;
                if (displayName.length() > 30) {
                    displayName = displayName.substring(0, 27) + "...";
                }
                cs.showText(displayName);
                cs.newLine();

                if (reason != null && !reason.isEmpty()) {
                    cs.showText("Reason: " + (reason.length() > 25 ? reason.substring(0, 22) + "..." : reason));
                    cs.newLine();
                }

                if (location != null && !location.isEmpty()) {
                    cs.showText("Location: " + (location.length() > 22 ? location.substring(0, 19) + "..." : location));
                    cs.newLine();
                }

                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                cs.showText("Date: " + sdf.format(new java.util.Date()));

                cs.endText();
            }

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            sigDoc.save(baos);
            return new java.io.ByteArrayInputStream(baos.toByteArray());
        }
    }

    /**
     * Inner class to handle the actual signing process using BouncyCastle.
     */
    private static class SigningSupport implements org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface {
        private final java.security.PrivateKey privateKey;
        private final java.security.cert.Certificate[] certChain;
        private static final Logger sigLogger = LoggerFactory.getLogger(SigningSupport.class);

        public SigningSupport(java.security.PrivateKey privateKey, java.security.cert.Certificate[] certChain) {
            this.privateKey = privateKey;
            this.certChain = certChain;
        }

        @Override
        public byte[] sign(java.io.InputStream content) throws IOException {
            try {
                // Add BouncyCastle provider if not already added
                if (java.security.Security.getProvider(org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME) == null) {
                    java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
                }

                // Read all content bytes - this is the data that will be signed
                byte[] contentBytes = content.readAllBytes();
                sigLogger.debug("Signing {} bytes of content", contentBytes.length);

                java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate) certChain[0];

                // Create certificate store
                java.util.List<java.security.cert.Certificate> certList = new java.util.ArrayList<>();
                java.util.Collections.addAll(certList, certChain);
                org.bouncycastle.cert.jcajce.JcaCertStore certStore =
                        new org.bouncycastle.cert.jcajce.JcaCertStore(certList);

                // Create CMS signed data generator
                org.bouncycastle.cms.CMSSignedDataGenerator gen = new org.bouncycastle.cms.CMSSignedDataGenerator();

                // Determine signature algorithm based on key type
                String signatureAlgorithm;
                String keyAlgorithm = privateKey.getAlgorithm();
                if ("EC".equals(keyAlgorithm) || "ECDSA".equals(keyAlgorithm)) {
                    signatureAlgorithm = "SHA256withECDSA";
                } else {
                    signatureAlgorithm = "SHA256withRSA";
                }

                // Create content signer
                org.bouncycastle.operator.ContentSigner contentSigner =
                        new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder(signatureAlgorithm)
                                .setProvider("BC")
                                .build(privateKey);

                // Create signer info generator with signed attributes
                org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder signerInfoBuilder =
                        new org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder(
                                new org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder()
                                        .setProvider("BC")
                                        .build());

                // Enable signed attributes (required for detached signatures)
                signerInfoBuilder.setDirectSignature(false);

                gen.addSignerInfoGenerator(
                        signerInfoBuilder.build(contentSigner,
                                new org.bouncycastle.cert.jcajce.JcaX509CertificateHolder(cert)));

                gen.addCertificates(certStore);

                // Create CMSTypedData for the content to be signed
                org.bouncycastle.cms.CMSTypedData msg = new org.bouncycastle.cms.CMSProcessableByteArray(contentBytes);

                // Generate detached signature (encapsulate = false)
                org.bouncycastle.cms.CMSSignedData signedData = gen.generate(msg, false);

                byte[] signature = signedData.getEncoded();
                sigLogger.debug("Generated signature of {} bytes", signature.length);

                return signature;

            } catch (Exception e) {
                throw new IOException("Error signing PDF: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Verifies all digital signatures in a PDF file.
     *
     * @param pdfDoc the PDF document to verify
     * @return list of signature verification results
     * @throws IOException if verification fails
     */
    public List<SignatureVerificationResult> verifySignatures(PDFDocument pdfDoc) throws IOException {
        List<SignatureVerificationResult> results = new ArrayList<>();

        if (pdfDoc == null || pdfDoc.getDocument() == null) {
            return results;
        }

        PDDocument document = pdfDoc.getDocument();

        // Get all signature fields
        List<org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature> signatures =
                document.getSignatureDictionaries();

        if (signatures.isEmpty()) {
            logger.info("No signatures found in document");
            return results;
        }

        logger.info("Found {} signature(s) in document", signatures.size());

        for (org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature signature : signatures) {
            SignatureVerificationResult result = verifySignature(pdfDoc.getFile(), signature);
            results.add(result);
        }

        return results;
    }

    /**
     * Verifies a single signature.
     */
    private SignatureVerificationResult verifySignature(File pdfFile,
            org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature signature) {

        String signerName = signature.getName();
        String reason = signature.getReason();
        String location = signature.getLocation();
        String contactInfo = signature.getContactInfo();
        java.util.Calendar signDate = signature.getSignDate();

        boolean isValid = false;
        String status = "Unknown";
        
        // Check if file exists and is readable
        if (pdfFile == null || !pdfFile.exists() || !pdfFile.canRead()) {
            return new SignatureVerificationResult(signerName, reason, location, contactInfo,
                    signDate, false, "Error", "PDF file is not accessible. Please save the document first.");
        }
        String details = "";

        try {
            // Add BouncyCastle provider if not already added
            if (java.security.Security.getProvider(org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME) == null) {
                java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            }

            // Check signature subfilter to determine signature type
            String subFilter = signature.getSubFilter();
            logger.debug("Signature subFilter: {}", subFilter);

            // Read the PDF file bytes directly from disk
            byte[] pdfBytes;
            try {
                pdfBytes = java.nio.file.Files.readAllBytes(pdfFile.toPath());
            } catch (IOException e) {
                status = "Error";
                details = "Cannot read PDF file: " + e.getMessage();
                return new SignatureVerificationResult(signerName, reason, location, contactInfo,
                        signDate, isValid, status, details);
            }
            logger.debug("Read {} bytes from PDF file for verification", pdfBytes.length);

            // Get signature content - may fail if signature is corrupted
            byte[] signatureContent;
            try {
                signatureContent = signature.getContents(pdfBytes);
            } catch (Exception e) {
                status = "Corrupted";
                details = "Signature data is corrupted or invalid. The document may have been modified after signing.";
                logger.error("Failed to read signature contents - signature may be corrupted", e);
                return new SignatureVerificationResult(signerName, reason, location, contactInfo,
                        signDate, isValid, status, details);
            }

            if (signatureContent == null || signatureContent.length == 0) {
                status = "Invalid";
                details = "No signature content found";
                return new SignatureVerificationResult(signerName, reason, location, contactInfo,
                        signDate, isValid, status, details);
            }
            logger.debug("Signature content size: {} bytes", signatureContent.length);

            // Get signed content (the bytes that were signed - byte ranges)
            byte[] signedContent;
            try {
                signedContent = signature.getSignedContent(pdfBytes);
            } catch (Exception e) {
                status = "Corrupted";
                details = "Cannot read signed content - signature may be corrupted.";
                logger.error("Failed to read signed content", e);
                return new SignatureVerificationResult(signerName, reason, location, contactInfo,
                        signDate, isValid, status, details);
            }
            logger.debug("Signed content size: {} bytes", signedContent.length);

            // Handle different signature formats based on SubFilter
            if ("adbe.x509.rsa_sha1".equals(subFilter)) {
                // PKCS#1 signature format - certificate is stored separately in /Cert entry
                return verifyPKCS1Signature(signature, signatureContent, signedContent,
                        signerName, reason, location, contactInfo, signDate);
            }

            // For PKCS#7/CMS signatures (adbe.pkcs7.detached, adbe.pkcs7.sha1, ETSI.CAdES.detached, etc.)
            org.bouncycastle.cms.CMSSignedData cmsSignedData;
            try {
                // First try parsing signature content directly
                cmsSignedData = new org.bouncycastle.cms.CMSSignedData(signatureContent);
            } catch (org.bouncycastle.cms.CMSException e) {
                // If that fails, try with ContentInfo wrapper
                try {
                    org.bouncycastle.asn1.ASN1InputStream asn1Stream =
                            new org.bouncycastle.asn1.ASN1InputStream(signatureContent);
                    org.bouncycastle.asn1.ASN1Primitive asn1Object = asn1Stream.readObject();
                    asn1Stream.close();

                    if (asn1Object instanceof org.bouncycastle.asn1.ASN1Sequence) {
                        org.bouncycastle.asn1.cms.ContentInfo contentInfo =
                                org.bouncycastle.asn1.cms.ContentInfo.getInstance(asn1Object);
                        cmsSignedData = new org.bouncycastle.cms.CMSSignedData(contentInfo);
                    } else {
                        status = "Unknown";
                        details = "Unsupported signature format. SubFilter: " + subFilter;
                        logger.warn("Unsupported signature format: {}", subFilter);
                        return new SignatureVerificationResult(signerName, reason, location, contactInfo,
                                signDate, isValid, status, details);
                    }
                } catch (Exception ex) {
                    status = "Unknown";
                    details = "Cannot parse signature. Format may not be supported. SubFilter: " + subFilter;
                    logger.warn("Cannot parse signature content: {}", ex.getMessage());
                    return new SignatureVerificationResult(signerName, reason, location, contactInfo,
                            signDate, isValid, status, details);
                }
            }

            // Get certificates from signature
            org.bouncycastle.util.Store<org.bouncycastle.cert.X509CertificateHolder> certStore =
                    cmsSignedData.getCertificates();

            // Get signer infos
            org.bouncycastle.cms.SignerInformationStore signerInfoStore = cmsSignedData.getSignerInfos();
            Collection<org.bouncycastle.cms.SignerInformation> signers = signerInfoStore.getSigners();

            if (signers.isEmpty()) {
                status = "Invalid";
                details = "No signer information found";
                return new SignatureVerificationResult(signerName, reason, location, contactInfo,
                        signDate, isValid, status, details);
            }

            // Verify each signer
            for (org.bouncycastle.cms.SignerInformation signer : signers) {
                Collection<org.bouncycastle.cert.X509CertificateHolder> certCollection =
                        certStore.getMatches(signer.getSID());

                if (certCollection.isEmpty()) {
                    status = "Invalid";
                    details = "Certificate not found in signature";
                    continue;
                }

                org.bouncycastle.cert.X509CertificateHolder certHolder = certCollection.iterator().next();

                // Convert to X509Certificate
                java.security.cert.X509Certificate cert =
                        new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                                .setProvider("BC")
                                .getCertificate(certHolder);

                // Get signer name from certificate if not in signature
                if (signerName == null || signerName.isEmpty()) {
                    signerName = cert.getSubjectX500Principal().getName();
                }

                // Get digest algorithm and calculate digest of signed content
                String digestAlgOID = signer.getDigestAlgOID();
                java.security.MessageDigest md = java.security.MessageDigest.getInstance(
                        getDigestAlgorithmName(digestAlgOID), "BC");
                byte[] calculatedDigest = md.digest(signedContent);

                // Get the message-digest attribute from the signature
                org.bouncycastle.asn1.cms.AttributeTable signedAttrs = signer.getSignedAttributes();
                if (signedAttrs != null) {
                    org.bouncycastle.asn1.cms.Attribute digestAttr = signedAttrs.get(
                            org.bouncycastle.asn1.cms.CMSAttributes.messageDigest);
                    if (digestAttr != null) {
                        org.bouncycastle.asn1.ASN1OctetString digestValue =
                                (org.bouncycastle.asn1.ASN1OctetString) digestAttr.getAttrValues().getObjectAt(0);
                        byte[] signedDigest = digestValue.getOctets();

                        logger.debug("Calculated digest: {}", bytesToHex(calculatedDigest));
                        logger.debug("Signed digest: {}", bytesToHex(signedDigest));

                        // Compare digests - this checks if document was modified
                        if (!java.util.Arrays.equals(calculatedDigest, signedDigest)) {
                            status = "Invalid";
                            details = "Document has been modified after signing (digest mismatch).";
                            logger.warn("Digest mismatch detected - document may have been modified");
                            return new SignatureVerificationResult(signerName, reason, location, contactInfo,
                                    signDate, isValid, status, details);
                        }
                    }
                }

                // Verify the cryptographic signature on the signed attributes
                // For detached signatures without content, we verify the signature over signed attributes only
                boolean sigValid = verifySignatureOnly(signer, certHolder);

                if (sigValid) {
                    isValid = true;
                    status = "Valid";

                    // Check certificate validity
                    try {
                        cert.checkValidity();
                        details = "Signature is valid. Certificate is valid.";
                    } catch (java.security.cert.CertificateExpiredException e) {
                        details = "Signature is valid but certificate has expired.";
                    } catch (java.security.cert.CertificateNotYetValidException e) {
                        details = "Signature is valid but certificate is not yet valid.";
                    }
                } else {
                    status = "Invalid";
                    details = "Signature verification failed - cryptographic signature invalid.";
                }
            }

        } catch (Exception e) {
            status = "Error";
            details = "Verification error: " + e.getMessage();
            logger.error("Error verifying signature", e);
        }

        return new SignatureVerificationResult(signerName, reason, location, contactInfo,
                signDate, isValid, status, details);
    }

    /**
     * Verifies a PKCS#1 signature (adbe.x509.rsa_sha1 format).
     * In this format, the certificate is stored in /Cert entry and signature is raw RSA.
     */
    private SignatureVerificationResult verifyPKCS1Signature(
            org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature signature,
            byte[] signatureBytes, byte[] signedContent,
            String signerName, String reason, String location, String contactInfo,
            java.util.Calendar signDate) {

        boolean isValid = false;
        String status = "Unknown";
        String details = "";

        try {
            // Get certificate from /Cert entry in signature dictionary
            org.apache.pdfbox.cos.COSDictionary sigDict = signature.getCOSObject();
            org.apache.pdfbox.cos.COSBase certObj = sigDict.getDictionaryObject(org.apache.pdfbox.cos.COSName.getPDFName("Cert"));

            if (certObj == null) {
                status = "Invalid";
                details = "No certificate found in signature (missing /Cert entry)";
                return new SignatureVerificationResult(signerName, reason, location, contactInfo,
                        signDate, isValid, status, details);
            }

            // Certificate can be a single cert or an array of certs
            byte[] certBytes;
            if (certObj instanceof org.apache.pdfbox.cos.COSString) {
                certBytes = ((org.apache.pdfbox.cos.COSString) certObj).getBytes();
            } else if (certObj instanceof org.apache.pdfbox.cos.COSArray) {
                org.apache.pdfbox.cos.COSArray certArray = (org.apache.pdfbox.cos.COSArray) certObj;
                if (certArray.size() > 0) {
                    org.apache.pdfbox.cos.COSBase firstCert = certArray.getObject(0);
                    if (firstCert instanceof org.apache.pdfbox.cos.COSString) {
                        certBytes = ((org.apache.pdfbox.cos.COSString) firstCert).getBytes();
                    } else {
                        status = "Invalid";
                        details = "Invalid certificate format in /Cert array";
                        return new SignatureVerificationResult(signerName, reason, location, contactInfo,
                                signDate, isValid, status, details);
                    }
                } else {
                    status = "Invalid";
                    details = "Empty certificate array in signature";
                    return new SignatureVerificationResult(signerName, reason, location, contactInfo,
                            signDate, isValid, status, details);
                }
            } else {
                status = "Invalid";
                details = "Unsupported certificate format in signature";
                return new SignatureVerificationResult(signerName, reason, location, contactInfo,
                        signDate, isValid, status, details);
            }

            // Parse the X.509 certificate
            java.security.cert.CertificateFactory certFactory =
                    java.security.cert.CertificateFactory.getInstance("X.509", "BC");
            java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate)
                    certFactory.generateCertificate(new java.io.ByteArrayInputStream(certBytes));

            // Get signer name from certificate if not provided
            if (signerName == null || signerName.isEmpty()) {
                signerName = cert.getSubjectX500Principal().getName();
            }

            // For adbe.x509.rsa_sha1:
            // The signature value is the RSA encryption of the SHA-1 digest of the byte range
            // We need to:
            // 1. Calculate SHA-1 of signed content
            // 2. Decrypt signature with public key to get the signed hash
            // 3. Compare the two hashes

            // Calculate SHA-1 hash of signed content
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1", "BC");
            byte[] calculatedHash = md.digest(signedContent);
            logger.debug("Calculated SHA-1 hash: {}", bytesToHex(calculatedHash));

            // Try standard verification first
            java.security.Signature sig = java.security.Signature.getInstance("SHA1withRSA", "BC");
            sig.initVerify(cert.getPublicKey());
            sig.update(signedContent);

            boolean verified = false;
            try {
                verified = sig.verify(signatureBytes);
            } catch (java.security.SignatureException e) {
                logger.debug("Standard verification failed, trying raw RSA decryption");

                // If standard verification fails, try raw RSA decryption
                // This handles cases where signature is just encrypted hash without PKCS#1 padding info
                try {
                    javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
                    cipher.init(javax.crypto.Cipher.DECRYPT_MODE, cert.getPublicKey());
                    byte[] decryptedHash = cipher.doFinal(signatureBytes);

                    // The decrypted data might be DigestInfo (ASN.1 structure) containing the hash
                    // or just the raw hash
                    if (decryptedHash.length == calculatedHash.length) {
                        verified = java.util.Arrays.equals(decryptedHash, calculatedHash);
                    } else if (decryptedHash.length > calculatedHash.length) {
                        // DigestInfo structure - extract the hash from the end
                        byte[] extractedHash = new byte[calculatedHash.length];
                        System.arraycopy(decryptedHash, decryptedHash.length - calculatedHash.length,
                                extractedHash, 0, calculatedHash.length);
                        verified = java.util.Arrays.equals(extractedHash, calculatedHash);

                        if (!verified) {
                            // Try parsing as DigestInfo ASN.1
                            try {
                                org.bouncycastle.asn1.ASN1InputStream asn1 =
                                        new org.bouncycastle.asn1.ASN1InputStream(decryptedHash);
                                org.bouncycastle.asn1.ASN1Sequence seq =
                                        (org.bouncycastle.asn1.ASN1Sequence) asn1.readObject();
                                asn1.close();

                                // DigestInfo ::= SEQUENCE { digestAlgorithm, digest }
                                if (seq.size() >= 2) {
                                    org.bouncycastle.asn1.ASN1OctetString digestOctet =
                                            (org.bouncycastle.asn1.ASN1OctetString) seq.getObjectAt(1);
                                    byte[] signedHash = digestOctet.getOctets();
                                    verified = java.util.Arrays.equals(signedHash, calculatedHash);
                                    logger.debug("Extracted hash from DigestInfo: {}", bytesToHex(signedHash));
                                }
                            } catch (Exception asn1Ex) {
                                logger.debug("Failed to parse DigestInfo: {}", asn1Ex.getMessage());
                            }
                        }
                    }
                } catch (Exception decryptEx) {
                    logger.debug("Raw RSA decryption failed: {}", decryptEx.getMessage());
                }
            }

            if (verified) {
                isValid = true;
                status = "Valid";

                // Check certificate validity
                try {
                    cert.checkValidity();
                    details = "Signature is valid (PKCS#1/RSA-SHA1). Certificate is valid.";
                } catch (java.security.cert.CertificateExpiredException e) {
                    details = "Signature is valid but certificate has expired.";
                } catch (java.security.cert.CertificateNotYetValidException e) {
                    details = "Signature is valid but certificate is not yet valid.";
                }
            } else {
                status = "Invalid";
                details = "Signature verification failed - document may have been modified.";
            }

        } catch (Exception e) {
            status = "Error";
            details = "PKCS#1 verification error: " + e.getMessage();
            logger.error("Error verifying PKCS#1 signature", e);
        }

        return new SignatureVerificationResult(signerName, reason, location, contactInfo,
                signDate, isValid, status, details);
    }

    /**
     * Verifies only the cryptographic signature without checking message digest.
     * This is used for detached signatures where we manually verify the digest.
     */
    private boolean verifySignatureOnly(org.bouncycastle.cms.SignerInformation signer,
                                        org.bouncycastle.cert.X509CertificateHolder certHolder) {
        try {
            // Get the signature bytes
            byte[] signatureBytes = signer.getSignature();

            // Get the signed attributes (what was actually signed)
            org.bouncycastle.asn1.cms.AttributeTable signedAttrs = signer.getSignedAttributes();
            if (signedAttrs == null) {
                logger.warn("No signed attributes found");
                return false;
            }

            // Encode the signed attributes for verification
            byte[] signedAttrBytes = signer.getEncodedSignedAttributes();

            // Get the signature algorithm
            String encryptionAlgOID = signer.getEncryptionAlgOID();
            String digestAlgOID = signer.getDigestAlgOID();

            // Determine the signature algorithm name
            String signatureAlgorithm = getSignatureAlgorithmName(digestAlgOID, encryptionAlgOID);

            // Verify using Java security API
            java.security.Signature sig = java.security.Signature.getInstance(signatureAlgorithm, "BC");
            java.security.cert.X509Certificate cert =
                    new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                            .setProvider("BC")
                            .getCertificate(certHolder);
            sig.initVerify(cert.getPublicKey());
            sig.update(signedAttrBytes);

            return sig.verify(signatureBytes);

        } catch (Exception e) {
            logger.error("Error verifying signature cryptographically", e);
            return false;
        }
    }

    /**
     * Converts OID to algorithm name for MessageDigest.
     */
    private String getDigestAlgorithmName(String oid) {
        return switch (oid) {
            case "1.3.14.3.2.26" -> "SHA-1";
            case "2.16.840.1.101.3.4.2.1" -> "SHA-256";
            case "2.16.840.1.101.3.4.2.2" -> "SHA-384";
            case "2.16.840.1.101.3.4.2.3" -> "SHA-512";
            case "1.2.840.113549.2.5" -> "MD5";
            default -> "SHA-256";
        };
    }

    /**
     * Determines signature algorithm name from digest and encryption OIDs.
     */
    private String getSignatureAlgorithmName(String digestOID, String encryptionOID) {
        String digestName = getDigestAlgorithmName(digestOID).replace("-", "");

        // RSA encryption OID
        if ("1.2.840.113549.1.1.1".equals(encryptionOID) ||
            "1.2.840.113549.1.1.11".equals(encryptionOID)) { // SHA256withRSA
            return digestName + "withRSA";
        }
        // ECDSA OIDs
        if ("1.2.840.10045.4.3.2".equals(encryptionOID)) { // SHA256withECDSA
            return digestName + "withECDSA";
        }
        if ("1.2.840.10045.2.1".equals(encryptionOID)) { // EC public key
            return digestName + "withECDSA";
        }

        // Default to RSA
        return digestName + "withRSA";
    }

    /**
     * Converts byte array to hex string for logging.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Result class containing signature verification information.
     */
    public record SignatureVerificationResult(
            String signerName,
            String reason,
            String location,
            String contactInfo,
            java.util.Calendar signDate,
            boolean isValid,
            String status,
            String details
    ) {
        public String getFormattedSignDate() {
            if (signDate == null) return "Unknown";
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.format(signDate.getTime());
        }
    }
}
