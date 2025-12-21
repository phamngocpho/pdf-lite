package org.pdflite.manager;

import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.pdflite.dialog.BookmarkInputDialog;
import org.pdflite.model.PDFDocument;
import org.pdflite.util.NavigationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages bookmark UI operations including sidebar visibility and bookmark dialogs.
 */
public class BookmarkUIManager {
    private static final Logger logger = LoggerFactory.getLogger(BookmarkUIManager.class);

    private final BorderPane rootPane;
    private final BookmarkManager bookmarkManager;
    private final UIStateManager uiStateManager;
    private final NavigationHelper navigationHelper;
    private ThemeManager themeManager;

    private VBox bookmarkSidebar;
    private boolean bookmarkSidebarVisible = false;

    /**
     * Creates a new BookmarkUIManager.
     *
     * @param rootPane         the root pane for sidebar placement
     * @param bookmarkManager  the bookmark manager
     * @param uiStateManager   the UI state manager
     * @param navigationHelper the navigation helper
     */
    public BookmarkUIManager(BorderPane rootPane, 
                            BookmarkManager bookmarkManager,
                            UIStateManager uiStateManager,
                            NavigationHelper navigationHelper) {
        this.rootPane = rootPane;
        this.bookmarkManager = bookmarkManager;
        this.uiStateManager = uiStateManager;
        this.navigationHelper = navigationHelper;

        // Set navigation callback
        bookmarkManager.setOnNavigateToPage(this::navigateToBookmarkedPage);
    }

    /**
     * Sets the theme manager for styling dialogs.
     */
    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
        if (bookmarkManager != null) {
            bookmarkManager.setThemeManager(themeManager);
        }
    }

    /**
     * Toggles the bookmark sidebar visibility.
     */
    public void handleToggleBookmarks(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError("No Document", "Please open a PDF document first.");
            return;
        }

        bookmarkSidebarVisible = !bookmarkSidebarVisible;

        if (bookmarkSidebarVisible) {
            showBookmarkSidebar();
        } else {
            hideBookmarkSidebar();
        }
    }

    /**
     * Shows dialog to add a bookmark for the current page.
     */
    public void handleAddBookmark(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError("No Document", "Please open a PDF document first.");
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
            bookmarkManager.addBookmark(currentPage, result.trim());
            uiStateManager.updateStatus("Bookmark added for page " + (currentPage + 1));
        }
    }

    /**
     * Shows the bookmark sidebar.
     */
    private void showBookmarkSidebar() {
        if (bookmarkSidebar == null) {
            bookmarkSidebar = bookmarkManager.createBookmarkSidebar();
        }

        if (rootPane.getRight() == null) {
            rootPane.setRight(bookmarkSidebar);
            logger.info("Bookmark sidebar shown");
        }
    }

    /**
     * Hides the bookmark sidebar.
     */
    private void hideBookmarkSidebar() {
        if (rootPane.getRight() == bookmarkSidebar) {
            rootPane.setRight(null);
            logger.info("Bookmark sidebar hidden");
        }
    }

    /**
     * Navigates to a bookmarked page.
     */
    private void navigateToBookmarkedPage(int pageNumber) {
        if (navigationHelper != null) {
            navigationHelper.navigateToPage(pageNumber);
            uiStateManager.updateStatus("Navigated to bookmarked page " + (pageNumber + 1));
        }
    }

    /**
     * Checks if the bookmark sidebar is currently visible.
     */
    public boolean isBookmarkSidebarVisible() {
        return bookmarkSidebarVisible;
    }
}
