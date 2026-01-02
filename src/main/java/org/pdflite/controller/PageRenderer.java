package org.pdflite.controller;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.pdflite.manager.LanguageManager;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.pdflite.view.AnnotationLayer;
import org.pdflite.view.ContextMenuPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Handles page rendering, caching, and display management.
 */
public class PageRenderer {
    private static final Logger logger = LoggerFactory.getLogger(PageRenderer.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private final PDFService pdfService;
    private final ExecutorService renderExecutor;
    private final Map<String, Image> imageCache;
    private final Set<Integer> loadingPages;
    private final Map<Integer, Future<?>> pendingRenders;

    private PDFDocument currentDocument;
    private double currentZoom;
    private final ContextMenuHandler contextMenuHandler;
    private boolean isTextSelectionActive = true; // Default to true (like browsers)
    private org.pdflite.command.CommandManager commandManager;
    private java.util.function.Consumer<Integer> refreshAnnotationsCallback;
    private java.util.function.Supplier<javafx.scene.effect.Effect> readingModeEffectSupplier;

    /**
     * Creates a new PageRenderer with the specified service and executor.
     */
    public PageRenderer(PDFService pdfService, ExecutorService renderExecutor) {
        this.pdfService = pdfService;
        this.renderExecutor = renderExecutor;
        this.loadingPages = ConcurrentHashMap.newKeySet();
        this.pendingRenders = new ConcurrentHashMap<>();
        this.contextMenuHandler = new ContextMenuHandler();

        // LRU cache with max 100 pages for smoother scrolling
        this.imageCache = new java.util.LinkedHashMap<>(100, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                return size() > 100;
            }
        };

        logger.info("PageRenderer initialized with new cache");
    }

    /**
     * Sets the current document and zoom level.
     * Clears the image cache when switching to a new document.
     *
     * @param document the PDF document
     * @param zoom     the zoom level
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
        adjustCacheSizeForZoom(zoom);
        logger.info("PageRenderer: Document set with zoom {}", zoom);
    }

    /**
     * Sets the zoom level.
     * Adjusts cache size based on zoom to prevent memory issues at high zoom.
     */
    public void setZoom(double zoom) {
        this.currentZoom = zoom;
        adjustCacheSizeForZoom(zoom);
    }

    /**
     * Adjusts cache size based on the zoom level.
     * At high zoom levels, reduce cache size to prevent memory issues.
     */
    private void adjustCacheSizeForZoom(double zoom) {
        int maxCacheSize;
        if (zoom >= 1.5) {
            maxCacheSize = 40; // Very high zoom: smaller cache
        } else if (zoom >= 1.2) {
            maxCacheSize = 60; // High zoom: medium cache
        } else {
            maxCacheSize = 100; // Normal zoom: full cache
        }

        // Trim cache if it exceeds the new limit
        // Removing from cache allows JavaFX to garbage collect the Image
        while (imageCache.size() > maxCacheSize) {
            String oldestKey = imageCache.keySet().iterator().next();
            imageCache.remove(oldestKey);
            logger.debug("Removed image from cache to maintain size limit: {}", oldestKey);
        }
    }

    /**
     * Sets whether highlight mode is active.
     */
    public void setHighlightModeActive() {
    }

    /**
     * Sets the command manager for undo/redo support.
     *
     * @param commandManager the command manager
     */
    public void setCommandManager(org.pdflite.command.CommandManager commandManager) {
        this.commandManager = commandManager;
        logger.debug("CommandManager set in PageRenderer");
    }

    /**
     * Sets the callback for refreshing annotations on a page.
     *
     * @param callback consumer that takes page index and refreshes annotations
     */
    public void setRefreshAnnotationsCallback(java.util.function.Consumer<Integer> callback) {
        this.refreshAnnotationsCallback = callback;
    }

    /**
     * Sets the supplier for reading mode effect.
     *
     * @param supplier supplier that returns the current reading mode effect
     */
    public void setReadingModeEffectSupplier(java.util.function.Supplier<javafx.scene.effect.Effect> supplier) {
        this.readingModeEffectSupplier = supplier;
    }

    /**
     * Clears all cached images and resets the state.
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
     * FIXED: Returns true if the page is NOT being loaded.
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

        // Render in a background thread
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
                        Label errorLabel = new Label(lang().getString("navigation.errorLoading") + " " + (pageIndex + 1));
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
     * CRITICAL: Must-match format used everywhere.
     */
    private String getCacheKey(int pageIndex, double zoom) {
        // Use a simple format that's easy to debug
        return pageIndex + "_" + String.format("%.2f", zoom);
    }

