package org.pdflite.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.pdflite.util.Constants;
import org.pdflite.util.NavigationHelper;
import org.pdflite.view.AnnotationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.pdflite.model.SearchResult;

/**
 * Main Controller for PDF Lite Application
 * Simplified with SearchManager and NavigationHelper
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

    // Services and helpers
    private PDFService pdfService;
    private NavigationHelper navigationHelper;
    private SearchManager searchManager;

    // Document state
    private PDFDocument currentDocument;
    private double currentZoom = Constants.DEFAULT_ZOOM;
    private VBox pagesContainer;

    // Rendering
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(2);
    private final java.util.Set<Integer> loadingPages = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final long SCROLL_DEBOUNCE_MS = 200;
    private Timer scrollTimer;

    // Annotation mode
    private boolean highlightModeActive = false;

    // Search dialog (for float mode)
    private SearchDialogController searchDialogController;
    private Stage searchDialogStage;

    @FXML
    public void initialize() {
        logger.info("Initializing MainController");
        pdfService = new PDFService();

        // Create helpers
        navigationHelper = new NavigationHelper(this, pdfService, renderExecutor, loadingPages);
        searchManager = new SearchManager(this, navigationHelper);

        // Setup zoom combo box
        if (zoomComboBox != null) {
            zoomComboBox.getItems().addAll("50%", "75%", "100%", "125%", "150%", "200%");
            zoomComboBox.setValue("100%");
            zoomComboBox.setOnAction(e -> handleZoomComboBoxChange());
        }

        // Setup page navigation
        if (pageNumberField != null) {
            pageNumberField.setOnAction(e -> navigationHelper.jumpToPage(
                Integer.parseInt(pageNumberField.getText())
            ));
        }

        // Setup scroll listener
        if (scrollPane != null) {
            scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
                if (currentDocument != null && pagesContainer != null) {
                    if (scrollTimer != null) {
                        scrollTimer.cancel();
                    }

                    scrollTimer = new Timer();
                    scrollTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            loadVisiblePages();
                        }
                    }, SCROLL_DEBOUNCE_MS);

                    navigationHelper.updateCurrentPageFromScroll();
                }
            });
        }

        updateUIState(false);
    }

    // ==================== FILE OPERATIONS ====================

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
    private void handleExit() {
        if (currentDocument != null) {
            pdfService.closePDF(currentDocument);
        }
        Platform.exit();
    }

    // ==================== ZOOM OPERATIONS ====================

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
    
    @FXML
    private void handleZoomChange() {
        handleZoomComboBoxChange();
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

    private void handleZoomComboBoxChange() {
        if (zoomComboBox != null && currentDocument != null) {
            String value = zoomComboBox.getValue();
            if (value != null) {
                try {
                    currentZoom = Double.parseDouble(value.replace("%", "")) / 100.0;
                    applyZoom(null);
                } catch (NumberFormatException e) {
                    logger.error("Invalid zoom value: {}", value);
                }
            }
        }
    }

    private void applyZoom(String prefix) {
        if (currentDocument != null) {
            currentDocument.setZoomLevel(currentZoom);

            if (zoomComboBox != null) {
                zoomComboBox.setValue(String.format("%.0f%%", currentZoom * 100));
            }

            loadingPages.clear();
            renderCurrentPage();

            Platform.runLater(() -> {
                searchManager.updateHighlightsAfterZoom(currentZoom);
            });

            String statusMessage = prefix != null
                ? String.format("%s - Zoom: %.0f%%", prefix, currentZoom * 100)
                : String.format("Zoom: %.0f%%", currentZoom * 100);
            updateStatusLabel(statusMessage);
        }
    }

    private double calculateFitToPageZoom(double imageWidth, double imageHeight) {
        if (scrollPane == null) {
            return Constants.DEFAULT_ZOOM;
        }

        double viewportWidth = scrollPane.getViewportBounds().getWidth() - 20;
        double viewportHeight = scrollPane.getViewportBounds().getHeight() - 20;

        double zoomWidth = viewportWidth / imageWidth;
        double zoomHeight = viewportHeight / imageHeight;

        return Math.min(1.0, Math.min(zoomWidth, zoomHeight));
    }

    // ==================== NAVIGATION ====================

    @FXML
    private void handlePreviousPage() {
        if (currentDocument != null && currentDocument.getCurrentPage() > 0) {
            navigationHelper.navigateToPage(currentDocument.getCurrentPage() - 1);
        }
    }

    @FXML
    private void handleNextPage() {
        if (currentDocument != null
            && currentDocument.getCurrentPage() < currentDocument.getTotalPages() - 1) {
            navigationHelper.navigateToPage(currentDocument.getCurrentPage() + 1);
        }
    }

    @FXML
    private void handleGoToPage() {
        if (pageNumberField != null && !pageNumberField.getText().isEmpty()) {
            try {
                int pageNum = Integer.parseInt(pageNumberField.getText());
                navigationHelper.jumpToPage(pageNum);
            } catch (NumberFormatException e) {
                showError("Invalid Input", "Please enter a valid page number");
                resetPageFieldToCurrentPage();
            }
        }
    }

    // ==================== SEARCH OPERATIONS ====================

    @FXML
    private void handleSearch() {
        handleSearchDialog();
    }

    @FXML
    private void handleSearchLeft() {
        searchManager.togglePanel(SearchManager.SearchPanelPosition.LEFT);
    }

    @FXML
    private void handleSearchRight() {
        searchManager.togglePanel(SearchManager.SearchPanelPosition.RIGHT);
    }

    @FXML
    private void handleHideSearch() {
        searchManager.clearSearch();
    }

    public void handleSearchDialog() {
        if (currentDocument == null) {
            showError("No PDF Loaded", "Please open a PDF file first");
            return;
        }

        try {
            if (searchDialogStage == null) {
                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/search-dialog.fxml")
                );

                Parent root = loader.load();

                searchDialogController = loader.getController();
                searchDialogController.setPDFDocument(currentDocument);
                searchDialogController.setMainController(this);

                searchDialogStage = new Stage();
                searchDialogStage.setTitle("Search in PDF");
                searchDialogStage.setScene(new Scene(root));
                searchDialogStage.initOwner(rootPane.getScene().getWindow());

                searchDialogStage.setOnCloseRequest(e -> {
                    searchDialogController.cleanup();
                });
            } else {
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

    // Called by SearchPanel/SearchDialog
    public void highlightSearchResults(List<SearchResult> results) {
        searchManager.showResults(results);
    }

    public void highlightSearchResult(SearchResult result) {
        searchManager.navigateToResult(result);
    }

    // ==================== ANNOTATION ====================

    @FXML
    private void handleHighlight() {
        highlightModeActive = !highlightModeActive;

        if (highlightModeActive) {
            updateStatusLabel("Highlight mode: Active - Click and drag to highlight");
            setAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.HIGHLIGHT);
        } else {
            updateStatusLabel("Highlight mode: Disabled");
            setAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.NONE);
        }
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

    // ==================== HELP ====================

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

    // ==================== INTERNAL METHODS ====================

    private void openPDFFile(File file) {
        try {
            if (currentDocument != null) {
                pdfService.closePDF(currentDocument);
            }

            currentDocument = pdfService.openPDF(file);

            if (scrollPane != null && scrollPane.getViewportBounds().getWidth() > 0
                && scrollPane.getViewportBounds().getHeight() > 0) {
                Image firstPage = pdfService.renderPage(currentDocument, 0, 1.0f);
                currentZoom = calculateFitToPageZoom(firstPage.getWidth(), firstPage.getHeight());
            } else {
                currentZoom = 0.7;
            }

            currentDocument.setZoomLevel(currentZoom);

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

                pagesContainer = new VBox(10);
                pagesContainer.setAlignment(javafx.geometry.Pos.TOP_CENTER);
                pagesContainer.setStyle("-fx-background-color: #808080; -fx-padding: 10;");

                int totalPages = currentDocument.getTotalPages();

                Image firstPage = pdfService.renderPage(currentDocument, 0, (float) currentZoom);
                double pageWidth = firstPage.getWidth();
                double pageHeight = firstPage.getHeight();

                logger.info("Creating continuous scroll view for {} pages", totalPages);

                for (int i = 0; i < totalPages; i++) {
                    VBox pageBox = createPagePlaceholder(i, pageWidth, pageHeight);
                    pagesContainer.getChildren().add(pageBox);
                }

                contentPane.getChildren().add(pagesContainer);

                Platform.runLater(this::loadVisiblePages);
            }

        } catch (IOException e) {
            logger.error("Error rendering page", e);
            showError("Rendering Error", "Could not render the page: " + e.getMessage());
        }
    }

    private VBox createPagePlaceholder(int pageIndex, double width, double height) {
        VBox pageBox = new VBox(5);
        pageBox.setAlignment(javafx.geometry.Pos.TOP_CENTER);
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
                    int totalPages = currentDocument.getTotalPages();
                    for (int i = 0; i < totalPages; i++) {
                        if (!loadingPages.contains(i)) {
                            VBox pageBox = (VBox) pagesContainer.getChildren().get(i);
                            if (!navigationHelper.isPageRendered(pageBox)) {
                                navigationHelper.loadPageAndWait(i, pageBox, null);
                            }
                        }
                    }
                    return;
                }

                double visibleStart = scrollValue * (contentHeight - bufferZone);
                double visibleEnd = visibleStart + bufferZone;

                double loadStart = Math.max(0, visibleStart - bufferZone);
                double loadEnd = Math.min(contentHeight, visibleEnd + bufferZone);

                int totalPages = currentDocument.getTotalPages();
                double currentY = 0;

                for (int i = 0; i < totalPages; i++) {
                    VBox pageBox = (VBox) pagesContainer.getChildren().get(i);
                    double pageHeight = pageBox.getPrefHeight();
                    double pageStart = currentY;
                    double pageEnd = currentY + pageHeight;

                    if (pageEnd >= loadStart && pageStart <= loadEnd) {
                        if (!loadingPages.contains(i)) {
                            if (!navigationHelper.isPageRendered(pageBox)) {
                                navigationHelper.loadPageAndWait(i, pageBox, null);
                            }
                        }
                    }

                    currentY = pageEnd + 10;
                }

            } catch (Exception e) {
                logger.error("Error loading visible pages", e);
            }
        });
    }

    // ==================== UI HELPERS ====================

    public void updatePageInfo() {
        if (currentDocument != null) {
            int current = currentDocument.getCurrentPage() + 1;
            int total = currentDocument.getTotalPages();

            if (totalPagesLabel != null) {
                totalPagesLabel.setText("/ " + total);
            }

            if (pageNumberField != null) {
                pageNumberField.setText(String.valueOf(current));
            }

            if (prevButton != null) {
                prevButton.setDisable(current == 1);
            }
            if (nextButton != null) {
                nextButton.setDisable(current == total);
            }
        }
    }

    private void resetPageFieldToCurrentPage() {
        if (currentDocument != null && pageNumberField != null) {
            pageNumberField.setText(String.valueOf(currentDocument.getCurrentPage() + 1));
        }
    }

    private void updateUIState(boolean hasDocument) {
        if (prevButton != null) prevButton.setDisable(!hasDocument);
        if (nextButton != null) nextButton.setDisable(!hasDocument);
        if (pageNumberField != null) pageNumberField.setDisable(!hasDocument);
        if (zoomComboBox != null) zoomComboBox.setDisable(!hasDocument);
    }

    public void updateStatusLabel(String message) {
        statusLabel.setText(message);
    }

    public void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ==================== GETTERS FOR HELPERS ====================

    public BorderPane getRootPane() { return rootPane; }
    public ScrollPane getScrollPane() { return scrollPane; }
    public VBox getPagesContainer() { return pagesContainer; }
    public PDFDocument getCurrentDocument() { return currentDocument; }
    public double getCurrentZoom() { return currentZoom; }
    public boolean isHighlightModeActive() { return highlightModeActive; }
    
    public int getTotalPages() {
        return currentDocument != null ? currentDocument.getTotalPages() : 0;
    }
}