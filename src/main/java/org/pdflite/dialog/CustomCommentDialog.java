package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pdflite.manager.LanguageManager;
import org.pdflite.manager.ThemeManager;
import org.pdflite.util.DialogTitleBar;

/**
 * Custom comment dialog with a custom title bar.
 */
public class CustomCommentDialog {

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private Stage dialogStage;
    private String comment = null;
    private TextArea commentTextArea;
    private Label charCountLabel;

    /**
     * Shows a comment dialog.
     *
     * @param themeManager the theme manager (can be null)
     * @return the comment entered, or null if cancelled
     */
    public static String show(ThemeManager themeManager) {
        CustomCommentDialog dialog = new CustomCommentDialog();
        return dialog.showAndWait(themeManager);
    }

    /**
     * Shows a comment dialog with initial text.
     *
     * @param themeManager the theme manager (can be null)
     * @param initialText  the initial comment text
     * @return the comment entered, or null if cancelled
     */
    public static String show(ThemeManager themeManager, String initialText) {
        CustomCommentDialog dialog = new CustomCommentDialog();
        return dialog.showAndWait(themeManager, initialText);
    }

    private String showAndWait(ThemeManager themeManager) {
        return showAndWait(themeManager, null);
    }

    private String showAndWait(ThemeManager themeManager, String initialText) {
        dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.TRANSPARENT);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle(lang().getString("comment.title"));

        // Create main container
        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("custom-comment-dialog");

        // Create custom title bar
        DialogTitleBar titleBar = new DialogTitleBar(lang().getString("comment.title"), dialogStage);
        mainContainer.getChildren().add(titleBar.getTitleBar());

        // Create content
        VBox contentBox = new VBox(15);
        contentBox.setPadding(new Insets(20));
        contentBox.setAlignment(Pos.TOP_LEFT);

        // Header with icon
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        Label iconLabel = new Label("💬");
        iconLabel.setStyle("-fx-font-size: 24px;");
        VBox headerTextBox = new VBox(2);
        Label titleLabel = new Label(lang().getString("comment.title"));
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        Label subtitleLabel = new Label(lang().getString("comment.subtitle"));
        subtitleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        headerTextBox.getChildren().addAll(titleLabel, subtitleLabel);
        headerBox.getChildren().addAll(iconLabel, headerTextBox);
        contentBox.getChildren().add(headerBox);

        // Comment text area
        VBox textAreaBox = new VBox(5);
        Label commentLabel = new Label(lang().getString("comment.label"));
        commentLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        
        commentTextArea = new TextArea();
        commentTextArea.setPromptText(lang().getString("comment.placeholder"));
        commentTextArea.setWrapText(true);
        commentTextArea.setPrefRowCount(6);
        commentTextArea.setPrefWidth(400);
        commentTextArea.setStyle("-fx-font-size: 12px;");
        
        if (initialText != null) {
            commentTextArea.setText(initialText);
        }
        
        textAreaBox.getChildren().addAll(commentLabel, commentTextArea);
        contentBox.getChildren().add(textAreaBox);

        // Character count
        HBox charCountBox = new HBox();
        charCountBox.setAlignment(Pos.CENTER_RIGHT);
        charCountLabel = new Label(java.text.MessageFormat.format(lang().getString("comment.charCount"), 0));
        charCountLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");
        charCountBox.getChildren().add(charCountLabel);
        contentBox.getChildren().add(charCountBox);

        // Update character count
        commentTextArea.textProperty().addListener((obs, oldVal, newVal) -> {
            int length = newVal != null ? newVal.length() : 0;
            charCountLabel.setText(java.text.MessageFormat.format(lang().getString("comment.charCount"), length));
        });

        // Initialize character count
        if (initialText != null) {
            int length = initialText.length();
            charCountLabel.setText(java.text.MessageFormat.format(lang().getString("comment.charCount"), length));
        }

        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        Button cancelButton = new Button(lang().getString("dialog.cancel"));
        cancelButton.setPrefWidth(90);
        cancelButton.getStyleClass().add("secondary-button");
        cancelButton.setOnAction(e -> {
            comment = null;
            dialogStage.close();
        });

        Button okButton = new Button(lang().getString("comment.addButton"));
        okButton.setPrefWidth(130);
        okButton.getStyleClass().add("primary-button");
        okButton.setDefaultButton(true);
        okButton.setOnAction(e -> {
            String text = commentTextArea.getText();
            if (text != null && !text.trim().isEmpty()) {
                comment = text;
                dialogStage.close();
            } else {
                // Show warning
                commentTextArea.setStyle("-fx-border-color: red; -fx-border-width: 2; -fx-font-size: 12px;");
            }
        });

        buttonBox.getChildren().addAll(cancelButton, okButton);
        contentBox.getChildren().add(buttonBox);

        mainContainer.getChildren().add(contentBox);

        Scene scene = new Scene(mainContainer);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialogStage.setScene(scene);

        dialogStage.setMinWidth(450);
        dialogStage.setMinHeight(350);

        // Apply theme
        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }

        // Request focus on text area
        javafx.application.Platform.runLater(commentTextArea::requestFocus);

        dialogStage.showAndWait();
        return comment;
    }
}
