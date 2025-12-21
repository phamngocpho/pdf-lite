package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pdflite.manager.ThemeManager;
import org.pdflite.util.DialogTitleBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom dialog for editing PDF document metadata.
 */
public class MetadataDialog {
    private static final Logger logger = LoggerFactory.getLogger(MetadataDialog.class);

    private final Stage dialog;
    private final Map<String, TextField> fields = new HashMap<>();
    private boolean confirmed = false;

    public MetadataDialog(Map<String, String> currentMetadata, ThemeManager themeManager) {
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Document Properties");

        VBox root = new VBox(0);
        root.getStyleClass().add("custom-confirm-dialog");

        // Custom title bar using DialogTitleBar utility
        DialogTitleBar titleBar = new DialogTitleBar("Document Properties", dialog);

        // Content area with scroll pane
        VBox content = createContent(currentMetadata);
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        scrollPane.setPrefViewportHeight(350);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Button bar
        HBox buttonBar = createButtonBar();

        root.getChildren().addAll(titleBar.getTitleBar(), scrollPane, buttonBar);

        Scene scene = new Scene(root, 500, 480);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);

        // Apply theme
        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }

        dialog.setScene(scene);
        dialog.setResizable(false);
    }

    private VBox createContent(Map<String, String> currentMetadata) {
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("dialog-content");

        // Create form fields
        addField(content, "Title:", "title", currentMetadata.getOrDefault("title", ""));
        addField(content, "Author:", "author", currentMetadata.getOrDefault("author", ""));
        addField(content, "Subject:", "subject", currentMetadata.getOrDefault("subject", ""));
        addField(content, "Keywords:", "keywords", currentMetadata.getOrDefault("keywords", ""));
        addField(content, "Creator:", "creator", currentMetadata.getOrDefault("creator", ""));

        // Read-only fields
        addReadOnlyField(content, "Producer:", currentMetadata.getOrDefault("producer", ""));
        addReadOnlyField(content, "Created:", currentMetadata.getOrDefault("creationDate", ""));
        addReadOnlyField(content, "Modified:", currentMetadata.getOrDefault("modificationDate", ""));

        return content;
    }

    private void addField(VBox container, String labelText, String fieldKey, String value) {
        VBox fieldBox = new VBox(4);

        Label label = new Label(labelText);
        label.getStyleClass().add("dialog-label");

        TextField textField = new TextField(value);
        textField.getStyleClass().add("dialog-text-field");
        textField.setPrefWidth(460);

        fields.put(fieldKey, textField);

        fieldBox.getChildren().addAll(label, textField);
        container.getChildren().add(fieldBox);
    }

    private void addReadOnlyField(VBox container, String labelText, String value) {
        VBox fieldBox = new VBox(4);

        Label label = new Label(labelText);
        label.getStyleClass().add("dialog-label");

        TextField textField = new TextField(value);
        textField.getStyleClass().add("dialog-text-field");
        textField.setPrefWidth(460);
        textField.setEditable(false);
        textField.setStyle("-fx-opacity: 0.7;");

        fieldBox.getChildren().addAll(label, textField);
        container.getChildren().add(fieldBox);
    }

    private HBox createButtonBar() {
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(0, 20, 20, 20));
        buttonBar.getStyleClass().add("dialog-button-bar");

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("dialog-button");
        cancelButton.setPrefWidth(100);
        cancelButton.setOnAction(e -> {
            confirmed = false;
            dialog.close();
        });

        Button okButton = new Button("OK");
        okButton.getStyleClass().addAll("dialog-button", "dialog-button-primary");
        okButton.setPrefWidth(100);
        okButton.setOnAction(e -> {
            confirmed = true;
            dialog.close();
        });
        okButton.setDefaultButton(true);

        buttonBar.getChildren().addAll(cancelButton, okButton);
        return buttonBar;
    }

    /**
     * Shows the dialog and waits for user input.
     *
     * @return true if OK was clicked, false if cancelled
     */
    public boolean showAndWait() {
        dialog.showAndWait();
        return confirmed;
    }

    /**
     * Gets the updated metadata values from the dialog.
     *
     * @return map of metadata key-value pairs
     */
    public Map<String, String> getMetadata() {
        Map<String, String> metadata = new HashMap<>();
        fields.forEach((key, field) -> metadata.put(key, field.getText().trim()));
        return metadata;
    }
}
