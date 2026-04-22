package org.pdflite.controller;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.pdflite.manager.ChatUIManager;
import org.pdflite.service.GroqService;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.pdflite.dialog.SettingsDialog;
import org.pdflite.dialog.KeyboardShortcutsDialog;
import org.pdflite.manager.*;
import org.pdflite.model.AnnotationLineStyle;
import org.pdflite.model.PDFDocument;
import org.pdflite.model.SearchResult;
import org.pdflite.service.PDFPrintService;
import org.pdflite.service.PDFService;
import org.pdflite.util.Constants;
import org.pdflite.util.NavigationHelper;
import org.pdflite.view.AnnotationLayer;
import org.pdflite.view.ContextMenuPane;
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

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }
    
    @FXML
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
    private Button openButton;
    @FXML
    private Button saveButton;
    @FXML
    private Button printButton;
    @FXML
    private Button prevButton;
    @FXML
    private Button nextButton;
    @FXML
    private Button zoomOutButton;
    @FXML
    private Button zoomInButton;
    @FXML
    private Button bookmarksButton;
    @FXML
    private Button aiChatButton;
    @FXML
    private Tooltip aiChatTooltip;
    @FXML
    private MenuBar menuBar;
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
    private ComboBox<AnnotationLineStyle> lineStyleComboBox;
    @FXML
    private Slider opacitySlider;
    @FXML
    private Label opacityLabel;
    @FXML
    private Label lineStyleTitleLabel;
    @FXML
    private Label opacityTitleLabel;
    @FXML
    private Label drawingToolsLabel;
    @FXML
    private Label drawingColorLabel;
    @FXML
    private Label highlightColorLabel;
    @FXML
    private Label strokeWidthTitleLabel;
    @FXML
    private ToggleGroup drawingToolsGroup;
    @FXML
    private ToggleButton btnDrawRect;
    @FXML
    private ToggleButton btnDrawCircle;
    @FXML
    private ToggleButton btnDrawArrow;
    @FXML
    private ToggleButton btnDrawFreehand;
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
    private javafx.scene.control.MenuItem toggleSidebarMenuItem;
    @FXML
    private javafx.scene.control.MenuItem fullScreenMenuItem;
    @FXML
    private javafx.scene.control.MenuItem presentationModeMenuItem;
    @FXML
    private RadioMenuItem systemThemeItem;
    @FXML
    private RadioMenuItem lightThemeItem;
    @FXML
    private RadioMenuItem darkThemeItem;
    @FXML
    private ToggleButton bookmarkToggleButton;
    @FXML
    private Menu languageMenu;
    @FXML
    private RadioMenuItem englishItem;
    @FXML
    private RadioMenuItem vietnameseItem;


    // ==================== Services and Managers ====================

    private PDFService pdfService;
    private PDFPrintService printService;
    private NavigationHelper navigationHelper;
    private PageRenderer pageRenderer;
    private ScrollHandler scrollHandler;
    private LanguageManager languageManager;

    // Managers
    private UILanguageManager uiLanguageManager;
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
    private PageLabelManager pageLabelManager;

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

    // PDF Outline Bookmark Manager
    private PDFOutlineBookmarkManager pdfOutlineBookmarkManager;

    // Smart Bookmark Manager
    private SmartBookmarkManager smartBookmarkManager;

    // Tab Manager
    private TabManager tabManager;

    // Save Manager
    private SaveManager saveManager;

    // AI Chat
    private GroqService groqService;
    private ChatUIManager chatUIManager;

    // OCR Manager
    private OCRManager ocrManager;

    // Reading Mode Manager
    private ReadingModeManager readingModeManager;

    // Auto Hide UI Manager
    private AutoHideUIManager autoHideUIManager;

    // New refactored managers
    private ToolbarManager toolbarManager;
    private HighlightModeManager highlightModeManager;
    private NavigationManager navigationManager;
    private PageReorderUIManager pageReorderUIManager;
    private PageInsertManager pageInsertManager;
    private AIChatManager aiChatManager;
    private AnnotationExchangeManager annotationExchangeManager;
    private PresentationViewController presentationViewController;

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
        scrollHandler = new ScrollHandler(pageRenderer, null);

        // Create helpers
        navigationHelper = new NavigationHelper(this, pdfService, renderExecutor, loadingPages);
        searchManager = new SearchManager(this, navigationHelper);

        // Initialize a recent files manager (needed by RecentFilesMenuManager)
        recentFilesManager = new RecentFilesManager();

        // Initialize language manager
        languageManager = LanguageManager.getInstance();
        
        // Initialize UI language manager (will set components later)
        uiLanguageManager = new UILanguageManager(languageManager);
        uiLanguageManager.initializeLanguageSelection();
        languageManager.addLanguageChangeListener(() -> uiLanguageManager.updateUILanguage());

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
        imageInsertionManager = new ImageInsertionManager(rootPane, uiStateManager, pageRenderer, null);
        imageInsertionManager.setRenderingManagerSupplier(this::getCurrentRenderingManager);

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
                installCustomColorGuard(newScene);
                themeManager = new ThemeManager(newScene, logoImageView);
                themeManager.setThemeMenuItems(systemThemeItem, lightThemeItem, darkThemeItem);
                searchDialogManager.setThemeManager(themeManager);

                if (saveStatusManager == null && saveStatusIndicator != null) {
                    saveStatusManager = new SaveStatusManager(saveStatusIndicator, autoSaveManager, uiStateManager);
                }

                if (dialogManager != null) dialogManager = new DialogManager(rootPane, themeManager, uiStateManager);
                if (encryptionManager != null)
                    encryptionManager = new EncryptionManager(rootPane, pdfService, themeManager, uiStateManager);

                imageInsertionManager = new ImageInsertionManager(rootPane, uiStateManager, pageRenderer, themeManager);
                imageInsertionManager.setRenderingManagerSupplier(this::getCurrentRenderingManager);

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
                if (annotationExchangeManager != null) {
                    annotationExchangeManager.setThemeManager(themeManager);
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
            pageNumberField.setOnAction(e -> {
                if (navigationManager != null) {
                    navigationManager.handleGoToPage();
                }
            });
            pageNumberField.setTextFormatter(new TextFormatter<>(change -> {
                String next = change.getControlNewText();
                return next.matches("[A-Za-z0-9\\-]*") ? change : null;
            }));
        }

        // Setup drawing tool icons
        drawingToolIconManager.setupDrawingToolIcons(btnDrawRect, btnDrawCircle, btnDrawArrow, btnDrawFreehand);
        drawingToolIconManager.setupUndoIcon(undoButton);
        drawingToolIconManager.setupRedoIcon(redoButton);

        // Setup menu hover to show on mouse enter
        setupMenuHoverBehavior();

        uiStateManager.updateUIState(false);

        // Check for recovery files on startup
        Platform.runLater(() -> {
            if (recoveryManager != null) {
                recoveryManager.checkForRecovery(this::openPDFFile);
            }
        });

        // Setup keyboard shortcuts and auto-hide UI
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null && keyboardShortcutManager != null) {
                keyboardShortcutManager.removeKeyboardShortcuts(oldScene);
            }
            if (newScene != null) {
                if (keyboardShortcutManager != null) {
                    keyboardShortcutManager.setupKeyboardShortcuts(newScene);
                }
                if (autoHideUIManager != null) {
                    autoHideUIManager.setupSceneTracking(newScene);
                }
            }
        });
    }

    private void installCustomColorGuard(javafx.scene.Scene scene) {
        scene.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            Object target = event.getTarget();
            if (!(target instanceof Hyperlink hyperlink)) {
                return;
            }

            String text = hyperlink.getText();
            if (text == null || !text.toLowerCase().contains("custom")) {
                return;
            }

            javafx.scene.Node parent = hyperlink;
            while (parent != null) {
                if (parent.getStyleClass().contains("color-picker-popup")) {
                    event.consume();
                    if (uiStateManager != null) {
                        uiStateManager.updateStatus("Custom color is disabled to avoid runtime crash");
                    }
                    return;
                }
                parent = parent.getParent();
            }
        });
    }

    /**
     * Sets up hover behavior for menu bar - menus open on mouse hover without clicking.
     */
    private void setupMenuHoverBehavior() {
        if (menuBar == null) return;

        // Wait for skin to be applied
        menuBar.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                Platform.runLater(this::installMenuHoverHandlers);
            }
        });
        
        // Also try immediately if skin is already set
        if (menuBar.getSkin() != null) {
            Platform.runLater(this::installMenuHoverHandlers);
        }
    }

    /**
     * Installs mouse hover handlers on menu bar buttons to open on hover.
     */
    private void installMenuHoverHandlers() {
        // Find container holding menu buttons
        for (javafx.scene.Node child : menuBar.getChildrenUnmodifiable()) {
            if (child instanceof javafx.scene.layout.HBox container) {
                installHoverOnContainer(container);
                return;
            }
        }
        
        // Try lookup as fallback
        javafx.scene.Node container = menuBar.lookup(".container");
        if (container instanceof javafx.scene.layout.HBox hbox) {
            installHoverOnContainer(hbox);
        }
    }

    /**
     * Installs hover handlers on the menu button container to open menus on hover.
     */
    private void installHoverOnContainer(javafx.scene.layout.HBox container) {
        var children = container.getChildren();
        
        for (int i = 0; i < children.size() && i < menuBar.getMenus().size(); i++) {
            javafx.scene.Node menuButton = children.get(i);
            final int menuIndex = i;
            
            // Open menu on hover (no click needed)
            menuButton.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
                Menu targetMenu = menuBar.getMenus().get(menuIndex);
                if (!targetMenu.isShowing()) {
                    // Hide any other showing menu first
                    for (Menu m : menuBar.getMenus()) {
                        if (m.isShowing()) {
                            m.hide();
                        }
                    }
                    targetMenu.show();
                }
            });
        }
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
        keyboardShortcutManager = new KeyboardShortcutManager(
                undoRedoManager,
                this::handleKeyboardShortcuts,
                this::handlePreviousPage,
                this::handleNextPage,
                this::handleZoomIn,
                this::handleZoomOut,
                this::handleFitToWidth,
                this::handleFitToPage,
                this::handleSearchLeft,
                this::handleHideSearch,
                this::handleToggleFullScreen,
                this::handleTogglePresentationMode,
                () -> presentationViewController != null && presentationViewController.isActive(),
                this::handleExitPresentationMode);

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
        pdfOptimizationManager.setRenderingManagerSupplier(this::getCurrentRenderingManager);

        // Page Operations Manager
        pageOperationsManager = new PageOperationsManager(
                uiStateManager, pageDuplicationManager, renderingManager,
                pageInfoManager, saveStatusManager, pageRenderer, themeManager);
        pageOperationsManager.setRenderingManagerSupplier(this::getCurrentRenderingManager);

        // Drawing Tools Setup Manager
        drawingToolsSetupManager = new DrawingToolsSetupManager(pageRenderer, uiStateManager);

        // Setup color pickers and drawing tools
        drawingToolsSetupManager.setUpdateDrawingStyleCallback(this::updateDrawingStyleForAllPages);
        drawingToolsSetupManager.setUpdateHighlightColorCallback(this::updateHighlightColorForAllPages);
        drawingToolsSetupManager.setupColorPicker(colorPicker, this::updateDrawingStyleForAllPages);
        drawingToolsSetupManager.setupHighlightColorPicker(highlightColorPicker, this::updateHighlightColorForAllPages);
        drawingToolsSetupManager.setupStrokeWidthSlider(strokeWidthSlider, strokeWidthLabel,
                this::updateDrawingStyleForAllPages);
        drawingToolsSetupManager.setupLineStyleSelector(lineStyleComboBox, this::updateDrawingStyleForAllPages);
        drawingToolsSetupManager.setupOpacitySlider(opacitySlider, opacityLabel, this::updateAnnotationOpacityForAllPages);

        // Save Manager
        saveManager = new SaveManager(fileManager, documentLifecycleManager, highlightPersistenceManager,
                autoSaveManager, saveStatusManager, uiStateManager, dialogManager);

        // Initialize TabManager with DocumentSetupManager and DrawingToolsSetupManager
        tabManager = new TabManager(documentTabPane, pdfService, fileManager, pageRenderer, zoomManager,
                pageInfoManager, uiStateManager, recentFilesManager, recentFilesMenuManager,
                documentSetupManager, drawingToolsSetupManager, bookmarkManager, autoSaveManager, pageLabelManager);
        tabManager.setRenderingManager(renderingManager);
        tabManager.setZoomChangeListener(zoomChangeListener);
        tabManager.setScrollHandler(scrollHandler);
        tabManager.setDrawingToolsSuppliers(
                () -> drawingToolsGroup,
                () -> btnDrawRect,
                () -> btnDrawCircle,
                () -> btnDrawArrow,
                () -> btnDrawFreehand
        );
        tabManager.setColorPickerSuppliers(
                () -> colorPicker,
                () -> highlightColorPicker,
                () -> strokeWidthSlider,
                () -> lineStyleComboBox,
                () -> opacitySlider
        );
        tabManager.setOnTabSwitched(doc -> {
            renderingManager = tabManager.getRenderingManager();
            annotationManager = tabManager.getAnnotationManager();
        });
        tabManager.initialize();

        // Setup text edit callback
        setupTextEditCallback();

        // Initialize new refactored managers
        initializeNewManagers();
    }

    /**
     * Initializes all manager classes.
     */
    private void initializeManagers() {
        // UI State Manager (needed by other managers)
        uiStateManager = new UIStateManager(statusLabel, prevButton, nextButton, pageNumberField, zoomComboBox, () -> themeManager);

        // Set UI components for language manager now that uiStateManager is ready
        uiLanguageManager.setUIComponents(
            titleLabel, menuBar, toolbar, openButton, saveButton, printButton,
            prevButton, nextButton, zoomOutButton, zoomInButton, bookmarksButton,
            aiChatButton, aiChatTooltip, drawingToolsMenu, drawingToolsLabel,
            drawingColorLabel, highlightColorLabel, strokeWidthTitleLabel, lineStyleTitleLabel, opacityTitleLabel,
            englishItem, vietnameseItem, toggleToolbarMenuItem, toggleSidebarMenuItem,
            fullScreenMenuItem, presentationModeMenuItem, uiStateManager
        );
        
        // Update UI with current language
        uiLanguageManager.updateUILanguage();

        // Create ZoomManager first (without listener)
        zoomManager = new ZoomManager(pdfService, null);
        zoomManager.initialize(zoomComboBox, null);

        // Rendering Manager - needs zoomManager
        renderingManager = new RenderingManager(pdfService, pageRenderer, scrollHandler, zoomManager);

        // Now create zoom change listener with renderingManager supplier
        zoomChangeListener = ListenerFactory.createZoomChangeListener(
                () -> renderingManager,
                () -> zoomManager,
                searchManager,
                uiStateManager
        );

        // Set the listener to zoomManager
        zoomManager.setZoomChangeListener(zoomChangeListener);

        // File Manager
        fileManager = new FileManager(pdfService, ListenerFactory.createFileOperationListener(uiStateManager), () -> themeManager);

        // Fullscreen Manager
        fullscreenManager = new FullscreenManager(rootPane, toolbar, ListenerFactory.createFullscreenListener(uiStateManager));

        // Page Info Manager
        pageLabelManager = new PageLabelManager();
        pageInfoManager = new PageInfoManager(totalPagesLabel, pageNumberField, prevButton, nextButton, pageLabelManager);

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
        pageDeletionManager.setRenderingManagerSupplier(this::getCurrentRenderingManager);

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

        // Annotation XFDF exchange manager
        annotationExchangeManager = new AnnotationExchangeManager(
                rootPane,
                uiStateManager,
                this::getActiveDocument,
                this::getCurrentAnnotationManager
        );

        // PDF Outline Bookmark Manager
        pdfOutlineBookmarkManager = new PDFOutlineBookmarkManager(bookmarkManager);

        // Smart Bookmark Manager (with AI support)
        smartBookmarkManager = new SmartBookmarkManager(bookmarkManager);
        if (groqService != null) {
            smartBookmarkManager.setGroqService(groqService);
        }

        // Set extended managers for BookmarkUIManager
        bookmarkUIManager.setExtendedManagers(
                pdfOutlineBookmarkManager,
                smartBookmarkManager,
                this::getActiveDocument
        );

        // Set callback to update icon after auto-save
        autoSaveManager.setOnAutoSaveCallback(() -> {
            if (saveStatusManager != null) {
                saveStatusManager.updateSaveStatusIndicator(true);
            }
            uiStateManager.updateStatus(lang().getString("autosave.saved"));
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

    /**
     * Initializes new refactored managers for separated logic.
     */
    private void initializeNewManagers() {
        // Toolbar Manager
        toolbarManager = new ToolbarManager(toolbar, toggleToolbarMenuItem);

        // Highlight Mode Manager
        highlightModeManager = new HighlightModeManager(
                uiStateManager,
                pageRenderer,
                () -> drawingToolsGroup,
                this::getCurrentPagesContainer,
                this::updateAnnotationModeForAllPages
        );

        // Navigation Manager
        navigationManager = new NavigationManager(
                navigationHelper,
                pageInfoManager,
                uiStateManager,
                this::getActiveDocument,
                pageLabelManager
        );

        // Page Reorder UI Manager
        pageReorderUIManager = new PageReorderUIManager(
                dialogManager,
                uiStateManager,
                pageRenderer,
                saveStatusManager
        );
        pageReorderUIManager.setDocumentSupplier(this::getActiveDocument);
        pageReorderUIManager.setRenderingManagerSupplier(this::getCurrentRenderingManager);

        // Page Insert Manager
        pageInsertManager = new PageInsertManager(
                uiStateManager,
                dialogManager,
                documentOperationManager,
                pageRenderer,
                scrollHandler,
                loadingPages
        );
        pageInsertManager.setContextSupplier(this::getCurrentContext);
        pageInsertManager.setDocumentSupplier(this::getActiveDocument);
        pageInsertManager.setPagesContainerSupplier(this::getCurrentPagesContainer);
        pageInsertManager.setScrollPaneSupplier(this::getCurrentScrollPane);
        pageInsertManager.setRenderingManagerSupplier(this::getCurrentRenderingManager);

        // AI Chat Manager
        aiChatManager = new AIChatManager(
                rootPane,
                uiStateManager,
                pageInfoManager,
                navigationHelper,
                pageRenderer
        );
        aiChatManager.setDocumentSupplier(this::getActiveDocument);
        aiChatManager.setRenderingManagerSupplier(this::getCurrentRenderingManager);
        aiChatManager.setStageSupplier(() -> (Stage) rootPane.getScene().getWindow());
        aiChatManager.setBookmarkManager(bookmarkManager);
        if (themeManager != null) {
            aiChatManager.setThemeManager(themeManager);
        }
        if (groqService != null) {
            aiChatManager.setGroqService(groqService);
        }

        // OCR Manager
        ocrManager = new OCRManager(rootPane, uiStateManager);
        ocrManager.setDocumentSupplier(this::getActiveDocument);
        if (themeManager != null) {
            ocrManager.setThemeManager(themeManager);
        }

        // Reading Mode Manager
        readingModeManager = new ReadingModeManager(uiStateManager);
        readingModeManager.setPagesContainerSupplier(this::getCurrentPagesContainer);
        
        // Connect reading mode to page renderer for new pages
        if (pageRenderer != null) {
            pageRenderer.setReadingModeEffectSupplier(() -> readingModeManager.getEffect());
        }

        // Auto Hide UI Manager
        autoHideUIManager = new AutoHideUIManager(menuBar, toolbar, rootPane);
        autoHideUIManager.setTabPane(documentTabPane);

        // Presentation Mode Controller
        presentationViewController = new PresentationViewController(
                rootPane,
                documentTabPane,
                uiStateManager,
                this::getCurrentContext,
                () -> rootPane.getScene() == null ? null : (Stage) rootPane.getScene().getWindow(),
                this::navigatePreviousPageInternal,
                this::navigateNextPageInternal
        );
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
            
            // Set current document for bookmark manager
            PDFDocument doc = getActiveDocument();
            if (doc != null && bookmarkManager != null) {
                bookmarkManager.setCurrentDocument(doc);
            }
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
    private void handleExportAnnotations() {
        if (annotationExchangeManager != null) {
            annotationExchangeManager.handleExportAnnotations();
        }
    }

    @FXML
    private void handleImportAnnotations() {
        if (annotationExchangeManager != null) {
            annotationExchangeManager.handleImportAnnotations();
        }
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
        if (presentationViewController != null && presentationViewController.isActive()) {
            presentationViewController.exit();
        }
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
        if (toolbarManager != null) {
            toolbarManager.handleToggleToolbar();
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
        if (presentationViewController != null && presentationViewController.isActive()) {
            presentationViewController.navigatePrevious();
            return;
        }
        navigatePreviousPageInternal();
    }

    @FXML
    private void handleNextPage() {
        if (presentationViewController != null && presentationViewController.isActive()) {
            presentationViewController.navigateNext();
            return;
        }
        navigateNextPageInternal();
    }

    private void navigatePreviousPageInternal() {
        if (navigationManager != null) {
            navigationManager.handlePreviousPage();
        }
    }

    private void navigateNextPageInternal() {
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
        if (pageRenderer != null && pageRenderer.getContextMenuHandler() != null
                && pageRenderer.getContextMenuHandler().hasTextAtPosition()) {
            // PDF-app style behavior: if text is selected, Ctrl+H highlights selection immediately.
            pageRenderer.getContextMenuHandler().handleHighlightSelection();
            clearContextMenuSelections();
            if (highlightModeManager != null) {
                highlightModeManager.deactivateHighlightMode();
            }
            return;
        }

        // One-shot behavior: no persistent drag-highlight mode.
        if (highlightModeManager != null) {
            highlightModeManager.deactivateHighlightMode();
        }
        if (uiStateManager != null) {
            uiStateManager.updateStatus(lang().getString("highlight.selectTextFirst"));
        }
    }

    private void clearContextMenuSelections() {
        VBox pagesContainer = getCurrentPagesContainer();
        if (pagesContainer == null) {
            return;
        }

        for (javafx.scene.Node node : pagesContainer.getChildren()) {
            if (!(node instanceof VBox pageBox) || pageBox.getChildren().isEmpty()) {
                continue;
            }

            javafx.scene.Node firstChild = pageBox.getChildren().getFirst();
            if (!(firstChild instanceof StackPane stackPane)) {
                continue;
            }

            for (javafx.scene.Node layer : stackPane.getChildren()) {
                if (layer instanceof ContextMenuPane contextMenuPane) {
                    contextMenuPane.clearSelection();
                }
            }
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

    // ==================== Language Operations ====================

    @FXML
    private void setEnglish() {
        languageManager.setLocale(LanguageManager.ENGLISH);
    }

    @FXML
    private void setVietnamese() {
        languageManager.setLocale(LanguageManager.VIETNAMESE);
    }

    // ==================== Fullscreen Operations ====================

    @FXML
    private void handleToggleFullScreen() {
        fullscreenManager.toggleFullScreen();
    }

    @FXML
    private void handleTogglePresentationMode() {
        if (presentationViewController != null) {
            presentationViewController.toggle();
        }
    }

    private void handleExitPresentationMode() {
        if (presentationViewController != null && presentationViewController.isActive()) {
            presentationViewController.exit();
        }
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

    @FXML
    private void handleToggleSidebar() {
        if (tabManager != null) {
            tabManager.toggleCurrentSidebar();
        }
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

    public void hideSearchPanel() {
        searchManager.clearSearch();
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
    private void handleDigitalSignature() {
        encryptionManager.digitalSignature(getActiveDocument());
    }

    @FXML
    private void handleVerifySignatures() {
        encryptionManager.verifySignatures(getActiveDocument());
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

    @FXML
    private void handleKeyboardShortcuts() {
        KeyboardShortcutsDialog.show(themeManager);
    }

    @FXML
    private void handlePageLabels() {
        PDFDocument document = getActiveDocument();
        if (document == null) {
            uiStateManager.showError(lang().getString("error.noDocument"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(lang().getString("pageLabels.title"));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
        ButtonType resetType = new ButtonType(lang().getString("pageLabels.reset"), ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().add(resetType);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField startPageField = new TextField("1");
        TextField prefixField = new TextField();
        TextField startNumberField = new TextField("1");

        ComboBox<PageLabelManager.NumberingStyle> styleComboBox = new ComboBox<>();
        styleComboBox.getItems().addAll(PageLabelManager.NumberingStyle.values());
        styleComboBox.setValue(PageLabelManager.NumberingStyle.DECIMAL);
        styleComboBox.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(PageLabelManager.NumberingStyle item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : getStyleDisplayName(item));
            }
        });
        styleComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(PageLabelManager.NumberingStyle item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : getStyleDisplayName(item));
            }
        });

        grid.add(new Label(lang().getString("pageLabels.startPage")), 0, 0);
        grid.add(startPageField, 1, 0);
        grid.add(new Label(lang().getString("pageLabels.prefix")), 0, 1);
        grid.add(prefixField, 1, 1);
        grid.add(new Label(lang().getString("pageLabels.numberStyle")), 0, 2);
        grid.add(styleComboBox, 1, 2);
        grid.add(new Label(lang().getString("pageLabels.startNumber")), 0, 3);
        grid.add(startNumberField, 1, 3);

        dialog.getDialogPane().setContent(grid);
        if (themeManager != null) {
            themeManager.applyThemeToDialog(dialog.getDialogPane());
        }

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.APPLY) {
                try {
                    int startPage = Integer.parseInt(startPageField.getText().trim());
                    int startNumber = Integer.parseInt(startNumberField.getText().trim());
                    PageLabelManager.NumberingStyle style = styleComboBox.getValue();
                    pageLabelManager.applyCustomRule(document, startPage, style, prefixField.getText(), startNumber);
                    updatePageInfo();
                    if (tabManager != null) {
                        tabManager.refreshCurrentTabSidebar();
                    }
                    uiStateManager.updateStatus(lang().getString("pageLabels.applied"));
                } catch (NumberFormatException ex) {
                    uiStateManager.showError(lang().getString("error.title"), lang().getString("pageLabels.invalidInput"));
                }
            } else if (result == resetType) {
                pageLabelManager.resetToDefault(document);
                updatePageInfo();
                if (tabManager != null) {
                    tabManager.refreshCurrentTabSidebar();
                }
                uiStateManager.updateStatus(lang().getString("pageLabels.applied"));
            }
        });
    }

    private String getStyleDisplayName(PageLabelManager.NumberingStyle style) {
        return switch (style) {
            case DECIMAL -> lang().getString("pageLabels.style.decimal");
            case ROMAN_UPPER -> lang().getString("pageLabels.style.romanUpper");
            case ROMAN_LOWER -> lang().getString("pageLabels.style.romanLower");
            case LETTER_UPPER -> lang().getString("pageLabels.style.letterUpper");
            case LETTER_LOWER -> lang().getString("pageLabels.style.letterLower");
        };
    }

    // ==================== Settings Dialog ====================

    @FXML
    private void handleSettings() {
        SettingsDialog settingsDialog = new SettingsDialog(themeManager);
        boolean saved = settingsDialog.showAndWait();
        if (saved) {
            // Refresh UI after settings change
            if (uiLanguageManager != null) {
                uiLanguageManager.updateUILanguage();
            }
            logger.info("Settings saved and applied");
        }
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
        if (pageReorderUIManager != null) {
            pageReorderUIManager.handleReorderPages();
        }
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
            if (tabManager != null) {
                tabManager.syncSidebarToCurrentPage();
            }
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
            javafx.scene.paint.Color drawingColor = colorPicker.getValue() == null
                    ? javafx.scene.paint.Color.WHITE
                    : colorPicker.getValue();
            currentAnnotationManager.updateDrawingStyleForAllPages(
                    drawingColor,
                    strokeWidthSlider.getValue(),
                    getSelectedLineStyle(),
                    getSelectedOpacity());
        }
    }

    private void updateHighlightColorForAllPages() {
        AnnotationManager currentAnnotationManager = getCurrentAnnotationManager();
        if (currentAnnotationManager != null && highlightColorPicker != null) {
            javafx.scene.paint.Color highlightColor = highlightColorPicker.getValue() == null
                    ? javafx.scene.paint.Color.YELLOW
                    : highlightColorPicker.getValue();
            currentAnnotationManager.updateHighlightColorForAllPages(highlightColor, getSelectedOpacity());
        }
    }

    private void updateAnnotationOpacityForAllPages() {
        updateDrawingStyleForAllPages();
        updateHighlightColorForAllPages();
    }

    private AnnotationLineStyle getSelectedLineStyle() {
        return lineStyleComboBox != null && lineStyleComboBox.getValue() != null
                ? lineStyleComboBox.getValue()
                : AnnotationLineStyle.SOLID;
    }

    private double getSelectedOpacity() {
        return opacitySlider != null ? opacitySlider.getValue() : 1.0;
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
        if (pageInsertManager != null) {
            pageInsertManager.handleInsertPage();
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

    /**
     * Clears all bookmarks for the current document.
     */
    @FXML
    private void handleClearAllBookmarks() {
        if (bookmarkUIManager != null) {
            bookmarkUIManager.handleClearAllBookmarks();
        }
    }

    /**
     * Imports bookmarks from PDF outline (Table of Contents).
     */
    @FXML
    private void handleImportOutlineBookmarks() {
        if (bookmarkUIManager != null) {
            bookmarkUIManager.handleImportOutlineBookmarks();
        }
    }

    /**
     * Analyzes document and creates smart bookmarks based on headings/chapters.
     */
    @FXML
    private void handleSmartBookmarks() {
        if (bookmarkUIManager != null) {
            bookmarkUIManager.handleSmartBookmarks();
        }
    }

    // ==================== AI CHAT OPERATIONS ====================

    /**
     * Toggles the AI Chat sidebar.
     */
    @FXML
    private void handleOpenChat() {
        if (aiChatManager != null) {
            aiChatManager.handleOpenChat();
        }
    }

    /**
     * Opens the OCR dialog for text recognition.
     */
    @FXML
    private void handleOCR() {
        if (ocrManager != null) {
            ocrManager.openOCRDialog();
        }
    }

    // ==================== READING MODE OPERATIONS ====================

    @FXML
    private void handleNormalMode() {
        if (readingModeManager != null) {
            readingModeManager.setMode(ReadingModeManager.ReadingMode.NORMAL);
            readingModeManager.applyToPages();
        }
    }

    @FXML
    private void handleNightMode() {
        if (readingModeManager != null) {
            readingModeManager.setMode(ReadingModeManager.ReadingMode.NIGHT);
            readingModeManager.applyToPages();
        }
    }

    @FXML
    private void handleSepiaMode() {
        if (readingModeManager != null) {
            readingModeManager.setMode(ReadingModeManager.ReadingMode.SEPIA);
            readingModeManager.applyToPages();
        }
    }

    @FXML
    private void handleLowBlueMode() {
        if (readingModeManager != null) {
            readingModeManager.setMode(ReadingModeManager.ReadingMode.LOW_BLUE);
            readingModeManager.applyToPages();
        }
    }

    @FXML
    private void handleAutoHideUI() {
        if (autoHideUIManager != null) {
            autoHideUIManager.toggle();
        }
    }

    public boolean isHighlightModeActive() {
        return highlightModeManager != null && highlightModeManager.isHighlightModeActive();
    }
}
