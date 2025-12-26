package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.pdflite.manager.LanguageManager;

/**
 * Dialog for entering password to open encrypted PDF files.
 */
public class PasswordDialog extends Dialog<String> {

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private final PasswordField passwordField;

    public PasswordDialog() {
        setTitle(lang().getString("password.title"));
        setHeaderText(lang().getString("password.header"));

        // Set the button types
        ButtonType loginButtonType = new ButtonType(lang().getString("password.open"), ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        // Create the password field
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        passwordField = new PasswordField();
        passwordField.setPromptText(lang().getString("password.placeholder"));
        passwordField.setPrefWidth(250);

        grid.add(new Label(lang().getString("password.label")), 0, 0);
        grid.add(passwordField, 1, 0);

        // Enable/Disable login button depending on whether a password was entered
        javafx.scene.Node loginButton = getDialogPane().lookupButton(loginButtonType);
        loginButton.setDisable(true);

        // Do some validation
        passwordField.textProperty().addListener((observable, oldValue, newValue) -> loginButton.setDisable(newValue.trim().isEmpty()));

        getDialogPane().setContent(grid);

        // Set minimum size for Ubuntu/Linux compatibility
        getDialogPane().setMinWidth(400);
        getDialogPane().setMinHeight(150);

        // Request focus on the password field by default
        javafx.application.Platform.runLater(passwordField::requestFocus);

        // Convert the result to a password-string when the login button is clicked
        setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return passwordField.getText();
            }
            return null;
        });
    }

    /**
     * Shows the dialog and waits for user input.
     *
     * @return the password entered by user, or null if canceled
     */
    public String showAndGetPassword() {
        return showAndWait().orElse(null);
    }
}

