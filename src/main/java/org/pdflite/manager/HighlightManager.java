package org.pdflite.manager;

import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import javafx.scene.control.ToggleGroup;
import org.pdflite.controller.PageRenderer;
import org.pdflite.model.Annotation;
import org.pdflite.model.HighlightAnnotation;
import org.pdflite.model.PDFDocument;
import org.pdflite.view.AnnotationLayer;
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

    private final ColorPicker highlightColorPicker;
    private final HighlightPersistenceManager persistenceManager;
    private final UIStateManager uiStateManager;
    private final PageRenderer pageRenderer;

    private final Supplier<PDFDocument> documentSupplier;
    private final DoubleSupplier zoomSupplier;
    private final Supplier<AnnotationManager> annotationManagerSupplier;

    private boolean highlightModeActive = false;

    private static final double HIGHLIGHT_HEIGHT_MULTIPLIER = 1.35;
    private static final double HIGHLIGHT_EXTRA_PADDING_PX = 2.0;

    /**
     * Creates a new HighlightManager.
     *
     * @param highlightColorPicker the color picker for highlight color selection
     * @param uiStateManager       the UI state manager
     * @param pageRenderer         the page renderer
     */
    public HighlightManager(ColorPicker highlightColorPicker,
                            UIStateManager uiStateManager,
                            Supplier<PDFDocument> documentSupplier,
                            DoubleSupplier zoomSupplier,
                            Supplier<AnnotationManager> annotationManagerSupplier,
                            PageRenderer pageRenderer) {
        this.highlightColorPicker = highlightColorPicker;
        this.uiStateManager = uiStateManager;
        this.documentSupplier = documentSupplier;
        this.zoomSupplier = zoomSupplier;
        this.annotationManagerSupplier = annotationManagerSupplier;
        this.pageRenderer = pageRenderer;
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
                            uiStateManager.updateStatus("No document loaded");
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
                            // Need to convert to canvas coordinates for AnnotationLayer

                            // Convert PDF coordinates to canvas coordinates
                            double finalScale = zoom * org.pdflite.util.Constants.LOW_RENDER_SCALE;

                            double canvasX = region.getX() * finalScale;
                            double canvasY = region.getY() * finalScale;
                            double canvasWidth = region.getWidth() * finalScale;

                            double originalHeight = region.getHeight() * finalScale;
                            double canvasHeight = (originalHeight * HIGHLIGHT_HEIGHT_MULTIPLIER) + HIGHLIGHT_EXTRA_PADDING_PX;
                            double canvasYAdjusted = canvasY - ((canvasHeight - originalHeight) / 2.0);

                            // Create highlight annotation
                            HighlightAnnotation highlight = new HighlightAnnotation(
                                    pageIndex,
                                    canvasX,
                                    canvasYAdjusted,
                                    canvasWidth,
                                    canvasHeight,
                                    highlightColor,
                                    batchId);

                            // Add to document
                            currentDocument.addAnnotation(highlight);

                            logger.debug("Added highlight: page={}, pdfX={}, pdfY={}, canvasX={}, canvasY={}, w={}, h={}",
                                    pageIndex, region.getX(), region.getY(), canvasX, canvasY, canvasWidth, canvasHeight);
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
                                String.format("Added %d highlight(s) - Save to persist changes",
                                        highlightRegions.size()));

                    } catch (Exception e) {
                        logger.error("Error creating highlights", e);
                        uiStateManager.updateStatus("Error creating highlights: " + e.getMessage());

                        // Show error dialog
                        javafx.application.Platform.runLater(() -> {
                            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                                    javafx.scene.control.Alert.AlertType.ERROR);
                            alert.setTitle("Highlight Error");
                            alert.setHeaderText("Failed to create highlights");
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
                            uiStateManager.updateStatus("No document loaded");
                            return;
                        }

                        // Find topmost highlight at cursor (reverse order)
                        HighlightAnnotation target = null;
                        List<Annotation> all = currentDocument.getAnnotations();
                        for (int i = all.size() - 1; i >= 0; i--) {
                            Annotation annotation = all.get(i);
                            if (annotation.getPageNumber() != pageIndex) {
                                continue;
                            }
                            if (annotation instanceof HighlightAnnotation highlight) {
                                double x2 = highlight.getX() + highlight.getWidth();
                                double y2 = highlight.getY() + highlight.getHeight();
                                if (canvasX >= highlight.getX() && canvasX <= x2 && canvasY >= highlight.getY() && canvasY <= y2) {
                                    target = highlight;
                                    break;
                                }
                            }
                        }

                        if (target == null) {
                            uiStateManager.updateStatus("No highlight at cursor");
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
                            uiStateManager.updateStatus("Failed to delete highlight");
                            return;
                        }

                        currentDocument.setHasUnsavedEdits(true);

                        AnnotationManager annotationManager = annotationManagerSupplier.get();
                        if (annotationManager != null) {
                            annotationManager.refreshPageAnnotations(pageIndex);
                        }

                        uiStateManager.updateStatus("Deleted " + removedCount + " highlight segment(s) - Save to persist changes");
                    } catch (Exception e) {
                        logger.error("Error deleting highlight", e);
                        uiStateManager.updateStatus("Error deleting highlight: " + e.getMessage());
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
            uiStateManager.showError("Save Error",
                    "Failed to save highlights: " + e.getMessage());
        }
    }
    
    /**
     * Toggles highlight mode on/off.
     * 
     * @param drawingToolsGroup the toggle group for drawing tools (to deselect when highlight is active)
     */
    public void toggleHighlightMode(ToggleGroup drawingToolsGroup) {
        highlightModeActive = !highlightModeActive;

        if (highlightModeActive) {
            // Deselect drawing tools when highlight is active
            if (drawingToolsGroup != null) {
                drawingToolsGroup.selectToggle(null);
            }

            uiStateManager.updateStatus("Highlight mode: Active - Click and drag to highlight");
            if (pageRenderer != null) {
                pageRenderer.setHighlightModeActive();
            }
            updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.HIGHLIGHT);
        } else {
            // Disable highlight mode
            uiStateManager.updateStatus("Highlight mode: Disabled");
            if (pageRenderer != null) {
                pageRenderer.setHighlightModeActive();
            }
            updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.NONE);
        }
        
        logger.debug("Highlight mode toggled: {}", highlightModeActive);
    }
    
    /**
     * Updates annotation mode for all pages.
     */
    private void updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode mode) {
        AnnotationManager annotationManager = annotationManagerSupplier.get();
        if (annotationManager != null) {
            annotationManager.updateAnnotationModeForAllPages(mode);
        }
    }
    
    /**
     * Checks if highlight mode is currently active.
     */
    public boolean isHighlightModeActive() {
        return highlightModeActive;
    }
}
