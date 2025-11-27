package org.pdflite.manager;

import javafx.application.Platform;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages UI state for the PDF viewer.
 * Handles enabling/disabling controls and status messages.
 */
public record UIStateManager(Label statusLabel, Button prevButton, Button nextButton, TextField pageNumberField,
                             ComboBox<String> zoomComboBox) {
    private static final Logger logger = LoggerFactory.getLogger(UIStateManager.class);

    /**
     * Creates a new UIStateManager.
     *
     * @param statusLabel     the status label
     * @param prevButton      the previous button
     * @param nextButton      the next button
     * @param pageNumberField the page number field
     * @param zoomComboBox    the zoom combo box
     */
    public UIStateManager {
    }

    /**
     * Updates UI state based on whether a document is open.
     *
     * @param hasDocument true if a document is open
     */
    public void updateUIState(boolean hasDocument) {
        if (prevButton != null) prevButton.setDisable(!hasDocument);
        if (nextButton != null) nextButton.setDisable(!hasDocument);
        if (pageNumberField != null) pageNumberField.setDisable(!hasDocument);
        if (zoomComboBox != null) zoomComboBox.setDisable(!hasDocument);
    }

    /**
     * Updates the status label.
     *
     * @param message the status message
     */
    public void updateStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    /**
     * Shows an error dialog.
     *
     * @param title   the error title
     * @param message the error message
     */
    public void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
        logger.error("Error: {} - {}", title, message);
    }
}

