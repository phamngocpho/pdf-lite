package org.pdflite.manager;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages UI state for the PDF viewer.
 * Handles enabling/disabling controls and status messages.
 */
public class UIStateManager {
    private static final Logger logger = LoggerFactory.getLogger(UIStateManager.class);

    private final Label statusLabel;
    private final Button prevButton;
    private final Button nextButton;
    private final TextField pageNumberField;
    private final ComboBox<String> zoomComboBox;

    /**
     * Creates a new UIStateManager.
     *
     * @param statusLabel the status label
     * @param prevButton the previous button
     * @param nextButton the next button
     * @param pageNumberField the page number field
     * @param zoomComboBox the zoom combo box
     */
    public UIStateManager(Label statusLabel, Button prevButton, Button nextButton,
                         TextField pageNumberField, ComboBox<String> zoomComboBox) {
        this.statusLabel = statusLabel;
        this.prevButton = prevButton;
        this.nextButton = nextButton;
        this.pageNumberField = pageNumberField;
        this.zoomComboBox = zoomComboBox;
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
     * @param title the error title
     * @param message the error message
     */
    public void showError(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
        logger.error("Error: {} - {}", title, message);
    }
}

