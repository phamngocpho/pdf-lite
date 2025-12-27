package org.pdflite.command;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.pdflite.manager.LanguageManager;
import org.pdflite.model.Annotation;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Command for deleting a page from a PDF document.
 * Supports undo by restoring the deleted page and its annotations.
 */
public class DeletePageCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(DeletePageCommand.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private final PDFDocument document;
    private final int pageIndex;
    private PDPage deletedPage;
    private final List<Annotation> deletedAnnotations = new ArrayList<>();
    private final Runnable refreshCallback;

    /**
     * Creates a new DeletePageCommand.
     *
     * @param document        the PDF document
     * @param pageIndex       the index of the page to delete (zero-based)
     * @param refreshCallback callback to refresh the UI after execution/undo
     */
    public DeletePageCommand(PDFDocument document, int pageIndex, Runnable refreshCallback) {
        this.document = document;
        this.pageIndex = pageIndex;
        this.refreshCallback = refreshCallback;
    }

    @Override
    public void execute() {
        PDDocument pdDoc = document.getDocument();

        // Save the page before deleting
        deletedPage = pdDoc.getPage(pageIndex);

        // Save annotations for this page
        deletedAnnotations.clear();
        deletedAnnotations.addAll(document.getAnnotationsForPage(pageIndex));

        // Remove annotations for this page
        document.getAnnotations().removeAll(deletedAnnotations);

        // Delete the page
        pdDoc.removePage(pageIndex);

        // Update annotations for pages after the deleted page
        for (Annotation ann : document.getAnnotations()) {
            if (ann.getPageNumber() > pageIndex) {
                ann.setPageNumber(ann.getPageNumber() - 1);
            }
        }

        document.setHasUnsavedEdits(true);

        if (refreshCallback != null) {
            refreshCallback.run();
        }

        logger.info("Deleted page {} from document", pageIndex + 1);
    }

    @Override
    public void undo() {
        PDDocument pdDoc = document.getDocument();

        // Re-insert the page at its original position
        pdDoc.getPages().insertBefore(deletedPage,
                pageIndex < pdDoc.getNumberOfPages() ? pdDoc.getPage(pageIndex) : null);

        // Update annotations for pages after the restored page
        for (Annotation ann : document.getAnnotations()) {
            if (ann.getPageNumber() >= pageIndex) {
                ann.setPageNumber(ann.getPageNumber() + 1);
            }
        }

        // Restore annotations for this page
        document.getAnnotations().addAll(deletedAnnotations);

        document.setHasUnsavedEdits(true);

        if (refreshCallback != null) {
            refreshCallback.run();
        }

        logger.info("Restored page {} to document", pageIndex + 1);
    }

    @Override
    public String getDescription() {
        return MessageFormat.format(lang().getString("command.deletePage"), pageIndex + 1);
    }
}
