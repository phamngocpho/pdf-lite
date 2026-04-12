package org.pdflite.manager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import org.pdflite.controller.PageRenderer;
import org.pdflite.controller.ScrollHandler;
import org.pdflite.model.DocumentContext;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.pdflite.util.ScrollCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages multi-tab document handling including opening, closing, and switching tabs.
 */
public class TabManager {

    private static final Logger logger = LoggerFactory.getLogger(TabManager.class);

    private final Map<Tab, DocumentContext> tabContextMap = new HashMap<>();
    private final TabPane documentTabPane;
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
    private final BookmarkManager bookmarkManager;
    private final AutoSaveManager autoSaveManager;
    private final PageLabelManager pageLabelManager;
    private final DocumentSidebarManager documentSidebarManager;

    private Tab welcomeTab;
    private RenderingManager renderingManager;
    private AnnotationManager annotationManager;
    private ListenerFactory.ZoomChangeListenerWithContext zoomChangeListener;
    private ScrollHandler scrollHandler;

    // Callbacks for UI updates
    private Consumer<PDFDocument> onTabSwitched;
    private Supplier<javafx.scene.control.ToggleGroup> drawingToolsGroupSupplier;
    private Supplier<javafx.scene.control.ToggleButton> btnDrawRectSupplier;
    private Supplier<javafx.scene.control.ToggleButton> btnDrawCircleSupplier;
    private Supplier<javafx.scene.control.ToggleButton> btnDrawArrowSupplier;
    private Supplier<javafx.scene.control.ColorPicker> colorPickerSupplier;
    private Supplier<javafx.scene.control.ColorPicker> highlightColorPickerSupplier;
    private Supplier<javafx.scene.control.Slider> strokeWidthSliderSupplier;

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    public TabManager(TabPane documentTabPane, PDFService pdfService, FileManager fileManager,
                      PageRenderer pageRenderer, ZoomManager zoomManager, PageInfoManager pageInfoManager,
                      UIStateManager uiStateManager, RecentFilesManager recentFilesManager,
                      RecentFilesMenuManager recentFilesMenuManager, DocumentSetupManager documentSetupManager,
                      DrawingToolsSetupManager drawingToolsSetupManager, BookmarkManager bookmarkManager,
                      AutoSaveManager autoSaveManager, PageLabelManager pageLabelManager) {
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
        this.bookmarkManager = bookmarkManager;
        this.autoSaveManager = autoSaveManager;
        this.pageLabelManager = pageLabelManager;
        this.documentSidebarManager = new DocumentSidebarManager(pdfService, pageLabelManager);
    }

