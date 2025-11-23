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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final float DEFAULT_DPI = 150f;

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
     * @throws IOException if the file cannot be read, password is incorrect,
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
            // If we can't load without password, it's encrypted
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
            // Create protection policy with 256-bit AES encryption
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
     * @throws IOException if decryption fails or password is incorrect
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

        // Reuse renderer from cache to maintain consistent font rendering
        // This prevents recreation of font caches and reduces glyph warnings
        PDFRenderer renderer = rendererCache.computeIfAbsent(
                pdfDoc.getDocument(),
                PDFRenderer::new
        );
        float dpi = DEFAULT_DPI * scale;

        logger.debug("Rendering page {} with DPI {}", pageIndex, dpi);

        // Synchronize on document to ensure thread-safe rendering
        // This prevents concurrent modification of page rotation and font resources
        BufferedImage bufferedImage;
        synchronized (pdfDoc.getDocument()) {
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

            // If document was encrypted, remove encryption before saving
            // (User already has access since they opened the document)
            if (pdDoc.isEncrypted()) {
                pdDoc.setAllSecurityToBeRemoved(true);
                logger.info("Removing encryption for save operation");
            }

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
            PDDocument newDoc = Loader.loadPDF(originalFile);
            pdfDoc.updateDocument(newDoc);

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

        PDDocument pdDoc = pdfDoc.getDocument();

        // Ensure directory exists
        Path parent = targetFile.toPath().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        // If document was encrypted, remove encryption before saving
        // (User already has access since they opened the document)
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

            // QUAN TRỌNG: Xóa cache để UI biết đường vẽ lại ảnh mới
            pdfDoc.clearCache();

            logger.info("Inserted {} blank page(s) at index {}", count, index);

        } catch (Exception e) {
            logger.error("Error inserting blank pages", e);
        }
    }
}
