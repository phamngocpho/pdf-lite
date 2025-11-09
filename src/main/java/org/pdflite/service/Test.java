package org.pdflite.service;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Demo class for PDF text search functionality
 * Demonstrates:
 * - File chooser for opening PDF files
 * - Search bar with real-time search capability
 * - Region-based text extraction using PDFBox
 * - Background thread processing
 */
public class Test extends Application {
    
    // Logger
    private static final Logger logger = LoggerFactory.getLogger(Test.class);
    
    // Constants
    private static final int WINDOW_WIDTH = 800;
    private static final int WINDOW_HEIGHT = 600;
    private static final int SEARCH_REGION_WIDTH = 600;
    private static final int SEARCH_REGION_HEIGHT = 800;
    
    // UI Components
    private TextField searchField;
    private TextArea resultArea;
    private Label statusLabel;
    private Button openButton;
    private Button searchButton;
    private ProgressIndicator progressIndicator;
    
    // PDF Components
    private PDDocument currentDocument;
    private File currentFile;
    
    // Thread pool for background tasks
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
    
    /**
     * Main entry point
     */
    public static void main(String[] args) {
        launch(args);
    }
    
    /**
     * JavaFX application start method
     * @param primaryStage The primary stage
     */
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("PDF Text Search Demo");
        
        // Create main layout
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        
        // Top: File chooser section
        VBox topSection = createFileChooserSection();
        root.setTop(topSection);
        
        // Center: Search bar and results
        VBox centerSection = createSearchSection();
        root.setCenter(centerSection);
        
        // Bottom: Status bar
        HBox bottomSection = createStatusBar();
        root.setBottom(bottomSection);
        
        // Create scene
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.show();
        
