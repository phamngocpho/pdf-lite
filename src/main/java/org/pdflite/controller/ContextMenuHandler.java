package org.pdflite.controller;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.pdflite.model.PDFDocument;
import org.pdflite.util.CoordinateConverter;
import org.pdflite.util.SmartTextSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.pdflite.model.ImageInfo;
import org.pdflite.util.ImageExtractor;

/**
 * Handles context menu operations with CORRECT coordinate mapping.
 *
 * <p>
 * <b>Implementation Notes:</b></p>
 * <ul>
 * <li>Uses {@link CoordinateConverter} for proper canvas-to-PDF coordinate
 * transformation</li>
 * <li>Implements Knowledge Base Section III coordinate conversion pipeline</li>
 * <li>Supports text extraction via {@link PDFTextStripperByArea} (Y=top
 * coords)</li>
 * <li>TODO: Image extraction via PDFStreamEngine (Y=bottom coords)</li>
 * </ul>
 *
 * @see CoordinateConverter
 * @see <a href="../../../docs/knowledge.md">Knowledge Base</a>
 */
public class ContextMenuHandler {

    private static final Logger logger = LoggerFactory.getLogger(ContextMenuHandler.class);

    // Selection state
    private SelectionInfo currentSelection;
    private final SmartTextSelector smartTextSelector;

    private List<ImageInfo> currentPageImages = new ArrayList<>();
    private ImageInfo currentImageUnderCursor;
    private int currentPageIndex = -1;

    public ContextMenuHandler() {
        logger.info("ContextMenuHandler initialized!");
        this.smartTextSelector = new SmartTextSelector();
    }

    /**
     * Analyzes rectangular selection area on the PDF page using smart text selection.
     *
     * <p>
     * <b>Coordinate Conversion Pipeline:</b></p>
     * <ol>
     * <li>Input: Canvas coordinates (pixels, Y=top)</li>
     * <li>Convert: Apply the scale factor to get PDF coordinates</li>
     * <li>Extract: Use SmartTextSelector to find nearest characters and extract text</li>
     * </ol>
     *
     * @param document  The PDF document
     * @param pageIndex Zero-based page index
     * @param canvasX1  Canvas X coordinate of the first corner (pixels)
     * @param canvasY1  Canvas Y coordinate of the first corner (pixels)
     * @param canvasX2  Canvas X coordinate of opposite corner (pixels)
     * @param canvasY2  Canvas Y coordinate of the opposite corner (pixels)
     * @param zoom      Current zoom level (e.g., 1.0 = 100%)
     */
    public void analyzeSelection(PDFDocument document, int pageIndex,
                                 double canvasX1, double canvasY1,
                                 double canvasX2, double canvasY2,
                                 double zoom) {

        if (document == null || document.getDocument() == null) {
            logger.warn("Analysis aborted: document is null");
            return;
        }

        try {
            float pageHeight = ensureTextPositionsExtracted(document, pageIndex);

            // Convert canvas coordinates to PDF coordinates
            Point2D pdfStart = CoordinateConverter.canvasToPdfJavaPoint(
                    canvasX1, canvasY1, pageHeight, zoom
            );
            Point2D pdfEnd = CoordinateConverter.canvasToPdfJavaPoint(
                    canvasX2, canvasY2, pageHeight, zoom
            );

            // Use smart text selector to get the selected text
            String selectedText = smartTextSelector.getSelectedText(pdfStart, pdfEnd);
            String cleanedText = (selectedText != null) ? selectedText.trim() : "";

            // Create selection info
            double x1 = Math.min(canvasX1, canvasX2);
            double y1 = Math.min(canvasY1, canvasY2);
            double width = Math.abs(canvasX2 - canvasX1);
            double height = Math.abs(canvasY2 - canvasY1);
            Rectangle2D.Float pdfRect = CoordinateConverter.canvasToPdfJavaRect(
                    x1, y1, width, height, pageHeight, zoom
            );

            currentSelection = new SelectionInfo(pageIndex, pdfRect, cleanedText, new ArrayList<>());

            if (currentSelection.hasText()) {
                logger.info("Length:   {} characters", currentSelection.getText().length());
            } else {
                logger.info("No text found in selection");
            }

        } catch (IOException e) {
            logger.error("Error analyzing selection: {}", e.getMessage(), e);
        }
    }

