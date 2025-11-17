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
    @FXML
    private ColorPicker colorPicker;
    @FXML
    private Slider strokeWidthSlider;
    @FXML
    private ToggleGroup drawingToolsGroup;
    @FXML
    private ToggleButton btnDrawRect;
    @FXML
    private ToggleButton btnDrawCircle;
    @FXML
    private ToggleButton btnDrawArrow;


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
        if (drawingToolsGroup != null) {
            // Listener lắng nghe sự thay đổi trạng thái của ToggleGroup
            drawingToolsGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
                // Nếu không có nút nào được chọn (newVal == null), chuyển về View Mode
                if (newVal == null) {
                    updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.NONE);
                    return;
                }

                // Nếu một nút được chọn, ánh xạ nó sang chế độ vẽ tương ứng
                ToggleButton selectedBtn = (ToggleButton) newVal;


                // Ánh xạ công cụ
                if (selectedBtn == btnDrawRect) {
                    updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.RECTANGLE);
                } else if (selectedBtn == btnDrawCircle) {
                    updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.CIRCLE);
                } else if (selectedBtn == btnDrawArrow) {
                    updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.ARROW);
                }
            });
        }

        if (colorPicker != null) {
            colorPicker.setValue(javafx.scene.paint.Color.BLACK);
        }
        // Listener của Slider và ColorPicker (Giữ nguyên)
        if (strokeWidthSlider != null) {
            strokeWidthSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                updateDrawingStyleForAllPages();
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
                    // 1. Lưu thông tin cần thiết
                    File currentFile = currentDocument.getFile();
                    double oldZoom = zoomManager.getCurrentZoom();

                    // 2. Xóa trang TRƯỚC KHI save
                    fileManager.deletePages(currentDocument, java.util.List.of(current));

                    // 3. Save document (sử dụng save thông thường, không dùng incremental)
                    pdfService.save(currentDocument);

                    // 4. CRITICAL: Đóng document cũ ĐỂ giải phóng file lock
                    pdfService.closePDF(currentDocument);

                    // 5. Clear TOÀN BỘ state
                    contentPane.getChildren().clear();
                    pagesContainer = null;
                    loadingPages.clear();

                    // 6. Clear cache và hủy tất cả render đang chờ
                    pageRenderer.clearCache();
                    pageRenderer.cancelAllPendingRenders();

                    // 7. Tạo MỚI PageRenderer và ScrollHandler
                    pageRenderer = new PageRenderer(pdfService, renderExecutor);
                    scrollHandler = new ScrollHandler(pageRenderer, scrollPane);

                    // 8. Mở LẠI file (để PDFBox load lại cấu trúc mới)
                    currentDocument = fileManager.openFile(currentFile);
                    if (currentDocument == null) {
                        uiStateManager.showError("Error", "Could not reopen the file after deletion.");
                        return;
                    }

                    // 9. Tính current page mới
                    int newTotal = currentDocument.getTotalPages();
                    int newCurrentPage = (current >= newTotal) ? Math.max(0, newTotal - 1) : current;
                    currentDocument.setCurrentPage(newCurrentPage);
                    currentDocument.setZoomLevel(oldZoom);

                    // 10. Cập nhật renderer với document mới
                    pageRenderer.setDocument(currentDocument, oldZoom);
                    zoomManager.setDocument(currentDocument);
                    zoomManager.setCurrentZoom(oldZoom);

                    // 11. Tạo lại RenderingManager
                    renderingManager = new RenderingManager(pdfService, pageRenderer, scrollHandler, zoomManager);
                    renderingManager.setDocument(currentDocument);
                    renderingManager.setUIComponents(null, scrollPane, contentPane);

                    // 12. CRITICAL: Set document cho ScrollHandler SAU KHI render
                    // (chờ pagesContainer được tạo)
                    renderingManager.renderAllPages();
                    pagesContainer = renderingManager.getPagesContainer();

                    // 13. Set document cho ScrollHandler với pagesContainer HỢP LỆ
                    scrollHandler.setDocument(currentDocument, pagesContainer);

                    // 14. Cập nhật UI
                    pageInfoManager.updatePageInfo(currentDocument);

                    // 15. Scroll về đầu và trigger render - CHỈ dùng 1 lớp runLater
                    Platform.runLater(() -> {
                        // Reset scroll position
                        scrollPane.setVvalue(0);
                        currentDocument.setCurrentPage(0);

                        // Clear loading pages trước khi trigger scroll
                        loadingPages.clear();

                        // Trigger scroll handler để load các trang cần thiết
                        scrollHandler.handleScroll();

                        // Update UI
                        pageInfoManager.updatePageInfo(currentDocument);
                        uiStateManager.updateStatus(
                                "Deleted page " + (current + 1) + ". Total pages: " + newTotal
                        );
                    });

                    logger.info("Successfully deleted page {} and reloaded document", current + 1);

                } catch (Exception e) {
                    logger.error("Error deleting page {}", current + 1, e);
                    uiStateManager.showError("Delete Page Error", "Could not delete the page: " + e.getMessage());

                    // Recovery: thử mở lại file gốc
                    try {
                        if (currentDocument != null && currentDocument.getFile() != null) {
                            openPDFFile(currentDocument.getFile());
                        }
                    } catch (Exception ex) {
                        logger.error("Failed to recover after delete error", ex);
                    }
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
            // Tắt nhóm vẽ hình khi bật Highlight
            if (drawingToolsGroup != null) {
                drawingToolsGroup.selectToggle(null);
            }

            uiStateManager.updateStatus("Highlight mode: Active - Click and drag to highlight");
            pageRenderer.setHighlightModeActive(true);
            updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.HIGHLIGHT);
        } else {
            // Tắt Highlight
            uiStateManager.updateStatus("Highlight mode: Disabled");
            pageRenderer.setHighlightModeActive(false);
            updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.NONE);
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

    // ==================== Rotation Operations ====================

    @FXML
    private void handleRotateLeft() {
        rotateDocument(-90);
    }

    @FXML
    private void handleRotateRight() {
        rotateDocument(90);
    }

    private void rotateDocument(int angle) {
        if (currentDocument == null) return;

        // 1. Tính góc xoay mới
        int currentRot = currentDocument.getRotation();
        currentDocument.setRotation(currentRot + angle);

        // 2. Render lại màn hình
        // Hàm này sẽ gọi renderPage -> renderPage thấy cache trống (do bước 1 đã clear) -> vẽ lại ảnh xoay
        if (renderingManager != null && zoomManager != null) {
            renderingManager.preserveScrollPositionAndApplyZoom(zoomManager.getCurrentZoom());
        }

        uiStateManager.updateStatus("Rotated document " + (angle > 0 ? "Right" : "Left"));
    }

    @FXML
    private void handleExtractPages() {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF Loaded",
                    "Please open a PDF file first before extracting pages.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/extract-dialog.fxml"));
            Parent root = loader.load();

            ExtractDialogController controller = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Extract PDF Pages");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(rootPane.getScene().getWindow());
            dialogStage.setScene(new Scene(root));

            controller.setDialogStage(dialogStage);
            controller.setSourceFile(currentDocument.getFile());

            dialogStage.setOnCloseRequest(event -> controller.shutdown());
            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Error opening extract dialog", e);
            uiStateManager.showError("Error", "Could not open extract dialog: " + e.getMessage());
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

    // ==================== Drawing Operations ====================

    private void updateAnnotationMode(AnnotationLayer.AnnotationMode mode) {
        if (pagesContainer == null) return;
        pagesContainer.getChildren().forEach(node -> {
            if (node instanceof javafx.scene.layout.VBox pageBox) {
                if (!pageBox.getChildren().isEmpty() && pageBox.getChildren().get(0) instanceof javafx.scene.layout.StackPane stack) {
                    stack.getChildren().stream()
                            .filter(child -> child instanceof AnnotationLayer)
                            .map(child -> (AnnotationLayer) child)
                            .forEach(layer -> {
                                layer.setAnnotationMode(mode);
                                // Nếu vẽ hình thì chọn màu Đỏ, Highlight thì màu Vàng
                                if (mode != AnnotationLayer.AnnotationMode.NONE && mode != AnnotationLayer.AnnotationMode.HIGHLIGHT) {
                                    layer.setDrawingColor(javafx.scene.paint.Color.RED);
                                }
                            });
                }
            }
        });
        uiStateManager.updateStatus("Tool: " + mode);
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

    // ==================== Drawing Operations ====================

    @FXML
    private void handleDrawRect() {
        updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.RECTANGLE);
    }

    @FXML
    private void handleDrawCircle() {
        updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.CIRCLE);
    }

    @FXML
    private void handleDrawArrow() {
        updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.ARROW);
    }

    @FXML
    private void handleColorChange() {
        updateDrawingStyleForAllPages();
    }
    private void updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode mode) {
        if (pagesContainer == null) return;

        processAllAnnotationLayers(layer -> layer.setAnnotationMode(mode));

        uiStateManager.updateStatus("Tool: " + mode);
    }
    private void updateDrawingStyleForAllPages() {
        if (pagesContainer == null || colorPicker == null || strokeWidthSlider == null) return;

        javafx.scene.paint.Color color = colorPicker.getValue();
        double width = strokeWidthSlider.getValue();

        processAllAnnotationLayers(layer -> {
            layer.setDrawingColor(color);
            layer.setLineWidth(width);
        });
    }
    private void processAllAnnotationLayers(java.util.function.Consumer<AnnotationLayer> action) {
        pagesContainer.getChildren().forEach(node -> {
            if (node instanceof VBox pageBox && !pageBox.getChildren().isEmpty()) {
                if (pageBox.getChildren().get(0) instanceof StackPane stack) {
                    stack.getChildren().stream()
                            .filter(child -> child instanceof AnnotationLayer)
                            .map(child -> (AnnotationLayer) child)
                            .forEach(action);
                }
            }
        });
    }
}
