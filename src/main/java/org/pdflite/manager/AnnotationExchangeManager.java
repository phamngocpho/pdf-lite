package org.pdflite.manager;

import javafx.scene.control.*;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.pdflite.dialog.CustomInfoDialog;
import org.pdflite.model.PDFDocument;
import org.pdflite.util.DialogTitleBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Coordinates XFDF import/export dialogs and applies changes to current document.
 */
public class AnnotationExchangeManager {
    private static final Logger logger = LoggerFactory.getLogger(AnnotationExchangeManager.class);

    private final javafx.scene.layout.BorderPane rootPane;
    private final UIStateManager uiStateManager;
    private final Supplier<PDFDocument> documentSupplier;
    private final Supplier<AnnotationManager> annotationManagerSupplier;
    private final AnnotationXFDFManager xfdfManager;
    private ThemeManager themeManager;

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    public AnnotationExchangeManager(javafx.scene.layout.BorderPane rootPane,
                                     UIStateManager uiStateManager,
                                     Supplier<PDFDocument> documentSupplier,
                                     Supplier<AnnotationManager> annotationManagerSupplier) {
        this.rootPane = rootPane;
        this.uiStateManager = uiStateManager;
        this.documentSupplier = documentSupplier;
        this.annotationManagerSupplier = annotationManagerSupplier;
        this.xfdfManager = new AnnotationXFDFManager();
    }

    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    public void handleExportAnnotations() {
        PDFDocument document = documentSupplier.get();
        if (document == null) {
            uiStateManager.showError(lang().getString("error.noDocument"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        ExportDialogResult options = showExportDialog(document.getTotalPages());
        if (options == null) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(lang().getString("annotation.xfdf.exportTitle"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XFDF Files", "*.xfdf"));
        chooser.setInitialFileName(getInitialExportName(document));
        File output = chooser.showSaveDialog(getOwnerWindow());
        if (output == null) {
            return;
        }

        try {
            AnnotationXFDFManager.ExportSummary summary =
                    xfdfManager.exportAnnotations(document, output, new AnnotationXFDFManager.ExportOptions(options.types(), options.pages()));

            String message = MessageFormat.format(lang().getString("annotation.xfdf.exportResult"),
                    summary.exportedCount(), summary.skippedCount());
            uiStateManager.updateStatus(message);
            CustomInfoDialog.show(lang().getString("annotation.xfdf.exportSuccess"), null, message, themeManager);
        } catch (Exception e) {
            logger.error("Failed to export annotations to XFDF", e);
            uiStateManager.showError(lang().getString("annotation.xfdf.exportFailed"), e.getMessage());
        }
    }

    public void handleImportAnnotations() {
        PDFDocument document = documentSupplier.get();
        if (document == null) {
            uiStateManager.showError(lang().getString("error.noDocument"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        ImportDialogResult options = showImportDialog();
        if (options == null) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(lang().getString("annotation.xfdf.importTitle"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XFDF Files", "*.xfdf"),
                new FileChooser.ExtensionFilter("FDF Files", "*.fdf")
        );
        File input = chooser.showOpenDialog(getOwnerWindow());
        if (input == null) {
            return;
        }

        try {
            AnnotationXFDFManager.ImportSummary summary = xfdfManager.importAnnotations(
                    document,
                    input,
                    new AnnotationXFDFManager.ImportOptions(options.replaceExisting(), options.skipDuplicates())
            );

            if (summary.importedCount() > 0) {
                document.setHasUnsavedEdits(true);
            }
            refreshAffectedPages(summary.affectedPages());

            String message = MessageFormat.format(lang().getString("annotation.xfdf.importResult"),
                    summary.importedCount(), summary.replacedCount(), summary.duplicateCount(), summary.invalidCount());
            uiStateManager.updateStatus(message);
            CustomInfoDialog.show(lang().getString("annotation.xfdf.importSuccess"), null, message, themeManager);
        } catch (Exception e) {
            logger.error("Failed to import annotations from XFDF", e);
            uiStateManager.showError(lang().getString("annotation.xfdf.importFailed"), e.getMessage());
        }
    }

    private ExportDialogResult showExportDialog(int totalPages) {
        Stage dialogStage = createDialogStage(lang().getString("annotation.xfdf.exportTitle"));
        final ExportDialogResult[] result = new ExportDialogResult[1];

        CheckBox highlight = new CheckBox(lang().getString("annotation.type.highlight"));
        CheckBox comment = new CheckBox(lang().getString("annotation.type.comment"));
        CheckBox rectangle = new CheckBox(lang().getString("annotation.type.rectangle"));
        CheckBox circle = new CheckBox(lang().getString("annotation.type.circle"));
        CheckBox arrow = new CheckBox(lang().getString("annotation.type.arrow"));
        CheckBox freehand = new CheckBox(lang().getString("annotation.type.freehand"));
        highlight.setSelected(true);
        comment.setSelected(true);
        rectangle.setSelected(true);
        circle.setSelected(true);
        arrow.setSelected(true);
        freehand.setSelected(true);

        TextField pageRange = new TextField("all");
        pageRange.setPromptText(lang().getString("annotation.xfdf.pageRangeHint"));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.getStyleClass().add("shortcut-group-card");
        grid.setPadding(new Insets(12));
        grid.add(new Label(lang().getString("annotation.xfdf.pageRange")), 0, 0);
        grid.add(pageRange, 1, 0);
        grid.add(new Label(lang().getString("annotation.xfdf.filterTypes")), 0, 1);
        grid.add(new HBox(8, highlight, comment, rectangle, circle, arrow, freehand), 1, 1);

        VBox content = new VBox(16, grid);
        content.setPadding(new Insets(16));
        content.getStyleClass().add("shortcut-help-content");

        Button okButton = new Button(lang().getString("dialog.ok"));
        okButton.getStyleClass().add("primary-button");
        okButton.setPrefWidth(80);
        okButton.setOnAction(event -> {
            Set<String> types = new LinkedHashSet<>();
            if (highlight.isSelected()) types.add("HIGHLIGHT");
            if (comment.isSelected()) types.add("COMMENT");
            if (rectangle.isSelected()) types.add("RECTANGLE");
            if (circle.isSelected()) types.add("CIRCLE");
            if (arrow.isSelected()) types.add("ARROW");
            if (freehand.isSelected()) types.add("FREEHAND");
            if (types.isEmpty()) {
                uiStateManager.showError(lang().getString("annotation.xfdf.exportFailed"),
                        lang().getString("annotation.xfdf.error.noTypeSelected"));
                return;
            }

            try {
                Set<Integer> pages = parsePageRange(pageRange.getText(), totalPages);
                result[0] = new ExportDialogResult(types, pages);
                dialogStage.close();
            } catch (Exception e) {
                uiStateManager.showError(lang().getString("annotation.xfdf.exportFailed"), e.getMessage());
            }
        });

        Button cancelButton = new Button(lang().getString("dialog.cancel"));
        cancelButton.setPrefWidth(80);
        cancelButton.setOnAction(event -> dialogStage.close());

        showCustomDialog(dialogStage, lang().getString("annotation.xfdf.exportTitle"), content, okButton, cancelButton);
        return result[0];
    }

    private ImportDialogResult showImportDialog() {
        Stage dialogStage = createDialogStage(lang().getString("annotation.xfdf.importTitle"));
        final ImportDialogResult[] result = new ImportDialogResult[1];

        RadioButton merge = new RadioButton(lang().getString("annotation.xfdf.mode.merge"));
        RadioButton replace = new RadioButton(lang().getString("annotation.xfdf.mode.replace"));
        ToggleGroup group = new ToggleGroup();
        merge.setToggleGroup(group);
        replace.setToggleGroup(group);
        merge.setSelected(true);

        CheckBox skipDuplicates = new CheckBox(lang().getString("annotation.xfdf.skipDuplicates"));
        skipDuplicates.setSelected(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.getStyleClass().add("shortcut-group-card");
        grid.setPadding(new Insets(12));
        grid.add(new Label(lang().getString("annotation.xfdf.importMode")), 0, 0);
        grid.add(new HBox(8, merge, replace), 1, 0);
        grid.add(skipDuplicates, 1, 1);

        VBox content = new VBox(16, grid);
        content.setPadding(new Insets(16));
        content.getStyleClass().add("shortcut-help-content");

        Button okButton = new Button(lang().getString("dialog.ok"));
        okButton.getStyleClass().add("primary-button");
        okButton.setPrefWidth(80);
        okButton.setOnAction(event -> {
            result[0] = new ImportDialogResult(replace.isSelected(), skipDuplicates.isSelected());
            dialogStage.close();
        });

        Button cancelButton = new Button(lang().getString("dialog.cancel"));
        cancelButton.setPrefWidth(80);
        cancelButton.setOnAction(event -> dialogStage.close());

        showCustomDialog(dialogStage, lang().getString("annotation.xfdf.importTitle"), content, okButton, cancelButton);
        return result[0];
    }

    private Stage createDialogStage(String title) {
        Stage dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.TRANSPARENT);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle(title);
        Window owner = getOwnerWindow();
        if (owner != null) {
            dialogStage.initOwner(owner);
        }
        return dialogStage;
    }

    private void showCustomDialog(Stage dialogStage, String title, VBox content, Button okButton, Button cancelButton) {
        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().addAll("custom-info-dialog", "shortcut-help-dialog");

        DialogTitleBar titleBar = new DialogTitleBar(title, dialogStage);
        mainContainer.getChildren().add(titleBar.getTitleBar());

        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 16, 16, 16));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        footer.getChildren().addAll(spacer, okButton, cancelButton);

        mainContainer.getChildren().addAll(content, footer);

        Scene scene = new Scene(mainContainer, 640, 220);
        scene.setFill(Color.TRANSPARENT);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                dialogStage.close();
                event.consume();
            }
        });

        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }

        dialogStage.setScene(scene);
        dialogStage.showAndWait();
    }

    private Set<Integer> parsePageRange(String raw, int totalPages) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw.trim())) {
            return Set.of();
        }

        Set<Integer> pages = new LinkedHashSet<>();
        String[] segments = raw.split(",");
        for (String segment : segments) {
            String part = segment.trim();
            if (part.isEmpty()) continue;
            if (part.contains("-")) {
                String[] pair = part.split("-", 2);
                int start = Integer.parseInt(pair[0].trim());
                int end = Integer.parseInt(pair[1].trim());
                if (start > end) {
                    int swap = start;
                    start = end;
                    end = swap;
                }
                for (int page = start; page <= end; page++) {
                    validatePage(page, totalPages);
                    pages.add(page - 1);
                }
            } else {
                int page = Integer.parseInt(part);
                validatePage(page, totalPages);
                pages.add(page - 1);
            }
        }
        return Set.copyOf(pages);
    }

    private void validatePage(int pageOneBased, int totalPages) {
        if (pageOneBased < 1 || pageOneBased > totalPages) {
            throw new IllegalArgumentException(
                    MessageFormat.format(lang().getString("annotation.xfdf.error.invalidPage"), pageOneBased, totalPages));
        }
    }

    private void refreshAffectedPages(Set<Integer> pages) {
        AnnotationManager annotationManager = annotationManagerSupplier.get();
        if (annotationManager == null || pages == null || pages.isEmpty()) {
            return;
        }
        for (Integer page : pages) {
            if (page != null && page >= 0) {
                annotationManager.refreshPageAnnotations(page);
            }
        }
    }

    private String getInitialExportName(PDFDocument document) {
        String base = document.getFile() != null ? document.getFile().getName() : "annotations";
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        return base + "_annotations.xfdf";
    }

    private Window getOwnerWindow() {
        return rootPane != null && rootPane.getScene() != null ? rootPane.getScene().getWindow() : null;
    }

    private record ExportDialogResult(Set<String> types, Set<Integer> pages) {}
    private record ImportDialogResult(boolean replaceExisting, boolean skipDuplicates) {}
}
