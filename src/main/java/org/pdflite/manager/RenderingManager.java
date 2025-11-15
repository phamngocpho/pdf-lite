package org.pdflite.manager;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;
import org.pdflite.controller.PageRenderer;
import org.pdflite.controller.ScrollHandler;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.pdflite.view.AnnotationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.image.ImageView;

import java.io.IOException;

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
                pagesContainer.setStyle("-fx-background-color: #808080; -fx-padding: 10;");
                if (contentPane != null) {
                    contentPane.getChildren().add(pagesContainer);
                }
            }

            // Clear existing pages
            pagesContainer.getChildren().clear();

            int totalPages = currentDocument.getTotalPages();
            Image firstPage = pdfService.renderPage(currentDocument, 0, (float) zoomManager.getCurrentZoom());
            double pageWidth = firstPage.getWidth();
            double pageHeight = firstPage.getHeight();

            logger.info("Creating continuous scroll view for {} pages", totalPages);

            // Create placeholders for all pages
            for (int i = 0; i < totalPages; i++) {
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

        } catch (IOException e) {
            logger.error("Error rendering page", e);
            throw new RuntimeException("Could not render the page: " + e.getMessage(), e);
        }
    }

    /**
     * Preserves scroll position and applies zoom by updating existing rendered pages.
     */
    public void preserveScrollPositionAndApplyZoom(double newZoom) {
        if (currentDocument == null || pagesContainer == null || scrollPane == null) {
            return;
        }

        try {
            // Lưu lại vị trí cuộn theo pixel trước khi zoom
            javafx.geometry.Bounds viewportBounds = scrollPane.getViewportBounds();
            javafx.geometry.Bounds contentBounds = pagesContainer.getBoundsInLocal();
            double oldVValue = scrollPane.getVvalue();
            double oldCenterY = oldVValue * (contentBounds.getHeight() - viewportBounds.getHeight())
                    + viewportBounds.getHeight() / 2;

            // Cập nhật zoom level và render lại ảnh
            currentDocument.setZoomLevel(newZoom);
            pageRenderer.setZoom(newZoom);

            // Update zoom cho các trang đã được render
            pagesContainer.getChildren().forEach(node -> {
                if (node instanceof VBox box) {
                    String id = box.getId();
                    if (id != null && id.startsWith("page-")) {
                        int pageIndex = Integer.parseInt(id.replace("page-", ""));

                        // Chỉ update các trang đã được render (có ImageView trong StackPane)
                        if (!box.getChildren().isEmpty() &&
                            box.getChildren().getFirst() instanceof StackPane stackPane) {
                            if (!stackPane.getChildren().isEmpty() &&
                                stackPane.getChildren().get(0) instanceof javafx.scene.image.ImageView imgView) {

                                try {
                                    Image newImg = pdfService.renderPage(currentDocument, pageIndex, (float) newZoom);
                                    imgView.setImage(newImg);

                                    // Update annotation layer size if exists
                                    if (stackPane.getChildren().size() > 1 &&
                                        stackPane.getChildren().get(1) instanceof AnnotationLayer annotationLayer) {
                                        annotationLayer.setWidth(newImg.getWidth());
                                        annotationLayer.setHeight(newImg.getHeight());
                                        annotationLayer.redraw();
                                    }
                                } catch (IOException e) {
                                    logger.error("Error updating page zoom for page {}", pageIndex + 1, e);
                                }
                            }
                        }
                    }
                }
            });

            // Sau khi layout xong, khôi phục lại đúng vị trí cũ (theo pixel)
            Platform.runLater(() -> {
                pagesContainer.applyCss();
                pagesContainer.layout();

                javafx.geometry.Bounds newContentBounds = pagesContainer.getBoundsInLocal();
                if (newContentBounds.getHeight() > 0 && contentBounds.getHeight() > 0) {
                    double newCenterY = oldCenterY * newContentBounds.getHeight() / contentBounds.getHeight();
                    double newVValue = (newCenterY - viewportBounds.getHeight() / 2)
                            / (newContentBounds.getHeight() - viewportBounds.getHeight());
                    scrollPane.setVvalue(Math.max(0, Math.min(1, newVValue)));
                }
            });

        } catch (Exception e) {
            logger.error("Error preserving scroll position during zoom", e);
            // Fallback: render all pages if something goes wrong
            renderAllPages();
        }
    }

    // ... (Hàm preserveScrollPositionAndApplyZoom(...) của bạn) ...

    /**
     * Re-renders a single page, updating its ImageView and resizing UI components.
     */
    public void rerenderPage(int pageIndex) {
        if (pagesContainer == null || pageIndex < 0 || pageIndex >= pagesContainer.getChildren().size()) {
            logger.warn("Cannot rerender page: Invalid index or pagesContainer not set");
            return;
        }
        try {
            // Cấu trúc của bạn là VBox(Container) -> VBox(pageBox) -> StackPane
            VBox pageBox = (VBox) pagesContainer.getChildren().get(pageIndex);
            StackPane stackPane = (StackPane) pageBox.getChildren().getFirst();

            ImageView imageView = null;
            AnnotationLayer annotationLayer = null;
            for (javafx.scene.Node child : stackPane.getChildren()) {
                if (child instanceof ImageView) {
                    imageView = (ImageView) child;
                } else if (child instanceof AnnotationLayer) {
                    annotationLayer = (AnnotationLayer) child;
                }
            }
            if (imageView == null) {
                logger.warn("Cannot rerender: ImageView not found");
                return;
            }

            final ImageView finalImageView = imageView;
            final AnnotationLayer finalAnnotationLayer = annotationLayer;
            final StackPane finalStackPane = stackPane;

            double currentZoom = zoomManager.getCurrentZoom();
            pageRenderer.renderPageAsync(
                    pageIndex,
                    currentZoom,
                    (newImage) -> {
                        if (newImage == null) {
                            logger.error("Failed to get new rendered image for page {}", pageIndex + 1);
                            return;
                        }

                        finalImageView.setImage(newImage);
                        finalImageView.setFitWidth(newImage.getWidth());
                        finalImageView.setFitHeight(newImage.getHeight());

                        finalStackPane.setPrefSize(newImage.getWidth(), newImage.getHeight());
                        finalStackPane.setMinSize(newImage.getWidth(), newImage.getHeight());
                        finalStackPane.setMaxSize(newImage.getWidth(), newImage.getHeight());

                        if (finalAnnotationLayer != null) {
                            finalAnnotationLayer.setWidth(newImage.getWidth());
                            finalAnnotationLayer.setHeight(newImage.getHeight());
                            finalAnnotationLayer.redraw();
                        }
                        logger.info("Rerendered page {} successfully.", pageIndex + 1);
                    }
            );
        } catch (Exception e) {
            logger.error("Error rerendering page " + pageIndex, e);
        }
    }

// ... (Hàm getPagesContainer() của bạn) ...
    /**
     * Gets the pages container.
     *
     * @return the pages container
     */
    public VBox getPagesContainer() {
        return pagesContainer;
    }
}

