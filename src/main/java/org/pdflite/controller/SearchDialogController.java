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
    
    // FXML Components
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
    
    // Dependencies
    private PDFDocument pdfDocument;
    private SearchService searchService;
    private MainController mainController;
    
    // State
    private ExecutorService searchExecutor;
    private final ObservableList<SearchResult> searchResults = 
        FXCollections.observableArrayList();
    
    /**
     * Initialize the controller
     */
    @FXML
    public void initialize() {
        searchService = new SearchService();
        
        // Create executor service
        createExecutorService();
        
        // Setup results list view
        resultsListView.setItems(searchResults);
        resultsListView.setCellFactory(lv -> new SearchResultCell());
        
        // Handle result selection
        resultsListView.getSelectionModel().selectedItemProperty()
            .addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    handleResultSelected(newVal);
                }
            });
        
        // Setup search field (Enter key triggers search)
        searchField.setOnAction(e -> handleSearch());
        
        // Initial state
        progressIndicator.setVisible(false);
        cancelButton.setDisable(true);
        
        logger.debug("SearchDialogController initialized");
    }
    
    /**
     * Create or recreate executor service
     */
    private void createExecutorService() {
        // Shutdown old executor if exists
        if (searchExecutor != null && !searchExecutor.isShutdown()) {
            searchExecutor.shutdown();
        }
        
        // Create new executor
        searchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true); // Make daemon thread to not block JVM shutdown
            t.setName("SearchExecutor");
            return t;
        });
        
        logger.debug("Search executor service created");
    }
    
    /**
     * Set the PDF document to search in
     * @param pdfDocument The PDF document
     */
    public void setPDFDocument(PDFDocument pdfDocument) {
        this.pdfDocument = pdfDocument;
        
        // Reset UI when document changes
        searchResults.clear();
        updateStatus("Ready");
        prevResultButton.setDisable(true);
        nextResultButton.setDisable(true);
    }
    
    /**
     * Set reference to main controller for navigation
     * @param mainController The main controller
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }
    
    /**
     * Handle search button action
     */
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
        
        // Ensure executor is ready
        if (searchExecutor == null || searchExecutor.isShutdown()) {
            createExecutorService();
        }
        
        // Clear previous results
        searchResults.clear();
        
        // Update UI state
        searchButton.setDisable(true);
        cancelButton.setDisable(false);
        progressIndicator.setVisible(true);
        prevResultButton.setDisable(true);
        nextResultButton.setDisable(true);
        updateStatus("Searching...");
        
        // Get search options
        boolean caseSensitive = caseSensitiveCheckbox.isSelected();
        boolean wholeWord = wholeWordCheckbox.isSelected();
        
        // Perform search in background
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
        
        // Update UI on JavaFX thread
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
     * Handle cancel button action
     */
    @FXML
    private void handleCancel() {
        searchService.cancelSearch();
        cancelButton.setDisable(true);
    }
    
    /**
     * Handle close button action
     */
    @FXML
    private void handleClose() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }
    
    /**
     * Handle previous result button action
     */
    @FXML
    private void handlePreviousResult() {
        int currentIndex = resultsListView.getSelectionModel().getSelectedIndex();
        if (currentIndex > 0) {
            resultsListView.getSelectionModel().select(currentIndex - 1);
            updateNavigationButtons();
        }
    }
    
    /**
     * Handle next result button action
     */
    @FXML
    private void handleNextResult() {
        int currentIndex = resultsListView.getSelectionModel().getSelectedIndex();
        if (currentIndex < searchResults.size() - 1) {
            resultsListView.getSelectionModel().select(currentIndex + 1);
            updateNavigationButtons();
        }
    }
    
    /**
     * Update navigation button states
     */
    private void updateNavigationButtons() {
        int currentIndex = resultsListView.getSelectionModel().getSelectedIndex();
        prevResultButton.setDisable(currentIndex <= 0);
        nextResultButton.setDisable(currentIndex >= searchResults.size() - 1);
    }
    
    private void handleResultSelected(SearchResult result) {
        if (mainController != null && result != null) {
            // ✅ FIX: Use highlightSearchResult to set active result
            mainController.highlightSearchResult(result);
            
            int position = resultsListView.getSelectionModel().getSelectedIndex() + 1;
            updateStatus(String.format("Result %d of %d on page %d", 
                                    position, searchResults.size(), result.getPageNumber()));
            
            updateNavigationButtons();
        }
    }
    
    /**
     * Update status label
     * @param message Status message
     */
    private void updateStatus(String message) {
        statusLabel.setText(message);
    }
    
    /**
     * Show error alert
     * @param message Error message
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Search Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Cleanup resources
     * Called when dialog is closed
     */
    public void cleanup() {
        // Cancel ongoing search
        searchService.cancelSearch();
        
        // Shutdown executor
        if (searchExecutor != null && !searchExecutor.isShutdown()) {
            searchExecutor.shutdown();
            logger.info("Search executor shutdown");
        }
        
        logger.info("SearchDialogController cleanup completed");
    }
    
    /**
     * Custom cell for displaying search results
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