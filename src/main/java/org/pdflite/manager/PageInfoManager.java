package org.pdflite.manager;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.pdflite.model.PDFDocument;

/**
 * Manages page information display and navigation buttons.
 * Handles updating page numbers and button states.
 */
public record PageInfoManager(Label totalPagesLabel, TextField pageNumberField, Button prevButton, Button nextButton,
                              PageLabelManager pageLabelManager) {

    /**
     * Creates a new PageInfoManager.
     *
     * @param totalPagesLabel the label showing total pages
     * @param pageNumberField the text field for page number
     * @param prevButton      the previous page button
     * @param nextButton      the next page button
     */
    public PageInfoManager {
    }

    /**
     * Updates page information display.
     *
     * @param document the PDF document
     */
    public void updatePageInfo(PDFDocument document) {
        if (document == null) {
            return;
        }

        int current = document.getCurrentPage() + 1;
        int total = document.getTotalPages();

        if (totalPagesLabel != null) {
            String pageLabel = pageLabelManager != null
                    ? pageLabelManager.getPageLabel(document, document.getCurrentPage())
                    : String.valueOf(current);
            if (!String.valueOf(current).equals(pageLabel)) {
                totalPagesLabel.setText("/ " + total + " [" + pageLabel + "]");
            } else {
                totalPagesLabel.setText("/ " + total);
            }
        }

        if (pageNumberField != null) {
            pageNumberField.setText(String.valueOf(current));
        }

        if (prevButton != null) {
            prevButton.setDisable(current == 1);
            prevButton.setMinSize(40, 40);
        }
        if (nextButton != null) {
            nextButton.setDisable(current == total);
            nextButton.setMinSize(40, 40);
        }
        if (pageNumberField != null) {
            pageNumberField.setPrefColumnCount(4);
        }
    }

    /**
     * Resets the page number field to show the current page.
     *
     * @param document the PDF document
     */
    public void resetPageFieldToCurrentPage(PDFDocument document) {
        if (document != null && pageNumberField != null) {
            pageNumberField.setText(String.valueOf(document.getCurrentPage() + 1));
        }
    }

    public String getPageInputFromField() {
        if (pageNumberField == null) {
            return "";
        }
        return pageNumberField.getText() != null ? pageNumberField.getText().trim() : "";
    }
}

