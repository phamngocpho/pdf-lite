package org.pdflite.manager;

import java.io.IOException;

import javafx.application.Platform;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.pdflite.controller.ExtractDialogController;
import org.pdflite.controller.InsertDialogController;
import org.pdflite.controller.MergeDialogController;
import org.pdflite.controller.PrintDialogController;
import org.pdflite.controller.SplitDialogController;
import org.pdflite.dialog.PageReorderDialog;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFPrintService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Manages dialog operations for PDF Lite.
 * Handles opening merge, split, extract, and print dialogs.
 */
public record DialogManager(BorderPane rootPane, ThemeManager themeManager, UIStateManager uiStateManager) {
    private static final Logger logger = LoggerFactory.getLogger(DialogManager.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    /**
     * Creates a new DialogManager.
     *
     * @param rootPane       the root pane for the dialog owner
     * @param themeManager   the theme manager for applying themes to dialogs
     * @param uiStateManager the UI state manager for error messages
     */
    public DialogManager {
    }

    /**
     * Opens the merge PDF dialog.
     */
    public void openMergeDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/merge-dialog.fxml"));
            Parent root = loader.load();

            MergeDialogController controller = loader.getController();
            Stage dialogStage = createDialogStage(root, lang().getString("menu.tools.merge"));

            controller.setDialogStage(dialogStage);

            dialogStage.setOnCloseRequest(event -> controller.shutdown());
            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Error opening merge dialog", e);
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.openMerge") + ": " + e.getMessage());
        }
    }

    /**
     * Opens the split PDF dialog.
     *
     * @param currentDocument the current PDF document
     */
    public void openSplitDialog(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError(lang().getString("error.noPdfLoaded"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        // Check permissions for encrypted PDFs
        if (currentDocument.getDocument().isEncrypted()) {
            AccessPermission permission = currentDocument.getDocument().getCurrentAccessPermission();
            if (permission != null && !permission.canExtractContent() && !permission.isOwnerPermission()) {
                org.pdflite.dialog.CustomInfoDialog.show(
                        lang().getString("error.noPermission"),
                        lang().getString("menu.tools.split"),
                        lang().getString("error.noPermissionExtract"),
                        themeManager
                );
                return;
            }
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/split-dialog.fxml"));
            Parent root = loader.load();

            SplitDialogController controller = loader.getController();
            Stage dialogStage = createDialogStage(root, lang().getString("menu.tools.split"));

            controller.setDialogStage(dialogStage);
            controller.setThemeManager(themeManager);

            // Use PDDocument for encrypted PDFs, File for regular PDFs
            if (currentDocument.getDocument().isEncrypted()) {
                controller.setSourceDocument(currentDocument.getDocument(), currentDocument.getFile());
            } else {
                controller.setSourceFile(currentDocument.getFile());
            }

            dialogStage.setOnCloseRequest(event -> controller.shutdown());
            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Error opening split dialog", e);
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.openSplit") + ": " + e.getMessage());
        }
    }

    /**
     * Opens the extract pages dialog.
     *
     * @param currentDocument the current PDF document
     */
    public void openExtractDialog(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError(lang().getString("error.noPdfLoaded"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        // Check permissions for encrypted PDFs
        if (currentDocument.getDocument().isEncrypted()) {
            AccessPermission permission = currentDocument.getDocument().getCurrentAccessPermission();
            if (permission != null && !permission.canExtractContent() && !permission.isOwnerPermission()) {
                org.pdflite.dialog.CustomInfoDialog.show(
                        lang().getString("error.noPermission"),
                        lang().getString("menu.tools.extract"),
                        lang().getString("error.noPermissionExtract"),
                        themeManager
                );
                return;
            }
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/extract-dialog.fxml"));
            Parent root = loader.load();

            ExtractDialogController controller = loader.getController();
            Stage dialogStage = createDialogStage(root, lang().getString("menu.tools.extract"));

            controller.setDialogStage(dialogStage);
            controller.setThemeManager(themeManager);

            // Use PDDocument for encrypted PDFs, File for regular PDFs
            if (currentDocument.getDocument().isEncrypted()) {
                controller.setSourceDocument(currentDocument.getDocument(), currentDocument.getFile());
            } else {
                controller.setSourceFile(currentDocument.getFile());
            }

            dialogStage.setOnCloseRequest(event -> controller.shutdown());
            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Error opening extract dialog", e);
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.openExtract") + ": " + e.getMessage());
        }
    }

    /**
     * Opens the page reorder dialog.
     *
     * @param currentDocument the current PDF document
     * @param onSuccess       callback to run when reorder is successful
     */
    public void openPageReorderDialog(PDFDocument currentDocument, Runnable onSuccess) {
        if (currentDocument == null) {
            uiStateManager.showError(lang().getString("error.noPdfLoaded"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        // Check permissions for encrypted PDFs
        if (currentDocument.getDocument().isEncrypted()) {
            AccessPermission permission = currentDocument.getDocument().getCurrentAccessPermission();
            if (permission != null && !permission.canModify() && !permission.isOwnerPermission()) {
                org.pdflite.dialog.CustomInfoDialog.show(
                        lang().getString("error.noPermission"),
                        lang().getString("menu.tools.reorder"),
                        lang().getString("error.noPermissionModify"),
                        themeManager
                );
                return;
            }
        }

        try {
            PageReorderDialog.show(
                    (Stage) rootPane.getScene().getWindow(),
                    currentDocument.getDocument(),
                    currentDocument.getFile(),
                    themeManager,
                    onSuccess
            );
        } catch (Exception e) {
            logger.error("Error opening page reorder dialog", e);
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.openReorder") + ": " + e.getMessage());
        }
    }

    /**
     * Opens the print dialog.
     *
     * @param currentDocument the current PDF document
     * @param printService    the print service
     * @param currentPage     the current page index
     */
    public void openPrintDialog(PDFDocument currentDocument, PDFPrintService printService, int currentPage) {
        if (currentDocument == null) {
            uiStateManager.showError(lang().getString("error.noPdfLoaded"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        // Check if printing is available
        if (!printService.isPrintingAvailable()) {
            uiStateManager.showError(lang().getString("error.noPrinter"), lang().getString("error.noPrinterMsg"));
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/print-dialog.fxml"));
            Parent root = loader.load();

            PrintDialogController controller = loader.getController();
            Stage dialogStage = createDialogStage(root, lang().getString("menu.file.print"));

            controller.setDialogStage(dialogStage);
            controller.setDocument(currentDocument, printService, currentPage);

            dialogStage.showAndWait();

            // Check if the user clicked print
            if (controller.isPrintClicked()) {
                uiStateManager.updateStatus(lang().getString("success.printed"));
                logger.info("Print job completed");
            }

        } catch (IOException e) {
            logger.error("Error opening print dialog", e);
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.openPrint") + ": " + e.getMessage());
        }
    }

    /**
     * Creates and configures a dialog stage with standard settings and custom title bar.
     *
     * @param root  The dialog root node
     * @param title The dialog title
     * @return Configured Stage object
     */
    public Stage createDialogStage(Parent root, String title) {
        Stage dialogStage = new Stage();
        dialogStage.setTitle(title);
        dialogStage.initStyle(javafx.stage.StageStyle.TRANSPARENT); // Transparent for rounded corners
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initOwner(rootPane.getScene().getWindow());

        Scene dialogScene = new Scene(root);
        dialogScene.setFill(javafx.scene.paint.Color.TRANSPARENT); // Transparent background
        dialogStage.setScene(dialogScene);

        if (themeManager != null) {
            themeManager.applyThemeToScene(dialogScene);
        }

        // Apply CSS and layout to ensure accurate size calculation
        // This is important for cross-platform compatibility
        root.applyCss();
        root.layout();

        // Workaround for Ubuntu/Linux dialog sizing issue
        // See: https://stackoverflow.com/questions/55190380/javafx-creates-alert-dialog-which-is-too-small
        // Set resizable to true initially to allow proper sizing on Linux
        dialogStage.setResizable(true);

        // Size the stage to fit its content
        // minWidth/minHeight are set in FXML files to ensure the minimum size on Ubuntu
        // sizeToScene() will calculate the actual size based on content
        dialogStage.sizeToScene();

        // After the dialog is shown, set resizable back to false (if desired)
        // This ensures proper sizing on Ubuntu while maintaining non-resizable behavior
        dialogStage.setOnShown(e -> Platform.runLater(() -> dialogStage.setResizable(false)));

        return dialogStage;
    }

    /**
     * Opens the insert blank page dialog.
     *
     * @param currentDocument the current PDF document
     * @return InsertDialogController if the dialog was opened successfully, null otherwise
     */
    public InsertDialogController openInsertDialog(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError(lang().getString("error.noDocument"), lang().getString("error.noPdfLoadedMsg"));
            return null;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/insert-dialog.fxml"));
            Parent root = loader.load();

            InsertDialogController controller = loader.getController();

            // Get the current page size to use as default in the dialog
            int currentPageIndex = currentDocument.getCurrentPage();
            org.apache.pdfbox.pdmodel.PDPage currentPage = currentDocument.getDocument().getPage(currentPageIndex);
            org.apache.pdfbox.pdmodel.common.PDRectangle currentMediaBox = currentPage.getMediaBox();
            controller.setDefaultSize(currentMediaBox.getWidth(), currentMediaBox.getHeight());

            Stage dialogStage = createDialogStage(root, lang().getString("pdftools.insertPage"));
            controller.setDialogStage(dialogStage);
            dialogStage.showAndWait();

            return controller;

        } catch (IOException e) {
            logger.error("Error opening insert dialog", e);
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.openInsert") + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Displays the About dialog.
     */
    public void showAboutDialog() {
        org.pdflite.dialog.CustomInfoDialog.show(
                lang().getString("about.title"),
                lang().getString("about.appName"),
                lang().getString("about.versionText") + "\n\n" +
                lang().getString("about.description") + "\n\n" +
                lang().getString("about.builtWith"),
                themeManager
        );
    }

    /**
     * Shows a warning dialog for saving encrypted PDFs.
     * Returns true if the user wants to continue, false if canceled.
     *
     * @return true if the user wants to continue saving, false if canceled
     */
    public boolean showEncryptedSaveWarning() {
        return org.pdflite.dialog.CustomConfirmDialog.show(
                lang().getString("dialog.warning"),
                lang().getString("message.passwordRequired"),
                lang().getString("message.unsavedChanges"),
                themeManager
        );
    }
}

