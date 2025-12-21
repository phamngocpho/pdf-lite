package org.pdflite.manager;

import org.pdflite.controller.PageRenderer;
import org.pdflite.dialog.DuplicatePageDialog;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages page operations such as duplication, deletion, and reordering.
 */
public class PageOperationsManager {
    
    private static final Logger logger = LoggerFactory.getLogger(PageOperationsManager.class);
    
    private final UIStateManager uiStateManager;
    private final PageDuplicationManager pageDuplicationManager;
    private final RenderingManager renderingManager;
    private final PageInfoManager pageInfoManager;
    private final SaveStatusManager saveStatusManager;
    private final PageRenderer pageRenderer;
    private final ThemeManager themeManager;
    
    public PageOperationsManager(UIStateManager uiStateManager,
                                PageDuplicationManager pageDuplicationManager,
                                RenderingManager renderingManager,
                                PageInfoManager pageInfoManager,
                                SaveStatusManager saveStatusManager,
                                PageRenderer pageRenderer,
                                ThemeManager themeManager) {
        this.uiStateManager = uiStateManager;
        this.pageDuplicationManager = pageDuplicationManager;
        this.renderingManager = renderingManager;
        this.pageInfoManager = pageInfoManager;
        this.saveStatusManager = saveStatusManager;
        this.pageRenderer = pageRenderer;
        this.themeManager = themeManager;
    }
    
    /**
     * Handles page duplication operation.
     * 
     * @param document The current PDF document
     */
    public void handleDuplicatePage(PDFDocument document) {
        if (document == null) {
            uiStateManager.showError("No Document", "Please open a PDF file first.");
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
                    renderingManager.renderAllPages();

                    // Update page info
                    pageInfoManager.updatePageInfo(document);

                    uiStateManager.updateStatus(
                        String.format("Duplicated page %d (%d copies) - Don't forget to save!", 
                                    sourcePageIndex + 1, numberOfCopies));
                    logger.info("Page {} duplicated {} times at position {}", 
                               sourcePageIndex + 1, numberOfCopies, insertPosition + 1);
                    
                    // Trigger auto-save
                    if (saveStatusManager != null) {
                        saveStatusManager.triggerAutoSave();
                    }
                } else {
                    uiStateManager.showError("Duplication Failed", 
                        "Failed to duplicate the page.");
                }
            }
        } catch (Exception e) {
            logger.error("Error duplicating page", e);
            uiStateManager.showError("Error", "Failed to duplicate page: " + e.getMessage());
        }
    }
}
