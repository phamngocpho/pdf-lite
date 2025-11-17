package org.pdflite.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.pdflite.service.PDFService;
import org.pdflite.service.PDFSplitService;
import org.pdflite.util.ThumbnailLoader;
import org.pdflite.util.ZipUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the PDF Split Dialog.
 * Allows users to select page ranges, preview pages, and split PDFs.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class SplitDialogController {

    private static final Logger logger = LoggerFactory.getLogger(SplitDialogController.class);

    @FXML private Label fileNameLabel;
    @FXML private Label totalPagesLabel;
    @FXML private ScrollPane previewScrollPane;
    @FXML private FlowPane previewPane;
    @FXML private RadioButton splitByRangeRadio;
    @FXML private RadioButton splitByPagesRadio;
    @FXML private RadioButton splitAllPagesRadio;
    @FXML private VBox rangeInputBox;
    @FXML private VBox pagesInputBox;
    @FXML private TextField rangeTextField;
    @FXML private TextField pagesPerFileTextField;
    @FXML private ListView<String> rangesListView;
    @FXML private Button addRangeButton;
    @FXML private Button removeRangeButton;
    @FXML private Button splitButton;
    @FXML private Button cancelButton;
    @FXML private CheckBox zipOutputCheckBox;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;

    private File sourceFile;
    private int totalPages;
    private final PDFSplitService splitService = new PDFSplitService();
    private final PDFService pdfService = new PDFService();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ObservableList<String> rangesList = FXCollections.observableArrayList();
    private Stage dialogStage;

    /**
     * Initializes the controller.
     */
    @FXML
    public void initialize() {
        logger.info("Initializing SplitDialogController");

        // Setup toggle group for split modes
        ToggleGroup splitModeGroup = new ToggleGroup();
        splitByRangeRadio.setToggleGroup(splitModeGroup);
        splitByPagesRadio.setToggleGroup(splitModeGroup);
        splitAllPagesRadio.setToggleGroup(splitModeGroup);

        // Set default selection
        splitByRangeRadio.setSelected(true);

        // Setup listeners for radio buttons
        splitModeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> updateInputVisibility());

        // Setup ranges list view
        rangesListView.setItems(rangesList);

        // Setup button states
        updateButtonStates();
        rangesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> 
            updateButtonStates());

        // Hide progress bar initially
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        // Initial visibility
        updateInputVisibility();
    }

    /**
     * Sets the dialog stage.
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /**
     * Sets the source PDF file.
     */
    public void setSourceFile(File file) {
        this.sourceFile = file;
        
        if (file == null) {
            return;
        }

        try {
            totalPages = splitService.getPageCount(file);
            fileNameLabel.setText(file.getName());
            totalPagesLabel.setText(String.format("Total Pages: %d", totalPages));
            
            // Load thumbnails
            loadThumbnails();
            
            updateStatus("Ready to split");
            logger.info("Loaded PDF: {} ({} pages)", file.getName(), totalPages);
        } catch (IOException e) {
            logger.error("Error loading PDF", e);
            showError("Error", "Failed to load PDF: " + e.getMessage());
        }
    }

    /**
     * Loads thumbnail previews for all pages.
     */
    private void loadThumbnails() {
        ThumbnailLoader.loadThumbnails(sourceFile, totalPages, previewPane, 
            pdfService, executorService, this::updateStatus);
    }

    /**
     * Updates input visibility based on selected split mode.
     */
    private void updateInputVisibility() {
        rangeInputBox.setVisible(splitByRangeRadio.isSelected());
        rangeInputBox.setManaged(splitByRangeRadio.isSelected());
        
        pagesInputBox.setVisible(splitByPagesRadio.isSelected());
        pagesInputBox.setManaged(splitByPagesRadio.isSelected());
    }

    /**
     * Handles add range button click.
     */
    @FXML
    private void handleAddRange() {
        String rangeText = rangeTextField.getText().trim();
        
        if (rangeText.isEmpty()) {
            showError("Invalid Input", "Please enter a page range (e.g., 1-5)");
            return;
        }

        // Parse range (e.g., "1-5" or "3-3")
        String[] parts = rangeText.split("-");
        if (parts.length != 2) {
            showError("Invalid Format", "Please use format: startPage-endPage (e.g., 1-5)");
            return;
        }

        try {
            int startPage = Integer.parseInt(parts[0].trim());
            int endPage = Integer.parseInt(parts[1].trim());

            if (startPage < 1 || endPage > totalPages || startPage > endPage) {
                showError("Invalid Range", 
                    String.format("Range must be between 1 and %d, with start <= end", totalPages));
                return;
            }

            rangesList.add(rangeText);
            rangeTextField.clear();
            updateButtonStates();
            
        } catch (NumberFormatException e) {
            showError("Invalid Input", "Please enter valid page numbers");
        }
    }

    /**
     * Handles remove range button click.
     */
    @FXML
    private void handleRemoveRange() {
        String selected = rangesListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            rangesList.remove(selected);
            updateButtonStates();
        }
    }

    /**
     * Handles split button click.
     */
    @FXML
    private void handleSplit() {
        if (sourceFile == null) {
            showError("No File", "Please select a PDF file first");
            return;
        }

        // Choose output directory
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Output Directory");
        File outputDir = dirChooser.showDialog(dialogStage);
        
        if (outputDir == null) {
            return; // User cancelled
        }

        // Perform split based on selected mode
        if (splitByRangeRadio.isSelected()) {
            performSplitByRanges(outputDir);
        } else if (splitByPagesRadio.isSelected()) {
            performSplitByPageCount(outputDir);
        } else if (splitAllPagesRadio.isSelected()) {
            performSplitAllPages(outputDir);
        }
    }

    /**
     * Performs split by custom ranges.
     */
    private void performSplitByRanges(File outputDir) {
        if (rangesList.isEmpty()) {
            showError("No Ranges", "Please add at least one page range");
            return;
        }

        List<PDFSplitService.PageRange> ranges = new ArrayList<>();
        String baseName = getBaseFileName(sourceFile);

        for (String rangeText : rangesList) {
            String[] parts = rangeText.split("-");
            int startPage = Integer.parseInt(parts[0].trim());
            int endPage = Integer.parseInt(parts[1].trim());
            String fileName = String.format("%s_pages_%s.pdf", baseName, rangeText.replace("-", "_to_"));

            ranges.add(new PDFSplitService.PageRange(startPage, endPage, fileName));
        }

        performSplit(outputDir, ranges);
    }

    /**
     * Performs split by page count.
     */
    private void performSplitByPageCount(File outputDir) {
        String pagesPerFileText = pagesPerFileTextField.getText().trim();
        
        if (pagesPerFileText.isEmpty()) {
            showError("Invalid Input", "Please enter the number of pages per file");
            return;
        }

        try {
            int pagesPerFile = Integer.parseInt(pagesPerFileText);
            
            if (pagesPerFile < 1 || pagesPerFile > totalPages) {
                showError("Invalid Input", 
                    String.format("Pages per file must be between 1 and %d", totalPages));
                return;
            }

            String baseName = getBaseFileName(sourceFile);
            
            executorService.submit(() -> {
                try {
                    setUIEnabled(false);
                    progressBar.setVisible(true);
                    progressBar.setManaged(true);
                    progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                    updateStatus("Splitting PDF...");

                    List<File> outputFiles = splitService.splitByPageCount(
                        sourceFile, outputDir, pagesPerFile, baseName
                    );

                    handleSplitSuccess(outputFiles, outputDir);

                } catch (IOException e) {
                    handleSplitError(e);
                }
            });
            
        } catch (NumberFormatException e) {
            showError("Invalid Input", "Please enter a valid number");
        }
    }

    /**
     * Performs split into individual pages.
     */
    private void performSplitAllPages(File outputDir) {
        String baseName = getBaseFileName(sourceFile);
        
        executorService.submit(() -> {
            try {
                setUIEnabled(false);
                progressBar.setVisible(true);
                progressBar.setManaged(true);
                progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                updateStatus("Splitting PDF into individual pages...");

                List<File> outputFiles = splitService.splitIntoIndividualPages(
                    sourceFile, outputDir, baseName
                );

                handleSplitSuccess(outputFiles, outputDir);

            } catch (IOException e) {
                handleSplitError(e);
            }
        });
    }

    /**
     * Performs the split operation.
     */
    private void performSplit(File outputDir, List<PDFSplitService.PageRange> ranges) {
        executorService.submit(() -> {
            try {
                setUIEnabled(false);
                progressBar.setVisible(true);
                progressBar.setManaged(true);
                progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                updateStatus("Splitting PDF...");

                List<File> outputFiles = splitService.splitPDF(sourceFile, outputDir, ranges);

                handleSplitSuccess(outputFiles, outputDir);

            } catch (IOException e) {
                handleSplitError(e);
            }
        });
    }

    /**
     * Handles successful split operation.
     */
    private void handleSplitSuccess(List<File> outputFiles, File outputDir) {
        Platform.runLater(() -> {
            progressBar.setProgress(1.0);
            updateStatus("Split completed successfully!");

            // Create ZIP if requested
            if (zipOutputCheckBox.isSelected() && outputFiles.size() > 1) {
                createZipArchive(outputFiles, outputDir);
            } else {
                showSplitCompleteDialog(outputFiles, outputDir);
            }
        });
    }

    /**
     * Creates a ZIP archive of the output files.
     */
    private void createZipArchive(List<File> outputFiles, File outputDir) {
        updateStatus("Creating ZIP archive...");
        
        executorService.submit(() -> {
            try {
                String baseName = getBaseFileName(sourceFile);
                File zipFile = new File(outputDir, baseName + "_split.zip");
                
                ZipUtility.createZipArchive(outputFiles, zipFile);
                
                Platform.runLater(() -> {
                    updateStatus("ZIP archive created!");
                    showInfo("Split Complete", 
                        String.format("Successfully split PDF into %d files.\nZIP archive created: %s", 
                            outputFiles.size(), zipFile.getName()));
                    handleCancel();
                });
                
            } catch (IOException e) {
                logger.error("Error creating ZIP", e);
                Platform.runLater(() -> {
                    updateStatus("ZIP creation failed");
                    showError("ZIP Error", "Failed to create ZIP archive: " + e.getMessage());
                    showSplitCompleteDialog(outputFiles, outputDir);
                });
            }
        });
    }

    /**
     * Shows split complete dialog.
     */
    private void showSplitCompleteDialog(List<File> outputFiles, File outputDir) {
        showInfo("Split Complete", 
            String.format("Successfully split PDF into %d files.\nOutput directory: %s", 
                outputFiles.size(), outputDir.getAbsolutePath()));
        handleCancel();
    }

    /**
     * Handles split error.
     */
    private void handleSplitError(IOException e) {
        logger.error("Error splitting PDF", e);
        Platform.runLater(() -> {
            progressBar.setVisible(false);
            progressBar.setManaged(false);
            setUIEnabled(true);
            updateStatus("Split failed!");
            showError("Split Error", "Failed to split PDF: " + e.getMessage());
        });
    }

    /**
     * Handles cancel button click.
     */
    @FXML
    private void handleCancel() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    /**
     * Updates button enabled states.
     */
    private void updateButtonStates() {
        boolean hasSelection = rangesListView.getSelectionModel().getSelectedItem() != null;
        
        removeRangeButton.setDisable(!hasSelection);
        splitButton.setDisable(sourceFile == null);
    }

    /**
     * Enables or disables UI controls.
     */
    private void setUIEnabled(boolean enabled) {
        Platform.runLater(() -> {
            splitByRangeRadio.setDisable(!enabled);
            splitByPagesRadio.setDisable(!enabled);
            splitAllPagesRadio.setDisable(!enabled);
            rangeTextField.setDisable(!enabled);
            pagesPerFileTextField.setDisable(!enabled);
            addRangeButton.setDisable(!enabled);
            removeRangeButton.setDisable(!enabled);
            splitButton.setDisable(!enabled);
            zipOutputCheckBox.setDisable(!enabled);
        });
    }

    /**
     * Updates status label.
     */
    private void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    /**
     * Gets base file name without extension.
     */
    private String getBaseFileName(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(0, lastDot) : name;
    }

    /**
     * Shows an error dialog.
     */
    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Shows an information dialog.
     */
    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Cleanup resources.
     */
    public void shutdown() {
        if (!executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}

