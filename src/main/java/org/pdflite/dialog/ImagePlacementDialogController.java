package org.pdflite.dialog;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.pdflite.manager.ImageManager;
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

        // Create and add a custom title bar
        String title = dialogStage.getTitle() != null ? dialogStage.getTitle() : "Insert Image";
        DialogTitleBar titleBar = new DialogTitleBar(title, dialogStage);
        // Copy children from the title bar to dialogTitleBar HBox
        dialogTitleBar.getChildren().setAll(titleBar.getTitleBar().getChildren());
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
        fileChooser.setTitle("Select Image File");

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
            showError("Error", "Image manager not initialized");
            return;
        }

        if (!imageManager.validateImageFile(file)) {
            showError("Invalid Image", "The selected file is not a valid image file.");
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
            dimensionsLabel.setText("Unable to read dimensions");
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
            showError("Invalid Input", "Please enter valid numbers for position and size.");
        } catch (IllegalArgumentException e) {
            showError("Invalid Input", e.getMessage());
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
            showError("No Image Selected", "Please select an image file.");
            return false;
        }

        if (xField.getText().trim().isEmpty()) {
            showError("Invalid Input", "Please enter X coordinate.");
            return false;
        }

        if (yField.getText().trim().isEmpty()) {
            showError("Invalid Input", "Please enter Y coordinate.");
            return false;
        }

        if (widthField.getText().trim().isEmpty()) {
            showError("Invalid Input", "Please enter width.");
            return false;
        }

        if (heightField.getText().trim().isEmpty()) {
            showError("Invalid Input", "Please enter height.");
            return false;
        }

        try {
            double width = Double.parseDouble(widthField.getText());
            double height = Double.parseDouble(heightField.getText());

            if (width <= 0 || height <= 0) {
                showError("Invalid Input", "Width and height must be positive.");
                return false;
            }
        } catch (NumberFormatException e) {
            showError("Invalid Input", "Please enter valid numbers for size.");
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
