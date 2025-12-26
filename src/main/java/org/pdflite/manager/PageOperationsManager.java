package org.pdflite.manager;

import org.pdflite.controller.PageRenderer;
import org.pdflite.dialog.DuplicatePageDialog;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Manages page operations such as duplication, deletion, and reordering.
 */
public class PageOperationsManager {

    private static final Logger logger = LoggerFactory.getLogger(PageOperationsManager.class);

    private final UIStateManager uiStateManager;
    private final PageDuplicationManager pageDuplicationManager;
    private final PageInfoManager pageInfoManager;
    private final SaveStatusManager saveStatusManager;
    private final PageRenderer pageRenderer;
    private final ThemeManager themeManager;
    private Supplier<RenderingManager> renderingManagerSupplier;

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    public PageOperationsManager(UIStateManager uiStateManager, PageDuplicationManager pageDuplicationManager,
                                 RenderingManager renderingManager, PageInfoManager pageInfoManager,
                                 SaveStatusManager saveStatusManager, PageRenderer pageRenderer,
                                 ThemeManager themeManager) {
        this.uiStateManager = uiStateManager;
        this.pageDuplicationManager = pageDuplicationManager;
        this.pageInfoManager = pageInfoManager;
        this.saveStatusManager = saveStatusManager;
        this.pageRenderer = pageRenderer;
        this.themeManager = themeManager;
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
     * Handles page duplication operation.
     *
     * @param document The current PDF document
     */
    public void handleDuplicatePage(PDFDocument document) {
        if (document == null) {
            uiStateManager.showError(lang().getString("error.noDocument"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        try {
            int currentPage = document.getCurrentPage();
            int totalPages = document.getTotalPages();

            // Show duplicate page dialog
            DuplicatePageDialog dialog = new DuplicatePageDialog(currentPage, totalPages, themeManager);

            if (dialog.showAndWait()) {
                int sourcePageIndex = dialog.getSourcePageIndex();
                int insertPosition = dialog.getInsertPosition();
                int numberOfCopies = dialog.getNumberOfCopies();

                // Duplicate the page
                if (pageDuplicationManager.duplicatePage(document, sourcePageIndex,
                        insertPosition, numberOfCopies)) {
                    // Clear caches and re-render
                    document.clearCache();
                    pageRenderer.clearCache();
                    
                    // Get current tab's rendering manager
                    RenderingManager rm = renderingManagerSupplier != null ? renderingManagerSupplier.get() : null;
                    if (rm != null) {
                        rm.renderAllPages();
                    }

                    // Update page info
                    pageInfoManager.updatePageInfo(document);

                    uiStateManager.updateStatus(lang().getString("success.duplicated"));
                    logger.info("Page {} duplicated {} times at position {}",
                            sourcePageIndex + 1, numberOfCopies, insertPosition + 1);

                    // Trigger auto-save
                    if (saveStatusManager != null) {
                        saveStatusManager.triggerAutoSave();
                    }
                } else {
                    uiStateManager.showError(lang().getString("error.duplicateFailed"),
                            lang().getString("error.duplicateFailedMsg"));
                }
            }
        } catch (Exception e) {
            logger.error("Error duplicating page", e);
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.duplicate") + ": " + e.getMessage());
        }
    }
}
