package org.pdflite.manager;

import javafx.application.Platform;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.pdflite.config.AIConfig;
import org.pdflite.dialog.BookmarkInputDialog;
import org.pdflite.dialog.CustomConfirmDialog;
import org.pdflite.dialog.CustomInfoDialog;
import org.pdflite.dialog.PrivacyConsentDialog;
import org.pdflite.model.PDFDocument;
import org.pdflite.util.NavigationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Manages bookmark UI operations including sidebar visibility and bookmark dialogs.
 * Separates bookmark logic from MainController following MVC pattern.
 */
public class BookmarkUIManager {
    private static final Logger logger = LoggerFactory.getLogger(BookmarkUIManager.class);

    private final BorderPane rootPane;
    private final BookmarkManager bookmarkManager;
    private final UIStateManager uiStateManager;
    private final NavigationHelper navigationHelper;
    private ThemeManager themeManager;
    
    // Optional managers for extended operations
    private PDFOutlineBookmarkManager pdfOutlineBookmarkManager;
    private SmartBookmarkManager smartBookmarkManager;
    private Supplier<PDFDocument> documentSupplier;

    private VBox bookmarkSidebar;
    private boolean bookmarkSidebarVisible = false;

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

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
     * Sets extended bookmark managers for outline and smart bookmarks.
     */
    public void setExtendedManagers(
            PDFOutlineBookmarkManager pdfOutlineBookmarkManager,
            SmartBookmarkManager smartBookmarkManager,
            Supplier<PDFDocument> documentSupplier) {
        this.pdfOutlineBookmarkManager = pdfOutlineBookmarkManager;
        this.smartBookmarkManager = smartBookmarkManager;
        this.documentSupplier = documentSupplier;
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
            uiStateManager.showError(lang().getString("error.noDocument"), lang().getString("error.noPdfLoadedMsg"));
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
            uiStateManager.showError(lang().getString("error.noDocument"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        int currentPage = currentDocument.getCurrentPage();

        String result = BookmarkInputDialog.show(
                lang().getString("bookmark.add"),
                lang().getString("bookmark.add") + " - " + lang().getString("status.page", currentPage + 1, ""),
                lang().getString("bookmark.title") + ":",
                lang().getString("status.page", currentPage + 1, ""),
                themeManager
        );

        if (result != null && !result.trim().isEmpty()) {
            bookmarkManager.addBookmark(currentPage, result.trim());
            uiStateManager.updateStatus(lang().getString("success.bookmarkAdded"));
        }
    }

    /**
     * Clears all bookmarks for the current document.
     */
    public void handleClearAllBookmarks() {
        PDFDocument doc = documentSupplier != null ? documentSupplier.get() : null;
        if (doc == null) {
            uiStateManager.showError(lang().getString("error.noDocument"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        if (bookmarkManager == null || bookmarkManager.getBookmarks().isEmpty()) {
            CustomInfoDialog.show(
                lang().getString("bookmark.title"),
                lang().getString("bookmark.empty"),
                lang().getString("bookmark.emptyMsg"),
                themeManager
            );
            return;
        }

        int count = bookmarkManager.getBookmarks().size();
        boolean confirm = CustomConfirmDialog.show(
            lang().getString("menu.view.clearBookmarks"),
            lang().getString("confirm.clear"),
            lang().getString("confirm.clear"),
            themeManager
        );

        if (confirm) {
            bookmarkManager.clearAllBookmarks();
            uiStateManager.updateStatus(lang().getString("message.bookmarkRemoved"));
        }
    }

    /**
     * Imports bookmarks from PDF outline (Table of Contents).
     */
    public void handleImportOutlineBookmarks() {
        PDFDocument doc = documentSupplier != null ? documentSupplier.get() : null;
        if (doc == null) {
            uiStateManager.showError(lang().getString("error.noDocument"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        if (pdfOutlineBookmarkManager == null) {
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.bookmark"));
            return;
        }

        if (pdfOutlineBookmarkManager.hasOutline(doc)) {
            CustomInfoDialog.show(
                lang().getString("menu.view.importOutline"), 
                lang().getString("bookmark.empty"),
                lang().getString("bookmark.emptyMsg"),
                themeManager
            );
            return;
        }

        int count = pdfOutlineBookmarkManager.getOutlineItemCount(doc);
        boolean confirm = CustomConfirmDialog.show(
            lang().getString("menu.view.importOutline"),
            lang().getString("menu.view.importOutline"),
            lang().getString("menu.view.importOutline") + " (" + count + ")",
            themeManager
        );

        if (confirm) {
            int imported = pdfOutlineBookmarkManager.importFromPDFOutline(doc);
            uiStateManager.updateStatus(lang().getString("success.bookmarkAdded") + " (" + imported + ")");
        }
    }

    /**
     * Analyzes document and creates smart bookmarks based on headings/chapters.
     */
    public void handleSmartBookmarks() {
        PDFDocument doc = documentSupplier != null ? documentSupplier.get() : null;
        if (doc == null) {
            uiStateManager.showError(lang().getString("error.noDocument"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        if (smartBookmarkManager == null) {
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.bookmark"));
            return;
        }

        // Check privacy consent first (AI will be used to improve bookmark titles)
        AIConfig config = AIConfig.getInstance();
        if (!config.isPrivacyConsented()) {
            boolean accepted = PrivacyConsentDialog.show(themeManager);
            if (!accepted) {
                CustomInfoDialog.show(
                    lang().getString("privacy.title"),
                    lang().getString("privacy.header"),
                    lang().getString("privacy.description"),
                    themeManager
                );
                return;
            }
            config.setPrivacyConsented(true);
            config.save();
        }

        boolean confirm = CustomConfirmDialog.show(
            lang().getString("smartBookmark.confirmTitle"),
            lang().getString("smartBookmark.confirmHeader"),
            lang().getString("smartBookmark.confirmMessage"),
            themeManager
        );

        if (confirm) {
            uiStateManager.updateStatus(lang().getString("status.processing"));
            
            new Thread(() -> {
                try {
                    int created = smartBookmarkManager.analyzeAndCreateBookmarks(doc);
                    
                    Platform.runLater(() -> {
                        if (created > 0) {
                            CustomInfoDialog.show(
                                lang().getString("menu.view.smartBookmarks"),
                                lang().getString("status.complete"),
                                lang().getString("success.bookmarkAdded") + " (" + created + ")",
                                themeManager
                            );
                            uiStateManager.updateStatus(lang().getString("success.bookmarkAdded") + " (" + created + ")");
                        } else {
                            CustomInfoDialog.show(
                                lang().getString("menu.view.smartBookmarks"),
                                lang().getString("bookmark.empty"),
                                lang().getString("bookmark.emptyMsg"),
                                themeManager
                            );
                            uiStateManager.updateStatus(lang().getString("bookmark.empty"));
                        }
                    });
                    
                } catch (Exception e) {
                    logger.error("Error creating smart bookmarks", e);
                    Platform.runLater(() -> uiStateManager.showError(lang().getString("error.title"),
                        lang().getString("error.bookmark") + ": " + e.getMessage()));
                }
            }, "SmartBookmarkAnalyzer").start();
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
     * Navigates to a bookmarked page with Y position.
     */
    private void navigateToBookmarkedPage(int pageNumber, float yPosition) {
        if (navigationHelper != null) {
            navigationHelper.navigateToPageWithOffset(pageNumber, yPosition);
            uiStateManager.updateStatus(lang().getString("bookmark.goTo") + " " + (pageNumber + 1));
        }
    }

    /**
     * Checks if the bookmark sidebar is currently visible.
     */
    public boolean isBookmarkSidebarVisible() {
        return bookmarkSidebarVisible;
    }
}
