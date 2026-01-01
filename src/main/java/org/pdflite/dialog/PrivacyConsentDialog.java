package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
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
 * Privacy consent dialog for AI features.
 * Shows warning about data being sent to Groq API.
 */
public class PrivacyConsentDialog {

    private Stage dialogStage;
    private boolean accepted = false;
    private final LanguageManager lang = LanguageManager.getInstance();

    /**
     * Shows the privacy consent dialog.
     *
     * @param themeManager the theme manager (can be null)
     * @return true if user accepted, false otherwise
     */
    public static boolean show(ThemeManager themeManager) {
        PrivacyConsentDialog dialog = new PrivacyConsentDialog();
        return dialog.showAndWait(themeManager);
    }

    private boolean showAndWait(ThemeManager themeManager) {
        dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.TRANSPARENT);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle(lang.getString("privacy.title"));

        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("custom-confirm-dialog");

        // Custom title bar
        DialogTitleBar titleBar = new DialogTitleBar(lang.getString("privacy.title"), dialogStage);
        mainContainer.getChildren().add(titleBar.getTitleBar());

        // Content
        VBox contentBox = new VBox(15);
        contentBox.setPadding(new Insets(20));
        contentBox.setAlignment(Pos.TOP_LEFT);

        // Warning icon and header
        SVGPath warningIcon = new SVGPath();
        warningIcon.setContent("M12 2L1 21h22L12 2zm0 3.5L20.5 19h-17L12 5.5zM11 10v4h2v-4h-2zm0 6v2h2v-2h-2z");
        warningIcon.setFill(Color.web("#f0ad4e"));
        warningIcon.setScaleX(0.9);
        warningIcon.setScaleY(0.9);

        Label headerLabel = new Label(lang.getString("privacy.header"));
        headerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        HBox headerBox = new HBox(12);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.getChildren().addAll(warningIcon, headerLabel);
        contentBox.getChildren().add(headerBox);

        // Description
        Label descLabel = new Label(lang.getString("privacy.description"));
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(450);
        contentBox.getChildren().add(descLabel);

        // Privacy policy link
        Hyperlink privacyLink = new Hyperlink(lang.getString("privacy.link"));
        privacyLink.setFocusTraversable(false);
        privacyLink.setStyle("-fx-border-color: transparent;");
        privacyLink.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(
                    new java.net.URI("https://groq.com/privacy-policy")
                );
            } catch (Exception ex) {
                // Ignore
            }
        });
        contentBox.getChildren().add(privacyLink);

        // Checkbox
        CheckBox consentCheckbox = new CheckBox(lang.getString("privacy.consent"));
        consentCheckbox.setWrapText(true);
        contentBox.getChildren().add(consentCheckbox);

        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(15, 0, 0, 0));

        Button cancelButton = new Button(lang.getString("privacy.decline"));
        cancelButton.setPrefWidth(100);
        cancelButton.setOnAction(e -> {
            accepted = false;
            dialogStage.close();
        });

        Button acceptButton = new Button(lang.getString("privacy.accept"));
        acceptButton.setPrefWidth(100);
        acceptButton.setDisable(true);
        acceptButton.setDefaultButton(true);
        acceptButton.setOnAction(e -> {
            accepted = true;
            dialogStage.close();
        });

        // Enable accept button only when checkbox is checked
        consentCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> acceptButton.setDisable(!newVal));

        buttonBox.getChildren().addAll(cancelButton, acceptButton);
        contentBox.getChildren().add(buttonBox);

        mainContainer.getChildren().add(contentBox);

        Scene scene = new Scene(mainContainer);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialogStage.setScene(scene);

        dialogStage.setMinWidth(500);
        dialogStage.setMinHeight(350);

        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }

        dialogStage.showAndWait();
        return accepted;
    }
}
