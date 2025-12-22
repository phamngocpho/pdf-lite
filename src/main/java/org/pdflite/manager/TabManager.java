package org.pdflite.manager;

import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.pdflite.controller.PageRenderer;
import org.pdflite.controller.ScrollHandler;
import org.pdflite.model.DocumentContext;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Manages multi-tab document operations including tab creation, switching, and closing.
 */
public class TabManager {
    
    private static final Logger logger = LoggerFactory.getLogger(TabManager.class);
    
    private final TabPane documentTabPane;
    private final Tab welcomeTab;
    private final Map<Tab, DocumentContext> tabContextMap;
    
    // Managers
    private final PDFService pdfService;
    private final FileManager fileManager;
    private final PageRenderer pageRenderer;
    private final ZoomManager zoomManager;
    private final PageInfoManager pageInfoManager;
    private final UIStateManager uiStateManager;
    private final RecentFilesManager recentFilesManager;
    private final RecentFilesMenuManager recentFilesMenuManager;
    private final DocumentSetupManager documentSetupManager;
    private final DrawingToolsSetupManager drawingToolsSetupManager;
    private final HighlightPersistenceManager highlightPersistenceManager;
    private final BookmarkManager bookmarkManager;
    private final AutoSaveManager autoSaveManager;
    private final ExecutorService renderExecutor;
    
    // UI Components for drawing tools
    private javafx.scene.control.ToggleGroup drawingToolsGroup;
    private javafx.scene.control.ToggleButton btnDrawRect;
    private javafx.scene.control.ToggleButton btnDrawCircle;
    private javafx.scene.control.ToggleButton btnDrawArrow;
    private javafx.scene.control.ColorPicker colorPicker;
    private javafx.scene.control.ColorPicker highlightColorPicker;
    
    // Callbacks
    private Consumer<Tab> onTabSwitched;
    private Runnable onAllTabsClosed;
    
    public TabManager(TabPane documentTabPane,
                      PDFService pdfService,
                      FileManager fileManager,
                      PageRenderer pageRenderer,
                      ZoomManager zoomManager,
                      PageInfoManager pageInfoManager,
                      UIStateManager uiStateManager,
                      RecentFilesManager recentFilesManager,
                      RecentFilesMenuManager recentFilesMenuManager,
                      DocumentSetupManager documentSetupManager,
                      DrawingToolsSetupManager drawingToolsSetupManager,
                      HighlightPersistenceManager highlightPersistenceManager,
                      BookmarkManager bookmarkManager,
                      AutoSaveManager autoSaveManager,
                      ExecutorService renderExecutor) {
        this.documentTabPane = documentTabPane;
        this.pdfService = pdfService;
        this.fileManager = fileManager;
        this.pageRenderer = pageRenderer;
        this.zoomManager = zoomManager;
        this.pageInfoManager = pageInfoManager;
        this.uiStateManager = uiStateManager;
        this.recentFilesManager = recentFilesManager;
        this.recentFilesMenuManager = recentFilesMenuManager;
        this.documentSetupManager = documentSetupManager;
        this.drawingToolsSetupManager = drawingToolsSetupManager;
        this.highlightPersistenceManager = highlightPersistenceManager;
        this.bookmarkManager = bookmarkManager;
        this.autoSaveManager = autoSaveManager;
        this.renderExecutor = renderExecutor;
        
        this.tabContextMap = new HashMap<>();
        
        // Store reference to welcome tab
        if (documentTabPane != null && !documentTabPane.getTabs().isEmpty()) {
            this.welcomeTab = documentTabPane.getTabs().get(0);
            setupTabSelectionListener();
        } else {
            this.welcomeTab = null;
        }
    }
    
