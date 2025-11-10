package org.pdflite.controller;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.pdflite.model.PDFDocument;
import org.pdflite.model.SearchResult;
import org.pdflite.view.AnnotationLayer;
import org.pdflite.view.SearchPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import org.pdflite.util.NavigationHelper;

/**
 * Manages all search-related functionality including:
 * - Search panel lifecycle (float/left/right)
 * - Search results highlighting
 * - Active result navigation
 * - Highlight rendering coordination
 */
public class SearchManager {

    private static final Logger logger = LoggerFactory.getLogger(SearchManager.class);

    // Highlight styling constants
    private static final Color SEARCH_HIGHLIGHT_COLOR = Color.YELLOW;
    private static final Color ACTIVE_HIGHLIGHT_COLOR = Color.ORANGE;
    private static final double SEARCH_HIGHLIGHT_OPACITY = 0.4;
    private static final double ACTIVE_HIGHLIGHT_OPACITY = 0.6;

    // Dependencies
    private final MainController mainController;
    private final NavigationHelper navigationHelper;

    // Search panel state
    private SearchPanel searchPanel;
    private boolean searchPanelVisible = false;
    private SearchPanelPosition searchPanelPosition = SearchPanelPosition.FLOAT;

    // Search results state
    private final Map<Integer, List<SearchResult>> resultsByPage = new HashMap<>();
    private SearchResult activeResult = null;

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
     * Toggle search panel visibility with specified position
     */
    public void togglePanel(SearchPanelPosition position) {
        this.searchPanelPosition = position;

        if (searchPanelVisible && this.searchPanelPosition == position) {
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

        logger.info("Loading {} pages with search results...", resultsByPage.size());

        loadPagesWithResults(new ArrayList<>(resultsByPage.keySet()), () -> {
            Platform.runLater(() -> {
                updateAllHighlights();
                logger.info("Highlighted {} search results across {} pages",
                        results.size(), resultsByPage.size());
            });
        });
    }

    public void navigateToResult(SearchResult result) {
        if (result == null) {
            logger.warn("Attempted to navigate to null result");
            return;
        }

        this.activeResult = result;
        int pageIndex = result.getPageNumber() - 1;

        logger.info("Navigating to search result: page={}, start={}, end={}, pos=({}, {})",
                result.getPageNumber(), result.getStartIndex(), result.getEndIndex(),
                result.getX(), result.getY());

        if (pageIndex >= 0 && pageIndex < mainController.getTotalPages()) {
            navigationHelper.ensurePageLoadedAndReady(pageIndex, () -> {
                Platform.runLater(() -> {
                    navigationHelper.navigateToPage(pageIndex);

                    // Delay to ensure scroll completes
                    Platform.runLater(() -> {
                        updateAllHighlights();
                        logger.info("Active result updated on page {}", result.getPageNumber());
                    });
                });
            });
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
            updateAnnotationLayersScale(newZoom);
            updateAllHighlights();
            logger.debug("Updated highlights after zoom to {}%", newZoom * 100);
        });
    }

    // ==================== PANEL MANAGEMENT ====================

    private void showPanel(SearchPanelPosition position) {
        PDFDocument currentDocument = mainController.getCurrentDocument();
        if (currentDocument == null) {
            mainController.showError("No PDF Loaded", "Please open a PDF file first");
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
        searchPanel.setPrefWidth(320);
        searchPanel.setMinWidth(250);
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

        clearHighlights();
        logger.info("Search panel hidden");
    }

    // ==================== HIGHLIGHT COORDINATION ====================

    private void groupResultsByPage(List<SearchResult> results) {
        resultsByPage.clear();
        for (SearchResult result : results) {
            int pageIndex = result.getPageNumber() - 1;
            resultsByPage.computeIfAbsent(pageIndex, k -> new ArrayList<>())
                    .add(result);
        }
    }

    private void loadPagesWithResults(List<Integer> pageIndices, Runnable onComplete) {
        loadPagesRecursive(pageIndices, 0, onComplete);
    }

    private void loadPagesRecursive(List<Integer> pageIndices, int currentIndex, Runnable onComplete) {
        if (currentIndex >= pageIndices.size()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        int pageIndex = pageIndices.get(currentIndex);
        VBox pagesContainer = mainController.getPagesContainer();

        if (pageIndex < 0 || pageIndex >= pagesContainer.getChildren().size()) {
            loadPagesRecursive(pageIndices, currentIndex + 1, onComplete);
            return;
        }

        VBox pageBox = (VBox) pagesContainer.getChildren().get(pageIndex);

        if (navigationHelper.isPageRendered(pageBox)) {
            logger.debug("Page {} already rendered, skipping", pageIndex + 1);
            loadPagesRecursive(pageIndices, currentIndex + 1, onComplete);
        } else {
            logger.debug("Loading page {} for search highlights", pageIndex + 1);
            navigationHelper.loadPageAndWait(pageIndex, pageBox, () -> {
                loadPagesRecursive(pageIndices, currentIndex + 1, onComplete);
            });
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

            if (pageIndex < 0 || pageIndex >= pagesContainer.getChildren().size()) {
                logger.warn("Invalid page index: {}", pageIndex);
                continue;
            }

            VBox pageBox = (VBox) pagesContainer.getChildren().get(pageIndex);
            AnnotationLayer layer = findAnnotationLayer(pageBox);

            if (layer != null) {
                layer.setSearchHighlights(pageResults);
                highlightCount += pageResults.size();

                if (activeResult != null && activeResult.getPageNumber() - 1 == pageIndex) {
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

        for (Node child : pagesContainer.getChildren()) {
            if (child instanceof VBox pageBox) {
                AnnotationLayer layer = findAnnotationLayer(pageBox);
                if (layer != null) {
                    layer.clearSearchHighlights();
                }
            }
        }

        logger.debug("Search highlights cleared");
    }

    // ==================== ANNOTATION LAYER HELPERS ====================

    private AnnotationLayer findAnnotationLayer(VBox pageBox) {
        if (pageBox.getChildren().isEmpty()) {
            return null;
        }

        if (pageBox.getChildren().get(0) instanceof StackPane stackPane) {
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
                && stackPane.getChildren().get(0) instanceof ImageView imageView) {
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

        for (Node child : pagesContainer.getChildren()) {
            if (child instanceof VBox pageBox) {
                AnnotationLayer layer = findAnnotationLayer(pageBox);
                if (layer != null) {
                    layer.setScale(newZoom);
                    layer.redraw();
                }
            }
        }
    }

    // ==================== GETTERS ====================

    public boolean isSearchPanelVisible() {
        return searchPanelVisible;
    }

    public SearchPanelPosition getSearchPanelPosition() {
        return searchPanelPosition;
    }

    public Map<Integer, List<SearchResult>> getResultsByPage() {
        return new HashMap<>(resultsByPage);
    }

    public SearchResult getActiveResult() {
        return activeResult;
    }
}