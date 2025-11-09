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
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.pdflite.util.Constants;
import org.pdflite.view.AnnotationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.pdflite.PDFLiteApplication;
import org.pdflite.model.SearchResult;
import org.pdflite.view.SearchPanel;

/**
 * Main Controller for PDF Lite Application
 */
public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML
    private BorderPane rootPane;
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private StackPane contentPane;
    @FXML
    private Label statusLabel;
    @FXML
    private Label totalPagesLabel;
    @FXML
    private TextField pageNumberField;
    @FXML
    private ComboBox<String> zoomComboBox;
    @FXML
    private Button prevButton;
    @FXML
    private Button nextButton;

    private SearchDialogController searchDialogController;
    private Stage searchDialogStage;

    public enum SearchPanelPosition {
        LEFT,
        RIGHT,
        FLOAT
    }

    private SearchPanel searchPanel;
    private boolean searchPanelVisible = false;
    private SearchPanelPosition searchPanelPosition = SearchPanelPosition.FLOAT;

    // Search results by page
    private final Map<Integer, List<SearchResult>> searchResultsByPage = new HashMap<>();
    private SearchResult currentActiveResult = null;

    private PDFService pdfService;
    private PDFDocument currentDocument;
    private double currentZoom = Constants.DEFAULT_ZOOM;
    private VBox pagesContainer;
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(2); // 2 threads for parallel rendering
    /**
     * Debounce delay in milliseconds for scroll event handling.
     * <p>
     * This delay prevents excessive page loading operations during continuous
     * scrolling. After the user stops scrolling, the system waits this duration
     * before triggering the page loading logic, which improves performance and
     * reduces unnecessary rendering.
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

        searchPanel = new SearchPanel();
        searchPanel.setMainController(this);
        searchPanel.setVisible(false);
        searchPanel.setManaged(false);
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
     * Calculate zoom level to fit image to viewport while maintaining aspect
     * ratio
     *
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
     *
     * @param prefix Optional prefix for the status message (e.g., "Fit to
     * Width")
     */
    // ✅ Update applyZoom method
    private void applyZoom(String prefix) {
        if (currentDocument != null) {
            currentDocument.setZoomLevel(currentZoom);

            if (zoomComboBox != null) {
                zoomComboBox.setValue(String.format("%.0f%%", currentZoom * 100));
            }

            loadingPages.clear();
            renderCurrentPage();

            // ✅ Update all annotation layers after render
            Platform.runLater(() -> {
                updateAnnotationLayersScale();
                if (!searchResultsByPage.isEmpty()) {
                    updateSearchHighlightsOnAllPages();
                }
            });

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
        if (currentDocument != null
                && currentDocument.getCurrentPage() < currentDocument.getTotalPages() - 1) {
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

    @FXML
    private void handleSearch() {
        if (currentDocument == null) {
            showError("No PDF Loaded", "Please open a PDF file first");
            return;
        }

        try {
            if (searchDialogStage == null) {
                // Load search dialog FXML
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/org/pdflite/search-dialog.fxml")
                );

                Parent root = loader.load();

                // Get controller
                searchDialogController = loader.getController();
                searchDialogController.setPDFDocument(currentDocument);
                searchDialogController.setMainController(this);

                // Create stage
                searchDialogStage = new Stage();
                searchDialogStage.setTitle("Search in PDF");
                searchDialogStage.setScene(new Scene(root));
                searchDialogStage.initOwner(rootPane.getScene().getWindow());

                // Handle close event
                searchDialogStage.setOnCloseRequest(e -> {
                    searchDialogController.cleanup();
                });
            } else {
                // Update document if changed
                searchDialogController.setPDFDocument(currentDocument);
            }

            searchDialogStage.show();
            searchDialogStage.toFront();

            logger.info("Search dialog opened");

        } catch (IOException e) {
            logger.error("Error loading search dialog", e);
            showError("Error", "Could not open search dialog: " + e.getMessage());
        }
    }

    @FXML
    private void handleSearchLeft() {
        toggleSearchPanel(SearchPanelPosition.LEFT);
    }

    /**
     * Handle search in right panel
     */
    @FXML
    private void handleSearchRight() {
        toggleSearchPanel(SearchPanelPosition.RIGHT);
    }

    /**
     * Handle hide search panel
     */
    @FXML
    private void handleHideSearch() {
        hideSearchPanel();
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
        if (currentDocument == null) {
            return;
        }

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
        if (currentDocument == null || pagesContainer == null || scrollPane == null) {
            return;
        }

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
                    if (!pageBox.getChildren().isEmpty()
                            && pageBox.getChildren().getFirst() instanceof StackPane) {
                        Label errorLabel = new Label("Error loading page");
                        errorLabel.setStyle("-fx-text-fill: red;");
                        ((StackPane) pageBox.getChildren().getFirst()).getChildren().set(0, errorLabel);
                    }
                });
            }
        });
    }

    private void updateCurrentPageFromScroll() {
        if (currentDocument == null || pagesContainer == null || scrollPane == null) {
            return;
        }

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
        if (currentDocument == null || pagesContainer == null || scrollPane == null) {
            return;
        }

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
        if (currentDocument == null || pageNumberField == null) {
            return;
        }

        try {
            int pageNum = Integer.parseInt(pageNumberField.getText()) - 1;
            if (pageNum >= 0 && pageNum < currentDocument.getTotalPages()) {
                navigateToPage(pageNum);
            } else {
                showError("Invalid Page", "Page number must be between 1 and "
                        + currentDocument.getTotalPages());
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
        if (prevButton != null) {
            prevButton.setDisable(!hasDocument);
        }
        if (nextButton != null) {
            nextButton.setDisable(!hasDocument);
        }
        if (pageNumberField != null) {
            pageNumberField.setDisable(!hasDocument);
        }
        if (zoomComboBox != null) {
            zoomComboBox.setDisable(!hasDocument);
        }
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

    public void jumpToPage(int pageNumber) {
        if (currentDocument == null) {
            return;
        }

        int pageIndex = pageNumber - 1;
        if (pageIndex < 0 || pageIndex >= currentDocument.getTotalPages()) {
            logger.warn("Invalid page number: {}", pageNumber);
            return;
        }

        // Update current page
        currentDocument.setCurrentPage(pageIndex);

        // Scroll to page
        Platform.runLater(() -> {
            scrollToPage(pageIndex);
            updatePageInfo();
        });

        logger.info("Jumped to page {}", pageNumber);
    }

    private void scrollToPage(int pageIndex) {
        if (pagesContainer == null || scrollPane == null) {
            logger.warn("Cannot scroll - container or scrollpane is null");
            return;
        }

        Platform.runLater(() -> {
            try {
                if (pageIndex < 0 || pageIndex >= pagesContainer.getChildren().size()) {
                    logger.warn("Invalid page index for scrolling: {}", pageIndex);
                    return;
                }

                // Force layout update first
                pagesContainer.layout();

                VBox targetPageBox = (VBox) pagesContainer.getChildren().get(pageIndex);

                // Use bounds in parent for accurate positioning
                double targetY = targetPageBox.getBoundsInParent().getMinY();

                double contentHeight = pagesContainer.getHeight();
                double viewportHeight = scrollPane.getViewportBounds().getHeight();

                if (contentHeight > viewportHeight) {
                    // Center the page in viewport
                    double pageHeight = targetPageBox.getHeight();
                    double centerOffset = Math.max(0, (viewportHeight - pageHeight) / 2);
                    double adjustedY = Math.max(0, targetY - centerOffset);

                    double maxScroll = contentHeight - viewportHeight;
                    double scrollPosition = adjustedY / maxScroll;

                    scrollPane.setVvalue(Math.min(1.0, Math.max(0.0, scrollPosition)));

                    logger.debug("Scrolled to page {} at position {} (targetY={}, adjustedY={})",
                            pageIndex + 1, scrollPosition, targetY, adjustedY);
                }

            } catch (Exception e) {
                logger.error("Error scrolling to page {}", pageIndex + 1, e);
            }
        });
    }

    public void highlightSearchResults(List<SearchResult> results) {
        // Clear previous highlights
        clearSearchHighlights();

        if (results == null || results.isEmpty()) {
            logger.info("No results to highlight");
            return;
        }

        // Group results by page
        searchResultsByPage.clear();
        for (SearchResult result : results) {
            int pageIndex = result.getPageNumber() - 1;
            searchResultsByPage.computeIfAbsent(pageIndex, k -> new java.util.ArrayList<>())
                    .add(result);
        }

        // ✅ FIX: Force load all pages with search results
        logger.info("Loading {} pages with search results...", searchResultsByPage.size());

        // Load pages one by one
        loadPagesWithResults(new java.util.ArrayList<>(searchResultsByPage.keySet()), 0, () -> {
            // After all pages loaded, apply highlights
            Platform.runLater(() -> {
                updateSearchHighlightsOnAllPages();
                logger.info("Highlighted {} search results across {} pages",
                        results.size(), searchResultsByPage.size());
            });
        });
    }

    public void toggleSearchPanel(SearchPanelPosition position) {
        this.searchPanelPosition = position;

        if (searchPanelVisible && this.searchPanelPosition == position) {
            // Hide panel if already visible in same mode
            hideSearchPanel();
        } else {
            showSearchPanel(position);
        }
    }

    private void showSearchPanel(SearchPanelPosition position) {
        if (currentDocument == null) {
            showError("No PDF Loaded", "Please open a PDF file first");
            return;
        }

        searchPanel.setPDFDocument(currentDocument);

        if (position == SearchPanelPosition.FLOAT) {
            // Show as dialog (existing method)
            handleSearch();
        } else {
            // Show as side panel
            showSearchSidePanel(position);
        }

        searchPanelVisible = true;
        logger.info("Search panel shown in {} mode", position);
    }

    private void showSearchSidePanel(SearchPanelPosition position) {
        // Clear any existing panels
        rootPane.setLeft(null);
        rootPane.setRight(null);

        searchPanel.setVisible(true);
        searchPanel.setManaged(true);
        searchPanel.setPrefWidth(320);
        searchPanel.setMinWidth(250);
        searchPanel.setMaxWidth(450);

        if (position == SearchPanelPosition.LEFT) {
            rootPane.setLeft(searchPanel);
        } else {
            rootPane.setRight(searchPanel);
        }

        logger.info("Search side panel shown on {}", position);
    }

    private void hideSearchPanel() {
        searchPanel.setVisible(false);
        searchPanel.setManaged(false);
        rootPane.setLeft(null);
        rootPane.setRight(null);
        searchPanelVisible = false;

        // Clear highlights
        clearSearchHighlights();

        logger.info("Search panel hidden");
    }

    private void ensurePageLoaded(int pageIndex, Runnable callback) {
        if (pagesContainer == null || pageIndex < 0 || pageIndex >= pagesContainer.getChildren().size()) {
            logger.warn("Invalid page index or container: {}", pageIndex);
            return;
        }

        VBox pageBox = (VBox) pagesContainer.getChildren().get(pageIndex);

        // Check if page is already rendered
        if (isPageRendered(pageBox)) {
            // Page already loaded, execute callback immediately
            if (callback != null) {
                callback.run();
            }
            logger.debug("Page {} already rendered", pageIndex + 1);
            return;
        }

        // Page not loaded, force load it
        logger.info("Force loading page {} before navigation", pageIndex + 1);

        // Load the page
        loadPageAndWait(pageIndex, pageBox, callback);
    }

    private void loadPageAndWait(int pageIndex, VBox pageBox, Runnable callback) {
        // Mark as loading
        loadingPages.add(pageIndex);

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

                    // Create annotation layer
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
                    logger.debug("Force loaded page {} with AnnotationLayer", pageIndex + 1);

                    // ✅ FIX: Wait for scene graph to update
                    if (callback != null) {
                        new java.util.Timer().schedule(new java.util.TimerTask() {
                            @Override
                            public void run() {
                                Platform.runLater(() -> {
                                    // ✅ Force layout update on containers only
                                    imageStack.layout();
                                    pageBox.layout();

                                    logger.debug("Page {} render completed, executing callback", pageIndex + 1);
                                    callback.run();
                                });
                            }
                        }, 300); // Wait 300ms for render
                    }
                });

            } catch (IOException e) {
                logger.error("Error force loading page {}", pageIndex + 1, e);
                loadingPages.remove(pageIndex);

                Platform.runLater(() -> {
                    if (!pageBox.getChildren().isEmpty()
                            && pageBox.getChildren().getFirst() instanceof StackPane) {
                        Label errorLabel = new Label("Error loading page");
                        errorLabel.setStyle("-fx-text-fill: red;");
                        ((StackPane) pageBox.getChildren().getFirst()).getChildren().set(0, errorLabel);
                    }
                });
            }
        });
    }

    private boolean isPageRendered(VBox pageBox) {
        if (pageBox == null || pageBox.getChildren().isEmpty()) {
            return false;
        }

        javafx.scene.Node firstChild = pageBox.getChildren().get(0);

        // Check if it's a StackPane with ImageView (rendered page)
        if (firstChild instanceof StackPane stackPane) {
            if (!stackPane.getChildren().isEmpty()) {
                javafx.scene.Node content = stackPane.getChildren().get(0);
                return content instanceof ImageView;
            }
        }

        return false;
    }

    public void highlightSearchResult(SearchResult result) {
        if (result == null) {
            logger.warn("Attempted to highlight null result");
            return;
        }

        this.currentActiveResult = result;

        // Ensure the page is loaded and visible
        int pageIndex = result.getPageNumber() - 1;
        if (pageIndex >= 0 && pageIndex < currentDocument.getTotalPages()) {

            // ✅ FIX: Use CountDownLatch to ensure page is fully loaded
            ensurePageLoadedAndReady(pageIndex, () -> {
                // Navigate to page
                Platform.runLater(() -> {
                    navigateToPage(pageIndex);

                    // Apply highlights after navigation completes
                    Platform.runLater(() -> {
                        updateSearchHighlightsOnAllPages();
                        logger.info("Highlighting result on page {} at ({}, {})",
                                result.getPageNumber(), result.getX(), result.getY());
                    });
                });
            });
        }
    }

    private void loadPagesWithResults(List<Integer> pageIndices, int currentIndex, Runnable onComplete) {
        if (currentIndex >= pageIndices.size()) {
            // All pages loaded
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        int pageIndex = pageIndices.get(currentIndex);

        if (pageIndex < 0 || pageIndex >= pagesContainer.getChildren().size()) {
            // Skip invalid page, continue to next
            loadPagesWithResults(pageIndices, currentIndex + 1, onComplete);
            return;
        }

        VBox pageBox = (VBox) pagesContainer.getChildren().get(pageIndex);

        if (isPageRendered(pageBox)) {
            // Page already rendered, continue to next
            logger.debug("Page {} already rendered, skipping", pageIndex + 1);
            loadPagesWithResults(pageIndices, currentIndex + 1, onComplete);
        } else {
            // Load page then continue
            logger.debug("Loading page {} for search highlights", pageIndex + 1);
            loadPageAndWait(pageIndex, pageBox, () -> {
                // Continue to next page
                loadPagesWithResults(pageIndices, currentIndex + 1, onComplete);
            });
        }
    }

    private void updateSearchHighlightsOnAllPages() {
        if (pagesContainer == null) {
            logger.warn("Cannot update highlights - pagesContainer is null");
            return;
        }

        int highlightCount = 0;
        int pagesMissing = 0;

        for (Map.Entry<Integer, List<SearchResult>> entry : searchResultsByPage.entrySet()) {
            int pageIndex = entry.getKey();
            List<SearchResult> pageResults = entry.getValue();

            if (pageIndex < 0 || pageIndex >= pagesContainer.getChildren().size()) {
                logger.warn("Invalid page index: {}", pageIndex);
                continue;
            }

            VBox pageBox = (VBox) pagesContainer.getChildren().get(pageIndex);

            // Find AnnotationLayer in the page
            AnnotationLayer annotationLayer = findAnnotationLayer(pageBox);
            if (annotationLayer != null) {
                // Set all highlights for this page
                annotationLayer.setSearchHighlights(pageResults);
                highlightCount += pageResults.size();

                // Set active result if it's on this page
                if (currentActiveResult != null
                        && currentActiveResult.getPageNumber() - 1 == pageIndex) {
                    annotationLayer.setActiveSearchResult(currentActiveResult);
                    logger.debug("Set active result on page {} at ({}, {})",
                            pageIndex + 1, currentActiveResult.getX(), currentActiveResult.getY());
                }

                logger.debug("Applied {} highlights to page {}", pageResults.size(), pageIndex + 1);
            } else {
                pagesMissing++;
                logger.warn("No AnnotationLayer found for page {} (not rendered yet)", pageIndex + 1);
            }
        }

        if (pagesMissing > 0) {
            logger.warn("{} pages missing AnnotationLayer - they may not be rendered yet", pagesMissing);
        }

        logger.info("Applied {} highlights to {} pages ({} missing)",
                highlightCount, searchResultsByPage.size() - pagesMissing, pagesMissing);
    }

    private AnnotationLayer findAnnotationLayer(VBox pageBox) {
        if (pageBox.getChildren().isEmpty()) {
            return null;
        }

        if (pageBox.getChildren().get(0) instanceof StackPane stackPane) {
            for (javafx.scene.Node node : stackPane.getChildren()) {
                if (node instanceof AnnotationLayer layer) {
                    // ✅ CRITICAL: Sync canvas size with rendered image
                    if (!stackPane.getChildren().isEmpty() 
                        && stackPane.getChildren().get(0) instanceof ImageView imageView) {
                        Image image = imageView.getImage();
                        if (image != null) {
                            double imageWidth = image.getWidth();
                            double imageHeight = image.getHeight();
                            
                            // ✅ Canvas must match rendered image exactly
                            if (layer.getWidth() != imageWidth || layer.getHeight() != imageHeight) {
                                layer.setWidth(imageWidth);
                                layer.setHeight(imageHeight);
                                logger.debug("Synced AnnotationLayer to image size: {}x{}", 
                                           imageWidth, imageHeight);
                            }
                        }
                    }
                    
                    // ✅ Update scale (for future use if needed)
                    layer.setScale(currentZoom);
                    
                    return layer;
                }
            }
        }
        return null;
    }

    private void clearSearchHighlights() {
        searchResultsByPage.clear();
        currentActiveResult = null;

        if (pagesContainer == null) {
            return;
        }

        for (javafx.scene.Node child : pagesContainer.getChildren()) {
            if (child instanceof VBox pageBox) {
                AnnotationLayer annotationLayer = findAnnotationLayer(pageBox);
                if (annotationLayer != null) {
                    annotationLayer.clearSearchHighlights();
                }
            }
        }

        logger.debug("Search highlights cleared");
    }

    private void ensurePageLoadedAndReady(int pageIndex, Runnable callback) {
        if (pagesContainer == null || pageIndex < 0 || pageIndex >= pagesContainer.getChildren().size()) {
            logger.warn("Invalid page index or container: {}", pageIndex);
            return;
        }

        VBox pageBox = (VBox) pagesContainer.getChildren().get(pageIndex);

        // Check if page is already rendered
        if (isPageRendered(pageBox)) {
            // Page already loaded
            logger.debug("Page {} already rendered", pageIndex + 1);
            if (callback != null) {
                // Wait a bit for any pending renders
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> {
                            // ✅ Force layout update on container only
                            pageBox.layout();

                            // ✅ Get StackPane and force its layout too
                            if (!pageBox.getChildren().isEmpty()
                                    && pageBox.getChildren().get(0) instanceof StackPane stackPane) {
                                stackPane.layout();
                            }

                            callback.run();
                        });
                    }
                }, 200); // Wait 200ms
            }
            return;
        }

        // Page not loaded, force load it
        logger.info("Force loading page {} before highlight", pageIndex + 1);
        loadPageAndWait(pageIndex, pageBox, callback);
    }

    // ✅ Call this after any zoom change
    private void updateAnnotationLayersScale() {
        if (pagesContainer == null) {
            return;
        }

        for (javafx.scene.Node child : pagesContainer.getChildren()) {
            if (child instanceof VBox pageBox) {
                AnnotationLayer layer = findAnnotationLayer(pageBox);
                if (layer != null) {
                    layer.setScale(currentZoom);
                    layer.redraw();
                }
            }
        }
    }

}
