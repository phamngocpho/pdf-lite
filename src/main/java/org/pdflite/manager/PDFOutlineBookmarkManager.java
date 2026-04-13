package org.pdflite.manager;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports bookmarks from PDF's built-in outline (Table of Contents).
 * Many PDFs have a built-in outline/TOC that can be converted to bookmarks.
 */
public record PDFOutlineBookmarkManager(BookmarkManager bookmarkManager) {
    private static final Logger logger = LoggerFactory.getLogger(PDFOutlineBookmarkManager.class);

    /**
     * Imports bookmarks from the PDF outline.
     *
     * @param pdfDocument the PDF document
     * @return number of bookmarks imported
     */
    public int importFromPDFOutline(PDFDocument pdfDocument) {
        if (pdfDocument == null || pdfDocument.getDocument() == null) {
            logger.warn("Cannot import outline: no document");
            return 0;
        }

        PDDocument pdDoc = pdfDocument.getDocument();
        PDDocumentOutline outline = pdDoc.getDocumentCatalog().getDocumentOutline();

        if (outline == null) {
            logger.info("PDF has no outline/table of contents");
            return 0;
        }

        List<OutlineBookmark> outlineBookmarks = new ArrayList<>();
        extractOutlineItems(outline, pdDoc, outlineBookmarks, 0);

        // Add to bookmark manager
        int imported = 0;
        for (OutlineBookmark ob : outlineBookmarks) {
            if (ob.pageNumber >= 0 && ob.pageNumber < pdfDocument.getTotalPages()) {
                // Check if a bookmark already exists
                if (!bookmarkManager.hasBookmark(ob.pageNumber)) {
                    String title = ob.getIndentedTitle();
                    bookmarkManager.addBookmark(ob.pageNumber, title);
                    imported++;
                }
            }
        }

        logger.info("Imported {} bookmarks from PDF outline", imported);
        return imported;
    }

    /**
     * Recursively extracts outline items.
     */
    private void extractOutlineItems(PDOutlineNode node, PDDocument pdDoc,
                                     List<OutlineBookmark> bookmarks, int level) {
        PDOutlineItem current = node.getFirstChild();

        while (current != null) {
            try {
                String title = current.getTitle();

                // Get page number from destination
                int pageNumber = getPageNumberFromOutlineItem(current, pdDoc);

                if (pageNumber >= 0 && title != null && !title.trim().isEmpty()) {
                    bookmarks.add(new OutlineBookmark(pageNumber, title.trim(), level));
                }

                // Recursively process children (nested outline items)
                if (current.hasChildren()) {
                    extractOutlineItems(current, pdDoc, bookmarks, level + 1);
                }

            } catch (Exception e) {
                logger.warn("Error processing outline item: {}", e.getMessage());
            }

            current = current.getNextSibling();
        }
    }

    /**
     * Extracts page number from the outline item (handles both destination and action).
     */
    private int getPageNumberFromOutlineItem(PDOutlineItem item, PDDocument pdDoc) {
        try {
            // Try destination first
            if (item.getDestination() != null) {
                int pageIndex = resolveDestinationPageIndex(item.getDestination(), pdDoc);
                if (pageIndex >= 0) {
                    return pageIndex;
                }
            }

            // Try action if destination didn't work
            if (item.getAction() != null) {
                PDAction action = item.getAction();
                if (action instanceof PDActionGoTo goToAction) {
                    int pageIndex = resolveDestinationPageIndex(goToAction.getDestination(), pdDoc);
                    if (pageIndex >= 0) {
                        return pageIndex;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error extracting page number: {}", e.getMessage());
        }

        return -1;
    }

    private int resolveDestinationPageIndex(PDDestination destination, PDDocument pdDoc) throws IOException {
        if (destination instanceof PDNamedDestination namedDestination) {
            return resolveDestinationPageIndex(
                    pdDoc.getDocumentCatalog().findNamedDestinationPage(namedDestination),
                    pdDoc
            );
        }

        if (destination instanceof PDPageDestination pageDest) {
            PDPage page = pageDest.getPage();
            if (page != null) {
                return pdDoc.getPages().indexOf(page);
            }

            int pageIndex = pageDest.retrievePageNumber();
            if (pageIndex < 0) {
                pageIndex = pageDest.getPageNumber();
            }
            if (pageIndex >= 0 && pageIndex < pdDoc.getNumberOfPages()) {
                return pageIndex;
            }
        }

        return -1;
    }

    /**
     * Checks if the PDF has an outline.
     */
    public boolean hasOutline(PDFDocument pdfDocument) {
        if (pdfDocument == null || pdfDocument.getDocument() == null) {
            return true;
        }

        PDDocumentOutline outline = pdfDocument.getDocument()
                .getDocumentCatalog()
                .getDocumentOutline();

        return outline == null || outline.getFirstChild() == null;
    }

    /**
     * Gets the number of outline items.
     */
    public int getOutlineItemCount(PDFDocument pdfDocument) {
        if (hasOutline(pdfDocument)) {
            return 0;
        }

        PDDocumentOutline outline = pdfDocument.getDocument()
                .getDocumentCatalog()
                .getDocumentOutline();

        List<OutlineBookmark> bookmarks = new ArrayList<>();
        extractOutlineItems(outline, pdfDocument.getDocument(), bookmarks, 0);

        return bookmarks.size();
    }

    /**
     * Internal class to hold outline bookmark data.
     *
     * @param level Nesting level (0 = top level, 1 = sub-item, etc.)
     */
    private record OutlineBookmark(int pageNumber, String title, int level) {

        /**
         * Gets title with indentation based on level.
         */
        String getIndentedTitle() {
            if (level == 0) {
                return "📑 " + title;
            } else if (level == 1) {
                return "  ├─ " + title;
            } else {
                return "  ".repeat(level) + "└─ " + title;
            }
        }
    }
}
