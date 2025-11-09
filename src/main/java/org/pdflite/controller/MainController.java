package org.pdflite.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
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
 * Main Controller for the PDF Lite Application.
 * <p>
 * This controller manages the user interface and coordinates all PDF viewing operations.
 * It handles file opening/closing, page navigation, zoom operations, continuous scrolling
 * view with lazy loading, annotation mode toggling, and UI state management.
 * </p>
 * <p>
 * The controller implements an efficient continuous scrolling mechanism with debounced
 * scroll event handling, lazy loading of pages, multithreaded rendering using an
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
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(6);

    /**
     * Page renderer for handling page rendering and caching.
     */
    private PageRenderer pageRenderer;

    /**
     * Scroll handler for managing scroll events and lazy loading.
     */
    private ScrollHandler scrollHandler;

    /**
     * Flag indicating whether highlight mode is currently active.
     */
    private boolean highlightModeActive = false;

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

        // Initialize page renderer and scroll handler
        pageRenderer = new PageRenderer(pdfService, renderExecutor);
        scrollHandler = new ScrollHandler(pageRenderer, scrollPane);

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
                scrollHandler.handleScroll();
                // Update page info after scroll handler processes the scroll
                Platform.runLater(() -> {
                    if (currentDocument != null) {
                        int currentPage = scrollHandler.getCurrentPageFromScroll();
                        if (currentPage >= 0 && currentPage != currentDocument.getCurrentPage()) {
                            currentDocument.setCurrentPage(currentPage);
                            updatePageInfo();
                        }
                    }
                });
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
        if (pageRenderer != null) {
            pageRenderer.setHighlightModeActive(true);
        }
    }

    /**
     * Disables highlight mode for all annotation layers.
     */
    private void disableHighlightMode() {
        setAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.NONE);
        if (pageRenderer != null) {
            pageRenderer.setHighlightModeActive(false);
        }
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
     * clears the loading pages set and cache, re-renders all pages at the new zoom level, and
     * updates the status label with the current zoom percentage.
     * </p>
     *
     * @param prefix optional prefix for the status message (e.g., "Fit to Width"), or null
     */
    private void applyZoom(String prefix) {
        if (currentDocument != null) {
            currentDocument.setZoomLevel(currentZoom);

            // Update zoom combo box
            if (zoomComboBox != null) {
                zoomComboBox.setValue(String.format("%.0f%%", currentZoom * 100));
            }

            // Clear cache and pending renders for new zoom level
            if (pageRenderer != null) {
                pageRenderer.clearCache();
                pageRenderer.cancelAllPendingRenders();
                pageRenderer.setZoom(currentZoom);
            }
            renderCurrentPage();

            String statusMessage = prefix != null
                    ? String.format("%s - Zoom: %.0f%%", prefix, currentZoom * 100)
                    : String.format("Zoom: %.0f%%", currentZoom * 100);
            updateStatusLabel(statusMessage);
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

            // Update renderer and scroll handler with new document
            if (pageRenderer != null) {
                pageRenderer.setDocument(currentDocument, currentZoom);
            }

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
                    VBox pageBox = pageRenderer.createPagePlaceholder(i, pageWidth, pageHeight);
                    pagesContainer.getChildren().add(pageBox);
                }

                contentPane.getChildren().add(pagesContainer);

                // Update scroll handler with document and container
                if (scrollHandler != null) {
                    scrollHandler.setDocument(currentDocument, pagesContainer);
                }

                // Load first few visible pages immediately
                if (scrollHandler != null) {
                    Platform.runLater(() -> scrollHandler.handleScroll());
                }
            }

        } catch (IOException e) {
            logger.error("Error rendering page", e);
            showError("Rendering Error", "Could not render the page: " + e.getMessage());
        }
    }

    /**
     * Scrolls the viewport to display the current page.
     * <p>
     * This method delegates to ScrollHandler to scroll to the current page.
     * </p>
     */
    private void scrollToCurrentPage() {
        if (currentDocument != null && scrollHandler != null) {
            scrollHandler.scrollToPage(currentDocument.getCurrentPage());
        }
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
