package org.pdflite.view;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.pdflite.controller.MainController;
import org.pdflite.model.PDFDocument;
import org.pdflite.model.SearchResult;
import org.pdflite.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final SearchService searchService;
    private MainController mainController;

    // State
    private ExecutorService searchExecutor;
    private final ObservableList<SearchResult> searchResults =
            FXCollections.observableArrayList();

    /**
     * Constructor
     */
    public SearchPanel() {
        searchService = new SearchService();

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
        createExecutorService();

        logger.debug("SearchPanel created");
    }

    /**
     * Setup UI layout
     */
    private void setupUI() {
        setSpacing(10);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #f5f5f5;");

        // Search controls
        VBox searchControls = new VBox(10);
        searchControls.setStyle("-fx-background-color: white; -fx-padding: 10; -fx-border-color: #cccccc;");

        Label searchLabel = new Label("Search:");
        searchLabel.setStyle("-fx-font-weight: bold;");

        searchField.setPromptText("Enter keyword...");

        HBox searchBox = new HBox(10);
        searchBox.getChildren().addAll(searchField, searchButton, cancelButton, progressIndicator);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        HBox optionsBox = new HBox(20);
        optionsBox.getChildren().addAll(caseSensitiveCheckbox, wholeWordCheckbox);

        searchControls.getChildren().addAll(searchLabel, searchBox, optionsBox);

        // Results section
        VBox resultsSection = new VBox(5);
        VBox.setVgrow(resultsSection, Priority.ALWAYS);

        HBox resultsHeader = new HBox(10);
        resultsHeader.setAlignment(Pos.CENTER_LEFT);
        Label resultsLabel = new Label("Results:");
        resultsLabel.setStyle("-fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        resultsHeader.getChildren().addAll(resultsLabel, spacer, prevResultButton, nextResultButton);

        resultsListView.setItems(searchResults);
        resultsListView.setCellFactory(lv -> new SearchResultCell());
        VBox.setVgrow(resultsListView, Priority.ALWAYS);

        resultsSection.getChildren().addAll(resultsHeader, resultsListView);

        // Status bar
        HBox statusBar = new HBox();
        statusBar.setStyle("-fx-background-color: #e0e0e0; -fx-padding: 5;");
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
        searchButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
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
     * Create executor service
     */
    private void createExecutorService() {
        if (searchExecutor != null && !searchExecutor.isShutdown()) {
            searchExecutor.shutdown();
        }

        searchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("SearchPanelExecutor");
            return t;
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
        String keyword = searchField.getText().trim();

        if (keyword.isEmpty()) {
            showError("Please enter a search keyword");
            return;
        }

        if (pdfDocument == null) {
            showError("No PDF document loaded");
            return;
        }

        if (searchExecutor == null || searchExecutor.isShutdown()) {
            createExecutorService();
        }

        searchResults.clear();
        searchButton.setDisable(true);
        cancelButton.setDisable(false);
        progressIndicator.setVisible(true);
        prevResultButton.setDisable(true);
        nextResultButton.setDisable(true);
        updateStatus("Searching...");

        boolean caseSensitive = caseSensitiveCheckbox.isSelected();
        boolean wholeWord = wholeWordCheckbox.isSelected();

        searchExecutor.submit(() -> performSearch(keyword, caseSensitive, wholeWord));
    }

    /**
     * Perform search
     */
    private void performSearch(String keyword, boolean caseSensitive, boolean wholeWord) {
        try {
            logger.info("Starting search for: {}", keyword);

            List<SearchResult> results = searchService.searchInDocument(
                    pdfDocument.getDocument(),
                    keyword,
                    caseSensitive,
                    wholeWord
            );

            Platform.runLater(() -> {
                if (searchService.isCancelled()) {
                    updateStatus("Search cancelled");
                } else {
                    searchResults.addAll(results);
                    updateStatus(String.format("Found %d result(s)", results.size()));

                    // ✅ FIX: Apply highlights to main viewer
                    if (mainController != null && !results.isEmpty()) {
                        mainController.highlightSearchResults(results);
                        logger.info("Applied {} highlights from search panel", results.size());
                    }

                    if (!results.isEmpty()) {
                        resultsListView.getSelectionModel().selectFirst();
                        nextResultButton.setDisable(results.size() <= 1);
                    }
                }
            });

        } catch (IOException e) {
            logger.error("Error during search", e);
            Platform.runLater(() -> {
                showError("Error during search: " + e.getMessage());
                updateStatus("Search failed");
            });
        } finally {
            Platform.runLater(() -> {
                searchButton.setDisable(false);
                cancelButton.setDisable(true);
                progressIndicator.setVisible(false);
            });
        }
    }

    /**
     * Handle cancel
     */
    private void handleCancel() {
        searchService.cancelSearch();
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
            // ✅ FIX: Use highlightSearchResult to set active result
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
        searchService.cancelSearch();
        if (searchExecutor != null && !searchExecutor.isShutdown()) {
            searchExecutor.shutdown();
        }
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