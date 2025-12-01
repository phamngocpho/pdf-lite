package org.pdflite.controller;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.pdflite.manager.AnnotationManager;
import org.pdflite.manager.DialogManager;
import org.pdflite.manager.DocumentLifecycleManager;
import org.pdflite.manager.DocumentOperationManager;
import org.pdflite.manager.EncryptionManager;
import org.pdflite.manager.FileManager;
import org.pdflite.manager.FullscreenManager;
import org.pdflite.manager.ListenerFactory;
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
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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
    private ListenerFactory.ZoomChangeListenerWithContext zoomChangeListener;

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
    private final Set<Integer> loadingPages = ConcurrentHashMap.newKeySet();
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

        // Initialize a recent files manager (needed by RecentFilesMenuManager)
        recentFilesManager = new RecentFilesManager();

        // Initialize managers
        initializeManagers();

        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                themeManager = new ThemeManager(newScene);
                searchDialogManager.setThemeManager(themeManager);

                // Cập nhật ThemeManager cho các Manager cần dùng nó
                if (dialogManager != null) dialogManager = new DialogManager(rootPane, themeManager, uiStateManager);
                if (encryptionManager != null) encryptionManager = new EncryptionManager(rootPane, pdfService, themeManager, uiStateManager);

                // Cập nhật DocumentOperationManager để nó có ThemeManager mới
                documentOperationManager = new DocumentOperationManager(pdfService, renderingManager, zoomManager,
                        pageInfoManager, uiStateManager, themeManager, fileManager);

                // Cập nhật DocumentLifecycleManager
                documentLifecycleManager = new DocumentLifecycleManager(pdfService, fileManager, zoomManager,
                        renderingManager, pageInfoManager, uiStateManager, themeManager, recentFilesManager,
                        recentFilesMenuManager);

                recentFilesMenuManager.updateRecentFilesMenu();
            }
        });

        // Set page change listener to update UI when page changes during scroll
        // Must be after initializeManagers() so pageInfoManager is initialized
        scrollHandler.setPageChangeListener(ListenerFactory.createPageChangeListener(currentDocument, pageInfoManager));

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
                ToggleButton selectedBtn = (newVal != null) ? (ToggleButton) newVal : null;
                
                // Handle tool selection - annotationManager will be null until the document is opened
                if (annotationManager == null) {
                    // If no document is open, just handle basic selection mode
                    if (selectedBtn == null) {
                        // No tool selected - enable text selection by default
                        if (pageRenderer != null && pagesContainer != null) {
                            pageRenderer.setSelectionModeActive(pagesContainer, true);
                        }
                    } else if (selectedBtn == btnSelectText) {
                        // Text selection tool
                        if (pageRenderer != null && pagesContainer != null) {
                            pageRenderer.setSelectionModeActive(pagesContainer, true);
                        }
                        uiStateManager.updateStatus("Tool: Text Selection");
                    } else {
                        // Drawing tool selected - disable text selection
                        if (pageRenderer != null && pagesContainer != null) {
                            pageRenderer.setSelectionModeActive(pagesContainer, false);
                        }
                    }
                    return;
                }
                
                // Document is an open-use annotation manager
                annotationManager.handleToolSelection(
                    selectedBtn,
                    btnSelectText,
                    btnDrawRect,
                    btnDrawCircle,
                    btnDrawArrow,
                    active -> {
                        if (pageRenderer != null && pagesContainer != null) {
                            pageRenderer.setSelectionModeActive(pagesContainer, active);
                        }
                    },
                    pagesContainer,
                    this::updateDrawingStyleForAllPages
                );
            });
        }
        if (btnSelectText != null) makeToggleButtonDeselectable(btnSelectText);
        if (btnDrawRect != null) makeToggleButtonDeselectable(btnDrawRect);
        if (btnDrawCircle != null) makeToggleButtonDeselectable(btnDrawCircle);
        if (btnDrawArrow != null) makeToggleButtonDeselectable(btnDrawArrow);

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

        // Zoom Manager - create with a listener that can be updated when the document is opened
        zoomChangeListener = ListenerFactory.createZoomChangeListener(renderingManager, searchManager, uiStateManager);
        zoomManager = new ZoomManager(pdfService, zoomChangeListener);
        zoomManager.initialize(zoomComboBox, scrollPane);

        // Rendering Manager
        renderingManager = new RenderingManager(pdfService, pageRenderer, scrollHandler, zoomManager);

        // File Manager
        fileManager = new FileManager(pdfService, ListenerFactory.createFileOperationListener(uiStateManager));

        // Fullscreen Manager
        fullscreenManager = new FullscreenManager(rootPane, toolbar, ListenerFactory.createFullscreenListener(uiStateManager));

        // Page Info Manager
        pageInfoManager = new PageInfoManager(totalPagesLabel, pageNumberField, prevButton, nextButton);

        // Search Dialog Manager
        searchDialogManager = new SearchDialogManager(rootPane, pageRenderer, zoomManager, renderingManager, uiStateManager, themeManager);

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

        // Annotation Manager (will be initialized when the document is opened)
        annotationManager = null;
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
        AtomicReference<VBox> pagesContainerRef =
                new AtomicReference<>(pagesContainer);
        currentDocument = documentLifecycleManager.openPDFFile(file, currentDocument, pageRenderer,
                scrollPane, pagesContainerRef);
        pagesContainer = pagesContainerRef.get();

        // Initialize the annotation manager when the document is opened
        if (currentDocument != null && pagesContainer != null) {
            annotationManager = new AnnotationManager(pagesContainer, uiStateManager, currentDocument);
            
            // Update zoom change listener with document context
            if (zoomChangeListener != null) {
                zoomChangeListener.updateContext(currentDocument, pagesContainer, scrollPane);
            }
            
            // Update page change listener with document context
            scrollHandler.setPageChangeListener(ListenerFactory.createPageChangeListener(currentDocument, pageInfoManager));
            
            // Enable text selection by default (like browsers) when the document is opened
            // Use Platform.runLater to ensure pages are fully rendered first
            Platform.runLater(() -> {
                if (pageRenderer != null && pagesContainer != null) {
                    pageRenderer.setSelectionModeActive(pagesContainer, true);
                }
            });
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

        // Warn the user if the document is encrypted
        if (currentDocument.getDocument().isEncrypted()) {
            if (!dialogManager.showEncryptedSaveWarning()) {
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

        // Shutdown executor service to prevent the app from running in the background
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

        int current = currentDocument.getCurrentPage();
        AtomicReference<VBox> pagesContainerRef =
                new AtomicReference<>(pagesContainer);
        AtomicReference<PageRenderer> pageRendererRef =
                new AtomicReference<>(pageRenderer);
        AtomicReference<ScrollHandler> scrollHandlerRef =
                new AtomicReference<>(scrollHandler);
        AtomicReference<RenderingManager> renderingManagerRef =
                new AtomicReference<>(renderingManager);

        PDFDocument newDocument = documentOperationManager.deletePage(currentDocument, current,
                renderExecutor, loadingPages, contentPane, scrollPane, pagesContainerRef,
                pageRendererRef, scrollHandlerRef, renderingManagerRef);

        if (newDocument != null) {
            currentDocument = newDocument;
            pagesContainer = pagesContainerRef.get();
            pageRenderer = pageRendererRef.get();
            scrollHandler = scrollHandlerRef.get();
            renderingManager = renderingManagerRef.get();

            // Recreate annotation manager with a new document
            if (pagesContainer != null) {
                annotationManager = new AnnotationManager(pagesContainer, uiStateManager, currentDocument);
            }
        } else {
            // Recovery: try to reopen an original file
            try {
                if (currentDocument != null && currentDocument.getFile() != null) {
                    openPDFFile(currentDocument.getFile());
                }
            } catch (Exception recovery) {
                logger.error("Failed to recover after delete error", recovery);
            }
        }
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
        dialogManager.showAboutDialog();
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
        if (annotationManager != null) {
            annotationManager.handleUndo();
        }
    }

    // ==================== INSERT PAGE ====================
    @FXML
    private void handleInsertPage() {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF", "Please open a PDF file first.");
            return;
        }

        InsertDialogController controller = dialogManager.openInsertDialog(currentDocument);
        if (controller == null || !controller.isInsertClicked()) {
            return;
        }

        AtomicReference<VBox> pagesContainerRef =
                new AtomicReference<>(pagesContainer);

        PDFDocument updatedDocument = documentOperationManager.insertBlankPages(
                currentDocument, controller, pagesContainerRef, loadingPages,
                pageRenderer, scrollHandler, scrollPane);

        if (updatedDocument != null) {
            pagesContainer = pagesContainerRef.get();
            // Recreate annotation manager with updated documents
            if (pagesContainer != null) {
                annotationManager = new AnnotationManager(pagesContainer, uiStateManager, currentDocument);
            }
        }
    }
}

