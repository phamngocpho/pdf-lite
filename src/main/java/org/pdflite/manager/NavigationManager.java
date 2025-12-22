package org.pdflite.manager;

import org.pdflite.model.PDFDocument;
import org.pdflite.util.NavigationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Manages page navigation operations.
 */
public record NavigationManager(NavigationHelper navigationHelper, PageInfoManager pageInfoManager,
                                UIStateManager uiStateManager, Supplier<PDFDocument> currentDocumentSupplier) {

    private static final Logger logger = LoggerFactory.getLogger(NavigationManager.class);

    /**
     * Navigates to the previous page.
     */
    public void handlePreviousPage() {
        PDFDocument currentDocument = currentDocumentSupplier.get();
        if (currentDocument != null && currentDocument.getCurrentPage() > 0) {
            navigationHelper.navigateToPage(currentDocument.getCurrentPage() - 1);
            logger.debug("Navigated to previous page: {}", currentDocument.getCurrentPage());
        }
    }

    /**
     * Navigates to the next page.
     */
    public void handleNextPage() {
        PDFDocument currentDocument = currentDocumentSupplier.get();
        if (currentDocument != null
                && currentDocument.getCurrentPage() < currentDocument.getTotalPages() - 1) {
            navigationHelper.navigateToPage(currentDocument.getCurrentPage() + 1);
            logger.debug("Navigated to next page: {}", currentDocument.getCurrentPage());
        }
    }

    /**
     * Navigates to a specific page number entered by the user.
     */
    public void handleGoToPage() {
        PDFDocument currentDocument = currentDocumentSupplier.get();
        int pageNum = pageInfoManager.getPageNumberFromField();
        if (pageNum > 0) {
            navigationHelper.jumpToPage(pageNum);
            logger.debug("Jumped to page: {}", pageNum);
        } else {
            uiStateManager.showError("Invalid Input", "Please enter a valid page number");
            pageInfoManager.resetPageFieldToCurrentPage(currentDocument);
        }
    }
}
