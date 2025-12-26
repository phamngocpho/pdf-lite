package org.pdflite.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.pdflite.manager.LanguageManager;
import org.pdflite.util.DialogTitleBar;

public class InsertDialogController {

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    public Button btnCancel;
    @FXML
    private HBox titleBar;
    @FXML
    private ComboBox<String> pageSizeCombo;
    @FXML
    private TextField widthField, heightField;
    @FXML
    private RadioButton rbPortrait, rbLandscape;
    @FXML
    private RadioButton rbAfter, rbBefore, rbLast;
    @FXML
    private Spinner<Integer> countSpinner;
    @FXML
    private Button btnInsert;

    private DialogTitleBar dialogTitleBar;
    private boolean insertClicked = false;
    private float defaultWidth = 595; // A4 default
    private float defaultHeight = 842; // A4 default
    private Stage dialogStage;

    /**
     * Sets the default page size based on current page dimensions.
     * This ensures inserted pages match the current page size.
     */
    public void setDefaultSize(float width, float height) {
        this.defaultWidth = width;
        this.defaultHeight = height;
        // Update size if "Match Current Page" is selected
        String matchKey = lang().getString("insert.matchCurrent");
        if (matchKey.equals(pageSizeCombo.getValue())) {
            updateSize(matchKey);
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

        pageSizeCombo.setOnAction(e -> {
            String val = pageSizeCombo.getValue();
            String customKey = lang().getString("insert.custom");
            boolean isCustom = customKey.equals(val);
            widthField.setDisable(!isCustom);
            heightField.setDisable(!isCustom);
            if (!isCustom) {
                updateSize(val);
            }
        });
    }

    private void updateSize(String size) {
        float w = 0, h = 0;
        String matchKey = lang().getString("insert.matchCurrent");
        
        if (matchKey.equals(size)) {
            w = defaultWidth;
            h = defaultHeight;
        } else if ("A4".equals(size)) {
            w = 595;
            h = 842;
        } else if ("Letter".equals(size)) {
            w = 612;
            h = 792;
        } else if ("Legal".equals(size)) {
            w = 612;
            h = 1008;
        }
        
        if (w > 0 && h > 0) {
            widthField.setText(String.valueOf(w));
            heightField.setText(String.valueOf(h));
        }
    }

    @FXML
    private void handleInsert() {
        insertClicked = true;
        ((Stage) btnInsert.getScene().getWindow()).close();
    }

    @FXML
    private void handleCancel() {
        insertClicked = false;
        ((Stage) btnInsert.getScene().getWindow()).close();
    }

    public boolean isInsertClicked() {
        return !insertClicked;
    }

    public float getWidth() {
        try {
            float w = Float.parseFloat(widthField.getText());
            float h = Float.parseFloat(heightField.getText());
            return rbPortrait.isSelected() ? w : h;
        } catch (Exception e) {
            return 595;
        }
    }

    public float getHeight() {
        try {
            float w = Float.parseFloat(widthField.getText());
            float h = Float.parseFloat(heightField.getText());
            return rbPortrait.isSelected() ? h : w;
        } catch (Exception e) {
            return 842;
        }
    }

    // --- QUAN TRỌNG: Tên hàm phải khớp với MainController ---
    public int getCount() {
        return countSpinner.getValue();
    }

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
        this.dialogStage = stage;
        dialogTitleBar = new DialogTitleBar(lang().getString("insert.title"), stage);
        titleBar.getChildren().setAll(dialogTitleBar.getTitleBar().getChildren());
        
        // Update all UI text
        updateAllUIText();
    }
    
    /**
     * Updates all UI text elements with current language.
     */
    private void updateAllUIText() {
        if (dialogStage == null || dialogStage.getScene() == null) {
            return;
        }
        
        // Update combo box items
        pageSizeCombo.getItems().clear();
        pageSizeCombo.getItems().addAll(
            lang().getString("insert.matchCurrent"),
            "A4",
            "Letter",
            "Legal",
            lang().getString("insert.custom")
        );
        pageSizeCombo.setValue(lang().getString("insert.matchCurrent"));
        
        // Update radio buttons
        rbPortrait.setText(lang().getString("insert.portrait"));
        rbLandscape.setText(lang().getString("insert.landscape"));
        rbAfter.setText(lang().getString("insert.afterCurrent"));
        rbBefore.setText(lang().getString("insert.beforeCurrent"));
        rbLast.setText(lang().getString("insert.atEnd"));
        
        // Update buttons
        btnInsert.setText(lang().getString("insert.insert"));
        btnCancel.setText(lang().getString("insert.cancel"));
        
        // Recursively update all Labels in the scene
        updateNodeText(dialogStage.getScene().getRoot());
        
        // Initialize with default size
        updateSize(lang().getString("insert.matchCurrent"));
    }
    
    /**
     * Recursively updates text for Labels.
     */
    private void updateNodeText(javafx.scene.Node node) {
        if (node instanceof Label label) {
            String text = label.getText();
            if (text != null && !text.isEmpty()) {
                switch (text) {
                    case "Page Size:" -> label.setText(lang().getString("insert.pageSize") + ":");
                    case "Dimensions (pt):" -> label.setText(lang().getString("insert.dimensions") + " (pt):");
                    case "Orientation:" -> label.setText(lang().getString("insert.orientation") + ":");
                    case "Position:" -> label.setText(lang().getString("insert.position") + ":");
                    case "Quantity:" -> label.setText(lang().getString("insert.quantity") + ":");
                    case "Configure page size and position." -> label.setText(lang().getString("insert.description"));
                }
            }
        }
        
        // Recursively process children
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                updateNodeText(child);
            }
        }
    }
}