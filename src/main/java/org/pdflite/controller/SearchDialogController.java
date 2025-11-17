package org.pdflite.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
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
 * Controller for the Search Dialog
 * Handles user interaction for searching text in PDF
 */
public class SearchDialogController {

    private static final Logger logger = LoggerFactory.getLogger(SearchDialogController.class);

    @FXML private TextField searchField;
    @FXML private CheckBox caseSensitiveCheckbox;
    @FXML private CheckBox wholeWordCheckbox;
    @FXML private Button searchButton;
    @FXML private Button cancelButton;
    @FXML private Button closeButton;
    @FXML private Button prevResultButton;
    @FXML private Button nextResultButton;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label statusLabel;
    @FXML private ListView<SearchResult> resultsListView;

    private PDFDocument pdfDocument;
    private SearchService searchService;
    private MainController mainController;

    private ExecutorService searchExecutor;
    private final ObservableList<SearchResult> searchResults =
            FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        searchService = new SearchService();

        createExecutorService();

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
        cancelButton.setDisable(true);

        logger.debug("SearchDialogController initialized");
    }

    private void createExecutorService() {
        if (searchExecutor != null && !searchExecutor.isShutdown()) {
            searchExecutor.shutdown();
        }

        searchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true); // Make daemon thread to not block JVM shutdown
            t.setName("SearchExecutor");
            return t;
        });

        logger.debug("Search executor service created");
    }

    public void setPDFDocument(PDFDocument pdfDocument) {
        this.pdfDocument = pdfDocument;

        searchResults.clear();
        updateStatus("Ready");
        prevResultButton.setDisable(true);
        nextResultButton.setDisable(true);
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
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

    @FXML
    private void handleCancel() {
        searchService.cancelSearch();
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
            updateStatus(String.format("Result %d of %d on page %d", 
                                    position, searchResults.size(), result.pageNumber()));
            
            updateNavigationButtons();
        }
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Search Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void cleanup() {
        searchService.cancelSearch();

        if (searchExecutor != null && !searchExecutor.isShutdown()) {
            searchExecutor.shutdown();
            logger.info("Search executor shutdown");
        }

        logger.info("SearchDialogController cleanup completed");
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