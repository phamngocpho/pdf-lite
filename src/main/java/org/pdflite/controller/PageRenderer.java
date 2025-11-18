package org.pdflite.controller;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.pdflite.view.AnnotationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.pdflite.view.ContextMenuPane;

/**
 * Handles page rendering, caching, and display management.
 */
public class PageRenderer {
    private static final Logger logger = LoggerFactory.getLogger(PageRenderer.class);

    private final PDFService pdfService;
    private final ExecutorService renderExecutor;
    private final Map<String, Image> imageCache;
    private final Set<Integer> loadingPages;
    private final Map<Integer, Future<?>> pendingRenders;

    private PDFDocument currentDocument;
    private double currentZoom;
    private boolean highlightModeActive;

    private ContextMenuHandler contextMenuHandler;

    /**
     * Creates a new PageRenderer with the specified service and executor.
     */
    public PageRenderer(PDFService pdfService, ExecutorService renderExecutor) {
        this.pdfService = pdfService;
        this.renderExecutor = renderExecutor;
        this.loadingPages = ConcurrentHashMap.newKeySet();
        this.pendingRenders = new ConcurrentHashMap<>();
        this.contextMenuHandler = new ContextMenuHandler();
        
        // LRU cache with max 50 pages
        this.imageCache = new java.util.LinkedHashMap<>(50, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                return size() > 50;
            }
        };
        
