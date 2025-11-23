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

    private static final long SCROLL_DEBOUNCE_MS = 50; // Wait 50ms after scroll stops

    private final PageRenderer pageRenderer;
    private final ScrollPane scrollPane;

    private PDFDocument currentDocument;
    private VBox pagesContainer;
    private Timer scrollTimer;
    private volatile long lastScrollTime = 0;
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
        Platform.runLater(this::updateCurrentPageFromScroll);
    }

    /**
     * Loads pages that are currently visible or near the viewport.
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
                double bufferSize = scrollPane.getViewportBounds().getHeight();
                double scrollValue = scrollPane.getVvalue();

                int totalPages = currentDocument.getTotalPages();

                // Calculate total content height based on all page placeholders
                // This ensures accurate calculation even when pages haven't been rendered yet
                double contentHeight = getContentHeight(totalPages);

                if (contentHeight <= bufferSize) {
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
                double visibleStart = scrollValue * (contentHeight - bufferSize);
                double visibleEnd = visibleStart + bufferSize;

                // Add a buffer zone (1 viewport above and below for smooth scrolling)
                double loadStart = Math.max(0, visibleStart - bufferSize);
                double loadEnd = Math.min(contentHeight, visibleEnd + bufferSize);

                // Priority lists: visible pages first, then nearby pages
                List<Integer> visiblePages = new ArrayList<>();
                List<Integer> nearbyPages = new ArrayList<>();
                Set<Integer> pagesInRange = new HashSet<>();

                for (int i = 0; i < totalPages; i++) {
                    // Safety check: ensure the page box exists
                    ScrollCalculator.PageBounds bounds = ScrollCalculator.calculatePageBounds(pagesContainer, i);

                    // Check if the page is in load range
                    if (bounds.overlaps(loadStart, loadEnd)) {
                        pagesInRange.add(i);

                        // Prioritize visible pages over buffer pages
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

                // Load visible pages first (high priority)
                for (int pageIndex : visiblePages) {
                    if (pageRenderer.isPageLoading(pageIndex)) {
                        VBox pageBox = getPageBox(pageIndex);
                        if (pageBox != null && isPlaceholder(pageBox)) {
                            pageRenderer.loadPage(pageIndex, pageBox);
                        }
                    }
                }

                // Then load nearby pages (lower priority)
                for (int pageIndex : nearbyPages) {
                    if (pageRenderer.isPageLoading(pageIndex)) {
                        VBox pageBox = getPageBox(pageIndex);
                        if (pageBox != null && isPlaceholder(pageBox)) {
                            pageRenderer.loadPage(pageIndex, pageBox);
                        }
                    }
                }

                // Additional safeguard: if we're near the end (last 5 pages), ensure they get loaded
                if (scrollValue > 0.8 && totalPages > 0) {
                    int startPage = Math.max(0, totalPages - 5);
                    for (int i = startPage; i < totalPages; i++) {
                        VBox pageBox = getPageBox(i);
                        if (pageBox != null && pageRenderer.isPageLoading(i) && isPlaceholder(pageBox)) {
                            pageRenderer.loadPage(i, pageBox);
                        }
                    }
                }

            } catch (Exception e) {
                logger.error("Error loading visible pages", e);
            }
        });
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

