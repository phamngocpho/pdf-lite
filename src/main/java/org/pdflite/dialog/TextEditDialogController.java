package org.pdflite.dialog;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the text edit dialog.
 * <p>
 * This dialog allows users to edit text content extracted from a PDF page.
 * It displays the original text (read-only) and provides a text area for
 * entering the new text content.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class TextEditDialogController {
    private static final Logger logger = LoggerFactory.getLogger(TextEditDialogController.class);

    @FXML
    private TextArea originalTextArea;

    @FXML
    private TextArea newTextArea;

    private Stage dialogStage;
    private boolean okClicked = false;
    private String newText;

    /**
     * Initializes the controller.
     */
    @FXML
    private void initialize() {
        logger.debug("TextEditDialogController initialized");
    }

    /**
     * Sets the dialog stage.
     *
     * @param dialogStage the dialog stage
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /**
     * Sets the original text to display.
     *
     * @param text the original text
     */
    public void setOriginalText(String text) {
        originalTextArea.setText(text);
        newTextArea.setText(text); // Pre-fill with original text
    }

    /**
     * Returns whether the OK button was clicked.
     *
     * @return true if OK was clicked, false otherwise
     */
    public boolean isOkClicked() {
        return okClicked;
    }

    /**
     * Returns the new text entered by the user.
     *
     * @return the new text, or null if cancelled
     */
    public String getNewText() {
        return newText;
    }

    /**
     * Handles the OK button action.
     */
    @FXML
    private void handleOK() {
        newText = newTextArea.getText();
        okClicked = true;
        logger.debug("Text edit confirmed: '{}' -> '{}'", originalTextArea.getText(), newText);
        dialogStage.close();
    }

    /**
     * Handles the Cancel button action.
     */
    @FXML
    private void handleCancel() {
        okClicked = false;
        logger.debug("Text edit cancelled");
        dialogStage.close();
    }
}
