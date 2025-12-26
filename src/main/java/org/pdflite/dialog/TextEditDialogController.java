package org.pdflite.dialog;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.pdflite.manager.LanguageManager;
import org.pdflite.util.DialogTitleBar;
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

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    @FXML
    private HBox dialogTitleBar;

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

        // Create and add custom title bar
        DialogTitleBar titleBar = new DialogTitleBar(lang().getString("textEdit.title"), dialogStage);
        // Copy children from title bar to dialogTitleBar HBox
        dialogTitleBar.getChildren().setAll(titleBar.getTitleBar().getChildren());
        
        // Update all UI text
        updateAllUIText();
    }

    /**
     * Updates all UI text elements with current language.
     */
    private void updateAllUIText() {
        if (dialogStage == null || dialogStage.getScene() == null) {
            return;
        }
        
        // Recursively update all Labels and Buttons in the scene
        updateNodeText(dialogStage.getScene().getRoot());
    }
    
    /**
     * Recursively updates text for Labels and Buttons.
     */
    private void updateNodeText(javafx.scene.Node node) {
        if (node instanceof javafx.scene.control.Label label) {
            String text = label.getText();
            if (text != null && !text.isEmpty()) {
                switch (text) {
                    case "Original Text:" -> label.setText(lang().getString("textEdit.original") + ":");
                    case "New Text:" -> label.setText(lang().getString("textEdit.new") + ":");
                    case "(Text extraction may be inaccurate with some fonts)" -> 
                        label.setText("(" + lang().getString("textEdit.warning") + ")");
                }
            }
        } else if (node instanceof javafx.scene.control.Button button) {
            String text = button.getText();
            if (text != null && !text.isEmpty()) {
                switch (text) {
                    case "OK" -> button.setText(lang().getString("textEdit.ok"));
                    case "Cancel" -> button.setText(lang().getString("textEdit.cancel"));
                }
            }
        }
        
        // Recursively process children
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                updateNodeText(child);
            }
        }
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
