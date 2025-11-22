package org.pdflite.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

public class InsertDialogController {

    @FXML private ComboBox<String> pageSizeCombo;
    @FXML private TextField widthField;
    @FXML private TextField heightField;
    @FXML private RadioButton radioPortrait;
    @FXML private RadioButton radioLandscape;
    @FXML private RadioButton radioAfter;
    @FXML private RadioButton radioBefore;
    @FXML private RadioButton radioEnd;
    @FXML private Spinner<Integer> countSpinner;
    @FXML private Button btnInsert;
    @FXML private Button btnCancel;

    private ToggleGroup orientationGroup;
    private ToggleGroup positionGroup;

    // Biến này để báo cho MainController biết người dùng có bấm Insert hay không
    private boolean isInsertClicked = false;

    @FXML
    public void initialize() {
        // 1. Setup Groups
        orientationGroup = new ToggleGroup();
        radioPortrait.setToggleGroup(orientationGroup);
        radioLandscape.setToggleGroup(orientationGroup);

        positionGroup = new ToggleGroup();
        radioAfter.setToggleGroup(positionGroup);
        radioBefore.setToggleGroup(positionGroup);
        radioEnd.setToggleGroup(positionGroup);

        // 2. Setup Combo Data
        pageSizeCombo.getItems().addAll("A4", "Letter", "Legal", "Custom");
        pageSizeCombo.setValue("A4");
        updateDimensions("A4");

        // 3. Listener ComboBox
        pageSizeCombo.setOnAction(e -> {
            String selected = pageSizeCombo.getValue();
            boolean isCustom = "Custom".equals(selected);
            widthField.setDisable(!isCustom);
            heightField.setDisable(!isCustom);
            if (!isCustom) updateDimensions(selected);
        });
    }

    // --- HÀM XỬ LÝ NÚT BẤM (MỚI THÊM) ---

    @FXML
    private void handleInsert() {
        isInsertClicked = true;
        closeDialog();
    }

    @FXML
    private void handleCancel() {
        isInsertClicked = false;
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) btnInsert.getScene().getWindow();
        stage.close();
    }

    // Getter để MainController kiểm tra
    public boolean isInsertClicked() {
        return isInsertClicked;
    }

    // ... (Các hàm helper updateDimensions, getWidth, getHeight, getInsertIndex giữ nguyên như cũ) ...
    private void updateDimensions(String size) {
        float w = 0, h = 0;
        switch (size) {
            case "A4" -> { w = PDRectangle.A4.getWidth(); h = PDRectangle.A4.getHeight(); }
            case "Letter" -> { w = PDRectangle.LETTER.getWidth(); h = PDRectangle.LETTER.getHeight(); }
            case "Legal" -> { w = PDRectangle.LEGAL.getWidth(); h = PDRectangle.LEGAL.getHeight(); }
        }
        widthField.setText(String.valueOf(w));
        heightField.setText(String.valueOf(h));
    }

    public float getWidth() {
        try {
            float w = Float.parseFloat(widthField.getText());
            float h = Float.parseFloat(heightField.getText());
            return radioPortrait.isSelected() ? w : h;
        } catch (Exception e) { return PDRectangle.A4.getWidth(); }
    }

    public float getHeight() {
        try {
            float w = Float.parseFloat(widthField.getText());
            float h = Float.parseFloat(heightField.getText());
            return radioPortrait.isSelected() ? h : w;
        } catch (Exception e) { return PDRectangle.A4.getHeight(); }
    }

    public int getInsertCount() { return countSpinner.getValue(); }

    public int getInsertIndex(int currentPage, int totalPages) {
        if (radioEnd.isSelected()) return totalPages;
        if (radioBefore.isSelected()) return currentPage;
        return currentPage + 1;
    }
}