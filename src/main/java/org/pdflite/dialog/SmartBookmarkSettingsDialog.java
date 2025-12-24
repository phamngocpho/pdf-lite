package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pdflite.manager.ThemeManager;
import org.pdflite.util.DialogTitleBar;

/**
 * Dialog for configuring Smart Bookmark detection settings.
 */
public class SmartBookmarkSettingsDialog {
    
    private final Stage dialog;
    private boolean confirmed = false;
    
    // Settings
    private Spinner<Double> headingFontSizeSpinner;
    private Spinner<Double> titleFontSizeSpinner;
    private CheckBox detectChapterPatternsCheck;
    private CheckBox detectBoldHeadingsCheck;
    private CheckBox detectAllCapsCheck;
    private Spinner<Integer> minTextLengthSpinner;
    private Spinner<Integer> maxTextLengthSpinner;
    
    public SmartBookmarkSettingsDialog(ThemeManager themeManager) {
        dialog = new Stage();
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Smart Bookmark Settings");
        
        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("custom-confirm-dialog");
        
        // Title bar
        DialogTitleBar titleBar = new DialogTitleBar("Smart Bookmark Settings", dialog);
        mainContainer.getChildren().add(titleBar.getTitleBar());
        
        // Content
        VBox content = createContent();
        mainContainer.getChildren().add(content);
        
        // Buttons
        HBox buttonBox = createButtonBox();
        mainContainer.getChildren().add(buttonBox);
        
        Scene scene = new Scene(mainContainer);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);
        
        dialog.setMinWidth(500);
        dialog.setMinHeight(450);
        
        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }
    }
    
    private VBox createContent() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_LEFT);
        
        Label infoLabel = new Label("Configure how Smart Bookmarks detects headings and chapters:");
        infoLabel.setWrapText(true);
        infoLabel.setMaxWidth(450);
        infoLabel.setStyle("-fx-font-size: 12px;");
        
        // Font size settings
        Label fontSizeLabel = new Label("Font Size Thresholds:");
        fontSizeLabel.setStyle("-fx-font-weight: bold;");
        
        GridPane fontSizeGrid = new GridPane();
        fontSizeGrid.setHgap(10);
        fontSizeGrid.setVgap(10);
        fontSizeGrid.setPadding(new Insets(5, 0, 0, 20));
        
        headingFontSizeSpinner = new Spinner<>(10.0, 30.0, 14.0, 0.5);
        headingFontSizeSpinner.setEditable(true);
        headingFontSizeSpinner.setPrefWidth(100);
        
        titleFontSizeSpinner = new Spinner<>(12.0, 40.0, 16.0, 0.5);
        titleFontSizeSpinner.setEditable(true);
        titleFontSizeSpinner.setPrefWidth(100);
        
        fontSizeGrid.add(new Label("Heading font size (pt):"), 0, 0);
        fontSizeGrid.add(headingFontSizeSpinner, 1, 0);
        fontSizeGrid.add(new Label("Title font size (pt):"), 0, 1);
        fontSizeGrid.add(titleFontSizeSpinner, 1, 1);
        
        // Detection options
        Label detectionLabel = new Label("Detection Options:");
        detectionLabel.setStyle("-fx-font-weight: bold;");
        
        VBox detectionBox = new VBox(8);
        detectionBox.setPadding(new Insets(5, 0, 0, 20));
        
        detectChapterPatternsCheck = new CheckBox("Detect chapter patterns (Chapter 1, Chương 1, etc.)");
        detectChapterPatternsCheck.setSelected(true);
        
        detectBoldHeadingsCheck = new CheckBox("Detect bold headings");
        detectBoldHeadingsCheck.setSelected(true);
        
        detectAllCapsCheck = new CheckBox("Detect ALL CAPS titles");
        detectAllCapsCheck.setSelected(true);
        
        detectionBox.getChildren().addAll(
            detectChapterPatternsCheck,
            detectBoldHeadingsCheck,
            detectAllCapsCheck
        );
        
        // Text length settings
        Label lengthLabel = new Label("Text Length Limits:");
        lengthLabel.setStyle("-fx-font-weight: bold;");
        
        GridPane lengthGrid = new GridPane();
        lengthGrid.setHgap(10);
        lengthGrid.setVgap(10);
        lengthGrid.setPadding(new Insets(5, 0, 0, 20));
        
        minTextLengthSpinner = new Spinner<>(1, 50, 3, 1);
        minTextLengthSpinner.setEditable(true);
        minTextLengthSpinner.setPrefWidth(100);
        
        maxTextLengthSpinner = new Spinner<>(50, 500, 200, 10);
        maxTextLengthSpinner.setEditable(true);
        maxTextLengthSpinner.setPrefWidth(100);
        
        lengthGrid.add(new Label("Minimum characters:"), 0, 0);
        lengthGrid.add(minTextLengthSpinner, 1, 0);
        lengthGrid.add(new Label("Maximum characters:"), 0, 1);
        lengthGrid.add(maxTextLengthSpinner, 1, 1);
        
        content.getChildren().addAll(
            infoLabel,
            new Separator(),
            fontSizeLabel, fontSizeGrid,
            new Separator(),
            detectionLabel, detectionBox,
            new Separator(),
            lengthLabel, lengthGrid
        );
        
        return content;
    }
    
    private HBox createButtonBox() {
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(0, 20, 20, 20));
        
        Button resetButton = new Button("Reset to Defaults");
        resetButton.setPrefWidth(130);
        resetButton.setOnAction(e -> resetToDefaults());
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(100);
        cancelButton.setOnAction(e -> {
            confirmed = false;
            dialog.close();
        });
        
        Button okButton = new Button("OK");
        okButton.setPrefWidth(100);
        okButton.setDefaultButton(true);
        okButton.setStyle("-fx-background-color: #2196f3; -fx-text-fill: white;");
        okButton.setOnAction(e -> {
            confirmed = true;
            dialog.close();
        });
        
        buttonBox.getChildren().addAll(resetButton, spacer, cancelButton, okButton);
        return buttonBox;
    }
    
    private void resetToDefaults() {
        headingFontSizeSpinner.getValueFactory().setValue(14.0);
        titleFontSizeSpinner.getValueFactory().setValue(16.0);
        detectChapterPatternsCheck.setSelected(true);
        detectBoldHeadingsCheck.setSelected(true);
        detectAllCapsCheck.setSelected(true);
        minTextLengthSpinner.getValueFactory().setValue(3);
        maxTextLengthSpinner.getValueFactory().setValue(200);
    }
    
    public boolean showAndWait() {
        dialog.showAndWait();
        return confirmed;
    }
    
    // Getters for settings
    public double getHeadingFontSize() {
        return headingFontSizeSpinner.getValue();
    }
    
    public double getTitleFontSize() {
        return titleFontSizeSpinner.getValue();
    }
    
    public boolean isDetectChapterPatterns() {
        return detectChapterPatternsCheck.isSelected();
    }
    
    public boolean isDetectBoldHeadings() {
        return detectBoldHeadingsCheck.isSelected();
    }
    
    public boolean isDetectAllCaps() {
        return detectAllCapsCheck.isSelected();
    }
    
    public int getMinTextLength() {
        return minTextLengthSpinner.getValue();
    }
    
    public int getMaxTextLength() {
        return maxTextLengthSpinner.getValue();
    }
}
