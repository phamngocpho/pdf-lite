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
    setOnContextMenuRequested(event -> {
        double x = event.getX();
        double y = event.getY();
        
        //logger.debug("Context menu requested at ({:.1f}, {:.1f})", x, y);
        
        // Check for image at cursor
        handler.analyzeCursorForImage(
            currentDocument,
            currentPageIndex,
            x, y,
            currentZoom
        );
        
        // Check for text selection
        boolean hasSelection = selectionRect.isVisible() && 
                              selectionRect.getWidth() >= MIN_SELECTION_SIZE &&
                              selectionRect.getHeight() >= MIN_SELECTION_SIZE;
        
        // Show menu if has text OR image
        if (hasSelection || handler.hasImageAtPosition()) {
            updateContextMenuItems();
            contextMenu.show(this, event.getScreenX(), event.getScreenY());
            //logger.info("Context menu shown (hasText={}, hasImage={})",hasSelection, handler.hasImageAtPosition());
        } else {
            logger.warn("Context menu blocked: no text or image");
        }
        
        event.consume();
    });
}
    
    private void handleMousePressed(MouseEvent event) {
    // LEFT CLICK: Start selection
    if (event.getButton() == MouseButton.PRIMARY) {
        isSelecting = true;
        
        selectionStartX = event.getX();
        selectionStartY = event.getY();
        
        selectionRect.setX(selectionStartX);
        selectionRect.setY(selectionStartY);
        selectionRect.setWidth(0);
        selectionRect.setHeight(0);
        selectionRect.setVisible(true);
        
        //logger.debug("Selection START at ({:.1f}, {:.1f})",selectionStartX, selectionStartY);
        
        event.consume();
        return;
    }
    
    // RIGHT CLICK: Fallback (if setOnContextMenuRequested doesn't work)
    if (event.getButton() == MouseButton.SECONDARY) {
        //logger.debug("RIGHT CLICK detected at ({:.1f}, {:.1f})",event.getX(), event.getY());
        // Let setOnContextMenuRequested handle it
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
            //logger.debug("Selection too small ({:.1f}x{:.1f}), ignored", width, height);
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
        
        //logger.info("Context menu shown");
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
            handler.handleCopyImage();
            logger.info("Image copied");
        });

        clearSelection.setOnAction(e -> clearSelection());

        contextMenu.getItems().addAll(copyText, copyImage, clearSelection);
    }
    
    private void updateContextMenuItems() {
        boolean hasText = handler.hasTextAtPosition();
        boolean hasImage = handler.hasImageAtPosition();

        contextMenu.getItems().get(0).setDisable(!hasText);  // Copy Text
        contextMenu.getItems().get(1).setDisable(!hasImage); // Copy Image

        logger.debug("Context menu: text={}, image={}", hasText, hasImage);
    }
    
    public void setDocumentInfo(PDFDocument document, int pageIndex, double zoom) {
        boolean pageChanged = (this.currentPageIndex != pageIndex);

        this.currentDocument = document;
        this.currentPageIndex = pageIndex;
        this.currentZoom = zoom;

        // Clear image cache when page changes
        if (pageChanged) {
            handler.clearImageCache();
        }
    }
    
    public void clearSelection() {
        selectionRect.setVisible(false);
        selectionRect.setWidth(0);
        selectionRect.setHeight(0);
    }
}