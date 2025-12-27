package org.pdflite.manager;

import java.awt.geom.Rectangle2D;
import java.text.MessageFormat;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import org.pdflite.controller.PageRenderer;
import org.pdflite.model.Annotation;
import org.pdflite.model.HighlightAnnotation;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.ColorPicker;
import javafx.scene.paint.Color;

/**
 * Manages highlight operations including color selection, creation, and persistence.
 * This manager separates highlighted concerns from MainController.
 */
public class HighlightManager {
    private static final Logger logger = LoggerFactory.getLogger(HighlightManager.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private final ColorPicker highlightColorPicker;
    private final HighlightPersistenceManager persistenceManager;
    private final UIStateManager uiStateManager;

    private final Supplier<PDFDocument> documentSupplier;
    private final DoubleSupplier zoomSupplier;
    private final Supplier<AnnotationManager> annotationManagerSupplier;

    private static final double HIGHLIGHT_HEIGHT_MULTIPLIER = 1.35;
    private static final double HIGHLIGHT_EXTRA_PADDING_PX = 2.0;

    /**
     * Creates a new HighlightManager.
     *
     * @param highlightColorPicker the color picker for highlight color selection
     * @param uiStateManager       the UI state manager
     */
    public HighlightManager(ColorPicker highlightColorPicker,
                            UIStateManager uiStateManager,
                            Supplier<PDFDocument> documentSupplier,
                            DoubleSupplier zoomSupplier,
                            Supplier<AnnotationManager> annotationManagerSupplier) {
        this.highlightColorPicker = highlightColorPicker;
        this.uiStateManager = uiStateManager;
        this.documentSupplier = documentSupplier;
        this.zoomSupplier = zoomSupplier;
        this.annotationManagerSupplier = annotationManagerSupplier;
        this.persistenceManager = new HighlightPersistenceManager();

        initializeColorPicker();

        logger.info("HighlightManager initialized");
    }

    /**
     * Initializes the highlight color picker with default values and event handlers.
     */
    private void initializeColorPicker() {
        if (highlightColorPicker != null) {
            highlightColorPicker.setValue(Color.YELLOW);
            highlightColorPicker.setOnAction(e -> updateHighlightColorForAllPages());
            logger.debug("Highlight color picker initialized with YELLOW");
        }
    }

    /**
     * Sets up the highlight callback for the context menu handler.
     * This callback is invoked when the user highlights text from the context menu.
     *
     * @param pageRenderer the page renderer containing the context menu handler
     */
    public void setupHighlightCallback(PageRenderer pageRenderer) {
        pageRenderer.getContextMenuHandler().setHighlightCallback(
                (pageIndex, highlightRegions, defaultColor) -> {
                    try {
                        PDFDocument currentDocument = documentSupplier.get();
                        if (currentDocument == null) {
                            uiStateManager.updateStatus(lang().getString("highlight.noDocument"));
                            logger.warn("Cannot highlight: no document loaded");
                            return;
                        }

                        // Get the highlight color from the color picker
                        Color highlightColor = getHighlightColor();

                        double zoom = zoomSupplier.getAsDouble();

                        logger.info("Creating {} highlights on page {} with color {}",
                                highlightRegions.size(), pageIndex + 1, highlightColor);

                        final String batchId = java.util.UUID.randomUUID().toString();

                        // Create highlight annotations for each region
                        for (Rectangle2D region : highlightRegions) {
                            // highlightRegions are in PDF coordinates (from SmartTextSelector)
                            // Store in normalized coordinates (PDF coords * LOW_RENDER_SCALE)
                            // so highlights remain correctly positioned when zoom changes

                            // Convert PDF coordinates to normalized coordinates (independent of zoom)
                            double normalizedX = region.getX() * org.pdflite.util.Constants.LOW_RENDER_SCALE;
                            double normalizedY = region.getY() * org.pdflite.util.Constants.LOW_RENDER_SCALE;
                            double normalizedWidth = region.getWidth() * org.pdflite.util.Constants.LOW_RENDER_SCALE;

                            double originalHeight = region.getHeight() * org.pdflite.util.Constants.LOW_RENDER_SCALE;
                            double expandedHeight = (originalHeight * HIGHLIGHT_HEIGHT_MULTIPLIER) + HIGHLIGHT_EXTRA_PADDING_PX;
                            double normalizedHeight = expandedHeight;
                            double normalizedYAdjusted = normalizedY - ((expandedHeight - originalHeight) / 2.0);

                            // Create highlight annotation with normalized coordinates
                            HighlightAnnotation highlight = new HighlightAnnotation(
                                    pageIndex,
                                    normalizedX,
                                    normalizedYAdjusted,
                                    normalizedWidth,
                                    normalizedHeight,
                                    highlightColor,
                                    batchId);

                            // Add to document
                            currentDocument.addAnnotation(highlight);

                            logger.debug("Added highlight: page={}, pdfX={}, pdfY={}, normalizedX={}, normalizedY={}, w={}, h={}",
                                    pageIndex, region.getX(), region.getY(), normalizedX, normalizedYAdjusted, normalizedWidth, normalizedHeight);
                        }

                        // Mark document as modified
                        currentDocument.setHasUnsavedEdits(true);

                        // Refresh the page to show the highlights
                        AnnotationManager annotationManager = annotationManagerSupplier.get();
                        if (annotationManager != null) {
                            annotationManager.refreshPageAnnotations(pageIndex);
                        }

                        // Update status
                        uiStateManager.updateStatus(
                                MessageFormat.format(lang().getString("highlight.added"),
                                        highlightRegions.size()));

                    } catch (Exception e) {
                        logger.error("Error creating highlights", e);
                        uiStateManager.updateStatus(lang().getString("highlight.error") + ": " + e.getMessage());

                        // Show error dialog
                        javafx.application.Platform.runLater(() -> {
                            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                                    javafx.scene.control.Alert.AlertType.ERROR);
                            alert.setTitle(lang().getString("highlight.errorTitle"));
                            alert.setHeaderText(lang().getString("highlight.errorHeader"));
                            alert.setContentText(e.getMessage());
                            alert.showAndWait();
                        });
                    }
                });

        logger.info("Highlight callback configured successfully");
    }

