package org.pdflite.controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.pdflite.model.PDFDocument;
import org.pdflite.util.PageContainerUtils;
import org.pdflite.util.ScrollCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/**
 * Handles scroll events and lazy loading of PDF pages.
 * <p>
 * This class manages scroll event debouncing, calculates visible page ranges,
 * and coordinates with PageRenderer to load pages on demand.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 */
public class ScrollHandler {
    private static final Logger logger = LoggerFactory.getLogger(ScrollHandler.class);

    private static final long SCROLL_DEBOUNCE_MS = 100; // Wait 100 ms after scroll stops (increased to reduce load frequency)
    private static final long NAVIGATION_LOCK_MS = 500; // Lock page updates during navigation animation

    private final PageRenderer pageRenderer;
    private final ScrollPane scrollPane;

    private PDFDocument currentDocument;
    private VBox pagesContainer;
    private Timer scrollTimer;
    private volatile long lastScrollTime = 0;
    private volatile long navigationLockUntil = 0; // Timestamp until which page updates are locked
    private PageChangeListener pageChangeListener;

    /**
     * Listener interface for page change events.
     */
    public interface PageChangeListener {
        void onPageChanged(int newPageIndex);
    }

    /**
     * Creates a new ScrollHandler.
     *
     * @param pageRenderer the page renderer for loading pages
     * @param scrollPane   the scroll pane to monitor
     */
    public ScrollHandler(PageRenderer pageRenderer, ScrollPane scrollPane) {
        this.pageRenderer = pageRenderer;
        this.scrollPane = scrollPane;
    }

    /**
     * Sets the current document and pages container.
     *
     * @param document       the PDF document
     * @param pagesContainer the container holding all page boxes
     */
    public void setDocument(PDFDocument document, VBox pagesContainer) {
        this.currentDocument = document;
        this.pagesContainer = pagesContainer;
    }

    /**
     * Sets the page change listener.
     *
     * @param listener the listener to notify when the page changes
     */
    public void setPageChangeListener(PageChangeListener listener) {
        this.pageChangeListener = listener;
    }

    /**
     * Locks page updates from scroll for a short duration.
     * This should be called when programmatically navigating to prevent
     * scroll events from overriding the navigation.
     */
    public void lockPageUpdates() {
        this.navigationLockUntil = System.currentTimeMillis() + NAVIGATION_LOCK_MS;
    }

    /**
     * Checks if page updates are currently locked.
     */
    private boolean isPageUpdateLocked() {
        return System.currentTimeMillis() < navigationLockUntil;
    }

