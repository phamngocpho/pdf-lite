package org.pdflite.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for reordering pages in a PDF document.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class PDFReorderService {
    private static final Logger logger = LoggerFactory.getLogger(PDFReorderService.class);

    /**
     * Reorders pages in a PDF document based on the new order.
     *
     * @param pdfDoc   the PDF document
     * @param newOrder list of page indices in the new order (0-based)
     */
    public void reorderPages(PDFDocument pdfDoc, List<Integer> newOrder) {
        if (pdfDoc == null || newOrder == null || newOrder.isEmpty()) {
            throw new IllegalArgumentException("Invalid parameters for reordering");
        }

        PDDocument doc = pdfDoc.getDocument();
        int totalPages = doc.getNumberOfPages();

        if (newOrder.size() != totalPages) {
            throw new IllegalArgumentException(
                    String.format("New order size (%d) doesn't match total pages (%d)",
                            newOrder.size(), totalPages)
            );
        }

        try {
            // Extract all pages in the new order
            List<PDPage> reorderedPages = new ArrayList<>();
            for (Integer pageIndex : newOrder) {
                if (pageIndex < 0 || pageIndex >= totalPages) {
                    throw new IllegalArgumentException("Invalid page index: " + pageIndex);
                }
                reorderedPages.add(doc.getPage(pageIndex));
            }

            // Remove all pages from document
            while (doc.getNumberOfPages() > 0) {
                doc.removePage(0);
            }

            // Add pages back in new order
            for (PDPage page : reorderedPages) {
                doc.addPage(page);
            }

            // Clear cache to force re-rendering
            pdfDoc.clearCache();

            logger.info("Successfully reordered {} pages", totalPages);

        } catch (Exception e) {
            logger.error("Error reordering pages", e);
            throw new RuntimeException("Failed to reorder pages: " + e.getMessage(), e);
        }
    }

    /**
     * Moves a page from one position to another.
     *
     * @param pdfDoc      the PDF document
     * @param fromIndex   the source index (0-based)
     * @param toIndex     the destination index (0-based)
     */
    public void movePage(PDFDocument pdfDoc, int fromIndex, int toIndex) {
        if (pdfDoc == null) {
            throw new IllegalArgumentException("PDF document cannot be null");
        }

        PDDocument doc = pdfDoc.getDocument();
        int totalPages = doc.getNumberOfPages();

        if (fromIndex < 0 || fromIndex >= totalPages) {
            throw new IllegalArgumentException("Invalid source index: " + fromIndex);
        }

        if (toIndex < 0 || toIndex >= totalPages) {
            throw new IllegalArgumentException("Invalid destination index: " + toIndex);
        }

        if (fromIndex == toIndex) {
            return; // No change needed
        }

        try {
            // Get the page to move
            PDPage pageToMove = doc.getPage(fromIndex);

            // Remove the page from its current position
            doc.removePage(fromIndex);

            // Insert at new position
            if (toIndex >= doc.getNumberOfPages()) {
                doc.addPage(pageToMove);
            } else {
                PDPage targetPage = doc.getPage(toIndex);
                doc.getPages().insertBefore(pageToMove, targetPage);
            }

            // Clear cache
            pdfDoc.clearCache();

            logger.info("Moved page from index {} to {}", fromIndex, toIndex);

        } catch (Exception e) {
            logger.error("Error moving page", e);
            throw new RuntimeException("Failed to move page: " + e.getMessage(), e);
        }
    }
}
