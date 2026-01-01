package org.pdflite.manager;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.OCRService;
import org.pdflite.util.DialogTitleBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Manager for OCR operations and UI dialogs.
 */
public class OCRManager {

    private static final Logger logger = LoggerFactory.getLogger(OCRManager.class);
    
    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private final OCRService ocrService;
    private final BorderPane rootPane;
    private final UIStateManager uiStateManager;
    private ThemeManager themeManager;
    private Supplier<PDFDocument> documentSupplier;

    public OCRManager(BorderPane rootPane, UIStateManager uiStateManager) {
        this.rootPane = rootPane;
        this.uiStateManager = uiStateManager;
        this.ocrService = new OCRService();
    }

    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    public void setDocumentSupplier(Supplier<PDFDocument> documentSupplier) {
        this.documentSupplier = documentSupplier;
    }

    public boolean isOCRAvailable() {
        return ocrService.isAvailable();
    }

    /**
     * Opens the OCR dialog.
     */
    public void openOCRDialog() {
        PDFDocument document = documentSupplier != null ? documentSupplier.get() : null;
        if (document == null) {
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.noPdfLoaded"));
            return;
        }

        if (!ocrService.isAvailable()) {
            showTesseractNotFoundDialog();
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.initOwner(rootPane.getScene().getWindow());

        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("custom-info-dialog");

        // Title bar
        DialogTitleBar titleBarHelper = new DialogTitleBar(lang().getString("ocr.title"), dialog);
        mainContainer.getChildren().add(titleBarHelper.getTitleBar());

        // Main content with left panel (settings) and right panel (results)
        HBox mainContent = new HBox(15);
        mainContent.setPadding(new Insets(20));

        // Left panel - Settings
        VBox settingsPanel = new VBox(12);
        settingsPanel.setPrefWidth(220);
        settingsPanel.setMinWidth(220);

        // Settings header
        Label settingsHeader = new Label(lang().getString("ocr.settings"));
        settingsHeader.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // Language selection
        VBox langBox = new VBox(5);
        Label langLabel = new Label(lang().getString("ocr.language"));
        langLabel.getStyleClass().add("field-label");
        ComboBox<String> langCombo = new ComboBox<>();
        langCombo.getItems().addAll("eng", "vie", "eng+vie", "jpn", "kor", "chi_sim", "chi_tra", "fra", "deu", "spa");
        langCombo.setValue("eng+vie");
        langCombo.setMaxWidth(Double.MAX_VALUE);
        langCombo.getStyleClass().add("ocr-combo");
        langBox.getChildren().addAll(langLabel, langCombo);

        // DPI selection
        VBox dpiBox = new VBox(5);
        Label dpiLabel = new Label(lang().getString("ocr.dpi"));
        dpiLabel.getStyleClass().add("field-label");
        ComboBox<Integer> dpiCombo = new ComboBox<>();
        dpiCombo.getItems().addAll(150, 200, 300, 400);
        dpiCombo.setValue(300);
        dpiCombo.setMaxWidth(Double.MAX_VALUE);
        dpiCombo.getStyleClass().add("ocr-combo");
        Label dpiHint = new Label(lang().getString("ocr.dpiHint"));
        dpiHint.getStyleClass().add("hint-label");
        dpiHint.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");
        dpiBox.getChildren().addAll(dpiLabel, dpiCombo, dpiHint);

        // Page selection
        VBox pageBox = new VBox(8);
        Label pageLabel = new Label(lang().getString("ocr.pages"));
        pageLabel.getStyleClass().add("field-label");
        
        ToggleGroup pageGroup = new ToggleGroup();
        RadioButton currentPageRadio = new RadioButton(lang().getString("ocr.currentPage"));
        currentPageRadio.setToggleGroup(pageGroup);
        currentPageRadio.setSelected(true);
        
        RadioButton allPagesRadio = new RadioButton(lang().getString("ocr.allPages"));
        allPagesRadio.setToggleGroup(pageGroup);
        
        RadioButton rangeRadio = new RadioButton(lang().getString("ocr.pageRange"));
        rangeRadio.setToggleGroup(pageGroup);
        
        TextField rangeField = new TextField();
        rangeField.setPromptText("1-5, 10, 15-20");
        rangeField.setDisable(true);
        rangeField.setMaxWidth(Double.MAX_VALUE);
        
        rangeRadio.selectedProperty().addListener((obs, old, selected) -> rangeField.setDisable(!selected));
        
        pageBox.getChildren().addAll(pageLabel, currentPageRadio, allPagesRadio, rangeRadio, rangeField);

        // Progress section
        VBox progressBox = new VBox(8);
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);
        
        Label progressLabel = new Label();
        progressLabel.setVisible(false);
        progressLabel.setStyle("-fx-font-size: 11px;");
        progressBox.getChildren().addAll(progressBar, progressLabel);

        // Start button
        Button startButton = new Button(lang().getString("ocr.start"));
        startButton.getStyleClass().add("primary-button");
        startButton.setMaxWidth(Double.MAX_VALUE);
        startButton.setPrefHeight(35);

        settingsPanel.getChildren().addAll(settingsHeader, langBox, dpiBox, pageBox, progressBox, startButton);

        // Right panel - Results
        VBox resultsPanel = new VBox(10);
        HBox.setHgrow(resultsPanel, Priority.ALWAYS);

        // Results header with export button
        HBox resultsHeader = new HBox(10);
        resultsHeader.setAlignment(Pos.CENTER_LEFT);
        Label resultsLabel = new Label(lang().getString("ocr.results"));
        resultsLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button copyButton = new Button(lang().getString("ocr.copy"));
        copyButton.setDisable(true);
        
        Button exportButton = new Button(lang().getString("ocr.export"));
        exportButton.setDisable(true);
        
        resultsHeader.getChildren().addAll(resultsLabel, spacer, copyButton, exportButton);

        // Result text area with better styling
        TextArea resultArea = new TextArea();
        resultArea.setPromptText(lang().getString("ocr.resultPlaceholder"));
        resultArea.setWrapText(true);
        resultArea.setEditable(false);
        resultArea.getStyleClass().add("ocr-result-area");
        resultArea.setStyle("-fx-font-family: 'Segoe UI', 'Arial', sans-serif; -fx-font-size: 13px;");
        VBox.setVgrow(resultArea, Priority.ALWAYS);

        resultsPanel.getChildren().addAll(resultsHeader, resultArea);

        mainContent.getChildren().addAll(settingsPanel, resultsPanel);

        // Bottom buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(0, 20, 20, 20));

        Button closeButton = new Button(lang().getString("dialog.close"));
        closeButton.setOnAction(e -> dialog.close());
        buttonBox.getChildren().add(closeButton);

        // Event handlers
        startButton.setOnAction(e -> {
            ocrService.setLanguage(langCombo.getValue());
            int dpi = dpiCombo.getValue();
            
            List<Integer> pages = new ArrayList<>();
            int totalPages = document.getTotalPages();
            
            if (currentPageRadio.isSelected()) {
                pages.add(document.getCurrentPage());
            } else if (allPagesRadio.isSelected()) {
                for (int i = 0; i < totalPages; i++) {
                    pages.add(i);
                }
            } else {
                pages = parsePageRange(rangeField.getText(), totalPages);
                if (pages.isEmpty()) {
                    uiStateManager.showError(lang().getString("error.title"), lang().getString("ocr.invalidRange"));
                    return;
                }
            }

            startButton.setDisable(true);
            progressBar.setVisible(true);
            progressLabel.setVisible(true);
            progressBar.setProgress(0);
            resultArea.clear();

            final int totalToProcess = pages.size();
            
            ocrService.recognizePagesAsync(document.getDocument(), pages, dpi, progress -> Platform.runLater(() -> {
                progressBar.setProgress((double) progress / totalToProcess);
                progressLabel.setText(String.format(lang().getString("ocr.progress"), progress, totalToProcess));
            })).thenAccept(results -> Platform.runLater(() -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < results.size(); i++) {
                    if (i > 0) sb.append("\n");
                    sb.append("━━━ ").append(lang().getString("ocr.pageHeader")).append(" ").append(i + 1).append(" ━━━\n\n");
                    sb.append(results.get(i).trim()).append("\n");
                }
                resultArea.setText(sb.toString());
                startButton.setDisable(false);
                exportButton.setDisable(false);
                copyButton.setDisable(false);
                progressLabel.setText(lang().getString("ocr.complete"));
                progressBar.setProgress(1.0);
                uiStateManager.updateStatus(lang().getString("ocr.complete"));
            })).exceptionally(ex -> {
                Platform.runLater(() -> {
                    startButton.setDisable(false);
                    progressBar.setVisible(false);
                    progressLabel.setVisible(false);
                    uiStateManager.showError(lang().getString("error.title"), ex.getMessage());
                });
                return null;
            });
        });

        copyButton.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content1 = new javafx.scene.input.ClipboardContent();
            content1.putString(resultArea.getText());
            clipboard.setContent(content1);
            uiStateManager.updateStatus(lang().getString("ocr.copied"));
        });

        exportButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle(lang().getString("ocr.exportTitle"));
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
            );
            fileChooser.setInitialFileName("ocr_result.txt");
            
            File file = fileChooser.showSaveDialog(dialog);
            if (file != null) {
                try {
                    ocrService.exportToTextFile(resultArea.getText(), file);
                    uiStateManager.updateStatus(lang().getString("ocr.exportSuccess"));
                } catch (IOException ex) {
                    uiStateManager.showError(lang().getString("error.title"), ex.getMessage());
                }
            }
        });

        mainContainer.getChildren().addAll(mainContent, buttonBox);

        Scene scene = new Scene(mainContainer, 700, 550);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void showTesseractNotFoundDialog() {
        // Show dialog with option to browse for tessdata folder
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.initOwner(rootPane.getScene().getWindow());

        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("custom-info-dialog");

        // Title bar
        DialogTitleBar titleBarHelper = new DialogTitleBar(lang().getString("ocr.notAvailable"), dialog);
        mainContainer.getChildren().add(titleBarHelper.getTitleBar());

        // Content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_LEFT);

        // Warning header with icon
        HBox headerBox = new HBox(12);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        javafx.scene.shape.SVGPath warningIcon = new javafx.scene.shape.SVGPath();
        warningIcon.setContent("M12 2L1 21h22L12 2zm0 3.5L20.5 19h-17L12 5.5zM11 10v4h2v-4h-2zm0 6v2h2v-2h-2z");
        warningIcon.setFill(javafx.scene.paint.Color.web("#f0ad4e"));
        warningIcon.setScaleX(0.9);
        warningIcon.setScaleY(0.9);
        
        Label headerLabel = new Label(lang().getString("ocr.tesseractNotFound"));
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        headerBox.getChildren().addAll(warningIcon, headerLabel);

        // Instructions
        Label instructionsLabel = new Label(lang().getString("ocr.installInstructions"));
        instructionsLabel.setWrapText(true);
        instructionsLabel.setMaxWidth(450);

        // Manual path selection
        Label manualLabel = new Label(lang().getString("ocr.manualSetup"));
        manualLabel.setStyle("-fx-font-weight: bold;");

        HBox pathBox = new HBox(10);
        pathBox.setAlignment(Pos.CENTER_LEFT);
        TextField pathField = new TextField();
        pathField.setPromptText(lang().getString("ocr.tessdataPath"));
        pathField.setPrefWidth(300);
        
        Button browseButton = new Button(lang().getString("ocr.browse"));
        browseButton.setOnAction(e -> {
            javafx.stage.DirectoryChooser dirChooser = new javafx.stage.DirectoryChooser();
            dirChooser.setTitle(lang().getString("ocr.selectTessdata"));
            File selectedDir = dirChooser.showDialog(dialog);
            if (selectedDir != null) {
                pathField.setText(selectedDir.getAbsolutePath());
            }
        });
        pathBox.getChildren().addAll(pathField, browseButton);

        content.getChildren().addAll(headerBox, instructionsLabel, manualLabel, pathBox);

        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        Button applyButton = new Button(lang().getString("dialog.apply"));
        applyButton.getStyleClass().add("primary-button");
        applyButton.setOnAction(e -> {
            String path = pathField.getText().trim();
            if (!path.isEmpty()) {
                File tessdataDir = new File(path);
                File engData = new File(tessdataDir, "eng.traineddata");
                if (engData.exists()) {
                    ocrService.setTessDataPath(path);
                    uiStateManager.updateStatus(lang().getString("ocr.pathSet"));
                    dialog.close();
                    // Re-open OCR dialog
                    openOCRDialog();
                } else {
                    uiStateManager.showError(lang().getString("error.title"), lang().getString("ocr.invalidTessdata"));
                }
            }
        });

        Button closeButton = new Button(lang().getString("dialog.close"));
        closeButton.setOnAction(e -> dialog.close());

        buttonBox.getChildren().addAll(applyButton, closeButton);
        content.getChildren().add(buttonBox);

        mainContainer.getChildren().add(content);

        Scene scene = new Scene(mainContainer, 520, 380);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private List<Integer> parsePageRange(String range, int totalPages) {
        List<Integer> pages = new ArrayList<>();
        if (range == null || range.trim().isEmpty()) {
            return pages;
        }

        String[] parts = range.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.contains("-")) {
                String[] bounds = part.split("-");
                if (bounds.length == 2) {
                    try {
                        int start = Integer.parseInt(bounds[0].trim()) - 1;
                        int end = Integer.parseInt(bounds[1].trim()) - 1;
                        for (int i = start; i <= end && i < totalPages; i++) {
                            if (i >= 0 && !pages.contains(i)) {
                                pages.add(i);
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }
            } else {
                try {
                    int page = Integer.parseInt(part) - 1;
                    if (page >= 0 && page < totalPages && !pages.contains(page)) {
                        pages.add(page);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return pages;
    }

    public void shutdown() {
        ocrService.shutdown();
    }
}
