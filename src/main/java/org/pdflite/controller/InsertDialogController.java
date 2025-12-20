package org.pdflite.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.pdflite.util.DialogTitleBar;

public class InsertDialogController {

    public Button btnCancel;
    @FXML private HBox titleBar;
    @FXML private ComboBox<String> pageSizeCombo;
    @FXML private TextField widthField, heightField;
    @FXML private RadioButton rbPortrait, rbLandscape;
    @FXML private RadioButton rbAfter, rbBefore, rbLast;
    @FXML private Spinner<Integer> countSpinner;
    @FXML private Button btnInsert;

    private DialogTitleBar dialogTitleBar;
    private boolean insertClicked = false;
    private float defaultWidth = 595; // A4 default
    private float defaultHeight = 842; // A4 default

    /**
     * Sets the default page size based on current page dimensions.
     * This ensures inserted pages match the current page size.
     */
    public void setDefaultSize(float width, float height) {
        this.defaultWidth = width;
        this.defaultHeight = height;
        // Update size if "Match Current Page" is selected
        if ("Match Current Page".equals(pageSizeCombo.getValue())) {
            updateSize("Match Current Page");
        }
    }

    @FXML
    public void initialize() {
        ToggleGroup orientation = new ToggleGroup();
        rbPortrait.setToggleGroup(orientation);
        rbLandscape.setToggleGroup(orientation);

        ToggleGroup position = new ToggleGroup();
        rbAfter.setToggleGroup(position);
        rbBefore.setToggleGroup(position);
        rbLast.setToggleGroup(position);

        pageSizeCombo.getItems().addAll("Match Current Page", "A4", "Letter", "Legal", "Custom");
        pageSizeCombo.setValue("Match Current Page");

        pageSizeCombo.setOnAction(e -> {
            String val = pageSizeCombo.getValue();
            boolean isCustom = "Custom".equals(val);
            widthField.setDisable(!isCustom);
            heightField.setDisable(!isCustom);
            if (!isCustom) {
                updateSize(val);
            }
        });
        
        // Initialize with default size (will be updated by setDefaultSize)
        updateSize("Match Current Page");
    }

    private void updateSize(String size) {
        float w = 0, h = 0;
        switch (size) {
            case "Match Current Page" -> {
                w = defaultWidth;
                h = defaultHeight;
            }
            case "A4" -> { w = 595; h = 842; }
            case "Letter" -> { w = 612; h = 792; }
            case "Legal" -> { w = 612; h = 1008; }
        }
        if (w > 0 && h > 0) {
            widthField.setText(String.valueOf(w));
            heightField.setText(String.valueOf(h));
        }
    }

    @FXML private void handleInsert() {
        insertClicked = true;
        ((Stage) btnInsert.getScene().getWindow()).close();
    }

    @FXML private void handleCancel() {
        insertClicked = false;
        ((Stage) btnInsert.getScene().getWindow()).close();
    }

    public boolean isInsertClicked() { return !insertClicked; }

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
    
    /**
     * Gets the selected page size option.
     * Used to determine if we should use the reference page size.
     */
    public String getSelectedPageSize() {
        return pageSizeCombo.getValue();
    }

    /**
     * Sets the dialog stage and initializes the custom title bar.
     *
     * @param stage the dialog stage
     */
    public void setDialogStage(Stage stage) {
        dialogTitleBar = new DialogTitleBar("Insert Blank Page", stage);
        titleBar.getChildren().setAll(dialogTitleBar.getTitleBar().getChildren());
    }
}