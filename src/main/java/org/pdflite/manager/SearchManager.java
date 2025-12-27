package org.pdflite.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.collections.ListChangeListener;
import javafx.scene.layout.HBox;


import org.pdflite.controller.MainController;
import org.pdflite.model.PDFDocument;
import org.pdflite.model.SearchResult;
import org.pdflite.util.NavigationHelper;
import org.pdflite.util.PageContainerUtils;
import org.pdflite.view.AnnotationLayer;
import org.pdflite.view.SearchPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Manages all search-related functionality including
 * - Search panel lifecycle (float/left/right)
 * - Search results highlighting
 * - Active result navigation
 * - Highlight rendering coordination
 */
public class SearchManager {

    private static final Logger logger = LoggerFactory.getLogger(SearchManager.class);

    // Dependencies
    private final MainController mainController;
    private final NavigationHelper navigationHelper;

    // Search panel state
    private final SearchPanel searchPanel;
    private boolean searchPanelVisible = false;
    private SearchPanelPosition searchPanelPosition = SearchPanelPosition.FLOAT;

    // Search results state
    private final Map<Integer, List<SearchResult>> resultsByPage = new HashMap<>();
    private SearchResult activeResult = null;

    // Page listeners for auto-highlighting
    private final Map<VBox, ListChangeListener<Node>> pageListeners = new HashMap<>();
    private ListChangeListener<Node> containerListener;

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }


    // Panel position enum
    public enum SearchPanelPosition {
        LEFT, RIGHT, FLOAT
    }

    /**
     * Constructor with dependency injection
     */
    public SearchManager(MainController mainController, NavigationHelper navigationHelper) {
        this.mainController = mainController;
        this.navigationHelper = navigationHelper;
        this.searchPanel = new SearchPanel();
        this.searchPanel.setMainController(mainController);
        this.searchPanel.setVisible(false);
        this.searchPanel.setManaged(false);
    }

    // ==================== PUBLIC API ====================

    /**
     * Toggle search panel visibility with a specified position
     */
    public void togglePanel(SearchPanelPosition position) {
        this.searchPanelPosition = position;

        if (searchPanelVisible) {
            hidePanel();
        } else {
            showPanel(position);
        }
    }

    public void showResults(List<SearchResult> results) {
        clearHighlights();

        if (results == null || results.isEmpty()) {
            logger.info("No results to highlight");
            return;
        }

        groupResultsByPage(results);

        groupResultsByPage(results);

        logger.info("Prepared {} pages with search results for lazy highlighting", resultsByPage.size());

        // Setup listeners to catch future renders (lazy loading)
        setupPageChangeListeners();

        // Update currently visible/rendered pages
        Platform.runLater(() -> {
            updateAllHighlights();
            logger.info("Highlighted search results on currently rendered pages");
        });


    }

    public void navigateToResult(SearchResult result) {
        if (result == null) {
            logger.warn("Attempted to navigate to null result");
            return;
        }

        this.activeResult = result;
        int pageIndex = result.pageNumber() - 1;

        logger.info("Navigating to search result: page={}, start={}, end={}, pos=({}, {})",
                result.pageNumber(), result.startIndex(), result.endIndex(),
                result.x(), result.y());

        if (pageIndex >= 0 && pageIndex < mainController.getTotalPages()) {
            navigationHelper.ensurePageLoadedAndReady(pageIndex, () -> Platform.runLater(() -> {
                navigationHelper.navigateToPage(pageIndex);

                // Delay to ensure scroll completes
                Platform.runLater(() -> {
                    updateAllHighlights();
                    logger.info("Active result updated on page {}", result.pageNumber());
                });
            }));
        }
    }

    public void clearSearch() {
        clearHighlights();
        hidePanel();
    }

    public void updateHighlightsAfterZoom(double newZoom) {
        if (resultsByPage.isEmpty()) {
            return;
        }

        Platform.runLater(() -> {
            // With the listener system, we mostly rely on page reload.
            // But we still update scale for any existing layers just in case.
            updateAnnotationLayersScale(newZoom);
            // Re-apply highlights to ensure consistency
            updateAllHighlights();
            logger.debug("Updated highlights after zoom to {}%", newZoom * 100);
        });

    }

    // ==================== PANEL MANAGEMENT ====================

    private void showPanel(SearchPanelPosition position) {
        PDFDocument currentDocument = mainController.getCurrentDocument();
        if (currentDocument == null) {
            mainController.showError(lang().getString("error.noPdfLoaded"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        searchPanel.setPDFDocument(currentDocument);

        if (position == SearchPanelPosition.FLOAT) {
            showFloatDialog();
        } else {
            showSidePanel(position);
        }

        searchPanelVisible = true;
        logger.info("Search panel shown in {} mode", position);
    }

    private void showFloatDialog() {
        mainController.handleSearchDialog();
    }

    private void showSidePanel(SearchPanelPosition position) {
        mainController.getRootPane().setLeft(null);
        mainController.getRootPane().setRight(null);

        searchPanel.setVisible(true);
        searchPanel.setManaged(true);
        searchPanel.setPrefWidth(350);
        searchPanel.setMinWidth(300);
        searchPanel.setMaxWidth(450);

        if (position == SearchPanelPosition.LEFT) {
            mainController.getRootPane().setLeft(searchPanel);
        } else {
            mainController.getRootPane().setRight(searchPanel);
        }

        logger.info("Search side panel shown on {}", position);
    }

    private void hidePanel() {
        searchPanel.setVisible(false);
        searchPanel.setManaged(false);
        mainController.getRootPane().setLeft(null);
        mainController.getRootPane().setRight(null);
        searchPanelVisible = false;

        searchPanelVisible = false;

        clearHighlights();
        removePageListeners();

        logger.info("Search panel hidden");
    }

    // ==================== HIGHLIGHT COORDINATION ====================

    private void groupResultsByPage(List<SearchResult> results) {
        resultsByPage.clear();
        for (SearchResult result : results) {
            int pageIndex = result.pageNumber() - 1;
            resultsByPage.computeIfAbsent(pageIndex, k -> new ArrayList<>())
                    .add(result);
        }
    }


    private void updateAllHighlights() {
        VBox pagesContainer = mainController.getPagesContainer();
        if (pagesContainer == null) {
            logger.warn("Cannot update highlights - pagesContainer is null");
            return;
        }

        int highlightCount = 0;
        int pagesMissing = 0;
        int activeSetCount = 0;

        logger.debug("Updating highlights with active result: {}", activeResult);

        for (Map.Entry<Integer, List<SearchResult>> entry : resultsByPage.entrySet()) {
            int pageIndex = entry.getKey();
            List<SearchResult> pageResults = entry.getValue();

            if (pageIndex < 0 || pageIndex >= mainController.getTotalPages()) {
                logger.warn("Invalid page index: {}", pageIndex);
                continue;
            }
            VBox pageBox = findPageBox(pagesContainer, pageIndex);
            AnnotationLayer layer = findAnnotationLayer(pageBox);

            if (layer != null) {
                layer.setSearchHighlights(pageResults);
                highlightCount += pageResults.size();

                if (activeResult != null && activeResult.pageNumber() - 1 == pageIndex) {
                    layer.setActiveSearchResult(activeResult);
                    activeSetCount++;

                    logger.debug("Set active result on page {} - {}",
                            pageIndex + 1, activeResult);
                } else {
                    layer.setActiveSearchResult(null);
                }

                logger.debug("Applied {} highlights to page {}", pageResults.size(), pageIndex + 1);
            } else {
                pagesMissing++;
                logger.warn("No AnnotationLayer found for page {} (not rendered yet)", pageIndex + 1);
            }
        }

        if (activeSetCount > 1) {
            logger.error("CRITICAL: Active result set on {} pages! Should be 1!", activeSetCount);
        } else if (activeSetCount == 0 && activeResult != null) {
            logger.warn("Active result not set on any page (page may not be loaded yet)");
        }

        if (pagesMissing > 0) {
            logger.warn("{} pages missing AnnotationLayer - they may not be rendered yet", pagesMissing);
        }

        logger.info("Applied {} highlights to {} pages ({} missing, {} active)",
                highlightCount, resultsByPage.size() - pagesMissing, pagesMissing, activeSetCount);
    }

    private void clearHighlights() {
        resultsByPage.clear();
        activeResult = null;

        VBox pagesContainer = mainController.getPagesContainer();
        if (pagesContainer == null) {
            return;
        }
        for (VBox pageBox : collectPageBoxes(pagesContainer)) {
            AnnotationLayer layer = findAnnotationLayer(pageBox);
            if (layer != null) {
                layer.clearSearchHighlights();
            }
        }

        logger.debug("Search highlights cleared");
    }

    // ==================== ANNOTATION LAYER HELPERS ====================

    private AnnotationLayer findAnnotationLayer(VBox pageBox) {
        if (pageBox.getChildren().isEmpty()) {
            return null;
        }
        if (pageBox.getChildren().getFirst() instanceof StackPane stackPane) {
            for (Node node : stackPane.getChildren()) {
                if (node instanceof AnnotationLayer layer) {
                    syncLayerWithImage(stackPane, layer);
                    return layer;
                }
            }
        }
        return null;
    }

    private void syncLayerWithImage(StackPane stackPane, AnnotationLayer layer) {
        if (!stackPane.getChildren().isEmpty()
                && stackPane.getChildren().getFirst() instanceof ImageView imageView) {
            Image image = imageView.getImage();
            if (image != null) {
                double imageWidth = image.getWidth();
                double imageHeight = image.getHeight();

                if (layer.getWidth() != imageWidth || layer.getHeight() != imageHeight) {
                    layer.setWidth(imageWidth);
                    layer.setHeight(imageHeight);
                    logger.debug("Synced AnnotationLayer to image size: {}x{}",
                            imageWidth, imageHeight);
                }
            }
        }

        layer.setScale(mainController.getCurrentZoom());
    }

    private void updateAnnotationLayersScale(double newZoom) {
        VBox pagesContainer = mainController.getPagesContainer();
        if (pagesContainer == null) {
            return;
        }
        for (VBox pageBox : collectPageBoxes(pagesContainer)) {
            AnnotationLayer layer = findAnnotationLayer(pageBox);
            if (layer != null) {
                layer.setScale(newZoom);
                layer.redraw();
            }
        }
    }

    private java.util.List<VBox> collectPageBoxes(VBox pagesContainer) {
        return PageContainerUtils.collectPageBoxes(pagesContainer);
    }

    private VBox findPageBox(VBox pagesContainer, int pageIndex) {
        return PageContainerUtils.findPageBox(pagesContainer, pageIndex);
    }

    // ==================== GETTERS ====================

    /**
     * Checks if the search panel is currently visible.
     *
     * @return true if the search panel is visible
     */
    public boolean isSearchPanelVisible() {
        return searchPanelVisible;
    }

    /**
     * Gets the current search panel position.
     *
     * @return the current search panel position
     */
    public SearchPanelPosition getSearchPanelPosition() {
        return searchPanelPosition;
    }

    /**
     * Gets all search results organized by page number.
     *
     * @return a map of page numbers to lists of search results
     */
    public Map<Integer, List<SearchResult>> getResultsByPage() {
        return new HashMap<>(resultsByPage);
    }

    /**
     * Gets the currently active search result.
     *
     * @return the active search result, or null if none is active
     */
    public SearchResult getActiveResult() {
        return activeResult;
    }

    // ==================== LISTENER MANAGEMENT ====================

    private void setupPageChangeListeners() {
        VBox pagesContainer = mainController.getPagesContainer();
        if (pagesContainer == null) return;

        // Cleanup old listeners if any
        removePageListeners();

        // 1. Listen for new pages being added (e.g. from renderAllPages)
        containerListener = change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (Node node : change.getAddedSubList()) {
                        if (node instanceof VBox pageBox) {
                            registerPageBoxListener(pageBox);
                        } else if (node instanceof HBox row) {
                            for (Node child : row.getChildren()) {
                                if (child instanceof VBox pageBox) {
                                    registerPageBoxListener(pageBox);
                                }
                            }
                        }
                    }
                }
            }
        };
        pagesContainer.getChildren().addListener(containerListener);

        // 2. Register listeners for existing pages
        for (VBox pageBox : collectPageBoxes(pagesContainer)) {
            registerPageBoxListener(pageBox);
        }

        logger.debug("Page change listeners setup complete");
    }

    private void registerPageBoxListener(VBox pageBox) {
        if (pageListeners.containsKey(pageBox)) return;

        ListChangeListener<Node> listener = change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    // When content is added (e.g. Image loaded replacing placeholder),
                    // check if we have an AnnotationLayer and apply highlights.
                    Platform.runLater(() -> checkAndHighlightPage(pageBox));
                }
            }
        };

        pageBox.getChildren().addListener(listener);
        pageListeners.put(pageBox, listener);
    }

    private void checkAndHighlightPage(VBox pageBox) {
        AnnotationLayer layer = findAnnotationLayer(pageBox);
        if (layer != null) {
            String id = pageBox.getId();
            if (id != null && id.startsWith("page-")) {
                try {
                    int pageIndex = Integer.parseInt(id.replace("page-", ""));
                    List<SearchResult> results = resultsByPage.get(pageIndex);

                    if (results != null && !results.isEmpty()) {
                        layer.setSearchHighlights(results);

                        if (activeResult != null && activeResult.pageNumber() - 1 == pageIndex) {
                            layer.setActiveSearchResult(activeResult);
                        }
                        logger.debug("Restored highlights for page {} (re-rendered)", pageIndex + 1);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid page ID format: {}", id);
                }
            }
        }
    }

    private void removePageListeners() {
        VBox pagesContainer = mainController.getPagesContainer();

        // Remove container listener
        if (containerListener != null && pagesContainer != null) {
            pagesContainer.getChildren().removeListener(containerListener);
        }
        containerListener = null;

        // Remove page listeners
        pageListeners.forEach((box, listener) -> box.getChildren().removeListener(listener));
        pageListeners.clear();
    }
}
