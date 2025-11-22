package org.pdflite.controller;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.pdflite.manager.AnnotationManager;
import org.pdflite.manager.DialogManager;
import org.pdflite.manager.DocumentLifecycleManager;
import org.pdflite.manager.DocumentOperationManager;
import org.pdflite.manager.EncryptionManager;
import org.pdflite.manager.FileManager;
import org.pdflite.manager.FullscreenManager;
import org.pdflite.manager.PageInfoManager;
import org.pdflite.manager.RecentFilesManager;
import org.pdflite.manager.RecentFilesMenuManager;
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
import org.pdflite.command.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

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
    private CommandManager commandManager;

    // New managers
    private DialogManager dialogManager;
    private EncryptionManager encryptionManager;
    private AnnotationManager annotationManager;
    private DocumentOperationManager documentOperationManager;
    private DocumentLifecycleManager documentLifecycleManager;
    private RecentFilesMenuManager recentFilesMenuManager;

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

        // Initialize recent files manager (needed by RecentFilesMenuManager)
        recentFilesManager = new RecentFilesManager();

        // Initialize managers
        initializeManagers();
        commandManager = new CommandManager();
        commandManager.addListener((canUndo, canRedo, undoDesc, redoDesc) -> Platform.runLater(() -> {
            // Update button states
            if (undoButton != null) {
                undoButton.setDisable(!canUndo);
                if (canUndo && undoDesc != null) {
                    undoButton.setTooltip(new Tooltip("Undo: " + undoDesc));
                }
            }
            if (redoButton != null) {
                redoButton.setDisable(!canRedo);
                if (canRedo && redoDesc != null) {
                    redoButton.setTooltip(new Tooltip("Redo: " + redoDesc));
                }
            }
        }));
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                setupKeyboardShortcuts(newScene);
            }
        });

        // Set page change listener to update UI when page changes during scroll
        // Must be after initializeManagers() so pageInfoManager is initialized
        scrollHandler.setPageChangeListener(newPageIndex -> Platform.runLater(() -> {
            if (currentDocument != null) {
                // The scroll handler already updated
                // currentDocument.setCurrentPage(newPageIndex)
                // Just update the UI
                pageInfoManager.updatePageInfo(currentDocument);
            }
        }));

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
                    if (pageRenderer != null)
                        pageRenderer.setSelectionModeActive(pagesContainer, false);
                    return;
                }

                ToggleButton selectedBtn = (ToggleButton) newVal;

                if (selectedBtn == btnSelectText) {
                    // Tắt vẽ
                    updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.NONE);
                    // Bật chọn Text
                    if (pageRenderer != null)
                        pageRenderer.setSelectionModeActive(pagesContainer, true);

                    uiStateManager.updateStatus("Tool: Text Selection");
                } else {
                    if (pageRenderer != null)
                        pageRenderer.setSelectionModeActive(pagesContainer, false);

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
        if (btnSelectText != null)
            makeToggleButtonDeselectable(btnSelectText);
        if (btnDrawRect != null)
            makeToggleButtonDeselectable(btnDrawRect);
        if (btnDrawCircle != null)
            makeToggleButtonDeselectable(btnDrawCircle);
        if (btnDrawArrow != null)
            makeToggleButtonDeselectable(btnDrawArrow);

        if (colorPicker != null) {
            colorPicker.setValue(javafx.scene.paint.Color.BLACK);
            colorPicker.setOnAction(e -> updateDrawingStyleForAllPages());
        }

        if (strokeWidthSlider != null) {
            strokeWidthSlider.valueProperty().addListener((obs, oldVal, newVal) -> updateDrawingStyleForAllPages());
        }

        uiStateManager.updateUIState(false);
    }

    private void setupKeyboardShortcuts(Scene scene) {
        // Ctrl+Z for Undo
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN),
                this::handleUndo);

        // Ctrl+Y for Redo
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN),
                this::handleRedo);

        // Ctrl+Shift+Z for Redo (alternative)
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                this::handleRedo);

        logger.info("Keyboard shortcuts registered: Ctrl+Z (Undo), Ctrl+Y (Redo)");
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

        // New managers
        dialogManager = new DialogManager(rootPane, themeManager, uiStateManager);
        encryptionManager = new EncryptionManager(rootPane, pdfService, themeManager, uiStateManager);

        // Recent Files Menu Manager (needs to be initialized after recentFilesManager)
        recentFilesMenuManager = new RecentFilesMenuManager(recentFilesMenu, recentFilesManager, uiStateManager,
                this::openPDFFile);
        recentFilesMenuManager.updateRecentFilesMenu();

        // Document Lifecycle Manager
        documentLifecycleManager = new DocumentLifecycleManager(pdfService, fileManager, zoomManager,
                renderingManager, pageInfoManager, uiStateManager, themeManager, recentFilesManager,
                recentFilesMenuManager);

        // Document Operation Manager
        documentOperationManager = new DocumentOperationManager(pdfService, renderingManager, zoomManager,
                pageInfoManager, uiStateManager, themeManager, fileManager);

        // Annotation Manager (will be initialized when document is opened)
        annotationManager = null;
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

                    Objects.requireNonNull(renderingManager).preserveScrollPositionAndApplyZoom(newZoom);
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
        java.util.concurrent.atomic.AtomicReference<VBox> pagesContainerRef = new java.util.concurrent.atomic.AtomicReference<>(
                pagesContainer);
        currentDocument = documentLifecycleManager.openPDFFile(file, currentDocument, pageRenderer,
                scrollPane, pagesContainerRef);
        pagesContainer = pagesContainerRef.get();

        // Initialize annotation manager when document is opened
        if (currentDocument != null && pagesContainer != null) {
            annotationManager = new AnnotationManager(pagesContainer, uiStateManager, currentDocument);
        }
    }

    @FXML
    private void handleSave() {
        documentLifecycleManager.saveDocument(currentDocument);
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
    private void handleRedo() {
        documentOperationManager.handleRedo(commandManager, this::reloadCurrentDocument);
    }

    @FXML
    private void handlePrint() {
        dialogManager.openPrintDialog(currentDocument, printService,
                currentDocument != null ? currentDocument.getCurrentPage() : 0);
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

    public void reloadCurrentDocument() throws IOException {
        if (currentDocument == null || currentDocument.getFile() == null) {
            return;
        }

        File currentFile = currentDocument.getFile();
        double currentZoom = zoomManager.getCurrentZoom();
        int currentPage = currentDocument.getCurrentPage();

        logger.info("Reloading document: {}", currentFile.getName());

        // Close current document
        if (currentDocument.getDocument() != null) {
            currentDocument.getDocument().close();
        }

        // Clear UI
        contentPane.getChildren().clear();
        pagesContainer = null;
        loadingPages.clear();

        // Clear caches
        pageRenderer.clearCache();
        pageRenderer.cancelAllPendingRenders();

        // Create new components
        pageRenderer = new PageRenderer(pdfService, renderExecutor);
        scrollHandler = new ScrollHandler(pageRenderer, scrollPane);

        // Reopen file
        currentDocument = fileManager.openFile(currentFile);

        if (currentDocument == null) {
            throw new IOException("Could not reopen document");
        }

        // Restore state
        int newTotal = currentDocument.getTotalPages();
        int newCurrentPage = Math.min(currentPage, newTotal - 1);
        currentDocument.setCurrentPage(Math.max(0, newCurrentPage));
        currentDocument.setZoomLevel(currentZoom);

        // Update components
        pageRenderer.setDocument(currentDocument, currentZoom);
        zoomManager.setDocument(currentDocument);
        zoomManager.setCurrentZoom(currentZoom);

        // Re-render
        renderingManager = new RenderingManager(pdfService, pageRenderer, scrollHandler, zoomManager);
        renderingManager.setDocument(currentDocument);
        renderingManager.setUIComponents(null, scrollPane, contentPane);
        renderingManager.renderAllPages();
        pagesContainer = renderingManager.getPagesContainer();

        scrollHandler.setDocument(currentDocument, pagesContainer);

        // Update UI
        pageInfoManager.updatePageInfo(currentDocument);

        // Scroll to current page
        Platform.runLater(() -> {
            scrollHandler.scrollToPage(newCurrentPage);
            pageInfoManager.updatePageInfo(currentDocument);
        });

        logger.info("Document reloaded successfully. Total pages: {}", newTotal);
    }

    /**
     * Gets the command manager.
     *
     * @return the command manager
     */
    public CommandManager getCommandManager() {
        return commandManager;
    }

    @FXML
    private void handleDeletePage() {
        documentOperationManager.handleDeletePageWithCommand(
                currentDocument,
                this,
                commandManager,
                this::reloadCurrentDocument
        );
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
            pageRenderer.setHighlightModeActive();
            updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.HIGHLIGHT);
        } else {
            // Tắt Highlight
            uiStateManager.updateStatus("Highlight mode: Disabled");
            pageRenderer.setHighlightModeActive();
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
        encryptionManager.showPDFPermissions(currentDocument);
    }

    @FXML
    private void handleEncryptPDF() {
        encryptionManager.encryptPDF(currentDocument);
    }

    @FXML
    private void handleDecryptPDF() {
        encryptionManager.decryptPDF(currentDocument);
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
        dialogManager.openMergeDialog();
    }

    @FXML
    private void handleSplitPDF() {
        dialogManager.openSplitDialog(currentDocument);
    }

    // ==================== Rotation Operations ====================

    @FXML
    private void handleRotateLeft() {
        documentOperationManager.rotateDocument(currentDocument, -90);
    }

    @FXML
    private void handleRotateRight() {
        documentOperationManager.rotateDocument(currentDocument, 90);
    }

    @FXML
    private void handleExtractPages() {
        dialogManager.openExtractDialog(currentDocument);
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

    @FXML
    private void handleClearRecentFiles() {
        recentFilesMenuManager.clearRecentFiles();
    }

    public void openLastFile() {
        recentFilesMenuManager.openLastFile();
    }

    private void updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode mode) {
        if (annotationManager != null) {
            annotationManager.updateAnnotationModeForAllPages(mode);
        }
    }

    private void updateDrawingStyleForAllPages() {
        if (annotationManager != null && colorPicker != null && strokeWidthSlider != null) {
            annotationManager.updateDrawingStyleForAllPages(colorPicker.getValue(), strokeWidthSlider.getValue());
        }
    }

    private void makeToggleButtonDeselectable(ToggleButton btn) {
        if (annotationManager != null && drawingToolsGroup != null) {
            annotationManager.makeToggleButtonDeselectable(btn, drawingToolsGroup);
        }
    }

    @FXML
    private void handleUndo() {
        // Phần 1: Undo của annotationManager (nếu có)
        if (annotationManager != null) {
            annotationManager.handleUndo();
        }

        // Phần 2: Undo theo commandManager
        documentOperationManager.handleUndo(commandManager, this::reloadCurrentDocument);
    }

    @FXML
    private Button undoButton;
    @FXML
    private Button redoButton;

}