        logger.info("PDF Search Demo application started");
    }
    
    /**
     * Creates the file chooser section with open button
     * @return VBox containing file chooser UI
     */
    private VBox createFileChooserSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc;");
        
        Label label = new Label("Select PDF File:");
        label.setStyle("-fx-font-weight: bold;");
        
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        
        openButton = new Button("Open PDF...");
        openButton.setOnAction(e -> handleOpenPDF());
        openButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        
        Label fileLabel = new Label("No file selected");
        fileLabel.setId("fileLabel");
        fileLabel.setStyle("-fx-text-fill: #666666;");
        
        buttonBox.getChildren().addAll(openButton, fileLabel);
        section.getChildren().addAll(label, buttonBox);
        
        return section;
    }
    
    /**
     * Creates the search section with search bar and results area
     * @return VBox containing search UI
     */
    private VBox createSearchSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        VBox.setVgrow(section, Priority.ALWAYS);
        
        // Search bar
        HBox searchBar = new HBox(10);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        
        Label searchLabel = new Label("Search:");
        searchLabel.setStyle("-fx-font-weight: bold;");
        
        searchField = new TextField();
        searchField.setPromptText("Enter keyword to search...");
        searchField.setPrefWidth(400);
        searchField.setOnAction(e -> handleSearch());
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        searchButton = new Button("Search");
        searchButton.setOnAction(e -> handleSearch());
        searchButton.setDisable(true);
        searchButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        
        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(20, 20);
        progressIndicator.setVisible(false);
        
        searchBar.getChildren().addAll(
            searchLabel, 
            searchField, 
            searchButton, 
            progressIndicator
        );
        
        // Results area
        Label resultsLabel = new Label("Search Results:");
        resultsLabel.setStyle("-fx-font-weight: bold;");
        
        resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setPromptText("Search results will appear here...");
        resultArea.setWrapText(true);
        VBox.setVgrow(resultArea, Priority.ALWAYS);
        
        section.getChildren().addAll(searchBar, resultsLabel, resultArea);
        
        return section;
    }
    
    /**
     * Creates the status bar at bottom
     * @return HBox containing status bar
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox();
        statusBar.setPadding(new Insets(5));
        statusBar.setStyle("-fx-background-color: #e0e0e0; -fx-border-color: #cccccc;");
        
        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #333333;");
        
        statusBar.getChildren().add(statusLabel);
        
        return statusBar;
    }
    
    /**
     * Handles the open PDF button action
     * Opens file chooser dialog and loads selected PDF
     */
    private void handleOpenPDF() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open PDF File");
        
        // Set extension filter
        FileChooser.ExtensionFilter pdfFilter = 
            new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf");
        fileChooser.getExtensionFilters().add(pdfFilter);
        
        // Set initial directory
        File initialDir = new File(System.getProperty("user.home"));
        if (initialDir.exists()) {
            fileChooser.setInitialDirectory(initialDir);
        }
        
        // Show open dialog
        File selectedFile = fileChooser.showOpenDialog(openButton.getScene().getWindow());
        
        if (selectedFile != null) {
            openPDFFile(selectedFile);
        }
    }
    
    /**
     * Opens and loads a PDF file
     * @param file The PDF file to open
     */
    private void openPDFFile(File file) {
        // Close previous document if exists
        closePreviousDocument();
        
        try {
            currentDocument = Loader.loadPDF(file);
            currentFile = file;
            
            // Update UI
            updateStatus("Loaded: " + file.getName() + 
                        " (" + currentDocument.getNumberOfPages() + " pages)");
            
            Label fileLabel = (Label) openButton.getParent().lookup("#fileLabel");
            fileLabel.setText(file.getName());
            fileLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
            
            searchButton.setDisable(false);
            
            logger.info("Successfully opened PDF: {}", file.getAbsolutePath());
            
        } catch (IOException e) {
            logger.error("Error opening PDF file: {}", file.getName(), e);
            showError("Error opening PDF: " + e.getMessage());
            updateStatus("Error loading PDF");
        }
    }
    
    /**
     * Handles the search button action
     * Performs text search across all pages
     */
    private void handleSearch() {
        if (currentDocument == null) {
            showError("Please open a PDF file first");
            return;
        }
        
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            showError("Please enter a search keyword");
            return;
        }
        
        // Disable search during processing
        searchButton.setDisable(true);
        progressIndicator.setVisible(true);
        resultArea.clear();
        updateStatus("Searching for: " + keyword);
        
        // Perform search in background thread
        searchExecutor.submit(() -> performSearch(keyword));
    }
    
    /**
     * Performs text search in PDF document
     * Runs in background thread
     * @param keyword The keyword to search for
     */
    private void performSearch(String keyword) {
        List<SearchResult> results = new ArrayList<>();
        int totalPages = currentDocument.getNumberOfPages();
        
        try {
            // Search using PDFTextStripper for full text
            PDFTextStripper textStripper = new PDFTextStripper();
            
            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                int pageNumber = pageIndex + 1;
                
                // Extract text from current page
                textStripper.setStartPage(pageNumber);
                textStripper.setEndPage(pageNumber);
                String pageText = textStripper.getText(currentDocument);
                
                // Search for keyword (case-insensitive)
                int index = 0;
                String lowerPageText = pageText.toLowerCase();
                String lowerKeyword = keyword.toLowerCase();
                
                while ((index = lowerPageText.indexOf(lowerKeyword, index)) >= 0) {
                    // Extract context around the keyword
                    int contextStart = Math.max(0, index - 50);
                    int contextEnd = Math.min(pageText.length(), index + keyword.length() + 50);
                    String context = pageText.substring(contextStart, contextEnd);
                    
                    results.add(new SearchResult(pageNumber, index, context));
                    index += keyword.length();
                }
                
                // Update progress
                final int currentPage = pageNumber;
                Platform.runLater(() -> 
                    updateStatus("Searching... Page " + currentPage + "/" + totalPages)
                );
            }
            
            // Display results on UI thread
            Platform.runLater(() -> displaySearchResults(keyword, results));
            
            logger.info("Search completed. Found {} occurrences of '{}'", 
                       results.size(), keyword);
            
        } catch (IOException e) {
            logger.error("Error during search", e);
            Platform.runLater(() -> {
                showError("Error during search: " + e.getMessage());
                updateStatus("Search failed");
            });
        } finally {
            // Re-enable search button
            Platform.runLater(() -> {
                searchButton.setDisable(false);
                progressIndicator.setVisible(false);
            });
        }
    }
    
    /**
     * Displays search results in the result area
     * @param keyword The searched keyword
     * @param results List of search results
     */
    private void displaySearchResults(String keyword, List<SearchResult> results) {
        if (results.isEmpty()) {
            resultArea.setText("No results found for: " + keyword);
            updateStatus("No results found");
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(results.size())
          .append(" occurrence(s) of '").append(keyword).append("'\n\n");
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            sb.append("[").append(i + 1).append("] Page ")
              .append(result.pageNumber)
              .append(" (position ").append(result.position).append(")\n");
            sb.append("  ...").append(result.context.trim()).append("...\n\n");
        }
        
        resultArea.setText(sb.toString());
        updateStatus("Found " + results.size() + " results");
    }
    
    /**
     * Closes the previous PDF document
     */
    private void closePreviousDocument() {
        if (currentDocument != null) {
            try {
                currentDocument.close();
                logger.info("Closed previous PDF document");
            } catch (IOException e) {
                logger.error("Error closing PDF document", e);
            }
        }
    }
    
    /**
     * Updates the status label
     * @param message The status message
     */
    private void updateStatus(String message) {
        statusLabel.setText(message);
    }
    
    /**
     * Shows an error alert
     * @param message The error message
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Cleanup method called when application stops
     */
    @Override
    public void stop() {
        closePreviousDocument();
        searchExecutor.shutdown();
        logger.info("PDF Search Demo application stopped");
    }
    
    /**
     * Inner class to hold search result data
     */
    private static class SearchResult {
        final int pageNumber;
        final int position;
        final String context;
        
        SearchResult(int pageNumber, int position, String context) {
            this.pageNumber = pageNumber;
            this.position = position;
            this.context = context;
        }
    }
}