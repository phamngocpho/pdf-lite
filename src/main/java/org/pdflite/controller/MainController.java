// src/main/java/org/pdflite/controller/MainController.java

package org.pdflite.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections; // Thêm import
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color; // Thêm import
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.pdflite.manager.*;
import org.pdflite.model.DrawingTool; // Thêm import
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
import javafx.scene.control.TextFormatter;

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
    @FXML private Button rotateLeftButton;
    @FXML private Button rotateRightButton;
    // === CÁC ĐIỀU KHIỂN VẼ MỚI ===
    @FXML private ComboBox<DrawingTool> drawingToolComboBox;
    @FXML private ColorPicker colorPicker;
    @FXML private Slider lineWidthSlider;

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

    // === THÊM DRAWING MANAGER ===
    private DrawingManager drawingManager;

    // ==================== Document State ====================
    private PDFDocument currentDocument;
    private VBox pagesContainer;
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(6);
    private final java.util.Set<Integer> loadingPages = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // Giữ nguyên logic 'highlightModeActive' cho tính năng highlight cũ
    private boolean highlightModeActive = false;

    // ==================== Initialization ====================
    @FXML
    public void initialize() {
        logger.info("Initializing MainController");
        pdfService = new PDFService();

        // === KHỞI TẠO DRAWING MANAGER (CHỈ DÙNG CHO HÌNH DẠNG MỚI) ===
        drawingManager = new DrawingManager();

        // (Code khởi tạo khác của bạn...)
        pageRenderer = new PageRenderer(pdfService, renderExecutor);
        scrollHandler = new ScrollHandler(pageRenderer, scrollPane);
        navigationHelper = new NavigationHelper(this, pdfService, renderExecutor, loadingPages);
        searchManager = new SearchManager(this, navigationHelper);
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                themeManager = new ThemeManager(newScene);
            }
        });

        initializeManagers();

        if (renderingManager != null) {
            renderingManager.setUIComponents(pagesContainer, scrollPane, contentPane);
        }

        if (pageNumberField != null) {
            pageNumberField.setOnAction(e -> handleGoToPage());
            pageNumberField.setTextFormatter(new TextFormatter<>(change -> {
                String next = change.getControlNewText();
                return next.matches("\\d*") ? change : null;
            }));
        }

        if (scrollPane != null) {
            scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
                scrollHandler.handleScroll();
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

        // === CÀI ĐẶT CÁC ĐIỀU KHIỂN VẼ MỚI ===
        setupDrawingControls();

        uiStateManager.updateUIState(false);
    }

    /**
     * Cài đặt listeners cho các công cụ vẽ mới.
     */
    private void setupDrawingControls() {
        // 1. Khởi tạo ComboBox
        if (drawingToolComboBox != null) {
            // Lấy tất cả giá trị từ Enum, trừ HIGHLIGHT (vì bạn muốn tách riêng)
            drawingToolComboBox.setItems(FXCollections.observableArrayList(
                    DrawingTool.NONE,
                    DrawingTool.RECTANGLE,
                    DrawingTool.CIRCLE,
                    DrawingTool.ARROW
            ));
            drawingToolComboBox.setValue(DrawingTool.NONE); // Đặt giá trị mặc định

            // 2. Listener cho ComboBox
            drawingToolComboBox.valueProperty().addListener((obs, oldTool, newTool) -> {
                if (newTool != null && newTool != DrawingTool.NONE) {
                    // Nếu chọn 1 công cụ (Rect, Circle, Arrow)
                    disableHighlightMode(); // Tắt chế độ highlight
                    drawingManager.setCurrentTool(newTool);
                    setAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.SHAPE);
                    uiStateManager.updateStatus("Draw tool selected: " + newTool);
                } else {
                    // Nếu chọn "NONE"
                    drawingManager.setCurrentTool(DrawingTool.NONE);
                    // Chỉ tắt chế độ SHAPE, không ảnh hưởng HIGHLIGHT
                    if (!highlightModeActive) {
                        setAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.NONE);
                    }
                    uiStateManager.updateStatus("Drawing tool deselected.");
                }
            });
        }

        // 3. Listener cho ColorPicker
        if (colorPicker != null) {
            // Đặt màu mặc định trong manager
            drawingManager.setCurrentColor(colorPicker.getValue());
            // Thêm listener
            colorPicker.setOnAction(e -> drawingManager.setCurrentColor(colorPicker.getValue()));
        }

        // 4. Listener cho Slider
        if (lineWidthSlider != null) {
            // Đặt độ rộng mặc định trong manager
            drawingManager.setCurrentLineWidth(lineWidthSlider.getValue());
            // Thêm listener
            lineWidthSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                drawingManager.setCurrentLineWidth(newVal.doubleValue());
            });
        }
    }

    /**
     * Khởi tạo tất cả manager (Code gốc của bạn)
     */
    private void initializeManagers() {
        uiStateManager = new UIStateManager(statusLabel, prevButton, nextButton, pageNumberField, zoomComboBox);
        zoomManager = new ZoomManager(pdfService, createZoomChangeListener());
        zoomManager.initialize(zoomComboBox, scrollPane);
        renderingManager = new RenderingManager(pdfService, pageRenderer, scrollHandler, zoomManager);
        fileManager = new FileManager(pdfService, createFileOperationListener());
        fullscreenManager = new FullscreenManager(rootPane, toolbar, createFullscreenListener());
        pageInfoManager = new PageInfoManager(totalPagesLabel, pageNumberField, prevButton, nextButton);
        searchDialogManager = new SearchDialogManager(rootPane, pageRenderer, zoomManager, renderingManager, uiStateManager);
    }

    /**
     * (Code gốc của bạn)
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
     * (Code gốc của bạn)
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
     * (Code gốc của bạn)
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
                    fileManager.deletePages(currentDocument, java.util.List.of(current));
                    // Reset UI containers and re-render pages
                    if (contentPane != null) {
                        contentPane.getChildren().clear();
                    }
                    pagesContainer = null;
                    loadingPages.clear();
                    renderingManager.setUIComponents(null, scrollPane, contentPane);
                    renderingManager.renderAllPages();
                    pagesContainer = renderingManager.getPagesContainer();
                    pageInfoManager.updatePageInfo(currentDocument);
                } catch (Exception e) {
                    logger.error("Error deleting page {}", current + 1, e);
                    uiStateManager.showError("Delete Page Error", "Could not delete the page: " + e.getMessage());
                }
            }
        });
    }

    // ==================== Zoom Operations ====================
    @FXML private void handleZoomIn() { zoomManager.zoomIn(); }
    @FXML private void handleZoomOut() { zoomManager.zoomOut(); }
    @FXML private void handleZoomChange() { zoomManager.handleZoomComboBoxChange(); }
    @FXML private void handleFitToWidth() { zoomManager.fitToWidth(); }
    @FXML private void handleFitToPage() { zoomManager.fitToPage(); }

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

    // ==================== Drawing & Highlight Operations ====================

    /**
     * Xử lý logic Highlight (Code gốc của bạn - ĐÃ SỬA ĐỔI)
     * Thêm logic để tắt các công cụ vẽ hình dạng mới.
     */
    @FXML
    private void handleHighlight() {
        highlightModeActive = !highlightModeActive;

        if (highlightModeActive) {
            // Tắt các công cụ vẽ hình dạng mới
            if (drawingToolComboBox != null) {
                drawingToolComboBox.setValue(DrawingTool.NONE);
            }
            drawingManager.setCurrentTool(DrawingTool.NONE);

            // Kích hoạt chế độ Highlight (logic cũ của bạn)
            uiStateManager.updateStatus("Highlight mode: Active - Click and drag to highlight");
            pageRenderer.setHighlightModeActive(true);
            setAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.HIGHLIGHT);
        } else {
            // Tắt chế độ Highlight
            uiStateManager.updateStatus("Highlight mode: Disabled");
            pageRenderer.setHighlightModeActive(false);
            setAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.NONE);
        }
    }

    /**
     * Hàm trợ giúp: Tắt chế độ Highlight khi chọn công cụ vẽ mới.
     */
    private void disableHighlightMode() {
        if (highlightModeActive) {
            highlightModeActive = false;
            pageRenderer.setHighlightModeActive(false);
            uiStateManager.updateStatus("Highlight mode: Disabled");
        }
    }

    // ... (Hàm disableHighlightMode() của bạn) ...

