package org.pdflite.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.pdflite.dialog.CustomInfoDialog;
import org.pdflite.service.PDFMergeService;
import org.pdflite.service.PDFService;
import org.pdflite.service.PDFSplitService;
import org.pdflite.util.DialogTitleBar;
import org.pdflite.util.ThumbnailLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the PDF Extract Pages Dialog.
 * Allows users to extract specific pages from a PDF document.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class ExtractDialogController {

    private static final Logger logger = LoggerFactory.getLogger(ExtractDialogController.class);

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
    private TextArea rangeTextArea;
    @FXML
    private Label rangeValidationLabel;
    @FXML
    private TextField outputFileNameField;
    @FXML
    private Button allPagesButton;
    @FXML
    private Button oddPagesButton;
    @FXML
    private Button evenPagesButton;
    @FXML
    private Button extractButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;

    private File sourceFile;
    private PDDocument sourceDocument;
    private int totalPages;
    private final PDFSplitService splitService = new PDFSplitService();
    private final PDFMergeService mergeService = new PDFMergeService();
    private final PDFService pdfService = new PDFService();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Stage dialogStage;
    private org.pdflite.manager.ThemeManager themeManager;

    /**
     * Initializes the controller.
     */
    @FXML
    public void initialize() {
        logger.info("Initializing ExtractDialogController");

        // Hide the progress bar initially
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        // Setup range validation
        if (rangeTextArea != null) {
            rangeTextArea.textProperty().addListener((obs, oldVal, newVal) -> validateRange(newVal));
        }
    }

    /**
     * Sets the dialog stage.
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
        
        // Create and add a custom title bar
        String title = dialogStage.getTitle() != null ? dialogStage.getTitle() : "Extract PDF Pages";
        DialogTitleBar titleBar = new DialogTitleBar(title, dialogStage);
        if (dialogTitleBar != null) {
            dialogTitleBar.getChildren().setAll(titleBar.getTitleBar().getChildren());
        }
        
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
            initializeUI(file, totalPages, true);
        } catch (IOException e) {
            logger.error("Error loading PDF", e);
            showError("Error", "Failed to load PDF: " + e.getMessage());
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
        initializeUI(file, totalPages, false);
    }

    /**
     * Initializes the UI with file information and loads thumbnails.
     *
     * @param file         The PDF file
     * @param pageCount    Total number of pages
     * @param loadFromFile Whether to load thumbnails from a file (true) or from a document (false)
     */
    private void initializeUI(File file, int pageCount, boolean loadFromFile) {
        fileNameLabel.setText(file != null ? file.getName() : "Document");
        totalPagesLabel.setText(String.format("Total Pages: %d", pageCount));

        // Load thumbnails
        if (loadFromFile) {
            loadThumbnails();
        } else {
            loadThumbnailsFromDocument();
        }

        updateStatus("Ready to extract");

        // Resize dialog after UI is initialized (workaround for Ubuntu sizing issue)
        if (dialogStage != null) {
            Platform.runLater(() -> Platform.runLater(() -> dialogStage.sizeToScene()));
        }

        if (file != null) {
            logger.info("Loaded PDF: {} ({} pages)", file.getName(), pageCount);
        } else {
            logger.info("Loaded PDF document ({} pages)", pageCount);
        }
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
     * Validates range input.
     */
    private void validateRange(String rangeText) {
        if (rangeText == null || rangeText.trim().isEmpty()) {
            rangeValidationLabel.setText("");
            rangeValidationLabel.setStyle("-fx-text-fill: #666;");
            return;
        }

        try {
            List<Integer> pages = parsePageRanges(rangeText);
            if (pages.isEmpty()) {
                rangeValidationLabel.setText("No valid pages specified");
                rangeValidationLabel.setStyle("-fx-text-fill: #f44336;");
            } else {
                rangeValidationLabel.setText(
                        String.format("✓ %d page(s) will be extracted", pages.size())
                );
                rangeValidationLabel.setStyle("-fx-text-fill: #4CAF50;");
            }
        } catch (IllegalArgumentException e) {
            rangeValidationLabel.setText("✗ " + e.getMessage());
            rangeValidationLabel.setStyle("-fx-text-fill: #f44336;");
        }
    }

    /**
     * Parses page ranges like "1-5, 10, 25-30" into a list of page numbers.
     */
    private List<Integer> parsePageRanges(String rangeText) throws IllegalArgumentException {
        List<Integer> pages = new ArrayList<>();
        String[] parts = rangeText.split(",");

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }

            if (part.contains("-")) {
                // Range like "1-5"
                String[] rangeParts = part.split("-");
                if (rangeParts.length != 2) {
                    throw new IllegalArgumentException("Invalid range format: " + part);
                }

                try {
                    int start = Integer.parseInt(rangeParts[0].trim());
                    int end = Integer.parseInt(rangeParts[1].trim());

                    if (start < 1 || end > totalPages || start > end) {
                        throw new IllegalArgumentException(
                                String.format("Invalid range %d-%d (total pages: %d)", start, end, totalPages)
                        );
                    }

                    for (int i = start; i <= end; i++) {
                        if (!pages.contains(i)) {
                            pages.add(i);
                        }
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid page number in range: " + part);
                }
            } else {
                // Single page like "10"
                try {
                    int page = Integer.parseInt(part);
                    if (page < 1 || page > totalPages) {
                        throw new IllegalArgumentException(
                                String.format("Page %d is out of range (total pages: %d)", page, totalPages)
                        );
                    }
                    if (!pages.contains(page)) {
                        pages.add(page);
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid page number: " + part);
                }
            }
        }

        // Sort pages
        Collections.sort(pages);
        return pages;
    }

    /**
     * Handles extract button click.
     */
    @FXML
    private void handleExtract() {
        if (sourceFile == null) {
            showError("No File", "Please select a PDF file first");
            return;
        }

        String rangeText = rangeTextArea.getText().trim();
        if (rangeText.isEmpty()) {
            showError("No Pages Selected", "Please enter page ranges to extract");
            return;
        }

        try {
            List<Integer> pages = parsePageRanges(rangeText);
            if (pages.isEmpty()) {
                showError("No Pages", "No valid pages to extract");
                return;
            }

            // Choose an output directory
            DirectoryChooser dirChooser = new DirectoryChooser();
            dirChooser.setTitle("Select Output Directory");
            File outputDir = dirChooser.showDialog(dialogStage);

            if (outputDir == null) {
                return; // User cancelled
            }

            // Determine output file name
            String outputFileName = outputFileNameField.getText().trim();
            if (outputFileName.isEmpty()) {
                String baseName = getBaseFileName(sourceFile);
                outputFileName = baseName + "_extracted.pdf";
            }
            if (!outputFileName.toLowerCase().endsWith(".pdf")) {
                outputFileName += ".pdf";
            }

            File outputFile = new File(outputDir, outputFileName);

            // Perform extraction
            performExtract(pages, outputFile);

        } catch (IllegalArgumentException e) {
            showError("Invalid Range", e.getMessage());
        }
    }

    /**
     * Performs the extract operation.
     */
    private void performExtract(List<Integer> pages, File outputFile) {
        executorService.submit(() -> {
            try {
                setUIEnabled(false);
                progressBar.setVisible(true);
                progressBar.setManaged(true);
                progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                updateStatus("Extracting pages...");

                // Create page ranges
                List<PDFSplitService.PageRange> ranges = createPageRanges(pages, outputFile);

                // If single range, extract directly
                if (ranges.size() == 1) {
                    List<File> result;
                    if (sourceDocument != null) {
                        result = splitService.splitPDF(
                                sourceDocument,
                                outputFile.getParentFile(),
                                ranges
                        );
                    } else {
                        result = splitService.splitPDF(
                                sourceFile,
                                outputFile.getParentFile(),
                                ranges
                        );
                    }
                    handleExtractSuccess(result.getFirst());
                } else {
                    // Multiple ranges, extract and merge
                    File tempDir = new File(System.getProperty("java.io.tmpdir"),
                            "pdf-lite-extract-" + System.currentTimeMillis());
                    if (!tempDir.mkdirs() && !tempDir.exists()) {
                        throw new IOException("Failed to create temporary directory: " + tempDir);
                    }

                    List<File> tempFiles;
                    if (sourceDocument != null) {
                        tempFiles = splitService.splitPDF(sourceDocument, tempDir, ranges);
                    } else {
                        tempFiles = splitService.splitPDF(sourceFile, tempDir, ranges);
                    }

                    // Merge all temp files into an output file
                    mergeService.mergePDFs(tempFiles, outputFile);

                    // Clean up temp files
                    for (File tempFile : tempFiles) {
                        if (!tempFile.delete()) {
                            logger.warn("Failed to delete temporary file: {}", tempFile);
                        }
                    }
                    if (!tempDir.delete()) {
                        logger.warn("Failed to delete temporary directory: {}", tempDir);
                    }

                    handleExtractSuccess(outputFile);
                }

            } catch (IOException e) {
                handleExtractError(e);
            }
        });
    }

    /**
     * Creates page ranges from a list of page numbers.
     */
    private List<PDFSplitService.PageRange> createPageRanges(List<Integer> pages, File outputFile) {
        List<PDFSplitService.PageRange> ranges = new ArrayList<>();
        int start = pages.getFirst();
        int end = start;

        for (int i = 1; i < pages.size(); i++) {
            if (pages.get(i) == end + 1) {
                // Consecutive page
                end = pages.get(i);
            } else {
                // Gap found, save current range
                ranges.add(new PDFSplitService.PageRange(start, end, outputFile.getName()));
                start = pages.get(i);
                end = start;
            }
        }
        // Add last range
        ranges.add(new PDFSplitService.PageRange(start, end, outputFile.getName()));
        return ranges;
    }

    /**
     * Handles successful extract operation.
     */
    private void handleExtractSuccess(File outputFile) {
        Platform.runLater(() -> {
            progressBar.setProgress(1.0);
            updateStatus("Extract completed successfully!");
            showInfo(
                    String.format("Successfully extracted pages to:\n%s", outputFile.getAbsolutePath()));
            handleCancel();
        });
    }

    /**
     * Handles extract error.
     */
    private void handleExtractError(IOException e) {
        logger.error("Error extracting pages", e);
        Platform.runLater(() -> {
            progressBar.setVisible(false);
            progressBar.setManaged(false);
            setUIEnabled(true);
            updateStatus("Extract failed!");
            showError("Extract Error", "Failed to extract pages: " + e.getMessage());
        });
    }

    /**
     * Handles all pages button.
     */
    @FXML
    private void handleAllPages() {
        if (totalPages > 0) {
            rangeTextArea.setText("1-" + totalPages);
        }
    }

    /**
     * Handles odd pages button.
     */
    @FXML
    private void handleOddPages() {
        if (totalPages > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= totalPages; i += 2) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(i);
            }
            rangeTextArea.setText(sb.toString());
        }
    }

    /**
     * Handles even pages button.
     */
    @FXML
    private void handleEvenPages() {
        if (totalPages > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i <= totalPages; i += 2) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(i);
            }
            rangeTextArea.setText(sb.toString());
        }
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
     * Enables or disables UI controls.
     */
    private void setUIEnabled(boolean enabled) {
        Platform.runLater(() -> {
            rangeTextArea.setDisable(!enabled);
            outputFileNameField.setDisable(!enabled);
            allPagesButton.setDisable(!enabled);
            oddPagesButton.setDisable(!enabled);
            evenPagesButton.setDisable(!enabled);
            extractButton.setDisable(!enabled);
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
            "Error",
            message,
            themeManager
        ));
    }

    /**
     * Shows an information dialog.
     */
    private void showInfo(String message) {
        Platform.runLater(() -> CustomInfoDialog.show(
            "Extract Complete",
            "Success",
            message,
            themeManager
        ));
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

