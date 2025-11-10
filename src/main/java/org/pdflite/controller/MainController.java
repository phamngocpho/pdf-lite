package org.pdflite.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDPage;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.pdflite.util.Constants;
import org.pdflite.view.AnnotationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.Interpolator;
import javafx.animation.KeyValue;
import javafx.util.Duration;
import javafx.scene.control.TextFormatter;


import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main Controller for the PDF Lite Application.
 * <p>
 * This controller manages the user interface and coordinates all PDF viewing operations.
 * It handles file opening/closing, page navigation, zoom operations, continuous scrolling
 * view with lazy loading, annotation mode toggling, and UI state management.
 * </p>
 * <p>
 * The controller implements an efficient continuous scrolling mechanism with debounced
 * scroll event handling, lazy loading of pages, multi-threaded rendering using an
 * ExecutorService, and page caching through the PDFDocument model.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 * @see PDFService
 * @see PDFDocument
 * @see AnnotationLayer
 */
public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // FXML Injected UI Components

    /**
     * The root pane of the application.
     */
    @FXML private BorderPane rootPane;

    /**
     * Scroll pane containing the PDF page content.
     */
    @FXML private ScrollPane scrollPane;

    /**
     * Container pane for PDF page images and annotations.
     */
    @FXML private StackPane contentPane;

    /**
     * Status label for displaying messages to the user.
     */
    @FXML private Label statusLabel;

    /**
     * Label showing the total number of pages.
     */
    @FXML private Label totalPagesLabel;

    /**
     * Text field for entering page numbers.
     */
    @FXML private TextField pageNumberField;

    /**
     * Combo box for selecting zoom levels.
     */
    @FXML private ComboBox<String> zoomComboBox;

    /**
     * Button for navigating to the previous page.
     */
    @FXML private Button prevButton;

    /**
     * Button for navigating to the next page.
     */
    @FXML private Button nextButton;

    // Service and Model

    /**
     * Service for PDF operations.
     */
    private PDFService pdfService;

    /**
     * Currently open PDF document.
     */
    private PDFDocument currentDocument;

    /**
     * Current zoom level (1.0 = 100%).
     */
    private double currentZoom = Constants.DEFAULT_ZOOM;

    /**
     * Container for all page boxes in continuous scroll view.
     */
    private VBox pagesContainer;

    /**
     * Executor service for parallel page rendering.
     */
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(2); // 2 threads for parallel rendering

    /**
     * Debounce delay in milliseconds for scroll event handling.
     * <p>
     * This delay prevents excessive page loading operations during continuous scrolling.
     * After the user stops scrolling, the system waits this duration before triggering
     * the page loading logic, which improves performance and reduces unnecessary rendering.
     * </p>
     */
    private static final long SCROLL_DEBOUNCE_MS = 200; // Wait 200ms after scroll stops

    /**
     * Flag indicating whether highlight mode is currently active.
     */
    private boolean highlightModeActive = false;

    /**
     * Timer for debouncing scroll events.
     */
    private java.util.Timer scrollTimer;

    /**
     * Set of page indices currently being loaded to prevent duplicate loading.
     */
    private final java.util.Set<Integer> loadingPages = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Initializes the controller after FXML injection.
     * <p>
     * This method is automatically called by the JavaFX framework after all
     * FXML-injected fields have been populated. It initializes the PDFService,
     * configures the zoom combo box with preset values, sets up page navigation
     * event handlers, configures scroll event listeners with debouncing, and
     * updates the initial UI state.
     * </p>
     */
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
            pageNumberField.setTextFormatter(new TextFormatter<>(change -> {
                String next = change.getControlNewText();
                return next.matches("\\d*") ? change : null;
            }));
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

    /**
     * Handles the "Open PDF" menu action.
     * <p>
     * This is a convenience method that delegates to {@link #handleOpenFile()}.
     * </p>
     */
    @FXML
    private void handleOpenPDF() {
        handleOpenFile();
    }

    /**
     * Opens a file chooser dialog and allows the user to select a PDF file.
     * <p>
     * This method displays a standard file chooser dialog filtered to show only
     * PDF files. If a file is selected, it calls {@link #openPDFFile(File)} to
     * open the document.
     * </p>
     */
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

    /**
     * Handles the "Fit to Width" action.
     * <p>
     * Calculates and applies a zoom level that fits the page width to the viewport,
     * ensuring the entire width of the page is visible without horizontal scrolling.
     * The zoom level is capped at 100% to prevent upscaling.
     * </p>
     */
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
    //Thêm hàm cuộn mượt
    private void smoothScrollTo(double targetVValue, Duration duration) {
        if (scrollPane == null) return;

        double start = scrollPane.getVvalue();
        double target = Math.max(0.0, Math.min(1.0, targetVValue));
        if (Math.abs(target - start) < 1e-4) return;

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(scrollPane.vvalueProperty(), start, Interpolator.EASE_BOTH)),
                new KeyFrame(duration,
                        new KeyValue(scrollPane.vvalueProperty(), target, Interpolator.EASE_BOTH))
        );
        timeline.play();
    }


    /**
     * Handles the "Fit to Page" action.
     * <p>
     * Calculates and applies a zoom level that fits the entire page (both width
     * and height) within the viewport while maintaining aspect ratio. The zoom
     * level is capped at 100% to prevent upscaling.
     * </p>
     */
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

    /**
     * Handles the "Highlight" action to toggle highlight mode.
     * <p>
     * When enabled, users can click and drag on PDF pages to create
     * semi-transparent yellow highlight annotations. When disabled,
     * mouse interactions do not create annotations.
     * </p>
     */
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

    /**
     * Enables highlight mode for all annotation layers.
     */
    private void enableHighlightMode() {
        setAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.HIGHLIGHT);
    }

    /**
     * Disables highlight mode for all annotation layers.
     */
    private void disableHighlightMode() {
        setAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.NONE);
    }

    /**
     * Sets the annotation mode for all page annotation layers.
     * <p>
     * This iterates through all pages in the container and updates their
     * annotation layers to the specified mode.
     * </p>
     *
     * @param mode the annotation mode to set
     */
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

    /**
     * Handles the "Go to Page" action.
     * <p>
     * Delegates to {@link #jumpToPage()} to process the page number.
     * </p>
     */
    @FXML
    private void handleGoToPage() {
        jumpToPage();
    }

    /**
     * Handles changes to the zoom combo box selection.
     * <p>
     * Delegates to {@link #handleZoomComboBoxChange()} to process the zoom change.
     * </p>
     */
    @FXML
    private void handleZoomChange() {
        handleZoomComboBoxChange();
    }

    /**
     * Processes zoom combo box value changes.
     * <p>
     * Extracts the percentage value from the combo box selection and
     * applies it as the new zoom level.
     * </p>
     */
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

    /**
     * Handles the "Exit" action.
     * <p>
     * Closes the current PDF document if open and exits the application.
     * </p>
     */
    @FXML
    private void handleExit() {
        if (currentDocument != null) {
            pdfService.closePDF(currentDocument);
        }
        Platform.exit();
    }

    /**
     * Handles the "Zoom In" action.
     * <p>
     * Increases the zoom level by the configured step amount, up to the maximum
     * zoom level. The zoom level is then applied to re-render the pages.
     * </p>
     *
     * @see Constants#ZOOM_STEP
     * @see Constants#MAX_ZOOM
     */
    @FXML
    private void handleZoomIn() {
        currentZoom = Math.min(Constants.MAX_ZOOM, currentZoom + Constants.ZOOM_STEP);
        if (currentDocument != null) {
            applyZoom(null);
        }
    }

    /**
     * Handles the "Zoom Out" action.
     * <p>
     * Decreases the zoom level by the configured step amount, down to the minimum
     * zoom level. The zoom level is then applied to re-render the pages.
     * </p>
     *
     * @see Constants#ZOOM_STEP
     * @see Constants#MIN_ZOOM
     */
    @FXML
    private void handleZoomOut() {
        currentZoom = Math.max(Constants.MIN_ZOOM, currentZoom - Constants.ZOOM_STEP);
        if (currentDocument != null) {
            applyZoom(null);
        }
    }

    /**
     * Applies the current zoom level and updates UI components.
     * <p>
     * This method updates the document's zoom level, updates the zoom combo box display,
     * clears the loading pages set, re-renders all pages at the new zoom level, and
     * updates the status label with the current zoom percentage.
     * </p>
     *
     * @param prefix optional prefix for the status message (e.g., "Fit to Width"), or null
     */
    private void applyZoom(String prefix) {
        if (currentDocument != null) {
            currentDocument.setZoomLevel(currentZoom);

            if (zoomComboBox != null) {
                zoomComboBox.setValue(String.format("%.0f%%", currentZoom * 100));
            }

            // 🔁 Chỉ render lại nội dung của từng trang hiện có
            pagesContainer.getChildren().forEach(node -> {
                if (node instanceof VBox box) {
                    int pageIndex = Integer.parseInt(box.getId().replace("page-", ""));
                    ImageView img = (ImageView) ((StackPane) box.getChildren().get(0)).getChildren().get(0);
                    try {
                        Image newImg = pdfService.renderPage(currentDocument, pageIndex, (float) currentZoom);
                        img.setImage(newImg);
                    } catch (IOException e) {
                        logger.error("Error updating page zoom", e);
                    }
                }
            });

            updateStatusLabel(String.format("Zoom: %.0f%%", currentZoom * 100));
        }
    }


    /**
     * Handles the "Previous Page" action.
     * <p>
     * Navigates to the previous page if not already on the first page.
     * </p>
     */
    @FXML
    private void handlePreviousPage() {
        if (currentDocument != null && currentDocument.getCurrentPage() > 0) {
            navigateToPage(currentDocument.getCurrentPage() - 1);
        }
    }

    /**
     * Handles the "Next Page" action.
     * <p>
     * Navigates to the next page if not already on the last page.
     * </p>
     */
    @FXML
    private void handleNextPage() {
        if (currentDocument != null &&
            currentDocument.getCurrentPage() < currentDocument.getTotalPages() - 1) {
            navigateToPage(currentDocument.getCurrentPage() + 1);
        }
    }

    /**
     * Navigates to a specific page by index.
     * <p>
     * This method updates the current page in the document model, scrolls
     * the view to display that page, and updates the page information UI.
     * </p>
     *
     * @param pageIndex the zero-based page index to navigate to
     */
    private void navigateToPage(int pageIndex) {
        if (currentDocument != null) {
            currentDocument.setCurrentPage(pageIndex);
            scrollToCurrentPage();
            updatePageInfo();
        }
    }

    /**
     * Handles the "About" menu action.
     * <p>
     * Displays an information dialog showing the application name, version,
     * and a brief description.
     * </p>
     */
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

    /**
     * Opens a PDF file and initializes the document viewer.
     * <p>
     * This method closes any currently open document, opens and loads the new PDF file,
     * calculates optimal zoom level to fit the page, updates the UI state and displays
     * the first page, and updates page information and status. If an error occurs, an
     * error dialog is displayed to the user.
     * </p>
     *
     * @param file the PDF file to open
     */
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

    /**
     * Renders all pages of the current document in continuous scroll mode.
     * <p>
     * This method creates a VBox container holding all pages vertically, creates
     * placeholders for each page with loading indicators, renders the first page
     * to determine dimensions, and schedules lazy loading of visible pages.
     * Pages are loaded on-demand as they become visible in the viewport,
     * improving performance for large documents.
     * </p>
     */
    private void renderCurrentPage() {
        if (currentDocument == null) return;

        try {
            if (pagesContainer == null) {
                pagesContainer = new VBox(10);
                pagesContainer.setAlignment(Pos.TOP_CENTER);
                pagesContainer.setStyle("-fx-background-color: #808080; -fx-padding: 10;");
                contentPane.getChildren().add(pagesContainer);
            }

            // 🧹 Thay vì clear toàn bộ contentPane, chỉ xóa bên trong pagesContainer
            pagesContainer.getChildren().clear();

            int totalPages = currentDocument.getTotalPages();
            Image firstPage = pdfService.renderPage(currentDocument, 0, (float) currentZoom);
            double pageWidth = firstPage.getWidth();
            double pageHeight = firstPage.getHeight();

            for (int i = 0; i < totalPages; i++) {
                VBox pageBox = createPagePlaceholder(i, pageWidth, pageHeight);
                pagesContainer.getChildren().add(pageBox);
            }

            Platform.runLater(this::loadVisiblePages);

        } catch (IOException e) {
            logger.error("Error rendering page", e);
            showError("Rendering Error", "Could not render the page: " + e.getMessage());
        }
    }


    /**
     * Creates a placeholder VBox for a PDF page before it's loaded.
     * <p>
     * The placeholder contains a loading indicator stack pane with "Loading..." text
     * and a page number label at the bottom. The placeholder dimensions match the
     * expected page size.
     * </p>
     *
     * @param pageIndex the zero-based page index
     * @param width the expected page width in pixels
     * @param height the expected page height in pixels
     * @return a VBox placeholder for the page
     */
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

    /**
     * Creates a loading placeholder stack pane with "Loading..." text.
     *
     * @param width the width of the placeholder in pixels
     * @param height the height of the placeholder in pixels
     * @return a StackPane with a centered loading label
     */
    private StackPane createLoadingPlaceholder(double width, double height) {
        Label loadingLabel = new Label("Loading...");
        loadingLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 14px;");

        StackPane placeholder = new StackPane(loadingLabel);
        placeholder.setPrefSize(width, height);
        placeholder.setStyle("-fx-background-color: #505050;");

        return placeholder;
    }

    /**
     * Loads pages that are currently visible or near the viewport.
     * <p>
     * This method implements lazy loading by calculating the currently visible range
     * based on scroll position, adding a buffer zone (one viewport height above and below),
     * loading all pages within the extended range, preventing duplicate loading using a
     * concurrent set, and using multi-threaded rendering for parallel page loading.
     * </p>
     * <p>
     * This method is typically called after scroll events with a debounce delay.
     * </p>
     */
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


    /**
     * Loads and renders a single page in the background.
     * <p>
     * This method marks the page as loading to prevent duplicate requests, renders the page
     * using PDFService in a background thread, creates an ImageView and AnnotationLayer for
     * the rendered page, replaces the placeholder with the actual content on the JavaFX thread,
     * and handles errors by displaying an error message in the placeholder.
     * </p>
     *
     * @param pageIndex the zero-based page index to load
     * @param pageBox the VBox container that will hold the rendered page
     */
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


    /**
     * Updates the current page indicator based on scroll position.
     * <p>
     * This method determines which page is currently centered in the viewport
     * by calculating the visible center position and finding the page that
     * contains that position. It then updates the document's current page
     * and the UI page information.
     * </p>
     * <p>
     * This method is called immediately during scroll events (without debouncing)
     * to provide responsive page number feedback to the user.
     * </p>
     */
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

    /**
     * Scrolls the viewport to display the current page.
     * <p>
     * This method calculates the vertical position of the current page in the
     * continuous scroll view and adjusts the scroll pane's vertical value to
     * bring that page into view. The calculation accounts for page heights
     * and spacing between pages.
     * </p>
     * <p>
     * This method is typically called after programmatic page navigation
     * (e.g., clicking previous/next buttons or jumping to a specific page).
     * </p>
     */
    private void scrollToCurrentPage() {
        if (currentDocument == null || pagesContainer == null || scrollPane == null) return;

        Platform.runLater(() -> {
            try {
                int targetPage = currentDocument.getCurrentPage(); // 0-based hoặc 1-based tùy bạn đang dùng
                double y = 0;

                for (int i = 0; i < targetPage; i++) {
                    VBox pageBox = (VBox) pagesContainer.getChildren().get(i);
                    y += pageBox.getPrefHeight() + 10; // 10 = spacing giữa các trang (điều chỉnh nếu khác)
                }

                double contentHeight  = pagesContainer.getHeight();
                double viewportHeight = scrollPane.getViewportBounds().getHeight();

                if (contentHeight > viewportHeight) {
                    double targetV = y / (contentHeight - viewportHeight);
                    smoothScrollTo(targetV, Duration.millis(350)); // 300–400ms là mượt
                }
            } catch (Exception ex) {
                // logger.error("Error scrolling to page", ex);
            }
        });
    }

    /**
     * Processes the page number field input and navigates to the specified page.
     * <p>
     * This method parses the page number from the text field (1-based), validates that
     * the page number is within valid bounds, navigates to the page if valid, or shows
     * an error dialog and resets the field if invalid.
     * </p>
     */
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

    /**
     * Resets the page number field to display the current page number.
     * <p>
     * This is typically called after an invalid page number input to restore
     * the field to a known good state.
     * </p>
     */
    private void resetPageFieldToCurrentPage() {
        if (currentDocument != null && pageNumberField != null) {
            pageNumberField.setText(String.valueOf(currentDocument.getCurrentPage() + 1));
        }
    }

    /**
     * Updates the page information display in the UI.
     * <p>
     * This method updates the total pages label, the current page number field, and
     * the enabled/disabled state of navigation buttons. Navigation buttons are disabled
     * when at the first or last page.
     * </p>
     */
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
            if (prevButton != null) prevButton.setMinSize(40, 40);
            if (nextButton != null) nextButton.setMinSize(40, 40);
            if (pageNumberField != null) {
                pageNumberField.setPrefColumnCount(4);
            }
        }
    }

    /**
     * Updates the enabled/disabled state of UI controls based on whether a document is open.
     * <p>
     * When no document is open, navigation and zoom controls are disabled.
     * When a document is open, these controls are enabled.
     * </p>
     *
     * @param hasDocument true if a document is currently open, false otherwise
     */
    private void updateUIState(boolean hasDocument) {
        // Enable/disable menu items and toolbar buttons based on whether a document is open
        if (prevButton != null) prevButton.setDisable(!hasDocument);
        if (nextButton != null) nextButton.setDisable(!hasDocument);
        if (pageNumberField != null) pageNumberField.setDisable(!hasDocument);
        if (zoomComboBox != null) zoomComboBox.setDisable(!hasDocument);
    }

    /**
     * Updates the status label with a message.
     *
     * @param message the message to display in the status label
     */
    private void updateStatusLabel(String message) {
        statusLabel.setText(message);
    }

    /**
     * Displays an error dialog to the user.
     *
     * @param title the title of the error dialog
     * @param message the error message to display
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
