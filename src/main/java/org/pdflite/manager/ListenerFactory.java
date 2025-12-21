package org.pdflite.manager;

import org.pdflite.controller.ScrollHandler;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.util.function.Supplier;

/**
 * Factory class for creating various listeners used in MainController.
 * This helps reduce the size of MainController by extracting listener creation logic.
 */
public class ListenerFactory {
    private static final Logger logger = LoggerFactory.getLogger(ListenerFactory.class);

    /**
     * Creates a zoom change listener with dynamic context.
     * The context can be updated later when the document is opened.
     *
     * @param renderingManagerSupplier supplier for the rendering manager
     * @param searchManager            the search manager
     * @param uiStateManager           the UI state manager
     * @return a zoom change listener that can be updated with document context
     */
    public static ZoomChangeListenerWithContext createZoomChangeListener(
            Supplier<RenderingManager> renderingManagerSupplier,
            SearchManager searchManager,
            UIStateManager uiStateManager) {
        return new ZoomChangeListenerWithContext(renderingManagerSupplier, searchManager, uiStateManager);
    }

    /**
     * Zoom change listener that can have its context updated dynamically.
     * This allows the listener to work before a document is opened.
     */
    public static class ZoomChangeListenerWithContext implements ZoomManager.ZoomChangeListener {
        private final Supplier<RenderingManager> renderingManagerSupplier;
        private final SearchManager searchManager;
        private final UIStateManager uiStateManager;
        private PDFDocument currentDocument;
        private VBox pagesContainer;
        private ScrollPane scrollPane;

        public ZoomChangeListenerWithContext(Supplier<RenderingManager> renderingManagerSupplier,
                                             SearchManager searchManager,
                                             UIStateManager uiStateManager) {
            this.renderingManagerSupplier = renderingManagerSupplier;
            this.searchManager = searchManager;
            this.uiStateManager = uiStateManager;
            logger.info("ZoomChangeListenerWithContext created with supplier");
        }

        /**
         * Updates the document context for this listener.
         *
         * @param currentDocument the current PDF document
         * @param pagesContainer  the page container
         * @param scrollPane      the scroll pane
         */
        public void updateContext(PDFDocument currentDocument, VBox pagesContainer, ScrollPane scrollPane) {
            this.currentDocument = currentDocument;
            this.pagesContainer = pagesContainer;
            this.scrollPane = scrollPane;
            RenderingManager renderingManager = renderingManagerSupplier.get();
            logger.info("ZoomChangeListener context updated - document: {}, pagesContainer: {}, scrollPane: {}, renderingManager: {}",
                    currentDocument != null ? "loaded" : "null",
                    pagesContainer != null ? "set" : "null",
                    scrollPane != null ? "set" : "null",
                    renderingManager != null ? "set" : "null");
        }

        @Override
        public void onZoomChanged(double newZoom) {
            RenderingManager renderingManager = renderingManagerSupplier.get();
            logger.info("ZoomChangeListener.onZoomChanged called - newZoom: {}, document: {}, pagesContainer: {}, renderingManager: {}",
                    newZoom,
                    currentDocument != null ? "loaded" : "null",
                    pagesContainer != null ? "set" : "null",
                    renderingManager != null ? "set" : "null");
            handleZoomChanged(newZoom, currentDocument, pagesContainer, scrollPane, renderingManager, searchManager);
        }

        @Override
        public void onZoomApplied(double newZoom, String statusMessage) {
            uiStateManager.updateStatus(statusMessage);
        }
    }

    /**
     * Handle zoom changed event with the provided context.
     * This is the common logic used by zoom change listeners.
     *
     * @param newZoom          the new zoom level
     * @param currentDocument  the current PDF document
     * @param pagesContainer   the page container
     * @param scrollPane       the scroll pane
     * @param renderingManager the rendering manager
     * @param searchManager    the search manager
     */
    private static void handleZoomChanged(double newZoom,
                                          PDFDocument currentDocument,
                                          VBox pagesContainer,
                                          ScrollPane scrollPane,
                                          RenderingManager renderingManager,
                                          SearchManager searchManager) {
        logger.info("handleZoomChanged - newZoom: {}, document: {}, pagesContainer: {}, scrollPane: {}, renderingManager: {}",
                newZoom,
                currentDocument != null ? "loaded" : "null",
                pagesContainer != null ? "set" : "null",
                scrollPane != null ? "set" : "null",
                renderingManager != null ? "set" : "null");

        if (currentDocument != null && pagesContainer != null && scrollPane != null) {
            // Switch layout mode based on the threshold (70% => 0.7)
            try {
                if (renderingManager != null) {
                    boolean shouldTwoPage = newZoom < 0.7;
                    logger.info("Setting two-page mode: {}", shouldTwoPage);
                    renderingManager.setTwoPageMode(shouldTwoPage);
                }
            } catch (Exception e) {
                logger.error("Error switching page layout mode", e);
            }

            if (renderingManager != null) {
                logger.info("Calling renderingManager.preserveScrollPositionAndApplyZoom({})", newZoom);
                renderingManager.preserveScrollPositionAndApplyZoom(newZoom);
            } else {
                logger.warn("renderingManager is null!");
            }
            Platform.runLater(() -> searchManager.updateHighlightsAfterZoom(newZoom));
        } else {
            logger.warn("Cannot handle zoom change - missing components: document={}, pagesContainer={}, scrollPane={}",
                    currentDocument != null, pagesContainer != null, scrollPane != null);
        }
    }

    /**
     * Creates a file operation listener.
     *
     * @param uiStateManager the UI state manager
     * @return the file operation listener
     */
    public static FileManager.FileOperationListener createFileOperationListener(UIStateManager uiStateManager) {
        return new FileManager.FileOperationListener() {
            @Override
            public void onFileOpened(PDFDocument document, java.io.File file) {
                // Handled in openPDFFile
            }

            @Override
            public void onFileSaved(String fileName) {
                uiStateManager.updateStatus("Saved: " + fileName);
            }

            @Override
            public void onFileSaveAs(String fileName) {
                uiStateManager.updateStatus("Saved As: " + fileName);
            }

            @Override
            public void onError(String title, String message) {
                uiStateManager.showError(title, message);
            }

            @Override
            public void onPageDeleted(int pageNumber) {
                uiStateManager.updateStatus("Deleted page " + pageNumber);
            }
        };
    }

    /**
     * Creates a fullscreen listener.
     *
     * @param uiStateManager the UI state manager
     * @return the fullscreen listener
     */
    public static FullscreenManager.FullscreenListener createFullscreenListener(UIStateManager uiStateManager) {
        return new FullscreenManager.FullscreenListener() {
            @Override
            public void onFullscreenChanged(boolean isFullscreen) {
                // Fullscreen state changed
            }

            @Override
            public void updateStatus(String message) {
                uiStateManager.updateStatus(message);
            }
        };
    }

    /**
     * Creates a page change listener for scroll handler.
     *
     * @param currentDocument the current PDF document
     * @param pageInfoManager the page info manager
     * @return the page change listener
     */
    public static ScrollHandler.PageChangeListener createPageChangeListener(
            PDFDocument currentDocument,
            PageInfoManager pageInfoManager) {
        return newPageIndex -> Platform.runLater(() -> {
            if (currentDocument != null) {
                // The scroll handler already updated currentDocument.setCurrentPage(newPageIndex)
                // Just update the UI
                pageInfoManager.updatePageInfo(currentDocument);
            }
        });
    }
}

