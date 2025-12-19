package org.pdflite.manager;

import javafx.geometry.Point2D;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.pdflite.model.PDFDocument;
import org.pdflite.model.TextRegion;
import org.pdflite.service.PDFService;
import org.pdflite.util.CoordinateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manager for text extraction, selection, and editing operations in PDF documents.
 * <p>
 * This manager provides functionality to:
 * <ul>
 *   <li>Extract text positions from PDF pages</li>
 *   <li>Identify text regions at specific coordinates</li>
 *   <li>Replace text in identified regions</li>
 *   <li>Show text edit dialogs</li>
 * </ul>
 * </p>
 * <p>
 * The manager handles coordinate conversion between PDF and JavaFX coordinate systems
 * and integrates with the ContentStreamManager for actual text manipulation.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class TextEditManager {
    private static final Logger logger = LoggerFactory.getLogger(TextEditManager.class);

    private final PDFService pdfService;
    private final UIStateManager uiStateManager;

    /**
     * Creates a new TextEditManager.
     *
     * @param pdfService     the PDF service for document operations
     * @param uiStateManager the UI state manager for status updates
     */
    public TextEditManager(PDFService pdfService, UIStateManager uiStateManager) {
        this.pdfService = pdfService;
        this.uiStateManager = uiStateManager;
    }

    /**
     * Extracts text positions from a specific page.
     * <p>
     * This method uses a custom PDFTextStripper to extract not just the text content,
     * but also the position information for each character/word on the page.
     * </p>
     *
     * @param page the PDF page to extract text from
     * @return a list of TextPosition objects containing text and position information
     * @throws IOException if text extraction fails
     */
    public List<TextPosition> extractTextPositions(PDPage page) throws IOException {
        if (page == null) {
            throw new IllegalArgumentException("Page cannot be null");
        }

        List<TextPosition> positions = new ArrayList<>();

        // Create a custom text stripper that captures TextPosition objects
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
                positions.addAll(textPositions);
                super.writeString(text, textPositions);
            }
        };

        // Extract text from the page (this populates the position list)
        try (PDDocument tempDoc = new PDDocument()) {
            tempDoc.addPage(page);
            stripper.getText(tempDoc);
        }

        logger.debug("Extracted {} text positions from page", positions.size());
        return positions;
    }

    /**
     * Extracts text positions from a page in a PDFDocument.
     *
     * @param pdfDoc    the PDF document
     * @param pageIndex the zero-based page index
     * @return a list of TextPosition objects
     * @throws IOException              if text extraction fails
     * @throws IllegalArgumentException if page index is invalid
     */
    public List<TextPosition> extractTextPositions(PDFDocument pdfDoc, int pageIndex) throws IOException {
        if (pdfDoc == null) {
            throw new IllegalArgumentException("PDFDocument cannot be null");
        }
        if (pageIndex < 0 || pageIndex >= pdfDoc.getTotalPages()) {
            throw new IllegalArgumentException("Invalid page index: " + pageIndex);
        }

        PDPage page = pdfDoc.getDocument().getPage(pageIndex);
        return extractTextPositions(page);
    }

    /**
     * Finds a text region at the specified coordinates on a page.
     * <p>
     * This method extracts all text positions from the page and identifies
     * which text region (if any) contains the given point. The coordinates
     * should be in PDF coordinate space.
     * </p>
     *
     * @param pdfDoc    the PDF document
     * @param pageIndex the zero-based page index
     * @param x         the X coordinate in PDF space
     * @param y         the Y coordinate in PDF space
     * @return a TextRegion if text is found at the coordinates, null otherwise
     * @throws IOException if text extraction fails
     */
    public TextRegion findTextAt(PDFDocument pdfDoc, int pageIndex, double x, double y) throws IOException {
        List<TextPosition> positions = extractTextPositions(pdfDoc, pageIndex);

        if (positions.isEmpty()) {
            logger.debug("No text found on page {}", pageIndex);
            return null;
        }

        // Find text positions that contain the click point
        List<TextPosition> matchingPositions = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        for (TextPosition pos : positions) {
            double posX = pos.getX();
            double posY = pos.getY();
            double posWidth = pos.getWidth();
            double posHeight = pos.getHeight();

            // Check if the point is within this text position's bounds
            if (x >= posX && x <= (posX + posWidth) &&
                    y >= posY && y <= (posY + posHeight)) {

                matchingPositions.add(pos);
                textBuilder.append(pos.getUnicode());

                // Update bounding box
                minX = Math.min(minX, posX);
                minY = Math.min(minY, posY);
                maxX = Math.max(maxX, posX + posWidth);
                maxY = Math.max(maxY, posY + posHeight);
            }
        }

        if (matchingPositions.isEmpty()) {
            logger.debug("No text found at coordinates ({}, {}) on page {}", x, y, pageIndex);
            return null;
        }

        // Create and return the text region
        double width = maxX - minX;
        double height = maxY - minY;
        String text = textBuilder.toString();

        logger.debug("Found text region at ({}, {}): '{}' ({}x{})",
                minX, minY, text, width, height);

        return new TextRegion(pageIndex, minX, minY, width, height, text, matchingPositions);
    }

    /**
     * Finds a text region at JavaFX coordinates (converts to PDF coordinates first).
     *
     * @param pdfDoc     the PDF document
     * @param pageIndex  the zero-based page index
     * @param javafxX    the X coordinate in JavaFX space
     * @param javafxY    the Y coordinate in JavaFX space
     * @param pageHeight the page height in points
     * @param scale      the current zoom scale
     * @return a TextRegion if text is found, null otherwise
     * @throws IOException if text extraction fails
     */
    public TextRegion findTextAtJavaFX(PDFDocument pdfDoc, int pageIndex,
                                       double javafxX, double javafxY,
                                       double pageHeight, double scale) throws IOException {
        // Convert JavaFX coordinates to PDF coordinates
        Point2D pdfPoint = CoordinateUtil.convertPoint(
                new Point2D(javafxX, javafxY),
                pageHeight,
                scale,
                true // to PDF
        );

        return findTextAt(pdfDoc, pageIndex, pdfPoint.getX(), pdfPoint.getY());
    }

    /**
     * Stores extracted text positions for later use.
     * <p>
     * This method can be used to cache text positions to avoid repeated extraction.
     * The positions are stored in a map keyed by page index.
     * </p>
     *
     * @param pdfDoc    the PDF document
     * @param pageIndex the page index
     * @param positions the text positions to store
     */
    public void storeTextPositions(PDFDocument pdfDoc, int pageIndex, List<TextPosition> positions) {
        if (pdfDoc == null || positions == null) {
            return;
        }

        // For now, we don't implement caching in PDFDocument
        // This is a placeholder for future enhancement
        logger.debug("Stored {} text positions for page {}", positions.size(), pageIndex);
    }

    /**
     * Retrieves stored text positions for a page.
     *
     * @param pdfDoc    the PDF document
     * @param pageIndex the page index
     * @return the stored text positions, or null if not cached
     */
    public List<TextPosition> getStoredTextPositions(PDFDocument pdfDoc, int pageIndex) {
        if (pdfDoc == null) {
            return null;
        }

        // For now, we don't implement caching in PDFDocument
        // This is a placeholder for future enhancement
        // Always return null to force re-extraction
        return null;
    }

    /**
     * Replaces text in a specific region of a PDF page.
     * <p>
     * This method uses the ContentStreamManager to manipulate the PDF content stream.
     * Note: This is a complex operation that requires careful handling of the PDF structure.
     * </p>
     *
     * @param doc       the PDF document
     * @param pageIndex the page index
     * @param region    the text region to replace
     * @param newText   the new text content
     * @throws IOException if the operation fails
     */
    public void replaceText(PDDocument doc, int pageIndex, TextRegion region, String newText) throws IOException {
        if (doc == null || region == null || newText == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }

        logger.info("Replacing text on page {}: '{}' -> '{}'",
                pageIndex, region.text(), newText);

        // This is a placeholder for the actual implementation
        // The actual text replacement would be done by ContentStreamManager
        // which will be implemented in Task 4

        uiStateManager.updateStatus("Text replacement not yet implemented");
        logger.warn("Text replacement is not yet implemented - requires ContentStreamManager");
    }

    /**
     * Shows a text edit dialog for the given region.
     * <p>
     * This method displays a dialog allowing the user to edit the text content
     * of the selected region. When confirmed, the callback is invoked with the new text.
     * </p>
     *
     * @param region    the text region to edit
     * @param onConfirm callback to invoke with the new text when confirmed
     */
    public void showTextEditDialog(TextRegion region, Consumer<String> onConfirm) {
        if (region == null || onConfirm == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }

        logger.info("Showing text edit dialog for region: '{}'", region.text());

        try {
            // Load the FXML file
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader();
            loader.setLocation(getClass().getResource("/org/pdflite/text-edit-dialog.fxml"));
            javafx.scene.layout.VBox dialogRoot = loader.load();

            // Get the controller and set the original text
            org.pdflite.dialog.TextEditDialogController controller = loader.getController();
            controller.setOriginalText(region.text());

            // Create and show the dialog
            javafx.stage.Stage dialogStage = new javafx.stage.Stage();
            dialogStage.setTitle("Edit Text");
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.setScene(new javafx.scene.Scene(dialogRoot));
            controller.setDialogStage(dialogStage);

            dialogStage.showAndWait();

            // If OK was clicked, invoke the callback
            if (controller.isOkClicked()) {
                String newText = controller.getNewText();
                logger.info("Text edit confirmed: '{}' -> '{}'", region.text(), newText);
                onConfirm.accept(newText);
                uiStateManager.updateStatus("Text edited successfully");
            } else {
                logger.info("Text edit cancelled");
                uiStateManager.updateStatus("Text edit cancelled");
            }
        } catch (IOException e) {
            logger.error("Error showing text edit dialog", e);
            uiStateManager.updateStatus("Error showing text edit dialog: " + e.getMessage());
        }
    }

    /**
     * Extracts all text regions from a page.
     * <p>
     * This method groups individual TextPosition objects into logical text regions
     * (words, lines, or paragraphs) based on their proximity.
     * </p>
     *
     * @param pdfDoc    the PDF document
     * @param pageIndex the page index
     * @return a list of text regions found on the page
     * @throws IOException if text extraction fails
     */
    public List<TextRegion> extractAllTextRegions(PDFDocument pdfDoc, int pageIndex) throws IOException {
        List<TextPosition> positions = extractTextPositions(pdfDoc, pageIndex);
        List<TextRegion> regions = new ArrayList<>();

        if (positions.isEmpty()) {
            return regions;
        }

        // Group positions into words (simple implementation)
        // A more sophisticated implementation would group by lines and paragraphs
        List<TextPosition> currentWord = new ArrayList<>();
        double lastX = -1;
        double lastY = -1;

        for (TextPosition pos : positions) {
            double x = pos.getX();
            double y = pos.getY();

            // Check if this position is part of the current word
            // (close enough in X and same Y)
            boolean isNewWord = false;
            if (lastX >= 0) {
                double xGap = x - lastX;
                double yGap = Math.abs(y - lastY);

                // New word if there's a significant gap or different line
                if (xGap > pos.getWidth() * 0.5 || yGap > pos.getHeight() * 0.5) {
                    isNewWord = true;
                }
            }

            if (isNewWord && !currentWord.isEmpty()) {
                // Create a region for the current word
                regions.add(createRegionFromPositions(pageIndex, currentWord));
                currentWord.clear();
            }

            currentWord.add(pos);
            lastX = x + pos.getWidth();
            lastY = y;
        }

        // Add the last word
        if (!currentWord.isEmpty()) {
            regions.add(createRegionFromPositions(pageIndex, currentWord));
        }

        logger.debug("Extracted {} text regions from page {}", regions.size(), pageIndex);
        return regions;
    }

    /**
     * Creates a TextRegion from a list of TextPosition objects.
     *
     * @param pageIndex the page index
     * @param positions the text positions
     * @return a TextRegion encompassing all the positions
     */
    private TextRegion createRegionFromPositions(int pageIndex, List<TextPosition> positions) {
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("Positions list cannot be empty");
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        StringBuilder text = new StringBuilder();

        for (TextPosition pos : positions) {
            text.append(pos.getUnicode());
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxX = Math.max(maxX, pos.getX() + pos.getWidth());
            maxY = Math.max(maxY, pos.getY() + pos.getHeight());
        }

        double width = maxX - minX;
        double height = maxY - minY;

        return new TextRegion(pageIndex, minX, minY, width, height, text.toString(), positions);
    }
}
