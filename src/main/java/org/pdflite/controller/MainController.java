package org.pdflite.controller;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.control.*;
import org.pdflite.manager.*;
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
import javafx.scene.control.MenuButton;
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
    public MenuButton drawingToolsMenu;

    // ==================== FXML Injected UI Components ====================

    @FXML
    private BorderPane rootPane;
    @FXML
    private TabPane documentTabPane;
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
    private ColorPicker highlightColorPicker;
    @FXML
    private Slider strokeWidthSlider;
    @FXML
    private Label strokeWidthLabel;
    @FXML
    private ToggleGroup drawingToolsGroup;
    @FXML
    private ToggleButton btnDrawRect;
    @FXML
    private ToggleButton btnDrawCircle;
    @FXML
    private ToggleButton btnDrawArrow;
    @FXML
    private Button undoButton;
    @FXML
    private Button redoButton;
    @FXML
    private javafx.scene.layout.HBox customTitleBar;
    @FXML
    private Label titleLabel;
    @FXML
    private javafx.scene.image.ImageView logoImageView;
    @FXML
    private Button minimizeButton;
    @FXML
    private Button maximizeButton;
    @FXML
    private Button closeButton;
    @FXML
    private StackPane saveStatusIndicator;
    @FXML
    private javafx.scene.control.MenuItem toggleToolbarMenuItem;
    @FXML
    private RadioMenuItem systemThemeItem;
    @FXML
    private RadioMenuItem lightThemeItem;
    @FXML
    private RadioMenuItem darkThemeItem;
    @FXML
    private ToggleButton bookmarkToggleButton;


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
    private org.pdflite.manager.ContentStreamManager contentStreamManager;
    private TitleBarManager titleBarManager;
    private ExportManager exportManager;
    private ImageInsertionManager imageInsertionManager;
    private HighlightPersistenceManager highlightPersistenceManager;

    // Refactored managers
    private RecoveryManager recoveryManager;
    private TextEditManager textEditManager;
    private ApplicationLifecycleManager applicationLifecycleManager;
    private KeyboardShortcutManager keyboardShortcutManager;
    private DocumentSetupManager documentSetupManager;
    private DocumentPropertiesManager documentPropertiesManager;
    private PDFOptimizationManager pdfOptimizationManager;
    private PageOperationsManager pageOperationsManager;
    private DrawingToolsSetupManager drawingToolsSetupManager;

    // Undo/Redo Manager
    private UndoRedoManager undoRedoManager;

    // Page Deletion Manager
    private PageDeletionManager pageDeletionManager;

    // Page Duplication Manager
    private PageDuplicationManager pageDuplicationManager;

    // Metadata Manager
    private MetadataManager metadataManager;

    // Auto-save Manager
    private AutoSaveManager autoSaveManager;

    // Save Status Manager
    private SaveStatusManager saveStatusManager;

    // Bookmark Manager
    private BookmarkManager bookmarkManager;

    // Bookmark UI Manager
    private BookmarkUIManager bookmarkUIManager;

    // Tab Manager
    private TabManager tabManager;

    // Save Manager
    private SaveManager saveManager;

    // ==================== Document State ====================
    
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(6);
    private final java.util.concurrent.ScheduledExecutorService autoSaveExecutor =
            Executors.newSingleThreadScheduledExecutor();
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
        scrollHandler = new ScrollHandler(pageRenderer, null);

        // Create helpers
        navigationHelper = new NavigationHelper(this, pdfService, renderExecutor, loadingPages);
        searchManager = new SearchManager(this, navigationHelper);

        // Initialize a recent files manager (needed by RecentFilesMenuManager)
        recentFilesManager = new RecentFilesManager();

        // Initialize title bar manager
        if (customTitleBar != null) {
            titleBarManager = new TitleBarManager(
                    customTitleBar, minimizeButton, maximizeButton, closeButton,
                    this::performExit, this::handleMaximize
            );
            titleBarManager.initialize();
        }

        // Initialize window resize manager
        WindowResizeManager resizeManager = new WindowResizeManager(rootPane);
        resizeManager.initialize();

        // Initialize export manager
        exportManager = new ExportManager(rootPane, uiStateManager);

        // Initialize image insertion manager
        imageInsertionManager = new ImageInsertionManager(rootPane, uiStateManager, renderingManager, pageRenderer, null);

        // Initialize highlight persistence manager
        highlightPersistenceManager = new HighlightPersistenceManager();

        // Initialize undo/redo manager
        undoRedoManager = new UndoRedoManager(uiStateManager);
        undoRedoManager.setButtons(undoButton, redoButton);

        // Set command manager in page renderer
        if (pageRenderer != null) {
            pageRenderer.setCommandManager(undoRedoManager.getCommandManager());
        }

        // Initialize drawing tool icon manager
        DrawingToolIconManager drawingToolIconManager = new DrawingToolIconManager();

        // Initialize managers
        initializeManagers();

        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                themeManager = new ThemeManager(newScene, logoImageView);
                themeManager.setThemeMenuItems(systemThemeItem, lightThemeItem, darkThemeItem);
                searchDialogManager.setThemeManager(themeManager);

                if (saveStatusManager == null && saveStatusIndicator != null) {
                    saveStatusManager = new SaveStatusManager(saveStatusIndicator, autoSaveManager, uiStateManager);
                }

                if (dialogManager != null) dialogManager = new DialogManager(rootPane, themeManager, uiStateManager);
                if (encryptionManager != null)
                    encryptionManager = new EncryptionManager(rootPane, pdfService, themeManager, uiStateManager);

                imageInsertionManager = new ImageInsertionManager(rootPane, uiStateManager, renderingManager, pageRenderer, themeManager);

                if (exportManager != null) {
                    exportManager.setThemeManager(themeManager);
                }

                if (pageRenderer != null && pageRenderer.getContextMenuHandler() != null) {
                    pageRenderer.getContextMenuHandler().setThemeManager(themeManager);
                }

                if (pageDeletionManager != null) {
                    pageDeletionManager.setThemeManagerSupplier(() -> themeManager);
                }

                if (bookmarkManager != null) {
                    bookmarkManager.setThemeManager(themeManager);
                }

                if (bookmarkUIManager != null) {
                    bookmarkUIManager.setThemeManager(themeManager);
                }

                documentOperationManager = new DocumentOperationManager(pdfService, renderingManager, zoomManager,
                        pageInfoManager, uiStateManager, themeManager, fileManager);

                documentLifecycleManager = new DocumentLifecycleManager(pdfService, fileManager, zoomManager,
                        renderingManager, pageInfoManager, uiStateManager, themeManager, recentFilesManager,
                        recentFilesMenuManager);

                recentFilesMenuManager.updateRecentFilesMenu();

                initializeRefactoredManagers();
            }
        });

        // Setup page navigation
        if (pageNumberField != null) {
            pageNumberField.setOnAction(e -> handleGoToPage());
            pageNumberField.setTextFormatter(new TextFormatter<>(change -> {
                String next = change.getControlNewText();
                return next.matches("\\d*") ? change : null;
            }));
        }

        // Setup drawing tool icons
        drawingToolIconManager.setupDrawingToolIcons(btnDrawRect, btnDrawCircle, btnDrawArrow);
        drawingToolIconManager.setupUndoIcon(undoButton);
        drawingToolIconManager.setupRedoIcon(redoButton);

        uiStateManager.updateUIState(false);

        // Check for recovery files on startup
        Platform.runLater(() -> {
            if (recoveryManager != null) {
                recoveryManager.checkForRecovery(this::openPDFFile);
            }
        });

        // Setup keyboard shortcuts
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null && keyboardShortcutManager != null) {
                keyboardShortcutManager.removeKeyboardShortcuts(oldScene);
            }
            if (newScene != null && keyboardShortcutManager != null) {
                keyboardShortcutManager.setupKeyboardShortcuts(newScene);
            }
        });
    }

    // ==================== Multi-Tab Management (Delegated to TabManager) ====================
    
    private org.pdflite.model.DocumentContext getCurrentContext() {
        return tabManager != null ? tabManager.getCurrentContext() : null;
    }
    
    private PDFDocument getActiveDocument() {
        return tabManager != null ? tabManager.getActiveDocument() : null;
    }
    
    private VBox getCurrentPagesContainer() {
        return tabManager != null ? tabManager.getCurrentPagesContainer() : null;
    }
    
    private ScrollPane getCurrentScrollPane() {
        return tabManager != null ? tabManager.getCurrentScrollPane() : null;
    }
    
    private AnnotationManager getCurrentAnnotationManager() {
        return tabManager != null ? tabManager.getCurrentAnnotationManager() : null;
    }

    /**
     * Initializes refactored managers that depend on other managers.
     */
    private void initializeRefactoredManagers() {
        // Recovery Manager
        recoveryManager = new RecoveryManager(autoSaveManager, uiStateManager, themeManager);

        // Text Edit Manager
        textEditManager = new TextEditManager(uiStateManager, contentStreamManager,
                () -> renderingManager, saveStatusManager);

        // Application Lifecycle Manager
        applicationLifecycleManager = new ApplicationLifecycleManager(
                fileManager, autoSaveManager, recoveryManager, renderExecutor, autoSaveExecutor);

        // Keyboard Shortcut Manager
        keyboardShortcutManager = new KeyboardShortcutManager(undoRedoManager);

        // Document Setup Manager
        documentSetupManager = new DocumentSetupManager(
                zoomManager, renderingManager, pageInfoManager, highlightPersistenceManager,
                autoSaveManager, saveStatusManager, pageRenderer, scrollHandler);

        // Document Properties Manager
        documentPropertiesManager = new DocumentPropertiesManager(
                metadataManager, uiStateManager, saveStatusManager, themeManager);

        // PDF Optimization Manager
        pdfOptimizationManager = new PDFOptimizationManager(
                uiStateManager, renderingManager, saveStatusManager, themeManager);

        // Page Operations Manager
        pageOperationsManager = new PageOperationsManager(
                uiStateManager, pageDuplicationManager, renderingManager,
                pageInfoManager, saveStatusManager, pageRenderer, themeManager);

        // Drawing Tools Setup Manager
        drawingToolsSetupManager = new DrawingToolsSetupManager(pageRenderer, uiStateManager);

        // Setup color pickers and drawing tools
        drawingToolsSetupManager.setUpdateDrawingStyleCallback(this::updateDrawingStyleForAllPages);
        drawingToolsSetupManager.setUpdateHighlightColorCallback(this::updateHighlightColorForAllPages);
        drawingToolsSetupManager.setupColorPicker(colorPicker, this::updateDrawingStyleForAllPages);
        drawingToolsSetupManager.setupHighlightColorPicker(highlightColorPicker, this::updateHighlightColorForAllPages);
        drawingToolsSetupManager.setupStrokeWidthSlider(strokeWidthSlider, strokeWidthLabel,
                this::updateDrawingStyleForAllPages);

        // Save Manager
        saveManager = new SaveManager(fileManager, documentLifecycleManager, highlightPersistenceManager,
                autoSaveManager, saveStatusManager, uiStateManager, dialogManager);

        // Initialize TabManager with DocumentSetupManager and DrawingToolsSetupManager
        tabManager = new TabManager(documentTabPane, pdfService, fileManager, pageRenderer, zoomManager,
                pageInfoManager, uiStateManager, recentFilesManager, recentFilesMenuManager,
                documentSetupManager, drawingToolsSetupManager, bookmarkManager, autoSaveManager);
        tabManager.setRenderingManager(renderingManager);
        tabManager.setZoomChangeListener(zoomChangeListener);
        tabManager.setScrollHandler(scrollHandler);
        tabManager.setDrawingToolsSuppliers(
                () -> drawingToolsGroup,
                () -> btnDrawRect,
                () -> btnDrawCircle,
                () -> btnDrawArrow
        );
        tabManager.setColorPickerSuppliers(
                () -> colorPicker,
                () -> highlightColorPicker,
                () -> strokeWidthSlider
        );
        tabManager.setOnTabSwitched(doc -> {
            renderingManager = tabManager.getRenderingManager();
            annotationManager = tabManager.getAnnotationManager();
        });
        tabManager.initialize();

        // Setup text edit callback
        setupTextEditCallback();
    }

    /**
     * Initializes all manager classes.
     */
    private void initializeManagers() {
        // UI State Manager (needed by other managers)
        uiStateManager = new UIStateManager(statusLabel, prevButton, nextButton, pageNumberField, zoomComboBox, () -> themeManager);

        // Create ZoomManager first (without listener)
        zoomManager = new ZoomManager(pdfService, null);
        zoomManager.initialize(zoomComboBox, null);

        // Rendering Manager - needs zoomManager
        renderingManager = new RenderingManager(pdfService, pageRenderer, scrollHandler, zoomManager);

        // Now create zoom change listener with renderingManager supplier
        zoomChangeListener = ListenerFactory.createZoomChangeListener(() -> renderingManager, searchManager, uiStateManager);

        // Set the listener to zoomManager
        zoomManager.setZoomChangeListener(zoomChangeListener);

        // File Manager
        fileManager = new FileManager(pdfService, ListenerFactory.createFileOperationListener(uiStateManager), () -> themeManager);

        // Fullscreen Manager
        fullscreenManager = new FullscreenManager(rootPane, toolbar, ListenerFactory.createFullscreenListener(uiStateManager));

        // Page Info Manager
        pageInfoManager = new PageInfoManager(totalPagesLabel, pageNumberField, prevButton, nextButton);

        // Search Dialog Manager
        searchDialogManager = new SearchDialogManager(rootPane, pageRenderer, zoomManager, renderingManager, uiStateManager, themeManager);

        // New managers
        dialogManager = new DialogManager(rootPane, themeManager, uiStateManager);
        encryptionManager = new EncryptionManager(rootPane, pdfService, themeManager, uiStateManager);

        // Recent Files Menu Manager
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

        // Content Stream Manager
        contentStreamManager = new org.pdflite.manager.ContentStreamManager();

        // Page Deletion Manager
        pageDeletionManager = new PageDeletionManager(
                uiStateManager,
                undoRedoManager,
                renderingManager,
                pageInfoManager,
                pageRenderer
        );

        // Page Duplication Manager
        pageDuplicationManager = new PageDuplicationManager();

        // Metadata Manager
        metadataManager = new MetadataManager();

        // Auto-save Manager
        autoSaveManager = new AutoSaveManager(autoSaveExecutor);

        // Bookmark Manager
        bookmarkManager = new BookmarkManager();

        // Bookmark UI Manager
        bookmarkUIManager = new BookmarkUIManager(rootPane, bookmarkManager, uiStateManager, navigationHelper);

        // Set callback to update icon after auto-save
        autoSaveManager.setOnAutoSaveCallback(() -> {
            if (saveStatusManager != null) {
                saveStatusManager.updateSaveStatusIndicator(true);
            }
            uiStateManager.updateStatus("Auto-saved");
        });

        // Set text edit callback for context menu
        setupTextEditCallback();

        // Highlight handling lives in HighlightManager
        HighlightManager highlightManager = new HighlightManager(
                highlightColorPicker,
                uiStateManager,
                this::getCurrentDocument,
                this::getCurrentZoom,
                this::getCurrentAnnotationManager
        );
        highlightManager.setupHighlightCallback(pageRenderer);
        highlightManager.setupDeleteHighlightCallback(pageRenderer);

        // Comment handling
        CommentManager commentManager = new CommentManager(
                uiStateManager,
                this::getCurrentDocument,
                this::getCurrentZoom,
                this::getCurrentAnnotationManager
        );
        commentManager.setupAddCommentCallback(pageRenderer);
        commentManager.setupDeleteCommentCallback(pageRenderer);
    }

    /**
     * Sets up the text edit callback for the context menu handler.
     * This callback is invoked when the user edits text and clicks OK in the text edit dialog.
     */
    private void setupTextEditCallback() {
        if (textEditManager == null || pageRenderer == null) {
            return;
        }

        // Create callback that gets the current document dynamically
        pageRenderer.getContextMenuHandler().setTextEditCallback(
                (pageIndex, coverX, coverY, coverWidth, coverHeight, textX, textY, newText, fontSize, font) -> textEditManager.createTextEditCallback(this::getCurrentDocument)
                        .onTextEdit(pageIndex, coverX, coverY, coverWidth, coverHeight,
                                textX, textY, newText, fontSize, font));

        logger.info("Text edit callback configured successfully");
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
        if (tabManager != null) {
            tabManager.openPDFFile(file, loadingPages, renderExecutor);
            // Update local references after tab switch
            renderingManager = tabManager.getRenderingManager();
            annotationManager = tabManager.getAnnotationManager();
        }
    }

    @FXML
    private void handleSave() {
        if (saveManager != null) {
            saveManager.save(getActiveDocument());
        }
    }

    @FXML
    private void handleSaveAs() {
        if (saveManager != null) {
            Stage stage = (Stage) rootPane.getScene().getWindow();
            saveManager.saveAs(getActiveDocument(), stage);
        }
    }

    @FXML
    private void handleExport() {
        exportManager.openExportDialog(getActiveDocument());
    }

    @FXML
    private void handlePrint() {
        PDFDocument currentDocument = getActiveDocument();
        dialogManager.openPrintDialog(currentDocument, printService,
                currentDocument != null ? currentDocument.getCurrentPage() : 0);
    }

    @FXML
    private void handleExit() {
        performExit();
    }

    public void performExit() {
        if (applicationLifecycleManager != null && tabManager != null) {
            for (org.pdflite.model.DocumentContext context : tabManager.getTabContextMap().values()) {
                applicationLifecycleManager.performExit(context.getDocument());
            }
        }
        System.exit(0);
    }

    /**
     * Notifies the controller that the window is maximized on startup.
     * This updates the maximize button icon to show the restore icon.
     */
    public void notifyWindowMaximized() {
        if (titleBarManager != null) {
            titleBarManager.setMaximizedState(true);
        }
    }

    // Custom Title Bar Handlers
    @FXML
    private void handleMinimize() {
        if (titleBarManager != null) {
            Stage stage = (Stage) rootPane.getScene().getWindow();
            titleBarManager.handleMinimize(stage);
        }
    }

    @FXML
    private void handleMaximize() {
        if (titleBarManager != null) {
            Stage stage = (Stage) rootPane.getScene().getWindow();
            titleBarManager.handleMaximize(stage);
        }
    }

    @FXML
    private void handleClose() {
        if (titleBarManager != null) {
            titleBarManager.handleClose();
        } else {
            performExit();
        }
    }

    @FXML
    private void handleToggleToolbar() {
        if (toolbar == null) return;

        boolean isToolbarVisible = toolbar.isVisible();

        if (isToolbarVisible) {
            // Hide toolbar
            toolbar.setManaged(false);
            toolbar.setVisible(false);

            // Update menu item text
            if (toggleToolbarMenuItem != null) {
                toggleToolbarMenuItem.setText("Show Toolbar");
            }
        } else {
            // Show toolbar
            toolbar.setManaged(true);
            toolbar.setVisible(true);

            // Update menu item text
            if (toggleToolbarMenuItem != null) {
                toggleToolbarMenuItem.setText("Hide Toolbar");
            }
        }
    }

    @FXML
    private void handleDeletePage() {
        if (pageDeletionManager != null) {
            pageDeletionManager.handleDeletePage(getActiveDocument());
        }
    }

    @FXML
    private void handleDuplicatePage() {
        if (pageOperationsManager != null) {
            pageOperationsManager.handleDuplicatePage(getActiveDocument());
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
        PDFDocument currentDocument = getActiveDocument();
        if (currentDocument != null && currentDocument.getCurrentPage() > 0) {
            navigationHelper.navigateToPage(currentDocument.getCurrentPage() - 1);
        }
    }

    @FXML
    private void handleNextPage() {
        PDFDocument currentDocument = getActiveDocument();
        if (currentDocument != null
                && currentDocument.getCurrentPage() < currentDocument.getTotalPages() - 1) {
            navigationHelper.navigateToPage(currentDocument.getCurrentPage() + 1);
        }
    }

    @FXML
    private void handleGoToPage() {
        PDFDocument currentDocument = getActiveDocument();
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
    private void setSystemTheme() {
        if (themeManager != null) {
            themeManager.setSystemTheme();
        }
    }

    @FXML
    private void setLightTheme() {
        if (themeManager != null) {
            themeManager.setLightTheme();
        }
    }

    @FXML
    private void setDarkTheme() {
        if (themeManager != null) {
            themeManager.setDarkTheme();
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
        searchDialogManager.openSearchDialog(getActiveDocument(), this);
    }

    public void highlightSearchResults(List<SearchResult> results) {
        searchManager.showResults(results);
    }

    public void highlightSearchResult(SearchResult result) {
        searchManager.navigateToResult(result);
    }

    // ==================== Image Insertion Operations ====================

    @FXML
    private void handleInsertImage() {
        imageInsertionManager.openInsertImageDialog(getActiveDocument());
    }

    @FXML
    private void handleInsertStamp() {
        imageInsertionManager.openInsertStampDialog(getActiveDocument());
    }

    @FXML
    private void handleAddWatermark() {
        imageInsertionManager.openWatermarkDialog(getActiveDocument());
    }

    @FXML
    private void handleEditText() {
        imageInsertionManager.showTextEditingInfo(getActiveDocument());
    }

    // ==================== PDF Encryption/Decryption ====================

    @FXML
    private void handleShowPDFPermissions() {
        encryptionManager.showPDFPermissions(getActiveDocument());
    }

    @FXML
    private void handleEncryptPDF() {
        encryptionManager.encryptPDF(getActiveDocument());
    }

    @FXML
    private void handleDecryptPDF() {
        encryptionManager.decryptPDF(getActiveDocument());
    }

    // ==================== About Dialog ====================

    @FXML
    private void handleAbout() {
        dialogManager.showAboutDialog();
    }

    // ==================== Document Properties ====================

    @FXML
    private void handleDocumentProperties() {
        if (documentPropertiesManager != null) {
            documentPropertiesManager.openDocumentPropertiesDialog(getActiveDocument());
        }
    }

    @FXML
    private void handleOptimizePDF() {
        if (pdfOptimizationManager != null) {
            pdfOptimizationManager.openOptimizationDialog(getActiveDocument());
        }
    }

    // ==================== Merge and Split Operations ====================

    @FXML
    private void handleMergePDFs() {
        dialogManager.openMergeDialog();
    }

    @FXML
    private void handleSplitPDF() {
        dialogManager.openSplitDialog(getActiveDocument());
    }

    // ==================== Rotation Operations ====================

    @FXML
    private void handleRotateLeft() {
        PDFDocument currentDocument = getActiveDocument();
        RenderingManager currentRenderingManager = getCurrentRenderingManager();
        documentOperationManager.rotateDocument(currentDocument, -90, currentRenderingManager);
        if (saveStatusManager != null) {
            saveStatusManager.triggerAutoSave();
        }
    }

    @FXML
    private void handleRotateRight() {
        PDFDocument currentDocument = getActiveDocument();
        RenderingManager currentRenderingManager = getCurrentRenderingManager();
        documentOperationManager.rotateDocument(currentDocument, 90, currentRenderingManager);
        if (saveStatusManager != null) {
            saveStatusManager.triggerAutoSave();
        }
    }

    /**
     * Gets the current rendering manager from the active tab.
     */
    private RenderingManager getCurrentRenderingManager() {
        org.pdflite.model.DocumentContext context = getCurrentContext();
        return context != null ? context.getRenderingManager() : renderingManager;
    }

    @FXML
    private void handleExtractPages() {
        dialogManager.openExtractDialog(getActiveDocument());
    }

    @FXML
    private void handleReorderPages() {
        PDFDocument currentDocument = getActiveDocument();
        dialogManager.openPageReorderDialog(currentDocument, () -> {
            // Callback: Refresh view after successful reorder
            Platform.runLater(() -> {
                // Clear all caches to force re-render with a new page order
                currentDocument.clearCache();
                pageRenderer.clearCache();

                // Re-render all pages
                renderingManager.renderAllPages();

                // Update status
                uiStateManager.updateStatus("Pages reordered - Don't forget to save!");

                // Trigger auto-save
                if (saveStatusManager != null) {
                    saveStatusManager.triggerAutoSave();
                }

                logger.info("View refreshed after page reorder");
            });
        });
    }


    public BorderPane getRootPane() {
        return rootPane;
    }

    public ScrollPane getScrollPane() {
        return getCurrentScrollPane();
    }

    public ScrollHandler getScrollHandler() {
        return tabManager != null ? tabManager.getCurrentScrollHandler() : scrollHandler;
    }

    public VBox getPagesContainer() {
        return getCurrentPagesContainer();
    }

    // This method is called by external classes, so keep the name
    public PDFDocument getCurrentDocument() {
        org.pdflite.model.DocumentContext context = getCurrentContext();
        return context != null ? context.getDocument() : null;
    }

    public double getCurrentZoom() {
        return zoomManager != null ? zoomManager.getCurrentZoom() : Constants.DEFAULT_ZOOM;
    }

    public boolean isHighlightModeActive() {
        return highlightModeActive;
    }

    public int getTotalPages() {
        PDFDocument doc = getCurrentDocument();
        return doc != null ? doc.getTotalPages() : 0;
    }

    /**
     * Updates page info (called by NavigationHelper).
     */
    public void updatePageInfo() {
        PDFDocument doc = getCurrentDocument();
        if (doc != null) {
            pageInfoManager.updatePageInfo(doc);
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
        AnnotationManager currentAnnotationManager = getCurrentAnnotationManager();
        if (currentAnnotationManager != null) {
            currentAnnotationManager.updateAnnotationModeForAllPages(mode);
        }
    }

    private void updateDrawingStyleForAllPages() {
        AnnotationManager currentAnnotationManager = getCurrentAnnotationManager();
        if (currentAnnotationManager != null && colorPicker != null && strokeWidthSlider != null) {
            currentAnnotationManager.updateDrawingStyleForAllPages(colorPicker.getValue(), strokeWidthSlider.getValue());
        }
    }

    private void updateHighlightColorForAllPages() {
        AnnotationManager currentAnnotationManager = getCurrentAnnotationManager();
        if (currentAnnotationManager != null && highlightColorPicker != null) {
            currentAnnotationManager.updateHighlightColorForAllPages(highlightColorPicker.getValue());
        }
    }

    public javafx.scene.paint.Color getHighlightColor() {
        return highlightColorPicker != null ? highlightColorPicker.getValue() : javafx.scene.paint.Color.YELLOW;
    }

    private void makeToggleButtonDeselectable(ToggleButton btn) {
        AnnotationManager currentAnnotationManager = getCurrentAnnotationManager();
        if (currentAnnotationManager != null && drawingToolsGroup != null) {
            currentAnnotationManager.makeToggleButtonDeselectable(btn, drawingToolsGroup);
        }
    }

    @FXML
    private void handleUndo() {
        if (undoRedoManager != null) {
            undoRedoManager.handleUndo();
        }
    }

    @FXML
    private void handleRedo() {
        if (undoRedoManager != null) {
            undoRedoManager.handleRedo();
        }
    }

    // ==================== INSERT PAGE ====================
    @FXML
    private void handleInsertPage() {
        PDFDocument currentDocument = getActiveDocument();
        VBox pagesContainer = getCurrentPagesContainer();
        ScrollPane scrollPane = getCurrentScrollPane();
        
        if (currentDocument == null) {
            uiStateManager.showError("No PDF", "Please open a PDF file first.");
            return;
        }

        InsertDialogController controller = dialogManager.openInsertDialog(currentDocument);
        if (controller == null || controller.isInsertClicked()) {
            return;
        }

        AtomicReference<VBox> pagesContainerRef = new AtomicReference<>(pagesContainer);

        PDFDocument updatedDocument = documentOperationManager.insertBlankPages(
                currentDocument, controller, pagesContainerRef, loadingPages,
                pageRenderer, scrollHandler, scrollPane);

        if (updatedDocument != null) {
            VBox updatedContainer = pagesContainerRef.get();
            // Update context with new container
            org.pdflite.model.DocumentContext context = getCurrentContext();
            if (context != null && updatedContainer != null) {
                // Recreate annotation manager with updated container
                AnnotationManager newAnnotationManager = new AnnotationManager(updatedContainer, uiStateManager, currentDocument);
                context.setAnnotationManager(newAnnotationManager);
            }
        }
    }

    // ==================== BOOKMARK OPERATIONS ====================
    
    /**
     * Toggles the bookmark sidebar visibility.
     */
    @FXML
    private void handleToggleBookmarks() {
        if (bookmarkUIManager != null) {
            bookmarkUIManager.handleToggleBookmarks(getActiveDocument());
        }
    }

    /**
     * Adds a bookmark for the current page.
     */
    @FXML
    private void handleAddBookmark() {
        if (bookmarkUIManager != null) {
            bookmarkUIManager.handleAddBookmark(getActiveDocument());
        }
    }
}

