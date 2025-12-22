package org.pdflite.controller;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.scene.control.*;
import org.pdflite.manager.*;
import org.pdflite.model.PDFDocument;
import org.pdflite.model.SearchResult;
import org.pdflite.service.PDFPrintService;
import org.pdflite.service.PDFService;
import org.pdflite.util.Constants;
import org.pdflite.util.NavigationHelper;
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

    // Toolbar Manager
    private ToolbarManager toolbarManager;

    // Highlight Manager
    private HighlightManager highlightManager;

    // Navigation Manager
    private NavigationManager navigationManager;

    // ==================== Document State ====================

    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(6);
    private final java.util.concurrent.ScheduledExecutorService autoSaveExecutor =
            Executors.newSingleThreadScheduledExecutor();
    private final Set<Integer> loadingPages = ConcurrentHashMap.newKeySet();

    // ==================== Initialization ====================

    @FXML
    public void initialize() {
        logger.info("Initializing MainController");
        pdfService = new PDFService();
        printService = new PDFPrintService(pdfService);

        // Initialize page renderer and scroll handler
        pageRenderer = new PageRenderer(pdfService, renderExecutor);
        scrollHandler = new ScrollHandler(pageRenderer, null); // Will be set per-tab

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

        // Initialize image insertion manager (will be fully initialized after rendering manager is ready)
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

                // Set theme menu items for automatic updates
                themeManager.setThemeMenuItems(systemThemeItem, lightThemeItem, darkThemeItem);

                searchDialogManager.setThemeManager(themeManager);

                // Initialize SaveStatusManager now that UI is ready
                if (saveStatusManager == null && saveStatusIndicator != null) {
                    saveStatusManager = new SaveStatusManager(saveStatusIndicator, autoSaveManager, uiStateManager);
                }

                // Cập nhật ThemeManager cho các Manager cần dùng nó
                if (dialogManager != null) dialogManager = new DialogManager(rootPane, themeManager, uiStateManager);
                if (encryptionManager != null)
                    encryptionManager = new EncryptionManager(rootPane, pdfService, themeManager, uiStateManager);

                // Cập nhật ImageInsertionManager với ThemeManager
                imageInsertionManager = new ImageInsertionManager(rootPane, uiStateManager, renderingManager, pageRenderer, themeManager);

                // Cập nhật ExportManager với ThemeManager
                if (exportManager != null) {
                    exportManager.setThemeManager(themeManager);
                }

                // Cập nhật ContextMenuHandler với ThemeManager
                if (pageRenderer != null && pageRenderer.getContextMenuHandler() != null) {
                    pageRenderer.getContextMenuHandler().setThemeManager(themeManager);
                }

                // Set theme manager supplier for PageDeletionManager
                if (pageDeletionManager != null) {
                    pageDeletionManager.setThemeManagerSupplier(() -> themeManager);
                }

                // Set theme manager for BookmarkManager
                if (bookmarkManager != null) {
                    bookmarkManager.setThemeManager(themeManager);
                }

                // Set theme manager for BookmarkUIManager
                if (bookmarkUIManager != null) {
                    bookmarkUIManager.setThemeManager(themeManager);
                }

                // Cập nhật DocumentOperationManager để nó có ThemeManager mới
                documentOperationManager = new DocumentOperationManager(pdfService, renderingManager, zoomManager,
                        pageInfoManager, uiStateManager, themeManager, fileManager);

                // Cập nhật DocumentLifecycleManager
                documentLifecycleManager = new DocumentLifecycleManager(
                        pdfService, fileManager, zoomManager,
                        renderingManager, pageInfoManager, uiStateManager, themeManager, recentFilesManager,
                        recentFilesMenuManager, highlightPersistenceManager, autoSaveManager, saveStatusManager,
                        dialogManager, null, rootPane); // applicationLifecycleManager will be set later

                recentFilesMenuManager.updateRecentFilesMenu();

                // Initialize refactored managers that need ThemeManager
                initializeRefactoredManagers();
            }
        });

        // Note: Page change listener and rendering manager UI components 
        // are now setup per-tab in openPDFFile()

        // Setup page navigation
        if (pageNumberField != null) {
            pageNumberField.setOnAction(e -> handleGoToPage());
            pageNumberField.setTextFormatter(new TextFormatter<>(change -> {
                String next = change.getControlNewText();
                return next.matches("\\d*") ? change : null;
            }));
        }

        // Note: Scroll listeners are now setup per-tab in openPDFFile()

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

        // Initialize TabManager after all managers are ready
        initializeTabManager();
    }

    // ==================== Tab Manager Initialization ====================

    /**
     * Initializes the TabManager after all other managers are ready.
     */
    private void initializeTabManager() {
        tabManager = new TabManager(
                documentTabPane,
                pdfService,
                fileManager,
                pageRenderer,
                zoomManager,
                pageInfoManager,
                uiStateManager,
                recentFilesManager,
                recentFilesMenuManager,
                documentSetupManager,
                drawingToolsSetupManager,
                highlightPersistenceManager,
                bookmarkManager,
                autoSaveManager,
                renderExecutor
        );

        // Set drawing tool components
        tabManager.setDrawingToolComponents(
                drawingToolsGroup,
                btnDrawRect,
                btnDrawCircle,
                btnDrawArrow,
                colorPicker,
                highlightColorPicker
        );

        // Set callback for tab switching to update controller state
        tabManager.setOnTabSwitched(tab -> {
            // Update controller's rendering manager reference
            renderingManager = tabManager.getCurrentRenderingManager();

            // Update annotation manager reference
            annotationManager = tabManager.getCurrentAnnotationManager();

            // Update zoom change listener context
            if (zoomChangeListener != null) {
                PDFDocument doc = tabManager.getCurrentDocument();
                VBox container = tabManager.getCurrentPagesContainer();
                ScrollPane scroll = tabManager.getCurrentScrollPane();
                if (doc != null && container != null && scroll != null) {
                    zoomChangeListener.updateContext(doc, container, scroll);
                }
            }

            // Update scroll handler
            if (scrollHandler != null) {
                PDFDocument doc = tabManager.getCurrentDocument();
                VBox container = tabManager.getCurrentPagesContainer();
                if (doc != null && container != null) {
                    scrollHandler.setDocument(doc, container);
                    scrollHandler.setPageChangeListener(
                            ListenerFactory.createPageChangeListener(doc, pageInfoManager));
                }
            }
        });
    }

    /**
     * Gets the current active document context.
     */
    private org.pdflite.model.DocumentContext getCurrentContext() {
        return tabManager != null ? tabManager.getCurrentContext() : null;
    }

    /**
     * Gets the current document from the active tab (internal use).
     */
    private PDFDocument getActiveDocument() {
        return tabManager != null ? tabManager.getCurrentDocument() : null;
    }

    /**
     * Gets the current pages container from the active tab.
     */
    private VBox getCurrentPagesContainer() {
        return tabManager != null ? tabManager.getCurrentPagesContainer() : null;
    }

    /**
     * Gets the current scroll pane from the active tab.
     */
    private ScrollPane getCurrentScrollPane() {
        return tabManager != null ? tabManager.getCurrentScrollPane() : null;
    }

    /**
     * Gets the current annotation manager from the active tab.
     */
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

        // Update DocumentLifecycleManager with ApplicationLifecycleManager
        documentLifecycleManager = new DocumentLifecycleManager(
                pdfService, fileManager, zoomManager,
                renderingManager, pageInfoManager, uiStateManager, themeManager, recentFilesManager,
                recentFilesMenuManager, highlightPersistenceManager, autoSaveManager, saveStatusManager,
                dialogManager, applicationLifecycleManager, rootPane);

        // Set context suppliers for DocumentLifecycleManager
        documentLifecycleManager.setContextSuppliers(
                this::getCurrentDocument,
                this::getCurrentContext);

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
                pageInfoManager, saveStatusManager, pageRenderer, themeManager,
                documentOperationManager, dialogManager, scrollHandler, loadingPages);

        // Set context suppliers for PageOperationsManager
        pageOperationsManager.setContextSuppliers(
                this::getCurrentDocument,
                this::getCurrentPagesContainer,
                this::getCurrentScrollPane,
                this::getCurrentContext);

        // Drawing Tools Setup Manager
        drawingToolsSetupManager = new DrawingToolsSetupManager(pageRenderer, uiStateManager);

        // Setup color pickers and drawing tools
        if (drawingToolsSetupManager != null) {
            // Set callbacks
            drawingToolsSetupManager.setUpdateDrawingStyleCallback(this::updateDrawingStyleForAllPages);
            drawingToolsSetupManager.setUpdateHighlightColorCallback(this::updateHighlightColorForAllPages);

            // Setup color pickers
            drawingToolsSetupManager.setupColorPicker(colorPicker, this::updateDrawingStyleForAllPages);
            drawingToolsSetupManager.setupHighlightColorPicker(highlightColorPicker, this::updateHighlightColorForAllPages);

            // Setup stroke width slider
            drawingToolsSetupManager.setupStrokeWidthSlider(strokeWidthSlider, strokeWidthLabel,
                    this::updateDrawingStyleForAllPages);
        }

        // Setup text edit callback
        setupTextEditCallback();
    }

    /**
     * /**
     * Initializes all manager classes.
     */
    private void initializeManagers() {
        // UI State Manager (needed by other managers)
        uiStateManager = new UIStateManager(statusLabel, prevButton, nextButton, pageNumberField, zoomComboBox, () -> themeManager);

        // Create ZoomManager first (without listener)
        // Note: Will be initialized per-tab with the tab's scrollPane
        zoomManager = new ZoomManager(pdfService, null);
        zoomManager.initialize(zoomComboBox, null); // ScrollPane will be set per-tab

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

        // Recent Files Menu Manager (needs to be initialized after recentFilesManager)
        recentFilesMenuManager = new RecentFilesMenuManager(recentFilesMenu, recentFilesManager, uiStateManager,
                this::openPDFFile);
        recentFilesMenuManager.updateRecentFilesMenu();

        // Document Lifecycle Manager
        documentLifecycleManager = new DocumentLifecycleManager(
                pdfService, fileManager, zoomManager,
                renderingManager, pageInfoManager, uiStateManager, themeManager, recentFilesManager,
                recentFilesMenuManager, highlightPersistenceManager, autoSaveManager, saveStatusManager,
                dialogManager, null, rootPane); // applicationLifecycleManager will be set later

        // Document Operation Manager
        documentOperationManager = new DocumentOperationManager(pdfService, renderingManager, zoomManager,
                pageInfoManager, uiStateManager, themeManager, fileManager);

        // Annotation Manager (will be initialized when the document is opened)
        annotationManager = null;

        // Content Stream Manager
        contentStreamManager = new org.pdflite.manager.ContentStreamManager();

        // Page Deletion Manager (needs undoRedoManager, renderingManager, pageInfoManager, pageRenderer)
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

        // Save Status Manager (needs to be created after saveStatusIndicator is injected)
        // Will be initialized in rootPane.sceneProperty listener

        // Navigation Manager
        navigationManager = new NavigationManager(
                navigationHelper, pageInfoManager, uiStateManager, this::getCurrentDocument);

        // Toolbar Manager
        toolbarManager = new ToolbarManager(toolbar, toggleToolbarMenuItem);

        // Highlight Manager (keeps MainController smaller)
        highlightManager = new HighlightManager(
                highlightColorPicker,
                uiStateManager,
                this::getCurrentDocument,
                this::getCurrentZoom,
                this::getCurrentAnnotationManager,
                pageRenderer
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
        if (textEditManager != null && pageRenderer != null) {
            textEditManager.setupTextEditCallback(pageRenderer, this::getCurrentDocument);
        }
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
            tabManager.openPDFFile(file);
        }
    }

    @FXML
    private void handleSave() {
        if (documentLifecycleManager != null) {
            documentLifecycleManager.handleSave();
        }
    }

    @FXML
    private void handleSaveAs() {
        if (documentLifecycleManager != null) {
            documentLifecycleManager.handleSaveAs();
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
        if (documentLifecycleManager != null) {
            documentLifecycleManager.performExit();
        } else {
            System.exit(0);
        }
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
        if (toolbarManager != null) {
            toolbarManager.toggleToolbar();
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
        if (navigationManager != null) {
            navigationManager.handlePreviousPage();
        }
    }

    @FXML
    private void handleNextPage() {
        if (navigationManager != null) {
            navigationManager.handleNextPage();
        }
    }

    @FXML
    private void handleGoToPage() {
        if (navigationManager != null) {
            navigationManager.handleGoToPage();
        }
    }

    // ==================== Highlight Operations ====================

    @FXML
    private void handleHighlight() {
        if (highlightManager != null) {
            highlightManager.toggleHighlightMode(drawingToolsGroup);
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
        documentOperationManager.rotateDocument(currentDocument, -90);
        if (saveStatusManager != null) {
            saveStatusManager.triggerAutoSave();
        }
    }

    @FXML
    private void handleRotateRight() {
        PDFDocument currentDocument = getActiveDocument();
        documentOperationManager.rotateDocument(currentDocument, 90);
        if (saveStatusManager != null) {
            saveStatusManager.triggerAutoSave();
        }
    }

    @FXML
    private void handleExtractPages() {
        dialogManager.openExtractDialog(getActiveDocument());
    }

    @FXML
    private void handleReorderPages() {
        if (pageOperationsManager != null) {
            pageOperationsManager.handleReorderPages();
        }
    }


    public BorderPane getRootPane() {
        return rootPane;
    }

    public ScrollPane getScrollPane() {
        return getCurrentScrollPane();
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
        return highlightManager != null && highlightManager.isHighlightModeActive();
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
        return highlightManager != null ? highlightManager.getHighlightColor() : javafx.scene.paint.Color.YELLOW;
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
        if (pageOperationsManager != null) {
            pageOperationsManager.handleInsertPage();
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

