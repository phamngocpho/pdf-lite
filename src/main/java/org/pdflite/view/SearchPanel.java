package org.pdflite.view;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import org.pdflite.controller.MainController;
import org.pdflite.manager.LanguageManager;
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

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

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
        caseSensitiveCheckbox = new CheckBox(lang().getString("search.caseSensitive"));
        wholeWordCheckbox = new CheckBox(lang().getString("search.wholeWord"));
        searchButton = new Button(lang().getString("toolbar.search"));
        cancelButton = new Button(lang().getString("dialog.cancel"));
        prevResultButton = new Button(lang().getString("search.prev"));
        nextResultButton = new Button(lang().getString("search.next"));
        progressIndicator = new ProgressIndicator();
        statusLabel = new Label(lang().getString("search.ready"));
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

        Label searchLabel = new Label(lang().getString("search.title") + ":");
        searchLabel.getStyleClass().add("search-title");
        searchLabel.setStyle("-fx-font-size: 13px;");

        searchField.setPromptText(lang().getString("search.keyword"));
        searchField.getStyleClass().add("search-input");
        searchField.setStyle("-fx-font-size: 12px;");

        // Set minimum width for buttons to prevent text truncation
        searchButton.setMinWidth(70);
        searchButton.setPrefWidth(70);
        cancelButton.setMinWidth(70);
        cancelButton.setPrefWidth(70);

        HBox searchBox = new HBox(8);
        searchBox.getChildren().addAll(searchField, searchButton, cancelButton, progressIndicator);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        HBox optionsBox = new HBox(20);
        caseSensitiveCheckbox.setText(lang().getString("search.caseSensitive"));
        caseSensitiveCheckbox.getStyleClass().add("search-checkbox");
        caseSensitiveCheckbox.setStyle("-fx-font-size: 12px;");
        wholeWordCheckbox.setText(lang().getString("search.wholeWord"));
        wholeWordCheckbox.getStyleClass().add("search-checkbox");
        wholeWordCheckbox.setStyle("-fx-font-size: 12px;");
        optionsBox.getChildren().addAll(caseSensitiveCheckbox, wholeWordCheckbox);

        searchControls.getChildren().addAll(searchLabel, searchBox, optionsBox);

        // Results section
        VBox resultsSection = new VBox(5);
        resultsSection.getStyleClass().add("search-results");

        HBox resultsHeader = new HBox(8);
        resultsHeader.setAlignment(Pos.CENTER_LEFT);

        Label resultsLabel = new Label(lang().getString("search.resultsLabel"));
        resultsLabel.getStyleClass().add("search-title");
        resultsLabel.setStyle("-fx-font-size: 13px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);


        prevResultButton.setText(lang().getString("search.prev"));
        prevResultButton.getStyleClass().add("search-nav-btn");
        prevResultButton.setStyle("-fx-font-size: 11px;");
        prevResultButton.setMinWidth(55);
        prevResultButton.setPrefWidth(55);
        nextResultButton.setText(lang().getString("search.next"));
        nextResultButton.getStyleClass().add("search-nav-btn");
        nextResultButton.setStyle("-fx-font-size: 11px;");
        nextResultButton.setMinWidth(55);
        nextResultButton.setPrefWidth(55);

        resultsHeader.getChildren().addAll(resultsLabel, spacer, prevResultButton, nextResultButton);

        resultsListView.setItems(searchResults);
        resultsListView.setCellFactory(lv -> new SearchResultCell());
        resultsListView.setStyle("-fx-font-size: 11px;");
        VBox.setVgrow(resultsListView, Priority.ALWAYS);

        resultsSection.getChildren().addAll(resultsHeader, resultsListView);

        // Status bar
        HBox statusBar = new HBox();
        statusBar.getStyleClass().add("search-status-bar");
        statusLabel.setStyle("-fx-font-size: 11px;");
        statusBar.getChildren().add(statusLabel);

        // Add all sections
        getChildren().addAll(searchControls, resultsSection, statusBar);
        
        // Make results section grow to fill available space
        VBox.setVgrow(resultsSection, Priority.ALWAYS);

        // Initial state
        progressIndicator.setMaxSize(20, 20);
        progressIndicator.setVisible(false);
        cancelButton.setDisable(false);
        cancelButton.setStyle("-fx-font-size: 12px;");
        prevResultButton.setDisable(true);
        nextResultButton.setDisable(true);

        // Style buttons
        searchButton.getStyleClass().add("search-button");
        searchButton.setStyle("-fx-font-size: 12px;");
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

        // Keyboard navigation: Left = previous result, Right = next result.
        // Keep normal caret movement when user is typing in the search field.
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (searchResults.isEmpty()) {
                return;
            }

            Object target = event.getTarget();
            if (target == searchField || target instanceof TextInputControl) {
                return;
            }

            if (event.getCode() == KeyCode.LEFT) {
                handlePreviousResult();
                event.consume();
            } else if (event.getCode() == KeyCode.RIGHT) {
                handleNextResult();
                event.consume();
            }
        });
    }


    /**
     * Set PDF document
     */
    public void setPDFDocument(PDFDocument document) {
        this.pdfDocument = document;
        searchResults.clear();
        updateStatus(lang().getString("search.ready"));
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
                cancelButton.setDisable(false);
                progressIndicator.setVisible(false);
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

    /**
     * Handle cancel
     */
    private void handleCancel() {
        searchHandler.cancelSearch();
        cancelButton.setDisable(false);
        if (mainController != null) {
            mainController.hideSearchPanel();
        }
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
            updateStatus(java.text.MessageFormat.format(lang().getString("search.resultOf"),
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
        alert.setTitle(lang().getString("search.errorTitle"));
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
    private class SearchResultCell extends ListCell<SearchResult> {
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
