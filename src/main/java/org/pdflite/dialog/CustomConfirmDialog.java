package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pdflite.manager.ThemeManager;
import org.pdflite.util.DialogTitleBar;

/**
 * Custom confirmation dialog with custom title bar.
 */
public class CustomConfirmDialog {
    
    private Stage dialogStage;
    private boolean confirmed = false;
    
    /**
     * Shows a confirmation dialog.
     *
     * @param title       the dialog title
     * @param header      the header text
     * @param content     the content text
     * @param themeManager the theme manager (can be null)
     * @return true if user clicked OK, false otherwise
     */
    public static boolean show(String title, String header, String content, ThemeManager themeManager) {
        CustomConfirmDialog dialog = new CustomConfirmDialog();
        return dialog.showAndWait(title, header, content, themeManager);
    }
    
    private boolean showAndWait(String title, String header, String content, ThemeManager themeManager) {
        dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.TRANSPARENT);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle(title);
        
        // Create main container
        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("custom-confirm-dialog");
        
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
        
        // Content label
        Label contentLabel = new Label(content);
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(400);
        contentBox.getChildren().add(contentLabel);
        
        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        
        Button cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(80);
        cancelButton.setOnAction(e -> {
            confirmed = false;
            dialogStage.close();
        });
        
        Button okButton = new Button("OK");
        okButton.setPrefWidth(80);
        okButton.getStyleClass().add("primary-button");
        okButton.setOnAction(e -> {
            confirmed = true;
            dialogStage.close();
        });
        
        buttonBox.getChildren().addAll(cancelButton, okButton);
        contentBox.getChildren().add(buttonBox);
        
        mainContainer.getChildren().add(contentBox);
        
        Scene scene = new Scene(mainContainer);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialogStage.setScene(scene);
        
        // Don't set fixed min height, let it size to content
        dialogStage.setMinWidth(450);
        
        // Apply theme if available
        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }
        
        dialogStage.showAndWait();
        return confirmed;
    }
}
