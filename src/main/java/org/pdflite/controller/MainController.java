package org.pdflite.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.pdflite.manager.*;
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
 * This controller coordinates all PDF viewing operations by delegating to
 * specialized managers.
 * It handles file operations, page navigation, zoom operations, continuous
 * scrolling view with
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
    private ToolBar toolbar;
    @FXML
    private Button prevButton;
    @FXML
    private Button nextButton;
    @FXML
    private Menu recentFilesMenu;

    // ==================== Services and Managers ====================

    private PDFService pdfService;
    private NavigationHelper navigationHelper;
    private PageRenderer pageRenderer;
    private ScrollHandler scrollHandler;

    // Managers
    private SearchManager searchManager;
    private ZoomManager zoomManager;
    private FileManager fileManager;
    private FullscreenManager fullscreenManager;
    private UIStateManager uiStateManager;
    private PageInfoManager pageInfoManager;
    private RenderingManager renderingManager;
    private SearchDialogManager searchDialogManager;
    private ThemeManager themeManager;
    private RecentFilesManager recentFilesManager;

    // ==================== Document State ====================

    private PDFDocument currentDocument;
    private VBox pagesContainer;
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(6);
    private final java.util.Set<Integer> loadingPages = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private boolean highlightModeActive = false;

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

        // Theme
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                themeManager = new ThemeManager(newScene);
            }
        });

        // Initialize managers
        initializeManagers();

        // Initialize recent files manager
        recentFilesManager = new RecentFilesManager();
        updateRecentFilesMenu();

        // Setup rendering manager with UI components
        if (renderingManager != null) {
            renderingManager.setUIComponents(pagesContainer, scrollPane, contentPane);
        }

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
        // UI State Manager (needed by other managers)
        uiStateManager = new UIStateManager(statusLabel, prevButton, nextButton, pageNumberField, zoomComboBox);

        // Zoom Manager
        zoomManager = new ZoomManager(pdfService, createZoomChangeListener());
        zoomManager.initialize(zoomComboBox, scrollPane);

        // Rendering Manager
        renderingManager = new RenderingManager(pdfService, pageRenderer, scrollHandler, zoomManager);

        // File Manager
        fileManager = new FileManager(pdfService, createFileOperationListener());

        // Fullscreen Manager
        fullscreenManager = new FullscreenManager(rootPane, toolbar, createFullscreenListener());

        // Page Info Manager
        pageInfoManager = new PageInfoManager(totalPagesLabel, pageNumberField, prevButton, nextButton);

        // Search Dialog Manager
        searchDialogManager = new SearchDialogManager(rootPane, pageRenderer, zoomManager, renderingManager,
                uiStateManager);
    }

    /**
     * Creates the zoom change listener.
     */
    private ZoomManager.ZoomChangeListener createZoomChangeListener() {
        return new ZoomManager.ZoomChangeListener() {
            @Override
            public void onZoomChanged(double newZoom) {
                if (currentDocument != null && pagesContainer != null && scrollPane != null) {
                    renderingManager.preserveScrollPositionAndApplyZoom(newZoom);
                    Platform.runLater(() -> searchManager.updateHighlightsAfterZoom(newZoom));
                }
            }

            @Override
            public void onZoomApplied(double newZoom, String statusMessage) {
                uiStateManager.updateStatus(statusMessage);
            }
        };
    }

    /**
     * Creates the file operation listener.
     */
    private FileManager.FileOperationListener createFileOperationListener() {
        return new FileManager.FileOperationListener() {
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
        };
    }

    /**
     * Creates the fullscreen listener.
     */
    private FullscreenManager.FullscreenListener createFullscreenListener() {
        return new FullscreenManager.FullscreenListener() {
            @Override
            public void onFullscreenChanged(boolean isFullscreen) {
                // Fullscreen state changed
            }

            @Override
            public void updateStatus(String message) {
                uiStateManager.updateStatus(message);
            }
        };
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
            renderingManager.setDocument(currentDocument);

            // Update UI
            uiStateManager.updateUIState(true);
            renderingManager.renderAllPages();
            pagesContainer = renderingManager.getPagesContainer();
            pageInfoManager.updatePageInfo(currentDocument);
            uiStateManager.updateStatus("Opened: " + file.getName());

            // Add to recent files
            recentFilesManager.addRecentFile(file.getAbsolutePath());
            updateRecentFilesMenu();

            logger.info("Successfully opened PDF: {}", file.getName());
        } catch (IOException e) {
            logger.error("Error opening PDF file", e);
            uiStateManager.showError("Error Opening PDF", "Could not open the PDF file: " + e.getMessage());
        }
    }

    @FXML
    private void handleSave() {
        if (currentDocument == null)
            return;
        try {
            fileManager.save(currentDocument);
        } catch (IOException e) {
            logger.error("Error saving document", e);
            uiStateManager.showError("Save Error", "Could not save the document: " + e.getMessage());
        }
    }

    @FXML
    private void handleSaveAs() {
        if (currentDocument == null)
            return;
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
        performExit();
    }

    public void performExit() {
        if (currentDocument != null) {
            fileManager.close(currentDocument);
        }

        // Shutdown executor service to prevent app from running in background
        if (!renderExecutor.isShutdown()) {
            renderExecutor.shutdownNow();
        }

        Platform.exit();
        System.exit(0);
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
                    // Lưu vị trí scroll trước khi xóa
                    double oldVValue = scrollPane.getVvalue();

                    // Xóa trang trong document
                    fileManager.deletePages(currentDocument, java.util.List.of(current));

                    // XÓA TRANG KHỎI UI (không render lại)
                    if (pagesContainer != null && pagesContainer.getChildren().size() > current) {
                        // Xóa VBox của trang đã chọn khỏi pagesContainer
                        pagesContainer.getChildren().remove(current);

                        // Cập nhật lại ID của các trang sau trang bị xóa
                        for (int i = current; i < pagesContainer.getChildren().size(); i++) {
                            if (pagesContainer.getChildren().get(i) instanceof VBox pageBox) {
                                final int pageIndex = i; // Tạo biến final để dùng trong lambda
                                pageBox.setId("page-" + pageIndex);

                                // Cập nhật label page number
                                pageBox.getChildren().forEach(child -> {
                                    if (child instanceof Label label) {
                                        label.setText("Page " + (pageIndex + 1));
                                    }
                                });
                            }
                        }
                    }

                    // Xóa trang khỏi cache của pageRenderer
                    pageRenderer.clearCache();

                    // Xóa khỏi loadingPages set
                    loadingPages.clear();

                    // Cập nhật currentPage nếu cần
                    int newTotal = currentDocument.getTotalPages();
                    if (current >= newTotal) {
                        currentDocument.setCurrentPage(Math.max(0, newTotal - 1));
                    } else {
                        currentDocument.setCurrentPage(current);
                    }

                    // Cập nhật UI info
                    pageInfoManager.updatePageInfo(currentDocument);
                    uiStateManager.updateStatus("Deleted page " + (current + 1));

                    // Khôi phục vị trí scroll
                    Platform.runLater(() -> {
                        scrollPane.setVvalue(oldVValue);
                        // Trigger scroll handler để load các trang visible nếu cần
                        scrollHandler.handleScroll();
                    });

                    logger.info("Successfully deleted page {} without full re-render", current + 1);

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

    @FXML
    private void handleNextPage() {
        if (currentDocument != null
                && currentDocument.getCurrentPage() < currentDocument.getTotalPages() - 1) {
            navigationHelper.navigateToPage(currentDocument.getCurrentPage() + 1);
        }
    }

    @FXML
    private void handleGoToPage() {
        int pageNum = pageInfoManager.getPageNumberFromField();
        if (pageNum > 0) {
            navigationHelper.jumpToPage(pageNum);
        } else {
            uiStateManager.showError("Invalid Input", "Please enter a valid page number");
            pageInfoManager.resetPageFieldToCurrentPage(currentDocument);
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

    @FXML
    private void setLightTheme() {
        themeManager.setLightTheme();
    }

    @FXML
    private void setDarkTheme() {
        themeManager.setDarkTheme();
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
        searchDialogManager.openSearchDialog(currentDocument, this);
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

    // ==================== Merge and Split Operations ====================

    @FXML
    private void handleMergePDFs() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/merge-dialog.fxml"));
            Parent root = loader.load();

            MergeDialogController controller = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Merge PDF Files");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(rootPane.getScene().getWindow());
            dialogStage.setScene(new Scene(root));

            controller.setDialogStage(dialogStage);

            dialogStage.setOnCloseRequest(event -> controller.shutdown());
            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Error opening merge dialog", e);
            uiStateManager.showError("Error", "Could not open merge dialog: " + e.getMessage());
        }
    }

    @FXML
    private void handleSplitPDF() {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF Loaded",
                    "Please open a PDF file first before splitting.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/split-dialog.fxml"));
            Parent root = loader.load();

            SplitDialogController controller = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Split PDF File");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(rootPane.getScene().getWindow());
            dialogStage.setScene(new Scene(root));

            controller.setDialogStage(dialogStage);
            controller.setSourceFile(currentDocument.getFile());

            dialogStage.setOnCloseRequest(event -> controller.shutdown());
            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Error opening split dialog", e);
            uiStateManager.showError("Error", "Could not open split dialog: " + e.getMessage());
        }
    }

    public BorderPane getRootPane() {
        return rootPane;
    }

    public ScrollPane getScrollPane() {
        return scrollPane;
    }

    public VBox getPagesContainer() {
        return pagesContainer;
    }

    public PDFDocument getCurrentDocument() {
        return currentDocument;
    }

    public double getCurrentZoom() {
        return zoomManager != null ? zoomManager.getCurrentZoom() : Constants.DEFAULT_ZOOM;
    }

    public boolean isHighlightModeActive() {
        return highlightModeActive;
    }

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

    private void updateRecentFilesMenu() {
        if (recentFilesMenu == null) {
            return;
        }

        recentFilesMenu.getItems().clear();
        List<String> recentFiles = recentFilesManager.getRecentFiles();

        if (recentFiles.isEmpty()) {
            MenuItem noFiles = new MenuItem("No recent files");
            noFiles.setDisable(true);
            recentFilesMenu.getItems().add(noFiles);
        } else {
            for (String filePath : recentFiles) {
                File file = new File(filePath);
                MenuItem item = new MenuItem(file.getName());
                item.setOnAction(e -> openPDFFile(file));
                recentFilesMenu.getItems().add(item);
            }
        }
    }

    @FXML
    private void handleClearRecentFiles() {
        recentFilesManager.clearRecentFiles();
        updateRecentFilesMenu();
        uiStateManager.updateStatus("Recent files cleared");
    }

    public void openLastFile() {
        String lastFile = recentFilesManager.getLastOpenedFile();
        if (lastFile != null) {
            File file = new File(lastFile);
            if (file.exists()) {
                openPDFFile(file);
            }
        }
    }
}
