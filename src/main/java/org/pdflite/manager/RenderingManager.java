package org.pdflite.manager;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;
import org.pdflite.controller.PageRenderer;
import org.pdflite.controller.ScrollHandler;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages PDF page rendering operations including initial rendering
 * and zoom-preserving re-rendering.
 */
public class RenderingManager {
    private static final Logger logger = LoggerFactory.getLogger(RenderingManager.class);

    private final PDFService pdfService;
    private final PageRenderer pageRenderer;
    private final ScrollHandler scrollHandler;
    private final ZoomManager zoomManager;

    private PDFDocument currentDocument;
    private VBox pagesContainer;
    private ScrollPane scrollPane;
    private Pane contentPane;

    /**
     * Creates a new RenderingManager.
     *
     * @param pdfService the PDF service for rendering pages
     * @param pageRenderer the page renderer
     * @param scrollHandler the scroll handler
     * @param zoomManager the zoom manager
     */
    public RenderingManager(PDFService pdfService, PageRenderer pageRenderer,
                            ScrollHandler scrollHandler, ZoomManager zoomManager) {
        this.pdfService = pdfService;
        this.pageRenderer = pageRenderer;
        this.scrollHandler = scrollHandler;
        this.zoomManager = zoomManager;
    }

    /**
     * Sets the UI components.
     *
     * @param pagesContainer the container for pages
     * @param scrollPane the scroll pane
     * @param contentPane the content pane
     */
    public void setUIComponents(VBox pagesContainer, ScrollPane scrollPane, Pane contentPane) {
        this.pagesContainer = pagesContainer;
        this.scrollPane = scrollPane;
        this.contentPane = contentPane;
    }

    /**
     * Sets the current document.
     *
     * @param document the PDF document
     */
    public void setDocument(PDFDocument document) {
        this.currentDocument = document;
    }

    /**
     * Renders all pages of the current document in continuous scroll mode.
     */
    public void renderAllPages() {
        if (currentDocument == null) {
            return;
        }

        try {
            if (pagesContainer == null) {
                pagesContainer = new VBox(10);
                pagesContainer.setAlignment(Pos.TOP_CENTER);
                pagesContainer.setStyle("-fx-background-color: #808080; -fx-padding: 20;");
                if (contentPane != null) {
                    contentPane.getChildren().add(pagesContainer);
                }
            }

            // Clear existing pages
            pagesContainer.getChildren().clear();

            int totalPages = currentDocument.getTotalPages();
            double currentZoom = zoomManager.getCurrentZoom();
            
            logger.info("Creating continuous scroll view for {} pages", totalPages);

            // Create placeholders for all pages - calculate individual page dimensions efficiently
            for (int i = 0; i < totalPages; i++) {
                // Get page dimensions without rendering (much faster)
                double[] dimensions = pdfService.getPageDimensions(currentDocument, i, (float) currentZoom);
                double pageWidth = dimensions[0];
                double pageHeight = dimensions[1];
                
                VBox pageBox = pageRenderer.createPagePlaceholder(i, pageWidth, pageHeight);
                pagesContainer.getChildren().add(pageBox);
            }

            // Update scroll handler with document and container
            if (scrollHandler != null) {
                scrollHandler.setDocument(currentDocument, pagesContainer);
            }

            // Load first few visible pages immediately
            Platform.runLater(() -> {
                if (scrollHandler != null) {
                    scrollHandler.handleScroll();
                }
            });

        } catch (Exception e) {
            logger.error("Error rendering page", e);
            throw new RuntimeException("Could not render the page: " + e.getMessage(), e);
        }
    }

    /**
     * Preserves scroll position and applies zoom by updating page dimensions only.
     * Pages will be re-rendered on-demand when they become visible.
     */
    public void preserveScrollPositionAndApplyZoom(double newZoom) {
        if (currentDocument == null || pagesContainer == null || scrollPane == null) {
            return;
        }

        try {
            // Lưu vị trí scroll trước khi zoom
            javafx.geometry.Bounds viewportBounds = scrollPane.getViewportBounds();
            javafx.geometry.Bounds contentBounds = pagesContainer.getBoundsInLocal();
            double oldVValue = scrollPane.getVvalue();
            double oldContentHeight = contentBounds.getHeight();
            
            // CRITICAL: Clear cache trước để không dùng image cũ
            pageRenderer.clearCache();
            pageRenderer.cancelAllPendingRenders();
            
            // Cập nhật zoom level
            currentDocument.setZoomLevel(newZoom);
            pageRenderer.setZoom(newZoom);
            
            // Update ONLY dimensions của tất cả pages, KHÔNG render
            for (int i = 0; i < pagesContainer.getChildren().size(); i++) {
                javafx.scene.Node node = pagesContainer.getChildren().get(i);
                if (node instanceof VBox box) {
                    String id = box.getId();
                    if (id != null && id.startsWith("page-")) {
                        int pageIndex = Integer.parseInt(id.replace("page-", ""));
                        
                        // Get new dimensions
                        double[] dimensions = pdfService.getPageDimensions(currentDocument, pageIndex, (float) newZoom);
                        double newWidth = dimensions[0];
                        double newHeight = dimensions[1];
                        
                        // Update VBox size
                        box.setPrefSize(newWidth, newHeight);
                        box.setMinSize(newWidth, newHeight);
                        box.setMaxSize(newWidth, newHeight);
                        
                        // Clear nội dung cũ và tạo placeholder mới
                        box.getChildren().clear();
                        javafx.scene.layout.StackPane placeholder = new javafx.scene.layout.StackPane();
                        placeholder.setPrefSize(newWidth, newHeight);
                        placeholder.setMinSize(newWidth, newHeight);
                        placeholder.setMaxSize(newWidth, newHeight);
                        placeholder.setStyle("-fx-background-color: #606060; -fx-padding: 0;");
                        box.getChildren().add(placeholder);
                    }
                }
            }
            
            // Khôi phục vị trí scroll (tỷ lệ tương đối)
            Platform.runLater(() -> {
                pagesContainer.applyCss();
                pagesContainer.layout();
                
                javafx.geometry.Bounds newContentBounds = pagesContainer.getBoundsInLocal();
                double newContentHeight = newContentBounds.getHeight();
                
                if (newContentHeight > 0 && oldContentHeight > 0) {
                    // Giữ nguyên tỷ lệ scroll
                    double oldScrollY = oldVValue * (oldContentHeight - viewportBounds.getHeight());
                    double newScrollY = oldScrollY * (newContentHeight / oldContentHeight);
                    double newVValue = newScrollY / (newContentHeight - viewportBounds.getHeight());
                    scrollPane.setVvalue(Math.max(0, Math.min(1, newVValue)));
                }
                
                // Trigger lazy loading cho các trang visible
                if (scrollHandler != null) {
                    scrollHandler.handleScroll();
                }
            });

        } catch (Exception e) {
            logger.error("Error preserving scroll position during zoom", e);
        }
    }

    /**
     * Gets the pages container.
     *
     * @return the pages container
     */
    public VBox getPagesContainer() {
        return pagesContainer;
    }
}

