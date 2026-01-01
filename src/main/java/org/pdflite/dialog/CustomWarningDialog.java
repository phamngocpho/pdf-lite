package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pdflite.manager.LanguageManager;
import org.pdflite.manager.ThemeManager;
import org.pdflite.util.DialogTitleBar;

/**
 * Custom warning dialog with a custom title bar and warning icon.
 */
public class CustomWarningDialog {

    private Stage dialogStage;

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    /**
     * Shows a warning dialog.
     *
     * @param title        the dialog title
     * @param header       the header text
     * @param content      the content text
     * @param themeManager the theme manager (can be null)
     */
    public static void show(String title, String header, String content, ThemeManager themeManager) {
        CustomWarningDialog dialog = new CustomWarningDialog();
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

        // Header with warning icon
        if (header != null && !header.isEmpty()) {
            HBox headerBox = new HBox(12);
            headerBox.setAlignment(Pos.CENTER_LEFT);

            // Warning icon (triangle with exclamation)
            SVGPath warningIcon = createWarningIcon();
            
            Label headerLabel = new Label(header);
            headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            
            headerBox.getChildren().addAll(warningIcon, headerLabel);
            contentBox.getChildren().add(headerBox);
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

        Button okButton = new Button(lang().getString("dialog.ok"));
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

    /**
     * Creates a warning triangle icon using SVG path.
     */
    private SVGPath createWarningIcon() {
        SVGPath icon = new SVGPath();
        // Warning triangle path
        icon.setContent("M12 2L1 21h22L12 2zm0 3.5L20.5 19h-17L12 5.5zM11 10v4h2v-4h-2zm0 6v2h2v-2h-2z");
        icon.setFill(Color.web("#f0ad4e")); // Warning orange/yellow color
        icon.setScaleX(0.9);
        icon.setScaleY(0.9);
        return icon;
    }
}