    /**
     * Displays an image in the page box with an annotation layer.
     */
    private void displayImage(Image image, VBox pageBox, int pageIndex) {
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCache(true);

        // Apply reading mode effect if available
        if (readingModeEffectSupplier != null) {
            imageView.setEffect(readingModeEffectSupplier.get());
        }

        imageView.setPickOnBounds(false);
        imageView.setMouseTransparent(false);

        // Create an annotation layer
        AnnotationLayer annotationLayer = new AnnotationLayer(image.getWidth(), image.getHeight());
        annotationLayer.setPickOnBounds(false);

        annotationLayer.setScale(currentZoom);
        annotationLayer.setPageIndex(pageIndex);

        // Set command manager for undo/redo support
        if (commandManager != null) {
            annotationLayer.setCommandManager(commandManager);
        }

        if (currentDocument != null) {
            annotationLayer.setAnnotations(currentDocument.getAnnotationsForPage(pageIndex));
        }

        annotationLayer.setOnAnnotationAdded(newAnn -> {
            if (currentDocument != null) {
                // Use command pattern for undo/redo support
                if (commandManager != null) {
                    org.pdflite.command.AddAnnotationCommand cmd =
                            new org.pdflite.command.AddAnnotationCommand(
                                    currentDocument,
                                    newAnn,
                                    refreshAnnotationsCallback); // Use callback for refresh
                    commandManager.executeCommand(cmd);
                } else {
                    // Fallback: add directly if no command manager
                    currentDocument.addAnnotation(newAnn);
                }
                logger.info("Drawing completed on page {}", pageIndex);
            }
        });


        annotationLayer.setOnContextMenuRequested(null);
        annotationLayer.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            // Do nothing - let the context menu handle it
        });

        // Create context menu pane
        ContextMenuPane contextPane = new ContextMenuPane(contextMenuHandler);
        contextPane.setDocumentInfo(currentDocument, pageIndex, currentZoom);
        contextPane.setAnnotationLayer(annotationLayer); // Set reference for comment clicks
        contextPane.setPrefSize(image.getWidth(), image.getHeight());
        contextPane.setMaxSize(image.getWidth(), image.getHeight());

        // Set mouse transparency based on current text selection mode
        contextPane.setMouseTransparent(!isTextSelectionActive);

        contextPane.toFront();

        // Stack layers: Image (bottom) -> Annotation -> ContextMenu (top)
        StackPane imageStack = new StackPane(imageView, annotationLayer, contextPane);
        imageStack.setAlignment(Pos.CENTER);
        imageStack.setPickOnBounds(false);
        // Set exact size to match placeholder and prevent layout shift
        imageStack.setPrefSize(image.getWidth(), image.getHeight());
        imageStack.setMinSize(image.getWidth(), image.getHeight());
        imageStack.setMaxSize(image.getWidth(), image.getHeight());

        // Replace placeholder with a rendered image
        if (!pageBox.getChildren().isEmpty()) {
            pageBox.getChildren().set(0, imageStack);
        } else {
            pageBox.getChildren().add(imageStack);
        }

        logger.debug("Page {} displayed successfully", pageIndex + 1);
    }

    /**
     * Creates a placeholder VBox for a PDF page before it's loaded.
     * The placeholder has the exact same dimensions as the rendered page
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
        pageBox.setStyle("-fx-background-color: #606060; -fx-padding: 0;");

        // Create a simple gray placeholder with exact dimensions
        StackPane placeholder = new StackPane();
        placeholder.setPrefSize(width, height);
        placeholder.setMinSize(width, height);
        placeholder.setMaxSize(width, height);
        placeholder.setStyle("-fx-background-color: #606060; -fx-padding: 0;");

        pageBox.getChildren().add(placeholder);
        return pageBox;
    }

    /**
     * Unloads a page by replacing it with a placeholder.
     * This helps free memory when pages are far from the viewport at high zoom levels.
     *
     * @param pageIndex the index of the page to unload
     * @param pageBox   the page box containing the rendered page
     */
    public void unloadPage(int pageIndex, VBox pageBox) {
        if (pageBox == null || pageBox.getChildren().isEmpty()) {
            return;
        }

        // Only unload if it's actually rendered (has ImageView)
        Object firstChild = pageBox.getChildren().getFirst();
        if (!(firstChild instanceof StackPane stackPane)) {
            return;
        }

        boolean hasImageView = stackPane.getChildren().stream()
                .anyMatch(child -> child instanceof ImageView);
        if (!hasImageView) {
            return; // Already a placeholder
        }

        // Get page dimensions before unloading
        double width = pageBox.getPrefWidth();
        double height = pageBox.getPrefHeight();

        // Replace with placeholder
        StackPane placeholder = new StackPane();
        placeholder.setPrefSize(width, height);
        placeholder.setMinSize(width, height);
        placeholder.setMaxSize(width, height);
        placeholder.setStyle("-fx-background-color: #606060; -fx-padding: 0;");

        Platform.runLater(() -> {
            pageBox.getChildren().set(0, placeholder);
            logger.debug("Unloaded page {} to free memory", pageIndex + 1);
        });
    }

    public void setSelectionModeActive(VBox pagesContainer, boolean active) {
        isTextSelectionActive = active; // Store the state for new pages

        if (pagesContainer == null) return;

        // Duyệt qua tất cả các trang đang hiển thị
        pagesContainer.getChildren().forEach(node -> {
            if (node instanceof VBox pageBox && !pageBox.getChildren().isEmpty()) {
                // Lấy StackPane (chứa Ảnh, Annotation, ContextMenu)
                if (pageBox.getChildren().getFirst() instanceof StackPane stack) {
                    stack.getChildren().forEach(layer -> {

                        // Xử lý lớp chọn Text (ContextMenuPane)
                        if (layer instanceof ContextMenuPane) {
                            // Nếu active = true -> MouseTransparent = false (Bắt chuột)
                            // Nếu active = false -> MouseTransparent = true (Cho xuyên qua)
                            layer.setMouseTransparent(!active);
                        }

                        // Xử lý lớp vẽ (AnnotationLayer)
                        // Khi đang chọn Text thì không được vẽ (cho chuột xuyên qua lớp vẽ luôn cho chắc)
                        if (layer instanceof AnnotationLayer) {
                            layer.setMouseTransparent(active);
                        }
                    });
                }
            }
        });
    }

    /**
     * Gets the context menu handler for setting callbacks.
     *
     * @return the context menu handler
     */
    public ContextMenuHandler getContextMenuHandler() {
        return contextMenuHandler;
    }
}