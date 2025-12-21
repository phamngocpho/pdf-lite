package org.pdflite.controller;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.pdflite.manager.ThemeManager;
import org.pdflite.model.ImageInfo;
import org.pdflite.model.PDFDocument;
import org.pdflite.util.CoordinateConverter;
import org.pdflite.util.ImageExtractor;
import org.pdflite.util.SmartTextSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.paint.Color;

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
    private float currentPageHeight = 0;

    // Callback for text editing
    private TextEditCallback textEditCallback;

    // Callback for highlight selection
    private HighlightCallback highlightCallback;

    // Callback for deleting highlight
    private DeleteHighlightCallback deleteHighlightCallback;
    // Theme manager for dialog styling
    private ThemeManager themeManager;

    public ContextMenuHandler() {
        logger.info("ContextMenuHandler initialized!");
        this.smartTextSelector = new SmartTextSelector();
    }

    /**
     * Sets the theme manager for dialog styling.
     */
    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    /**
     * Sets the callback for text editing operations.
     */
    public void setTextEditCallback(TextEditCallback callback) {
        this.textEditCallback = callback;
    }

    /**
     * Sets the callback for highlight operations.
     */
    public void setHighlightCallback(HighlightCallback callback) {
        this.highlightCallback = callback;
    }

    /**
     * Callback interface for text editing operations.
     */
    public interface TextEditCallback {
        void onTextEdit(int pageIndex,
                        float coverX, float coverY, float coverWidth, float coverHeight,
                        float textX, float textY,
                        String newText, float fontSize, PDFont font);
    }

    /**
     * Callback interface for highlight operations.
     */
    public interface HighlightCallback {
        void onHighlight(int pageIndex, List<Rectangle2D> highlightRegions, Color highlightColor);
    }

    /**
     * Callback interface for deleting highlight annotations.
     */
    public interface DeleteHighlightCallback {
        void onDeleteHighlight(int pageIndex, double canvasX, double canvasY);
    }

    /**
     * Callback interface for adding comment annotations.
     */
    public interface AddCommentCallback {
        void onAddComment(int pageIndex, double canvasX, double canvasY, String comment);
    }

    /**
     * Callback interface for deleting comment annotations.
     */
    public interface DeleteCommentCallback {
        void onDeleteComment(int pageIndex, double canvasX, double canvasY);
    }

    /**
     * Sets the callback for deleting highlight annotations.
     */
    public void setDeleteHighlightCallback(DeleteHighlightCallback callback) {
        this.deleteHighlightCallback = callback;
    }

    private AddCommentCallback addCommentCallback;
    private DeleteCommentCallback deleteCommentCallback;

    /**
     * Sets the callback for adding comment annotations.
     */
    public void setAddCommentCallback(AddCommentCallback callback) {
        this.addCommentCallback = callback;
    }

    /**
     * Sets the callback for deleting comment annotations.
     */
    public void setDeleteCommentCallback(DeleteCommentCallback callback) {
        this.deleteCommentCallback = callback;
    }

    /**
     * Handles add comment operation.
     * Opens a dialog for the user to enter comment text.
     */
    public void handleAddComment(int pageIndex, double canvasX, double canvasY, double zoom) {
        logger.info("Opening comment dialog at canvas position ({}, {})", canvasX, canvasY);

        // Show custom comment dialog
        String comment = org.pdflite.dialog.CustomCommentDialog.show(themeManager);

        // If OK was clicked, add the comment via callback
        if (comment != null && !comment.trim().isEmpty()) {
            logger.info("Comment confirmed: '{}'", comment);

            if (addCommentCallback != null) {
                addCommentCallback.onAddComment(pageIndex, canvasX, canvasY, comment);
            } else {
                logger.warn("Add comment callback not set");
            }
        } else {
            logger.info("Comment cancelled or empty");
        }
    }

    /**
     * Handles delete comment operation.
     */
    public void handleDeleteComment(int pageIndex, double canvasX, double canvasY) {
        if (deleteCommentCallback == null) {
            logger.warn("Delete comment callback not set");
            return;
        }

        deleteCommentCallback.onDeleteComment(pageIndex, canvasX, canvasY);
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

            // Use smart text selector to get the selected text, positions, and highlight regions
            String selectedText = smartTextSelector.getSelectedText(pdfStart, pdfEnd);
            String cleanedText = (selectedText != null) ? selectedText.trim() : "";
            List<org.apache.pdfbox.text.TextPosition> textPositions =
                    smartTextSelector.getSelectedTextPositions(pdfStart, pdfEnd);
            List<Rectangle2D> highlightRegions =
                    smartTextSelector.getHighlightRegions(pdfStart, pdfEnd);

            // Create selection info using the drag rectangle converted to PDF coordinates
            // The drag rectangle gives us the correct position in PDF space
            double x1 = Math.min(canvasX1, canvasX2);
            double y1 = Math.min(canvasY1, canvasY2);
            double width = Math.abs(canvasX2 - canvasX1);
            double height = Math.abs(canvasY2 - canvasY1);
            Rectangle2D.Float pdfRect = CoordinateConverter.canvasToPdfJavaRect(
                    x1, y1, width, height, pageHeight, zoom
            );

            currentSelection = new SelectionInfo(pageIndex, pdfRect, cleanedText, new ArrayList<>(),
                    textPositions, highlightRegions);

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
     * Handles highlight selection operation.
     * Creates highlight annotations for the selected text regions.
     */
    public void handleHighlightSelection() {
        if (currentSelection == null || !currentSelection.hasText()) {
            logger.warn("No text to highlight");
            return;
        }

        if (highlightCallback == null) {
            logger.warn("Highlight callback not set");
            return;
        }

        List<Rectangle2D> highlightRegions = currentSelection.getHighlightRegions();
        if (highlightRegions == null || highlightRegions.isEmpty()) {
            logger.warn("No highlight regions available");
            return;
        }

        // Get the highlight color from MainController via callback
        // For now, use a default color (will be set by callback)
        Color highlightColor = Color.YELLOW;

        logger.info("Creating {} highlight annotations for page {}",
                highlightRegions.size(), currentSelection.getPageIndex());

        // Call the callback to create highlights
        highlightCallback.onHighlight(
                currentSelection.getPageIndex(),
                highlightRegions,
                highlightColor);
    }

    /**
     * Handles delete highlight operation.
     * This is invoked from the context menu when user right-clicks on an existing highlight.
     */
    public void handleDeleteHighlight(int pageIndex, double canvasX, double canvasY) {
        if (deleteHighlightCallback == null) {
            logger.warn("Delete highlight callback not set");
            return;
        }

        deleteHighlightCallback.onDeleteHighlight(pageIndex, canvasX, canvasY);
    }

    /**
     * Opens the text edit dialog for the selected text.
     * Note: This adds new text on top of the old text (PDF text editing limitation).
     */
    public void handleEditText() {
        if (currentSelection == null || !currentSelection.hasText()) {
            logger.warn("No text to edit");
            return;
        }

        String originalText = currentSelection.getText();
        logger.info("Opening text edit dialog for: '{}'", originalText);

        try {
            // Load the FXML file
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader();
            loader.setLocation(getClass().getResource("/org/pdflite/text-edit-dialog.fxml"));
            javafx.scene.layout.VBox dialogRoot = loader.load();

            // Get the controller and set the original text
            org.pdflite.dialog.TextEditDialogController controller = loader.getController();
            controller.setOriginalText(originalText);

            // Create and show the dialog
            javafx.stage.Stage dialogStage = new javafx.stage.Stage();
            dialogStage.initStyle(javafx.stage.StageStyle.TRANSPARENT); // Transparent for rounded corners
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            javafx.scene.Scene dialogScene = new javafx.scene.Scene(dialogRoot);
            dialogScene.setFill(javafx.scene.paint.Color.TRANSPARENT); // Transparent background

            // Apply current theme to dialog
            if (themeManager != null) {
                themeManager.applyThemeToScene(dialogScene);
            }

            dialogStage.setScene(dialogScene);
            controller.setDialogStage(dialogStage);

            dialogStage.showAndWait();

            // If OK was clicked, add the new text via callback
            if (controller.isOkClicked()) {
                String newText = controller.getNewText();
                logger.info("Text edit confirmed: '{}' -> '{}'", originalText, newText);

                if (textEditCallback != null) {
                    // Use highlight regions to get the actual text bounds (what's shown in blue)
                    List<Rectangle2D> highlightRegions = currentSelection.getHighlightRegions();

                    if (highlightRegions == null || highlightRegions.isEmpty()) {
                        logger.warn("No highlight regions available, using selection rectangle");
                        // Fallback to selection rectangle
                        highlightRegions = new ArrayList<>();
                        highlightRegions.add(currentSelection.pdfRect);
                    }

                    // Calculate bounding box of all highlight regions
                    double minX = Double.MAX_VALUE;
                    double minY = Double.MAX_VALUE;
                    double maxX = Double.MIN_VALUE;
                    double maxY = Double.MIN_VALUE;

                    for (Rectangle2D region : highlightRegions) {
                        minX = Math.min(minX, region.getMinX());
                        minY = Math.min(minY, region.getMinY());
                        maxX = Math.max(maxX, region.getMaxX());
                        maxY = Math.max(maxY, region.getMaxY());
                    }

                    // These are in Java coordinates (top-left origin)
                    float textX = (float) minX;
                    float yJava = (float) minY;
                    float coverWidth = (float) (maxX - minX);
                    float height = (float) (maxY - minY);

                    // Convert to PDF User Space coordinates (bottom-left origin)
                    // For the covering rectangle. We need the bottom-left corner
                    // to Extend upward for diacritics and downward for descenders
                    float coverY = currentPageHeight - yJava - height - 3.0f;  // Extend down 3 points
                    float coverHeight = height + 9.0f;  // Extend up 6 points plus down 3 points = total 9

                    // For text placement, we need the baseline position
                    // Since coverY was extended down by 3 points, add 3 to compensate and keep text position
                    // Then add the original offset (12% of height)
                    float textY = coverY + 3.0f + (height * 0.05f);

                    // Extract font and font size from the first TextPosition
                    PDFont originalFont = null;
                    PDFont font;
                    float fontSize = 12.0f; // Default fallback

                    List<org.apache.pdfbox.text.TextPosition> positions = currentSelection.getTextPositions();
                    if (positions != null && !positions.isEmpty()) {
                        org.apache.pdfbox.text.TextPosition firstPos = positions.getFirst();
                        try {
                            originalFont = firstPos.getFont();
                            fontSize = firstPos.getFontSizeInPt();
                            logger.info("Extracted font: {} (size: {})",
                                    originalFont != null ? originalFont.getName() : "null", fontSize);
                        } catch (Exception e) {
                            logger.warn("Could not extract font from TextPosition: {}", e.getMessage());
                        }
                    }

                    // Map the original font to a Standard 14 font that supports all characters
                    // Embedded subset fonts cannot be used for new text
                    font = mapToStandardFont(originalFont);

                    // Ensure minimum font size for readability
                    if (fontSize < 8.0f) {
                        logger.warn("Font size {} too small, using 10pt", fontSize);
                        fontSize = 10.0f;
                    }

                    logger.info("TEXT REPLACEMENT:");
                    logger.info("  Page height: {}", currentPageHeight);
                    logger.info("  Selection rect (Java coords): x={}, y={}, width={}, height={}",
                            textX, yJava, coverWidth, height);
                    logger.info("  Cover rect (User Space): x={}, y={}, width={}, height={}",
                            textX, coverY, coverWidth, coverHeight);
                    logger.info("  Text position (User Space): x={}, y={}", textX, textY);
                    logger.info("  Font: {}, Size: {}", font.getName(), fontSize);

                    textEditCallback.onTextEdit(currentSelection.pageIndex,
                            textX, coverY, coverWidth, coverHeight,
                            textX, textY,
                            newText, fontSize, font);
                } else {
                    logger.warn("Text edit callback not set - cannot add text to PDF");
                }
            } else {
                logger.info("Text edit cancelled");
            }
        } catch (java.io.IOException e) {
            logger.error("Error showing text edit dialog", e);
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
        private final List<org.apache.pdfbox.text.TextPosition> textPositions;
        private final List<Rectangle2D> highlightRegions;


        public SelectionInfo(int pageIndex, Rectangle2D.Float pdfRect,
                             String text, List<Object> images) {
            this.pageIndex = pageIndex;
            this.pdfRect = pdfRect;
            this.text = text;
            this.textPositions = new ArrayList<>();
            this.highlightRegions = new ArrayList<>();
            // TODO: Implement image extraction
        }

        public SelectionInfo(int pageIndex, Rectangle2D.Float pdfRect,
                             String text, List<Object> images,
                             List<org.apache.pdfbox.text.TextPosition> textPositions) {
            this.pageIndex = pageIndex;
            this.pdfRect = pdfRect;
            this.text = text;
            this.textPositions = textPositions != null ? textPositions : new ArrayList<>();
            this.highlightRegions = new ArrayList<>();
            // TODO: Implement image extraction
        }

        public SelectionInfo(int pageIndex, Rectangle2D.Float pdfRect,
                             String text, List<Object> images,
                             List<org.apache.pdfbox.text.TextPosition> textPositions,
                             List<Rectangle2D> highlightRegions) {
            this.pageIndex = pageIndex;
            this.pdfRect = pdfRect;
            this.text = text;
            this.textPositions = textPositions != null ? textPositions : new ArrayList<>();
            this.highlightRegions = highlightRegions != null ? highlightRegions : new ArrayList<>();
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

        public List<org.apache.pdfbox.text.TextPosition> getTextPositions() {
            return textPositions;
        }

        public List<Rectangle2D> getHighlightRegions() {
            return highlightRegions;
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
     * Maps an original PDF font to a Standard 14 font that supports all characters.
     * Embedded subset fonts cannot be used for adding new text, so we need to
     * find a similar Standard 14 font.
     *
     * @param originalFont The original font from the PDF (maybe null or subset)
     * @return A Standard 14 font that can be used for new text
     */
    private PDFont mapToStandardFont(PDFont originalFont) {
        if (originalFont == null) {
            logger.info("No original font, using Helvetica");
            return new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA);
        }

        String fontName = originalFont.getName().toLowerCase();
        logger.info("Mapping font '{}' to Standard 14 font", originalFont.getName());

        // Check for bold
        boolean isBold = fontName.contains("bold");

        // Check for italic/oblique
        boolean isItalic = fontName.contains("italic") || fontName.contains("oblique");

        // Check for monospace/courier
        boolean isMono = fontName.contains("courier") || fontName.contains("mono");

        // Check for serif/times
        boolean isSerif = fontName.contains("times") || fontName.contains("serif");

        // Map to appropriate Standard 14 font
        Standard14Fonts.FontName standardFont;

        if (isMono) {
            if (isBold && isItalic) {
                standardFont = Standard14Fonts.FontName.COURIER_BOLD_OBLIQUE;
            } else if (isBold) {
                standardFont = Standard14Fonts.FontName.COURIER_BOLD;
            } else if (isItalic) {
                standardFont = Standard14Fonts.FontName.COURIER_OBLIQUE;
            } else {
                standardFont = Standard14Fonts.FontName.COURIER;
            }
        } else if (isSerif) {
            if (isBold && isItalic) {
                standardFont = Standard14Fonts.FontName.TIMES_BOLD_ITALIC;
            } else if (isBold) {
                standardFont = Standard14Fonts.FontName.TIMES_BOLD;
            } else if (isItalic) {
                standardFont = Standard14Fonts.FontName.TIMES_ITALIC;
            } else {
                standardFont = Standard14Fonts.FontName.TIMES_ROMAN;
            }
        } else {
            // Default to Helvetica (sans-serif)
            if (isBold && isItalic) {
                standardFont = Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE;
            } else if (isBold) {
                standardFont = Standard14Fonts.FontName.HELVETICA_BOLD;
            } else if (isItalic) {
                standardFont = Standard14Fonts.FontName.HELVETICA_OBLIQUE;
            } else {
                standardFont = Standard14Fonts.FontName.HELVETICA;
            }
        }

        logger.info("Mapped to Standard 14 font: {}", standardFont);
        return new PDType1Font(standardFont);
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
        currentPageHeight = cropBox.getHeight();
        return currentPageHeight;
    }
}
