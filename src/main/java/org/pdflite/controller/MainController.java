package org.pdflite.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.pdflite.manager.FileManager;
import org.pdflite.manager.FullscreenManager;
import org.pdflite.manager.PageInfoManager;
import org.pdflite.manager.UIStateManager;
import org.pdflite.manager.ZoomManager;
import org.pdflite.model.PDFDocument;
import org.pdflite.model.SearchResult;
import org.pdflite.service.PDFService;
import org.pdflite.util.Constants;
import org.pdflite.util.NavigationHelper;
import org.pdflite.view.AnnotationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main Controller for the PDF Lite Application.
 * <p>
 * This controller coordinates all PDF viewing operations by delegating to specialized managers.
 * It handles file operations, page navigation, zoom operations, continuous scrolling view with
 * lazy loading, annotation mode toggling, and UI state management.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // ==================== FXML Injected UI Components ====================

    @FXML private BorderPane rootPane;
    @FXML private ScrollPane scrollPane;
    @FXML private StackPane contentPane;
    @FXML private Label statusLabel;
    @FXML private Label totalPagesLabel;
    @FXML private TextField pageNumberField;
    @FXML private ComboBox<String> zoomComboBox;
    @FXML private ToolBar toolbar;
    @FXML private Button prevButton;
    @FXML private Button nextButton;

    // ==================== Services and Managers ====================

    private PDFService pdfService;
    private NavigationHelper navigationHelper;
    private SearchManager searchManager;
    private PageRenderer pageRenderer;
    private ScrollHandler scrollHandler;

    // Managers
    private ZoomManager zoomManager;
    private FileManager fileManager;
    private FullscreenManager fullscreenManager;
    private UIStateManager uiStateManager;
    private PageInfoManager pageInfoManager;

    // ==================== Document State ====================

    private PDFDocument currentDocument;
    private VBox pagesContainer;
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(6);
    private final java.util.Set<Integer> loadingPages = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private boolean highlightModeActive = false;

    // ==================== Search Dialog ====================

    private SearchDialogController searchDialogController;
    private Stage searchDialogStage;

    // ==================== Initialization ====================

    @FXML
    public void initialize() {
        logger.info("Initializing MainController");
        pdfService = new PDFService();

        // Initialize page renderer and scroll handler
        pageRenderer = new PageRenderer(pdfService, renderExecutor);
        scrollHandler = new ScrollHandler(pageRenderer, scrollPane);

        // Create helpers
        navigationHelper = new NavigationHelper(this, pdfService, renderExecutor, loadingPages);
        searchManager = new SearchManager(this, navigationHelper);

        // Initialize managers
        initializeManagers();

        // Setup page navigation
        if (pageNumberField != null) {
            pageNumberField.setOnAction(e -> handleGoToPage());
            pageNumberField.setTextFormatter(new TextFormatter<>(change -> {
                String next = change.getControlNewText();
                return next.matches("\\d*") ? change : null;
            }));
        }

        // Setup scroll listener
        if (scrollPane != null) {
            scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
                scrollHandler.handleScroll();
                // Update page info after scroll handler processes the scroll
                Platform.runLater(() -> {
                    if (currentDocument != null) {
                        int currentPage = scrollHandler.getCurrentPageFromScroll();
                        if (currentPage >= 0 && currentPage != currentDocument.getCurrentPage()) {
                            currentDocument.setCurrentPage(currentPage);
                            pageInfoManager.updatePageInfo(currentDocument);
                        }
                    }
                });
            });
        }

        uiStateManager.updateUIState(false);
    }

    /**
     * Initializes all manager classes.
     */
    private void initializeManagers() {
        // Zoom Manager
        zoomManager = new ZoomManager(pdfService, new ZoomManager.ZoomChangeListener() {
            @Override
            public void onZoomChanged(double newZoom) {
                if (currentDocument != null) {
                    loadingPages.clear();
                    renderCurrentPage();
                    Platform.runLater(() -> searchManager.updateHighlightsAfterZoom(newZoom));
                }
            }

            @Override
            public void onZoomApplied(double newZoom, String statusMessage) {
                uiStateManager.updateStatus(statusMessage);
            }
        });
        zoomManager.initialize(zoomComboBox, scrollPane);

        // File Manager
        fileManager = new FileManager(pdfService, new FileManager.FileOperationListener() {
            @Override
            public void onFileOpened(PDFDocument document, File file) {
                // Handled in openPDFFile
            }

            @Override
            public void onFileSaved(String fileName) {
                uiStateManager.updateStatus("Saved: " + fileName);
            }

            @Override
            public void onFileSaveAs(String fileName) {
                uiStateManager.updateStatus("Saved As: " + fileName);
            }

            @Override
            public void onError(String title, String message) {
                uiStateManager.showError(title, message);
            }

            @Override
            public void onPageDeleted(int pageNumber) {
                uiStateManager.updateStatus("Deleted page " + pageNumber);
            }
        });

        // Fullscreen Manager
        fullscreenManager = new FullscreenManager(rootPane, toolbar, new FullscreenManager.FullscreenListener() {
            @Override
            public void onFullscreenChanged(boolean isFullscreen) {
                // Fullscreen state changed
            }

            @Override
            public void updateStatus(String message) {
                uiStateManager.updateStatus(message);
            }
        });

        // UI State Manager
        uiStateManager = new UIStateManager(statusLabel, prevButton, nextButton, pageNumberField, zoomComboBox);

        // Page Info Manager
        pageInfoManager = new PageInfoManager(totalPagesLabel, pageNumberField, prevButton, nextButton);
    }

    // ==================== File Operations ====================

    @FXML
    private void handleOpenPDF() {
        handleOpenFile();
    }

    @FXML
    private void handleOpenFile() {
        Stage stage = (Stage) rootPane.getScene().getWindow();
        File file = fileManager.showOpenDialog(stage);
        if (file != null) {
            openPDFFile(file);
        }
    }

    private void openPDFFile(File file) {
        try {
            // Close current document if open
            if (currentDocument != null) {
                fileManager.close(currentDocument);
            }

            // Open new document
            currentDocument = fileManager.openFile(file);
            if (currentDocument == null) {
                return;
            }

            // Calculate initial zoom
            Image firstPage = pdfService.renderPage(currentDocument, 0, 1.0f);
            double initialZoom = zoomManager.calculateInitialZoom(firstPage);
            zoomManager.setCurrentZoom(initialZoom);
            currentDocument.setZoomLevel(initialZoom);

            // Update renderer and scroll handler with new document
            pageRenderer.setDocument(currentDocument, initialZoom);
            zoomManager.setDocument(currentDocument);

            // Update UI
            uiStateManager.updateUIState(true);
            renderCurrentPage();
            pageInfoManager.updatePageInfo(currentDocument);
            uiStateManager.updateStatus("Opened: " + file.getName());

            logger.info("Successfully opened PDF: {}", file.getName());
        } catch (IOException e) {
            logger.error("Error opening PDF file", e);
            uiStateManager.showError("Error Opening PDF", "Could not open the PDF file: " + e.getMessage());
        }
    }

    @FXML
    private void handleSave() {
        if (currentDocument == null) return;
        try {
            fileManager.save(currentDocument);
        } catch (IOException e) {
            logger.error("Error saving document", e);
            uiStateManager.showError("Save Error", "Could not save the document: " + e.getMessage());
        }
    }

    @FXML
    private void handleSaveAs() {
        if (currentDocument == null) return;
        try {
            Stage stage = (Stage) rootPane.getScene().getWindow();
            fileManager.saveAs(currentDocument, stage);
        } catch (IOException e) {
            logger.error("Error saving document as", e);
            uiStateManager.showError("Save As Error", "Could not save the document: " + e.getMessage());
        }
    }

    @FXML
    private void handleExit() {
        if (currentDocument != null) {
            fileManager.close(currentDocument);
        }
        Platform.exit();
    }

    @FXML
    private void handleDeletePage() {
        if (currentDocument == null) {
            return;
        }

        int total = currentDocument.getTotalPages();
        if (total <= 1) {
            uiStateManager.showError("Delete Page", "Cannot delete the last remaining page.");
            return;
        }

        int current = currentDocument.getCurrentPage();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Page");
        confirm.setHeaderText("Delete current page?");
        confirm.setContentText("This will remove page " + (current + 1) + " from the document.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                try {
                    fileManager.deletePages(currentDocument, java.util.List.of(current));
                    // Reset UI containers and re-render pages
                    if (contentPane != null) {
                        contentPane.getChildren().clear();
                    }
                    pagesContainer = null;
                    loadingPages.clear();
                    renderCurrentPage();
                    pageInfoManager.updatePageInfo(currentDocument);
                } catch (Exception e) {
                    logger.error("Error deleting page {}", current + 1, e);
                    uiStateManager.showError("Delete Page Error", "Could not delete the page: " + e.getMessage());
                }
            }
        });
    }

    // ==================== Zoom Operations ====================

    @FXML
    private void handleZoomIn() {
        zoomManager.zoomIn();
    }

    @FXML
    private void handleZoomOut() {
        zoomManager.zoomOut();
    }

    @FXML
    private void handleZoomChange() {
        zoomManager.handleZoomComboBoxChange();
    }

    @FXML
    private void handleFitToWidth() {
        zoomManager.fitToWidth();
    }

    @FXML
    private void handleFitToPage() {
        zoomManager.fitToPage();
    }

    // ==================== Navigation Operations ====================

    @FXML
    private void handlePreviousPage() {
        if (currentDocument != null && currentDocument.getCurrentPage() > 0) {
            navigationHelper.navigateToPage(currentDocument.getCurrentPage() - 1);
        }
    }

    private void applyZoom(String prefix) {
        if (currentDocument != null && scrollPane != null && pagesContainer != null) {
            // Lưu lại vị trí cuộn theo pixel trước khi zoom
            Bounds viewportBounds = scrollPane.getViewportBounds();
            Bounds contentBounds = pagesContainer.getBoundsInLocal();
            double oldVValue = scrollPane.getVvalue();
            double oldCenterY = oldVValue * (contentBounds.getHeight() - viewportBounds.getHeight())
                    + viewportBounds.getHeight() / 2;

            // Cập nhật zoom level và render lại ảnh
            currentDocument.setZoomLevel(currentZoom);

            if (zoomComboBox != null) {
                zoomComboBox.setValue(String.format("%.0f%%", currentZoom * 100));
            }

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

            // Sau khi layout xong, khôi phục lại đúng vị trí cũ (theo pixel)
            Platform.runLater(() -> {
                Bounds newContentBounds = pagesContainer.getBoundsInLocal();
                double newCenterY = oldCenterY * newContentBounds.getHeight() / contentBounds.getHeight();
                double newVValue = (newCenterY - viewportBounds.getHeight() / 2)
                        / (newContentBounds.getHeight() - viewportBounds.getHeight());
                scrollPane.setVvalue(Math.max(0, Math.min(1, newVValue)));
            });

            updateStatusLabel(String.format("Zoom: %.0f%%", currentZoom * 100));
        }
    }




    private double calculateFitToPageZoom(double imageWidth, double imageHeight) {
        if (scrollPane == null) {
            return Constants.DEFAULT_ZOOM;
        }
    }

    // ==================== Highlight Operations ====================

    @FXML
    private void handleHighlight() {
        highlightModeActive = !highlightModeActive;

        if (highlightModeActive) {
            uiStateManager.updateStatus("Highlight mode: Active - Click and drag to highlight");
            pageRenderer.setHighlightModeActive(true);
            setAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.HIGHLIGHT);
        } else {
            uiStateManager.updateStatus("Highlight mode: Disabled");
            pageRenderer.setHighlightModeActive(false);
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

    // ==================== Fullscreen Operations ====================

    @FXML
    private void handleToggleFullScreen() {
        fullscreenManager.toggleFullScreen();
    }

    // ==================== Search Operations ====================

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
            uiStateManager.showError("No PDF Loaded", "Please open a PDF file first");
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

                searchDialogStage.setOnCloseRequest(e -> searchDialogController.cleanup());
            } else {
                searchDialogController.setPDFDocument(currentDocument);
            }
            if (pageRenderer != null) {
                pageRenderer.clearCache();
                pageRenderer.cancelAllPendingRenders();
                pageRenderer.setZoom(zoomManager.getCurrentZoom());
            }
            renderCurrentPage();
            searchDialogStage.show();
            searchDialogStage.toFront();

            logger.info("Search dialog opened");

        } catch (IOException e) {
            logger.error("Error loading search dialog", e);
            uiStateManager.showError("Error", "Could not open search dialog: " + e.getMessage());
        }
    }

    public void highlightSearchResults(List<SearchResult> results) {
        searchManager.showResults(results);
    }

    public void highlightSearchResult(SearchResult result) {
        searchManager.navigateToResult(result);
    }

    // ==================== About Dialog ====================

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

    // ==================== Page Rendering ====================

    /**
     * Renders all pages of the current document in continuous scroll mode.
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

            // Thay vì clear toàn bộ contentPane, chỉ xóa bên trong pagesContainer
            pagesContainer.getChildren().clear();

            int totalPages = currentDocument.getTotalPages();
            Image firstPage = pdfService.renderPage(currentDocument, 0, (float) zoomManager.getCurrentZoom());
            double pageWidth = firstPage.getWidth();
            double pageHeight = firstPage.getHeight();

            for (int i = 0; i < totalPages; i++) {
                VBox pageBox = pageRenderer.createPagePlaceholder(i, pageWidth, pageHeight);
                pagesContainer.getChildren().add(pageBox);
            }

            Platform.runLater(this::loadVisiblePages);

        } catch (IOException e) {
            logger.error("Error rendering page", e);
            uiStateManager.showError("Rendering Error", "Could not render the page: " + e.getMessage());
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


    public BorderPane getRootPane() { return rootPane; }
    public ScrollPane getScrollPane() { return scrollPane; }
    public VBox getPagesContainer() { return pagesContainer; }
    public PDFDocument getCurrentDocument() { return currentDocument; }
    public double getCurrentZoom() { return zoomManager != null ? zoomManager.getCurrentZoom() : Constants.DEFAULT_ZOOM; }
    public boolean isHighlightModeActive() { return highlightModeActive; }
    
    public int getTotalPages() {
        return currentDocument != null ? currentDocument.getTotalPages() : 0;
    }

    /**
     * Updates page info (called by NavigationHelper).
     */
    public void updatePageInfo() {
        if (currentDocument != null) {
            pageInfoManager.updatePageInfo(currentDocument);
        }
    }

    /**
     * Shows an error dialog (for external classes like SearchManager).
     */
    public void showError(String title, String message) {
        uiStateManager.showError(title, message);
    }
}
