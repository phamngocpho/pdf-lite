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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.pdflite.dialog.CustomInfoDialog;
import org.pdflite.manager.LanguageManager;
import org.pdflite.service.PDFService;
import org.pdflite.service.PDFSplitService;
import org.pdflite.util.DialogTitleBar;
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

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    @FXML
    private javafx.scene.layout.HBox dialogTitleBar;
    @FXML
    private Label fileNameLabel;
    @FXML
    private Label totalPagesLabel;
    @FXML
    private ScrollPane previewScrollPane;
    @FXML
    private FlowPane previewPane;
    @FXML
    private RadioButton splitByRangeRadio;
    @FXML
    private RadioButton splitByPagesRadio;
    @FXML
    private RadioButton splitAllPagesRadio;
    @FXML
    private VBox rangeInputBox;
    @FXML
    private VBox pagesInputBox;
    @FXML
    private TextField rangeTextField;
    @FXML
    private TextField pagesPerFileTextField;
    @FXML
    private ListView<String> rangesListView;
    @FXML
    private Button addRangeButton;
    @FXML
    private Button removeRangeButton;
    @FXML
    private Button splitButton;
    @FXML
    private Button cancelButton;
    @FXML
    private CheckBox zipOutputCheckBox;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;

    private File sourceFile;
    private PDDocument sourceDocument;
    private int totalPages;
    private final PDFSplitService splitService = new PDFSplitService();
    private final PDFService pdfService = new PDFService();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ObservableList<String> rangesList = FXCollections.observableArrayList();
    private Stage dialogStage;
    private org.pdflite.manager.ThemeManager themeManager;

    /**
     * Initializes the controller.
     */
    @FXML
    public void initialize() {
        logger.info("Initializing SplitDialogController");

        // Set up the toggle group for split modes
        ToggleGroup splitModeGroup = new ToggleGroup();
        splitByRangeRadio.setToggleGroup(splitModeGroup);
        splitByPagesRadio.setToggleGroup(splitModeGroup);
        splitAllPagesRadio.setToggleGroup(splitModeGroup);

        // Set default selection
        splitByRangeRadio.setSelected(true);

        // Setup listeners for radio buttons
        splitModeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            updateInputVisibility();
            updateButtonStates();
        });

        // Setup ranges list view
        rangesListView.setItems(rangesList);

        // Setup button states
        updateButtonStates();
        rangesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                updateButtonStates());

        // Listen to changes in pagesPerFileTextField for option 2
        pagesPerFileTextField.textProperty().addListener((obs, oldVal, newVal) -> updateButtonStates());

        // Hide the progress bar initially
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

        // Create and add a custom title bar
        String title = lang().getString("split.title");
        DialogTitleBar titleBar = new DialogTitleBar(title, dialogStage);
        if (dialogTitleBar != null) {
            dialogTitleBar.getChildren().setAll(titleBar.getTitleBar().getChildren());
        }

        // Update all UI text
        updateAllUIText();

        // Ensure FlowPane layout is calculated correctly after the dialog is shown
        // This fixes the issue where FlowPane doesn't calculate the correct column count initially
        // Use a small delay to ensure the dialog is fully rendered
        Platform.runLater(() -> Platform.runLater(() -> {
            if (previewPane != null && previewScrollPane != null) {
                // Force layout calculation after the dialog is visible
                previewScrollPane.applyCss();
                previewScrollPane.layout();
                previewPane.applyCss();
                previewPane.layout();
            }
        }));
    }
    
    /**
     * Updates all UI text elements with current language.
     */
    private void updateAllUIText() {
        if (dialogStage == null || dialogStage.getScene() == null) {
            return;
        }
        
        // Update radio buttons
        splitByRangeRadio.setText(lang().getString("split.byRange"));
        splitByPagesRadio.setText(lang().getString("split.byPages"));
        splitAllPagesRadio.setText(lang().getString("split.allPages"));
        
        // Update buttons
        addRangeButton.setText(lang().getString("split.addRange"));
        removeRangeButton.setText(lang().getString("split.removeRange"));
        splitButton.setText(lang().getString("split.split"));
        cancelButton.setText(lang().getString("split.cancel"));
        
        // Update checkbox
        zipOutputCheckBox.setText(lang().getString("split.zipOutput"));
        
        // Update status
        statusLabel.setText(lang().getString("split.status.ready"));
        
        // Recursively update all Labels in the scene
        updateNodeText(dialogStage.getScene().getRoot());
    }
    
    /**
     * Recursively updates text for Labels.
     */
    private void updateNodeText(javafx.scene.Node node) {
        if (node instanceof Label label) {
            String text = label.getText();
            if (text != null && !text.isEmpty()) {
                if (text.equals("File:")) {
                    label.setText(lang().getString("split.file") + ":");
                } else if (text.equals("No file selected")) {
                    label.setText(lang().getString("split.noFileSelected"));
                } else if (text.startsWith("Total Pages:")) {
                    // Keep dynamic content
                } else if (text.equals("Page Preview")) {
                    label.setText(lang().getString("split.preview"));
                } else if (text.equals("Split Options")) {
                    label.setText(lang().getString("split.options"));
                } else if (text.contains("Enter page ranges")) {
                    label.setText(lang().getString("split.enterRanges") + ":");
                } else if (text.equals("Selected Ranges:")) {
                    label.setText(lang().getString("split.selectedRanges") + ":");
                } else if (text.contains("Number of pages per file")) {
                    label.setText(lang().getString("split.pagesPerFile") + ":");
                } else if (text.equals("Each page will be saved as a separate PDF file.")) {
                    label.setText(lang().getString("split.eachPageSeparate"));
                } else if (text.equals("Output Options")) {
                    label.setText(lang().getString("split.outputOptions"));
                } else if (text.equals("Ready to split")) {
                    label.setText(lang().getString("split.status.ready"));
                }
            }
        } else if (node instanceof TextField textField) {
            String prompt = textField.getPromptText();
            if (prompt != null && !prompt.isEmpty()) {
                if (prompt.contains("1-5")) {
                    textField.setPromptText(lang().getString("split.rangeExample"));
                } else if (prompt.equals("e.g., 10")) {
                    textField.setPromptText(lang().getString("split.pagesPerFilePrompt"));
                } else if (prompt.equals("1")) {
                    textField.setPromptText("1");
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
     * Sets the theme manager.
     */
    public void setThemeManager(org.pdflite.manager.ThemeManager themeManager) {
        this.themeManager = themeManager;
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
            totalPagesLabel.setText(java.text.MessageFormat.format(lang().getString("split.totalPages"), totalPages));

            // Load thumbnails
            loadThumbnails();

            updateStatus(lang().getString("split.status.ready"));
            logger.info("Loaded PDF: {} ({} pages)", file.getName(), totalPages);
        } catch (IOException e) {
            logger.error("Error loading PDF", e);
            showError(lang().getString("error.title"), lang().getString("error.openPdf") + ": " + e.getMessage());
        }
    }

    /**
     * Sets the source PDF document (for encrypted PDFs already opened).
     */
    public void setSourceDocument(PDDocument document, File file) {
        this.sourceDocument = document;
        this.sourceFile = file;

        if (document == null) {
            return;
        }

        totalPages = document.getNumberOfPages();
        fileNameLabel.setText(file != null ? file.getName() : "Document");
        totalPagesLabel.setText(java.text.MessageFormat.format(lang().getString("split.totalPages"), totalPages));

        // Load thumbnails from documents
        loadThumbnailsFromDocument();

        updateStatus(lang().getString("split.status.ready"));
        logger.info("Loaded PDF document ({} pages)", totalPages);
    }

    /**
     * Loads thumbnail previews from PDDocument.
     */
    private void loadThumbnailsFromDocument() {
        ThumbnailLoader.loadThumbnailsFromDocument(sourceDocument, totalPages, previewPane,
                pdfService, executorService, this::updateStatus);
    }

    /**
     * Loads thumbnail previews for all pages.
     */
    private void loadThumbnails() {
        ThumbnailLoader.loadThumbnails(sourceFile, totalPages, previewPane,
                pdfService, executorService, this::updateStatus);
    }

    /**
     * Updates input visibility based on the selected split mode.
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
            showError(lang().getString("error.title"), lang().getString("split.error.invalidRange"));
            return;
        }

        // Parse range (e.g., "1-5" or "3-3")
        String[] parts = rangeText.split("-");
        if (parts.length != 2) {
            showError(lang().getString("error.title"), lang().getString("split.error.invalidFormat"));
            return;
        }

        try {
            int startPage = Integer.parseInt(parts[0].trim());
            int endPage = Integer.parseInt(parts[1].trim());

            if (startPage < 1 || endPage > totalPages || startPage > endPage) {
                showError(lang().getString("error.title"),
                        java.text.MessageFormat.format(lang().getString("split.error.rangeOutOfBounds"), totalPages));
                return;
            }

            rangesList.add(rangeText);
            rangeTextField.clear();
            updateButtonStates();

        } catch (NumberFormatException e) {
            showError(lang().getString("error.title"), lang().getString("split.error.invalidNumber"));
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
            showError(lang().getString("error.title"), lang().getString("split.error.noFile"));
            return;
        }

        // Choose an output directory
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle(lang().getString("split.selectOutput"));
        File outputDir = dirChooser.showDialog(dialogStage);

        if (outputDir == null) {
            return; // User cancelled
        }

        // Perform split based on the selected mode
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
            showError(lang().getString("error.title"), lang().getString("split.error.noRanges"));
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
            showError(lang().getString("error.title"), lang().getString("split.error.invalidNumber"));
            return;
        }

        try {
            int pagesPerFile = Integer.parseInt(pagesPerFileText);

            if (pagesPerFile < 1 || pagesPerFile > totalPages) {
                showError(lang().getString("error.title"),
                        java.text.MessageFormat.format(lang().getString("split.error.invalidPagesPerFile"), totalPages));
                return;
            }

            String baseName = getBaseFileName(sourceFile);

            executorService.submit(() -> {
                try {
                    setUIEnabled(false);
                    progressBar.setVisible(true);
                    progressBar.setManaged(true);
                    progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                    updateStatus(lang().getString("split.status.splitting"));

                    List<File> outputFiles = splitService.splitByPageCount(
                            sourceFile, outputDir, pagesPerFile, baseName
                    );

                    handleSplitSuccess(outputFiles, outputDir);

                } catch (IOException e) {
                    handleSplitError(e);
                }
            });

        } catch (NumberFormatException e) {
            showError(lang().getString("error.title"), lang().getString("split.error.invalidNumber"));
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
                updateStatus(lang().getString("split.status.splittingAll"));

                List<File> outputFiles;
                if (sourceDocument != null) {
                    // Use already-opened document (supports encrypted PDFs)
                    outputFiles = splitService.splitIntoIndividualPages(
                            sourceDocument, outputDir, baseName
                    );
                } else {
                    // Use a file directly
                    outputFiles = splitService.splitIntoIndividualPages(
                            sourceFile, outputDir, baseName
                    );
                }

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
                updateStatus(lang().getString("split.status.splitting"));

                List<File> outputFiles;
                if (sourceDocument != null) {
                    // Use already-opened document (supports encrypted PDFs)
                    outputFiles = splitService.splitPDF(sourceDocument, outputDir, ranges);
                } else {
                    // Use a file directly
                    outputFiles = splitService.splitPDF(sourceFile, outputDir, ranges);
                }

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
            updateStatus(lang().getString("split.status.success"));

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
        updateStatus(lang().getString("split.status.creatingZip"));

        executorService.submit(() -> {
            try {
                String baseName = getBaseFileName(sourceFile);
                File zipFile = new File(outputDir, baseName + "_split.zip");

                ZipUtility.createZipArchive(outputFiles, zipFile);

                Platform.runLater(() -> {
                    updateStatus(lang().getString("split.status.zipCreated"));
                    showInfo(
                            java.text.MessageFormat.format(lang().getString("split.success.zipMessage"),
                                    outputFiles.size(), zipFile.getName()));
                    handleCancel();
                });

            } catch (IOException e) {
                logger.error("Error creating ZIP", e);
                Platform.runLater(() -> {
                    updateStatus(lang().getString("split.status.zipFailed"));
                    showError(lang().getString("error.title"), lang().getString("split.error.zip") + ": " + e.getMessage());
                    showSplitCompleteDialog(outputFiles, outputDir);
                });
            }
        });
    }

    /**
     * Shows a split complete dialog.
     */
    private void showSplitCompleteDialog(List<File> outputFiles, File outputDir) {
        showInfo(
                java.text.MessageFormat.format(lang().getString("split.success.message"),
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
            updateStatus(lang().getString("split.status.failed"));
            showError(lang().getString("error.title"), lang().getString("split.error.split") + ": " + e.getMessage());
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
     * Update button enabled states.
     */
    private void updateButtonStates() {
        boolean hasSelection = rangesListView.getSelectionModel().getSelectedItem() != null;

        removeRangeButton.setDisable(!hasSelection);

        // Enable split button based on selected mode and validation
        boolean canSplit = false;
        if (sourceFile == null) {
            canSplit = false;
        } else if (splitByRangeRadio.isSelected()) {
            // For custom ranges, need at least one range
            canSplit = !rangesList.isEmpty();
        } else if (splitByPagesRadio.isSelected()) {
            // For page count, need a valid number
            String pagesText = pagesPerFileTextField.getText().trim();
            if (!pagesText.isEmpty()) {
                try {
                    int pagesPerFile = Integer.parseInt(pagesText);
                    canSplit = pagesPerFile >= 1 && pagesPerFile <= totalPages;
                } catch (NumberFormatException e) {
                    canSplit = false;
                }
            }
        } else if (splitAllPagesRadio.isSelected()) {
            // For individual pages, always enabled if the file exists
            canSplit = true;
        }

        splitButton.setDisable(!canSplit);
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
        Platform.runLater(() -> CustomInfoDialog.show(
                title,
                lang().getString("error.title"),
                message,
                themeManager
        ));
    }

    /**
     * Shows an information dialog.
     */
    private void showInfo(String message) {
        Platform.runLater(() -> {
            CustomInfoDialog.show(
                    lang().getString("split.success.title"),
                    lang().getString("success.title"),
                    message,
                    themeManager
            );
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

