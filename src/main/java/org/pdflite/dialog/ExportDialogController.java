package org.pdflite.dialog;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Controller for the export dialog.
 * Allows users to export PDF to images or text.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class ExportDialogController {
    private static final Logger logger = LoggerFactory.getLogger(ExportDialogController.class);

    @FXML
    private ToggleGroup exportTypeGroup;
    @FXML
    private RadioButton imageRadio;
    @FXML
    private RadioButton textRadio;

    @FXML
    private VBox imageOptionsPane;
    @FXML
    private ComboBox<String> imageFormatComboBox;
    @FXML
    private Spinner<Integer> dpiSpinner;

    @FXML
    private VBox textOptionsPane;

    @FXML
    private ToggleGroup pageRangeGroup;
    @FXML
    private RadioButton currentPageRadio;
    @FXML
    private RadioButton allPagesRadio;
    @FXML
    private RadioButton specificPagesRadio;
    @FXML
    private TextField pageRangeField;

    @FXML
    private TextField outputPathField;
    @FXML
    private Button browseButton;

    @FXML
    private Label infoLabel;

    private Stage dialogStage;
    private boolean exportClicked = false;
    private int totalPages;
    private ExportConfig config;

    @FXML
    private void initialize() {
        logger.debug("ExportDialogController initialized");

        config = new ExportConfig();

        // Setup image format combo
        imageFormatComboBox.getItems().addAll("PNG", "JPG");
        imageFormatComboBox.setValue("PNG");

        // Setup DPI spinner
        SpinnerValueFactory<Integer> dpiFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(72, 600, 300, 50);
        dpiSpinner.setValueFactory(dpiFactory);

        setupListeners();
    }

    private void setupListeners() {
        // Export type toggle
        exportTypeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == imageRadio) {
                imageOptionsPane.setVisible(true);
                imageOptionsPane.setManaged(true);
                textOptionsPane.setVisible(false);
                textOptionsPane.setManaged(false);
                updateInfoLabel();
            } else {
                imageOptionsPane.setVisible(false);
                imageOptionsPane.setManaged(false);
                textOptionsPane.setVisible(true);
                textOptionsPane.setManaged(true);
                updateInfoLabel();
            }
        });

        // Page range toggle
        pageRangeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            pageRangeField.setDisable(newVal != specificPagesRadio);
            updateInfoLabel();
        });

        // Update info when format changes
        imageFormatComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateInfoLabel());
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
        // Title bar will be added by ExportManager wrapping the FXML content
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
        updateInfoLabel();
    }

    public boolean isExportClicked() {
        return exportClicked;
    }

    public ExportConfig getConfig() {
        return config;
    }

    @FXML
    private void handleBrowse() {
        if (imageRadio.isSelected() && allPagesRadio.isSelected()) {
            // Multiple images - choose directory
            DirectoryChooser dirChooser = new DirectoryChooser();
            dirChooser.setTitle("Select Output Directory");
            File dir = dirChooser.showDialog(dialogStage);
            if (dir != null) {
                outputPathField.setText(dir.getAbsolutePath());
            }
        } else {
            // Single file - choose file
            FileChooser fileChooser = new FileChooser();

            if (imageRadio.isSelected()) {
                fileChooser.setTitle("Save Image As");
                String format = imageFormatComboBox.getValue().toLowerCase();
                fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter(format.toUpperCase() + " Image", "*." + format)
                );
            } else {
                fileChooser.setTitle("Save Text As");
                fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("Text File", "*.txt")
                );
            }

            File file = fileChooser.showSaveDialog(dialogStage);
            if (file != null) {
                outputPathField.setText(file.getAbsolutePath());
            }
        }
    }

    @FXML
    private void handleExport() {
        if (!validateInput()) {
            return;
        }

        buildConfig();
        exportClicked = true;
        dialogStage.close();
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    private boolean validateInput() {
        if (outputPathField.getText() == null || outputPathField.getText().trim().isEmpty()) {
            showError("Please select an output location.");
            return false;
        }

        if (specificPagesRadio.isSelected()) {
            String pageRange = pageRangeField.getText();
            if (pageRange == null || pageRange.trim().isEmpty()) {
                showError("Please enter page range.");
                return false;
            }
            if (!pageRange.matches("[0-9,\\-\\s]+")) {
                showError("Invalid page range format. Use format like: 1-3,5,7-9");
                return false;
            }
        }

        return true;
    }

    private void buildConfig() {
        config.exportToImage = imageRadio.isSelected();
        config.outputPath = outputPathField.getText();

        if (imageRadio.isSelected()) {
            config.imageFormat = imageFormatComboBox.getValue();
            config.dpi = dpiSpinner.getValue();
        }

        if (currentPageRadio.isSelected()) {
            config.pageRange = PageRange.CURRENT;
        } else if (allPagesRadio.isSelected()) {
            config.pageRange = PageRange.ALL;
        } else {
            config.pageRange = PageRange.SPECIFIC;
            config.specificPages = pageRangeField.getText();
        }
    }

    private void updateInfoLabel() {
        StringBuilder info = new StringBuilder();

        if (imageRadio.isSelected()) {
            if (allPagesRadio.isSelected()) {
                info.append("Will export ").append(totalPages).append(" images to a folder");
            } else if (currentPageRadio.isSelected()) {
                info.append("Will export 1 image");
            } else {
                info.append("Will export selected pages as images");
            }
        } else {
            if (allPagesRadio.isSelected()) {
                info.append("Will export all text to one file");
            } else if (currentPageRadio.isSelected()) {
                info.append("Will export current page text");
            } else {
                info.append("Will export selected pages text");
            }
        }

        infoLabel.setText(info.toString());
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Validation Error");
        alert.setHeaderText("Invalid Input");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public enum PageRange {
        CURRENT, ALL, SPECIFIC
    }

    public static class ExportConfig {
        public boolean exportToImage;
        public String imageFormat;
        public int dpi;
        public PageRange pageRange;
        public String specificPages;
        public String outputPath;
    }
}
