package org.pdflite.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.pdflite.util.Constants;
import org.pdflite.view.AnnotationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main Controller for PDF Lite Application
 */
public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML private BorderPane rootPane;
    @FXML private ScrollPane scrollPane;
    @FXML private StackPane contentPane;
    @FXML private Label statusLabel;
    @FXML private Label totalPagesLabel;
    @FXML private TextField pageNumberField;
    @FXML private ComboBox<String> zoomComboBox;
    @FXML private Button prevButton;
    @FXML private Button nextButton;

    private PDFService pdfService;
    private PDFDocument currentDocument;
    private double currentZoom = Constants.DEFAULT_ZOOM;
    private VBox pagesContainer;
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(2); // 2 threads for parallel rendering
    /**
     * Debounce delay in milliseconds for scroll event handling.
     * <p>
     * This delay prevents excessive page loading operations during continuous scrolling.
     * After the user stops scrolling, the system waits this duration before triggering
     * the page loading logic, which improves performance and reduces unnecessary rendering.
     * </p>
     */
    private static final long SCROLL_DEBOUNCE_MS = 200; // Wait 200ms after scroll stops; // Wait 200ms after scroll stops
    private boolean highlightModeActive = false;
    private java.util.Timer scrollTimer;
    private final java.util.Set<Integer> loadingPages = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @FXML
    public void initialize() {
        logger.info("Initializing MainController");
        pdfService = new PDFService();

        // Setup zoom combo box if present
        if (zoomComboBox != null) {
            zoomComboBox.getItems().addAll("50%", "75%", "100%", "125%", "150%", "200%");
            zoomComboBox.setValue("100%");
            zoomComboBox.setOnAction(e -> handleZoomComboBoxChange());
        }

        // Setup page navigation
        if (pageNumberField != null) {
            pageNumberField.setOnAction(e -> jumpToPage());
        }

        // Setup scroll listener for continuous scrolling
        if (scrollPane != null) {
            scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
                if (currentDocument != null && pagesContainer != null) {
                    // Cancel previous timer
                    if (scrollTimer != null) {
                        scrollTimer.cancel();
                    }

                    // Start new timer to load pages after scroll stops
                    scrollTimer = new java.util.Timer();
                    scrollTimer.schedule(new java.util.TimerTask() {
                        @Override
                        public void run() {
                            loadVisiblePages();
                        }
                    }, SCROLL_DEBOUNCE_MS);

                    // Immediately update current page indicator
                    updateCurrentPageFromScroll();
                }
            });
        }

        updateUIState(false);
    }

    @FXML
    private void handleOpenPDF() {
        handleOpenFile();
    }

    @FXML
    private void handleOpenFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open PDF File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        
        Stage stage = (Stage) rootPane.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);
        
        if (file != null) {
            openPDFFile(file);
        }
    }

    @FXML
    private void handleFitToWidth() {
        if (currentDocument != null && scrollPane != null) {
            try {
                Image image = pdfService.renderPage(currentDocument, currentDocument.getCurrentPage(), 1.0f);
                double viewportWidth = scrollPane.getViewportBounds().getWidth() - 20;
                double imageWidth = image.getWidth();
                currentZoom = Math.min(1.0, viewportWidth / imageWidth);
                applyZoom("Fit to Width");
            } catch (IOException e) {
                logger.error("Error fitting to width", e);
            }
        }
    }

    @FXML
    private void handleFitToPage() {
        if (currentDocument != null && scrollPane != null) {
            try {
                Image image = pdfService.renderPage(currentDocument, currentDocument.getCurrentPage(), 1.0f);
                currentZoom = calculateFitToPageZoom(image.getWidth(), image.getHeight());
                applyZoom("Fit to Page");
            } catch (IOException e) {
                logger.error("Error fitting to page", e);
            }
        }
    }

    /**
     * Calculate zoom level to fit image to viewport while maintaining aspect ratio
     * @param imageWidth Width of the image
     * @param imageHeight Height of the image
     * @return Calculated zoom level (maximum 1.0)
     */
    private double calculateFitToPageZoom(double imageWidth, double imageHeight) {
        if (scrollPane == null) {
            return Constants.DEFAULT_ZOOM;
        }

        double viewportWidth = scrollPane.getViewportBounds().getWidth() - 20; // padding
        double viewportHeight = scrollPane.getViewportBounds().getHeight() - 20; // padding

        double zoomWidth = viewportWidth / imageWidth;
        double zoomHeight = viewportHeight / imageHeight;

        return Math.min(1.0, Math.min(zoomWidth, zoomHeight));
    }

    @FXML
    private void handleHighlight() {
        highlightModeActive = !highlightModeActive;

        if (highlightModeActive) {
            updateStatusLabel("Highlight mode: Active - Click and drag to highlight");
            enableHighlightMode();
        } else {
            updateStatusLabel("Highlight mode: Disabled");
            disableHighlightMode();
        }
    }

    private void enableHighlightMode() {
        setAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.HIGHLIGHT);
    }

    private void disableHighlightMode() {
        setAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.NONE);
    }

    private void setAnnotationModeForAllPages(AnnotationLayer.AnnotationMode mode) {
        if (pagesContainer != null) {
            pagesContainer.getChildren().forEach(node -> {
                if (node instanceof VBox pageBox) {
                    pageBox.getChildren().forEach(child -> {
                        if (child instanceof StackPane stackPane) {
                            stackPane.getChildren().forEach(stackChild -> {
                                if (stackChild instanceof AnnotationLayer annotationLayer) {
                                    annotationLayer.setAnnotationMode(mode);
                                }
                            });
                        }
                    });
                }
            });
        }
    }

    @FXML
    private void handleGoToPage() {
        jumpToPage();
    }

    @FXML
    private void handleZoomChange() {
        handleZoomComboBoxChange();
    }

    private void handleZoomComboBoxChange() {
        if (zoomComboBox != null && currentDocument != null) {
            String value = zoomComboBox.getValue();
            if (value != null) {
                try {
                    // Remove % and parse
                    currentZoom = Double.parseDouble(value.replace("%", "")) / 100.0;
                    applyZoom(null);
                } catch (NumberFormatException e) {
                    logger.error("Invalid zoom value: {}", value);
                }
            }
        }
    }

    @FXML
    private void handleExit() {
        if (currentDocument != null) {
            pdfService.closePDF(currentDocument);
        }
        Platform.exit();
    }

    @FXML
    private void handleZoomIn() {
        currentZoom = Math.min(Constants.MAX_ZOOM, currentZoom + Constants.ZOOM_STEP);
        if (currentDocument != null) {
            applyZoom(null);
        }
    }

    @FXML
    private void handleZoomOut() {
        currentZoom = Math.max(Constants.MIN_ZOOM, currentZoom - Constants.ZOOM_STEP);
        if (currentDocument != null) {
            applyZoom(null);
        }
    }

    /**
     * Apply the current zoom level and update UI components
     * @param prefix Optional prefix for the status message (e.g., "Fit to Width")
     */
    private void applyZoom(String prefix) {
        if (currentDocument != null) {
            currentDocument.setZoomLevel(currentZoom);

            // Update zoom combo box
            if (zoomComboBox != null) {
                zoomComboBox.setValue(String.format("%.0f%%", currentZoom * 100));
            }

            // Clear loading pages set and re-render all pages with new zoom
            loadingPages.clear();
            renderCurrentPage();

            String statusMessage = prefix != null
                    ? String.format("%s - Zoom: %.0f%%", prefix, currentZoom * 100)
                    : String.format("Zoom: %.0f%%", currentZoom * 100);
            updateStatusLabel(statusMessage);
        }
    }

    @FXML
    private void handlePreviousPage() {
        if (currentDocument != null && currentDocument.getCurrentPage() > 0) {
            navigateToPage(currentDocument.getCurrentPage() - 1);
        }
    }

    @FXML
    private void handleNextPage() {
        if (currentDocument != null && 
            currentDocument.getCurrentPage() < currentDocument.getTotalPages() - 1) {
            navigateToPage(currentDocument.getCurrentPage() + 1);
        }
    }

    private void navigateToPage(int pageIndex) {
        if (currentDocument != null) {
            currentDocument.setCurrentPage(pageIndex);
            scrollToCurrentPage();
            updatePageInfo();
        }
    }

    @FXML
    private void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About PDF Lite");
        alert.setHeaderText("PDF Lite - PDF Viewer & Editor");
        alert.setContentText("""
                Version 1.0
                
                A lightweight PDF viewer with annotation features.
                
                Built with JavaFX and Apache PDFBox""");
        alert.showAndWait();
    }

    private void openPDFFile(File file) {
        try {
            // Close current document if any
            if (currentDocument != null) {
                pdfService.closePDF(currentDocument);
            }

            // Open new document
            currentDocument = pdfService.openPDF(file);

            // Calculate initial zoom to fit both width and height
            if (scrollPane != null && scrollPane.getViewportBounds().getWidth() > 0
                && scrollPane.getViewportBounds().getHeight() > 0) {
                // Get first page to calculate proper zoom
                Image firstPage = pdfService.renderPage(currentDocument, 0, 1.0f);
                currentZoom = calculateFitToPageZoom(firstPage.getWidth(), firstPage.getHeight());
            } else {
                currentZoom = 0.7; // Default to 70% if it can't calculate
            }

            currentDocument.setZoomLevel(currentZoom);
            
            // Update UI
            updateUIState(true);
            renderCurrentPage();
            updatePageInfo();
            updateStatusLabel("Opened: " + file.getName());
            
            logger.info("Successfully opened PDF: {}", file.getName());
        } catch (IOException e) {
            logger.error("Error opening PDF file", e);
            showError("Error Opening PDF", "Could not open the PDF file: " + e.getMessage());
        }
    }

    private void renderCurrentPage() {
        if (currentDocument == null) return;

        try {
            if (contentPane != null) {
                contentPane.getChildren().clear();

                // Create container for all pages
                pagesContainer = new VBox(10);
                pagesContainer.setAlignment(Pos.TOP_CENTER);
                pagesContainer.setStyle("-fx-background-color: #808080; -fx-padding: 10;");

                // Create placeholders for all pages
                int totalPages = currentDocument.getTotalPages();

                // Render first page to get dimensions
                Image firstPage = pdfService.renderPage(currentDocument, 0, (float) currentZoom);
                double pageWidth = firstPage.getWidth();
                double pageHeight = firstPage.getHeight();

                logger.info("Creating continuous scroll view for {} pages", totalPages);

                for (int i = 0; i < totalPages; i++) {
                    VBox pageBox = createPagePlaceholder(i, pageWidth, pageHeight);
                    pagesContainer.getChildren().add(pageBox);
                }

                contentPane.getChildren().add(pagesContainer);

                // Load first few visible pages immediately
                Platform.runLater(this::loadVisiblePages);
            }

        } catch (IOException e) {
            logger.error("Error rendering page", e);
            showError("Rendering Error", "Could not render the page: " + e.getMessage());
        }
    }

    private VBox createPagePlaceholder(int pageIndex, double width, double height) {
        VBox pageBox = new VBox(5);
        pageBox.setAlignment(Pos.TOP_CENTER);
        pageBox.setId("page-" + pageIndex);
        pageBox.setPrefSize(width, height + 20);
        pageBox.setStyle("-fx-background-color: #606060; -fx-border-color: #404040;");

        Label pageNumberLabel = new Label("Page " + (pageIndex + 1));
        pageNumberLabel.setStyle("-fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 5;");

        StackPane placeholder = createLoadingPlaceholder(width, height);

        pageBox.getChildren().addAll(placeholder, pageNumberLabel);
        return pageBox;
    }

    private StackPane createLoadingPlaceholder(double width, double height) {
        Label loadingLabel = new Label("Loading...");
        loadingLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 14px;");

        StackPane placeholder = new StackPane(loadingLabel);
        placeholder.setPrefSize(width, height);
        placeholder.setStyle("-fx-background-color: #505050;");

        return placeholder;
    }

    private void loadVisiblePages() {
        if (currentDocument == null || pagesContainer == null || scrollPane == null) return;

        Platform.runLater(() -> {
            try {
                double bufferZone = scrollPane.getViewportBounds().getHeight();
                double scrollValue = scrollPane.getVvalue();
                double contentHeight = pagesContainer.getHeight();

                if (contentHeight <= bufferZone) {
                    // All content is visible, load all pages
                    int totalPages = currentDocument.getTotalPages();
                    for (int i = 0; i < totalPages; i++) {
                        if (!loadingPages.contains(i)) {
                            VBox pageBox = (VBox) pagesContainer.getChildren().get(i);
                            if (pageBox.getChildren().getFirst() instanceof StackPane placeholder) {
                                if (placeholder.getChildren().getFirst() instanceof Label) {
                                    loadPage(i, pageBox);
                                }
                            }
                        }
                    }
                    return;
                }

                // Calculate visible range
                double visibleStart = scrollValue * (contentHeight - bufferZone);
                double visibleEnd = visibleStart + bufferZone;

                // Add buffer zone (1 viewport above and below)
                double loadStart = Math.max(0, visibleStart - bufferZone);
                double loadEnd = Math.min(contentHeight, visibleEnd + bufferZone);

                int totalPages = currentDocument.getTotalPages();
                double currentY = 0;

                for (int i = 0; i < totalPages; i++) {
                    VBox pageBox = (VBox) pagesContainer.getChildren().get(i);
                    double pageHeight = pageBox.getPrefHeight();
                    double pageStart = currentY;
                    double pageEnd = currentY + pageHeight;

                    // Check if page is in load range
                    if (pageEnd >= loadStart && pageStart <= loadEnd) {
                        // Load page if not already loading or loaded
                        if (!loadingPages.contains(i)) {
                            if (pageBox.getChildren().getFirst() instanceof StackPane placeholder) {
                                if (placeholder.getChildren().getFirst() instanceof Label) {
                                    loadPage(i, pageBox);
                                }
                            }
                        }
                    }

                    currentY = pageEnd + 10; // Add spacing
                }

            } catch (Exception e) {
                logger.error("Error loading visible pages", e);
            }
        });
    }


    private void loadPage(int pageIndex, VBox pageBox) {
        // Mark as loading to prevent duplicate requests
        loadingPages.add(pageIndex);

        // Render in background thread to avoid blocking UI
        renderExecutor.submit(() -> {
            try {
                Image image = pdfService.renderPage(
                    currentDocument,
                    pageIndex,
                    (float) currentZoom
                );

                // Update UI on JavaFX thread
                Platform.runLater(() -> {
                    ImageView imageView = new ImageView(image);
                    imageView.setPreserveRatio(true);
                    imageView.setSmooth(true);
                    imageView.setCache(true);

                    // Create annotation layer on top of the image
                    AnnotationLayer annotationLayer = new AnnotationLayer(image.getWidth(), image.getHeight());
                    if (highlightModeActive) {
                        annotationLayer.setAnnotationMode(AnnotationLayer.AnnotationMode.HIGHLIGHT);
                    }

                    // Stack the image and annotation layer
                    StackPane imageStack = new StackPane(imageView, annotationLayer);
                    imageStack.setAlignment(Pos.CENTER);

                    // Replace placeholder with actual image
                    if (!pageBox.getChildren().isEmpty()) {
                        pageBox.getChildren().set(0, imageStack);
                    }

                    loadingPages.remove(pageIndex);
                    logger.debug("Loaded page {}", pageIndex + 1);
                });

            } catch (IOException e) {
                logger.error("Error loading page {}", pageIndex + 1, e);
                loadingPages.remove(pageIndex);
                // Keep placeholder with error message
                Platform.runLater(() -> {
                    if (!pageBox.getChildren().isEmpty() &&
                        pageBox.getChildren().getFirst() instanceof StackPane) {
                        Label errorLabel = new Label("Error loading page");
                        errorLabel.setStyle("-fx-text-fill: red;");
                        ((StackPane) pageBox.getChildren().getFirst()).getChildren().set(0, errorLabel);
                    }
                });
            }
        });
    }


    private void updateCurrentPageFromScroll() {
        if (currentDocument == null || pagesContainer == null || scrollPane == null) return;

        try {
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double scrollValue = scrollPane.getVvalue();
            double contentHeight = pagesContainer.getHeight();

            if (contentHeight <= viewportHeight) {
                return; // All content visible, stay on current page
            }

            double visibleStart = scrollValue * (contentHeight - viewportHeight);
            double visibleCenter = visibleStart + (viewportHeight / 2);

            int totalPages = currentDocument.getTotalPages();
            double currentY = 0;

            for (int i = 0; i < totalPages; i++) {
                VBox pageBox = (VBox) pagesContainer.getChildren().get(i);
                double pageHeight = pageBox.getPrefHeight();
                double pageEnd = currentY + pageHeight;

                if (visibleCenter >= currentY && visibleCenter < pageEnd) {
                    currentDocument.setCurrentPage(i);
                    updatePageInfo();
                    break;
                }

                currentY = pageEnd + 10; // Add spacing
            }
        } catch (Exception e) {
            logger.error("Error updating current page from scroll", e);
        }
    }

    private void scrollToCurrentPage() {
        if (currentDocument == null || pagesContainer == null || scrollPane == null) return;

        Platform.runLater(() -> {
            try {
                int targetPage = currentDocument.getCurrentPage();
                double currentY = 0;

                for (int i = 0; i < targetPage; i++) {
                    VBox pageBox = (VBox) pagesContainer.getChildren().get(i);
                    currentY += pageBox.getPrefHeight() + 10; // Add spacing
                }

                double contentHeight = pagesContainer.getHeight();
                double viewportHeight = scrollPane.getViewportBounds().getHeight();

                if (contentHeight > viewportHeight) {
                    double scrollPosition = currentY / (contentHeight - viewportHeight);
                    scrollPane.setVvalue(Math.min(1.0, Math.max(0.0, scrollPosition)));
                }
            } catch (Exception e) {
                logger.error("Error scrolling to page", e);
            }
        });
    }

    private void jumpToPage() {
        if (currentDocument == null || pageNumberField == null) return;

        try {
            int pageNum = Integer.parseInt(pageNumberField.getText()) - 1;
            if (pageNum >= 0 && pageNum < currentDocument.getTotalPages()) {
                navigateToPage(pageNum);
            } else {
                showError("Invalid Page", "Page number must be between 1 and " + 
                         currentDocument.getTotalPages());
                resetPageFieldToCurrentPage();
            }
        } catch (NumberFormatException e) {
            showError("Invalid Input", "Please enter a valid page number");
            resetPageFieldToCurrentPage();
        }
    }

    private void resetPageFieldToCurrentPage() {
        if (currentDocument != null && pageNumberField != null) {
            pageNumberField.setText(String.valueOf(currentDocument.getCurrentPage() + 1));
        }
    }

    private void updatePageInfo() {
        if (currentDocument != null) {
            int current = currentDocument.getCurrentPage() + 1;
            int total = currentDocument.getTotalPages();

            if (totalPagesLabel != null) {
                totalPagesLabel.setText("/ " + total);
            }

            if (pageNumberField != null) {
                pageNumberField.setText(String.valueOf(current));
            }

            // Update navigation buttons
            if (prevButton != null) {
                prevButton.setDisable(current == 1);
            }
            if (nextButton != null) {
                nextButton.setDisable(current == total);
            }
        }
    }

    private void updateUIState(boolean hasDocument) {
        // Enable/disable menu items and toolbar buttons based on whether a document is open
        if (prevButton != null) prevButton.setDisable(!hasDocument);
        if (nextButton != null) nextButton.setDisable(!hasDocument);
        if (pageNumberField != null) pageNumberField.setDisable(!hasDocument);
        if (zoomComboBox != null) zoomComboBox.setDisable(!hasDocument);
    }

    private void updateStatusLabel(String message) {
        statusLabel.setText(message);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
