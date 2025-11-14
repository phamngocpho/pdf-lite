package org.pdflite.view;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import org.pdflite.controller.ContextMenuHandler;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContextMenuPane extends StackPane {
    private static final Logger logger = LoggerFactory.getLogger(ContextMenuPane.class);
    
    private final ContextMenuHandler handler;
    private ContextMenu contextMenu;
    private PDFDocument currentDocument;
    private int currentPageIndex;
    private double currentZoom;

    // Selection tracking
    private boolean isSelecting = false;
    private double selectionStartX, selectionStartY;
    private Rectangle selectionRect;
    
    private static final Color SELECTION_FILL = Color.color(0.2, 0.5, 1.0, 0.2);
    private static final Color SELECTION_STROKE = Color.DODGERBLUE;
    private static final double MIN_SELECTION_SIZE = 5.0;
    
    public ContextMenuPane(ContextMenuHandler handler) {
        this.handler = handler;
        this.currentZoom = 1.0;
        
        setupSelectionVisual();
        setupContextMenu();
        setupEventHandlers();
        
        //Transparent but catches mouse events
        setPickOnBounds(true);
        setStyle("-fx-background-color: transparent;");
    }

    private void setupSelectionVisual() {
        selectionRect = new Rectangle();
        selectionRect.setFill(SELECTION_FILL);
        selectionRect.setStroke(SELECTION_STROKE);
        selectionRect.setStrokeWidth(2);
        selectionRect.setVisible(false);
        selectionRect.setMouseTransparent(true);
        selectionRect.setManaged(false);
        
        getChildren().add(selectionRect);
    }

    private void setupEventHandlers() {
        setOnMousePressed(this::handleMousePressed);
        setOnMouseDragged(this::handleMouseDragged);
        setOnMouseReleased(this::handleMouseReleased);
    }
    
    private void handleMousePressed(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            isSelecting = true;
            
            // Store local coordinates
            selectionStartX = event.getX();
            selectionStartY = event.getY();
            
            // Initialize rectangle at position
            selectionRect.setX(selectionStartX);
            selectionRect.setY(selectionStartY);
            selectionRect.setWidth(0);
            selectionRect.setHeight(0);
            selectionRect.setVisible(true);
            
            logger.debug("Selection START at LOCAL ({:.1f}, {:.1f})", 
                selectionStartX, selectionStartY);
            
            event.consume();
            return;
        }
        
        if (event.getButton() == MouseButton.SECONDARY) {
            if (selectionRect.isVisible() && 
                selectionRect.getWidth() >= MIN_SELECTION_SIZE &&
                selectionRect.getHeight() >= MIN_SELECTION_SIZE) {
                showContextMenuForSelection(event);
            }
            event.consume();
        }
    }
    
    private void handleMouseDragged(MouseEvent event) {
        if (isSelecting) {
            double currentX = event.getX();
            double currentY = event.getY();
            
            // Calculate bounding box
            double x = Math.min(selectionStartX, currentX);
            double y = Math.min(selectionStartY, currentY);
            double width = Math.abs(currentX - selectionStartX);
            double height = Math.abs(currentY - selectionStartY);
            
            selectionRect.setX(x);
            selectionRect.setY(y);
            selectionRect.setWidth(width);
            selectionRect.setHeight(height);
            
            //logger.trace("Selection DRAG: ({:.1f}, {:.1f}) + {:.1f}x{:.1f}", x, y, width, height);
            
            event.consume();
        }
    }
    
    private void handleMouseReleased(MouseEvent event) {
        if (isSelecting && event.getButton() == MouseButton.PRIMARY) {
            double endX = event.getX();
            double endY = event.getY();
            
            finalizeSelection(endX, endY);
            
            isSelecting = false;
            event.consume();
        }
    }
    
    private void finalizeSelection(double endX, double endY) {
        double width = selectionRect.getWidth();
        double height = selectionRect.getHeight();
        
        if (width < MIN_SELECTION_SIZE || height < MIN_SELECTION_SIZE) {
            logger.debug("Selection too small ({:.1f}x{:.1f}), ignored", width, height);
            selectionRect.setVisible(false);
            return;
        }
        
        double x = selectionRect.getX();
        double y = selectionRect.getY();
        
        //logger.info("Selection FINALIZED: ({:.1f}, {:.1f}) + {:.1f}x{:.1f} px", x, y, width, height);
        
        // Pass coordinates to handler
        handler.analyzeSelection(
            currentDocument,
            currentPageIndex,
            x, y,
            x + width, y + height,
            currentZoom
        );
    }
    
    private void showContextMenuForSelection(MouseEvent event) {
        updateContextMenuItems();
        contextMenu.show(this, event.getScreenX(), event.getScreenY());
        
        logger.info("Context menu shown");
    }
    
    private void setupContextMenu() {
        contextMenu = new ContextMenu();
        
        MenuItem copyText = new MenuItem("Copy Text");
        MenuItem copyImage = new MenuItem("Copy Image");
        MenuItem separator = new MenuItem("──────────");
        MenuItem clearSelection = new MenuItem("Clear Selection");
        
        separator.setDisable(true);
        
        copyText.setOnAction(e -> {
            handler.handleCopyText();
            clearSelection();
        });
        
        copyImage.setOnAction(e -> {
            logger.info("Copy Image - TODO");
        });
        
        clearSelection.setOnAction(e -> clearSelection());
        
        contextMenu.getItems().addAll(copyText, copyImage, separator, clearSelection);
    }
    
    private void updateContextMenuItems() {
        boolean hasText = handler.hasTextAtPosition();
        contextMenu.getItems().get(0).setDisable(!hasText);
        contextMenu.getItems().get(1).setDisable(true);
    }
    
    public void setDocumentInfo(PDFDocument document, int pageIndex, double zoom) {
        this.currentDocument = document;
        this.currentPageIndex = pageIndex;
        this.currentZoom = zoom;
        
        /**
        logger.debug("Document info: Page {}/{}, Zoom {:.0f}%",
            pageIndex + 1, 
            document != null ? document.getTotalPages() : 0,
            zoom * 100);
        */
    }
    
    public void clearSelection() {
        selectionRect.setVisible(false);
        selectionRect.setWidth(0);
        selectionRect.setHeight(0);
    }
}