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
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
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
    private javafx.scene.control.MenuItem toggleToolbarMenuItem;
    @FXML
    private RadioMenuItem systemThemeItem;
    @FXML
    private RadioMenuItem lightThemeItem;
    @FXML
    private RadioMenuItem darkThemeItem;


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

    // Undo/Redo Manager
    private UndoRedoManager undoRedoManager;
    
    // Page Deletion Manager
    private PageDeletionManager pageDeletionManager;
    
    // Metadata Manager
    private MetadataManager metadataManager;

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
                searchDialogManager.setThemeManager(themeManager);

                // Cập nhật ThemeManager cho các Manager cần dùng nó
                if (dialogManager != null) dialogManager = new DialogManager(rootPane, themeManager, uiStateManager);
                if (encryptionManager != null) encryptionManager = new EncryptionManager(rootPane, pdfService, themeManager, uiStateManager);

                // Cập nhật ImageInsertionManager với ThemeManager
                imageInsertionManager = new ImageInsertionManager(rootPane, uiStateManager, renderingManager, pageRenderer, themeManager);

                // Cập nhật ContextMenuHandler với ThemeManager
                if (pageRenderer != null && pageRenderer.getContextMenuHandler() != null) {
                    pageRenderer.getContextMenuHandler().setThemeManager(themeManager);
                }
                
                // Set theme manager supplier for PageDeletionManager
                if (pageDeletionManager != null) {
                    pageDeletionManager.setThemeManagerSupplier(() -> themeManager);
                }
                
                // Update theme menu text with bullet points
                updateThemeMenuText();

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
                    } else {
                        // Drawing tool selected - disable text selection
                        if (pageRenderer != null && pagesContainer != null) {
                            pageRenderer.setSelectionModeActive(pagesContainer, false);
                        }
                    }
                    return;
                }
                
                // Document is open - use annotation manager
                annotationManager.handleToolSelection(
                    selectedBtn,
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
        if (btnDrawRect != null) makeToggleButtonDeselectable(btnDrawRect);
        if (btnDrawCircle != null) makeToggleButtonDeselectable(btnDrawCircle);
        if (btnDrawArrow != null) makeToggleButtonDeselectable(btnDrawArrow);

        // Setup drawing tool icons
        drawingToolIconManager.setupDrawingToolIcons(btnDrawRect, btnDrawCircle, btnDrawArrow);
        drawingToolIconManager.setupUndoIcon(undoButton);
        drawingToolIconManager.setupRedoIcon(redoButton);

        if (colorPicker != null) {
            colorPicker.setValue(javafx.scene.paint.Color.BLACK);
            colorPicker.setOnAction(e -> updateDrawingStyleForAllPages());
        }
        
        if (highlightColorPicker != null) {
            highlightColorPicker.setValue(javafx.scene.paint.Color.YELLOW);
            highlightColorPicker.setOnAction(e -> updateHighlightColorForAllPages());
        }

        if (strokeWidthSlider != null) {
            strokeWidthSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                updateDrawingStyleForAllPages();
                if (strokeWidthLabel != null) {
                    strokeWidthLabel.setText(String.format("%.0f", newVal.doubleValue()));
                }
            });
            // Initialize label with current value
            if (strokeWidthLabel != null) {
                strokeWidthLabel.setText(String.format("%.0f", strokeWidthSlider.getValue()));
            }
        }

        uiStateManager.updateUIState(false);
        
        // Setup keyboard shortcuts for undo/redo
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, this::handleKeyboardShortcuts);
            }
            if (newScene != null) {
                newScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, this::handleKeyboardShortcuts);
            }
        });
    }
    
    /**
     * Handles keyboard shortcuts for undo/redo operations.
     */
    private void handleKeyboardShortcuts(javafx.scene.input.KeyEvent event) {
        if (event.isControlDown()) {
            if (event.getCode() == javafx.scene.input.KeyCode.Z) {
                handleUndo();
                event.consume();
            } else if (event.getCode() == javafx.scene.input.KeyCode.Y) {
                handleRedo();
                event.consume();
            }
        }
    }

    /**
     * Initializes all manager classes.
     */
    private void initializeManagers() {
        // UI State Manager (needed by other managers)
        uiStateManager = new UIStateManager(statusLabel, prevButton, nextButton, pageNumberField, zoomComboBox, () -> themeManager);

        // Create ZoomManager first (without listener)
        zoomManager = new ZoomManager(pdfService, null);
        zoomManager.initialize(zoomComboBox, scrollPane);

        // Rendering Manager - needs zoomManager
        renderingManager = new RenderingManager(pdfService, pageRenderer, scrollHandler, zoomManager);
        
        // Now create zoom change listener with renderingManager
        zoomChangeListener = ListenerFactory.createZoomChangeListener(renderingManager, searchManager, uiStateManager);
        
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
        
        // Metadata Manager
        metadataManager = new MetadataManager();

        // Set text edit callback for context menu
        setupTextEditCallback();

        // Highlight handling lives in HighlightManager (keeps MainController smaller)
        HighlightManager highlightManager = new HighlightManager(
                highlightColorPicker,
                uiStateManager,
                () -> currentDocument,
                this::getCurrentZoom,
                () -> annotationManager
        );
        highlightManager.setupHighlightCallback(pageRenderer);
        highlightManager.setupDeleteHighlightCallback(pageRenderer);
    }

    /**
     * Sets up the text edit callback for the context menu handler.
     * This callback is invoked when the user edits text and clicks OK in the text edit dialog.
     */
    private void setupTextEditCallback() {
        pageRenderer.getContextMenuHandler().setTextEditCallback(
            (pageIndex, coverX, coverY, coverWidth, coverHeight, textX, textY, newText, fontSize, font) -> {
            try {
                // Get current document
                if (currentDocument == null) {
                    uiStateManager.updateStatus("No document loaded");
                    logger.warn("Cannot replace text: no document loaded");
                    return;
                }

                // Get the page
                org.apache.pdfbox.pdmodel.PDPage page = currentDocument.getDocument().getPage(pageIndex);

                // Replace text: cover old text with white rectangle, then add new text
                logger.info("Replacing text on page {}: covering ({}, {}) {}x{}, adding '{}' at ({}, {}) with font {} size {}",
                        pageIndex + 1, coverX, coverY, coverWidth, coverHeight, 
                        newText, textX, textY, font.getName(), fontSize);

                contentStreamManager.replaceText(
                        currentDocument.getDocument(),
                        page,
                        coverX, coverY, coverWidth, coverHeight,
                        newText,
                        textX, textY,
                        font,
                        fontSize
                );

                // Mark document as modified
                currentDocument.setHasUnsavedEdits(true);
                logger.info("Document marked as modified");

                // Refresh the page rendering to show the new text
                refreshCurrentPage();

                // Update status
                uiStateManager.updateStatus("Text replaced successfully - Save to persist changes");

            } catch (IOException e) {
                logger.error("Error adding text to PDF", e);
                uiStateManager.updateStatus("Error adding text: " + e.getMessage());
                
                // Show error dialog
                Platform.runLater(() -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Text Edit Error");
                    alert.setHeaderText("Failed to add text to PDF");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
            } catch (IndexOutOfBoundsException e) {
                logger.error("Invalid page index: {}", pageIndex, e);
                uiStateManager.updateStatus("Error: Invalid page index");
                
                Platform.runLater(() -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Text Edit Error");
                    alert.setHeaderText("Invalid page index");
                    alert.setContentText("Page " + (pageIndex + 1) + " does not exist in the document.");
                    alert.showAndWait();
                });
            }
        });
        
        logger.info("Text edit callback configured successfully");
    }
    
    /**
     * Refreshes the current page rendering to show changes.
     */
    private void refreshCurrentPage() {
        if (currentDocument == null || pagesContainer == null) {
            return;
        }

        logger.info("Refreshing current page rendering");

        // Clear caches to force re-render
        currentDocument.clearCache();
        pageRenderer.clearCache();
        pageRenderer.cancelAllPendingRenders();

        // Re-render all visible pages
        Platform.runLater(() -> renderingManager.renderAllPages());
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
            // Load existing highlights from PDF
            if (highlightPersistenceManager != null) {
                try {
                    List<org.pdflite.model.HighlightAnnotation> loadedHighlights = 
                        highlightPersistenceManager.loadHighlightsFromPDF(
                            currentDocument.getDocument());
                    
                    // Add loaded highlights to document
                    for (org.pdflite.model.HighlightAnnotation highlight : loadedHighlights) {
                        currentDocument.addAnnotation(highlight);
                    }
                    
                    logger.info("Loaded {} highlights from PDF", loadedHighlights.size());
                } catch (Exception e) {
                    logger.error("Error loading highlights from PDF", e);
                }
            }
            
            annotationManager = new AnnotationManager(pagesContainer, uiStateManager, currentDocument);
            
            // Set refresh callback for PageRenderer to use in commands
            if (pageRenderer != null) {
                pageRenderer.setRefreshAnnotationsCallback(pageIndex -> {
                    if (annotationManager != null) {
                        annotationManager.refreshPageAnnotations(pageIndex);
                    }
                });
            }
            
            // Update zoom manager with current document
            if (zoomManager != null) {
                zoomManager.setDocument(currentDocument);
            }
            
            // Update rendering manager with current document
            if (renderingManager != null) {
                renderingManager.setDocument(currentDocument);
            }
            
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
    private void handleExport() {
        exportManager.openExportDialog(currentDocument);
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
        // Quick cleanup and exit
        try {
            // Close document first (important to save state)
            if (currentDocument != null) {
                fileManager.close(currentDocument);
            }
            
            // Try to shutdown executor gracefully with short timeout
            if (!renderExecutor.isShutdown()) {
                renderExecutor.shutdown();
                // Don't wait, just force exit
            }
        } catch (Exception e) {
            // Ignore errors during cleanup
        }
        
        // Force exit immediately
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
            pageDeletionManager.handleDeletePage(currentDocument);
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
    private void setSystemTheme() {
        themeManager.setSystemTheme();
        updateThemeMenuGraphics();
    }

    @FXML
    private void setLightTheme() {
        themeManager.setLightTheme();
        updateThemeMenuGraphics();
    }

    @FXML
    private void setDarkTheme() {
        themeManager.setDarkTheme();
        updateThemeMenuGraphics();
    }
    
    private void updateThemeMenuText() {
        updateThemeMenuGraphics();
    }
    
    private void updateThemeMenuGraphics() {
        // Create bullet graphic for selected item
        Circle bullet = new Circle(3);
        bullet.setFill(Color.web("#0A84FF"));
        
        // Set graphics - bullet for selected, null for others
        systemThemeItem.setGraphic(systemThemeItem.isSelected() ? new Circle(3, Color.web("#0A84FF")) : null);
        lightThemeItem.setGraphic(lightThemeItem.isSelected() ? new Circle(3, Color.web("#0A84FF")) : null);
        darkThemeItem.setGraphic(darkThemeItem.isSelected() ? new Circle(3, Color.web("#0A84FF")) : null);
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

    // ==================== Image Insertion Operations ====================

    @FXML
    private void handleInsertImage() {
        imageInsertionManager.openInsertImageDialog(currentDocument);
    }

    @FXML
    private void handleInsertStamp() {
        imageInsertionManager.openInsertStampDialog(currentDocument);
    }

    @FXML
    private void handleAddWatermark() {
        imageInsertionManager.openWatermarkDialog(currentDocument);
    }

    @FXML
    private void handleEditText() {
        imageInsertionManager.showTextEditingInfo(currentDocument);
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

    // ==================== Document Properties ====================

    @FXML
    private void handleDocumentProperties() {
        if (currentDocument == null) {
            uiStateManager.showError("No Document", "Please open a PDF file first.");
            return;
        }

        try {
            // Get current metadata
            var currentMetadata = metadataManager.getMetadata(currentDocument);

            // Show metadata dialog
            org.pdflite.dialog.MetadataDialog dialog = new org.pdflite.dialog.MetadataDialog(
                    currentMetadata, themeManager);

            if (dialog.showAndWait()) {
                // User clicked OK, update metadata
                var updatedMetadata = dialog.getMetadata();
                if (metadataManager.updateMetadata(currentDocument, updatedMetadata)) {
                    uiStateManager.updateStatus("Document properties updated");
                    logger.info("Document metadata updated successfully");
                } else {
                    uiStateManager.showError("Update Failed", "Failed to update document properties.");
                }
            }
        } catch (Exception e) {
            logger.error("Error opening document properties dialog", e);
            uiStateManager.showError("Error", "Failed to open document properties: " + e.getMessage());
        }
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

    @FXML
    private void handleReorderPages() {
        dialogManager.openPageReorderDialog(currentDocument, () -> {
            // Callback: Refresh view after successful reorder
            Platform.runLater(() -> {
                // Clear all caches to force re-render with new page order
                currentDocument.clearCache();
                pageRenderer.clearCache();
                
                // Re-render all pages
                renderingManager.renderAllPages();
                
                // Update status
                uiStateManager.updateStatus("Pages reordered - Don't forget to save!");
                
                logger.info("View refreshed after page reorder");
            });
        });
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
    
    private void updateHighlightColorForAllPages() {
        if (annotationManager != null && highlightColorPicker != null) {
            annotationManager.updateHighlightColorForAllPages(highlightColorPicker.getValue());
        }
    }
    
    public javafx.scene.paint.Color getHighlightColor() {
        return highlightColorPicker != null ? highlightColorPicker.getValue() : javafx.scene.paint.Color.YELLOW;
    }

    private void makeToggleButtonDeselectable(ToggleButton btn) {
        if (annotationManager != null && drawingToolsGroup != null) {
            annotationManager.makeToggleButtonDeselectable(btn, drawingToolsGroup);
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
        if (currentDocument == null) {
            uiStateManager.showError("No PDF", "Please open a PDF file first.");
            return;
        }

        InsertDialogController controller = dialogManager.openInsertDialog(currentDocument);
        if (controller == null || controller.isInsertClicked()) {
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

