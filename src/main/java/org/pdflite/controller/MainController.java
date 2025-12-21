package org.pdflite.controller;

import java.io.File;
import java.io.IOException;
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

    // ==================== Document State ====================

    // Multi-tab support: Map each tab to its document context
    private final java.util.Map<Tab, org.pdflite.model.DocumentContext> tabContextMap = new java.util.HashMap<>();
    private Tab welcomeTab; // Reference to the welcome tab
    
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
        scrollHandler = new ScrollHandler(pageRenderer, null); // Will be set per-tab

        // Store reference to welcome tab
        if (documentTabPane != null && !documentTabPane.getTabs().isEmpty()) {
            welcomeTab = documentTabPane.getTabs().get(0);
            
            // Setup tab selection listener to switch context
            documentTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (newTab != null && newTab != welcomeTab) {
                    switchToTabContext(newTab);
                }
            });
        }

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
                documentLifecycleManager = new DocumentLifecycleManager(pdfService, fileManager, zoomManager,
                        renderingManager, pageInfoManager, uiStateManager, themeManager, recentFilesManager,
                        recentFilesMenuManager);

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
    }

    // ==================== Multi-Tab Management ====================
    
    /**
     * Gets the current active document context.
     */
    private org.pdflite.model.DocumentContext getCurrentContext() {
        if (documentTabPane == null) return null;
        Tab selectedTab = documentTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null || selectedTab == welcomeTab) return null;
        return tabContextMap.get(selectedTab);
    }
    
    /**
     * Gets the current document from the active tab (internal use).
     */
    private PDFDocument getActiveDocument() {
        org.pdflite.model.DocumentContext context = getCurrentContext();
        return context != null ? context.getDocument() : null;
    }
    
    /**
     * Gets the current pages container from the active tab.
     */
    private VBox getCurrentPagesContainer() {
        org.pdflite.model.DocumentContext context = getCurrentContext();
        return context != null ? context.getPagesContainer() : null;
    }
    
    /**
     * Gets the current scroll pane from the active tab.
     */
    private ScrollPane getCurrentScrollPane() {
        org.pdflite.model.DocumentContext context = getCurrentContext();
        return context != null ? context.getScrollPane() : null;
    }
    
    /**
     * Gets the current annotation manager from the active tab.
     */
    private AnnotationManager getCurrentAnnotationManager() {
        org.pdflite.model.DocumentContext context = getCurrentContext();
        return context != null ? context.getAnnotationManager() : null;
    }
    
    /**
     * Switches the active context when a tab is selected.
     */
    private void switchToTabContext(Tab tab) {
        org.pdflite.model.DocumentContext context = tabContextMap.get(tab);
        if (context == null) return;
        
        PDFDocument document = context.getDocument();
        VBox pagesContainer = context.getPagesContainer();
        ScrollPane scrollPane = context.getScrollPane();
        
        // Update managers with the new context
        if (zoomManager != null) {
            zoomManager.setDocument(document);
            zoomManager.setCurrentZoom(document.getZoomLevel());
            zoomManager.initialize(zoomComboBox, scrollPane);
        }
        
        // CRITICAL: Use the RenderingManager from the context, not the global one
        if (context.getRenderingManager() != null) {
            renderingManager = context.getRenderingManager();
            renderingManager.setDocument(document);
            renderingManager.setUIComponents(pagesContainer, scrollPane, context.getContentPane());
        }
        
        // Update zoom change listener context
        if (zoomChangeListener != null) {
            zoomChangeListener.updateContext(document, pagesContainer, scrollPane);
        }
        
        if (scrollHandler != null) {
            scrollHandler.setDocument(document, pagesContainer);
            scrollHandler.setPageChangeListener(ListenerFactory.createPageChangeListener(document, pageInfoManager));
        }
        
        if (pageRenderer != null) {
            pageRenderer.setDocument(document, document.getZoomLevel());
        }
        
        // Update navigation helper
        if (navigationHelper != null) {
            // NavigationHelper uses getCurrentDocument() so it will automatically use the new context
        }
        
        // Update search manager
        if (searchManager != null) {
            // SearchManager uses getCurrentDocument() so it will automatically use the new context
        }
        
        // CRITICAL FIX: Update AutoSaveManager to track the correct document
        // This prevents auto-save from saving the wrong document when switching tabs
        if (autoSaveManager != null) {
            autoSaveManager.setDocument(document);
            logger.debug("AutoSaveManager updated to track document: {}", document.getFile().getName());
        }
        
        // Update UI
        if (pageInfoManager != null) {
            pageInfoManager.updatePageInfo(document);
        }
        
        if (uiStateManager != null) {
            uiStateManager.updateUIState(true);
            uiStateManager.updateStatus("Switched to: " + document.getFile().getName());
        }
        
        // Update annotation manager reference
        annotationManager = context.getAnnotationManager();
        
        // Sync drawing colors from UI to the new tab's annotation layers
        if (annotationManager != null) {
            if (colorPicker != null && strokeWidthSlider != null) {
                annotationManager.updateDrawingStyleForAllPages(
                    colorPicker.getValue(), strokeWidthSlider.getValue());
            }
            if (highlightColorPicker != null) {
                annotationManager.updateHighlightColorForAllPages(
                    highlightColorPicker.getValue());
            }
        }
        
        // Update bookmark manager
        if (bookmarkManager != null) {
            bookmarkManager.setCurrentDocument(document);
        }
        
        logger.info("Switched to tab: {} (page {}/{})", 
            document.getFile().getName(), 
            document.getCurrentPage() + 1, 
            document.getTotalPages());
    }
    
    /**
     * Creates a new tab for a document.
     */
    private Tab createDocumentTab(PDFDocument document, org.pdflite.model.DocumentContext context) {
        Tab tab = new Tab(document.getFile().getName());
        
        // Create the tab content
        ScrollPane scrollPane = context.getScrollPane();
        tab.setContent(scrollPane);
        
        // Store context
        tabContextMap.put(tab, context);
        
        // Handle tab close
        tab.setOnCloseRequest(event -> {
            if (!handleCloseTab(tab)) {
                event.consume(); // Cancel close if user cancels
            }
        });
        
        // Add tab and select it
        if (documentTabPane != null) {
            // Hide welcome tab when first document is opened
            if (welcomeTab != null && documentTabPane.getTabs().contains(welcomeTab)) {
                documentTabPane.getTabs().remove(welcomeTab);
            }
            
            documentTabPane.getTabs().add(tab);
            documentTabPane.getSelectionModel().select(tab);
        }
        
        return tab;
    }
    
    /**
     * Handles closing a tab.
     * @return true if tab was closed, false if cancelled
     */
    private boolean handleCloseTab(Tab tab) {
        org.pdflite.model.DocumentContext context = tabContextMap.get(tab);
        if (context == null) return true;
        
        PDFDocument document = context.getDocument();
        
        // TODO: Check if document has unsaved changes and prompt user
        
        // Close the document
        if (fileManager != null) {
            fileManager.close(document);
        }
        
        // Remove from map
        tabContextMap.remove(tab);
        
        // If no more tabs, show welcome tab
        if (documentTabPane != null && documentTabPane.getTabs().size() == 1) {
            documentTabPane.getTabs().add(0, welcomeTab);
            documentTabPane.getSelectionModel().select(welcomeTab);
            
            // Update UI to disabled state
            if (uiStateManager != null) {
                uiStateManager.updateUIState(false);
            }
        }
        
        logger.info("Closed tab: {}", document.getFile().getName());
        return true;
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

        // Set text edit callback for context menu
        setupTextEditCallback();

        // Highlight handling lives in HighlightManager (keeps MainController smaller)
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
        try {
            // Open the document
            PDFDocument newDocument = fileManager.openFile(file);
            if (newDocument == null) {
                return;
            }

            // CRITICAL: Reset to page 1 (index 0) when opening a new file
            newDocument.setCurrentPage(0);

            // Set initial zoom to 100% (1.0) for consistent display
            double initialZoom = 1.0;
            newDocument.setZoomLevel(initialZoom);

            // Create new UI components for this tab
            ScrollPane scrollPane = new ScrollPane();
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            scrollPane.getStyleClass().add("pdf-scroll-pane");
            
            StackPane contentPane = new StackPane();
            contentPane.getStyleClass().add("pdf-content-area");
            scrollPane.setContent(contentPane);
            
            VBox pagesContainer = new VBox(10);
            pagesContainer.setAlignment(javafx.geometry.Pos.TOP_CENTER);
            pagesContainer.setStyle("-fx-background-color: #808080; -fx-padding: 20;");
            contentPane.getChildren().add(pagesContainer);
            
            // Create document context
            org.pdflite.model.DocumentContext context = new org.pdflite.model.DocumentContext(
                newDocument, scrollPane, pagesContainer, contentPane);
            
            // Create tab FIRST before setting up scroll listener
            Tab tab = createDocumentTab(newDocument, context);
            
            // Create per-tab scroll handler
            ScrollHandler tabScrollHandler = new ScrollHandler(pageRenderer, scrollPane);
            tabScrollHandler.setDocument(newDocument, pagesContainer);
            tabScrollHandler.setPageChangeListener(ListenerFactory.createPageChangeListener(newDocument, pageInfoManager));
            
            // Setup scroll listener for this tab
            scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
                // Only handle scroll if this is the active tab
                if (documentTabPane.getSelectionModel().getSelectedItem() == tab) {
                    tabScrollHandler.handleScroll();
                }
            });
            
            // Create per-tab rendering manager
            RenderingManager tabRenderingManager = new RenderingManager(pdfService, pageRenderer, tabScrollHandler, zoomManager);
            tabRenderingManager.setDocument(newDocument);
            tabRenderingManager.setUIComponents(pagesContainer, scrollPane, contentPane);
            context.setRenderingManager(tabRenderingManager); // Store in context
            
            // Update page renderer with new document
            pageRenderer.setDocument(newDocument, initialZoom);
            
            // Render all pages
            tabRenderingManager.renderAllPages();
            
            // Setup document using DocumentSetupManager
            if (documentSetupManager != null) {
                AnnotationManager tabAnnotationManager = documentSetupManager.setupDocument(
                    newDocument, pagesContainer, scrollPane, zoomChangeListener, uiStateManager);
                context.setAnnotationManager(tabAnnotationManager);
            }
            
            // Setup drawing tools for this tab
            if (drawingToolsSetupManager != null && context.getAnnotationManager() != null) {
                drawingToolsSetupManager.setupDrawingToolSelection(
                    drawingToolsGroup, btnDrawRect, btnDrawCircle, btnDrawArrow,
                    pagesContainer, context.getAnnotationManager());
                
                drawingToolsSetupManager.makeToggleButtonsDeselectable(
                    btnDrawRect, btnDrawCircle, btnDrawArrow, drawingToolsGroup, context.getAnnotationManager());
            }
            
            // Now switch to the new tab context (this will update all managers including AutoSaveManager)
            switchToTabContext(tab);
            
            // Scroll to top and enable text selection
            Platform.runLater(() -> {
                scrollPane.setVvalue(0.0);
                if (pagesContainer != null) {
                    pageRenderer.setSelectionModeActive(pagesContainer, true);
                }
            });

            // Add to recent files
            recentFilesManager.addRecentFile(file.getAbsolutePath());
            recentFilesMenuManager.updateRecentFilesMenu();

            // Update status
            uiStateManager.updateStatus("Opened: " + file.getName());
            
            // Load bookmarks for the new document
            if (bookmarkManager != null) {
                bookmarkManager.setCurrentDocument(newDocument);
                logger.info("Bookmarks loaded for document: {}", file.getName());
            }

            logger.info("Successfully opened PDF in new tab: {} ({} pages)",
                    file.getName(), newDocument.getTotalPages());
                    
        } catch (IOException e) {
            logger.error("Error opening PDF file", e);
            uiStateManager.showError("Error Opening PDF", "Could not open the PDF file: " + e.getMessage());
        }
    }

    @FXML
    private void handleSave() {
        PDFDocument currentDocument = getActiveDocument();
        if (currentDocument == null) {
            return;
        }

        // Save highlights to PDF before saving document
        if (highlightPersistenceManager != null) {
            try {
                highlightPersistenceManager.saveHighlightsToPDF(
                        currentDocument.getDocument(),
                        currentDocument.getAnnotations());
                logger.info("Highlights saved to PDF");
            } catch (Exception e) {
                logger.error("Error saving highlights to PDF", e);
                uiStateManager.showError("Save Error",
                        "Failed to save highlights: " + e.getMessage());
            }
        }

        documentLifecycleManager.saveDocument(currentDocument);

        // Clear auto-save after a successful save
        if (autoSaveManager != null) {
            autoSaveManager.clearAutoSave(currentDocument);
        }

        // Update save status indicator
        if (saveStatusManager != null) {
            saveStatusManager.updateSaveStatusIndicator(true);
        }
    }

    @FXML
    private void handleSaveAs() {
        PDFDocument currentDocument = getActiveDocument();
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
        if (applicationLifecycleManager != null) {
            // Close all open documents
            for (org.pdflite.model.DocumentContext context : tabContextMap.values()) {
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

