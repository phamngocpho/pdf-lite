package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

/**
 * Dialog for entering password to open encrypted PDF files.
 */
public class PasswordDialog extends Dialog<String> {

    private final PasswordField passwordField;

    public PasswordDialog() {
        setTitle("Mật khẩu PDF");
        setHeaderText("File PDF này được bảo vệ bởi mật khẩu");

        // Set the button types
        ButtonType loginButtonType = new ButtonType("Mở", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        // Create the password field
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        passwordField = new PasswordField();
        passwordField.setPromptText("Nhập mật khẩu");
        passwordField.setPrefWidth(250);

        grid.add(new Label("Mật khẩu:"), 0, 0);
        grid.add(passwordField, 1, 0);

        // Enable/Disable login button depending on whether a password was entered
        javafx.scene.Node loginButton = getDialogPane().lookupButton(loginButtonType);
        loginButton.setDisable(true);

        // Do some validation
        passwordField.textProperty().addListener((observable, oldValue, newValue) -> loginButton.setDisable(newValue.trim().isEmpty()));

        getDialogPane().setContent(grid);

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
     * @return the password entered by user, or null if cancelled
     */
    public String showAndGetPassword() {
        return showAndWait().orElse(null);
    }
}

