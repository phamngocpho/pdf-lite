package org.pdflite.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import org.pdflite.controller.MainController;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.pdflite.view.AnnotationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;

/**
 * Utility class for page navigation and loading operations.
 * Handles:
 * - Page jumping and scrolling
 * - Lazy page loading
 * - Page render checking
 * - Scroll coordination
 */
public class NavigationHelper {

    private static final Logger logger = LoggerFactory.getLogger(NavigationHelper.class);

    // Dependencies
    private final MainController mainController;
    private final PDFService pdfService;
    private final ExecutorService renderExecutor;
    private final Set<Integer> loadingPages;

    /**
     * Constructor with dependency injection
     */
    public NavigationHelper(MainController mainController,
                            PDFService pdfService,
                            ExecutorService renderExecutor,
                            Set<Integer> loadingPages) {
        this.mainController = mainController;
        this.pdfService = pdfService;
        this.renderExecutor = renderExecutor;
        this.loadingPages = loadingPages;
    }

    // ==================== PUBLIC API ====================

    /**
     * Jump to specific page number (1-based)
     */
    public void jumpToPage(int pageNumber) {
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

        Platform.runLater(() -> {
            scrollToPage(pageIndex);
            mainController.updatePageInfo();
        });

        logger.info("Jumped to page {}", pageNumber);
    }

    /**
     * Navigate to page by index (0-based)
     */
    public void navigateToPage(int pageIndex) {
        PDFDocument currentDocument = mainController.getCurrentDocument();
        if (currentDocument == null) {
            return;
        }

        currentDocument.setCurrentPage(pageIndex);
        scrollToCurrentPage();
        mainController.updatePageInfo();
    }

    /**
     * Scroll to current page in document
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
                double currentY = 0;

                for (int i = 0; i < targetPage; i++) {
                    VBox pageBox = (VBox) pagesContainer.getChildren().get(i);
                    currentY += pageBox.getPrefHeight() + 10;
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
     * Scroll to specific page index with centering
     */
    public void scrollToPage(int pageIndex) {
        VBox pagesContainer = mainController.getPagesContainer();
        ScrollPane scrollPane = mainController.getScrollPane();

        if (pagesContainer == null || scrollPane == null) {
            logger.warn("Cannot scroll - container or scrollpane is null");
            return;
        }

        Platform.runLater(() -> {
            try {
                if (pageIndex < 0 || pageIndex >= pagesContainer.getChildren().size()) {
                    logger.warn("Invalid page index for scrolling: {}", pageIndex);
                    return;
                }

                pagesContainer.layout();

                VBox targetPageBox = (VBox) pagesContainer.getChildren().get(pageIndex);
                double targetY = targetPageBox.getBoundsInParent().getMinY();

                double contentHeight = pagesContainer.getHeight();
                double viewportHeight = scrollPane.getViewportBounds().getHeight();

                if (contentHeight > viewportHeight) {
                    double pageHeight = targetPageBox.getHeight();
                    double centerOffset = Math.max(0, (viewportHeight - pageHeight) / 2);
                    double adjustedY = Math.max(0, targetY - centerOffset);

                    double maxScroll = contentHeight - viewportHeight;
                    double scrollPosition = adjustedY / maxScroll;

                    scrollPane.setVvalue(Math.min(1.0, Math.max(0.0, scrollPosition)));

                    logger.debug("Scrolled to page {} at position {} (targetY={}, adjustedY={})",
                            pageIndex + 1, scrollPosition, targetY, adjustedY);
                }

            } catch (Exception e) {
                logger.error("Error scrolling to page {}", pageIndex + 1, e);
            }
        });
    }

    /**
     * Ensure page is loaded before executing callback
     */
    public void ensurePageLoaded(int pageIndex, Runnable callback) {
        VBox pagesContainer = mainController.getPagesContainer();
        if (pagesContainer == null || pageIndex < 0 || pageIndex >= pagesContainer.getChildren().size()) {
            logger.warn("Invalid page index or container: {}", pageIndex);
            return;
        }

        VBox pageBox = (VBox) pagesContainer.getChildren().get(pageIndex);

        if (isPageRendered(pageBox)) {
            if (callback != null) {
                callback.run();
            }
            logger.debug("Page {} already rendered", pageIndex + 1);
            return;
        }

        logger.info("Force loading page {} before navigation", pageIndex + 1);
        loadPageAndWait(pageIndex, pageBox, callback);
    }

    /**
     * Ensure page is fully loaded and layout is ready
     */
    public void ensurePageLoadedAndReady(int pageIndex, Runnable callback) {
        VBox pagesContainer = mainController.getPagesContainer();
        if (pagesContainer == null || pageIndex < 0 || pageIndex >= pagesContainer.getChildren().size()) {
            logger.warn("Invalid page index or container: {}", pageIndex);
            return;
        }

        VBox pageBox = (VBox) pagesContainer.getChildren().get(pageIndex);

        if (isPageRendered(pageBox)) {
            logger.debug("Page {} already rendered", pageIndex + 1);
            if (callback != null) {
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> {
                            pageBox.layout();

                            if (!pageBox.getChildren().isEmpty()
                                    && pageBox.getChildren().get(0) instanceof StackPane stackPane) {
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
     * Load page and wait for render completion
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
     * Check if page is already rendered (has ImageView)
     */
    public boolean isPageRendered(VBox pageBox) {
        if (pageBox == null || pageBox.getChildren().isEmpty()) {
            return false;
        }

        Node firstChild = pageBox.getChildren().get(0);

        if (firstChild instanceof StackPane stackPane) {
            if (!stackPane.getChildren().isEmpty()) {
                Node content = stackPane.getChildren().get(0);
                return content instanceof ImageView;
            }
        }

        return false;
    }

    /**
     * Update current page indicator from scroll position
     */
    public void updateCurrentPageFromScroll() {
        PDFDocument currentDocument = mainController.getCurrentDocument();
        VBox pagesContainer = mainController.getPagesContainer();
        ScrollPane scrollPane = mainController.getScrollPane();

        if (currentDocument == null || pagesContainer == null || scrollPane == null) {
            return;
        }

        try {
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double scrollValue = scrollPane.getVvalue();
            double contentHeight = pagesContainer.getHeight();

            if (contentHeight <= viewportHeight) {
                return;
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
                    currentDocument.setCurrentPage(i);
                    mainController.updatePageInfo();
                    break;
                }

                currentY = pageEnd + 10;
            }
        } catch (Exception e) {
            logger.error("Error updating current page from scroll", e);
        }
    }
}