    public void initialize() {
        if (documentTabPane != null && !documentTabPane.getTabs().isEmpty()) {
            welcomeTab = documentTabPane.getTabs().getFirst();

            documentTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (newTab != null && newTab != welcomeTab) {
                    switchToTabContext(newTab);
                }
            });
        }
    }

    public void setRenderingManager(RenderingManager renderingManager) {
        this.renderingManager = renderingManager;
    }

    public void setAnnotationManager(AnnotationManager annotationManager) {
        this.annotationManager = annotationManager;
    }

    public void setZoomChangeListener(ListenerFactory.ZoomChangeListenerWithContext listener) {
        this.zoomChangeListener = listener;
    }

    public void setScrollHandler(ScrollHandler scrollHandler) {
        this.scrollHandler = scrollHandler;
    }

    public void setOnTabSwitched(Consumer<PDFDocument> callback) {
        this.onTabSwitched = callback;
    }

    public void setDrawingToolsSuppliers(
            Supplier<javafx.scene.control.ToggleGroup> drawingToolsGroupSupplier,
            Supplier<javafx.scene.control.ToggleButton> btnDrawRectSupplier,
            Supplier<javafx.scene.control.ToggleButton> btnDrawCircleSupplier,
            Supplier<javafx.scene.control.ToggleButton> btnDrawArrowSupplier) {
        this.drawingToolsGroupSupplier = drawingToolsGroupSupplier;
        this.btnDrawRectSupplier = btnDrawRectSupplier;
        this.btnDrawCircleSupplier = btnDrawCircleSupplier;
        this.btnDrawArrowSupplier = btnDrawArrowSupplier;
    }

    public void setColorPickerSuppliers(
            Supplier<javafx.scene.control.ColorPicker> colorPickerSupplier,
            Supplier<javafx.scene.control.ColorPicker> highlightColorPickerSupplier,
            Supplier<javafx.scene.control.Slider> strokeWidthSliderSupplier) {
        this.colorPickerSupplier = colorPickerSupplier;
        this.highlightColorPickerSupplier = highlightColorPickerSupplier;
        this.strokeWidthSliderSupplier = strokeWidthSliderSupplier;
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
    public PDFDocument getActiveDocument() {
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
     * Gets the current scroll handler from the active tab.
     */
    public ScrollHandler getCurrentScrollHandler() {
        DocumentContext context = getCurrentContext();
        return context != null ? context.getScrollHandler() : null;
    }

    public Map<Tab, DocumentContext> getTabContextMap() {
        return tabContextMap;
    }

    /**
     * Finds an existing tab that has the specified file open.
     *
     * @param file the file to search for
     * @return the tab containing the file, or null if not found
     */
    private Tab findTabByFile(File file) {
        if (file == null) return null;
        
        String filePath = file.getAbsolutePath();
        for (Map.Entry<Tab, DocumentContext> entry : tabContextMap.entrySet()) {
            DocumentContext context = entry.getValue();
            if (context != null && context.getDocument() != null) {
                File docFile = context.getDocument().getFile();
                if (docFile != null && docFile.getAbsolutePath().equals(filePath)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * Opens a PDF file in a new tab.
     */
    public void openPDFFile(File file, Set<Integer> loadingPages,
                            java.util.concurrent.ExecutorService renderExecutor) {
        // Ensure we're on the FX Application Thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> openPDFFile(file, loadingPages, renderExecutor));
            return;
        }
        
        // Check if file is already open in a tab
        Tab existingTab = findTabByFile(file);
        if (existingTab != null) {
            documentTabPane.getSelectionModel().select(existingTab);
            uiStateManager.updateStatus(lang().getString("status.switchedTo", file.getName()));
            logger.info("File already open, switched to existing tab: {}", file.getName());
            return;
        }
        
        try {
            PDFDocument newDocument = fileManager.openFile(file);
            if (newDocument == null) {
                return;
            }

            // Reset to page 1 when opening a new file
            newDocument.setCurrentPage(0);
            if (pageLabelManager != null) {
                pageLabelManager.initializeDocument(newDocument);
            }

            // Set initial zoom to 100%
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
            pagesContainer.setAlignment(Pos.TOP_CENTER);
            pagesContainer.setStyle("-fx-background-color: #808080; -fx-padding: 10 20 20 20;");
            contentPane.getChildren().add(pagesContainer);

            // Create document context
            DocumentContext context = new DocumentContext(newDocument, scrollPane, pagesContainer, contentPane);

            // Create tab before setting up scroll listener
            Tab tab = createDocumentTab(newDocument, context);

            // Create per-tab scroll handler
            ScrollHandler tabScrollHandler = new ScrollHandler(pageRenderer, scrollPane);
            tabScrollHandler.setDocumentLoading(true); // Block scroll during load
            tabScrollHandler.setDocument(newDocument, pagesContainer);
            tabScrollHandler.setPageChangeListener(newPageIndex -> Platform.runLater(() -> {
                if (pageInfoManager != null) {
                    pageInfoManager.updatePageInfo(newDocument);
                }
                documentSidebarManager.syncCurrentPage(newDocument, newPageIndex);
            }));
            
            // Store scroll handler in context
            context.setScrollHandler(tabScrollHandler);

            // Setup scroll listener for this tab
            scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
                if (documentTabPane.getSelectionModel().getSelectedItem() == tab) {
                    tabScrollHandler.handleScroll();
                }
            });
            attachZoomWheelHandler(scrollPane, tab);

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
                        newDocument, pagesContainer, scrollPane, zoomChangeListener, uiStateManager);
                context.setAnnotationManager(tabAnnotationManager);
            }

            // Setup drawing tools for this tab
            if (drawingToolsSetupManager != null && context.getAnnotationManager() != null
                    && drawingToolsGroupSupplier != null) {
                drawingToolsSetupManager.setupDrawingToolSelection(
                        drawingToolsGroupSupplier.get(),
                        btnDrawRectSupplier.get(),
                        btnDrawCircleSupplier.get(),
                        btnDrawArrowSupplier.get(),
                        pagesContainer, context.getAnnotationManager());

                drawingToolsSetupManager.makeToggleButtonsDeselectable(
                        btnDrawRectSupplier.get(),
                        btnDrawCircleSupplier.get(),
                        btnDrawArrowSupplier.get(),
                        drawingToolsGroupSupplier.get(),
                        context.getAnnotationManager());
            }

            // Switch to the new tab context
            switchToTabContext(tab);

            // Scroll to top, enable text selection, and unlock scroll after initial render
            Platform.runLater(() -> {
                scrollPane.setVvalue(0.0);
                if (pagesContainer != null) {
                    pageRenderer.setSelectionModeActive(pagesContainer, true);
                }
                documentSidebarManager.syncCurrentPage(newDocument, 0);
                // Enable scroll after a short delay to ensure initial pages are loaded
                Platform.runLater(() -> tabScrollHandler.setDocumentLoading(false));
            });

            // Add to recent files
            recentFilesManager.addRecentFile(file.getAbsolutePath());
            recentFilesMenuManager.updateRecentFilesMenu();

            // Update status
            uiStateManager.updateStatus(lang().getString("status.opened", file.getName()));

            // Load bookmarks for the new document
            if (bookmarkManager != null) {
                bookmarkManager.setCurrentDocument(newDocument);
                logger.info("Bookmarks loaded for document: {}", file.getName());
            }

            logger.info("Successfully opened PDF in new tab: {} ({} pages)",
                    file.getName(), newDocument.getTotalPages());

        } catch (IOException e) {
            logger.error("Error opening PDF file", e);
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.openPdf") + ": " + e.getMessage());
        }
    }

    /**
     * Switches the active context when a tab is selected.
     */
    public void switchToTabContext(Tab tab) {
        DocumentContext context = tabContextMap.get(tab);
        if (context == null) return;

        PDFDocument document = context.getDocument();
        VBox pagesContainer = context.getPagesContainer();
        ScrollPane scrollPane = context.getScrollPane();

        // Update managers with the new context
        if (zoomManager != null) {
            zoomManager.setDocument(document);
            zoomManager.setScrollPane(scrollPane);
            zoomManager.setCurrentZoom(document.getZoomLevel());
        }

        // Use the RenderingManager from the context
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
            scrollHandler.setPageChangeListener(newPageIndex -> Platform.runLater(() -> {
                if (pageInfoManager != null) {
                    pageInfoManager.updatePageInfo(document);
                }
                documentSidebarManager.syncCurrentPage(document, newPageIndex);
            }));
        }

        if (pageRenderer != null) {
            pageRenderer.setDocument(document, document.getZoomLevel());
        }

        // Update AutoSaveManager to track the correct document
        if (autoSaveManager != null) {
            autoSaveManager.setDocument(document);
            logger.debug("AutoSaveManager updated to track document: {}", document.getFile().getName());
        }

        // Update UI
        if (pageInfoManager != null) {
            pageInfoManager.updatePageInfo(document);
        }
        documentSidebarManager.syncCurrentPage(document, document.getCurrentPage());

        if (uiStateManager != null) {
            uiStateManager.updateUIState(true);
            uiStateManager.updateStatus(lang().getString("status.switchedTo", document.getFile().getName()));
        }

        // Update annotation manager reference
        annotationManager = context.getAnnotationManager();

        // Sync drawing colors from UI to the new tab's annotation layers
        if (annotationManager != null && colorPickerSupplier != null && strokeWidthSliderSupplier != null) {
            javafx.scene.control.ColorPicker colorPicker = colorPickerSupplier.get();
            javafx.scene.control.Slider strokeWidthSlider = strokeWidthSliderSupplier.get();
            if (colorPicker != null && strokeWidthSlider != null) {
                annotationManager.updateDrawingStyleForAllPages(
                        colorPicker.getValue(), strokeWidthSlider.getValue());
            }

            javafx.scene.control.ColorPicker highlightColorPicker = highlightColorPickerSupplier.get();
            if (highlightColorPicker != null) {
                annotationManager.updateHighlightColorForAllPages(highlightColorPicker.getValue());
            }
        }

        // Update bookmark manager
        if (bookmarkManager != null) {
            bookmarkManager.setCurrentDocument(document);
        }

        // Notify callback
        if (onTabSwitched != null) {
            onTabSwitched.accept(document);
        }

        logger.info("Switched to tab: {} (page {}/{})",
                document.getFile().getName(),
                document.getCurrentPage() + 1,
                document.getTotalPages());
    }

    // SVG path for close icon (same as title bar)
    private static final String CLOSE_ICON_PATH = "m256-236-20-20 224-224-224-224 20-20 224 224 224-224 20 20-224 224 224 224-20 20-224-224-224 224Z";

    /**
     * Creates a new tab for a document.
     */
    private Tab createDocumentTab(PDFDocument document, DocumentContext context) {
        Tab tab = new Tab();
        tab.setClosable(false); // We use custom close button

        // Create custom tab graphic with label and close button
        HBox tabGraphic = createTabGraphic(document.getFile().getName(), tab);
        tab.setGraphic(tabGraphic);

        // Create the tab content
        tab.setContent(buildDocumentContent(context));

        // Store context
        tabContextMap.put(tab, context);

        // Add tab and select it
        if (documentTabPane != null) {
            // Hide welcome tab when the first document is opened
            if (welcomeTab != null) {
                documentTabPane.getTabs().remove(welcomeTab);
            }

            documentTabPane.getTabs().add(tab);
            documentTabPane.getSelectionModel().select(tab);
        }

        return tab;
    }

    private BorderPane buildDocumentContent(DocumentContext context) {
        if (context.getTabRoot() != null) {
            return context.getTabRoot();
        }

        BorderPane container = new BorderPane();
        ScrollPane scrollPane = context.getScrollPane();
        PDFDocument document = context.getDocument();

        VBox sidebar = documentSidebarManager.createSidebar(
                document,
                pageIndex -> navigateToPage(context, pageIndex),
                () -> toggleSidebarForContext(context)
        );
        SplitPane splitPane = new SplitPane(sidebar, scrollPane);
        splitPane.setDividerPositions(context.getSidebarDividerPosition());
        splitPane.getDividers().getFirst().positionProperty().addListener((obs, oldVal, newVal) -> {
            if (!context.isSidebarCollapsed()) {
                context.setSidebarDividerPosition(newVal.doubleValue());
            }
        });

        context.setTabRoot(container);
        context.setSplitPane(splitPane);
        context.setSidebarContainer(sidebar);
        context.setSidebarCollapsed(false);

        container.setCenter(splitPane);

        return container;
    }

    private void navigateToPage(DocumentContext context, int pageIndex) {
        PDFDocument document = context.getDocument();
        VBox pagesContainer = context.getPagesContainer();
        ScrollPane scrollPane = context.getScrollPane();
        ScrollHandler tabScrollHandler = context.getScrollHandler();

        if (document == null || pagesContainer == null || scrollPane == null
                || pageIndex < 0 || pageIndex >= document.getTotalPages()) {
            return;
        }

        document.setCurrentPage(pageIndex);
        if (tabScrollHandler != null) {
            tabScrollHandler.lockPageUpdates();
        }

        Platform.runLater(() -> {
            try {
                pagesContainer.layout();
                double currentY = ScrollCalculator.calculatePageYPosition(pagesContainer, pageIndex);
                double contentHeight = pagesContainer.getHeight();
                double viewportHeight = scrollPane.getViewportBounds().getHeight();

                if (contentHeight > viewportHeight) {
                    double targetV = currentY / Math.max(1.0, (contentHeight - viewportHeight));
                    scrollPane.setVvalue(Math.max(0.0, Math.min(1.0, targetV)));
                }

                if (pageInfoManager != null) {
                    pageInfoManager.updatePageInfo(document);
                }
                documentSidebarManager.syncCurrentPage(document, pageIndex);
            } catch (Exception e) {
                logger.debug("Failed to navigate via sidebar: {}", e.getMessage());
            }
        });
    }

    /**
     * Creates custom tab graphic with label and close button.
     */
    private HBox createTabGraphic(String fileName, Tab tab) {
        HBox graphic = new HBox(8);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(0, 0, 0, 0));

        // File name label
        Label label = new Label(fileName);
        label.getStyleClass().add("tab-label");

        // Close button with SVG icon
        Button closeButton = new Button();
        closeButton.getStyleClass().add("tab-close-btn");

        SVGPath closeIcon = new SVGPath();
        closeIcon.setContent(CLOSE_ICON_PATH);
        closeIcon.getStyleClass().add("tab-close-icon-svg");

        StackPane closeIconPane = new StackPane(closeIcon);
        closeIconPane.setMinSize(10, 10);
        closeIconPane.setPrefSize(10, 10);
        closeIconPane.setMaxSize(10, 10);
        closeIconPane.setPickOnBounds(false);

        closeButton.setGraphic(closeIconPane);
        closeButton.setOnAction(e -> {
            if (handleCloseTab(tab)) {
                documentTabPane.getTabs().remove(tab);
            }
        });

        graphic.getChildren().addAll(label, closeButton);
        return graphic;
    }

    /**
     * Handles closing a tab.
     * @return true if tab was closed, false if cancelled
     */
    public boolean handleCloseTab(Tab tab) {
        DocumentContext context = tabContextMap.get(tab);
        if (context == null) return true;

        PDFDocument document = context.getDocument();

        // Close the document
        if (fileManager != null) {
            fileManager.close(document);
        }

        // Remove from map
        tabContextMap.remove(tab);

        // If no more tabs, show welcome tab
        if (documentTabPane != null && documentTabPane.getTabs().size() == 1) {
            documentTabPane.getTabs().addFirst(welcomeTab);
            documentTabPane.getSelectionModel().select(welcomeTab);

            if (uiStateManager != null) {
                uiStateManager.updateUIState(false);
            }
        }

        logger.info("Closed tab: {}", document.getFile().getName());
        return true;
    }

    public RenderingManager getRenderingManager() {
        return renderingManager;
    }

    public AnnotationManager getAnnotationManager() {
        return annotationManager;
    }

    public Tab getWelcomeTab() {
        return welcomeTab;
    }

    public void refreshCurrentTabSidebar() {
        if (documentTabPane == null) {
            return;
        }
        Tab selectedTab = documentTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null || selectedTab == welcomeTab) {
            return;
        }

        DocumentContext context = tabContextMap.get(selectedTab);
        if (context != null) {
            documentSidebarManager.refreshPageLabels(context.getDocument());
        }
    }

    public void toggleCurrentSidebar() {
        DocumentContext context = getCurrentContext();
        toggleSidebarForContext(context);
    }

    private void toggleSidebarForContext(DocumentContext context) {
        if (context == null || context.getSplitPane() == null || context.getSidebarContainer() == null) {
            return;
        }

        SplitPane splitPane = context.getSplitPane();
        VBox sidebar = context.getSidebarContainer();

        if (context.isSidebarCollapsed()) {
            if (!splitPane.getItems().contains(sidebar)) {
                splitPane.getItems().add(0, sidebar);
            }
            context.setSidebarCollapsed(false);
            splitPane.setDividerPositions(context.getSidebarDividerPosition());
        } else {
            if (!splitPane.getDividers().isEmpty()) {
                context.setSidebarDividerPosition(splitPane.getDividerPositions()[0]);
            }
            splitPane.getItems().remove(sidebar);
            context.setSidebarCollapsed(true);
        }
    }

    public void syncSidebarToCurrentPage() {
        DocumentContext context = getCurrentContext();
        if (context == null || context.getDocument() == null) {
            return;
        }
        PDFDocument document = context.getDocument();
        documentSidebarManager.syncCurrentPage(document, document.getCurrentPage());
    }

    private void attachZoomWheelHandler(ScrollPane scrollPane, Tab tab) {
        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!event.isShortcutDown()) {
                return;
            }
            if (documentTabPane.getSelectionModel().getSelectedItem() != tab) {
                return;
            }
            if (zoomManager != null) {
                zoomManager.adjustZoomFromWheel(event.getDeltaY());
                event.consume();
            }
        });
    }
}