    /**
     * Handles scroll events with debouncing.
     * This should be called when the scroll value changes.
     */
    public void handleScroll() {
        if (currentDocument == null || pagesContainer == null) {
            return;
        }

        // Cancel previous timer
        if (scrollTimer != null) {
            scrollTimer.cancel();
        }

        // Start a new timer to load pages after scroll stops
        scrollTimer = new Timer();
        scrollTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                loadVisiblePages();
            }
        }, SCROLL_DEBOUNCE_MS);

        // Update current page indicator on JavaFX thread to ensure UI is updated
        // Skip if page updates are locked (during programmatic navigation)
        if (!isPageUpdateLocked()) {
            Platform.runLater(this::updateCurrentPageFromScroll);
        }
    }

    /**
     * Loads pages that are currently visible or near the viewport.
     * Optimized for high zoom levels to prevent lag.
     */
    private void loadVisiblePages() {
        if (currentDocument == null || pagesContainer == null || scrollPane == null) {
            return;
        }

        // Detect fast scrolling
        long currentTime = System.currentTimeMillis();
        boolean isFastScrolling = (currentTime - lastScrollTime) < SCROLL_DEBOUNCE_MS;
        lastScrollTime = currentTime;

        Platform.runLater(() -> {
            try {
                double viewportHeight = scrollPane.getViewportBounds().getHeight();
                double scrollValue = scrollPane.getVvalue();

                int totalPages = currentDocument.getTotalPages();
                double currentZoom = currentDocument.getZoomLevel();

                // Calculate total content height based on all page placeholders
                double contentHeight = getContentHeight(totalPages);

                if (contentHeight <= viewportHeight) {
                    // All content is visible, load all pages
                    for (int i = 0; i < totalPages; i++) {
                        if (pageRenderer.isPageLoading(i)) {
                            VBox pageBox = getPageBox(i);
                            if (pageBox != null && isPlaceholder(pageBox)) {
                                pageRenderer.loadPage(i, pageBox);
                            }
                        }
                    }
                    return;
                }

                // Calculate visible range
                double visibleStart = scrollValue * (contentHeight - viewportHeight);
                double visibleEnd = visibleStart + viewportHeight;

                // Adaptive buffer zone: reduce buffer to prevent lag
                // Smaller buffer = fewer pages loaded = less lag
                // At 100% zoom: 0.5 viewport buffer (reduced from 1.0)
                // At 120%+ zoom: 0.3 viewport buffer
                // At 150%+ zoom: 0.2 viewport buffer
                double bufferMultiplier = currentZoom >= 1.5 ? 0.2 : (currentZoom >= 1.2 ? 0.3 : 0.5);
                double bufferSize = viewportHeight * bufferMultiplier;

                double loadStart = Math.max(0, visibleStart - bufferSize);
                double loadEnd = Math.min(contentHeight, visibleEnd + bufferSize);

                // Priority lists: visible pages first, then nearby pages
                List<Integer> visiblePages = new ArrayList<>();
                List<Integer> nearbyPages = new ArrayList<>();
                Set<Integer> pagesInRange = new HashSet<>();

                for (int i = 0; i < totalPages; i++) {
                    ScrollCalculator.PageBounds bounds = ScrollCalculator.calculatePageBounds(pagesContainer, i);

                    if (bounds.overlaps(loadStart, loadEnd)) {
                        pagesInRange.add(i);

                        if (bounds.overlaps(visibleStart, visibleEnd)) {
                            visiblePages.add(i);
                        } else {
                            nearbyPages.add(i);
                        }
                    }
                }

                // Cancel pending renders outside the load range (fast scroll optimization)
                if (isFastScrolling) {
                    pageRenderer.cancelOutOfRangePendingRenders(pagesInRange);
                }

                // Limit concurrent page loads to prevent lag
                // Always limit, not just at high zoom
                int maxConcurrentLoads = currentZoom >= 1.5 ? 2 : (currentZoom >= 1.2 ? 3 : 5);
                int loadedCount = 0;

                // Load visible pages first (high priority)
                loadedCount = loadPagesInList(visiblePages, maxConcurrentLoads, loadedCount);

                // Then load nearby pages (lower priority) - only if we haven't hit the limit
                loadedCount = loadPagesInList(nearbyPages, maxConcurrentLoads, loadedCount);

                // Always unload pages far from the viewport to prevent lag
                // Adaptive unload distance based on zoom
                double unloadDistance = currentZoom >= 1.5 ? viewportHeight :
                        (currentZoom >= 1.2 ? viewportHeight * 1.5 : viewportHeight * 2.0);
                double unloadStart = Math.max(0, visibleStart - unloadDistance);
                double unloadEnd = Math.min(contentHeight, visibleEnd + unloadDistance);

                // Unload pages outside the keep range
                for (int i = 0; i < totalPages; i++) {
                    ScrollCalculator.PageBounds bounds = ScrollCalculator.calculatePageBounds(pagesContainer, i);
                    if (!bounds.overlaps(unloadStart, unloadEnd)) {
                        // Page is far from viewport, unload it
                        VBox pageBox = getPageBox(i);
                        if (pageBox != null && !isPlaceholder(pageBox)) {
                            pageRenderer.unloadPage(i, pageBox);
                        }
                    }
                }

                // Additional safeguard: if we're near the end (last 3 pages), ensure they get loaded
                if (scrollValue > 0.85 && totalPages > 0 && loadedCount < maxConcurrentLoads) {
                    int startPage = Math.max(0, totalPages - 3);
                    for (int i = startPage; i < totalPages; i++) {
                        if (loadedCount >= maxConcurrentLoads) break;
                        VBox pageBox = getPageBox(i);
                        if (pageBox != null && pageRenderer.isPageLoading(i) && isPlaceholder(pageBox)) {
                            pageRenderer.loadPage(i, pageBox);
                            loadedCount++;
                        }
                    }
                }

            } catch (Exception e) {
                logger.error("Error loading visible pages", e);
            }
        });
    }

    /**
     * Loads pages from a list up to the maximum concurrent load limit.
     *
     * @param pageList           the list of page indices to load
     * @param maxConcurrentLoads the maximum number of pages to load
     * @param currentLoadedCount the current count of loaded pages
     * @return the updated count of loaded pages
     */
    private int loadPagesInList(List<Integer> pageList, int maxConcurrentLoads, int currentLoadedCount) {
        int loadedCount = currentLoadedCount;
        for (int pageIndex : pageList) {
            if (loadedCount >= maxConcurrentLoads) break;
            if (pageRenderer.isPageLoading(pageIndex)) {
                VBox pageBox = getPageBox(pageIndex);
                if (pageBox != null && isPlaceholder(pageBox)) {
                    pageRenderer.loadPage(pageIndex, pageBox);
                    loadedCount++;
                }
            }
        }
        return loadedCount;
    }

    private double getContentHeight(int totalPages) {
        return ScrollCalculator.calculateContentHeight(pagesContainer, totalPages);
    }

    /**
     * Checks if a page box contains a placeholder (not yet loaded).
     * A placeholder is a StackPane that doesn't contain an ImageView.
     *
     * @param pageBox the page box to check
     * @return true if the page box contains a placeholder
     */
    private boolean isPlaceholder(VBox pageBox) {
        if (pageBox.getChildren().isEmpty()) {
            return true;
        }

        Object firstChild = pageBox.getChildren().isEmpty() ? null : pageBox.getChildren().getFirst();
        if (firstChild instanceof javafx.scene.layout.StackPane stackPane) {
            // If StackPane contains an ImageView, it's rendered
            boolean hasImageView = stackPane.getChildren().stream()
                    .anyMatch(child -> child instanceof javafx.scene.image.ImageView);
            return !hasImageView;
        }

        // If it's not a StackPane, it's likely a placeholder
        return true;
    }

    /**
     * Returns the VBox that represents the page at the given pageIndex regardless
     * of whether pagesContainer is arranged as single VBoxes or rows (HBox)
     */
    private VBox getPageBox(int pageIndex) {
        return PageContainerUtils.findPageBox(pagesContainer, pageIndex);
    }

    /**
     * Updates the current page indicator based on scroll position.
     */
    private void updateCurrentPageFromScroll() {
        // Skip if page updates are locked (during programmatic navigation)
        if (isPageUpdateLocked()) {
            return;
        }

        if (currentDocument == null || pagesContainer == null || scrollPane == null) {
            return;
        }

        try {
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double scrollValue = scrollPane.getVvalue();

            // Use calculated content height instead of actual height for more accurate calculation
            int totalPages = currentDocument.getTotalPages();
            double contentHeight = getContentHeight(totalPages);

            if (contentHeight <= viewportHeight) {
                // All content visible, set to the first page
                if (currentDocument.getCurrentPage() != 0) {
                    currentDocument.setCurrentPage(0);
                    // Notify listener about page change
                    if (pageChangeListener != null) {
                        pageChangeListener.onPageChanged(0);
                    }
                }
                return;
            }

            double visibleStart = scrollValue * (contentHeight - viewportHeight);
            double visibleCenter = visibleStart + (viewportHeight / 2);


            for (int i = 0; i < totalPages; i++) {
                ScrollCalculator.PageBounds bounds = ScrollCalculator.calculatePageBounds(pagesContainer, i);

                if (bounds.contains(visibleCenter)) {
                    if (currentDocument.getCurrentPage() != i) {
                        currentDocument.setCurrentPage(i);
                        // Notify listener about page change
                        if (pageChangeListener != null) {
                            pageChangeListener.onPageChanged(i);
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Error updating current page from scroll", e);
        }
    }

    /**
     * Gets the current page index based on scroll position.
     * This method can be used by external code to get the current page.
     *
     * @return the current page index, or -1 if unable to determine
     */
    public int getCurrentPageFromScroll() {
        if (currentDocument == null || pagesContainer == null || scrollPane == null) {
            return -1;
        }

        try {
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double scrollValue = scrollPane.getVvalue();
            double contentHeight = pagesContainer.getHeight();

            if (contentHeight <= viewportHeight) {
                return 0;
            }

            double visibleStart = scrollValue * (contentHeight - viewportHeight);
            double visibleCenter = visibleStart + (viewportHeight / 2);

            int totalPages = currentDocument.getTotalPages();

            for (int i = 0; i < totalPages; i++) {
                ScrollCalculator.PageBounds bounds = ScrollCalculator.calculatePageBounds(pagesContainer, i);

                if (bounds.contains(visibleCenter)) {
                    return i;
                }
            }
        } catch (Exception e) {
            logger.error("Error getting current page from scroll", e);
        }

        return -1;
    }

    /**
     * Scrolls to a specific page.
     *
     * @param pageIndex the page index to scroll to
     */
    public void scrollToPage(int pageIndex) {
        if (currentDocument == null || pagesContainer == null || scrollPane == null) {
            return;
        }

        Platform.runLater(() -> {
            try {
                double currentY = ScrollCalculator.calculatePageYPosition(pagesContainer, pageIndex);

                double contentHeight = pagesContainer.getHeight();
                double viewportHeight = scrollPane.getViewportBounds().getHeight();

                if (contentHeight > viewportHeight) {
                    double scrollPosition = currentY / (contentHeight - viewportHeight);
                    scrollPane.setVvalue(Math.min(1.0, Math.max(0.0, scrollPosition)));
                }
            } catch (Exception e) {
                logger.error("Error scrolling to page", e);
            }
        });
    }

    /**
     * Cleans up resources.
     */
    public void cleanup() {
        if (scrollTimer != null) {
            scrollTimer.cancel();
            scrollTimer = null;
        }
    }
}

