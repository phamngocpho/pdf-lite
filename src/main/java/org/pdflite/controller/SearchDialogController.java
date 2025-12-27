package org.pdflite.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.pdflite.manager.LanguageManager;
import org.pdflite.model.PDFDocument;
import org.pdflite.model.SearchResult;
import org.pdflite.util.DialogTitleBar;
import org.pdflite.util.SearchHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Controller for the Search Dialog
 * Handles user interaction for searching text in PDF
 */
public class SearchDialogController {

    private static final Logger logger = LoggerFactory.getLogger(SearchDialogController.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    @FXML
    private HBox titleBar;
    @FXML
    private TextField searchField;
    @FXML
    private CheckBox caseSensitiveCheckbox;
    @FXML
    private CheckBox wholeWordCheckbox;
    @FXML
    private Button searchButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button closeButton;
    @FXML
    private Button prevResultButton;
    @FXML
    private Button nextResultButton;
    @FXML
    private ProgressIndicator progressIndicator;
    @FXML
    private Label statusLabel;
    @FXML
    private ListView<SearchResult> resultsListView;

    private DialogTitleBar dialogTitleBar;
    private PDFDocument pdfDocument;
    private SearchHandler searchHandler;
    private MainController mainController;

    private final ObservableList<SearchResult> searchResults =
            FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        searchHandler = new SearchHandler("SearchExecutor");

        searchHandler.createExecutorService();

        resultsListView.setItems(searchResults);
        resultsListView.setCellFactory(lv -> new SearchResultCell());

        resultsListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        handleResultSelected(newVal);
                    }
                });

        searchField.setOnAction(e -> handleSearch());

        progressIndicator.setVisible(false);
        progressIndicator.setManaged(false);
        cancelButton.setDisable(true);

        logger.debug("SearchDialogController initialized");
    }

    public void setPDFDocument(PDFDocument pdfDocument) {
        this.pdfDocument = pdfDocument;

        searchResults.clear();
        updateStatus(lang().getString("search.ready"));
        prevResultButton.setDisable(true);
        nextResultButton.setDisable(true);
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
    private void handleSearch() {
        searchHandler.executeSearch(pdfDocument, createCallbacks());
    }

    /**
     * Creates the UI callbacks for the search handler.
     */
    private SearchHandler.SearchUICallbacks createCallbacks() {
        return new SearchHandler.SearchUICallbacks() {
            @Override
            public String getSearchKeyword() {
                return searchField.getText().trim();
            }

            @Override
            public boolean isCaseSensitive() {
                return caseSensitiveCheckbox.isSelected();
            }

            @Override
            public boolean isWholeWord() {
                return wholeWordCheckbox.isSelected();
            }

            @Override
            public ObservableList<SearchResult> getSearchResults() {
                return searchResults;
            }

            @Override
            public MainController getMainController() {
                return mainController;
            }

            @Override
            public void updateStatus(String message) {
                SearchDialogController.this.updateStatus(message);
            }

            @Override
            public void showError(String message) {
                SearchDialogController.this.showError(message);
            }

            @Override
            public void onSearchStart() {
                searchButton.setDisable(true);
                cancelButton.setDisable(false);
                progressIndicator.setVisible(true);
                progressIndicator.setManaged(true);
                prevResultButton.setDisable(true);
                nextResultButton.setDisable(true);
            }

            @Override
            public void onSearchComplete() {
                searchButton.setDisable(false);
                cancelButton.setDisable(true);
                progressIndicator.setVisible(false);
                progressIndicator.setManaged(false);
            }

            @Override
            public void onSearchResultsReady(List<SearchResult> results) {
                if (!results.isEmpty()) {
                    resultsListView.getSelectionModel().selectFirst();
                    nextResultButton.setDisable(results.size() <= 1);
                }

                // Trigger highlighting on pages via MainController -> SearchManager
                if (getMainController() != null) {
                    getMainController().highlightSearchResults(results);
                }
            }
        };
    }

    @FXML
    private void handleCancel() {
        searchHandler.cancelSearch();
        cancelButton.setDisable(true);
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void handlePreviousResult() {
        int currentIndex = resultsListView.getSelectionModel().getSelectedIndex();
        if (currentIndex > 0) {
            resultsListView.getSelectionModel().select(currentIndex - 1);
            updateNavigationButtons();
        }
    }

    @FXML
    private void handleNextResult() {
        int currentIndex = resultsListView.getSelectionModel().getSelectedIndex();
        if (currentIndex < searchResults.size() - 1) {
            resultsListView.getSelectionModel().select(currentIndex + 1);
            updateNavigationButtons();
        }
    }

    private void updateNavigationButtons() {
        int currentIndex = resultsListView.getSelectionModel().getSelectedIndex();
        prevResultButton.setDisable(currentIndex <= 0);
        nextResultButton.setDisable(currentIndex >= searchResults.size() - 1);
    }

    private void handleResultSelected(SearchResult result) {
        if (mainController != null && result != null) {
            mainController.highlightSearchResult(result);

            int position = resultsListView.getSelectionModel().getSelectedIndex() + 1;
            updateStatus(java.text.MessageFormat.format(lang().getString("search.resultOf"),
                    position, searchResults.size(), result.pageNumber()));

            updateNavigationButtons();
        }
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(lang().getString("search.errorTitle"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void cleanup() {
        searchHandler.cleanup();
        logger.info("SearchDialogController cleanup completed");
    }

    /**
     * Sets the dialog stage and initializes the custom title bar.
     *
     * @param stage the dialog stage
     */
    public void setDialogStage(Stage stage) {
        dialogTitleBar = new DialogTitleBar(lang().getString("search.title"), stage);
        titleBar.getChildren().setAll(dialogTitleBar.getTitleBar().getChildren());
        
        // Update all UI text
        updateAllUIText();
    }
    
    private void updateAllUIText() {
        if (searchField != null) searchField.setPromptText(lang().getString("search.keyword"));
        if (caseSensitiveCheckbox != null) caseSensitiveCheckbox.setText(lang().getString("search.caseSensitive"));
        if (wholeWordCheckbox != null) wholeWordCheckbox.setText(lang().getString("search.wholeWord"));
        if (searchButton != null) searchButton.setText(lang().getString("menu.edit.find"));
        if (cancelButton != null) cancelButton.setText(lang().getString("dialog.cancel"));
        if (closeButton != null) closeButton.setText(lang().getString("search.close"));
        if (prevResultButton != null) prevResultButton.setText(lang().getString("search.prev"));
        if (nextResultButton != null) nextResultButton.setText(lang().getString("search.next"));
        if (statusLabel != null) statusLabel.setText(lang().getString("search.ready"));
        
        // Update labels in scene
        if (titleBar != null && titleBar.getScene() != null) {
            javafx.scene.Parent root = titleBar.getScene().getRoot();
            updateLabelsInParent(root);
        }
    }
    
    private void updateLabelsInParent(javafx.scene.Parent parent) {
        for (javafx.scene.Node node : parent.getChildrenUnmodifiable()) {
            if (node instanceof Label label) {
                String text = label.getText();
                if (text != null) {
                    switch (text) {
                        case "Search:" -> label.setText(lang().getString("search.label"));
                        case "Results:" -> label.setText(lang().getString("search.resultsLabel"));
                    }
                }
            } else if (node instanceof javafx.scene.Parent p) {
                updateLabelsInParent(p);
            }
        }
    }

    private static class SearchResultCell extends ListCell<SearchResult> {
        @Override
        protected void updateItem(SearchResult item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.getDisplayText());
                setStyle("-fx-padding: 5;");
            }
        }
    }
}