    /**
     * Selects text at a point (for double-click word or triple-click line selection).
     *
     * @param document  The PDF document
     * @param pageIndex Zero-based page index
     * @param canvasX   Canvas X coordinate (pixels)
     * @param canvasY   Canvas Y coordinate (pixels)
     * @param zoom      Current zoom level
     * @param clickType "word" for double-click, "line" for triple-click
     * @return Selected text
     */
    public String selectTextAtPoint(PDFDocument document, int pageIndex,
                                     double canvasX, double canvasY,
                                     double zoom, String clickType) {
        if (document == null || document.getDocument() == null) {
            return "";
        }

        try {
            float pageHeight = ensureTextPositionsExtracted(document, pageIndex);

            // Convert canvas coordinates to PDF coordinates
            Point2D pdfPoint = CoordinateConverter.canvasToPdfJavaPoint(
                    canvasX, canvasY, pageHeight, zoom
            );

            String selectedText = "";
            if ("word".equals(clickType)) {
                selectedText = smartTextSelector.selectWord(pdfPoint);
            } else if ("line".equals(clickType)) {
                selectedText = smartTextSelector.selectLine(pdfPoint);
            }

            return (selectedText != null) ? selectedText.trim() : "";

        } catch (IOException e) {
            logger.error("Error selecting text at point: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * Gets highlight regions for visual feedback (one rectangle per line of text).
     *
     * @param document  The PDF document
     * @param pageIndex Zero-based page index
     * @param canvasX1  Canvas X coordinate of the first corner (pixels)
     * @param canvasY1  Canvas Y coordinate of the first corner (pixels)
     * @param canvasX2  Canvas X coordinate of opposite corner (pixels)
     * @param canvasY2  Canvas Y coordinate of the opposite corner (pixels)
     * @param zoom      Current zoom level
     * @return List of rectangles in canvas coordinates (one per line)
     */
    public List<Rectangle2D> getHighlightRegions(PDFDocument document, int pageIndex,
                                                 double canvasX1, double canvasY1,
                                                 double canvasX2, double canvasY2,
                                                 double zoom) {
        if (document == null || document.getDocument() == null) {
            return new ArrayList<>();
        }

        try {
            float pageHeight = ensureTextPositionsExtracted(document, pageIndex);

            // Convert canvas coordinates to PDF coordinates
            Point2D pdfStart = CoordinateConverter.canvasToPdfJavaPoint(
                    canvasX1, canvasY1, pageHeight, zoom
            );
            Point2D pdfEnd = CoordinateConverter.canvasToPdfJavaPoint(
                    canvasX2, canvasY2, pageHeight, zoom
            );

            // Get highlight regions in PDF coordinates
            List<Rectangle2D> pdfRegions = smartTextSelector.getHighlightRegions(pdfStart, pdfEnd);

            // Convert PDF regions to canvas coordinates
            List<Rectangle2D> canvasRegions = new ArrayList<>();
            double finalScale = zoom * org.pdflite.util.Constants.LOW_RENDER_SCALE;

            for (Rectangle2D pdfRegion : pdfRegions) {
                double canvasX = pdfRegion.getX() * finalScale;
                double canvasY = pdfRegion.getY() * finalScale;
                double canvasWidth = pdfRegion.getWidth() * finalScale;
                double canvasHeight = pdfRegion.getHeight() * finalScale;
                canvasRegions.add(new Rectangle2D.Double(canvasX, canvasY, canvasWidth, canvasHeight));
            }

            return canvasRegions;

        } catch (IOException e) {
            logger.error("Error getting highlight regions: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Copies extracted text to the system clipboard.
     */
    public void handleCopyText() {
        if (currentSelection != null && currentSelection.hasText()) {
            copyToClipboard(currentSelection.getText());
        } else {
            logger.warn("No text to copy");
        }
    }

    /**
     * Copies text to clipboard (used for automatic copying).
     */
    public void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);

        logger.info("Copied {} characters to clipboard", text.length());
    }

    /**
     * Checks if text is available at the last analyzed position.
     *
     */
    public boolean hasTextAtPosition() {
        return currentSelection != null && currentSelection.hasText();
    }

    /**
     * Gets current selection info (for debugging).
     *
     */
    public SelectionInfo getCurrentSelection() {
        return currentSelection;
    }

    /**
     * Inner class to hold selection information.
     *
     */
    public static class SelectionInfo {

        private final int pageIndex;
        private final Rectangle2D.Float pdfRect;
        private final String text;


        public SelectionInfo(int pageIndex, Rectangle2D.Float pdfRect,
                             String text, List<Object> images) {
            this.pageIndex = pageIndex;
            this.pdfRect = pdfRect;
            this.text = text;
            // TODO: Implement image extraction
        }

        public boolean hasText() {
            return text != null && !text.isEmpty();
        }

        public String getText() {
            return text;
        }

        public int getPageIndex() {
            return pageIndex;
        }

        public Rectangle2D.Float getPdfRect() {
            return pdfRect;
        }
    }

    /**
     * Analyzes if the cursor is over an image (for the context menu).
     *
     * <p>
     * <b>Algorithm:</b></p>
     * <ol>
     * <li>Extract all images from the current page</li>
     * <li>Convert image bounds from PDF (Y=bottom) to Canvas (Y=top)</li>
     * <li>Check if the cursor point intersects with any image bounds</li>
     * </ol>
     */
    public void analyzeCursorForImage(PDFDocument document, int pageIndex,
                                      double canvasX, double canvasY,
                                      double zoom) {

        currentImageUnderCursor = null;

        if (document == null || document.getDocument() == null) {
            return;
        }

        try {
            PDPage page = document.getDocument().getPage(pageIndex);
            PDRectangle cropBox = page.getCropBox();
            float pageHeight = cropBox.getHeight();

            // Extract images if not cached
            if (currentPageImages.isEmpty()) {
                ImageExtractor extractor = new ImageExtractor();
                currentPageImages = extractor.extractImages(page, pageIndex);
                logger.info("║ Extracted {} images from page {}                      ║",
                        currentPageImages.size(), pageIndex + 1);
            }

            // Check each image
            for (int i = 0; i < currentPageImages.size(); i++) {
                ImageInfo imageInfo = currentPageImages.get(i);

                // Convert to canvas coords
                Rectangle2D.Float canvasRect = CoordinateConverter.pdfToCanvasRect(
                        imageInfo.getXPosition(),
                        imageInfo.getYPosition(),
                        imageInfo.width(),
                        imageInfo.height(),
                        pageHeight,
                        zoom
                );

                // Hit-test
                boolean hit = canvasRect.contains(canvasX, canvasY);
                logger.info("Hit-test:    {} (cursor in bounds: {})",
                        hit ? "HIT" : "MISS",
                        hit ? "YES" : "NO");

                if (hit) {
                    currentImageUnderCursor = imageInfo;
                    logger.info("FOUND: Image #{} contains cursor!", i + 1);
                    break;
                }
            }

        } catch (Exception e) {
            logger.error("ERROR: {}", e.getMessage(), e);
        }
    }

    /**
     * Copies image under cursor to clipboard.
     */
    public void handleCopyImage() {
        if (currentImageUnderCursor != null) {
            BufferedImage bufferedImage = currentImageUnderCursor.image();

            // Convert BufferedImage to JavaFX Image
            Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);

            // Put to clipboard
            ClipboardContent content = new ClipboardContent();
            content.putImage(fxImage);
            Clipboard.getSystemClipboard().setContent(content);

            logger.info("Copied image ({}x{}) to clipboard",
                    bufferedImage.getWidth(), bufferedImage.getHeight());
        } else {
            logger.warn("No image under cursor to copy");
        }
    }

    /**
     * Checks if the cursor is currently over an image.
     */
    public boolean hasImageAtPosition() {
        return currentImageUnderCursor != null;
    }

    /**
     * Clears image cache (call when page changes).
     */
    public void clearImageCache() {
        currentPageImages.clear();
        currentImageUnderCursor = null;
    }

    /**
     * Clears text selector cache (call when the page changes).
     */
    public void clearTextSelector() {
        if (smartTextSelector != null) {
            smartTextSelector.clear();
        }
        currentPageIndex = -1;
    }

    /**
     * Ensures text positions are extracted for the given page and returns page height.
     * This helper method eliminates duplicate code across multiple methods.
     *
     * @param document  The PDF document
     * @param pageIndex Zero-based page index
     * @return Page height in points
     * @throws IOException if extraction fails
     */
    private float ensureTextPositionsExtracted(PDFDocument document, int pageIndex) throws IOException {
        // Extract text positions if page changed
        if (currentPageIndex != pageIndex) {
            smartTextSelector.extractTextPositions(document.getDocument(), pageIndex);
            currentPageIndex = pageIndex;
        }

        PDPage page = document.getDocument().getPage(pageIndex);
        PDRectangle cropBox = page.getCropBox();
        return cropBox.getHeight();
    }
}
