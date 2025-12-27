package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pdflite.manager.LanguageManager;
import org.pdflite.manager.ThemeManager;
import org.pdflite.util.DialogTitleBar;

/**
 * Custom password dialog with a custom title bar.
 */
public class CustomPasswordDialog {

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private Stage dialogStage;
    private String password = null;
    private PasswordField passwordField;

    /**
     * Shows a password dialog.
     *
     * @param themeManager the theme manager (can be null)
     * @return the password entered, or null if cancelled
     */
    public static String show(ThemeManager themeManager) {
        CustomPasswordDialog dialog = new CustomPasswordDialog();
        return dialog.showAndWait(themeManager);
    }

    private String showAndWait(ThemeManager themeManager) {
        dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.TRANSPARENT);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle(lang().getString("password.title"));

        // Create main container
        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("custom-confirm-dialog");

        // Create custom title bar
        DialogTitleBar titleBar = new DialogTitleBar(lang().getString("password.title"), dialogStage);
        mainContainer.getChildren().add(titleBar.getTitleBar());

        // Create content
        VBox contentBox = new VBox(15);
        contentBox.setPadding(new Insets(20));
        contentBox.setAlignment(Pos.TOP_LEFT);

        // Header label
        Label headerLabel = new Label(lang().getString("password.header"));
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        contentBox.getChildren().add(headerLabel);

        // Password field
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        passwordField = new PasswordField();
        passwordField.setPromptText(lang().getString("password.placeholder"));
        passwordField.setPrefWidth(250);

        grid.add(new Label(lang().getString("password.label")), 0, 0);
        grid.add(passwordField, 1, 0);

        contentBox.getChildren().add(grid);

        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        Button cancelButton = new Button(lang().getString("dialog.cancel"));
        cancelButton.setPrefWidth(80);
        cancelButton.setOnAction(e -> {
            password = null;
            dialogStage.close();
        });

        Button okButton = new Button(lang().getString("password.open"));
        okButton.setPrefWidth(80);
        okButton.setDefaultButton(true);
        okButton.setDisable(true);
        okButton.setOnAction(e -> {
            password = passwordField.getText();
            dialogStage.close();
        });

        // Enable/disable OK button based on password field
        passwordField.textProperty().addListener((obs, oldVal, newVal) ->
                okButton.setDisable(newVal == null || newVal.trim().isEmpty())
        );

        buttonBox.getChildren().addAll(cancelButton, okButton);
        contentBox.getChildren().add(buttonBox);

        mainContainer.getChildren().add(contentBox);

        Scene scene = new Scene(mainContainer);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialogStage.setScene(scene);

        dialogStage.setMinWidth(450);
        dialogStage.setMinHeight(200);

        // Apply theme
        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }

        // Request focus on password field
        javafx.application.Platform.runLater(passwordField::requestFocus);

        dialogStage.showAndWait();
        return password;
    }
}
