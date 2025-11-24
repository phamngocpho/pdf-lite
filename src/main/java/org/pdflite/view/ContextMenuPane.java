package org.pdflite.view;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.Group;
import org.pdflite.controller.ContextMenuHandler;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Rectangle2D;
import java.util.List;

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
    private Group highlightGroup; // Group containing highlight rectangles for each line
    private int clickCount = 0;
    private javafx.animation.Timeline clickTimer;

    private static final Color HIGHLIGHT_FILL = Color.color(0.0, 0.47, 0.84, 0.3); // Similar to SmartPDFViewer
    private static final double MIN_SELECTION_SIZE = 5.0;
    private static final double HIGHLIGHT_HEIGHT_MULTIPLIER = 1.3;

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
        // Group for highlight regions (one per line)
        highlightGroup = new Group();
        highlightGroup.setMouseTransparent(true);
        highlightGroup.setManaged(false);

        getChildren().add(highlightGroup);
    }

    private void setupEventHandlers() {
        setOnMousePressed(this::handleMousePressed);
        setOnMouseDragged(this::handleMouseDragged);
        setOnMouseReleased(this::handleMouseReleased);
        setOnMouseClicked(this::handleMouseClicked);
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

            // Check for text selection (has highlight regions)
            boolean hasSelection = !highlightGroup.getChildren().isEmpty() && highlightGroup.isVisible();

            // Show the menu if it has text OR image
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

            // Clear previous highlights
            clearHighlightRegions();

            selectionStartX = event.getX();
            selectionStartY = event.getY();

            //logger.debug("Selection START at ({:.1f}, {:.1f})",selectionStartX, selectionStartY);

            event.consume();
            return;
        }

        // RIGHT-CLICK: Fallback (if setOnContextMenuRequested doesn't work)
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

            // Update highlight regions directly (no rectangle box)
            if (width >= MIN_SELECTION_SIZE && height >= MIN_SELECTION_SIZE) {
                updateHighlightRegions(x, y, x + width, y + height);
            } else {
                clearHighlightRegions();
            }

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

    private void handleMouseClicked(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            clickCount++;

            if (clickTimer != null) {
                clickTimer.stop();
            }

            clickTimer = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(
                            javafx.util.Duration.millis(300),
                            e -> {
                                handleClicks(event);
                                clickCount = 0;
                            }
                    )
            );
            clickTimer.setCycleCount(1);
            clickTimer.play();
        }
    }

    private void handleClicks(MouseEvent event) {
        if (clickCount == 2) {
            // Double-click: select a word
            String selectedText = handler.selectTextAtPoint(
                    currentDocument,
                    currentPageIndex,
                    event.getX(),
                    event.getY(),
                    currentZoom,
                    "word"
            );
            if (!selectedText.isEmpty()) {
                logger.info("Selected word: {}", selectedText);
            }
        } else if (clickCount >= 3) {
            // Triple-click: select line
            String selectedText = handler.selectTextAtPoint(
                    currentDocument,
                    currentPageIndex,
                    event.getX(),
                    event.getY(),
                    currentZoom,
                    "line"
            );
            if (!selectedText.isEmpty()) {
                logger.info("Selected line: {}", selectedText);
            }
        }
    }

    private void finalizeSelection(double endX, double endY) {
        // Calculate the final bounding box
        double x = Math.min(selectionStartX, endX);
        double y = Math.min(selectionStartY, endY);
        double width = Math.abs(endX - selectionStartX);
        double height = Math.abs(endY - selectionStartY);

        if (width < MIN_SELECTION_SIZE || height < MIN_SELECTION_SIZE) {
            //logger.debug("Selection too small ({:.1f}x{:.1f}), ignored", width, height);
            clearSelection();
            return;
        }

        // Update highlight regions (already updated during drag, but ensure it's final)
        updateHighlightRegions(x, y, x + width, y + height);

        //logger.info("Selection FINALIZED: ({:.1f}, {:.1f}) + {:.1f}x{:.1f} px", x, y, width, height);

        // Pass coordinates to the handler
        handler.analyzeSelection(
                currentDocument,
                currentPageIndex,
                x, y,
                x + width, y + height,
                currentZoom
        );
    }

    private void updateHighlightRegions(double x1, double y1, double x2, double y2) {
        // Clear existing highlights
        clearHighlightRegions();

        // Get highlight regions from handler
        List<Rectangle2D> regions = handler.getHighlightRegions(
                currentDocument,
                currentPageIndex,
                x1, y1, x2, y2,
                currentZoom
        );

        // Create rectangles for each region
        for (Rectangle2D region : regions) {
            // Increase height for better visibility
            double increasedHeight = region.getHeight() * HIGHLIGHT_HEIGHT_MULTIPLIER;
            double heightDiff = increasedHeight - region.getHeight();
            
            Rectangle highlightRect = new Rectangle(
                    region.getX(),
                    region.getY() - heightDiff / 2, // Center the increased height
                    region.getWidth(),
                    increasedHeight
            );
            highlightRect.setFill(HIGHLIGHT_FILL);
            highlightRect.setMouseTransparent(true);
            highlightRect.setManaged(false);
            highlightGroup.getChildren().add(highlightRect);
        }

        highlightGroup.setVisible(true);
    }

    private void clearHighlightRegions() {
        highlightGroup.getChildren().clear();
        highlightGroup.setVisible(false);
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

        // Clear image and text selector cache when the page changes
        if (pageChanged) {
            handler.clearImageCache();
            handler.clearTextSelector();
        }
    }

    public void clearSelection() {
        clearHighlightRegions();
    }
}