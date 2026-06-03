package org.pdflite.dialog;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.pdflite.manager.ImageManager;
import org.pdflite.manager.LanguageManager;
import org.pdflite.model.ImagePlacement;
import org.pdflite.util.DialogTitleBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Controller for the image placement dialog.
 * <p>
 * This dialog allows users to select an image file and specify where
 * to place it on a PDF page, including position, size, and whether
 * it should be inserted as a rubber stamp annotation.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class ImagePlacementDialogController {
    private static final Logger logger = LoggerFactory.getLogger(ImagePlacementDialogController.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    @FXML
    private HBox dialogTitleBar;

    @FXML
    private TextField imageFileField;

    @FXML
    private Label dimensionsLabel;

    @FXML
    private Spinner<Integer> pageSpinner;

    @FXML
    private Label totalPagesLabel;

    @FXML
    private TextField xField;

    @FXML
    private TextField yField;

    @FXML
    private TextField widthField;

    @FXML
    private TextField heightField;

    @FXML
    private CheckBox maintainAspectRatioCheckBox;

    @FXML
    private CheckBox isStampCheckBox;

    @FXML
    private CheckBox topLeftOriginCheckBox;

    private Stage dialogStage;
    private boolean insertClicked = false;
    private File selectedImageFile;
    private ImagePlacement resultPlacement;
    private ImageManager imageManager;
    private int[] originalImageDimensions;
    private double currentPageHeight = 792.0; // Default US Letter height in points

    /**
     * Initializes the controller.
     */
    @FXML
    private void initialize() {
        logger.debug("ImagePlacementDialogController initialized");

        // Add listeners for aspect ratio maintenance
        widthField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (maintainAspectRatioCheckBox.isSelected() && originalImageDimensions != null) {
                updateHeightFromWidth();
            }
        });

        heightField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (maintainAspectRatioCheckBox.isSelected() && originalImageDimensions != null) {
                updateWidthFromHeight();
            }
        });
    }

    /**
     * Sets the dialog stage.
     *
     * @param dialogStage the dialog stage
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
        this.stageTitleFallback(dialogStage);
        // Copy children from the title bar to dialogTitleBar HBox
        dialogTitleBar.getChildren().setAll(titleBar.getTitleBar().getChildren());
        
        // Update all UI text
        updateAllUIText();
    }

    private DialogTitleBar titleBar;

    private void stageTitleFallback(Stage dialogStage) {
        String title = dialogStage.getTitle();
        if (title == null || title.isEmpty()) {
            title = lang().getString("imagePlacement.title");
        }
        titleBar = new DialogTitleBar(title, dialogStage);
    }
    
    /**
     * Updates all UI text elements with current language.
     */
    private void updateAllUIText() {
        if (dialogStage == null || dialogStage.getScene() == null) {
            return;
        }
        
        // Recursively update all Labels and Buttons in the scene
        updateNodeText(dialogStage.getScene().getRoot());
    }
    
    /**
     * Recursively updates text for Labels and Buttons.
     */
    private void updateNodeText(javafx.scene.Node node) {
        if (node instanceof Label label) {
            String text = label.getText();
            if (text != null && !text.isEmpty()) {
                // Map English text to property keys
                switch (text) {
                    case "Image File:" -> label.setText(lang().getString("imagePlacement.browse") + ":");
                    case "Dimensions:" -> label.setText(lang().getString("imagePlacement.dimensions") + ":");
                    case "Page:" -> label.setText(lang().getString("imagePlacement.page") + ":");
                    case "Position (in points):" -> label.setText(lang().getString("imagePlacement.position") + ":");
                    case "X:" -> label.setText("X:");
                    case "Y:" -> label.setText("Y:");
                    case "Size (in points):" -> label.setText(lang().getString("imagePlacement.size") + ":");
                    case "Width:" -> label.setText(lang().getString("watermark.width") + ":");
                    case "Height:" -> label.setText(lang().getString("watermark.height") + ":");
                }
            }
        } else if (node instanceof Button button) {
            String text = button.getText();
            if (text != null && !text.isEmpty()) {
                switch (text) {
                    case "Browse" -> button.setText(lang().getString("imagePlacement.browse"));
                    case "Insert" -> button.setText(lang().getString("imagePlacement.insert"));
                    case "Cancel" -> button.setText(lang().getString("imagePlacement.cancel"));
                }
            }
        } else if (node instanceof CheckBox checkBox) {
            String text = checkBox.getText();
            if (text != null && !text.isEmpty()) {
                switch (text) {
                    case "Maintain aspect ratio" -> checkBox.setText(lang().getString("imagePlacement.maintainAspect"));
                    case "Insert as rubber stamp annotation" -> checkBox.setText(lang().getString("imagePlacement.asStamp"));
                    case "Use top-left origin (Y=0 at top)" -> checkBox.setText(lang().getString("imagePlacement.topLeftOrigin"));
                }
            }
        }
        
        // Recursively process children
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                updateNodeText(child);
            }
        }
    }

    /**
     * Sets the image manager.
     *
     * @param imageManager the image manager
     */
    public void setImageManager(ImageManager imageManager) {
        this.imageManager = imageManager;
    }

    /**
     * Sets the total number of pages in the document.
     *
     * @param totalPages the total pages
     */
    public void setTotalPages(int totalPages) {
        totalPagesLabel.setText("/ " + totalPages);

        // Setup page spinner
        SpinnerValueFactory<Integer> valueFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, totalPages, 1);
        pageSpinner.setValueFactory(valueFactory);
    }

    /**
     * Sets the current page height for coordinate conversion.
     *
     * @param pageHeight the page height in points
     */
    public void setPageHeight(double pageHeight) {
        this.currentPageHeight = pageHeight;
    }

    /**
     * Sets the default page to insert on.
     *
     * @param pageNumber the page number (1-based)
     */
    public void setDefaultPage(int pageNumber) {
        if (pageSpinner.getValueFactory() != null) {
            pageSpinner.getValueFactory().setValue(pageNumber);
        }
    }

    /**
     * Sets whether the stamp checkbox is selected by default.
     *
     * @param isStamp true to check the stamp checkbox, false otherwise
     */
    public void setStampDefault(boolean isStamp) {
        if (isStampCheckBox != null) {
            isStampCheckBox.setSelected(isStamp);
        }
    }

    /**
     * Returns whether the insert button was clicked.
     *
     * @return true if insert was clicked, false otherwise
     */
    public boolean isInsertClicked() {
        return insertClicked;
    }

    /**
     * Returns the resulting image placement.
     *
     * @return the image placement, or null if canceled
     */
    public ImagePlacement getResultPlacement() {
        return resultPlacement;
    }

    /**
     * Handles the browse button action.
     */
    @FXML
    private void handleBrowse() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(lang().getString("imagePlacement.selectImage"));

        // Add file filters
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("PNG Files", "*.png"),
                new FileChooser.ExtensionFilter("JPEG Files", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(dialogStage);
        if (file != null) {
            selectImageFile(file);
        }
    }

    /**
     * Selects an image file and updates the UI.
     *
     * @param file the image file
     */
    private void selectImageFile(File file) {
        if (imageManager == null) {
            showError(lang().getString("error.title"), lang().getString("imagePlacement.error.noManager"));
            return;
        }

        if (!imageManager.validateImageFile(file)) {
            showError(lang().getString("error.title"), lang().getString("imagePlacement.error.invalidImage"));
            return;
        }

        selectedImageFile = file;
        imageFileField.setText(file.getName());

        // Get and display image dimensions
        originalImageDimensions = imageManager.getImageDimensions(file);
        if (originalImageDimensions != null) {
            dimensionsLabel.setText(originalImageDimensions[0] + " x " + originalImageDimensions[1] + " pixels");

            // Set default size to image dimensions (or scaled down if too large)
            double maxSize = 200;
            double[] scaled = imageManager.calculateScaledDimensions(
                    originalImageDimensions[0], originalImageDimensions[1], maxSize, maxSize);

            widthField.setText(String.format("%.0f", scaled[0]));
            heightField.setText(String.format("%.0f", scaled[1]));
        } else {
            dimensionsLabel.setText(lang().getString("imagePlacement.unableToDimensions"));
        }

        logger.debug("Selected image file: {}", file.getName());
    }

    /**
     * Updates height field based on width while maintaining aspect ratio.
     */
    private void updateHeightFromWidth() {
        if (originalImageDimensions == null) return;

        try {
            double width = Double.parseDouble(widthField.getText());
            double aspectRatio = (double) originalImageDimensions[0] / originalImageDimensions[1];
            double height = width / aspectRatio;
            heightField.setText(String.format("%.0f", height));
        } catch (NumberFormatException e) {
            // Ignore invalid input
        }
    }

    /**
     * Updates width field based on height while maintaining aspect ratio.
     */
    private void updateWidthFromHeight() {
        if (originalImageDimensions == null) return;

        try {
            double height = Double.parseDouble(heightField.getText());
            double aspectRatio = (double) originalImageDimensions[0] / originalImageDimensions[1];
            double width = height * aspectRatio;
            widthField.setText(String.format("%.0f", width));
        } catch (NumberFormatException e) {
            // Ignore invalid input
        }
    }

    /**
     * Handles the insert button action.
     */
    @FXML
    private void handleInsert() {
        if (!validateInput()) {
            return;
        }

        try {
            int pageIndex = pageSpinner.getValue() - 1; // Convert to 0-based
            double x = Double.parseDouble(xField.getText());
            double y = Double.parseDouble(yField.getText());
            double width = Double.parseDouble(widthField.getText());
            double height = Double.parseDouble(heightField.getText());
            boolean isStamp = isStampCheckBox.isSelected();

            // Convert coordinates if user is using top-left origin
            if (topLeftOriginCheckBox != null && topLeftOriginCheckBox.isSelected()) {
                // User entered top-left coordinates, convert to PDF bottom-left
                // In top-left: y=0 is at top
                // In PDF bottom-left: y=0 is at bottom
                // Formula: pdfY = pageHeight - topLeftY - imageHeight
                y = currentPageHeight - y - height;
                logger.debug("Converted top-left Y={} to PDF bottom-left Y={} (pageHeight={}, imageHeight={})",
                        Double.parseDouble(yField.getText()), y, currentPageHeight, height);
            }

            if (isStamp) {
                resultPlacement = ImagePlacement.forStamp(pageIndex, x, y, width, height, selectedImageFile);
            } else {
                resultPlacement = ImagePlacement.forImage(pageIndex, x, y, width, height, selectedImageFile);
            }

            insertClicked = true;
            logger.info("Image placement created: page={}, pos=({}, {}), size={}x{}, stamp={}",
                    pageIndex, x, y, width, height, isStamp);
            dialogStage.close();

        } catch (NumberFormatException e) {
            showError(lang().getString("error.title"), lang().getString("imagePlacement.error.invalidNumber"));
        } catch (IllegalArgumentException e) {
            showError(lang().getString("error.title"), e.getMessage());
        }
    }

    /**
     * Handles the cancel button action.
     */
    @FXML
    private void handleCancel() {
        insertClicked = false;
        logger.debug("Image placement cancelled");
        dialogStage.close();
    }

    /**
     * Validates the input fields.
     *
     * @return true if valid, false otherwise
     */
    private boolean validateInput() {
        if (selectedImageFile == null) {
            showError(lang().getString("error.title"), lang().getString("imagePlacement.error.noImage"));
            return false;
        }

        if (xField.getText().trim().isEmpty()) {
            showError(lang().getString("error.title"), lang().getString("imagePlacement.error.noX"));
            return false;
        }

        if (yField.getText().trim().isEmpty()) {
            showError(lang().getString("error.title"), lang().getString("imagePlacement.error.noY"));
            return false;
        }

        if (widthField.getText().trim().isEmpty()) {
            showError(lang().getString("error.title"), lang().getString("imagePlacement.error.noWidth"));
            return false;
        }

        if (heightField.getText().trim().isEmpty()) {
            showError(lang().getString("error.title"), lang().getString("imagePlacement.error.noHeight"));
            return false;
        }

        try {
            double width = Double.parseDouble(widthField.getText());
            double height = Double.parseDouble(heightField.getText());

            if (width <= 0 || height <= 0) {
                showError(lang().getString("error.title"), lang().getString("imagePlacement.error.invalidSize"));
                return false;
            }
        } catch (NumberFormatException e) {
            showError(lang().getString("error.title"), lang().getString("imagePlacement.error.invalidNumber"));
            return false;
        }

        return true;
    }

    /**
     * Shows an error dialog.
     *
     * @param title   the dialog title
     * @param message the error message
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initOwner(dialogStage);
        alert.showAndWait();
    }
}