// ==================== Rotation Operations (MỚI) ====================

    @FXML
    private void handleRotateLeft() {
        rotateCurrentPage(-90); // Xoay ngược chiều kim đồng hồ
    }

    @FXML
    private void handleRotateRight() {
        rotateCurrentPage(90); // Xoay thuận chiều kim đồng hồ
    }

    /**
     * Hàm trợ giúp để xoay trang hiện tại.
     */
    private void rotateCurrentPage(int degrees) {
        if (currentDocument == null || renderingManager == null || pdfService == null || scrollHandler == null) {
            return;
        }

        // Lấy trang hiện tại (từ logic scroll của bạn)
        int currentPageIndex = scrollHandler.getCurrentPageFromScroll();
        if (currentPageIndex < 0) {
            // Fallback về trang trong model nếu scroll handler chưa sẵn sàng
            currentPageIndex = currentDocument.getCurrentPage();
        }

        try {
            // 1. Thay đổi dữ liệu trong PDF (bộ nhớ)
            pdfService.rotatePage(currentDocument, currentPageIndex, degrees);

            // 2. Yêu cầu RenderingManager render lại trang đó trên UI
            renderingManager.rerenderPage(currentPageIndex);

            uiStateManager.updateStatus("Rotated page " + (currentPageIndex + 1));

        } catch (Exception e) {
            logger.error("Error during page rotation", e);
            uiStateManager.showError("Rotation Error", "Could not rotate page: " + e.getMessage());
        }
    }

