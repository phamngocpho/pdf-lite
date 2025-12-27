package org.pdflite.manager;

import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.pdflite.dialog.EncryptionDialog;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.pdflite.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Manages PDF encryption and decryption operations.
 * Handles showing permissions, encrypting, and decrypting PDF files.
 */
public record EncryptionManager(BorderPane rootPane, PDFService pdfService, ThemeManager themeManager,
                                UIStateManager uiStateManager) {
    private static final Logger logger = LoggerFactory.getLogger(EncryptionManager.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    /**
     * Creates a new EncryptionManager.
     *
     * @param rootPane       the root pane for the dialog owner
     * @param pdfService     the PDF service for encryption operations
     * @param themeManager   the theme manager for applying themes to dialogs
     * @param uiStateManager the UI state manager for status updates and errors
     */
    public EncryptionManager {
    }

    /**
     * Shows PDF permissions information.
     *
     * @param currentDocument the current PDF document
     */
    public void showPDFPermissions(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError(lang().getString("error.noPdfLoaded"),
                    lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        StringBuilder info = new StringBuilder();

        if (!currentDocument.getDocument().isEncrypted()) {
            info.append(lang().getString("permissions.notEncrypted")).append("\n");
        } else {
            info.append(lang().getString("permissions.encrypted")).append("\n\n");

            AccessPermission perm =
                    currentDocument.getDocument().getCurrentAccessPermission();

            if (perm != null) {
                if (perm.isOwnerPermission()) {
                    info.append("OWNER\n\n");
                } else {
                    info.append("USER\n\n");
                }

                info.append(lang().getString("permissions.info")).append("\n");
                info.append("  - ").append(lang().getString("menu.file.print")).append(": ").append(perm.canPrint() ? "✓" : "✗").append("\n");
                info.append("  - ").append(lang().getString("menu.edit")).append(": ").append(perm.canModify() ? "✓" : "✗").append("\n");
                info.append("  - ").append(lang().getString("menu.tools.extract")).append(": ").append(perm.canExtractContent() ? "✓" : "✗").append("\n");
            }
        }

        org.pdflite.dialog.CustomInfoDialog.show(
                lang().getString("permissions.title"),
                lang().getString("permissions.title"),
                info.toString(),
                themeManager
        );
    }

    /**
     * Encrypts a PDF file.
     *
     * @param currentDocument the current PDF document
     */
    public void encryptPDF(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError(lang().getString("error.noPdfLoaded"),
                    lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        EncryptionDialog dialog = new EncryptionDialog();

        dialog.showAndWait(themeManager).ifPresent(result -> {
            try {
                Stage stage = (Stage) rootPane.getScene().getWindow();
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle(lang().getString("dialog.title.save"));
                fileChooser.setInitialFileName("encrypted_" + currentDocument.getFileName());
                fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter(Constants.PDF_DESCRIPTION, Constants.PDF_EXTENSION)
                );

                File outputFile = fileChooser.showSaveDialog(stage);
                if (outputFile == null) {
                    return; // User cancelled
                }

                // Encrypt the PDF
                pdfService.encryptPDF(
                        currentDocument.getFile(),
                        outputFile,
                        result.ownerPassword(),
                        result.userPassword(),
                        result.permissions()
                );

                org.pdflite.dialog.CustomInfoDialog.show(
                        lang().getString("success.title"),
                        lang().getString("success.encrypted"),
                        lang().getString("success.saved") + ":\n" + outputFile.getAbsolutePath(),
                        themeManager
                );

                logger.info("Successfully encrypted PDF: {}", outputFile.getName());

            } catch (IOException e) {
                logger.error("Error encrypting PDF", e);
                uiStateManager.showError(lang().getString("error.title"),
                        lang().getString("error.encrypt") + ": " + e.getMessage());
            }
        });
    }

    /**
     * Decrypts a PDF file (removes encryption).
     *
     * @param currentDocument the current PDF document
     */
    public void decryptPDF(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError(lang().getString("error.noPdfLoaded"),
                    lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        if (!currentDocument.getDocument().isEncrypted()) {
            org.pdflite.dialog.CustomInfoDialog.show(
                    lang().getString("dialog.info"),
                    lang().getString("menu.tools.decrypt"),
                    lang().getString("message.pdfNotEncrypted"),
                    themeManager
            );
            return;
        }

        // Check if user has owner permission
        AccessPermission permission =
                currentDocument.getDocument().getCurrentAccessPermission();

        if (permission == null || !permission.isOwnerPermission()) {
            org.pdflite.dialog.CustomInfoDialog.show(
                    lang().getString("error.title"),
                    lang().getString("message.passwordRequired"),
                    lang().getString("message.incorrectPassword"),
                    themeManager
            );
            return;
        }

        // Confirm action
        boolean confirmed = org.pdflite.dialog.CustomConfirmDialog.show(
                lang().getString("confirm.title"),
                lang().getString("menu.tools.decrypt"),
                lang().getString("confirm.decrypt"),
                themeManager
        );

        if (confirmed) {
            try {
                Stage stage = (Stage) rootPane.getScene().getWindow();
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle(lang().getString("dialog.title.save"));
                fileChooser.setInitialFileName("decrypted_" + currentDocument.getFileName());
                fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter(Constants.PDF_DESCRIPTION, Constants.PDF_EXTENSION)
                );

                File outputFile = fileChooser.showSaveDialog(stage);
                if (outputFile == null) {
                    return; // User cancelled
                }

                // Remove all security before saving
                currentDocument.getDocument().setAllSecurityToBeRemoved(true);
                pdfService.saveAs(currentDocument, outputFile);

                org.pdflite.dialog.CustomInfoDialog.show(
                        lang().getString("success.title"),
                        lang().getString("success.decrypted"),
                        lang().getString("success.saved") + ":\n" + outputFile.getAbsolutePath(),
                        themeManager
                );

                logger.info("Successfully removed encryption from PDF: {}", outputFile.getName());

            } catch (IOException e) {
                logger.error("Error removing encryption", e);
                uiStateManager.showError(lang().getString("error.title"),
                        lang().getString("error.decrypt") + ": " + e.getMessage());
            }
        }
    }
}

