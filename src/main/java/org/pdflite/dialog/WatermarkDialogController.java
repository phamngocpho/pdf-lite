package org.pdflite.dialog;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.pdflite.model.WatermarkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Controller for the watermark dialog.
 * Allows users to configure and apply watermarks to PDF documents.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class WatermarkDialogController {
    private static final Logger logger = LoggerFactory.getLogger(WatermarkDialogController.class);

    @FXML
    private ToggleGroup typeToggleGroup;
    @FXML
    private RadioButton textRadio;
    @FXML
    private RadioButton imageRadio;

    @FXML
    private VBox textOptionsPane;
    @FXML
    private TextField textField;
    @FXML
    private ComboBox<String> fontComboBox;
    @FXML
    private Spinner<Integer> fontSizeSpinner;
    @FXML
    private ColorPicker colorPicker;

    @FXML
    private VBox imageOptionsPane;
    @FXML
    private TextField imageFileField;
    @FXML
    private Slider scaleSlider;
    @FXML
    private Label scaleLabel;

    @FXML
    private ComboBox<String> positionComboBox;
    @FXML
    private HBox customPositionPane;
    @FXML
    private TextField customXField;
    @FXML
    private TextField customYField;

    @FXML
    private Slider opacitySlider;
    @FXML
    private Label opacityLabel;
    @FXML
    private Slider rotationSlider;
    @FXML
    private Label rotationLabel;

    @FXML
    private ToggleGroup pageRangeToggleGroup;
    @FXML
    private RadioButton allPagesRadio;
    @FXML
    private RadioButton specificPagesRadio;
    @FXML
    private TextField pageRangeField;

    private Stage dialogStage;
    private boolean applyClicked = false;
    private WatermarkConfig config;
    private File selectedImageFile;

    @FXML
    private void initialize() {
        logger.debug("WatermarkDialogController initialized");

        config = new WatermarkConfig();

        // Setup font combo box
        fontComboBox.getItems().addAll(
                "Helvetica",
                "Helvetica-Bold",
                "Times-Roman",
                "Times-Bold",
                "Courier",
                "Courier-Bold"
        );
        fontComboBox.setValue("Helvetica");

        // Setup font size spinner
        SpinnerValueFactory<Integer> fontSizeFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(8, 200, 72);
        fontSizeSpinner.setValueFactory(fontSizeFactory);

        // Setup position combo box
        for (WatermarkConfig.Position pos : WatermarkConfig.Position.values()) {
            positionComboBox.getItems().add(pos.getDisplayName());
        }
        positionComboBox.setValue(WatermarkConfig.Position.CENTER.getDisplayName());

        // Setup color picker
        colorPicker.setValue(javafx.scene.paint.Color.GRAY);

        // Setup text field
        textField.setText("CONFIDENTIAL");

        // Add listeners
        setupListeners();
    }

    private void setupListeners() {
        // Type toggle listener
        typeToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == textRadio) {
                textOptionsPane.setVisible(true);
                textOptionsPane.setManaged(true);
                imageOptionsPane.setVisible(false);
                imageOptionsPane.setManaged(false);
            } else {
                textOptionsPane.setVisible(false);
                textOptionsPane.setManaged(false);
                imageOptionsPane.setVisible(true);
                imageOptionsPane.setManaged(true);
            }
        });

        // Position combo box listener
        positionComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isCustom = newVal != null && newVal.equals(WatermarkConfig.Position.CUSTOM.getDisplayName());
            customPositionPane.setVisible(isCustom);
            customPositionPane.setManaged(isCustom);
        });

        // Opacity slider listener
        opacitySlider.valueProperty().addListener((obs, oldVal, newVal) -> opacityLabel.setText(String.format("%.0f%%", newVal.doubleValue() * 100)));

        // Rotation slider listener
        rotationSlider.valueProperty().addListener((obs, oldVal, newVal) -> rotationLabel.setText(String.format("%.0f°", newVal.doubleValue())));

        // Scale slider listener
        scaleSlider.valueProperty().addListener((obs, oldVal, newVal) -> scaleLabel.setText(String.format("%.0f%%", newVal.doubleValue() * 100)));

        // Page range toggle listener
        pageRangeToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> pageRangeField.setDisable(newVal == allPagesRadio));
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public boolean isApplyClicked() {
        return applyClicked;
    }

    public WatermarkConfig getConfig() {
        return config;
    }

    @FXML
    private void handleBrowseImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Watermark Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(dialogStage);
        if (file != null) {
            selectedImageFile = file;
            imageFileField.setText(file.getName());
        }
    }

    @FXML
    private void handlePreview() {
        // TODO: Implement preview functionality
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Preview");
        alert.setHeaderText("Preview Feature");
        alert.setContentText("Preview functionality will be implemented in a future version.");
        alert.showAndWait();
    }

    @FXML
    private void handleApply() {
        if (!validateInput()) {
            return;
        }

        buildConfig();
        applyClicked = true;
        dialogStage.close();
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    private boolean validateInput() {
        if (textRadio.isSelected()) {
            if (textField.getText() == null || textField.getText().trim().isEmpty()) {
                showError("Please enter watermark text.");
                return false;
            }
        } else {
            if (selectedImageFile == null) {
                showError("Please select an image file.");
                return false;
            }
        }

        if (specificPagesRadio.isSelected()) {
            String pageRange = pageRangeField.getText();
            if (pageRange == null || pageRange.trim().isEmpty()) {
                showError("Please enter page range.");
                return false;
            }
            // Basic validation of page range format
            if (!pageRange.matches("[0-9,\\-\\s]+")) {
                showError("Invalid page range format. Use format like: 1-3,5,7-9");
                return false;
            }
        }

        return true;
    }

    private void buildConfig() {
        // Set type
        config.setType(textRadio.isSelected() ?
                WatermarkConfig.WatermarkType.TEXT : WatermarkConfig.WatermarkType.IMAGE);

        // Set text options
        if (textRadio.isSelected()) {
            config.setText(textField.getText());
            config.setFontName(fontComboBox.getValue());
            config.setFontSize(fontSizeSpinner.getValue());

            javafx.scene.paint.Color fxColor = colorPicker.getValue();
            config.setColor(new java.awt.Color(
                    (float) fxColor.getRed(),
                    (float) fxColor.getGreen(),
                    (float) fxColor.getBlue(),
                    (float) fxColor.getOpacity()
            ));
        } else {
            config.setImageFile(selectedImageFile);
            config.setScale((float) scaleSlider.getValue());
        }

        // Set position
        String positionStr = positionComboBox.getValue();
        for (WatermarkConfig.Position pos : WatermarkConfig.Position.values()) {
            if (pos.getDisplayName().equals(positionStr)) {
                config.setPosition(pos);
                break;
            }
        }

        // Set custom position if applicable
        if (config.getPosition() == WatermarkConfig.Position.CUSTOM) {
            try {
                config.setCustomX(Float.parseFloat(customXField.getText()));
                config.setCustomY(Float.parseFloat(customYField.getText()));
            } catch (NumberFormatException e) {
                config.setCustomX(0);
                config.setCustomY(0);
            }
        }

        // Set opacity and rotation
        config.setOpacity((float) opacitySlider.getValue());
        config.setRotation((float) rotationSlider.getValue());

        // Set page range
        config.setApplyToAllPages(allPagesRadio.isSelected());
        if (specificPagesRadio.isSelected()) {
            config.setPageRange(pageRangeField.getText());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Validation Error");
        alert.setHeaderText("Invalid Input");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
