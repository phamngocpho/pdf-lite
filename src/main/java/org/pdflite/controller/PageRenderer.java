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
 * <p>
 * This class is responsible for rendering PDF pages asynchronously, managing
 * an LRU cache for rendered images, and updating the UI with rendered pages.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
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
     *
     * @param pdfService the PDF service for rendering pages
     * @param renderExecutor the executor service for parallel rendering
     */
    public PageRenderer(PDFService pdfService, ExecutorService renderExecutor) {
        this.pdfService = pdfService;
        this.renderExecutor = renderExecutor;
        this.loadingPages = ConcurrentHashMap.newKeySet();
        this.pendingRenders = new ConcurrentHashMap<>();
        this.contextMenuHandler = new ContextMenuHandler();
        logger.info("ContextMenuHandler integrated into PageRenderer");
        
        // LRU cache with max 50 pages
        this.imageCache = new java.util.LinkedHashMap<>(50, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                return size() > 50;
            }
        };
    }

    /**
     * Sets the current document and zoom level.
     *
     * @param document the PDF document
     * @param zoom the zoom level
     */
    public void setDocument(PDFDocument document, double zoom) {
        this.currentDocument = document;
        this.currentZoom = zoom;
    }

    /**
     * Sets the zoom level.
     *
     * @param zoom the zoom level
     */
    public void setZoom(double zoom) {
        this.currentZoom = zoom;
    }

    /**
     * Sets whether highlight mode is active.
     *
     * @param active true if highlight mode is active
     */
    public void setHighlightModeActive(boolean active) {
        this.highlightModeActive = active;
    }

    /**
     * Clears all cached images.
     */
    public void clearCache() {
        imageCache.clear();
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
    }

    /**
     * Cancels pending render tasks for pages outside the specified range.
     *
     * @param pagesInRange set of page indices that should be kept
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
     * Checks if a page is currently loading.
     *
     * @param pageIndex the page index
     * @return true if the page is loading
     */
    public boolean isPageLoading(int pageIndex) {
        return !loadingPages.contains(pageIndex);
    }

    /**
     * Loads and renders a single page in the background.
     *
     * @param pageIndex the zero-based page index to load
     * @param pageBox the VBox container that will hold the rendered page
     */
    public void loadPage(int pageIndex, VBox pageBox) {
        if (currentDocument == null) {
            return;
        }

        // Mark as loading to prevent duplicate requests
        loadingPages.add(pageIndex);

        // Check cache first
        String cacheKey = getCacheKey(pageIndex, currentZoom);
        Image cachedImage = imageCache.get(cacheKey);
        
        if (cachedImage != null) {
            // Use cached image immediately
            Platform.runLater(() -> {
                displayImage(cachedImage, pageBox, pageIndex);
                loadingPages.remove(pageIndex);
            });
            return;
        }

        // Render in background thread to avoid blocking UI
        Future<?> future = renderExecutor.submit(() -> {
            try {
                if (!Thread.currentThread().isInterrupted()) {
                    Image image = pdfService.renderPage(
                        currentDocument,
                        pageIndex,
                        (float) currentZoom
                    );

                    // Cache the rendered image
                    imageCache.put(cacheKey, image);

                    // Update UI on JavaFX thread
                    Platform.runLater(() -> {
                        if (!Thread.currentThread().isInterrupted()) {
                            displayImage(image, pageBox, pageIndex);
                            loadingPages.remove(pageIndex);
                            logger.debug("Loaded page {}", pageIndex + 1);
                        }
                    });
                }

            } catch (IOException e) {
                logger.error("Error loading page {}", pageIndex + 1, e);
                loadingPages.remove(pageIndex);
                // Keep placeholder with error message
                Platform.runLater(() -> {
                    if (!pageBox.getChildren().isEmpty() &&
                        pageBox.getChildren().getFirst() instanceof StackPane) {
                        Label errorLabel = new Label("Error loading page");
                        errorLabel.setStyle("-fx-text-fill: red;");
                        ((StackPane) pageBox.getChildren().getFirst()).getChildren().set(0, errorLabel);
                    }
                });
            } finally {
                pendingRenders.remove(pageIndex);
            }
        });

        // Track the future for potential cancellation
        pendingRenders.put(pageIndex, future);
    }

    /**
     * Generates a cache key for a page image.
     *
     * @param pageIndex the page index
     * @param zoom the zoom level
     * @return cache key string
     */
    private String getCacheKey(int pageIndex, double zoom) {
        return String.format("page_%d_zoom_%.2f", pageIndex, zoom);
    }

    /**
     * Displays an image in the page box with annotation layer.
     *
     * @param image the image to display
     * @param pageBox the container for the page
     * @param pageIndex the page index
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
            // return nothing
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

    if (!pageBox.getChildren().isEmpty()) {
        pageBox.getChildren().set(0, imageStack);
    }

    logger.debug("Page {} displayed - Context menu layer active", pageIndex + 1);
}

    /**
     * Creates a placeholder VBox for a PDF page before it's loaded.
     *
     * @param pageIndex the zero-based page index
     * @param width the expected page width in pixels
     * @param height the expected page height in pixels
     * @return a VBox placeholder for the page
     */
    public VBox createPagePlaceholder(int pageIndex, double width, double height) {
        VBox pageBox = new VBox(5);
        pageBox.setAlignment(Pos.TOP_CENTER);
        pageBox.setId("page-" + pageIndex);
        pageBox.setPrefSize(width, height + 20);
        pageBox.setStyle("-fx-background-color: #606060; -fx-border-color: #404040;");

        Label pageNumberLabel = new Label("Page " + (pageIndex + 1));
        pageNumberLabel.setStyle("-fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 5;");

        StackPane placeholder = createLoadingPlaceholder(width, height);

        pageBox.getChildren().addAll(placeholder, pageNumberLabel);
        return pageBox;
    }

    /**
     * Creates a loading placeholder stack pane with "Loading..." text.
     *
     * @param width the width of the placeholder in pixels
     * @param height the height of the placeholder in pixels
     * @return a StackPane with a centered loading label
     */
    private StackPane createLoadingPlaceholder(double width, double height) {
        Label loadingLabel = new Label("Loading...");
        loadingLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 14px;");

        StackPane placeholder = new StackPane(loadingLabel);
        placeholder.setPrefSize(width, height);
        placeholder.setStyle("-fx-background-color: #505050;");

        return placeholder;
    }
   
public void resetForDocumentChange() {
    clearCache();
    cancelAllPendingRenders();
    loadingPages.clear();
}
}