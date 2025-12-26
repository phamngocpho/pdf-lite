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
import org.pdflite.manager.LanguageManager;
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

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private final ObservableList<Bookmark> bookmarks;
    private final Gson gson;
    private PDFDocument currentDocument;
    private java.util.function.BiConsumer<Integer, Float> onNavigateToPage; // pageNumber, yPosition
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
     * Sets the callback for navigating to a page with Y position.
     */
    public void setOnNavigateToPage(java.util.function.BiConsumer<Integer, Float> callback) {
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
        
        // Refresh ListView to fix layout issues
        refreshListView();
        
        logger.info("Added bookmark for page {}: {}", pageNumber, title);
    }

    /**
     * Adds multiple bookmarks at once (batch operation).
     * More efficient than adding one by one.
     */
    public void addBookmarksBatch(List<Bookmark> newBookmarks) {
        if (currentDocument == null) {
            logger.warn("Cannot add bookmarks: no document loaded");
            return;
        }

        int added = 0;
        for (Bookmark bookmark : newBookmarks) {
            // Check if bookmark already exists
            if (!bookmarks.stream().anyMatch(b -> b.getPageNumber() == bookmark.getPageNumber())) {
                bookmarks.add(bookmark);
                added++;
            }
        }

        if (added > 0) {
            saveBookmarks();
            // Refresh ListView after batch add
            refreshListView();
            logger.info("Added {} bookmarks in batch", added);
        }
    }

    /**
     * Refreshes the ListView by reloading from file (same as app startup).
     * This fixes layout issues when adding items dynamically.
     */
    public void refreshListView() {
        if (bookmarkListView != null && currentDocument != null) {
            javafx.application.Platform.runLater(() -> {
                // Reload from file - exactly like app startup
                loadBookmarks();
            });
        }
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
     * Removes all bookmarks.
     */
    public void clearAllBookmarks() {
        int count = bookmarks.size();
        bookmarks.clear();
        saveBookmarks();
        logger.info("Cleared all {} bookmarks", count);
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
        sidebar.setPrefWidth(280);
        sidebar.setMinWidth(250);

        // Title
        Label titleLabel = new Label(lang().getString("bookmark.title"));
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Add bookmark button
        Button addButton = new Button("+ " + lang().getString("bookmark.add"));
        addButton.setMaxWidth(Double.MAX_VALUE);
        addButton.setOnAction(e -> showAddBookmarkDialog());

        // Clear all button
        Button clearAllButton = new Button(lang().getString("bookmark.clearAll"));
        clearAllButton.setMaxWidth(Double.MAX_VALUE);
        clearAllButton.setStyle("-fx-text-fill: #f44336;"); // Red text
        clearAllButton.setOnAction(e -> handleClearAll());

        // Export/Import buttons
        HBox actionButtons = new HBox(5);
        Button exportButton = new Button(lang().getString("bookmark.export"));
        Button importButton = new Button(lang().getString("bookmark.import"));
        exportButton.setMaxWidth(Double.MAX_VALUE);
        importButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(exportButton, Priority.ALWAYS);
        HBox.setHgrow(importButton, Priority.ALWAYS);
        
        exportButton.setOnAction(e -> handleExport());
        importButton.setOnAction(e -> handleImport());
        actionButtons.getChildren().addAll(exportButton, importButton);

        // Bookmark list with fixed cell size to prevent layout bugs
        bookmarkListView = new ListView<>(bookmarks);
        bookmarkListView.setFixedCellSize(52); // Fixed height for each cell
        bookmarkListView.setCellFactory(lv -> new BookmarkCell());
        VBox.setVgrow(bookmarkListView, Priority.ALWAYS);
        
        // Disable horizontal scrollbar
        bookmarkListView.setStyle("-fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        
        // Force no horizontal scrollbar
        bookmarkListView.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                ScrollBar hBar = (ScrollBar) bookmarkListView.lookup(".scroll-bar:horizontal");
                if (hBar != null) {
                    hBar.setManaged(false);
                    hBar.setVisible(false);
                    hBar.setPrefWidth(0);
                    hBar.setMaxWidth(0);
                }
            }
        });

        sidebar.getChildren().addAll(titleLabel, addButton, clearAllButton, actionButtons, bookmarkListView);

        return sidebar;
    }

    /**
     * Shows dialog to add a new bookmark.
     */
    private void showAddBookmarkDialog() {
        if (currentDocument == null) {
            showAlert(lang().getString("error.noDocument"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        int currentPage = currentDocument.getCurrentPage();
        
        String result = BookmarkInputDialog.show(
                lang().getString("bookmark.add"),
                java.text.MessageFormat.format(lang().getString("bookmark.addForPage"), currentPage + 1),
                lang().getString("bookmark.titleLabel"),
                java.text.MessageFormat.format(lang().getString("bookmark.defaultTitle"), currentPage + 1),
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
            showAlert(lang().getString("bookmark.noBookmarks"), lang().getString("bookmark.noBookmarksToExport"));
            return;
        }

        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle(lang().getString("bookmark.export"));
        fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        fileChooser.setInitialFileName("bookmarks.json");

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                exportBookmarks(file);
                showAlert(lang().getString("bookmark.exportSuccess"), lang().getString("bookmark.exportSuccessMsg"));
            } catch (Exception e) {
                showAlert(lang().getString("bookmark.exportFailed"), lang().getString("bookmark.exportFailedMsg") + ": " + e.getMessage());
            }
        }
    }

    /**
     * Handles bookmark import.
     */
    private void handleImport() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle(lang().getString("bookmark.import"));
        fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("JSON Files", "*.json")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                importBookmarks(file);
                showAlert(lang().getString("bookmark.importSuccess"), lang().getString("bookmark.importSuccessMsg"));
            } catch (Exception e) {
                showAlert(lang().getString("bookmark.importFailed"), lang().getString("bookmark.importFailedMsg") + ": " + e.getMessage());
            }
        }
    }

    /**
     * Handles clear all bookmarks.
     */
    private void handleClearAll() {
        if (bookmarks.isEmpty()) {
            showAlert(lang().getString("bookmark.noBookmarks"), lang().getString("bookmark.noBookmarksToClear"));
            return;
        }

        boolean confirmed = CustomConfirmDialog.show(
                lang().getString("bookmark.clearAllTitle"),
                lang().getString("bookmark.clearAllHeader"),
                java.text.MessageFormat.format(lang().getString("bookmark.clearAllMsg"), bookmarks.size()),
                themeManager
        );

        if (confirmed) {
            clearAllBookmarks();
            showAlert(lang().getString("bookmark.cleared"), lang().getString("bookmark.clearedMsg"));
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
        private final Button goButton;

        public BookmarkCell() {
            setPadding(Insets.EMPTY);
            setStyle("-fx-padding: 0;");
            
            content = new HBox(6);
            content.setAlignment(Pos.CENTER_LEFT);
            content.setPadding(new Insets(4, 8, 4, 8));
            content.setMinHeight(48);
            content.setMaxHeight(48);
            content.setPrefHeight(48);

            textContent = new VBox(1);
            textContent.setMinWidth(0);
            textContent.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(textContent, Priority.ALWAYS);
            
            titleLabel = new Label();
            titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            titleLabel.setWrapText(false);
            titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            titleLabel.setMinWidth(0);
            titleLabel.setMaxWidth(Double.MAX_VALUE);
            
            pageLabel = new Label();
            pageLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
            pageLabel.setWrapText(false);
            pageLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            pageLabel.setMinWidth(0);
            pageLabel.setMaxWidth(Double.MAX_VALUE);
            
            textContent.getChildren().addAll(titleLabel, pageLabel);

            goButton = new Button(lang().getString("bookmark.go"));
            goButton.setStyle("-fx-font-size: 13px; -fx-padding: 4 10;");
            goButton.setMinWidth(42);
            goButton.setPrefWidth(42);
            goButton.setMaxWidth(42);

            content.getChildren().addAll(textContent, goButton);
            
            // Bind width to ListView width minus scrollbar
            content.maxWidthProperty().bind(
                javafx.beans.binding.Bindings.createDoubleBinding(
                    () -> getListView() != null ? getListView().getWidth() - 20 : 200,
                    widthProperty()
                )
            );
            
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(Bookmark bookmark, boolean empty) {
            super.updateItem(bookmark, empty);

            if (empty || bookmark == null) {
                setGraphic(null);
                setContextMenu(null);
                setText(null);
            } else {
                titleLabel.setText(bookmark.getTitle());
                pageLabel.setText(java.text.MessageFormat.format(lang().getString("bookmark.pageInfo"), 
                                bookmark.getPageNumber() + 1) + " • " + 
                                bookmark.getCreatedAt().format(TIME_FORMATTER));

                // Go button action
                goButton.setOnAction(e -> {
                    if (onNavigateToPage != null) {
                        onNavigateToPage.accept(bookmark.getPageNumber(), bookmark.getYPosition());
                    }
                });

                // Context menu for right-click
                ContextMenu contextMenu = new ContextMenu();
                
                MenuItem goToPageItem = new MenuItem(lang().getString("bookmark.goTo"));
                goToPageItem.setOnAction(e -> {
                    if (onNavigateToPage != null) {
                        onNavigateToPage.accept(bookmark.getPageNumber(), bookmark.getYPosition());
                    }
                });
                
                MenuItem editItem = new MenuItem(lang().getString("bookmark.editTitle"));
                editItem.setOnAction(e -> showEditBookmarkDialog(bookmark));
                
                MenuItem deleteItem = new MenuItem(lang().getString("bookmark.delete"));
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
                lang().getString("bookmark.deleteTitle"),
                lang().getString("bookmark.deleteHeader"),
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
                lang().getString("bookmark.editBookmark"),
                lang().getString("bookmark.editBookmarkHeader"),
                lang().getString("bookmark.newTitle"),
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
