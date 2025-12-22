package org.pdflite.manager;

import org.pdflite.controller.PageRenderer;
import org.pdflite.controller.ScrollHandler;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.pdflite.util.PageContainerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

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
    // Two-page mode flag
    private boolean twoPageMode = false;

    // Zoom operations can be triggered rapidly; use a sequence guard so only the latest
    // request applies its scroll restoration.
    private long zoomSequence = 0;

    /**
     * Creates a new RenderingManager.
     *
     * @param pdfService    the PDF service for rendering pages
     * @param pageRenderer  the page renderer
     * @param scrollHandler the scroll handler
     * @param zoomManager   the zoom manager
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
     * @param scrollPane     the scroll pane
     * @param contentPane    the content pane
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
                // default to single-page layout
                pagesContainer.getProperties().put("twoPageMode", false);
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

            // Load the first few visible pages immediately
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
            final long zoomRequestId = ++zoomSequence;

            // Ensure layout is up-to-date before capturing scroll/anchor.
            scrollPane.applyCss();
            scrollPane.layout();
            pagesContainer.applyCss();
            pagesContainer.layout();

            Node scrollContent = scrollPane.getContent();
            if (scrollContent == null) {
                return;
            }
            scrollContent.applyCss();
            if (scrollContent instanceof javafx.scene.Parent parent) {
                parent.layout();
            }

            // Capture an anchor point (viewport center) within the current page so zoom keeps the same spot visible.
            int totalPages = currentDocument.getTotalPages();
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double oldContentHeight = scrollContent.getBoundsInLocal().getHeight();
            double oldVValue = scrollPane.getVvalue();
            double oldScrollY = (oldContentHeight > viewportHeight)
                    ? oldVValue * (oldContentHeight - viewportHeight)
                    : 0;
            double oldAnchorYInContent = oldScrollY + (viewportHeight / 2.0);

            Bounds pagesBoundsInContent = pagesContainer.getBoundsInParent();
            double oldAnchorYInPagesContainer = oldAnchorYInContent - pagesBoundsInContent.getMinY();

            AnchorInfo anchorInfo = findAnchorInfoFromLayout(oldAnchorYInPagesContainer, totalPages);
            int anchorPageIndex = anchorInfo.pageIndex;
            double relativeOffsetInPage = anchorInfo.relativeOffset;

            if (anchorPageIndex < 0) {
                anchorPageIndex = Math.max(0, Math.min(totalPages - 1, currentDocument.getCurrentPage()));
                relativeOffsetInPage = 0.5;
            }

            final int anchorPageIndexFinal = anchorPageIndex;
            final double relativeOffsetInPageFinal = relativeOffsetInPage;
            // totalPages is captured above; no need to capture again.

            // Switch layout mode based on the threshold (70% => 0.7)
            boolean shouldTwoPage = newZoom < 0.7;
            if (shouldTwoPage != this.twoPageMode) {
                applyTwoPageModeLayout(shouldTwoPage);
            }

            // CRITICAL: Clear cache trước để không dùng image cũ
            pageRenderer.clearCache();
            pageRenderer.cancelAllPendingRenders();

            // Cập nhật zoom level
            currentDocument.setZoomLevel(newZoom);
            pageRenderer.setZoom(newZoom);

            // Update ONLY dimensions của tất cả pages, KHÔNG render
            java.util.List<javafx.scene.layout.VBox> pageBoxes = PageContainerUtils.collectPageBoxes(pagesContainer);

            for (javafx.scene.layout.VBox box : pageBoxes) {
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

            // Khôi phục vị trí scroll (tỷ lệ tương đối)
            Platform.runLater(() -> {
                if (zoomRequestId != zoomSequence) {
                    // A newer zoom request has superseded this one.
                    return;
                }

                scrollPane.applyCss();
                scrollPane.layout();
                pagesContainer.applyCss();
                pagesContainer.layout();

                Node newScrollContent = scrollPane.getContent();
                if (newScrollContent == null) {
                    return;
                }
                newScrollContent.applyCss();
                if (newScrollContent instanceof javafx.scene.Parent parent) {
                    parent.layout();
                }

                double newViewportHeight = scrollPane.getViewportBounds().getHeight();
                double newContentHeight = newScrollContent.getBoundsInLocal().getHeight();

                Bounds newPagesBoundsInContent = pagesContainer.getBoundsInParent();

                Bounds pageBoundsInPages = getPageBoundsInPagesContainer(anchorPageIndexFinal);
                if (pageBoundsInPages == null || pageBoundsInPages.getHeight() <= 0) {
                    return;
                }

                double newAnchorYInPages = pageBoundsInPages.getMinY()
                        + (relativeOffsetInPageFinal * pageBoundsInPages.getHeight());
                double newAnchorYInContent = newPagesBoundsInContent.getMinY() + newAnchorYInPages;

                double newScrollY = newAnchorYInContent - (newViewportHeight / 2.0);
                double newVValue;
                if (newContentHeight <= newViewportHeight) {
                    newVValue = 0;
                } else {
                    newVValue = newScrollY / (newContentHeight - newViewportHeight);
                }
                scrollPane.setVvalue(clamp(newVValue, 0.0, 1.0));

                // Trigger lazy loading for visible pages
                if (scrollHandler != null) {
                    scrollHandler.handleScroll();
                }
            });

        } catch (NumberFormatException e) {
            logger.error("Invalid page placeholder id format during zoom", e);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid page index during zoom", e);
        } catch (RuntimeException e) {
            logger.error("Error preserving scroll position during zoom", e);
        }
    }

    private record AnchorInfo(int pageIndex, double relativeOffset) {
    }

    private AnchorInfo findAnchorInfoFromLayout(double anchorYInPagesContainer, int totalPages) {
        int bestIndex = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestRelativeOffset = 0.5;

        for (int i = 0; i < totalPages; i++) {
            Bounds bounds = getPageBoundsInPagesContainer(i);
            if (bounds == null) continue;

            double start = bounds.getMinY();
            double end = bounds.getMaxY();

            double distance;
            if (anchorYInPagesContainer < start) {
                distance = start - anchorYInPagesContainer;
            } else if (anchorYInPagesContainer > end) {
                distance = anchorYInPagesContainer - end;
            } else {
                distance = 0;
            }

            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;

                double height = bounds.getHeight();
                if (height > 0) {
                    bestRelativeOffset = (anchorYInPagesContainer - start) / height;
                } else {
                    bestRelativeOffset = 0.5;
                }
                bestRelativeOffset = clamp(bestRelativeOffset, 0.0, 1.0);

                if (distance == 0) {
                    break;
                }
            }
        }

        return new AnchorInfo(bestIndex, bestRelativeOffset);
    }

    private Bounds getPageBoundsInPagesContainer(int pageIndex) {
        if (pagesContainer == null) return null;
        VBox pageBox = PageContainerUtils.findPageBox(pagesContainer, pageIndex);
        if (pageBox == null) return null;

        try {
            Bounds sceneBounds = pageBox.localToScene(pageBox.getBoundsInLocal());
            return pagesContainer.sceneToLocal(sceneBounds);
        } catch (RuntimeException e) {
            // Fallback: may be less accurate in nested layouts, but better than null.
            return pageBox.getBoundsInParent();
        }
    }

    /**
     * Enables or disables two-page mode. This rearranges placeholders into rows
     * but does not trigger re-render of images — existing ImageViews are preserved.
     */
    public void setTwoPageMode(boolean enable) {
        if (pagesContainer == null || currentDocument == null) return;
        if (this.twoPageMode == enable) return;

        // Remember the current page to keep the user roughly on the same page.
        int currentPage = (scrollHandler != null) ? scrollHandler.getCurrentPageFromScroll() : currentDocument.getCurrentPage();

        applyTwoPageModeLayout(enable);

        Platform.runLater(() -> {
            pagesContainer.applyCss();
            pagesContainer.layout();
            if (scrollHandler != null) {
                scrollHandler.scrollToPage(Math.max(0, currentPage));
                scrollHandler.handleScroll();
            }
        });
    }

    private void applyTwoPageModeLayout(boolean enable) {
        // Collect existing page boxes (whether currently single or arranged in rows)
        java.util.List<javafx.scene.layout.VBox> pageBoxes = PageContainerUtils.collectPageBoxes(pagesContainer);

        // Rebuild pagesContainer children according to mode
        pagesContainer.getChildren().clear();
        pagesContainer.setSpacing(10);

        if (enable) {
            // Build rows of two pages (HBox per row)
            for (int i = 0; i < pageBoxes.size(); i += 2) {
                javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(20);
                row.setAlignment(javafx.geometry.Pos.CENTER);
                javafx.scene.layout.VBox left = pageBoxes.get(i);
                row.getChildren().add(left);
                if (i + 1 < pageBoxes.size()) {
                    javafx.scene.layout.VBox right = pageBoxes.get(i + 1);
                    row.getChildren().add(right);
                }
                pagesContainer.getChildren().add(row);
            }
        } else {
            // Flatten back to single page VBoxes
            pagesContainer.getChildren().addAll(pageBoxes);
        }

        pagesContainer.getProperties().put("twoPageMode", enable);
        this.twoPageMode = enable;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Gets the page container.
     *
     * @return the page container
     */
    public VBox getPagesContainer() {
        return pagesContainer;
    }

    /**
     * Clears the page renderer cache to force re-rendering.
     * This should be called after modifying the PDF content (e.g., text edits).
     */
    public void clearPageRendererCache() {
        if (pageRenderer != null) {
            pageRenderer.clearCache();
            logger.info("Cleared PageRenderer cache");
        }
    }

    /**
     * Clears cache and reloads only the visible pages without recreating all placeholders.
     * This is more efficient than renderAllPages() for single page edits.
     */
    public void reloadVisiblePages() {
        if (scrollHandler != null) {
            // Clear cache first
            clearPageRendererCache();
            
            // Trigger scroll handler to reload visible pages
            Platform.runLater(() -> {
                scrollHandler.handleScroll();
                logger.info("Triggered reload of visible pages");
            });
        }
    }
}

