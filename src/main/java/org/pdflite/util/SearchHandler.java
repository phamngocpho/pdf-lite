package org.pdflite.util;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import org.pdflite.controller.MainController;
import org.pdflite.manager.LanguageManager;
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
 * Shared search handler for search functionality.
 * <p>
 * This class encapsulates common search logic that can be reused
 * by both SearchPanel and SearchDialogController to avoid code duplication.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class SearchHandler {

    private static final Logger logger = LoggerFactory.getLogger(SearchHandler.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private final SearchService searchService;
    private ExecutorService searchExecutor;
    private final String executorThreadName;

    /**
     * Interface for UI component callbacks during search operations.
     */
    public interface SearchUICallbacks {
        /**
         * Gets the search keyword from the UI.
         *
         * @return the search keyword
         */
        String getSearchKeyword();

        /**
         * Gets whether case-sensitive search is enabled.
         *
         * @return true if case-sensitive search is enabled
         */
        boolean isCaseSensitive();

        /**
         * Gets whether whole-word search is enabled.
         *
         * @return true if whole-word search is enabled
         */
        boolean isWholeWord();

        /**
         * Gets the observable list of search results.
         *
         * @return the observable list of search results
         */
        ObservableList<SearchResult> getSearchResults();

        /**
         * Gets the main controller for applying highlights.
         *
         * @return the main controller, or null if not available
         */
        MainController getMainController();

        /**
         * Updates the status message.
         *
         * @param message the status message
         */
        void updateStatus(String message);

        /**
         * Shows an error message.
         *
         * @param message the error message
         */
        void showError(String message);

        /**
         * Called when search starts to update UI state.
         */
        void onSearchStart();

        /**
         * Called when search completes to update UI state.
         */
        void onSearchComplete();

        /**
         * Called when search results are ready to update UI.
         *
         * @param results the search results
         */
        void onSearchResultsReady(List<SearchResult> results);
    }

    /**
     * Creates a new SearchHandler.
     *
     * @param executorThreadName the name for the executor thread
     */
    public SearchHandler(String executorThreadName) {
        this.searchService = new SearchService();
        this.executorThreadName = executorThreadName;
    }

    /**
     * Creates the executor service for search operations.
     */
    public void createExecutorService() {
        if (searchExecutor != null && !searchExecutor.isShutdown()) {
            searchExecutor.shutdown();
        }

        searchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName(executorThreadName);
            return t;
        });

        logger.debug("Search executor service created: {}", executorThreadName);
    }

    /**
     * Validates search input and document.
     *
     * @param keyword     the search keyword
     * @param pdfDocument the PDF document
     * @param callbacks   the UI callbacks
     * @return true if validation passes, false otherwise
     */
    public boolean validateSearch(String keyword, PDFDocument pdfDocument, SearchUICallbacks callbacks) {
        if (keyword.isEmpty()) {
            callbacks.showError(lang().getString("search.enterKeyword"));
            return true;
        }

        if (pdfDocument == null) {
            callbacks.showError(lang().getString("search.noDocument"));
            return true;
        }

        return false;
    }

    /**
     * Prepares the search executor service.
     */
    public void prepareExecutor() {
        if (searchExecutor == null || searchExecutor.isShutdown()) {
            createExecutorService();
        }
    }

    /**
     * Executes a search operation with full flow management.
     * <p>
     * This method handles the complete search flow, including validation,
     * UI state updates, and search execution. It should be called from
     * the UI component's search handler method.
     * </p>
     *
     * @param pdfDocument the PDF document to search
     * @param callbacks   the UI callbacks for updates
     */
    public void executeSearch(PDFDocument pdfDocument, SearchUICallbacks callbacks) {
        String keyword = callbacks.getSearchKeyword();

        // Validate search input
        if (validateSearch(keyword, pdfDocument, callbacks)) {
            return; // Validation failed
        }

        // Prepare executor
        prepareExecutor();

        // Clear previous results
        callbacks.getSearchResults().clear();

        // Update UI state for search start
        callbacks.onSearchStart();
        callbacks.updateStatus(lang().getString("search.searching"));

        // Submit the search task
        searchExecutor.submit(() -> performSearch(pdfDocument, callbacks));
    }

    /**
     * Performs the search operation.
     *
     * @param pdfDocument the PDF document to search
     * @param callbacks   the UI callbacks for updates
     */
    public void performSearch(PDFDocument pdfDocument, SearchUICallbacks callbacks) {
        String keyword = callbacks.getSearchKeyword();
        boolean caseSensitive = callbacks.isCaseSensitive();
        boolean wholeWord = callbacks.isWholeWord();

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
                    callbacks.updateStatus(lang().getString("search.cancelled"));
                } else {
                    callbacks.getSearchResults().addAll(results);
                    String message = lang().getString("search.found").replace("{0}", String.valueOf(results.size()));
                    callbacks.updateStatus(message);

                    // Apply highlights to the main viewer
                    MainController mainController = callbacks.getMainController();
                    if (mainController != null && !results.isEmpty()) {
                        mainController.highlightSearchResults(results);
                        logger.info("Applied {} highlights from search", results.size());
                    }

                    callbacks.onSearchResultsReady(results);
                }
            });

        } catch (IOException e) {
            logger.error("Error during search", e);
            Platform.runLater(() -> {
                callbacks.showError(lang().getString("search.failed") + ": " + e.getMessage());
                callbacks.updateStatus(lang().getString("search.failed"));
            });
        } finally {
            Platform.runLater(callbacks::onSearchComplete);
        }
    }

    /**
     * Cancels the current search operation.
     */
    public void cancelSearch() {
        searchService.cancelSearch();
    }

    /**
     * Cleans up resources.
     */
    public void cleanup() {
        searchService.cancelSearch();
        if (searchExecutor != null && !searchExecutor.isShutdown()) {
            searchExecutor.shutdown();
            logger.info("Search executor shutdown: {}", executorThreadName);
        }
    }

    /**
     * Gets the search service instance.
     *
     * @return the search service
     */
    public SearchService getSearchService() {
        return searchService;
    }

    /**
     * Gets the executor service instance.
     *
     * @return the executor service
     */
    public ExecutorService getSearchExecutor() {
        return searchExecutor;
    }
}

