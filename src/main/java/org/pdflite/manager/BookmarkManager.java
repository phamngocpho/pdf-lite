package org.pdflite.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.pdflite.manager.ThemeManager;
import org.pdflite.model.Bookmark;
import org.pdflite.model.PDFDocument;
import org.pdflite.util.LocalDateTimeAdapter;
import org.pdflite.dialog.BookmarkInputDialog;
import org.pdflite.dialog.CustomConfirmDialog;
import org.pdflite.dialog.CustomInfoDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Manages bookmarks for PDF documents.
 * Handles creation, deletion, persistence, and UI for bookmarks.
 */
public class BookmarkManager {
    private static final Logger logger = LoggerFactory.getLogger(BookmarkManager.class);
    private static final String BOOKMARKS_DIR = ".pdflite/bookmarks";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final ObservableList<Bookmark> bookmarks;
    private final Gson gson;
    private PDFDocument currentDocument;
    private Consumer<Integer> onNavigateToPage;
    private ListView<Bookmark> bookmarkListView;
    private ThemeManager themeManager;

    public BookmarkManager() {
        this.bookmarks = FXCollections.observableArrayList();
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();
        
        // Create bookmarks directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(BOOKMARKS_DIR));
        } catch (IOException e) {
            logger.error("Failed to create bookmarks directory", e);
        }
    }

    /**
     * Sets the theme manager for styling dialogs.
     */
    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    /**
     * Sets the current document and loads its bookmarks.
     */
    public void setCurrentDocument(PDFDocument document) {
        this.currentDocument = document;
        loadBookmarks();
    }

    /**
     * Sets the callback for navigating to a page.
     */
    public void setOnNavigateToPage(Consumer<Integer> callback) {
        this.onNavigateToPage = callback;
    }

    /**
     * Adds a bookmark for the specified page.
     */
    public void addBookmark(int pageNumber, String title) {
        if (currentDocument == null) {
            logger.warn("Cannot add bookmark: no document loaded");
            return;
        }

        // Check if bookmark already exists
        if (bookmarks.stream().anyMatch(b -> b.getPageNumber() == pageNumber)) {
            logger.info("Bookmark already exists for page {}", pageNumber);
            return;
        }

        Bookmark bookmark = new Bookmark(pageNumber, title);
        bookmarks.add(bookmark);
        saveBookmarks();
        
        logger.info("Added bookmark for page {}: {}", pageNumber, title);
    }

    /**
     * Removes a bookmark.
     */
    public void removeBookmark(Bookmark bookmark) {
        bookmarks.remove(bookmark);
        saveBookmarks();
        logger.info("Removed bookmark for page {}", bookmark.getPageNumber());
    }

    /**
     * Checks if a page has a bookmark.
     */
    public boolean hasBookmark(int pageNumber) {
        return bookmarks.stream().anyMatch(b -> b.getPageNumber() == pageNumber);
    }

    /**
     * Gets all bookmarks.
     */
    public ObservableList<Bookmark> getBookmarks() {
        return bookmarks;
    }

    /**
     * Saves bookmarks to JSON file.
     */
    private void saveBookmarks() {
        if (currentDocument == null) {
            return;
        }

        String filename = getBookmarkFilename();
        Path filepath = Paths.get(BOOKMARKS_DIR, filename);

        try (Writer writer = new FileWriter(filepath.toFile())) {
            gson.toJson(bookmarks, writer);
            logger.info("Saved {} bookmarks to {}", bookmarks.size(), filepath);
        } catch (IOException e) {
            logger.error("Failed to save bookmarks", e);
        }
    }

    /**
     * Loads bookmarks from JSON file.
     */
    private void loadBookmarks() {
        bookmarks.clear();

        if (currentDocument == null) {
            return;
        }

        String filename = getBookmarkFilename();
        Path filepath = Paths.get(BOOKMARKS_DIR, filename);

        if (!Files.exists(filepath)) {
            logger.info("No bookmarks file found for current document");
            return;
        }

        try (Reader reader = new FileReader(filepath.toFile())) {
            Type listType = new TypeToken<ArrayList<Bookmark>>(){}.getType();
            List<Bookmark> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                bookmarks.addAll(loaded);
                logger.info("Loaded {} bookmarks from {}", bookmarks.size(), filepath);
            }
        } catch (IOException e) {
            logger.error("Failed to load bookmarks", e);
        }
    }

    /**
     * Exports bookmarks to a JSON file.
     */
    public void exportBookmarks(File file) {
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(bookmarks, writer);
            logger.info("Exported {} bookmarks to {}", bookmarks.size(), file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to export bookmarks", e);
            throw new RuntimeException("Failed to export bookmarks: " + e.getMessage());
        }
    }

    /**
     * Imports bookmarks from a JSON file.
     */
    public void importBookmarks(File file) {
        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<ArrayList<Bookmark>>(){}.getType();
            List<Bookmark> imported = gson.fromJson(reader, listType);
            
            if (imported != null) {
                bookmarks.clear();
                bookmarks.addAll(imported);
                saveBookmarks();
                logger.info("Imported {} bookmarks from {}", bookmarks.size(), file.getAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Failed to import bookmarks", e);
            throw new RuntimeException("Failed to import bookmarks: " + e.getMessage());
        }
    }

    /**
     * Creates the bookmark sidebar UI component.
     */
    public VBox createBookmarkSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(10));
        sidebar.setStyle("-fx-background-color: -fx-background;");
        sidebar.setPrefWidth(250);
        sidebar.setMinWidth(200);

        // Title
        Label titleLabel = new Label("Bookmarks");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Add bookmark button
        Button addButton = new Button("+ Add Bookmark");
        addButton.setMaxWidth(Double.MAX_VALUE);
        addButton.setOnAction(e -> showAddBookmarkDialog());

        // Export/Import buttons
        HBox actionButtons = new HBox(5);
        Button exportButton = new Button("Export");
        Button importButton = new Button("Import");
        exportButton.setMaxWidth(Double.MAX_VALUE);
        importButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(exportButton, Priority.ALWAYS);
        HBox.setHgrow(importButton, Priority.ALWAYS);
        
        exportButton.setOnAction(e -> handleExport());
        importButton.setOnAction(e -> handleImport());
        actionButtons.getChildren().addAll(exportButton, importButton);

        // Bookmark list
        bookmarkListView = new ListView<>(bookmarks);
        bookmarkListView.setCellFactory(lv -> new BookmarkCell());
        VBox.setVgrow(bookmarkListView, Priority.ALWAYS);

        sidebar.getChildren().addAll(titleLabel, addButton, actionButtons, bookmarkListView);

        return sidebar;
    }

    /**
     * Shows dialog to add a new bookmark.
     */
    private void showAddBookmarkDialog() {
        if (currentDocument == null) {
            showAlert("No Document", "Please open a PDF document first.");
            return;
        }

        int currentPage = currentDocument.getCurrentPage();
        
        String result = BookmarkInputDialog.show(
                "Add Bookmark",
                "Add bookmark for page " + (currentPage + 1),
                "Bookmark title:",
                "Page " + (currentPage + 1),
                themeManager
        );

        if (result != null && !result.trim().isEmpty()) {
            addBookmark(currentPage, result.trim());
        }
    }

    /**
     * Handles bookmark export.
     */
    private void handleExport() {
        if (bookmarks.isEmpty()) {
            showAlert("No Bookmarks", "There are no bookmarks to export.");
            return;
        }

        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Export Bookmarks");
        fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        fileChooser.setInitialFileName("bookmarks.json");

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                exportBookmarks(file);
                showAlert("Export Successful", "Bookmarks exported successfully.");
            } catch (Exception e) {
                showAlert("Export Failed", "Failed to export bookmarks: " + e.getMessage());
            }
        }
    }

    /**
     * Handles bookmark import.
     */
    private void handleImport() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Import Bookmarks");
        fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("JSON Files", "*.json")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                importBookmarks(file);
                showAlert("Import Successful", "Bookmarks imported successfully.");
            } catch (Exception e) {
                showAlert("Import Failed", "Failed to import bookmarks: " + e.getMessage());
            }
        }
    }

    /**
     * Gets the bookmark filename for the current document.
     */
    private String getBookmarkFilename() {
        if (currentDocument == null || currentDocument.getFile() == null) {
            return "bookmarks.json";
        }
        
        String filename = currentDocument.getFile().getName();
        return filename.replaceAll("[^a-zA-Z0-9.-]", "_") + "_bookmarks.json";
    }

    /**
     * Shows an alert dialog.
     */
    private void showAlert(String title, String message) {
        CustomInfoDialog.show(title, null, message, themeManager);
    }

    /**
     * Custom cell for displaying bookmarks in the list.
     */
    private class BookmarkCell extends ListCell<Bookmark> {
        private final HBox content;
        private final VBox textContent;
        private final Label titleLabel;
        private final Label pageLabel;
        private final Button deleteButton;
        private final Button goButton;

        public BookmarkCell() {
            content = new HBox(10);
            content.setAlignment(Pos.CENTER_LEFT);
            content.setPadding(new Insets(5));

            textContent = new VBox(2);
            titleLabel = new Label();
            titleLabel.setStyle("-fx-font-weight: bold;");
            pageLabel = new Label();
            pageLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
            textContent.getChildren().addAll(titleLabel, pageLabel);
            HBox.setHgrow(textContent, Priority.ALWAYS);

            goButton = new Button("Go");
            goButton.setStyle("-fx-font-size: 11px; -fx-padding: 5 10;");
            
            deleteButton = new Button("×");
            deleteButton.setStyle("-fx-font-size: 18px; -fx-text-fill: red; -fx-background-color: transparent; -fx-cursor: hand;");
            deleteButton.setMinWidth(30);
            deleteButton.setPrefWidth(30);

            content.getChildren().addAll(textContent, goButton, deleteButton);
        }

        @Override
        protected void updateItem(Bookmark bookmark, boolean empty) {
            super.updateItem(bookmark, empty);

            if (empty || bookmark == null) {
                setGraphic(null);
                setContextMenu(null);
            } else {
                titleLabel.setText(bookmark.getTitle());
                pageLabel.setText("Page " + (bookmark.getPageNumber() + 1) + " • " + 
                                bookmark.getCreatedAt().format(TIME_FORMATTER));

                // Go button action
                goButton.setOnAction(e -> {
                    if (onNavigateToPage != null) {
                        onNavigateToPage.accept(bookmark.getPageNumber());
                    }
                });

                // Delete button action
                deleteButton.setOnAction(e -> confirmAndDeleteBookmark(bookmark));

                // Context menu for right-click
                ContextMenu contextMenu = new ContextMenu();
                
                MenuItem goToPageItem = new MenuItem("Go to Page");
                goToPageItem.setOnAction(e -> {
                    if (onNavigateToPage != null) {
                        onNavigateToPage.accept(bookmark.getPageNumber());
                    }
                });
                
                MenuItem editItem = new MenuItem("Edit Title");
                editItem.setOnAction(e -> showEditBookmarkDialog(bookmark));
                
                MenuItem deleteItem = new MenuItem("Delete");
                deleteItem.setStyle("-fx-text-fill: red;");
                deleteItem.setOnAction(e -> confirmAndDeleteBookmark(bookmark));
                
                contextMenu.getItems().addAll(goToPageItem, editItem, new SeparatorMenuItem(), deleteItem);
                setContextMenu(contextMenu);

                setGraphic(content);
            }
        }
    }

    /**
     * Confirms and deletes a bookmark.
     */
    private void confirmAndDeleteBookmark(Bookmark bookmark) {
        boolean confirmed = CustomConfirmDialog.show(
                "Delete Bookmark",
                "Delete this bookmark?",
                bookmark.getTitle(),
                themeManager
        );
        
        if (confirmed) {
            removeBookmark(bookmark);
        }
    }

    /**
     * Shows dialog to edit bookmark title.
     */
    private void showEditBookmarkDialog(Bookmark bookmark) {
        String result = BookmarkInputDialog.show(
                "Edit Bookmark",
                "Edit bookmark title",
                "New title:",
                bookmark.getTitle(),
                themeManager
        );

        if (result != null && !result.trim().isEmpty()) {
            bookmark.setTitle(result.trim());
            saveBookmarks();
            // Refresh the list view
            if (bookmarkListView != null) {
                bookmarkListView.refresh();
            }
            logger.info("Updated bookmark title to: {}", result);
        }
    }
}
