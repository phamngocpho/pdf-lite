package org.pdflite.manager;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager for duplicating pages in a PDF document.
 */
public class PageDuplicationManager {
    private static final Logger logger = LoggerFactory.getLogger(PageDuplicationManager.class);

    /**
     * Duplicates a page in the PDF document.
     *
     * @param pdfDocument     the PDF document
     * @param sourcePageIndex the index of the page to duplicate (0-based)
     * @param insertPosition  the position where to insert the duplicate (0-based)
     * @param numberOfCopies  how many copies to create
     * @return true if duplication was successful
     */
    public boolean duplicatePage(PDFDocument pdfDocument, int sourcePageIndex,
                                 int insertPosition, int numberOfCopies) {
        if (pdfDocument == null) {
            logger.warn("Cannot duplicate page: document is null");
            return false;
        }

        PDDocument document = pdfDocument.getDocument();
        if (document == null) {
            logger.warn("Cannot duplicate page: underlying PDDocument is null");
            return false;
        }

        int totalPages = document.getNumberOfPages();
        if (sourcePageIndex < 0 || sourcePageIndex >= totalPages) {
            logger.warn("Invalid source page index: {}", sourcePageIndex);
            return false;
        }

        if (insertPosition < 0 || insertPosition > totalPages) {
            logger.warn("Invalid insert position: {}", insertPosition);
            return false;
        }

        if (numberOfCopies < 1) {
            logger.warn("Invalid number of copies: {}", numberOfCopies);
            return false;
        }

        try {
            // Get the source page
            PDPage sourcePage = document.getPage(sourcePageIndex);

            // Create and insert copies
            for (int i = 0; i < numberOfCopies; i++) {
                // Import the page (creates a deep copy)
                PDPage duplicatedPage = document.importPage(sourcePage);

                // Remove from end and insert at desired position
                document.removePage(duplicatedPage);
                document.getPages().insertBefore(duplicatedPage,
                        document.getPage(insertPosition + i));
            }

            // Mark document as modified
            pdfDocument.setHasUnsavedEdits(true);

            logger.info("Duplicated page {} {} times at position {}",
                    sourcePageIndex + 1, numberOfCopies, insertPosition + 1);
            return true;

        } catch (Exception e) {
            logger.error("Error duplicating page", e);
            return false;
        }
    }
}
