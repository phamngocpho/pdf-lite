package org.pdflite.manager;

import org.pdflite.model.PDFDocument;
import org.pdflite.util.NavigationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Manager for page navigation operations.
 */
public record NavigationManager(NavigationHelper navigationHelper, PageInfoManager pageInfoManager,
                                UIStateManager uiStateManager, Supplier<PDFDocument> documentSupplier,
                                PageLabelManager pageLabelManager) {

    private static final Logger logger = LoggerFactory.getLogger(NavigationManager.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    /**
     * Handles navigation to a specific page from the page number field.
     */
    public void handleGoToPage() {
        PDFDocument currentDocument = documentSupplier.get();
        String pageInput = pageInfoManager.getPageInputFromField();
        OptionalInt resolvedPageIndex = pageLabelManager != null
                ? pageLabelManager.resolvePageIndex(currentDocument, pageInput)
                : OptionalInt.empty();

        if (resolvedPageIndex.isPresent()) {
            int pageIndex = resolvedPageIndex.getAsInt();
            navigationHelper.navigateToPage(pageIndex);
            logger.debug("Navigated to page index {} from input {}", pageIndex, pageInput);
        } else {
            uiStateManager.showError(lang().getString("error.title"), lang().getString("navigation.invalidPageInput"));
            pageInfoManager.resetPageFieldToCurrentPage(currentDocument);
        }
    }

    /**
     * Navigates to the previous page.
     */
    public void handlePreviousPage() {
        PDFDocument currentDocument = documentSupplier.get();
        if (currentDocument != null && currentDocument.getCurrentPage() > 0) {
            navigationHelper.navigateToPage(currentDocument.getCurrentPage() - 1);
        }
    }

    /**
     * Navigates to the next page.
     */
    public void handleNextPage() {
        PDFDocument currentDocument = documentSupplier.get();
        if (currentDocument != null
                && currentDocument.getCurrentPage() < currentDocument.getTotalPages() - 1) {
            navigationHelper.navigateToPage(currentDocument.getCurrentPage() + 1);
        }
    }

    /**
     * Navigates to a specific page index.
     */
    public void navigateToPage(int pageIndex) {
        navigationHelper.navigateToPage(pageIndex);
    }
}