    /**
     * Sets up the delete highlight callback for the context menu handler.
     * Deletes all segments created in the same highlight action (batch) when possible.
     */
    public void setupDeleteHighlightCallback(PageRenderer pageRenderer) {
        pageRenderer.getContextMenuHandler().setDeleteHighlightCallback(
                (pageIndex, canvasX, canvasY) -> {
                    try {
                        PDFDocument currentDocument = documentSupplier.get();
                        if (currentDocument == null) {
                            uiStateManager.updateStatus(lang().getString("highlight.noDocument"));
                            return;
                        }

                        double zoom = zoomSupplier.getAsDouble();

                        // Find topmost highlight at cursor (reverse order)
                        // canvasX/canvasY are in screen coordinates
                        // Highlights are stored in render coordinates (screen / zoom)
                        HighlightAnnotation target = null;
                        List<Annotation> all = currentDocument.getAnnotations();
                        
                        for (int i = all.size() - 1; i >= 0; i--) {
                            Annotation annotation = all.get(i);
                            if (annotation.getPageNumber() != pageIndex) {
                                continue;
                            }
                            if (annotation instanceof HighlightAnnotation highlight) {
                                // Scale render coordinates to screen coordinates for hit detection
                                double scaledX = highlight.getX() * zoom;
                                double scaledY = highlight.getY() * zoom;
                                double scaledWidth = highlight.getWidth() * zoom;
                                double scaledHeight = highlight.getHeight() * zoom;
                                double x2 = scaledX + scaledWidth;
                                double y2 = scaledY + scaledHeight;
                                
                                if (canvasX >= scaledX && canvasX <= x2 && canvasY >= scaledY && canvasY <= y2) {
                                    target = highlight;
                                    break;
                                }
                            }
                        }

                        if (target == null) {
                            uiStateManager.updateStatus(lang().getString("highlight.noHighlightAtCursor"));
                            return;
                        }

                        int removedCount = 0;
                        String batchId = target.getBatchId();
                        if (batchId != null && !batchId.isBlank()) {
                            for (int i = all.size() - 1; i >= 0; i--) {
                                Annotation annotation = all.get(i);
                                if (annotation.getPageNumber() != pageIndex) {
                                    continue;
                                }
                                if (annotation instanceof HighlightAnnotation highlight) {
                                    if (batchId.equals(highlight.getBatchId())) {
                                        all.remove(i);
                                        removedCount++;
                                    }
                                }
                            }
                        } else {
                            boolean removed = all.remove(target);
                            removedCount = removed ? 1 : 0;
                        }

                        if (removedCount <= 0) {
                            uiStateManager.updateStatus(lang().getString("highlight.deleteFailed"));
                            return;
                        }

                        currentDocument.setHasUnsavedEdits(true);

                        AnnotationManager annotationManager = annotationManagerSupplier.get();
                        if (annotationManager != null) {
                            annotationManager.refreshPageAnnotations(pageIndex);
                        }

                        uiStateManager.updateStatus(MessageFormat.format(lang().getString("highlight.deleted"), removedCount));
                    } catch (Exception e) {
                        logger.error("Error deleting highlight", e);
                        uiStateManager.updateStatus(lang().getString("highlight.errorDelete") + ": " + e.getMessage());
                    }
                }
        );
    }

