package org.pdflite.manager;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.pdflite.command.CommandManager;
import org.pdflite.command.DeletePageCommand;
import org.pdflite.controller.MainController;
import org.pdflite.controller.PageRenderer;
import org.pdflite.controller.ScrollHandler;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

/**
 * Manages document operations such as rotation and page deletion.
 */
public record DocumentOperationManager(PDFService pdfService, RenderingManager renderingManager,
                                       ZoomManager zoomManager, PageInfoManager pageInfoManager,
                                       UIStateManager uiStateManager, ThemeManager themeManager,
                                       FileManager fileManager) {
    private static final Logger logger = LoggerFactory.getLogger(DocumentOperationManager.class);

    /**
     * Creates a new DocumentOperationManager.
     *
     * @param pdfService       the PDF service
     * @param renderingManager the rendering manager
     * @param zoomManager      the zoom manager
     * @param pageInfoManager  the page info manager
     * @param uiStateManager   the UI state manager
     * @param themeManager     the theme manager
     * @param fileManager      the file manager
     */
    public DocumentOperationManager {
    }

    /**
     * Rotates the document by the specified angle.
     *
     * @param currentDocument the current PDF document
     * @param angle           the rotation angle in degrees (positive for clockwise, negative for counter-clockwise)
     */
    public void rotateDocument(PDFDocument currentDocument, int angle) {
        if (currentDocument == null) return;

        // Calculate a new rotation angle
        int currentRot = currentDocument.getRotation();
        currentDocument.setRotation(currentRot + angle);

        // Re-render the screen
        if (renderingManager != null && zoomManager != null) {
            renderingManager.preserveScrollPositionAndApplyZoom(zoomManager.getCurrentZoom());
        }

        uiStateManager.updateStatus("Rotated document " + (angle > 0 ? "Right" : "Left"));
    }

    /**
     * Deletes a page from the document.
     * This is a complex operation that requires reloading the document.
     *
     * @param currentDocument the current PDF document
     * @param pageIndex       the page index to delete (0-based)
     * @param renderExecutor  the executor service for rendering
     * @param loadingPages    the set of pages currently loading
     * @param contentPane     the content pane
     * @param scrollPane      the scroll pane
     * @param pagesContainer  the page container (will be updated)
     * @param pageRenderer    the page renderer (will be recreated)
     * @param scrollHandler   the scroll handler (will be recreated)
     * @return the new PDFDocument after deletion, or null if deletion failed
     */
    public PDFDocument deletePage(PDFDocument currentDocument, int pageIndex,
                                  ExecutorService renderExecutor,
                                  Set<Integer> loadingPages,
                                  Pane contentPane, ScrollPane scrollPane,
                                  AtomicReference<VBox> pagesContainer,
                                  AtomicReference<PageRenderer> pageRenderer,
                                  AtomicReference<ScrollHandler> scrollHandler,
                                  AtomicReference<RenderingManager> renderingManagerWrapper) {
        if (currentDocument == null) {
            return null;
        }

        int total = currentDocument.getTotalPages();
        if (total <= 1) {
            uiStateManager.showError("Delete Page", "Cannot delete the last remaining page.");
            return null;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Page");
        confirm.setHeaderText("Delete current page?");
        confirm.setContentText("This will remove page " + (pageIndex + 1) + " from the document.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        if (themeManager != null) {
            themeManager.applyThemeToScene(confirm.getDialogPane().getScene());
        }

        final PDFDocument[] result = new PDFDocument[1];
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    // 1. Save necessary information
                    File currentFile = currentDocument.getFile();
                    double oldZoom = zoomManager.getCurrentZoom();

                    // 2. Delete page BEFORE save
                    fileManager.deletePages(currentDocument, List.of(pageIndex));

                    // 3. Save document
                    pdfService.save(currentDocument);

                    // 4. CRITICAL: Close old document to release file lock
                    pdfService.closePDF(currentDocument);

                    // 5. Clear ALL state
                    contentPane.getChildren().clear();
                    pagesContainer.set(null);
                    loadingPages.clear();

                    // 6. Clear cache and cancel all pending renders
                    pageRenderer.get().clearCache();
                    pageRenderer.get().cancelAllPendingRenders();

                    // 7. Create NEW PageRenderer and ScrollHandler
                    PageRenderer newPageRenderer = new PageRenderer(pdfService, renderExecutor);
                    ScrollHandler newScrollHandler = new ScrollHandler(newPageRenderer, scrollPane);
                    pageRenderer.set(newPageRenderer);
                    scrollHandler.set(newScrollHandler);

                    // Set page change listener again after recreating ScrollHandler
                    newScrollHandler.setPageChangeListener(newPageIndex -> Platform.runLater(() -> {
                        if (result[0] != null) {
                            pageInfoManager.updatePageInfo(result[0]);
                        }
                    }));

                    // 8. Reopen file (so PDFBox loads new structure)
                    result[0] = fileManager.openFile(currentFile);
                    if (result[0] == null) {
                        uiStateManager.showError("Error", "Could not reopen the file after deletion.");
                        return;
                    }

                    // 9. Calculate new current page
                    int newTotal = result[0].getTotalPages();
                    int newCurrentPage = (pageIndex >= newTotal) ? Math.max(0, newTotal - 1) : pageIndex;
                    result[0].setCurrentPage(newCurrentPage);
                    result[0].setZoomLevel(oldZoom);

                    // 10. Update renderer with a new document
                    newPageRenderer.setDocument(result[0], oldZoom);
                    zoomManager.setDocument(result[0]);
                    zoomManager.setCurrentZoom(oldZoom);

                    // 11. Recreate RenderingManager (note: caller will update renderingManager reference)
                    RenderingManager newRenderingManager = new RenderingManager(
                            pdfService, newPageRenderer, newScrollHandler, zoomManager);
                    newRenderingManager.setDocument(result[0]);
                    newRenderingManager.setUIComponents(null, scrollPane, contentPane);

                    // Update the renderingManager reference (stored in a wrapper for caller to update)
                    renderingManagerWrapper.set(newRenderingManager);

                    // 12. CRITICAL: Set document for ScrollHandler AFTER rendering
                    newRenderingManager.renderAllPages();
                    pagesContainer.set(newRenderingManager.getPagesContainer());

                    // 13. Set the document for ScrollHandler with valid pagesContainer
                    newScrollHandler.setDocument(result[0], pagesContainer.get());

                    // 14. Update UI
                    pageInfoManager.updatePageInfo(result[0]);

                    // 15. Scroll to the top and trigger render
                    Platform.runLater(() -> {
                        // Reset scroll position
                        scrollPane.setVvalue(0);
                        result[0].setCurrentPage(0);

                        // Clear loading pages before triggering scroll
                        loadingPages.clear();

                        // Trigger scroll handler to load necessary pages
                        newScrollHandler.handleScroll();

                        // Update UI
                        pageInfoManager.updatePageInfo(result[0]);
                        uiStateManager.updateStatus(
                                "Deleted page " + (pageIndex + 1) + ". Total pages: " + newTotal
                        );
                    });

                    logger.info("Successfully deleted page {} and reloaded document", pageIndex + 1);

                } catch (Exception ex) {
                    logger.error("Error deleting page {}", pageIndex + 1, ex);
                    uiStateManager.showError("Delete Page Error", "Could not delete the page: " + ex.getMessage());
                    result[0] = null;
                }
            }
        });

        return result[0];
    }

    /**
     * Handles delete page operation with confirmation dialog and command pattern support.
     * <p>
     * This method:
     * - Shows a confirmation dialog
     * - Creates and executes a DeletePageCommand through CommandManager
     * - Supports undo/redo functionality
     * </p>
     *
     * @param currentDocument the current PDF document
     * @param commandManager  the command manager for undo/redo support
     * @param controller      the main controller (needed by DeletePageCommand)
     * @param reloadCallback  callback to reload document after deletion
     */
    public void handleDeletePage(PDFDocument currentDocument, 
                                 CommandManager commandManager,
                                 MainController controller,
                                 ReloadCallback reloadCallback) {
        if (currentDocument == null) {
            return;
        }

        int total = currentDocument.getTotalPages();
        if (total <= 1) {
            uiStateManager.showError("Delete Page", "Cannot delete the last remaining page.");
            return;
        }

        int current = currentDocument.getCurrentPage();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Page");
        confirm.setHeaderText("Delete current page?");
        confirm.setContentText("This will remove page " + (current + 1) + " from the document.\n" +
                              "You can undo this action with Ctrl+Z.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        // Apply theme to dialog
        if (themeManager != null) {
            themeManager.applyThemeToScene(confirm.getDialogPane().getScene());
        }

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                try {
                    // Create and execute delete command through CommandManager
                    DeletePageCommand command = new DeletePageCommand(controller, pdfService, current);
                    commandManager.executeCommand(command, () -> {
                        if (reloadCallback != null) {
                            reloadCallback.reload();
                        }
                    });
                    
                    logger.info("Delete page command executed successfully");
                    uiStateManager.updateStatus("Deleted page " + (current + 1));
                    
                } catch (Exception e) {
                    logger.error("Error deleting page {}", current + 1, e);
                    uiStateManager.showError("Delete Page Error", 
                        "Could not delete the page: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Callback interface for reloading the document.
     */
    @FunctionalInterface
    public interface ReloadCallback {
        void reload() throws IOException;
    }
}

