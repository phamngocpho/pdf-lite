package org.pdflite.manager;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.pdflite.model.PDFDocument;

/**
 * Manages page information display and navigation buttons.
 * Handles updating page numbers and button states.
 */
public class PageInfoManager {

    private final Label totalPagesLabel;
    private final TextField pageNumberField;
    private final Button prevButton;
    private final Button nextButton;

    /**
     * Creates a new PageInfoManager.
     *
     * @param totalPagesLabel the label showing total pages
     * @param pageNumberField the text field for page number
     * @param prevButton the previous page button
     * @param nextButton the next page button
     */
    public PageInfoManager(Label totalPagesLabel, TextField pageNumberField,
                          Button prevButton, Button nextButton) {
        this.totalPagesLabel = totalPagesLabel;
        this.pageNumberField = pageNumberField;
        this.prevButton = prevButton;
        this.nextButton = nextButton;
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
            totalPagesLabel.setText("/ " + total);
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

    /**
     * Gets the page number from the text field.
     *
     * @return the page number (1-based), or -1 if invalid
     */
    public int getPageNumberFromField() {
        if (pageNumberField == null || pageNumberField.getText().isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(pageNumberField.getText());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

