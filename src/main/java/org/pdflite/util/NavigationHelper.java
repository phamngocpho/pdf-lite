package org.pdflite.util;

import java.io.IOException;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;

import org.pdflite.controller.MainController;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.pdflite.view.AnnotationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Utility class for page navigation and loading operations.
 * Handles:
 * - Page jumping and scrolling
 * - Lazy page loading
 * - Page render checking
 * - Scroll coordination
 *
 * @param mainController Dependencies
 */
public record NavigationHelper(MainController mainController, PDFService pdfService, ExecutorService renderExecutor,
                               Set<Integer> loadingPages) {

    private static final Logger logger = LoggerFactory.getLogger(NavigationHelper.class);

    /**
     * Constructor with dependency injection
     */
    public NavigationHelper {
    }

    // ==================== PUBLIC API ====================

    /**
     * Jump to a specific page number (1-based) with instant jump (no animation)
     */
    public void jumpToPage(int pageNumber) {
        jumpToPage(pageNumber, false);
    }

    /**
     * Jump to a specific page number (1-based)
     * @param pageNumber the page number (1-based)
     * @param smooth if true, use smooth animation; if false, jump instantly
     */
    public void jumpToPage(int pageNumber, boolean smooth) {
        PDFDocument currentDocument = mainController.getCurrentDocument();
        if (currentDocument == null) {
            return;
        }

        int pageIndex = pageNumber - 1;
        if (pageIndex < 0 || pageIndex >= currentDocument.getTotalPages()) {
            logger.warn("Invalid page number: {}", pageNumber);
            return;
        }

        currentDocument.setCurrentPage(pageIndex);

        // Lock scroll-based page updates during navigation
        org.pdflite.controller.ScrollHandler scrollHandler = mainController.getScrollHandler();
        if (scrollHandler != null) {
            scrollHandler.lockPageUpdates();
        }

        Platform.runLater(() -> {
            if (smooth) {
                scrollToPage(pageIndex);
            } else {
                scrollToPageInstant(pageIndex);
            }
            mainController.updatePageInfo();
        });

        logger.info("Jumped to page {}", pageNumber);
    }

    /**
     * Navigate to the page by index (0-based) with instant jump (no animation)
     */
    public void navigateToPage(int pageIndex) {
        navigateToPage(pageIndex, false);
    }

    /**
     * Navigate to the page by index (0-based)
     * @param pageIndex the page index (0-based)
     * @param smooth if true, use smooth animation; if false, jump instantly
     */
    public void navigateToPage(int pageIndex, boolean smooth) {
        PDFDocument currentDocument = mainController.getCurrentDocument();
        if (currentDocument == null) {
            return;
        }

        currentDocument.setCurrentPage(pageIndex);
        
        // Lock scroll-based page updates during navigation
        org.pdflite.controller.ScrollHandler scrollHandler = mainController.getScrollHandler();
        if (scrollHandler != null) {
            scrollHandler.lockPageUpdates();
        }
        
        if (smooth) {
            scrollToCurrentPage();
        } else {
            scrollToPageInstant(pageIndex);
        }
        mainController.updatePageInfo();
    }

    /**
     * Scroll to the current page in documents with smooth animation
     */
    public void scrollToCurrentPage() {
        PDFDocument currentDocument = mainController.getCurrentDocument();
        VBox pagesContainer = mainController.getPagesContainer();
        ScrollPane scrollPane = mainController.getScrollPane();

        if (currentDocument == null || pagesContainer == null || scrollPane == null) {
            return;
        }

        Platform.runLater(() -> {
            try {
                int targetPage = currentDocument.getCurrentPage();
                double currentY = ScrollCalculator.calculatePageYPosition(pagesContainer, targetPage);

                double contentHeight = pagesContainer.getHeight();
                double viewportHeight = scrollPane.getViewportBounds().getHeight();

                if (contentHeight > viewportHeight) {
                    double targetV = currentY / (contentHeight - viewportHeight);
                    smoothScrollTo(scrollPane, targetV, Duration.millis(350));
                }
            } catch (Exception e) {
                logger.error("Error scrolling to page", e);
            }
        });
    }

    /**
     * Smooth scroll to the target position with animation
     */
    private void smoothScrollTo(ScrollPane scrollPane, double targetVValue, Duration duration) {
        if (scrollPane == null) return;

        double start = scrollPane.getVvalue();
        double target = Math.max(0.0, Math.min(1.0, targetVValue));
        if (Math.abs(target - start) < 1e-4) return;

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(scrollPane.vvalueProperty(), start, Interpolator.EASE_BOTH)),
                new KeyFrame(duration,
                        new KeyValue(scrollPane.vvalueProperty(), target, Interpolator.EASE_BOTH))
        );
        timeline.play();
    }

    /**
     * Scroll to a specific page index with centering and smooth animation
     */
    public void scrollToPage(int pageIndex) {
        VBox pagesContainer = mainController.getPagesContainer();
        ScrollPane scrollPane = mainController.getScrollPane();

        if (pagesContainer == null || scrollPane == null) {
            logger.warn("Cannot scroll - container or scroll pane is null");
            return;
        }

        Platform.runLater(() -> {
            try {
                if (pageIndex < 0 || pageIndex >= mainController.getTotalPages()) {
                    logger.warn("Invalid page index for scrolling: {}", pageIndex);
                    return;
                }

                pagesContainer.layout();

                double currentY = ScrollCalculator.calculatePageYPosition(pagesContainer, pageIndex);

                double contentHeight = pagesContainer.getHeight();
                double viewportHeight = scrollPane.getViewportBounds().getHeight();

                if (contentHeight > viewportHeight) {
                    double pageHeight = ScrollCalculator.calculatePageBounds(pagesContainer, pageIndex).height();
                    double centerOffset = Math.max(0, (viewportHeight - pageHeight) / 2);
                    double adjustedY = Math.max(0, currentY - centerOffset);

                    double maxScroll = contentHeight - viewportHeight;
                    double scrollPosition = adjustedY / maxScroll;

                    smoothScrollTo(scrollPane, scrollPosition, Duration.millis(350));

                    logger.debug("Scrolled to page {} at position {} (currentY={}, adjustedY={})",
                            pageIndex + 1, scrollPosition, currentY, adjustedY);
                }

            } catch (Exception e) {
                logger.error("Error scrolling to page {}", pageIndex + 1, e);
            }
        });
    }

    /**
     * Scroll to a specific page index instantly (no animation)
     */
    public void scrollToPageInstant(int pageIndex) {
        VBox pagesContainer = mainController.getPagesContainer();
        ScrollPane scrollPane = mainController.getScrollPane();

        if (pagesContainer == null || scrollPane == null) {
            logger.warn("Cannot scroll - container or scroll pane is null");
            return;
        }

        // Ensure target page is loaded before scrolling to prevent flicker
        VBox targetPageBox = PageContainerUtils.findPageBox(pagesContainer, pageIndex);
        if (targetPageBox != null && !isPageRendered(targetPageBox)) {
            // Pre-load the target page synchronously-ish via Platform.runLater chain
            loadingPages.add(pageIndex);
            renderExecutor.submit(() -> {
                try {
                    PDFDocument currentDocument = mainController.getCurrentDocument();
                    double currentZoom = mainController.getCurrentZoom();
                    Image image = pdfService.renderPage(currentDocument, pageIndex, (float) currentZoom);
                    
                    Platform.runLater(() -> {
                        ImageView imageView = new ImageView(image);
                        imageView.setPreserveRatio(true);
                        imageView.setSmooth(true);
                        imageView.setCache(true);

                        AnnotationLayer annotationLayer = new AnnotationLayer(image.getWidth(), image.getHeight());
                        if (mainController.isHighlightModeActive()) {
                            annotationLayer.setAnnotationMode(AnnotationLayer.AnnotationMode.HIGHLIGHT);
                        }

                        StackPane imageStack = new StackPane(imageView, annotationLayer);
                        imageStack.setAlignment(Pos.CENTER);

                        if (!targetPageBox.getChildren().isEmpty()) {
                            targetPageBox.getChildren().set(0, imageStack);
                        }
                        loadingPages.remove(pageIndex);
                        
                        // Now scroll after page is loaded
                        doInstantScroll(pagesContainer, scrollPane, pageIndex);
                    });
                } catch (IOException e) {
                    logger.error("Error pre-loading page {}", pageIndex + 1, e);
                    loadingPages.remove(pageIndex);
                    // Still try to scroll even if load failed
                    Platform.runLater(() -> doInstantScroll(pagesContainer, scrollPane, pageIndex));
                }
            });
        } else {
            // Page already loaded, scroll immediately
            Platform.runLater(() -> doInstantScroll(pagesContainer, scrollPane, pageIndex));
        }
    }

    /**
     * Performs the actual instant scroll to a page
     */
    private void doInstantScroll(VBox pagesContainer, ScrollPane scrollPane, int pageIndex) {
        try {
            if (pageIndex < 0 || pageIndex >= mainController.getTotalPages()) {
                logger.warn("Invalid page index for scrolling: {}", pageIndex);
                return;
            }

            pagesContainer.layout();

            double currentY = ScrollCalculator.calculatePageYPosition(pagesContainer, pageIndex);

            double contentHeight = pagesContainer.getHeight();
            double viewportHeight = scrollPane.getViewportBounds().getHeight();

            if (contentHeight > viewportHeight) {
                double pageHeight = ScrollCalculator.calculatePageBounds(pagesContainer, pageIndex).height();
                double centerOffset = Math.max(0, (viewportHeight - pageHeight) / 2);
                double adjustedY = Math.max(0, currentY - centerOffset);

                double maxScroll = contentHeight - viewportHeight;
                double scrollPosition = Math.max(0.0, Math.min(1.0, adjustedY / maxScroll));

                // Instant jump - no animation
                scrollPane.setVvalue(scrollPosition);

                logger.debug("Instant jumped to page {} at position {}", pageIndex + 1, scrollPosition);
            }

        } catch (Exception e) {
            logger.error("Error scrolling to page {}", pageIndex + 1, e);
        }
    }

    /**
     * Ensure the page is fully loaded and the layout is ready
     */
    public void ensurePageLoadedAndReady(int pageIndex, Runnable callback) {
        VBox pagesContainer = mainController.getPagesContainer();
        if (pagesContainer == null || pageIndex < 0 || pageIndex >= mainController.getTotalPages()) {
            logger.warn("Invalid page index or container: {}", pageIndex);
            return;
        }

        VBox pageBox = findPageBox(pagesContainer, pageIndex);

        if (isPageRendered(pageBox)) {
            logger.debug("Page {} already rendered", pageIndex + 1);
            if (callback != null) {
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> {
                            pageBox.layout();

                            if (!pageBox.getChildren().isEmpty()
                                    && pageBox.getChildren().getFirst() instanceof StackPane stackPane) {
                                stackPane.layout();
                            }

                            callback.run();
                        });
                    }
                }, 200);
            }
            return;
        }

        logger.info("Force loading page {} before highlight", pageIndex + 1);
        loadPageAndWait(pageIndex, pageBox, callback);
    }

    /**
     * Load the page and wait for render completion
     */
    public void loadPageAndWait(int pageIndex, VBox pageBox, Runnable callback) {
        loadingPages.add(pageIndex);

        renderExecutor.submit(() -> {
            try {
                PDFDocument currentDocument = mainController.getCurrentDocument();
                double currentZoom = mainController.getCurrentZoom();

                Image image = pdfService.renderPage(
                        currentDocument,
                        pageIndex,
                        (float) currentZoom
                );

                Platform.runLater(() -> {
                    ImageView imageView = new ImageView(image);
                    imageView.setPreserveRatio(true);
                    imageView.setSmooth(true);
                    imageView.setCache(true);

                    AnnotationLayer annotationLayer = new AnnotationLayer(
                            image.getWidth(),
                            image.getHeight()
                    );

                    if (mainController.isHighlightModeActive()) {
                        annotationLayer.setAnnotationMode(AnnotationLayer.AnnotationMode.HIGHLIGHT);
                    }

                    StackPane imageStack = new StackPane(imageView, annotationLayer);
                    imageStack.setAlignment(Pos.CENTER);

                    if (!pageBox.getChildren().isEmpty()) {
                        pageBox.getChildren().set(0, imageStack);
                    }

                    loadingPages.remove(pageIndex);
                    logger.debug("Force loaded page {} with AnnotationLayer", pageIndex + 1);

                    if (callback != null) {
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                Platform.runLater(() -> {
                                    imageStack.layout();
                                    pageBox.layout();

                                    logger.debug("Page {} render completed, executing callback", pageIndex + 1);
                                    callback.run();
                                });
                            }
                        }, 300);
                    }
                });

            } catch (IOException e) {
                logger.error("Error force loading page {}", pageIndex + 1, e);
                loadingPages.remove(pageIndex);

                Platform.runLater(() -> {
                    if (!pageBox.getChildren().isEmpty()
                            && pageBox.getChildren().getFirst() instanceof StackPane stackPane) {
                        Label errorLabel = new Label("Error loading page");
                        errorLabel.setStyle("-fx-text-fill: red;");
                        stackPane.getChildren().set(0, errorLabel);
                    }
                });
            }
        });
    }

    /**
     * Check if the page is already rendered (has ImageView)
     */
    public boolean isPageRendered(VBox pageBox) {
        if (pageBox == null || pageBox.getChildren().isEmpty()) {
            return false;
        }

        Node firstChild = pageBox.getChildren().getFirst();

        if (firstChild instanceof StackPane stackPane) {
            if (!stackPane.getChildren().isEmpty()) {
                Node content = stackPane.getChildren().getFirst();
                return content instanceof ImageView;
            }
        }

        return false;
    }

    private static VBox findPageBox(VBox pagesContainer, int pageIndex) {
        return PageContainerUtils.findPageBox(pagesContainer, pageIndex);
    }
}