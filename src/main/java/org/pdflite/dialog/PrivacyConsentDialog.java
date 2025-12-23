package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pdflite.manager.ThemeManager;
import org.pdflite.util.DialogTitleBar;

/**
 * Privacy consent dialog for AI features.
 * Shows warning about data being sent to Groq API.
 */
public class PrivacyConsentDialog {

    private Stage dialogStage;
    private boolean accepted = false;

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
        dialogStage.setTitle("Privacy Notice");

        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("custom-confirm-dialog");

        // Custom title bar
        DialogTitleBar titleBar = new DialogTitleBar("Privacy Notice - AI Features", dialogStage);
        mainContainer.getChildren().add(titleBar.getTitleBar());

        // Content
        VBox contentBox = new VBox(15);
        contentBox.setPadding(new Insets(20));
        contentBox.setAlignment(Pos.TOP_LEFT);

        // Warning icon and header
        SVGPath warningIcon = new SVGPath();
        warningIcon.setContent("m130-172 350-604 350 604H130Zm48-28h604L480-720 178-200Zm302-60q8.5 0 14.25-5.75T500-280q0-8.5-5.75-14.25T480-300q-8.5 0-14.25 5.75T460-280q0 8.5 5.75 14.25T480-260Zm-14-80h28v-200h-28v200Zm14-120Z");
        warningIcon.setFill(Color.web("#ff9800"));
        
        StackPane iconContainer = new StackPane(warningIcon);
        iconContainer.setMinSize(20, 20);
        iconContainer.setMaxSize(20, 20);
        double scale = 20.0 / 960.0;
        warningIcon.setScaleX(scale);
        warningIcon.setScaleY(scale);

        Label headerLabel = new Label("Data Privacy Warning");
        headerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        HBox headerBox = new HBox(8);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.getChildren().addAll(iconContainer, headerLabel);
        contentBox.getChildren().add(headerBox);

        // Description
        Label descLabel = new Label(
            "When using AI features (summarize, read text, etc.), your PDF content " +
            "will be sent to Groq's servers for processing.\n\n" +
            "This includes:\n" +
            "• Text content from selected pages\n" +
            "• Your chat messages with the AI assistant\n\n" +
            "Groq may process and store this data according to their privacy policy."
        );
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(450);
        contentBox.getChildren().add(descLabel);

        // Privacy policy link
        Hyperlink privacyLink = new Hyperlink("View Groq Privacy Policy");
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
        CheckBox consentCheckbox = new CheckBox("I understand and agree to send data to Groq API");
        consentCheckbox.setWrapText(true);
        contentBox.getChildren().add(consentCheckbox);

        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(15, 0, 0, 0));

        Button cancelButton = new Button("Decline");
        cancelButton.setPrefWidth(100);
        cancelButton.setOnAction(e -> {
            accepted = false;
            dialogStage.close();
        });

        Button acceptButton = new Button("Accept");
        acceptButton.setPrefWidth(100);
        acceptButton.setDisable(true);
        acceptButton.setDefaultButton(true);
        acceptButton.setOnAction(e -> {
            accepted = true;
            dialogStage.close();
        });

        // Enable accept button only when checkbox is checked
        consentCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            acceptButton.setDisable(!newVal);
        });

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
