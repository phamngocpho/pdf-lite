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
 * Custom information dialog with custom title bar.
 */
public class CustomInfoDialog {
    
    private Stage dialogStage;
    
    /**
     * Shows an information dialog.
     *
     * @param title       the dialog title
     * @param header      the header text
     * @param content     the content text
     * @param themeManager the theme manager (can be null)
     */
    public static void show(String title, String header, String content, ThemeManager themeManager) {
        CustomInfoDialog dialog = new CustomInfoDialog();
        dialog.showAndWait(title, header, content, themeManager);
    }
    
    private void showAndWait(String title, String header, String content, ThemeManager themeManager) {
        dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.TRANSPARENT);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle(title);
        
        // Create main container
        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("custom-info-dialog");
        
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
        
        // Content - use Label for better styling
        Label contentLabel = new Label(content);
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(450);
        contentLabel.getStyleClass().add("info-content-label");
        contentBox.getChildren().add(contentLabel);
        
        // Button
        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        
        Button okButton = new Button("OK");
        okButton.setPrefWidth(80);
        okButton.getStyleClass().add("primary-button");
        okButton.setOnAction(e -> dialogStage.close());
        
        buttonBox.getChildren().add(okButton);
        contentBox.getChildren().add(buttonBox);
        
        mainContainer.getChildren().add(contentBox);
        
        Scene scene = new Scene(mainContainer);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialogStage.setScene(scene);
        
        // Don't set fixed min height, let it size to content
        dialogStage.setMinWidth(500);
        
        // Apply theme if available
        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }
        
        dialogStage.showAndWait();
    }
}
