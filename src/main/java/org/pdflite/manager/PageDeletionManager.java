package org.pdflite.manager;

import javafx.application.Platform;
import org.pdflite.command.DeletePageCommand;
import org.pdflite.dialog.CustomConfirmDialog;
import org.pdflite.model.PDFDocument;
import org.pdflite.controller.PageRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Manages page deletion operations with undo/redo support.
 * Handles user confirmation, command execution, and UI updates.
 */
public class PageDeletionManager {
    private static final Logger logger = LoggerFactory.getLogger(PageDeletionManager.class);

    private final UIStateManager uiStateManager;
    private final UndoRedoManager undoRedoManager;
    private final PageInfoManager pageInfoManager;
    private final PageRenderer pageRenderer;
    private Supplier<ThemeManager> themeManagerSupplier;
    private Supplier<RenderingManager> renderingManagerSupplier;

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    /**
     * Creates a new PageDeletionManager.
     *
     * @param uiStateManager   the UI state manager
     * @param undoRedoManager  the undo/redo manager
     * @param pageInfoManager  the page info manager
     * @param pageRenderer     the page renderer
     */
    public PageDeletionManager(UIStateManager uiStateManager,
                               UndoRedoManager undoRedoManager,
                               RenderingManager renderingManager,
                               PageInfoManager pageInfoManager,
                               PageRenderer pageRenderer) {
        this.uiStateManager = uiStateManager;
        this.undoRedoManager = undoRedoManager;
        this.pageInfoManager = pageInfoManager;
        this.pageRenderer = pageRenderer;

        logger.info("PageDeletionManager initialized");
    }

    /**
     * Sets the theme manager supplier.
     *
     * @param themeManagerSupplier supplier that provides the current theme manager
     */
    public void setThemeManagerSupplier(Supplier<ThemeManager> themeManagerSupplier) {
        this.themeManagerSupplier = themeManagerSupplier;
    }

    /**
     * Sets the rendering manager supplier for multi-tab support.
     *
     * @param renderingManagerSupplier supplier that provides the current tab's rendering manager
     */
    public void setRenderingManagerSupplier(Supplier<RenderingManager> renderingManagerSupplier) {
        this.renderingManagerSupplier = renderingManagerSupplier;
    }

    /**
     * Handles the page deletion operation.
     * Shows confirmation dialog, creates command, and executes it.
     *
     * @param currentDocument the current PDF document
     */
    public void handleDeletePage(PDFDocument currentDocument) {
        // Validate document
        if (currentDocument == null) {
            uiStateManager.showError(lang().getString("error.noDocument"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        // Check if we can delete
        if (currentDocument.getTotalPages() <= 1) {
            uiStateManager.showError(lang().getString("error.deleteFailed"), lang().getString("error.delete"));
            return;
        }

        int currentPage = currentDocument.getCurrentPage();

        // Show confirmation dialog
        if (!showConfirmationDialog(currentPage)) {
            return;
        }

        // Execute deletion
        executeDeletePage(currentDocument, currentPage);
    }

    /**
     * Shows a confirmation dialog for page deletion.
     *
     * @param pageNumber the page number to delete (0-based)
     * @return true if user confirmed, false otherwise
     */
    private boolean showConfirmationDialog(int pageNumber) {
        ThemeManager themeManager = themeManagerSupplier != null ? themeManagerSupplier.get() : null;
        return CustomConfirmDialog.show(
                lang().getString("confirm.title"),
                lang().getString("confirm.delete"),
                lang().getString("toolbar.undo") + " (Ctrl+Z)",
                themeManager
        );
    }

    /**
     * Executes the page deletion command.
     *
     * @param currentDocument the current PDF document
     * @param pageNumber      the page number to delete (0-based)
     */
    private void executeDeletePage(PDFDocument currentDocument, int pageNumber) {
        try {
            // Create delete page command with refresh callback
            DeletePageCommand cmd = new DeletePageCommand(
                    currentDocument,
                    pageNumber,
                    () -> refreshAfterDeletion(currentDocument)
            );

            // Execute command through undo/redo manager
            if (undoRedoManager != null) {
                undoRedoManager.getCommandManager().executeCommand(cmd);
                uiStateManager.updateStatus(lang().getString("success.deleted"));
                logger.info("Deleted page {} from document", pageNumber + 1);
            }

            // Adjust current page if needed
            adjustCurrentPageAfterDeletion(currentDocument);

        } catch (Exception e) {
            logger.error("Error deleting page {}", pageNumber + 1, e);
            uiStateManager.showError(lang().getString("error.deleteFailed"), lang().getString("error.delete") + ": " + e.getMessage());
        }
    }

    /**
     * Refreshes the UI after page deletion.
     *
     * @param currentDocument the current PDF document
     */
    private void refreshAfterDeletion(PDFDocument currentDocument) {
        Platform.runLater(() -> {
            // Clear cache and re-render
            currentDocument.clearCache();

            if (pageRenderer != null) {
                pageRenderer.clearCache();
            }

            // Get current tab's rendering manager
            RenderingManager rm = renderingManagerSupplier != null ? renderingManagerSupplier.get() : null;
            if (rm != null) {
                rm.renderAllPages();
            }

            // Update page info
            if (pageInfoManager != null) {
                pageInfoManager.updatePageInfo(currentDocument);
            }

            logger.debug("UI refreshed after page deletion");
        });
    }

    /**
     * Adjusts the current page index after deletion if necessary.
     *
     * @param currentDocument the current PDF document
     */
    private void adjustCurrentPageAfterDeletion(PDFDocument currentDocument) {
        if (currentDocument.getCurrentPage() >= currentDocument.getTotalPages()) {
            int newPage = Math.max(0, currentDocument.getTotalPages() - 1);
            currentDocument.setCurrentPage(newPage);
            logger.debug("Adjusted current page to {} after deletion", newPage);
        }
    }
}
