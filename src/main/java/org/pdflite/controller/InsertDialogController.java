package org.pdflite.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

public class InsertDialogController {

    @FXML private ComboBox<String> pageSizeCombo;
    @FXML private TextField widthField, heightField;
    @FXML private RadioButton rbPortrait, rbLandscape;
    @FXML private RadioButton rbAfter, rbBefore, rbLast;
    @FXML private Spinner<Integer> countSpinner;
    @FXML private Button btnInsert;

    private boolean insertClicked = false;

    @FXML
    public void initialize() {
        ToggleGroup orientation = new ToggleGroup();
        rbPortrait.setToggleGroup(orientation);
        rbLandscape.setToggleGroup(orientation);

        ToggleGroup position = new ToggleGroup();
        rbAfter.setToggleGroup(position);
        rbBefore.setToggleGroup(position);
        rbLast.setToggleGroup(position);

        pageSizeCombo.getItems().addAll("A4", "Letter", "Legal", "Custom");
        pageSizeCombo.setValue("A4");
        updateSize("A4");

        pageSizeCombo.setOnAction(e -> {
            String val = pageSizeCombo.getValue();
            boolean isCustom = "Custom".equals(val);
            widthField.setDisable(!isCustom);
            heightField.setDisable(!isCustom);
            if (!isCustom) updateSize(val);
        });
    }

    private void updateSize(String size) {
        float w=0, h=0;
        switch (size) {
            case "A4" -> { w=595; h=842; }
            case "Letter" -> { w=612; h=792; }
            case "Legal" -> { w=612; h=1008; }
        }
        widthField.setText(String.valueOf(w));
        heightField.setText(String.valueOf(h));
    }

    @FXML private void handleInsert() {
        insertClicked = true;
        ((Stage) btnInsert.getScene().getWindow()).close();
    }

    @FXML private void handleCancel() {
        insertClicked = false;
        ((Stage) btnInsert.getScene().getWindow()).close();
    }

    public boolean isInsertClicked() { return insertClicked; }

    public float getWidth() {
        try {
            float w = Float.parseFloat(widthField.getText());
            float h = Float.parseFloat(heightField.getText());
            return rbPortrait.isSelected() ? w : h;
        } catch (Exception e) { return 595; }
    }

    public float getHeight() {
        try {
            float w = Float.parseFloat(widthField.getText());
            float h = Float.parseFloat(heightField.getText());
            return rbPortrait.isSelected() ? h : w;
        } catch (Exception e) { return 842; }
    }

    // --- QUAN TRỌNG: Tên hàm phải khớp với MainController ---
    public int getCount() { return countSpinner.getValue(); }

    public int getInsertIndex(int current, int total) {
        if (rbLast.isSelected()) return total;
        if (rbBefore.isSelected()) return current;
        return current + 1;
    }
}