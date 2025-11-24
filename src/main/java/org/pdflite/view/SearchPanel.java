package org.pdflite.view;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.pdflite.controller.MainController;
import org.pdflite.model.PDFDocument;
import org.pdflite.model.SearchResult;
import org.pdflite.util.SearchHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Reusable Search Panel component
 * Can be used in side panel or dialog
 */
public class SearchPanel extends VBox {

    private static final Logger logger = LoggerFactory.getLogger(SearchPanel.class);

    // UI Components
    private final TextField searchField;
    private final CheckBox caseSensitiveCheckbox;
    private final CheckBox wholeWordCheckbox;
    private final Button searchButton;
    private final Button cancelButton;
    private final Button prevResultButton;
    private final Button nextResultButton;
    private final ProgressIndicator progressIndicator;
    private final Label statusLabel;
    private final ListView<SearchResult> resultsListView;

    // Dependencies
    private PDFDocument pdfDocument;
    private final SearchHandler searchHandler;
    private MainController mainController;

    // State
    private final ObservableList<SearchResult> searchResults =
            FXCollections.observableArrayList();

    /**
     * Constructor
     */
    public SearchPanel() {
        searchHandler = new SearchHandler("SearchPanelExecutor");

        // Initialize UI components
        searchField = new TextField();
        caseSensitiveCheckbox = new CheckBox("Case Sensitive");
        wholeWordCheckbox = new CheckBox("Whole Word");
        searchButton = new Button("Search");
        cancelButton = new Button("Cancel");
        prevResultButton = new Button("◀ Prev");
        nextResultButton = new Button("Next ▶");
        progressIndicator = new ProgressIndicator();
        statusLabel = new Label("Ready");
        resultsListView = new ListView<>();

        // Setup UI
        setupUI();
        setupEventHandlers();
        searchHandler.createExecutorService();

        logger.debug("SearchPanel created");
    }

    /**
     * Setup UI layout
     */
    private void setupUI() {
        setSpacing(10);
        setPadding(new Insets(10));
        getStyleClass().add("search-panel-root");

        // Search controls
        VBox searchControls = new VBox(10);
        searchControls.getStyleClass().add("search-controls");

        Label searchLabel = new Label("Search:");
        searchLabel.getStyleClass().add("search-title");

        searchField.setPromptText("Enter keyword...");
        searchField.getStyleClass().add("search-input");

        HBox searchBox = new HBox(10);
        searchBox.getChildren().addAll(searchField, searchButton, cancelButton, progressIndicator);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        HBox optionsBox = new HBox(20);
        caseSensitiveCheckbox.getStyleClass().add("search-checkbox");
        wholeWordCheckbox.getStyleClass().add("search-checkbox");
        optionsBox.getChildren().addAll(caseSensitiveCheckbox, wholeWordCheckbox);

        searchControls.getChildren().addAll(searchLabel, searchBox, optionsBox);

        // Results section
        VBox resultsSection = new VBox(5);
        resultsSection.getStyleClass().add("search-results");

        HBox resultsHeader = new HBox(10);
        resultsHeader.setAlignment(Pos.CENTER_LEFT);

        Label resultsLabel = new Label("Results:");
        resultsLabel.getStyleClass().add("search-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);


        prevResultButton.getStyleClass().add("search-nav-btn");
        nextResultButton.getStyleClass().add("search-nav-btn");

        resultsHeader.getChildren().addAll(resultsLabel, spacer, prevResultButton, nextResultButton);

        resultsListView.setItems(searchResults);
        resultsListView.setCellFactory(lv -> new SearchResultCell());
        VBox.setVgrow(resultsListView, Priority.ALWAYS);

        resultsSection.getChildren().addAll(resultsHeader, resultsListView);

        // Status bar
        HBox statusBar = new HBox();
        statusBar.getStyleClass().add("search-status-bar");
        statusBar.getChildren().add(statusLabel);

        // Add all sections
        getChildren().addAll(searchControls, resultsSection, statusBar);

        // Initial state
        progressIndicator.setMaxSize(20, 20);
        progressIndicator.setVisible(false);
        cancelButton.setDisable(true);
        prevResultButton.setDisable(true);
        nextResultButton.setDisable(true);

        // Style buttons
        searchButton.getStyleClass().add("search-button");
    }

    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        searchField.setOnAction(e -> handleSearch());
        searchButton.setOnAction(e -> handleSearch());
        cancelButton.setOnAction(e -> handleCancel());
        prevResultButton.setOnAction(e -> handlePreviousResult());
        nextResultButton.setOnAction(e -> handleNextResult());

        resultsListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        handleResultSelected(newVal);
                    }
                });
    }


    /**
     * Set PDF document
     */
    public void setPDFDocument(PDFDocument document) {
        this.pdfDocument = document;
        searchResults.clear();
        updateStatus("Ready");
        prevResultButton.setDisable(true);
        nextResultButton.setDisable(true);
    }

    /**
     * Set main controller
     */
    public void setMainController(MainController controller) {
        this.mainController = controller;
    }

    /**
     * Handle search
     */
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
                SearchPanel.this.updateStatus(message);
            }

            @Override
            public void showError(String message) {
                SearchPanel.this.showError(message);
            }

            @Override
            public void onSearchStart() {
                searchButton.setDisable(true);
                cancelButton.setDisable(false);
                progressIndicator.setVisible(true);
                prevResultButton.setDisable(true);
                nextResultButton.setDisable(true);
            }

            @Override
            public void onSearchComplete() {
                searchButton.setDisable(false);
                cancelButton.setDisable(true);
                progressIndicator.setVisible(false);
            }

            @Override
            public void onSearchResultsReady(List<SearchResult> results) {
                if (!results.isEmpty()) {
                    resultsListView.getSelectionModel().selectFirst();
                    nextResultButton.setDisable(results.size() <= 1);
                }
            }
        };
    }

    /**
     * Handle cancel
     */
    private void handleCancel() {
        searchHandler.cancelSearch();
        cancelButton.setDisable(true);
    }

    /**
     * Handle previous result
     */
    private void handlePreviousResult() {
        int currentIndex = resultsListView.getSelectionModel().getSelectedIndex();
        if (currentIndex > 0) {
            resultsListView.getSelectionModel().select(currentIndex - 1);
            updateNavigationButtons();
        }
    }

    /**
     * Handle next result
     */
    private void handleNextResult() {
        int currentIndex = resultsListView.getSelectionModel().getSelectedIndex();
        if (currentIndex < searchResults.size() - 1) {
            resultsListView.getSelectionModel().select(currentIndex + 1);
            updateNavigationButtons();
        }
    }

    /**
     * Update navigation buttons
     */
    private void updateNavigationButtons() {
        int currentIndex = resultsListView.getSelectionModel().getSelectedIndex();
        prevResultButton.setDisable(currentIndex <= 0);
        nextResultButton.setDisable(currentIndex >= searchResults.size() - 1);
    }

    /**
     * Handle result selected
     */
    private void handleResultSelected(SearchResult result) {
        if (mainController != null && result != null) {
            mainController.highlightSearchResult(result);

            int position = resultsListView.getSelectionModel().getSelectedIndex() + 1;
            updateStatus(String.format("Result %d of %d on page %d",
                    position, searchResults.size(), result.pageNumber()));

            updateNavigationButtons();
        }
    }

    /**
     * Update status
     */
    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    /**
     * Show error
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Search Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Cleanup
     */
    public void cleanup() {
        searchHandler.cleanup();
        logger.info("SearchPanel cleanup completed");
    }

    /**
     * Search result cell
     */
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
