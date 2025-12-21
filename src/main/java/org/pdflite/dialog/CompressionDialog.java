package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pdflite.manager.CompressionManager;
import org.pdflite.manager.ThemeManager;
import org.pdflite.util.DialogTitleBar;

/**
 * Dialog for PDF compression settings.
 */
public class CompressionDialog {

    private final Stage dialog;
    private CompressionManager.CompressionLevel selectedLevel = CompressionManager.CompressionLevel.MEDIUM;
    private boolean confirmed = false;
    private Label estimateLabel;
    private final org.pdflite.model.PDFDocument pdfDocument;
    private final CompressionManager compressionManager;

    public CompressionDialog(long currentSize, int estimatedReduction, ThemeManager themeManager,
                             org.pdflite.model.PDFDocument pdfDocument, CompressionManager compressionManager) {
        this.pdfDocument = pdfDocument;
        this.compressionManager = compressionManager;
        dialog = new Stage();
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Optimize PDF");

        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("custom-confirm-dialog");

        // Title bar
        DialogTitleBar titleBar = new DialogTitleBar("Optimize PDF", dialog);
        mainContainer.getChildren().add(titleBar.getTitleBar());

        // Content
        VBox content = createContent(currentSize, estimatedReduction);
        mainContainer.getChildren().add(content);

        // Buttons
        HBox buttonBox = createButtonBox();
        mainContainer.getChildren().add(buttonBox);

        Scene scene = new Scene(mainContainer);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);

        dialog.setMinWidth(450);
        dialog.setMinHeight(350);

        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }
    }

    private VBox createContent(long currentSize, int estimatedReduction) {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_LEFT);

        // Info label
        Label infoLabel = new Label("Reduce PDF file size by compressing images.");
        infoLabel.setWrapText(true);
        infoLabel.setMaxWidth(400);

        // Current size
        Label sizeLabel = new Label(String.format("Current size: %.2f MB", currentSize / 1024.0 / 1024.0));
        sizeLabel.setStyle("-fx-font-weight: bold;");

        // Compression level selection
        Label levelLabel = new Label("Compression level:");
        levelLabel.setStyle("-fx-font-weight: bold;");

        ToggleGroup group = new ToggleGroup();
        VBox radioBox = new VBox(8);

        for (CompressionManager.CompressionLevel level : CompressionManager.CompressionLevel.values()) {
            RadioButton radio = new RadioButton(level.getDescription());
            radio.setToggleGroup(group);
            radio.setUserData(level);

            if (level == CompressionManager.CompressionLevel.MEDIUM) {
                radio.setSelected(true);
            }

            radioBox.getChildren().add(radio);
        }

        group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedLevel = (CompressionManager.CompressionLevel) newVal.getUserData();
                updateEstimate();
            }
        });

        // Warning
        javafx.scene.shape.SVGPath warningIcon = new javafx.scene.shape.SVGPath();
        warningIcon.setContent("m130-172 350-604 350 604H130Zm48-28h604L480-720 178-200Zm302-60q8.5 0 14.25-5.75T500-280q0-8.5-5.75-14.25T480-300q-8.5 0-14.25 5.75T460-280q0 8.5 5.75 14.25T480-260Zm-14-80h28v-200h-28v200Zm14-120Z");
        warningIcon.setFill(javafx.scene.paint.Color.web("#ff9800"));

        // Set fixed size for icon
        javafx.scene.layout.StackPane iconContainer = new javafx.scene.layout.StackPane(warningIcon);
        iconContainer.setMinSize(16, 16);
        iconContainer.setMaxSize(16, 16);
        iconContainer.setPrefSize(16, 16);

        // Scale to fit container
        double scale = 16.0 / 960.0; // SVG viewBox is 960x960
        warningIcon.setScaleX(scale);
        warningIcon.setScaleY(scale);

        Label warningLabel = new Label("Higher compression may reduce image quality. " +
                "This operation cannot be undone.");
        warningLabel.setWrapText(true);
        warningLabel.setMaxWidth(360);
        warningLabel.setStyle("-fx-text-fill: #ff9800; -fx-font-size: 11px;");

        javafx.scene.layout.HBox warningBox = new javafx.scene.layout.HBox(8);
        warningBox.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        warningBox.getChildren().addAll(iconContainer, warningLabel);

        // Estimated reduction
        estimateLabel = new Label(
                String.format("Estimated size reduction: ~%d%%", estimatedReduction)
        );
        estimateLabel.setStyle("-fx-text-fill: #4caf50; -fx-font-weight: bold;");

        content.getChildren().addAll(estimateLabel, infoLabel, sizeLabel, levelLabel, radioBox, warningBox);
        return content;
    }

    /**
     * Updates the estimate label when compression level changes.
     */
    private void updateEstimate() {
        if (estimateLabel != null && compressionManager != null && pdfDocument != null) {
            int newEstimate = compressionManager.estimateCompression(pdfDocument, selectedLevel);
            estimateLabel.setText(String.format("Estimated size reduction: ~%d%%", newEstimate));
        }
    }

    private HBox createButtonBox() {
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(0, 20, 20, 20));

        Button cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(100);
        cancelButton.setOnAction(e -> {
            confirmed = false;
            dialog.close();
        });

        Button optimizeButton = new Button("Optimize");
        optimizeButton.setPrefWidth(100);
        optimizeButton.setDefaultButton(true);
        optimizeButton.setStyle("-fx-background-color: #2196f3; -fx-text-fill: white;");
        optimizeButton.setOnAction(e -> {
            confirmed = true;
            dialog.close();
        });

        buttonBox.getChildren().addAll(cancelButton, optimizeButton);
        return buttonBox;
    }

    /**
     * Shows the dialog and waits for user input.
     *
     * @return true if Optimize was clicked, false otherwise
     */
    public boolean showAndWait() {
        dialog.showAndWait();
        return confirmed;
    }

    /**
     * Gets the selected compression level.
     *
     * @return the selected compression level
     */
    public CompressionManager.CompressionLevel getSelectedLevel() {
        return selectedLevel;
    }
}