// ==================== Theme, Fullscreen, Search, Etc. ====================
// ... (Các hàm setLightTheme, setDarkTheme, v.v. của bạn) ...

    // ==================== Theme Operations ====================
    @FXML private void setLightTheme() { themeManager.setLightTheme(); }
    @FXML private void setDarkTheme() { themeManager.setDarkTheme(); }

    /**
     * Cập nhật `setAnnotationModeForAllPages` (ĐÃ SỬA ĐỔI)
     * - Truyền DrawingManager xuống
     * - [QUAN TRỌNG] Truyền cả pageNumber xuống
     */
    private void setAnnotationModeForAllPages(AnnotationLayer.AnnotationMode mode) {
        if (pagesContainer != null) {
            // Chúng ta cần lấy page index (i)
            for (int i = 0; i < pagesContainer.getChildren().size(); i++) {
                javafx.scene.Node node = pagesContainer.getChildren().get(i);
                if (node instanceof VBox pageBox) {
                    final int pageIndex = i; // Page number (0-based)
                    pageBox.getChildren().forEach(child -> {
                        if (child instanceof StackPane stackPane) {
                            stackPane.getChildren().forEach(stackChild -> {
                                if (stackChild instanceof AnnotationLayer annotationLayer) {
                                    // Truyền các phụ thuộc cần thiết
                                    annotationLayer.setDrawingManager(drawingManager);
                                    annotationLayer.setPageNumber(pageIndex);
                                    annotationLayer.setAnnotationMode(mode);
                                }
                            });
                        }
                    });
                }
            }
        }
    }

    // ==================== Fullscreen Operations ====================
    @FXML private void handleToggleFullScreen() { fullscreenManager.toggleFullScreen(); }

    // ==================== Search Operations ====================
    @FXML private void handleSearch() { handleSearchDialog(); }
    @FXML private void handleSearchLeft() { searchManager.togglePanel(SearchManager.SearchPanelPosition.LEFT); }
    @FXML private void handleSearchRight() { searchManager.togglePanel(SearchManager.SearchPanelPosition.RIGHT); }
    @FXML private void handleHideSearch() { searchManager.clearSearch(); }
    public void handleSearchDialog() { searchDialogManager.openSearchDialog(currentDocument, this); }
    public void highlightSearchResults(List<SearchResult> results) { searchManager.showResults(results); }
    public void highlightSearchResult(SearchResult result) { searchManager.navigateToResult(result); }

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

        if (themeManager != null)
            themeManager.applyToDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    // ==================== Merge and Split Operations ====================
    @FXML
    private void handleMergePDFs() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/merge-dialog.fxml")
            );
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
                    getClass().getResource("/org/pdflite/split-dialog.fxml")
            );
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

    // ==================== Getters ====================
    public BorderPane getRootPane() { return rootPane; }
    public ScrollPane getScrollPane() { return scrollPane; }
    public VBox getPagesContainer() { return pagesContainer; }
    public PDFDocument getCurrentDocument() { return currentDocument; }
    public double getCurrentZoom() { return zoomManager != null ? zoomManager.getCurrentZoom() : Constants.DEFAULT_ZOOM; }
    public boolean isHighlightModeActive() { return highlightModeActive; }

    public int getTotalPages() {
        return currentDocument != null ? currentDocument.getTotalPages() : 0;
    }

    public void updatePageInfo() {
        if (currentDocument != null) {
            pageInfoManager.updatePageInfo(currentDocument);
        }
    }

    public void showError(String title, String message) {
        uiStateManager.showError(title, message);
    }
}