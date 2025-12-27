package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pdflite.manager.LanguageManager;
import org.pdflite.manager.ThemeManager;
import org.pdflite.util.DialogTitleBar;

import java.io.File;
import java.util.Optional;

/**
 * Dialog for digitally signing PDF files with certificate.
 */
public class DigitalSignatureDialog {

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private final TextField keystorePathField;
    private final PasswordField keystorePasswordField;
    private final TextField aliasField;
    private final PasswordField keyPasswordField;
    private final TextField reasonField;
    private final TextField locationField;
    private final TextField contactField;
    private final CheckBox visibleSignatureCheck;
    private final Spinner<Integer> pageSpinner;
    private final Spinner<Integer> xSpinner;
    private final Spinner<Integer> ySpinner;
    private final Spinner<Integer> widthSpinner;
    private final Spinner<Integer> heightSpinner;

    private Stage dialogStage;
    private SignatureResult result;
    private int totalPages = 1;

    public DigitalSignatureDialog() {
        keystorePathField = new TextField();
        keystorePasswordField = new PasswordField();
        aliasField = new TextField();
        keyPasswordField = new PasswordField();
        reasonField = new TextField();
        locationField = new TextField();
        contactField = new TextField();
        visibleSignatureCheck = new CheckBox(lang().getString("signature.visibleSignature"));
        pageSpinner = new Spinner<>(1, 9999, 1);
        xSpinner = new Spinner<>(0, 9999, 50);
        ySpinner = new Spinner<>(0, 9999, 50);
        widthSpinner = new Spinner<>(50, 500, 200);
        heightSpinner = new Spinner<>(20, 200, 50);
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
        pageSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, totalPages, 1));
    }

    public Optional<SignatureResult> showAndWait(ThemeManager themeManager) {
        dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.TRANSPARENT);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle(lang().getString("signature.title"));

        // Create main container
        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("signature-dialog");

        // Create custom title bar
        DialogTitleBar titleBar = new DialogTitleBar(lang().getString("signature.title"), dialogStage);
        mainContainer.getChildren().add(titleBar.getTitleBar());

        // Create content with scroll
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));

        Label headerLabel = new Label(lang().getString("signature.header"));
        headerLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");

        // Keystore section
        Label keystoreLabel = new Label(lang().getString("signature.keystoreLabel"));
        keystoreLabel.setStyle("-fx-font-weight: bold;");
        Label keystoreDesc = new Label(lang().getString("signature.keystoreDesc"));
        keystoreDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");

        GridPane keystoreGrid = new GridPane();
        keystoreGrid.setHgap(10);
        keystoreGrid.setVgap(10);
        keystoreGrid.setPadding(new Insets(5, 0, 0, 20));

        keystorePathField.setPromptText(lang().getString("signature.keystorePlaceholder"));
        keystorePathField.setPrefWidth(200);
        keystorePathField.setEditable(false);

        Button browseButton = new Button(lang().getString("signature.browse"));
        browseButton.setOnAction(e -> browseKeystore());

        HBox keystoreBox = new HBox(5, keystorePathField, browseButton);

        keystorePasswordField.setPromptText(lang().getString("signature.keystorePasswordPlaceholder"));
        keystorePasswordField.setPrefWidth(250);

        aliasField.setPromptText(lang().getString("signature.aliasPlaceholder"));
        aliasField.setPrefWidth(250);

        keyPasswordField.setPromptText(lang().getString("signature.keyPasswordPlaceholder"));
        keyPasswordField.setPrefWidth(250);

        keystoreGrid.add(new Label(lang().getString("signature.keystoreFile")), 0, 0);
        keystoreGrid.add(keystoreBox, 1, 0);
        keystoreGrid.add(new Label(lang().getString("signature.keystorePassword")), 0, 1);
        keystoreGrid.add(keystorePasswordField, 1, 1);
        keystoreGrid.add(new Label(lang().getString("signature.alias")), 0, 2);
        keystoreGrid.add(aliasField, 1, 2);
        keystoreGrid.add(new Label(lang().getString("signature.keyPassword")), 0, 3);
        keystoreGrid.add(keyPasswordField, 1, 3);

        // Signature info section
        Label infoLabel = new Label(lang().getString("signature.infoLabel"));
        infoLabel.setStyle("-fx-font-weight: bold;");
        Label infoDesc = new Label(lang().getString("signature.infoDesc"));
        infoDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(10);
        infoGrid.setVgap(10);
        infoGrid.setPadding(new Insets(5, 0, 0, 20));

        reasonField.setPromptText(lang().getString("signature.reasonPlaceholder"));
        reasonField.setPrefWidth(250);

        locationField.setPromptText(lang().getString("signature.locationPlaceholder"));
        locationField.setPrefWidth(250);

        contactField.setPromptText(lang().getString("signature.contactPlaceholder"));
        contactField.setPrefWidth(250);

        infoGrid.add(new Label(lang().getString("signature.reason")), 0, 0);
        infoGrid.add(reasonField, 1, 0);
        infoGrid.add(new Label(lang().getString("signature.location")), 0, 1);
        infoGrid.add(locationField, 1, 1);
        infoGrid.add(new Label(lang().getString("signature.contact")), 0, 2);
        infoGrid.add(contactField, 1, 2);

        // Visible signature section
        Label visibleLabel = new Label(lang().getString("signature.appearanceLabel"));
        visibleLabel.setStyle("-fx-font-weight: bold;");

        VBox visibleBox = new VBox(10);
        visibleBox.setPadding(new Insets(5, 0, 0, 20));

        visibleSignatureCheck.setSelected(false);

        GridPane positionGrid = new GridPane();
        positionGrid.setHgap(10);
        positionGrid.setVgap(10);
        positionGrid.setPadding(new Insets(5, 0, 0, 0));
        positionGrid.setDisable(true);

        pageSpinner.setEditable(true);
        pageSpinner.setPrefWidth(80);
        xSpinner.setEditable(true);
        xSpinner.setPrefWidth(80);
        ySpinner.setEditable(true);
        ySpinner.setPrefWidth(80);
        widthSpinner.setEditable(true);
        widthSpinner.setPrefWidth(80);
        heightSpinner.setEditable(true);
        heightSpinner.setPrefWidth(80);

        positionGrid.add(new Label(lang().getString("signature.page")), 0, 0);
        positionGrid.add(pageSpinner, 1, 0);
        positionGrid.add(new Label(lang().getString("signature.x")), 2, 0);
        positionGrid.add(xSpinner, 3, 0);
        positionGrid.add(new Label(lang().getString("signature.y")), 4, 0);
        positionGrid.add(ySpinner, 5, 0);
        positionGrid.add(new Label(lang().getString("signature.width")), 0, 1);
        positionGrid.add(widthSpinner, 1, 1);
        positionGrid.add(new Label(lang().getString("signature.height")), 2, 1);
        positionGrid.add(heightSpinner, 3, 1);

        visibleSignatureCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            positionGrid.setDisable(!newVal);
        });

        visibleBox.getChildren().addAll(visibleSignatureCheck, positionGrid);

        // Buttons
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));

        Button cancelButton = new Button(lang().getString("signature.cancel"));
        cancelButton.setPrefWidth(80);
        cancelButton.setOnAction(e -> {
            result = null;
            dialogStage.close();
        });

        Button signButton = new Button(lang().getString("signature.sign"));
        signButton.setPrefWidth(80);
        signButton.setDisable(true);
        signButton.setOnAction(e -> {
            if (validateInput()) {
                result = new SignatureResult(
                        keystorePathField.getText(),
                        keystorePasswordField.getText(),
                        aliasField.getText().trim(),
                        keyPasswordField.getText(),
                        reasonField.getText().trim(),
                        locationField.getText().trim(),
                        contactField.getText().trim(),
                        visibleSignatureCheck.isSelected(),
                        pageSpinner.getValue(),
                        xSpinner.getValue(),
                        ySpinner.getValue(),
                        widthSpinner.getValue(),
                        heightSpinner.getValue()
                );
                dialogStage.close();
            }
        });

        buttonBar.getChildren().addAll(cancelButton, signButton);

        // Validation listeners
        keystorePathField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(signButton));
        keystorePasswordField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(signButton));
        aliasField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(signButton));

        // Add all sections
        vbox.getChildren().addAll(
                headerLabel,
                keystoreLabel, keystoreDesc, keystoreGrid,
                new Separator(),
                infoLabel, infoDesc, infoGrid,
                new Separator(),
                visibleLabel, visibleBox,
                buttonBar
        );

        scrollPane.setContent(vbox);
        mainContainer.getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Scene scene = new Scene(mainContainer);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialogStage.setScene(scene);
        dialogStage.setMinWidth(520);
        dialogStage.setMinHeight(580);
        dialogStage.setWidth(520);
        dialogStage.setHeight(580);

        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }

        javafx.application.Platform.runLater(keystorePathField::requestFocus);

        dialogStage.showAndWait();
        return Optional.ofNullable(result);
    }

    private void browseKeystore() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(lang().getString("signature.selectKeystore"));
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Keystore Files", "*.p12", "*.pfx", "*.jks"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = fileChooser.showOpenDialog(dialogStage);
        if (file != null) {
            keystorePathField.setText(file.getAbsolutePath());
        }
    }

    private void validateForm(Button signButton) {
        boolean valid = !keystorePathField.getText().isEmpty()
                && !keystorePasswordField.getText().isEmpty()
                && !aliasField.getText().trim().isEmpty();
        signButton.setDisable(!valid);
    }

    private boolean validateInput() {
        if (keystorePathField.getText().isEmpty()) {
            showError(lang().getString("signature.error.noKeystore"));
            return false;
        }

        File keystoreFile = new File(keystorePathField.getText());
        if (!keystoreFile.exists()) {
            showError(lang().getString("signature.error.keystoreNotFound"));
            return false;
        }

        if (keystorePasswordField.getText().isEmpty()) {
            showError(lang().getString("signature.error.noKeystorePassword"));
            return false;
        }

        if (aliasField.getText().trim().isEmpty()) {
            showError(lang().getString("signature.error.noAlias"));
            return false;
        }

        return true;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(lang().getString("error.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public Stage getDialogStage() {
        return dialogStage;
    }

    /**
     * Result class containing digital signature parameters.
     */
    public record SignatureResult(
            String keystorePath,
            String keystorePassword,
            String alias,
            String keyPassword,
            String reason,
            String location,
            String contact,
            boolean visibleSignature,
            int page,
            int x,
            int y,
            int width,
            int height
    ) {
    }
}
