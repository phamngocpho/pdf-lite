package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;

/**
 * Dialog for encrypting PDF files with password protection.
 */
public class EncryptionDialog extends Dialog<EncryptionDialog.EncryptionResult> {

    private final PasswordField ownerPasswordField;
    private final PasswordField ownerPasswordConfirmField;
    private final PasswordField userPasswordField;
    private final PasswordField userPasswordConfirmField;
    private final CheckBox allowPrintingCheck;
    private final CheckBox allowModifyCheck;
    private final CheckBox allowCopyCheck;
    private final CheckBox allowAnnotationsCheck;

    public EncryptionDialog() {
        setTitle("Mã hóa PDF");
        setHeaderText("Đặt mật khẩu bảo vệ cho file PDF");

        // Set the button types
        ButtonType encryptButtonType = new ButtonType("Mã hóa", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(encryptButtonType, ButtonType.CANCEL);

        // Create the form
        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));

        // Owner password section
        Label ownerLabel = new Label("Mật khẩu chủ sở hữu (Owner Password):");
        ownerLabel.setStyle("-fx-font-weight: bold;");
        Label ownerDesc = new Label("Có toàn quyền truy cập, có thể xóa mật khẩu");
        ownerDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");

        GridPane ownerGrid = new GridPane();
        ownerGrid.setHgap(10);
        ownerGrid.setVgap(10);
        ownerGrid.setPadding(new Insets(5, 0, 0, 20));

        ownerPasswordField = new PasswordField();
        ownerPasswordField.setPromptText("Nhập mật khẩu chủ");
        ownerPasswordField.setPrefWidth(250);

        ownerPasswordConfirmField = new PasswordField();
        ownerPasswordConfirmField.setPromptText("Xác nhận mật khẩu");
        ownerPasswordConfirmField.setPrefWidth(250);

        ownerGrid.add(new Label("Mật khẩu:"), 0, 0);
        ownerGrid.add(ownerPasswordField, 1, 0);
        ownerGrid.add(new Label("Xác nhận:"), 0, 1);
        ownerGrid.add(ownerPasswordConfirmField, 1, 1);

        // User password section
        Label userLabel = new Label("Mật khẩu người dùng (User Password) - Tùy chọn:");
        userLabel.setStyle("-fx-font-weight: bold;");
        Label userDesc = new Label("Quyền hạn chế theo cài đặt bên dưới");
        userDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");

        GridPane userGrid = new GridPane();
        userGrid.setHgap(10);
        userGrid.setVgap(10);
        userGrid.setPadding(new Insets(5, 0, 0, 20));

        userPasswordField = new PasswordField();
        userPasswordField.setPromptText("Nhập mật khẩu người dùng (để trống nếu không cần)");
        userPasswordField.setPrefWidth(250);

        userPasswordConfirmField = new PasswordField();
        userPasswordConfirmField.setPromptText("Xác nhận mật khẩu");
        userPasswordConfirmField.setPrefWidth(250);

        userGrid.add(new Label("Mật khẩu:"), 0, 0);
        userGrid.add(userPasswordField, 1, 0);
        userGrid.add(new Label("Xác nhận:"), 0, 1);
        userGrid.add(userPasswordConfirmField, 1, 1);

        // Permissions section
        Label permLabel = new Label("Quyền hạn cho người dùng:");
        permLabel.setStyle("-fx-font-weight: bold;");

        VBox permBox = new VBox(8);
        permBox.setPadding(new Insets(5, 0, 0, 20));

        allowPrintingCheck = new CheckBox("Cho phép in (Allow Printing)");
        allowPrintingCheck.setSelected(true);

        allowModifyCheck = new CheckBox("Cho phép chỉnh sửa nội dung (Allow Modify)");
        allowModifyCheck.setSelected(false);

        allowCopyCheck = new CheckBox("Cho phép sao chép văn bản (Allow Copy)");
        allowCopyCheck.setSelected(false);

        allowAnnotationsCheck = new CheckBox("Cho phép thêm chú thích (Allow Annotations)");
        allowAnnotationsCheck.setSelected(false);

        permBox.getChildren().addAll(
                allowPrintingCheck,
                allowModifyCheck,
                allowCopyCheck,
                allowAnnotationsCheck
        );

        // Add all sections to the main vbox
        vbox.getChildren().addAll(
                ownerLabel, ownerDesc, ownerGrid,
                new Separator(),
                userLabel, userDesc, userGrid,
                new Separator(),
                permLabel, permBox
        );

        getDialogPane().setContent(vbox);
        getDialogPane().setPrefWidth(500);

        // Validation
        javafx.scene.Node encryptButton = getDialogPane().lookupButton(encryptButtonType);
        encryptButton.setDisable(true);

        // Enable encrypt button only if owner password is valid
        ownerPasswordField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(encryptButton));
        ownerPasswordConfirmField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(encryptButton));
        userPasswordField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(encryptButton));
        userPasswordConfirmField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(encryptButton));

        // Request focus on the owner password field
        javafx.application.Platform.runLater(ownerPasswordField::requestFocus);

        // Convert result
        setResultConverter(dialogButton -> {
            if (dialogButton == encryptButtonType) {
                if (!validatePasswords()) {
                    return null;
                }

                AccessPermission permissions = new AccessPermission();
                permissions.setCanPrint(allowPrintingCheck.isSelected());
                permissions.setCanModify(allowModifyCheck.isSelected());
                permissions.setCanExtractContent(allowCopyCheck.isSelected());
                permissions.setCanModifyAnnotations(allowAnnotationsCheck.isSelected());

                String userPwd = userPasswordField.getText().trim();
                return new EncryptionResult(
                        ownerPasswordField.getText(),
                        userPwd.isEmpty() ? ownerPasswordField.getText() : userPwd,
                        permissions
                );
            }
            return null;
        });
    }

    private void validateForm(javafx.scene.Node encryptButton) {
        String ownerPwd = ownerPasswordField.getText();
        String ownerConfirm = ownerPasswordConfirmField.getText();
        String userPwd = userPasswordField.getText();
        String userConfirm = userPasswordConfirmField.getText();

        boolean ownerValid = !ownerPwd.isEmpty() && ownerPwd.equals(ownerConfirm);
        boolean userValid = userPwd.isEmpty() || userPwd.equals(userConfirm);

        encryptButton.setDisable(!(ownerValid && userValid));
    }

    private boolean validatePasswords() {
        String ownerPwd = ownerPasswordField.getText();
        String ownerConfirm = ownerPasswordConfirmField.getText();
        String userPwd = userPasswordField.getText();
        String userConfirm = userPasswordConfirmField.getText();

        if (ownerPwd.isEmpty()) {
            showError("Vui lòng nhập mật khẩu chủ sở hữu");
            return false;
        }

        if (!ownerPwd.equals(ownerConfirm)) {
            showError("Mật khẩu chủ sở hữu không khớp");
            return false;
        }

        if (!userPwd.isEmpty() && !userPwd.equals(userConfirm)) {
            showError("Mật khẩu người dùng không khớp");
            return false;
        }

        return true;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Result class containing encryption parameters.
     */
    public record EncryptionResult(String ownerPassword, String userPassword, AccessPermission permissions) {
    }
}

