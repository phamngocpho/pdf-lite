package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.pdflite.manager.ThemeManager;
import org.pdflite.util.DialogTitleBar;

import java.util.Optional;

/**
 * Dialog for encrypting PDF files with password protection.
 */
public class EncryptionDialog {

    private final PasswordField ownerPasswordField;
    private final PasswordField ownerPasswordConfirmField;
    private final PasswordField userPasswordField;
    private final PasswordField userPasswordConfirmField;
    private final CheckBox allowPrintingCheck;
    private final CheckBox allowModifyCheck;
    private final CheckBox allowCopyCheck;
    private final CheckBox allowAnnotationsCheck;
    
    private Stage dialogStage;
    private EncryptionResult result;

    public EncryptionDialog() {
        ownerPasswordField = new PasswordField();
        ownerPasswordConfirmField = new PasswordField();
        userPasswordField = new PasswordField();
        userPasswordConfirmField = new PasswordField();
        allowPrintingCheck = new CheckBox("Cho phép in (Allow Printing)");
        allowModifyCheck = new CheckBox("Cho phép chỉnh sửa nội dung (Allow Modify)");
        allowCopyCheck = new CheckBox("Cho phép sao chép văn bản (Allow Copy)");
        allowAnnotationsCheck = new CheckBox("Cho phép thêm chú thích (Allow Annotations)");
    }

    public Optional<EncryptionResult> showAndWait(ThemeManager themeManager) {
        dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.TRANSPARENT);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("Mã hóa PDF");
        
        // Create main container
        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("encryption-dialog");
        
        // Create custom title bar
        DialogTitleBar titleBar = new DialogTitleBar("Mã hóa PDF", dialogStage);
        mainContainer.getChildren().add(titleBar.getTitleBar());
        
        // Create content
        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));

        Label headerLabel = new Label("Đặt mật khẩu bảo vệ cho file PDF");
        headerLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");

        // Owner password section
        Label ownerLabel = new Label("Mật khẩu chủ sở hữu (Owner Password):");
        ownerLabel.setStyle("-fx-font-weight: bold;");
        Label ownerDesc = new Label("Có toàn quyền truy cập, có thể xóa mật khẩu");
        ownerDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");

        GridPane ownerGrid = new GridPane();
        ownerGrid.setHgap(10);
        ownerGrid.setVgap(10);
        ownerGrid.setPadding(new Insets(5, 0, 0, 20));

        ownerPasswordField.setPromptText("Nhập mật khẩu chủ");
        ownerPasswordField.setPrefWidth(250);

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

        userPasswordField.setPromptText("Nhập mật khẩu người dùng (để trống nếu không cần)");
        userPasswordField.setPrefWidth(250);

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

        allowPrintingCheck.setSelected(true);
        allowModifyCheck.setSelected(false);
        allowCopyCheck.setSelected(false);
        allowAnnotationsCheck.setSelected(false);

        permBox.getChildren().addAll(
                allowPrintingCheck,
                allowModifyCheck,
                allowCopyCheck,
                allowAnnotationsCheck
        );

        // Buttons
        ButtonBar buttonBar = new ButtonBar();
        buttonBar.setPadding(new Insets(10, 0, 0, 0));
        
        Button cancelButton = new Button("Hủy");
        cancelButton.setPrefWidth(80);
        cancelButton.setOnAction(e -> {
            result = null;
            dialogStage.close();
        });
        
        Button encryptButton = new Button("Mã hóa");
        encryptButton.setPrefWidth(80);
        encryptButton.setDisable(true);
        encryptButton.setOnAction(e -> {
            if (validatePasswords()) {
                AccessPermission permissions = new AccessPermission();
                permissions.setCanPrint(allowPrintingCheck.isSelected());
                permissions.setCanModify(allowModifyCheck.isSelected());
                permissions.setCanExtractContent(allowCopyCheck.isSelected());
                permissions.setCanModifyAnnotations(allowAnnotationsCheck.isSelected());

                String userPwd = userPasswordField.getText().trim();
                result = new EncryptionResult(
                        ownerPasswordField.getText(),
                        userPwd.isEmpty() ? ownerPasswordField.getText() : userPwd,
                        permissions
                );
                dialogStage.close();
            }
        });
        
        ButtonBar.setButtonData(cancelButton, ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonBar.setButtonData(encryptButton, ButtonBar.ButtonData.OK_DONE);
        buttonBar.getButtons().addAll(cancelButton, encryptButton);

        // Validation
        ownerPasswordField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(encryptButton));
        ownerPasswordConfirmField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(encryptButton));
        userPasswordField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(encryptButton));
        userPasswordConfirmField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(encryptButton));

        // Add all sections to the main vbox
        vbox.getChildren().addAll(
                headerLabel,
                ownerLabel, ownerDesc, ownerGrid,
                new Separator(),
                userLabel, userDesc, userGrid,
                new Separator(),
                permLabel, permBox,
                buttonBar
        );
        
        mainContainer.getChildren().add(vbox);

        Scene scene = new Scene(mainContainer);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialogStage.setScene(scene);
        dialogStage.setMinWidth(500);
        dialogStage.setMinHeight(550);
        
        // Apply theme if available
        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }

        // Request focus on the owner password field
        javafx.application.Platform.runLater(ownerPasswordField::requestFocus);

        dialogStage.showAndWait();
        return Optional.ofNullable(result);
    }

    private void validateForm(Button encryptButton) {
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
    
    public Stage getDialogStage() {
        return dialogStage;
    }

    /**
     * Result class containing encryption parameters.
     */
    public record EncryptionResult(String ownerPassword, String userPassword, AccessPermission permissions) {
    }
}
