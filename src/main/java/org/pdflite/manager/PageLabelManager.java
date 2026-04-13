package org.pdflite.manager;

import org.apache.pdfbox.pdmodel.common.PDPageLabelRange;
import org.apache.pdfbox.pdmodel.common.PDPageLabels;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.OptionalInt;

/**
 * Manages page labels and custom numbering rules per document.
 */
public class PageLabelManager {

    private static final Logger logger = LoggerFactory.getLogger(PageLabelManager.class);

    public enum NumberingStyle {
        DECIMAL,
        ROMAN_UPPER,
        ROMAN_LOWER,
        LETTER_UPPER,
        LETTER_LOWER
    }

    public void initializeDocument(PDFDocument document) {
        if (document == null) {
            return;
        }
    }

    public void resetToDefault(PDFDocument document) {
        if (document == null || document.getDocument() == null) {
            return;
        }

        PDPageLabels labels = new PDPageLabels(document.getDocument());
        labels.setLabelItem(0, createRange(NumberingStyle.DECIMAL, "", 1));
        document.getDocument().getDocumentCatalog().setPageLabels(labels);
        document.setHasUnsavedEdits(true);
    }

    public void applyCustomRule(PDFDocument document, int startPageOneBased, NumberingStyle style,
                                String prefix, int startNumber) {
        if (document == null || document.getDocument() == null || document.getTotalPages() == 0) {
            return;
        }

        int startPageIndex = Math.max(0, Math.min(startPageOneBased - 1, document.getTotalPages() - 1));
        NumberingStyle numberingStyle = style != null ? style : NumberingStyle.DECIMAL;
        String safePrefix = prefix != null ? prefix : "";
        int safeStartNumber = Math.max(1, startNumber);

        PDPageLabels labels = getOrCreatePageLabels(document);
        labels.setLabelItem(startPageIndex, createRange(numberingStyle, safePrefix, safeStartNumber));
        document.getDocument().getDocumentCatalog().setPageLabels(labels);
        document.setHasUnsavedEdits(true);
    }

    public String getPageLabel(PDFDocument document, int pageIndex) {
        if (document == null || document.getDocument() == null || pageIndex < 0 || pageIndex >= document.getTotalPages()) {
            return "";
        }

        try {
            PDPageLabels labels = document.getDocument().getDocumentCatalog().getPageLabels();
            if (labels != null) {
                String[] labelsByPage = labels.getLabelsByPageIndices();
                if (pageIndex < labelsByPage.length && labelsByPage[pageIndex] != null) {
                    return labelsByPage[pageIndex];
                }
            }
        } catch (IOException ex) {
            logger.warn("Unable to read PDF page labels", ex);
        }

        return String.valueOf(pageIndex + 1);
    }

    /**
     * Resolves user input to a physical page index.
     * Input can be an index (1-based) or a page label.
     */
    public OptionalInt resolvePageIndex(PDFDocument document, String input) {
        if (document == null || input == null || input.isBlank()) {
            return OptionalInt.empty();
        }

        String normalizedInput = normalize(input);
        try {
            int oneBased = Integer.parseInt(normalizedInput);
            if (oneBased >= 1 && oneBased <= document.getTotalPages()) {
                return OptionalInt.of(oneBased - 1);
            }
        } catch (NumberFormatException ignored) {
            // Try matching by page label below.
        }

        for (int i = 0; i < document.getTotalPages(); i++) {
            String label = getPageLabel(document, i);
            if (normalize(label).equalsIgnoreCase(normalizedInput)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    private PDPageLabels getOrCreatePageLabels(PDFDocument document) {
        try {
            PDPageLabels labels = document.getDocument().getDocumentCatalog().getPageLabels();
            if (labels != null) {
                return labels;
            }
        } catch (IOException ex) {
            logger.warn("Unable to read existing PDF page labels; creating a new labels dictionary", ex);
        }
        return new PDPageLabels(document.getDocument());
    }

    private PDPageLabelRange createRange(NumberingStyle style, String prefix, int startNumber) {
        PDPageLabelRange range = new PDPageLabelRange();
        range.setStyle(toPdfBoxStyle(style));
        range.setPrefix(prefix);
        range.setStart(startNumber);
        return range;
    }

    private String toPdfBoxStyle(NumberingStyle style) {
        return switch (style) {
            case DECIMAL -> PDPageLabelRange.STYLE_DECIMAL;
            case ROMAN_UPPER -> PDPageLabelRange.STYLE_ROMAN_UPPER;
            case ROMAN_LOWER -> PDPageLabelRange.STYLE_ROMAN_LOWER;
            case LETTER_UPPER -> PDPageLabelRange.STYLE_LETTERS_UPPER;
            case LETTER_LOWER -> PDPageLabelRange.STYLE_LETTERS_LOWER;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "");
    }
}
