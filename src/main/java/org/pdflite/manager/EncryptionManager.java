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
            uiStateManager.showError("No PDF Loaded",
                    "Please open a PDF file first.");
            return;
        }

        StringBuilder info = new StringBuilder();
        info.append("Thông tin bảo mật PDF:\n\n");

        if (!currentDocument.getDocument().isEncrypted()) {
            info.append("File không được mã hóa\n");
            info.append("Không có mật khẩu bảo vệ");
        } else {
            info.append("File được mã hóa\n\n");

            AccessPermission perm =
                    currentDocument.getDocument().getCurrentAccessPermission();

            if (perm != null) {
                if (perm.isOwnerPermission()) {
                    info.append("Quyền: OWNER (Toàn quyền)\n\n");
                } else {
                    info.append("Quyền: USER (Hạn chế)\n\n");
                }

                info.append("Quyền được cấp:\n");
                info.append("  - In ấn: ").append(perm.canPrint() ? "Có" : "Không").append("\n");
                info.append("  - Chỉnh sửa: ").append(perm.canModify() ? "Có" : "Không").append("\n");
                info.append("  - Sao chép text: ").append(perm.canExtractContent() ? "Có" : "Không").append("\n");
                info.append("  - Chú thích: ").append(perm.canModifyAnnotations() ? "Có" : "Không").append("\n");
                info.append("  - Điền form: ").append(perm.canFillInForm() ? "Có" : "Không").append("\n");
            }
        }

        org.pdflite.dialog.CustomInfoDialog.show(
            "Quyền PDF",
            "Thông tin bảo mật và quyền truy cập",
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
            uiStateManager.showError("No PDF Loaded",
                    "Please open a PDF file first before encrypting.");
            return;
        }

        EncryptionDialog dialog = new EncryptionDialog();

        dialog.showAndWait(themeManager).ifPresent(result -> {
            try {
                Stage stage = (Stage) rootPane.getScene().getWindow();
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save Encrypted PDF As");
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
                    "Thành công",
                    "PDF đã được mã hóa",
                    "File đã được lưu tại:\n" + outputFile.getAbsolutePath(),
                    themeManager
                );

                logger.info("Successfully encrypted PDF: {}", outputFile.getName());

            } catch (IOException e) {
                logger.error("Error encrypting PDF", e);
                uiStateManager.showError("Encryption Error",
                        "Could not encrypt PDF: " + e.getMessage());
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
            uiStateManager.showError("No PDF Loaded",
                    "Please open a PDF file first before removing encryption.");
            return;
        }

        if (!currentDocument.getDocument().isEncrypted()) {
            org.pdflite.dialog.CustomInfoDialog.show(
                "Thông tin",
                "PDF không được mã hóa",
                "File PDF này không có mật khẩu bảo vệ.",
                themeManager
            );
            return;
        }

        // Check if user has owner permission
        AccessPermission permission =
                currentDocument.getDocument().getCurrentAccessPermission();

        if (permission == null || !permission.isOwnerPermission()) {
            org.pdflite.dialog.CustomInfoDialog.show(
                "Không có quyền",
                "Không thể xóa mật khẩu",
                "Bạn cần mật khẩu chủ sở hữu (Owner Password) để xóa bảo vệ.\n" +
                "Hiện tại bạn chỉ có quyền người dùng (User Permission).",
                themeManager
            );
            return;
        }

        // Confirm action
        boolean confirmed = org.pdflite.dialog.CustomConfirmDialog.show(
            "Xác nhận",
            "Xóa mật khẩu bảo vệ",
            "Bạn có chắc muốn xóa mật khẩu bảo vệ khỏi file PDF này?\n" +
            "File mới sẽ không có mật khẩu.",
            themeManager
        );

        if (confirmed) {
            try {
                Stage stage = (Stage) rootPane.getScene().getWindow();
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save Decrypted PDF As");
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
                    "Thành công",
                    "Đã xóa mật khẩu",
                    "File không có mật khẩu đã được lưu tại:\n" + outputFile.getAbsolutePath(),
                    themeManager
                );

                logger.info("Successfully removed encryption from PDF: {}", outputFile.getName());

            } catch (IOException e) {
                logger.error("Error removing encryption", e);
                uiStateManager.showError("Decryption Error",
                        "Could not remove encryption: " + e.getMessage());
            }
        }
    }
}