        logger.info("PageRenderer initialized with new cache");
    }

    /**
     * Sets the current document and zoom level.
     * Clears the image cache when switching to a new document.
     *
     * @param document the PDF document
     * @param zoom the zoom level
     */
    public void setDocument(PDFDocument document, double zoom) {
        // Clear cache when switching documents to prevent showing old document's pages
        if (this.currentDocument != document) {
            clearCache();
            cancelAllPendingRenders();
            logger.info("Cleared cache and cancelled pending renders for new document");
        }
        this.currentDocument = document;
        this.currentZoom = zoom;
        logger.info("PageRenderer: Document set with zoom {}", zoom);
    }

    /**
     * Sets the zoom level.
     */
    public void setZoom(double zoom) {
        this.currentZoom = zoom;
    }

    /**
     * Sets whether highlight mode is active.
     */
    public void setHighlightModeActive(boolean active) {
        this.highlightModeActive = active;
    }

    /**
     * Clears all cached images and resets state.
     * CRITICAL: This clears PageRenderer's cache completely.
     */
    public void clearCache() {
        imageCache.clear();
        loadingPages.clear();
        logger.info("PageRenderer cache cleared. Size: {}", imageCache.size());
    }

    /**
     * Cancels all pending render tasks.
     */
    public void cancelAllPendingRenders() {
        pendingRenders.forEach((pageIndex, future) -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
        pendingRenders.clear();
        loadingPages.clear();
        logger.info("All pending renders cancelled");
    }

    /**
     * Cancels pending render tasks for pages outside the specified range.
     */
    public void cancelOutOfRangePendingRenders(java.util.Set<Integer> pagesInRange) {
        pendingRenders.forEach((pageIndex, future) -> {
            if (!pagesInRange.contains(pageIndex) && !future.isDone()) {
                future.cancel(true);
                pendingRenders.remove(pageIndex);
                loadingPages.remove(pageIndex);
                logger.debug("Cancelled render for page {} (out of range)", pageIndex + 1);
            }
        });
    }

    /**
     * Checks if a page can be loaded (not currently loading).
     * FIXED: Returns true if page is NOT being loaded.
     */
    public boolean isPageLoading(int pageIndex) {
        return !loadingPages.contains(pageIndex);
    }

    /**
     * Loads and renders a single page in the background.
     */
    public void loadPage(int pageIndex, VBox pageBox) {
        if (currentDocument == null) {
            logger.warn("Cannot load page {}: currentDocument is null", pageIndex);
            return;
        }

        // Prevent duplicate requests
        if (loadingPages.contains(pageIndex)) {
            logger.debug("Page {} already loading, skipping", pageIndex + 1);
            return;
        }

        // Mark as loading
        loadingPages.add(pageIndex);
        logger.debug("Starting to load page {}", pageIndex + 1);

        // Check cache first - use CONSISTENT cache key format
        String cacheKey = getCacheKey(pageIndex, currentZoom);
        Image cachedImage = imageCache.get(cacheKey);

        if (cachedImage != null) {
            logger.debug("Using cached image for page {} (key: {})", pageIndex + 1, cacheKey);
            Platform.runLater(() -> {
                displayImage(cachedImage, pageBox, pageIndex);
                loadingPages.remove(pageIndex);
            });
            return;
        }

        // Render in background thread
        Future<?> future = renderExecutor.submit(() -> {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    logger.debug("Render interrupted for page {}", pageIndex + 1);
                    return;
                }

                // Render the page
                Image image = pdfService.renderPage(
                        currentDocument,
                        pageIndex,
                        (float) currentZoom
                );

                // Cache with CONSISTENT key format
                imageCache.put(cacheKey, image);
                logger.debug("Rendered and cached page {} (key: {})", pageIndex + 1, cacheKey);

                // Update UI on JavaFX thread
                Platform.runLater(() -> {
                    if (!Thread.currentThread().isInterrupted()) {
                        displayImage(image, pageBox, pageIndex);
                        loadingPages.remove(pageIndex);
                        logger.debug("Displayed page {}", pageIndex + 1);
                    }
                });

            } catch (IOException e) {
                logger.error("Error loading page {}", pageIndex + 1, e);
                loadingPages.remove(pageIndex);
                
                // Show error in UI
                Platform.runLater(() -> {
                    if (!pageBox.getChildren().isEmpty() &&
                        pageBox.getChildren().getFirst() instanceof StackPane stackPane) {
                        Label errorLabel = new Label("Error loading page " + (pageIndex + 1));
                        errorLabel.setStyle("-fx-text-fill: red;");
                        stackPane.getChildren().clear();
                        stackPane.getChildren().add(errorLabel);
                    }
                });
            } catch (Exception e) {
                logger.error("Unexpected error loading page {}", pageIndex + 1, e);
                loadingPages.remove(pageIndex);
            } finally {
                pendingRenders.remove(pageIndex);
            }
        });

        // Track the future for potential cancellation
        pendingRenders.put(pageIndex, future);
    }

    /**
     * Generates a CONSISTENT cache key for a page image.
     * CRITICAL: Must match format used everywhere.
     */
    private String getCacheKey(int pageIndex, double zoom) {
        // Use simple format that's easy to debug
        return pageIndex + "_" + String.format("%.2f", zoom);
    }

    /**
     * Displays an image in the page box with annotation layer.
     */
    private void displayImage(Image image, VBox pageBox, int pageIndex) {
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCache(true);
        
        imageView.setPickOnBounds(false);
        imageView.setMouseTransparent(false);

        // Create annotation layer
        AnnotationLayer annotationLayer = new AnnotationLayer(image.getWidth(), image.getHeight());
        annotationLayer.setPickOnBounds(false);
        annotationLayer.setOnContextMenuRequested(null);
        annotationLayer.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                // Do nothing - let context menu handle it
            }
        });

        // Create context menu pane
        ContextMenuPane contextPane = new ContextMenuPane(contextMenuHandler);
        contextPane.setDocumentInfo(currentDocument, pageIndex, currentZoom);
        contextPane.setPrefSize(image.getWidth(), image.getHeight());
        contextPane.setMaxSize(image.getWidth(), image.getHeight());
        
        contextPane.toFront();

        // Stack layers: Image (bottom) -> Annotation -> ContextMenu (top)
        StackPane imageStack = new StackPane(imageView, annotationLayer, contextPane);
        imageStack.setAlignment(Pos.CENTER);
        imageStack.setPickOnBounds(false);
        // Set exact size to match placeholder and prevent layout shift
        imageStack.setPrefSize(image.getWidth(), image.getHeight());
        imageStack.setMinSize(image.getWidth(), image.getHeight());
        imageStack.setMaxSize(image.getWidth(), image.getHeight());

        // Replace placeholder with rendered image
        if (!pageBox.getChildren().isEmpty()) {
            pageBox.getChildren().set(0, imageStack);
        } else {
            pageBox.getChildren().add(imageStack);
        }

        logger.debug("Page {} displayed successfully", pageIndex + 1);
    }

    /**
     * Creates a placeholder VBox for a PDF page before it's loaded.
     * The placeholder has the exact same dimensions as the rendered page,
     * but displays only a gray background to prevent layout shifts during loading.
     */
    public VBox createPagePlaceholder(int pageIndex, double width, double height) {
        VBox pageBox = new VBox();
        pageBox.setAlignment(Pos.CENTER);
        pageBox.setId("page-" + pageIndex);
        // Set exact size to match rendered page (no extra spacing)
        pageBox.setPrefSize(width, height);
        pageBox.setMinSize(width, height);
        pageBox.setMaxSize(width, height);
        pageBox.setStyle("-fx-background-color: #505050;");

        // Create a simple gray placeholder with exact dimensions
        StackPane placeholder = new StackPane();
        placeholder.setPrefSize(width, height);
        placeholder.setMinSize(width, height);
        placeholder.setMaxSize(width, height);
        placeholder.setStyle("-fx-background-color: #505050;");

        pageBox.getChildren().add(placeholder);
        return pageBox;
    }
}