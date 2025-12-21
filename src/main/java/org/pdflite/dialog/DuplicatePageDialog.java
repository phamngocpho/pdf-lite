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
 * Dialog for duplicating pages in a PDF document.
 */
public class DuplicatePageDialog {

    private final Stage dialog;
    private boolean confirmed = false;
    private int sourcePageIndex;
    private int insertPosition;
    private int numberOfCopies = 1;
    private final int totalPages;

    public DuplicatePageDialog(int currentPage, int totalPages, ThemeManager themeManager) {
        this.totalPages = totalPages;
        this.sourcePageIndex = currentPage;
        this.insertPosition = currentPage + 1; // Insert after current page by default

        dialog = new Stage();
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Duplicate Page");

        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("custom-confirm-dialog");

        // Title bar
        DialogTitleBar titleBar = new DialogTitleBar("Duplicate Page", dialog);
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

        dialog.setMinWidth(400);
        dialog.setMinHeight(300);

        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }
    }

    private VBox createContent() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_LEFT);

        // Info label
        Label infoLabel = new Label("Create duplicate copies of a page.");
        infoLabel.setWrapText(true);

        // Use GridPane for better alignment
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER_LEFT);

        // Source page selection
        Label sourceLabel = new Label("Page to duplicate:");
        sourceLabel.setStyle("-fx-font-weight: bold;");

        Spinner<Integer> sourceSpinner = new Spinner<>(1, totalPages, sourcePageIndex + 1);
        sourceSpinner.setEditable(true);
        sourceSpinner.setPrefWidth(150);
        sourceSpinner.setMinHeight(30);
        sourceSpinner.setMaxHeight(30);
        sourceSpinner.setStyle("-fx-focus-traversable: false;");
        sourceSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                sourcePageIndex = newVal - 1;
            }
        });

        grid.add(sourceLabel, 0, 0);
        grid.add(sourceSpinner, 1, 0);

        // Number of copies
        Label copiesLabel = new Label("Number of copies:");
        copiesLabel.setStyle("-fx-font-weight: bold;");

        Spinner<Integer> copiesSpinner = new Spinner<>(1, 100, 1);
        copiesSpinner.setEditable(true);
        copiesSpinner.setPrefWidth(150);
        copiesSpinner.setMinHeight(30);
        copiesSpinner.setMaxHeight(30);
        copiesSpinner.setStyle("-fx-focus-traversable: false;");
        copiesSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                numberOfCopies = newVal;
            }
        });

        grid.add(copiesLabel, 0, 1);
        grid.add(copiesSpinner, 1, 1);

        // Insert position
        Label positionLabel = new Label("Insert at position:");
        positionLabel.setStyle("-fx-font-weight: bold;");

        Spinner<Integer> positionSpinner = new Spinner<>(1, totalPages + 1, insertPosition + 1);
        positionSpinner.setEditable(true);
        positionSpinner.setPrefWidth(150);
        positionSpinner.setMinHeight(30);
        positionSpinner.setMaxHeight(30);
        positionSpinner.setStyle("-fx-focus-traversable: false;");
        positionSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                insertPosition = newVal - 1;
            }
        });

        grid.add(positionLabel, 0, 2);
        grid.add(positionSpinner, 1, 2);

        Label positionHint = new Label("(1 = beginning, " + (totalPages + 1) + " = end)");
        positionHint.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        content.getChildren().addAll(infoLabel, grid, positionHint);
        return content;
    }

    private HBox createButtonBox() {
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(0, 20, 20, 20));

        Button cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(100);
        cancelButton.setOnAction(e -> {
            confirmed = false;
            dialog.close();
        });

        Button duplicateButton = new Button("Duplicate");
        duplicateButton.setPrefWidth(100);
        duplicateButton.setDefaultButton(true);
        duplicateButton.setStyle("-fx-background-color: #2196f3; -fx-text-fill: white;");
        duplicateButton.setOnAction(e -> {
            confirmed = true;
            dialog.close();
        });

        buttonBox.getChildren().addAll(cancelButton, duplicateButton);
        return buttonBox;
    }

    public boolean showAndWait() {
        dialog.showAndWait();
        return confirmed;
    }

    public int getSourcePageIndex() {
        return sourcePageIndex;
    }

    public int getInsertPosition() {
        return insertPosition;
    }

    public int getNumberOfCopies() {
        return numberOfCopies;
    }
}
