package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pdflite.manager.ThemeManager;
import org.pdflite.util.DialogTitleBar;

/**
 * Custom input dialog for bookmark title with custom title bar.
 */
public class BookmarkInputDialog {

    private Stage dialogStage;
    private String result = null;
    private TextField inputField;

    /**
     * Shows an input dialog for bookmark title.
     *
     * @param title        the dialog title
     * @param header       the header text
     * @param prompt       the prompt text
     * @param defaultValue the default value
     * @param themeManager the theme manager (can be null)
     * @return the entered text, or null if cancelled
     */
    public static String show(String title, String header, String prompt, String defaultValue, ThemeManager themeManager) {
        BookmarkInputDialog dialog = new BookmarkInputDialog();
        return dialog.showAndWait(title, header, prompt, defaultValue, themeManager);
    }

    private String showAndWait(String title, String header, String prompt, String defaultValue, ThemeManager themeManager) {
        dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.TRANSPARENT);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle(title);

        // Create main container with proper style class
        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("bookmark-input-dialog");

        // Create custom title bar
        DialogTitleBar titleBar = new DialogTitleBar(title, dialogStage);
        mainContainer.getChildren().add(titleBar.getTitleBar());

        // Create content
        VBox contentBox = new VBox(15);
        contentBox.setPadding(new Insets(20));
        contentBox.setAlignment(Pos.TOP_LEFT);

        // Header label
        if (header != null && !header.isEmpty()) {
            Label headerLabel = new Label(header);
            headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            contentBox.getChildren().add(headerLabel);
        }

        // Prompt label
        if (prompt != null && !prompt.isEmpty()) {
            Label promptLabel = new Label(prompt);
            contentBox.getChildren().add(promptLabel);
        }

        // Input field
        inputField = new TextField(defaultValue);
        inputField.setPrefWidth(400);
        inputField.setOnAction(e -> handleOk());
        contentBox.getChildren().add(inputField);

        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        Button cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(80);
        cancelButton.setOnAction(e -> {
            result = null;
            dialogStage.close();
        });

        Button okButton = new Button("OK");
        okButton.setPrefWidth(80);
        okButton.setDefaultButton(true);
        okButton.setOnAction(e -> handleOk());

        buttonBox.getChildren().addAll(cancelButton, okButton);
        contentBox.getChildren().add(buttonBox);

        mainContainer.getChildren().add(contentBox);

        Scene scene = new Scene(mainContainer);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialogStage.setScene(scene);

        dialogStage.setMinWidth(450);
        dialogStage.setMinHeight(200);

        // Apply theme BEFORE showing
        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }

        // Focus input field
        javafx.application.Platform.runLater(() -> {
            inputField.requestFocus();
            inputField.selectAll();
        });

        dialogStage.showAndWait();
        return result;
    }

    private void handleOk() {
        result = inputField.getText();
        dialogStage.close();
    }
}
