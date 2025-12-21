package org.pdflite.manager;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.pdflite.model.Annotation;
import org.pdflite.model.HighlightAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manager for persisting highlight annotations to PDF using PDFBox.
 * <p>
 * NOTE: Due to PDFBox 3.x API limitations (protected constructors for PDAnnotationTextMarkup),
 * this implementation currently stores highlights in the application model only.
 * Highlights are preserved during the session but not written to the PDF file.
 * <p>
 * For full PDF persistence, consider:
 * 1. Using PDFBox 2.x (has public constructors)
 * 2. Implementing custom COS-level annotation creation
 * 3. Using the external PDF library with better annotation support
 */
public class HighlightPersistenceManager {
    private static final Logger logger = LoggerFactory.getLogger(HighlightPersistenceManager.class);

    /**
     * Saves all highlight annotations to the PDF document.
     * <p>
     * NOTE: Currently, this only validates the highlights but does not write to PDF
     * due to PDFBox 3.x API limitations.
     *
     * @param document    the PDDocument to save highlights to
     * @param annotations list of annotations to save
     */
    public void saveHighlightsToPDF(PDDocument document, List<Annotation> annotations) {
        if (document == null || annotations == null) {
            logger.warn("Cannot save highlights: document or annotations is null");
            return;
        }

        int highlightCount = 0;

        for (Annotation annotation : annotations) {
            if (annotation instanceof HighlightAnnotation highlight) {
                highlightCount++;
                logger.debug("Highlight on page {}: x={}, y={}, w={}, h={}",
                        highlight.getPageNumber(), highlight.getX(), highlight.getY(),
                        highlight.getWidth(), highlight.getHeight());
            }
        }

        logger.info("Processed {} highlight annotations (stored in document model)", highlightCount);
        logger.warn("Note: Highlights are not persisted to PDF file due to PDFBox 3.x API limitations");
    }

    /**
     * Loads highlight annotations from the PDF document.
     * <p>
     * NOTE: Currently returns the empty list as highlights are only stored in memory.
     *
     * @param document the PDDocument to load highlights from
     * @return list of HighlightAnnotation objects (currently empty)
     */
    public List<HighlightAnnotation> loadHighlightsFromPDF(PDDocument document) {
        List<HighlightAnnotation> highlights = new ArrayList<>();

        if (document == null) {
            logger.warn("Cannot load highlights: document is null");
            return highlights;
        }

        logger.info("Loaded {} highlight annotations from PDF (memory-only storage)", 0);
        logger.debug("Note: Highlights are stored in application model, not in PDF file");

        return highlights;
    }
}
