package org.pdflite.controller;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.pdflite.dialog.EncryptionDialog;
import org.pdflite.manager.FileManager;
import org.pdflite.manager.FullscreenManager;
import org.pdflite.manager.PageInfoManager;
import org.pdflite.manager.RecentFilesManager;
import org.pdflite.manager.RenderingManager;
import org.pdflite.manager.SearchDialogManager;
import org.pdflite.manager.SearchManager;
import org.pdflite.manager.ThemeManager;
import org.pdflite.manager.UIStateManager;
import org.pdflite.manager.ZoomManager;
import org.pdflite.model.PDFDocument;
import org.pdflite.model.SearchResult;
import org.pdflite.service.PDFPrintService;
import org.pdflite.service.PDFService;
import org.pdflite.util.Constants;
import org.pdflite.util.NavigationHelper;
import org.pdflite.view.AnnotationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

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
    @FXML
    private ToggleButton btnSelectText;


    // ==================== Services and Managers ====================

    private PDFService pdfService;
    private PDFPrintService printService;
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
        printService = new PDFPrintService(pdfService);

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
        
        // Set page change listener to update UI when page changes during scroll
        // Must be after initializeManagers() so pageInfoManager is initialized
        scrollHandler.setPageChangeListener(newPageIndex -> Platform.runLater(() -> {
            if (currentDocument != null) {
                // The scroll handler already updated currentDocument.setCurrentPage(newPageIndex)
                // Just update the UI
                pageInfoManager.updatePageInfo(currentDocument);
            }
        }));

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
            scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> scrollHandler.handleScroll());
        }
        if (drawingToolsGroup != null) {
            drawingToolsGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {

                if (newVal == null) {
                    updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.NONE);
                    if (pageRenderer != null) pageRenderer.setSelectionModeActive(pagesContainer, false);
                    return;
                }

                ToggleButton selectedBtn = (ToggleButton) newVal;

                if (selectedBtn == btnSelectText) {
                    // Tắt vẽ
                    updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.NONE);
                    // Bật chọn Text
                    if (pageRenderer != null) pageRenderer.setSelectionModeActive(pagesContainer, true);

                    uiStateManager.updateStatus("Tool: Text Selection");
                } else {
                    if (pageRenderer != null) pageRenderer.setSelectionModeActive(pagesContainer, false);


                    // Ánh xạ công cụ
                    if (selectedBtn == btnDrawRect) {
                        updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.RECTANGLE);
                    } else if (selectedBtn == btnDrawCircle) {
                        updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.CIRCLE);
                    } else if (selectedBtn == btnDrawArrow) {
                        updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.ARROW);
                    }
                    updateDrawingStyleForAllPages();
                }
            });
        }
            if (btnSelectText != null) makeToggleButtonDeselectable(btnSelectText);
            if (btnDrawRect != null) makeToggleButtonDeselectable(btnDrawRect);
            if (btnDrawCircle != null) makeToggleButtonDeselectable(btnDrawCircle);
            if (btnDrawArrow != null) makeToggleButtonDeselectable(btnDrawArrow);

        if (colorPicker != null) {
            colorPicker.setValue(javafx.scene.paint.Color.BLACK);
        }

        if (colorPicker != null) {
            colorPicker.setValue(javafx.scene.paint.Color.BLACK);

            colorPicker.setOnAction(e -> updateDrawingStyleForAllPages());
        }

        if (strokeWidthSlider != null) {
            strokeWidthSlider.valueProperty().addListener((obs, oldVal, newVal) -> updateDrawingStyleForAllPages());
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
                    // Switch layout mode based on threshold (70% => 0.7)
                    try {
                        if (renderingManager != null) {
                            boolean shouldTwoPage = newZoom < 0.7;
                            renderingManager.setTwoPageMode(shouldTwoPage);
                        }
                    } catch (Exception e) {
                        logger.error("Error switching page layout mode", e);
                    }

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

            // CRITICAL: Reset to page 1 (index 0) when opening a new file
            // This ensures we don't try to open at a page that doesn't exist in the new file
            currentDocument.setCurrentPage(0);

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
            
            // Scroll to top (page 1) to ensure we're viewing the first page
            Platform.runLater(() -> {
                if (scrollPane != null && pagesContainer != null) {
                    scrollPane.setVvalue(0.0);
                }
            });
            
            uiStateManager.updateStatus("Opened: " + file.getName());

            // Add to recent files
            recentFilesManager.addRecentFile(file.getAbsolutePath());
            updateRecentFilesMenu();

            logger.info("Successfully opened PDF: {} ({} pages, starting at page 1)", 
                    file.getName(), currentDocument.getTotalPages());
        } catch (IOException e) {
            logger.error("Error opening PDF file", e);
            uiStateManager.showError("Error Opening PDF", "Could not open the PDF file: " + e.getMessage());
        }
    }

    @FXML
    private void handleSave() {
        if (currentDocument == null)
            return;

        // Check if document is encrypted
        if (currentDocument.getDocument().isEncrypted()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Lưu file đã mã hóa");
            alert.setHeaderText("File PDF này có mật khẩu bảo vệ");
            alert.setContentText("""
                    Bạn muốn:
                    - Lưu và GIỮ mật khẩu (chọn Cancel và dùng 'Save As')
                    - Lưu và XÓA mật khẩu (chọn OK)""");

            ButtonType keepPassword = new ButtonType("Giữ mật khẩu", ButtonBar.ButtonData.CANCEL_CLOSE);
            ButtonType removePassword = new ButtonType("Xóa mật khẩu", ButtonBar.ButtonData.OK_DONE);
            alert.getButtonTypes().setAll(removePassword, keepPassword);

            if (themeManager != null) {
                themeManager.applyThemeToScene(alert.getDialogPane().getScene());
            }

            alert.showAndWait().ifPresent(response -> {
                if (response == removePassword) {
                    // User wants to remove password - proceed with save
                    try {
                        fileManager.save(currentDocument);
                        
                        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                        successAlert.setTitle("Thành công");
                        successAlert.setHeaderText("Đã lưu file");
                        successAlert.setContentText("File đã được lưu và mật khẩu đã được xóa.");
                        
                        if (themeManager != null) {
                            themeManager.applyThemeToScene(successAlert.getDialogPane().getScene());
                        }
                        
                        successAlert.showAndWait();
                    } catch (IOException e) {
                        logger.error("Error saving document", e);
                        uiStateManager.showError("Save Error", "Could not save the document: " + e.getMessage());
                    }
                } else {
                    // User wants to keep password - suggest Save As
                    Alert infoAlert = new Alert(Alert.AlertType.INFORMATION);
                    infoAlert.setTitle("Thông tin");
                    infoAlert.setHeaderText("Sử dụng Save As");
                    infoAlert.setContentText("Để giữ mật khẩu, vui lòng sử dụng chức năng 'Save As'\n" +
                            "hoặc chức năng 'Encrypt PDF' để đặt lại mật khẩu mới.");
                    
                    if (themeManager != null) {
                        themeManager.applyThemeToScene(infoAlert.getDialogPane().getScene());
                    }
                    
                    infoAlert.showAndWait();
                }
            });
        } else {
            // Normal save for non-encrypted documents
            try {
                fileManager.save(currentDocument);
            } catch (IOException e) {
                logger.error("Error saving document", e);
                uiStateManager.showError("Save Error", "Could not save the document: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleSaveAs() {
        if (currentDocument == null)
            return;

        // Warn user if document is encrypted
        if (currentDocument.getDocument().isEncrypted()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Cảnh báo");
            alert.setHeaderText("File có mật khẩu bảo vệ");
            alert.setContentText("""
                    Lưu ý: File mới sẽ KHÔNG CÓ MẬT KHẨU.
                    
                    Nếu muốn giữ mật khẩu hoặc đặt mật khẩu mới,
                    vui lòng sử dụng chức năng 'Encrypt PDF' sau khi lưu.""");

            ButtonType continueButton = new ButtonType("Tiếp tục lưu", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelButton = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(continueButton, cancelButton);

            if (themeManager != null) {
                themeManager.applyThemeToScene(alert.getDialogPane().getScene());
            }

            var result = alert.showAndWait();
            if (result.isEmpty() || result.get() != continueButton) {
                return; // User cancelled
            }
        }

        try {
            Stage stage = (Stage) rootPane.getScene().getWindow();
            fileManager.saveAs(currentDocument, stage);
        } catch (IOException e) {
            logger.error("Error saving document as", e);
            uiStateManager.showError("Save As Error", "Could not save the document: " + e.getMessage());
        }
    }

    @FXML
    private void handlePrint() {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF Loaded", "Please open a PDF file first before printing.");
            return;
        }

        // Check if printing is available
        if (!printService.isPrintingAvailable()) {
            uiStateManager.showError("No Printer Available", 
                    "No printer is available on this system. Please install a printer and try again.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/print-dialog.fxml"));
            Parent root = loader.load();

            PrintDialogController controller = loader.getController();
            Stage dialogStage = createDialogStage(root, "Print PDF");

            controller.setDialogStage(dialogStage);
            controller.setDocument(currentDocument, printService, currentDocument.getCurrentPage());

            dialogStage.showAndWait();

            // Check if user clicked print
            if (controller.isPrintClicked()) {
                uiStateManager.updateStatus("Print job sent successfully");
                logger.info("Print job completed");
            }

        } catch (IOException e) {
            logger.error("Error opening print dialog", e);
            uiStateManager.showError("Error", "Could not open print dialog: " + e.getMessage());
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
                
                // Set page change listener again after recreating ScrollHandler
                scrollHandler.setPageChangeListener(newPageIndex -> Platform.runLater(() -> {
                    if (currentDocument != null) {
                        pageInfoManager.updatePageInfo(currentDocument);
                    }
                }));

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

            } catch (Exception ex) {
                logger.error("Error deleting page {}", current + 1, ex);
                uiStateManager.showError("Delete Page Error", "Could not delete the page: " + ex.getMessage());

                // Recovery: thử mở lại file gốc
                try {
                    if (currentDocument != null && currentDocument.getFile() != null) {
                        openPDFFile(currentDocument.getFile());
                    }
                } catch (Exception recovery) {
                    logger.error("Failed to recover after delete error", recovery);
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

    // ==================== PDF Encryption/Decryption ====================

    @FXML
    private void handleShowPDFPermissions() {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF Loaded",
                    "Please open a PDF file first.");
            return;
        }

        StringBuilder info = new StringBuilder();
        info.append("Thông tin bảo mật PDF:\n\n");

        if (!currentDocument.getDocument().isEncrypted()) {
            info.append("File không được mã hóa\n");
            info.append("Không có mật khẩu bảo vệ");
        } else {
            info.append("File được mã hóa\n\n");

            AccessPermission perm =
                    currentDocument.getDocument().getCurrentAccessPermission();

            if (perm != null) {
                if (perm.isOwnerPermission()) {
                    info.append("Quyền: OWNER (Toàn quyền)\n\n");
                } else {
                    info.append("Quyền: USER (Hạn chế)\n\n");
                }

                info.append("Quyền được cấp:\n");
                info.append("  - In ấn: ").append(perm.canPrint() ? "Có" : "Không").append("\n");
                info.append("  - Chỉnh sửa: ").append(perm.canModify() ? "Có" : "Không").append("\n");
                info.append("  - Sao chép text: ").append(perm.canExtractContent() ? "Có" : "Không").append("\n");
                info.append("  - Chú thích: ").append(perm.canModifyAnnotations() ? "Có" : "Không").append("\n");
                info.append("  - Điền form: ").append(perm.canFillInForm() ? "Có" : "Không").append("\n");
            }
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Quyền PDF");
        alert.setHeaderText("Thông tin bảo mật và quyền truy cập");
        alert.setContentText(info.toString());
        alert.getDialogPane().setPrefWidth(450);

        if (themeManager != null) {
            themeManager.applyThemeToScene(alert.getDialogPane().getScene());
        }

        alert.showAndWait();
    }

    @FXML
    private void handleEncryptPDF() {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF Loaded",
                    "Please open a PDF file first before encrypting.");
            return;
        }

        EncryptionDialog dialog = new EncryptionDialog();
        
        // Apply theme if available
        if (themeManager != null) {
            themeManager.applyThemeToScene(dialog.getDialogPane().getScene());
        }

        dialog.showAndWait().ifPresent(result -> {
            try {
                Stage stage = (Stage) rootPane.getScene().getWindow();
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save Encrypted PDF As");
                fileChooser.setInitialFileName("encrypted_" + currentDocument.getFileName());
                fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter(Constants.PDF_DESCRIPTION, Constants.PDF_EXTENSION)
                );
                
                File outputFile = fileChooser.showSaveDialog(stage);
                if (outputFile == null) {
                    return; // User cancelled
                }

                // Encrypt the PDF
                pdfService.encryptPDF(
                        currentDocument.getFile(),
                        outputFile,
                        result.ownerPassword(),
                        result.userPassword(),
                        result.permissions()
                );

                Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                successAlert.setTitle("Thành công");
                successAlert.setHeaderText("PDF đã được mã hóa");
                successAlert.setContentText("File đã được lưu tại:\n" + outputFile.getAbsolutePath());
                
                if (themeManager != null) {
                    themeManager.applyThemeToScene(successAlert.getDialogPane().getScene());
                }
                
                successAlert.showAndWait();

                logger.info("Successfully encrypted PDF: {}", outputFile.getName());

            } catch (IOException e) {
                logger.error("Error encrypting PDF", e);
                uiStateManager.showError("Encryption Error",
                        "Could not encrypt PDF: " + e.getMessage());
            }
        });
    }

    @FXML
    private void handleDecryptPDF() {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF Loaded",
                    "Please open a PDF file first before removing encryption.");
            return;
        }

        if (!currentDocument.getDocument().isEncrypted()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thông tin");
            alert.setHeaderText("PDF không được mã hóa");
            alert.setContentText("File PDF này không có mật khẩu bảo vệ.");
            
            if (themeManager != null) {
                themeManager.applyThemeToScene(alert.getDialogPane().getScene());
            }
            
            alert.showAndWait();
            return;
        }

        // Check if user has owner permission
        AccessPermission permission =
                currentDocument.getDocument().getCurrentAccessPermission();
        
        if (permission == null || !permission.isOwnerPermission()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Không có quyền");
            alert.setHeaderText("Không thể xóa mật khẩu");
            alert.setContentText("Bạn cần mật khẩu chủ sở hữu (Owner Password) để xóa bảo vệ.\n" +
                    "Hiện tại bạn chỉ có quyền người dùng (User Permission).");
            
            if (themeManager != null) {
                themeManager.applyThemeToScene(alert.getDialogPane().getScene());
            }
            
            alert.showAndWait();
            return;
        }

        // Confirm action
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Xác nhận");
        confirmAlert.setHeaderText("Xóa mật khẩu bảo vệ");
        confirmAlert.setContentText("Bạn có chắc muốn xóa mật khẩu bảo vệ khỏi file PDF này?\n" +
                "File mới sẽ không có mật khẩu.");
        
        if (themeManager != null) {
            themeManager.applyThemeToScene(confirmAlert.getDialogPane().getScene());
        }

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    Stage stage = (Stage) rootPane.getScene().getWindow();
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Save Decrypted PDF As");
                    fileChooser.setInitialFileName("decrypted_" + currentDocument.getFileName());
                    fileChooser.getExtensionFilters().add(
                            new FileChooser.ExtensionFilter(Constants.PDF_DESCRIPTION, Constants.PDF_EXTENSION)
                    );

                    File outputFile = fileChooser.showSaveDialog(stage);
                    if (outputFile == null) {
                        return; // User cancelled
                    }

                    // Remove all security before saving
                    currentDocument.getDocument().setAllSecurityToBeRemoved(true);
                    pdfService.saveAs(currentDocument, outputFile);

                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Thành công");
                    successAlert.setHeaderText("Đã xóa mật khẩu");
                    successAlert.setContentText("File không có mật khẩu đã được lưu tại:\n" +
                            outputFile.getAbsolutePath());
                    
                    if (themeManager != null) {
                        themeManager.applyThemeToScene(successAlert.getDialogPane().getScene());
                    }
                    
                    successAlert.showAndWait();

                    logger.info("Successfully removed encryption from PDF: {}", outputFile.getName());

                } catch (IOException e) {
                    logger.error("Error removing encryption", e);
                    uiStateManager.showError("Decryption Error",
                            "Could not remove encryption: " + e.getMessage());
                }
            }
        });
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

        DialogPane dialogPane = alert.getDialogPane();
        if (themeManager != null) {
            themeManager.applyThemeToScene(dialogPane.getScene());
        }

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
            Stage dialogStage = createDialogStage(root, "Merge PDF Files");

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

        // Check permissions for encrypted PDFs
        if (currentDocument.getDocument().isEncrypted()) {
            AccessPermission permission = currentDocument.getDocument().getCurrentAccessPermission();
            if (permission != null && !permission.canExtractContent() && !permission.isOwnerPermission()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Không có quyền");
                alert.setHeaderText("Không thể tách PDF");
                alert.setContentText("Bạn không có quyền trích xuất nội dung từ file PDF này.\n" +
                        "Cần quyền Owner hoặc quyền Extract Content.");
                
                if (themeManager != null) {
                    themeManager.applyThemeToScene(alert.getDialogPane().getScene());
                }
                
                alert.showAndWait();
                return;
            }
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/split-dialog.fxml"));
            Parent root = loader.load();

            SplitDialogController controller = loader.getController();
            Stage dialogStage = createDialogStage(root, "Split PDF File");

            controller.setDialogStage(dialogStage);
            
            // Use PDDocument for encrypted PDFs, File for regular PDFs
            if (currentDocument.getDocument().isEncrypted()) {
                controller.setSourceDocument(currentDocument.getDocument(), currentDocument.getFile());
            } else {
                controller.setSourceFile(currentDocument.getFile());
            }


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

        // Check permissions for encrypted PDFs
        if (currentDocument.getDocument().isEncrypted()) {
            AccessPermission permission = currentDocument.getDocument().getCurrentAccessPermission();
            if (permission != null && !permission.canExtractContent() && !permission.isOwnerPermission()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Không có quyền");
                alert.setHeaderText("Không thể trích xuất trang");
                alert.setContentText("Bạn không có quyền trích xuất nội dung từ file PDF này.\n" +
                        "Cần quyền Owner hoặc quyền Extract Content.");
                
                if (themeManager != null) {
                    themeManager.applyThemeToScene(alert.getDialogPane().getScene());
                }
                
                alert.showAndWait();
                return;
            }
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/extract-dialog.fxml"));
            Parent root = loader.load();

            ExtractDialogController controller = loader.getController();
            Stage dialogStage = createDialogStage(root, "Extract PDF Pages");

            controller.setDialogStage(dialogStage);
            
            // Use PDDocument for encrypted PDFs, File for regular PDFs
            if (currentDocument.getDocument().isEncrypted()) {
                controller.setSourceDocument(currentDocument.getDocument(), currentDocument.getFile());
            } else {
                controller.setSourceFile(currentDocument.getFile());
            }

            dialogStage.setOnCloseRequest(event -> controller.shutdown());
            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Error opening extract dialog", e);
            uiStateManager.showError("Error", "Could not open extract dialog: " + e.getMessage());
        }
    }

    /**
     * Creates and configures a dialog stage with standard settings.
     *
     * @param root The dialog root node
     * @param title The dialog title
     * @return Configured Stage object
     */
    private Stage createDialogStage(Parent root, String title) {
        Stage dialogStage = new Stage();
        dialogStage.setTitle(title);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initOwner(rootPane.getScene().getWindow());

        Scene dialogScene = new Scene(root);
        dialogStage.setScene(dialogScene);

        if (themeManager != null) {
            themeManager.applyThemeToScene(dialogScene);
        }

        return dialogStage;
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
        processAllAnnotationLayers(layer -> {
            layer.setAnnotationMode(mode);
            if (mode != AnnotationLayer.AnnotationMode.NONE && mode != AnnotationLayer.AnnotationMode.HIGHLIGHT) {
                if (colorPicker != null) {
                    layer.setDrawingColor(colorPicker.getValue());
                }
            }
            if (strokeWidthSlider != null) {
                layer.setLineWidth(strokeWidthSlider.getValue());
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
    private void processAllAnnotationLayers(java.util.function.Consumer<AnnotationLayer> action) {
        for (VBox pageBox : collectPageBoxes()) {
            if (pageBox == null || pageBox.getChildren().isEmpty()) continue;
            if (pageBox.getChildren().getFirst() instanceof StackPane stack) {
                stack.getChildren().stream()
                        .filter(child -> child instanceof AnnotationLayer)
                        .map(child -> (AnnotationLayer) child)
                        .forEach(action);
            }
        }
    }

    private java.util.List<VBox> collectPageBoxes() {
        java.util.List<VBox> list = new java.util.ArrayList<>();
        if (pagesContainer == null) return list;

        Object twoMode = pagesContainer.getProperties().get("twoPageMode");
        boolean twoPage = twoMode instanceof Boolean && (Boolean) twoMode;

        if (!twoPage) {
            for (javafx.scene.Node node : pagesContainer.getChildren()) {
                if (node instanceof VBox vb) list.add(vb);
            }
            return list;
        }

        for (javafx.scene.Node rowNode : pagesContainer.getChildren()) {
            if (rowNode instanceof javafx.scene.layout.HBox row) {
                for (javafx.scene.Node child : row.getChildren()) {
                    if (child instanceof VBox vb) list.add(vb);
                }
            }
        }

        return list;
    }

    private void updateDrawingStyleForAllPages() {
        if (pagesContainer == null || colorPicker == null || strokeWidthSlider == null) return;

        javafx.scene.paint.Color color = colorPicker.getValue();
        double width = strokeWidthSlider.getValue();

        processAllAnnotationLayers(layer -> {
            layer.setDrawingColor(color);
            layer.setLineWidth(width);
            layer.redraw();
        });

        uiStateManager.updateStatus("Drawing style updated");
    }

    private void makeToggleButtonDeselectable(ToggleButton btn) {
        btn.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (btn.isSelected()) {
                drawingToolsGroup.selectToggle(null);
                event.consume();
            }
        });
    }

    @FXML
    private void handleUndo() {
        if (currentDocument == null) return;

        java.util.List<org.pdflite.model.Annotation> anns = currentDocument.getAnnotations();

        if (anns.isEmpty()) {
            uiStateManager.updateStatus("Nothing to undo");
            return;
        }

        int lastIndex = anns.size() - 1;
        org.pdflite.model.Annotation lastAnn = anns.get(lastIndex);
        int pageIndexOfLastAnn = lastAnn.getPageNumber();
        anns.remove(lastIndex);

        refreshPageAnnotations(pageIndexOfLastAnn);

        uiStateManager.updateStatus("Undid last action");
    }

    private void refreshPageAnnotations(int pageIndex) {
        if (pagesContainer == null) return;

        if (pageIndex >= 0 && pageIndex < pagesContainer.getChildren().size()) {
            javafx.scene.Node pageNode = pagesContainer.getChildren().get(pageIndex);
            if (pageNode instanceof VBox pageBox && !pageBox.getChildren().isEmpty()) {
                if (pageBox.getChildren().get(0) instanceof StackPane stack) {
                    stack.getChildren().stream()
                            .filter(node -> node instanceof AnnotationLayer)
                            .map(node -> (AnnotationLayer) node)
                            .findFirst()
                            .ifPresent(layer -> {
                                java.util.List<org.pdflite.model.Annotation> pageAnns = new java.util.ArrayList<>();
                                for (org.pdflite.model.Annotation a : currentDocument.getAnnotations()) {
                                    if (a.getPageNumber() == pageIndex) {
                                        pageAnns.add(a);
                                    }
                                }
                                layer.setAnnotations(pageAnns);
                            });
                }
            }
        }
    }
}

