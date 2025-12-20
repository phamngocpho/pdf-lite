package org.pdflite.manager;

import javafx.scene.control.ColorPicker;
import javafx.scene.paint.Color;
import org.pdflite.controller.ContextMenuHandler;
import org.pdflite.controller.PageRenderer;
import org.pdflite.model.Annotation;
import org.pdflite.model.HighlightAnnotation;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * Manages highlight operations including color selection, creation, and persistence.
 * This manager separates highlight concerns from MainController.
 */
public class HighlightManager {
    private static final Logger logger = LoggerFactory.getLogger(HighlightManager.class);
    
    private final ColorPicker highlightColorPicker;
    private final HighlightPersistenceManager persistenceManager;
    private final UIStateManager uiStateManager;
    private final AnnotationManager annotationManager;
    
    private PDFDocument currentDocument;
    private double currentZoom = 1.0;
    
    /**
     * Creates a new HighlightManager.
     *
     * @param highlightColorPicker the color picker for highlight color selection
     * @param uiStateManager the UI state manager
     * @param annotationManager the annotation manager (can be null initially)
     */
    public HighlightManager(ColorPicker highlightColorPicker, 
                           UIStateManager uiStateManager,
                           AnnotationManager annotationManager) {
        this.highlightColorPicker = highlightColorPicker;
        this.uiStateManager = uiStateManager;
        this.annotationManager = annotationManager;
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
                    if (currentDocument == null) {
                        uiStateManager.updateStatus("No document loaded");
                        logger.warn("Cannot highlight: no document loaded");
                        return;
                    }
                    
                    // Get the highlight color from the color picker
                    Color highlightColor = getHighlightColor();
                    
                    logger.info("Creating {} highlights on page {} with color {}", 
                            highlightRegions.size(), pageIndex + 1, highlightColor);
                    
                    // Create highlight annotations for each region
                    for (Rectangle2D region : highlightRegions) {
                        // highlightRegions are in PDF coordinates (from SmartTextSelector)
                        // Need to convert to canvas coordinates for AnnotationLayer
                        
                        // Convert PDF coordinates to canvas coordinates
                        double finalScale = currentZoom * org.pdflite.util.Constants.LOW_RENDER_SCALE;
                        
                        double canvasX = region.getX() * finalScale;
                        double canvasY = region.getY() * finalScale;
                        double canvasWidth = region.getWidth() * finalScale;
                        double canvasHeight = region.getHeight() * finalScale;
                        
                        // Create highlight annotation
                        HighlightAnnotation highlight = new HighlightAnnotation(
                            pageIndex, 
                            canvasX, 
                            canvasY, 
                            canvasWidth, 
                            canvasHeight, 
                            highlightColor);
                        
                        // Add to document
                        currentDocument.addAnnotation(highlight);
                        
                        logger.debug("Added highlight: page={}, pdfX={}, pdfY={}, canvasX={}, canvasY={}, w={}, h={}", 
                                pageIndex, region.getX(), region.getY(), canvasX, canvasY, canvasWidth, canvasHeight);
                    }
                    
                    // Mark document as modified
                    currentDocument.setHasUnsavedEdits(true);
                    
                    // Refresh the page to show the highlights
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
     * Updates highlight color for all pages.
     */
    private void updateHighlightColorForAllPages() {
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
    public void setDocument(PDFDocument document, double zoom) {
        this.currentDocument = document;
        this.currentZoom = zoom;
    }
    
    /**
     * Sets the current zoom level.
     *
     * @param zoom the zoom level
     */
    public void setZoom(double zoom) {
        this.currentZoom = zoom;
    }
    
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
}
