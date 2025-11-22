package org.pdflite.command;

import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.pdflite.controller.MainController;
import org.pdflite.model.HighlightAnnotation;
import org.pdflite.view.AnnotationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Command for adding/removing highlight annotations.
 * <p>
 * This command stores highlight annotation data to allow undo/redo.
 * Since highlights are visual overlays (not embedded in PDF), they can be
 * easily added and removed from the AnnotationLayer.
 * </p>
 * 
 * @author PDF Lite Team
 * @version 1.0.0
 */
public class HighlightCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(HighlightCommand.class);
    
    private final MainController controller;
    private final int pageIndex;
    private final HighlightAnnotation highlight;
    private final long timestamp;
    
    // For finding the correct annotation layer
    private boolean isExecuted = false;
    
    /**
     * Creates a new HighlightCommand.
     * 
     * @param controller the main controller
     * @param pageIndex the zero-based page index where highlight was added
     * @param highlight the highlight annotation to add
     */
    public HighlightCommand(MainController controller, int pageIndex, HighlightAnnotation highlight) {
        this.controller = controller;
        this.pageIndex = pageIndex;
        this.highlight = highlight;
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public void execute() throws IOException {
        logger.info("Executing HighlightCommand for page {}", pageIndex + 1);
        
        Platform.runLater(() -> {
            AnnotationLayer layer = findAnnotationLayer(pageIndex);
            if (layer != null) {
                // Add highlight to the layer
                addHighlightToLayer(layer, highlight);
                isExecuted = true;
                logger.debug("Added highlight to page {}", pageIndex + 1);
            } else {
                logger.error("Could not find annotation layer for page {}", pageIndex + 1);
            }
        });
    }
    
    @Override
    public void undo() throws IOException {
        logger.info("Undoing HighlightCommand for page {}", pageIndex + 1);
        
        Platform.runLater(() -> {
            AnnotationLayer layer = findAnnotationLayer(pageIndex);
            if (layer != null) {
                // Remove the highlight from the layer
                removeHighlightFromLayer(layer, highlight);
                logger.debug("Removed highlight from page {}", pageIndex + 1);
            } else {
                logger.error("Could not find annotation layer for page {}", pageIndex + 1);
            }
        });
    }
    
    @Override
    public String getDescription() {
        return "Highlight on Page " + (pageIndex + 1);
    }
    
    @Override
    public CommandType getType() {
        return CommandType.HIGHLIGHT;
    }
    
    @Override
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public boolean canUndo() {
        return isExecuted;
    }
    
    /**
     * Finds the AnnotationLayer for a specific page index.
     * 
     * @param pageIndex the zero-based page index
     * @return the AnnotationLayer, or null if not found
     */
    private AnnotationLayer findAnnotationLayer(int pageIndex) {
        VBox pagesContainer = controller.getPagesContainer();
        if (pagesContainer == null || pageIndex >= pagesContainer.getChildren().size()) {
            return null;
        }
        
        javafx.scene.Node pageNode = pagesContainer.getChildren().get(pageIndex);
        if (pageNode instanceof VBox pageBox) {
            for (javafx.scene.Node child : pageBox.getChildren()) {
                if (child instanceof StackPane stackPane) {
                    // Find AnnotationLayer in the StackPane
                    for (javafx.scene.Node stackChild : stackPane.getChildren()) {
                        if (stackChild instanceof AnnotationLayer) {
                            return (AnnotationLayer) stackChild;
                        }
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Adds a highlight to the annotation layer by recreating it.
     * Since AnnotationLayer doesn't expose addAnnotation(), we use reflection
     * or recreate the highlight by accessing the annotations list.
     */
    private void addHighlightToLayer(AnnotationLayer layer, HighlightAnnotation highlight) {
        try {
            // Access the private annotations field using reflection
            java.lang.reflect.Field annotationsField = AnnotationLayer.class.getDeclaredField("annotations");
            annotationsField.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            List<org.pdflite.model.Annotation> annotations = 
                (List<org.pdflite.model.Annotation>) annotationsField.get(layer);
            
            annotations.add(highlight);
            layer.redraw();
            
            logger.debug("Added highlight via reflection: {}x{} at ({}, {})", 
                highlight.getWidth(), highlight.getHeight(), highlight.getX(), highlight.getY());
                
        } catch (Exception e) {
            logger.error("Error adding highlight to layer", e);
        }
    }
    
    /**
     * Removes a highlight from the annotation layer.
     */
    private void removeHighlightFromLayer(AnnotationLayer layer, HighlightAnnotation highlight) {
        try {
            // Access the private annotations field using reflection
            java.lang.reflect.Field annotationsField = AnnotationLayer.class.getDeclaredField("annotations");
            annotationsField.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            List<org.pdflite.model.Annotation> annotations = 
                (List<org.pdflite.model.Annotation>) annotationsField.get(layer);
            
            // Remove highlight by matching coordinates and dimensions
            annotations.removeIf(ann -> 
                ann instanceof HighlightAnnotation h &&
                Math.abs(h.getX() - highlight.getX()) < 0.1 &&
                Math.abs(h.getY() - highlight.getY()) < 0.1 &&
                Math.abs(h.getWidth() - highlight.getWidth()) < 0.1 &&
                Math.abs(h.getHeight() - highlight.getHeight()) < 0.1
            );
            
            layer.redraw();
            
            logger.debug("Removed highlight via reflection");
                
        } catch (Exception e) {
            logger.error("Error removing highlight from layer", e);
        }
    }
}