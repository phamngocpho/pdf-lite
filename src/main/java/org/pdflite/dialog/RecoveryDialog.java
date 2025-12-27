package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pdflite.manager.LanguageManager;
import org.pdflite.manager.ThemeManager;
import org.pdflite.util.DialogTitleBar;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Dialog for recovering auto-saved documents.
 */
public class RecoveryDialog {

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private final Stage dialog;
    private boolean recoverClicked = false;
    private File selectedFile;

    public RecoveryDialog(File[] recoveryFiles, ThemeManager themeManager) {
        dialog = new Stage();
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(lang().getString("recovery.title"));

        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("custom-confirm-dialog");

        // Title bar
        DialogTitleBar titleBar = new DialogTitleBar(lang().getString("recovery.title"), dialog);
        mainContainer.getChildren().add(titleBar.getTitleBar());

        // Content
        VBox content = createContent(recoveryFiles);
        mainContainer.getChildren().add(content);

        // Buttons
        HBox buttonBox = createButtonBox();
        mainContainer.getChildren().add(buttonBox);

        Scene scene = new Scene(mainContainer);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);

        dialog.setMinWidth(500);
        dialog.setMinHeight(300);

        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }
    }

    private VBox createContent(File[] recoveryFiles) {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_LEFT);

        // Info icon and message
        javafx.scene.shape.SVGPath infoIcon = new javafx.scene.shape.SVGPath();
        infoIcon.setContent("M440-280h80v-240h-80v240Zm40-320q17 0 28.5-11.5T520-640q0-17-11.5-28.5T480-680q-17 0-28.5 11.5T440-640q0 17 11.5 28.5T480-600Zm0 520q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-320Z");
        infoIcon.setFill(javafx.scene.paint.Color.web("#2196f3"));

        StackPane iconContainer = new StackPane(infoIcon);
        iconContainer.setMinSize(20, 20);
        iconContainer.setMaxSize(20, 20);
        iconContainer.setPrefSize(20, 20);

        double scale = 20.0 / 960.0;
        infoIcon.setScaleX(scale);
        infoIcon.setScaleY(scale);

        Label infoLabel = new Label(lang().getString("autosave.recoveryPrompt"));
        infoLabel.setWrapText(true);
        infoLabel.setMaxWidth(440);

        HBox infoBox = new HBox(10);
        infoBox.setAlignment(Pos.TOP_LEFT);
        infoBox.getChildren().addAll(iconContainer, infoLabel);

        // Recovery files list
        ListView<File> fileListView = new ListView<>();
        fileListView.setPrefHeight(150);
        fileListView.getItems().addAll(recoveryFiles);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        fileListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                if (empty || file == null) {
                    setText(null);
                } else {
                    String fileName = file.getName().replace("autosave_", "").replace(".pdf", "");
                    String date = dateFormat.format(new Date(file.lastModified()));
                    setText(fileName + " (saved: " + date + ")");
                }
            }
        });

        // Select first item by default
        if (recoveryFiles.length > 0) {
            fileListView.getSelectionModel().select(0);
            selectedFile = recoveryFiles[0];
        }

        fileListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> selectedFile = newVal);

        content.getChildren().addAll(infoBox, fileListView);
        return content;
    }

    private HBox createButtonBox() {
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(0, 20, 20, 20));

        Button discardButton = new Button(lang().getString("dialog.cancel"));
        discardButton.setPrefWidth(100);
        discardButton.setOnAction(e -> {
            recoverClicked = false;
            dialog.close();
        });

        Button recoverButton = new Button(lang().getString("dialog.ok"));
        recoverButton.setPrefWidth(100);
        recoverButton.setDefaultButton(true);
        recoverButton.setStyle("-fx-background-color: #2196f3; -fx-text-fill: white;");
        recoverButton.setOnAction(e -> {
            if (selectedFile != null) {
                recoverClicked = true;
                dialog.close();
            }
        });

        buttonBox.getChildren().addAll(discardButton, recoverButton);
        return buttonBox;
    }

    public boolean showAndWait() {
        dialog.showAndWait();
        return recoverClicked;
    }

    public File getSelectedFile() {
        return selectedFile;
    }
}
