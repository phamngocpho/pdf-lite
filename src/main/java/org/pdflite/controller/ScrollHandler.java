package org.pdflite.controller;

import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

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
    
    private static final long SCROLL_DEBOUNCE_MS = 150; // Wait 150ms after scroll stops
    
    private final PageRenderer pageRenderer;
    private final ScrollPane scrollPane;
    
    private PDFDocument currentDocument;
    private VBox pagesContainer;
    private Timer scrollTimer;
    private volatile long lastScrollTime = 0;

    /**
     * Creates a new ScrollHandler.
     *
     * @param pageRenderer the page renderer for loading pages
     * @param scrollPane the scroll pane to monitor
     */
    public ScrollHandler(PageRenderer pageRenderer, ScrollPane scrollPane) {
        this.pageRenderer = pageRenderer;
        this.scrollPane = scrollPane;
    }

    /**
     * Sets the current document and pages container.
     *
     * @param document the PDF document
     * @param pagesContainer the container holding all page boxes
     */
    public void setDocument(PDFDocument document, VBox pagesContainer) {
        this.currentDocument = document;
        this.pagesContainer = pagesContainer;
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

        // Start new timer to load pages after scroll stops
        scrollTimer = new Timer();
        scrollTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                loadVisiblePages();
            }
        }, SCROLL_DEBOUNCE_MS);

        // Immediately update current page indicator
        updateCurrentPageFromScroll();
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
                double contentHeight = pagesContainer.getHeight();

                if (contentHeight <= bufferSize) {
                    // All content is visible, load all pages
                    int totalPages = currentDocument.getTotalPages();
                    for (int i = 0; i < totalPages; i++) {
                        if (pageRenderer.isPageLoading(i)) {
                            VBox pageBox = (VBox) pagesContainer.getChildren().get(i);
                            if (isPlaceholder(pageBox)) {
                                pageRenderer.loadPage(i, pageBox);
                            }
                        }
                    }
                    return;
                }

                // Calculate visible range
                double visibleStart = scrollValue * (contentHeight - bufferSize);
                double visibleEnd = visibleStart + bufferSize;

                // Add buffer zone (1 viewport above and below for smooth scrolling)
                double loadStart = Math.max(0, visibleStart - bufferSize);
                double loadEnd = Math.min(contentHeight, visibleEnd + bufferSize);

                int totalPages = currentDocument.getTotalPages();
                double currentY = 0;

                // Priority lists: visible pages first, then nearby pages
                List<Integer> visiblePages = new ArrayList<>();
                List<Integer> nearbyPages = new ArrayList<>();
                Set<Integer> pagesInRange = new HashSet<>();

                for (int i = 0; i < totalPages; i++) {
                    VBox pageBox = (VBox) pagesContainer.getChildren().get(i);
                    double pageHeight = pageBox.getPrefHeight();
                    double pageStart = currentY;
                    double pageEnd = currentY + pageHeight;

                    // Check if page is in load range
                    if (pageEnd >= loadStart && pageStart <= loadEnd) {
                        pagesInRange.add(i);
                        
                        // Prioritize visible pages over buffer pages
                        if (pageEnd >= visibleStart && pageStart <= visibleEnd) {
                            visiblePages.add(i);
                        } else {
                            nearbyPages.add(i);
                        }
                    }

                    currentY = pageEnd + 10; // Add spacing
                }

                // Cancel pending renders outside the load range (fast scroll optimization)
                if (isFastScrolling) {
                    pageRenderer.cancelOutOfRangePendingRenders(pagesInRange);
                }

                // Load visible pages first (high priority)
                for (int pageIndex : visiblePages) {
                    if (pageRenderer.isPageLoading(pageIndex)) {
                        VBox pageBox = (VBox) pagesContainer.getChildren().get(pageIndex);
                        if (isPlaceholder(pageBox)) {
                            pageRenderer.loadPage(pageIndex, pageBox);
                        }
                    }
                }

                // Then load nearby pages (lower priority)
                for (int pageIndex : nearbyPages) {
                    if (pageRenderer.isPageLoading(pageIndex)) {
                        VBox pageBox = (VBox) pagesContainer.getChildren().get(pageIndex);
                        if (isPlaceholder(pageBox)) {
                            pageRenderer.loadPage(pageIndex, pageBox);
                        }
                    }
                }

            } catch (Exception e) {
                logger.error("Error loading visible pages", e);
            }
        });
    }

    /**
     * Checks if a page box contains a placeholder (not yet loaded).
     *
     * @param pageBox the page box to check
     * @return true if the page box contains a placeholder
     */
    private boolean isPlaceholder(VBox pageBox) {
        if (pageBox.getChildren().isEmpty()) {
            return false;
        }
        
        Object firstChild = pageBox.getChildren().getFirst();
        if (firstChild instanceof javafx.scene.layout.StackPane placeholder) {
            return !placeholder.getChildren().isEmpty() &&
                    placeholder.getChildren().getFirst() instanceof javafx.scene.control.Label;
        }
        return false;
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
            double contentHeight = pagesContainer.getHeight();

            if (contentHeight <= viewportHeight) {
                return; // All content visible, stay on current page
            }

            double visibleStart = scrollValue * (contentHeight - viewportHeight);
            double visibleCenter = visibleStart + (viewportHeight / 2);

            int totalPages = currentDocument.getTotalPages();
            double currentY = 0;

            for (int i = 0; i < totalPages; i++) {
                VBox pageBox = (VBox) pagesContainer.getChildren().get(i);
                double pageHeight = pageBox.getPrefHeight();
                double pageEnd = currentY + pageHeight;

                if (visibleCenter >= currentY && visibleCenter < pageEnd) {
                    if (currentDocument.getCurrentPage() != i) {
                        currentDocument.setCurrentPage(i);
                    }
                    break;
                }

                currentY = pageEnd + 10; // Add spacing
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
            double currentY = 0;

            for (int i = 0; i < totalPages; i++) {
                VBox pageBox = (VBox) pagesContainer.getChildren().get(i);
                double pageHeight = pageBox.getPrefHeight();
                double pageEnd = currentY + pageHeight;

                if (visibleCenter >= currentY && visibleCenter < pageEnd) {
                    return i;
                }

                currentY = pageEnd + 10;
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
                double currentY = 0;

                for (int i = 0; i < pageIndex; i++) {
                    VBox pageBox = (VBox) pagesContainer.getChildren().get(i);
                    currentY += pageBox.getPrefHeight() + 10; // Add spacing
                }

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