    /**
     * Sets up the tab selection listener to switch context when tabs change.
     */
    private void setupTabSelectionListener() {
        documentTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab != welcomeTab) {
                switchToTabContext(newTab);
                if (onTabSwitched != null) {
                    onTabSwitched.accept(newTab);
                }
            }
        });
    }
    
    /**
     * Sets drawing tool UI components for annotation management.
     */
    public void setDrawingToolComponents(javafx.scene.control.ToggleGroup drawingToolsGroup,
                                         javafx.scene.control.ToggleButton btnDrawRect,
                                         javafx.scene.control.ToggleButton btnDrawCircle,
                                         javafx.scene.control.ToggleButton btnDrawArrow,
                                         javafx.scene.control.ColorPicker colorPicker,
                                         javafx.scene.control.ColorPicker highlightColorPicker) {
        this.drawingToolsGroup = drawingToolsGroup;
        this.btnDrawRect = btnDrawRect;
        this.btnDrawCircle = btnDrawCircle;
        this.btnDrawArrow = btnDrawArrow;
        this.colorPicker = colorPicker;
        this.highlightColorPicker = highlightColorPicker;
    }
    
    /**
     * Sets callback to be invoked when a tab is switched.
     */
    public void setOnTabSwitched(Consumer<Tab> callback) {
        this.onTabSwitched = callback;
    }
    
    /**
     * Sets callback to be invoked when all tabs are closed.
     */
    public void setOnAllTabsClosed(Runnable callback) {
        this.onAllTabsClosed = callback;
    }
    
    /**
     * Opens a PDF file in a new tab.
     */
    public void openPDFFile(File file) {
        try {
            // Open the document
            PDFDocument newDocument = fileManager.openFile(file);
            if (newDocument == null) {
                return;
            }

            // Reset to page 1 (index 0) when opening a new file
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
            DocumentContext context = new DocumentContext(
                newDocument, scrollPane, pagesContainer, contentPane);
            
            // Create tab FIRST before setting up scroll listener
            Tab tab = createDocumentTab(newDocument, context);
            
            // Create per-tab scroll handler
            ScrollHandler tabScrollHandler = new ScrollHandler(pageRenderer, scrollPane);
            tabScrollHandler.setDocument(newDocument, pagesContainer);
            tabScrollHandler.setPageChangeListener(
                ListenerFactory.createPageChangeListener(newDocument, pageInfoManager));
            
            // Setup scroll listener for this tab
            scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
                // Only handle scroll if this is the active tab
                if (documentTabPane.getSelectionModel().getSelectedItem() == tab) {
                    tabScrollHandler.handleScroll();
                }
            });
            
            // Create per-tab rendering manager
            RenderingManager tabRenderingManager = new RenderingManager(
                pdfService, pageRenderer, tabScrollHandler, zoomManager);
            tabRenderingManager.setDocument(newDocument);
            tabRenderingManager.setUIComponents(pagesContainer, scrollPane, contentPane);
            context.setRenderingManager(tabRenderingManager);
            
            // Update page renderer with new document
            pageRenderer.setDocument(newDocument, initialZoom);
            
            // Render all pages
            tabRenderingManager.renderAllPages();
            
            // Setup document using DocumentSetupManager
            if (documentSetupManager != null) {
                AnnotationManager tabAnnotationManager = documentSetupManager.setupDocument(
                    newDocument, pagesContainer, scrollPane, 
                    null, // zoomChangeListener will be set by controller
                    uiStateManager);
                context.setAnnotationManager(tabAnnotationManager);
            }
            
            // Setup drawing tools for this tab
            if (drawingToolsSetupManager != null && context.getAnnotationManager() != null) {
                drawingToolsSetupManager.setupDrawingToolSelection(
                    drawingToolsGroup, btnDrawRect, btnDrawCircle, btnDrawArrow,
                    pagesContainer, context.getAnnotationManager());
                
                drawingToolsSetupManager.makeToggleButtonsDeselectable(
                    btnDrawRect, btnDrawCircle, btnDrawArrow, drawingToolsGroup, 
                    context.getAnnotationManager());
            }
            
            // Now switch to the new tab context
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
            uiStateManager.showError("Error Opening PDF", 
                "Could not open the PDF file: " + e.getMessage());
        }
    }
    
    /**
     * Creates a new tab for a document.
     */
    private Tab createDocumentTab(PDFDocument document, DocumentContext context) {
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
     * @return true if tab was closed, false if canceled
     */
    private boolean handleCloseTab(Tab tab) {
        DocumentContext context = tabContextMap.get(tab);
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
            
            // Notify callback
            if (onAllTabsClosed != null) {
                onAllTabsClosed.run();
            }
        }
        
        logger.info("Closed tab: {}", document.getFile().getName());
        return true;
    }
    
    /**
     * Switches the active context when a tab is selected.
     */
    private void switchToTabContext(Tab tab) {
        DocumentContext context = tabContextMap.get(tab);
        if (context == null) return;
        
        PDFDocument document = context.getDocument();
        VBox pagesContainer = context.getPagesContainer();
        ScrollPane scrollPane = context.getScrollPane();
        
        // Update managers with the new context
        if (zoomManager != null) {
            zoomManager.setDocument(document);
            zoomManager.setCurrentZoom(document.getZoomLevel());
            zoomManager.initialize(null, scrollPane); // ComboBox already initialized
        }
        
        // Update page renderer
        if (pageRenderer != null) {
            pageRenderer.setDocument(document, document.getZoomLevel());
        }
        
        // Update AutoSaveManager to track the correct document
        if (autoSaveManager != null) {
            autoSaveManager.setDocument(document);
            logger.debug("AutoSaveManager updated to track document: {}", 
                document.getFile().getName());
        }
        
        // Update UI
        if (pageInfoManager != null) {
            pageInfoManager.updatePageInfo(document);
        }
        
        if (uiStateManager != null) {
            uiStateManager.updateUIState(true);
            uiStateManager.updateStatus("Switched to: " + document.getFile().getName());
        }
        
        // Sync drawing colors from UI to the new tab's annotation layers
        AnnotationManager annotationManager = context.getAnnotationManager();
        if (annotationManager != null) {
            if (colorPicker != null && drawingToolsSetupManager != null) {
                // Get stroke width from slider if available
                double strokeWidth = 2.0; // default
                annotationManager.updateDrawingStyleForAllPages(
                    colorPicker.getValue(), strokeWidth);
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
     * Gets the current active document context.
     */
    public DocumentContext getCurrentContext() {
        if (documentTabPane == null) return null;
        Tab selectedTab = documentTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null || selectedTab == welcomeTab) return null;
        return tabContextMap.get(selectedTab);
    }
    
    /**
     * Gets the current document from the active tab.
     */
    public PDFDocument getCurrentDocument() {
        DocumentContext context = getCurrentContext();
        return context != null ? context.getDocument() : null;
    }
    
    /**
     * Gets the current pages container from the active tab.
     */
    public VBox getCurrentPagesContainer() {
        DocumentContext context = getCurrentContext();
        return context != null ? context.getPagesContainer() : null;
    }
    
    /**
     * Gets the current scroll pane from the active tab.
     */
    public ScrollPane getCurrentScrollPane() {
        DocumentContext context = getCurrentContext();
        return context != null ? context.getScrollPane() : null;
    }
    
    /**
     * Gets the current annotation manager from the active tab.
     */
    public AnnotationManager getCurrentAnnotationManager() {
        DocumentContext context = getCurrentContext();
        return context != null ? context.getAnnotationManager() : null;
    }
    
    /**
     * Gets the current rendering manager from the active tab.
     */
    public RenderingManager getCurrentRenderingManager() {
        DocumentContext context = getCurrentContext();
        return context != null ? context.getRenderingManager() : null;
    }
}