    /**
     * Updates highlight color for all pages.
     */
    private void updateHighlightColorForAllPages() {
        AnnotationManager annotationManager = annotationManagerSupplier.get();
        if (annotationManager != null && highlightColorPicker != null) {
            annotationManager.updateHighlightColorForAllPages(highlightColorPicker.getValue());
        }
    }

    /**
     * Gets the current highlight color from the color picker.
     *
     * @return the highlight color (defaults to YELLOW if picker is null)
     */
    public Color getHighlightColor() {
        return highlightColorPicker != null ?
                highlightColorPicker.getValue() : Color.YELLOW;
    }

    /**
     * Sets the current document and zoom level.
     *
     * @param document the PDF document
     * @param zoom the current zoom level
     */
    // Document and zoom come from suppliers (MainController).

    /**
     * Loads existing highlights from the PDF document.
     *
     * @param document the PDF document to load highlights from
     */
    public void loadHighlights(PDFDocument document) {
        if (document == null || persistenceManager == null) {
            return;
        }

        try {
            List<HighlightAnnotation> loadedHighlights =
                    persistenceManager.loadHighlightsFromPDF(document.getDocument());

            // Add loaded highlights to document
            for (HighlightAnnotation highlight : loadedHighlights) {
                document.addAnnotation(highlight);
            }

            logger.info("Loaded {} highlights from PDF", loadedHighlights.size());
        } catch (Exception e) {
            logger.error("Error loading highlights from PDF", e);
        }
    }

    /**
     * Saves highlights to the PDF document.
     *
     * @param document the PDF document to save highlights to
     */
    public void saveHighlights(PDFDocument document) {
        if (document == null || persistenceManager == null) {
            return;
        }

        try {
            persistenceManager.saveHighlightsToPDF(
                    document.getDocument(),
                    document.getAnnotations());
            logger.info("Highlights saved to PDF");
        } catch (Exception e) {
            logger.error("Error saving highlights to PDF", e);
            uiStateManager.showError(lang().getString("highlight.saveError"),
                    lang().getString("highlight.saveErrorMsg") + ": " + e.getMessage());
        }
    }
}
