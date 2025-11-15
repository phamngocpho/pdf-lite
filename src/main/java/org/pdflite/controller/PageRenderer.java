package org.pdflite.controller;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.pdflite.manager.DrawingManager;
import org.pdflite.model.DrawingTool;
// -------------
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.pdflite.view.AnnotationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Consumer;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.pdflite.view.ContextMenuPane;

/**
 * Handles page rendering, caching, and display management.
 * (Javadoc gốc của bạn)
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

    // [THÊM BIẾN NÀY]
    private final DrawingManager drawingManager;

    /**
     * Creates a new PageRenderer with the specified service and executor.
     *
     * @param pdfService the PDF service for rendering pages
     * @param renderExecutor the executor service for parallel rendering
     * @param drawingManager The application's drawing manager (mới)
     */
    public PageRenderer(PDFService pdfService, ExecutorService renderExecutor, DrawingManager drawingManager) { // <-- SỬA CONSTRUCTOR
        this.pdfService = pdfService;
        this.renderExecutor = renderExecutor;
        this.drawingManager = drawingManager; // <-- THÊM DÒNG NÀY
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
     */
    public void setDocument(PDFDocument document, double zoom) {
        this.currentDocument = document;
        this.currentZoom = zoom;
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
     */
    public boolean isPageLoading(int pageIndex) {
        return !loadingPages.contains(pageIndex);
    }

    /**
     * Loads and renders a single page in the background.
     */
    public void loadPage(int pageIndex, VBox pageBox) {
        if (currentDocument == null) {
            return;
        }
        loadingPages.add(pageIndex);
        String cacheKey = getCacheKey(pageIndex, currentZoom);
        Image cachedImage = imageCache.get(cacheKey);

        if (cachedImage != null) {
            Platform.runLater(() -> {
                displayImage(cachedImage, pageBox, pageIndex);
                loadingPages.remove(pageIndex);
            });
            return;
        }

        Future<?> future = renderExecutor.submit(() -> {
            try {
                if (!Thread.currentThread().isInterrupted()) {
                    Image image = pdfService.renderPage(
                            currentDocument,
                            pageIndex,
                            (float) currentZoom
                    );
                    imageCache.put(cacheKey, image);
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
        pendingRenders.put(pageIndex, future);
    }

    /**
     * Asynchronously renders a single page and executes a callback on completion.
     * (Javadoc của bạn)
     */
    public void renderPageAsync(int pageIndex, double zoom, Consumer<Image> callback) {
        if (currentDocument == null) {
            return;
        }
        renderExecutor.submit(() -> {
            try {
                Image image = pdfService.renderPage(
                        currentDocument,
                        pageIndex,
                        (float) zoom
                );
                Platform.runLater(() -> callback.accept(image));
            } catch (IOException e) {
                logger.error("Error async rendering page {}", pageIndex + 1, e);
                Platform.runLater(() -> callback.accept(null));
            }
        });
    }

    /**
     * Generates a cache key for a page image.
     */
    private String getCacheKey(int pageIndex, double zoom) {
        return String.format("page_%d_zoom_%.2f", pageIndex, zoom);
    }

    /**
     * Displays an image in the page box with annotation layer.
     * [MODIFIED] This method is updated to correctly set the annotation mode
     * on new layers based on the global DrawingManager state.
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

        // Create annotation layer on top of the image
        AnnotationLayer annotationLayer = new AnnotationLayer(image.getWidth(), image.getHeight());
        annotationLayer.setPickOnBounds(false);

        // [SỬA LỖI] Đặt trạng thái chính xác cho layer MỚI
        annotationLayer.setDrawingManager(drawingManager);
        annotationLayer.setPageNumber(pageIndex);

        if (highlightModeActive) {
            // Người dùng đang ở chế độ highlight
            annotationLayer.setAnnotationMode(AnnotationLayer.AnnotationMode.HIGHLIGHT);
        } else if (drawingManager.getCurrentTool() != DrawingTool.NONE) {
            // Người dùng đang ở chế độ vẽ hình
            annotationLayer.setAnnotationMode(AnnotationLayer.AnnotationMode.SHAPE);
        } else {
            // Không ở chế độ nào
            annotationLayer.setAnnotationMode(AnnotationLayer.AnnotationMode.NONE);
        }
        // [HẾT SỬA LỖI]

        ContextMenuPane contextPane = new ContextMenuPane(contextMenuHandler);
        contextPane.setDocumentInfo(currentDocument, pageIndex, currentZoom);
        contextPane.setPrefSize(image.getWidth(), image.getHeight());
        contextPane.setMaxSize(image.getWidth(), image.getHeight());

        // Stack layers: Image (bottom) -> Annotation -> ContextMenu (top)
        StackPane imageStack = new StackPane(imageView, annotationLayer, contextPane);
        imageStack.setAlignment(Pos.CENTER);

        // Replace placeholder with actual image
        if (!pageBox.getChildren().isEmpty()) {
            pageBox.getChildren().set(0, imageStack);
        }

        logger.debug("Page {} displayed with context menu support (layers: Image -> Annotation -> Context)",
                pageIndex + 1);
    }

    /**
     * Creates a placeholder VBox for a PDF page before it's loaded.
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
     */
    private StackPane createLoadingPlaceholder(double width, double height) {
        Label loadingLabel = new Label("Loading...");
        loadingLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 14px;");
        StackPane placeholder = new StackPane(loadingLabel);
        placeholder.setPrefSize(width, height);
        placeholder.setStyle("-fx-background-color: #505050;");
        return placeholder;
    }
}