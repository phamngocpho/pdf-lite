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
     * Clears all bookmarks for the current document.
     */
    public void handleClearAllBookmarks() {
        PDFDocument doc = documentSupplier != null ? documentSupplier.get() : null;
        if (doc == null) {
            uiStateManager.showError("No Document", "Please open a PDF document first.");
            return;
        }

        if (bookmarkManager == null || bookmarkManager.getBookmarks().isEmpty()) {
            CustomInfoDialog.show(
                "No Bookmarks",
                "No Bookmarks to Clear",
                "There are no bookmarks for this document.",
                themeManager
            );
            return;
        }

        int count = bookmarkManager.getBookmarks().size();
        boolean confirm = CustomConfirmDialog.show(
            "Clear All Bookmarks",
            "Delete All Bookmarks?",
            String.format("This will permanently delete all %d bookmarks for this document.\n\n" +
                         "This action cannot be undone.", count),
            themeManager
        );

        if (confirm) {
            bookmarkManager.clearAllBookmarks();
            uiStateManager.updateStatus(String.format("Cleared %d bookmarks", count));
        }
    }

    /**
     * Imports bookmarks from PDF outline (Table of Contents).
     */
    public void handleImportOutlineBookmarks() {
        PDFDocument doc = documentSupplier != null ? documentSupplier.get() : null;
        if (doc == null) {
            uiStateManager.showError("No Document", "Please open a PDF document first.");
            return;
        }

        if (pdfOutlineBookmarkManager == null) {
            uiStateManager.showError("Error", "Outline bookmark manager not initialized.");
            return;
        }

        if (pdfOutlineBookmarkManager.hasOutline(doc)) {
            CustomInfoDialog.show(
                "No Outline", 
                "No Table of Contents",
                "This PDF does not have a table of contents (outline).",
                themeManager
            );
            return;
        }

        int count = pdfOutlineBookmarkManager.getOutlineItemCount(doc);
        boolean confirm = CustomConfirmDialog.show(
            "Import Bookmarks",
            "Import from PDF Outline",
            String.format("Import %d bookmarks from PDF outline?", count),
            themeManager
        );

        if (confirm) {
            int imported = pdfOutlineBookmarkManager.importFromPDFOutline(doc);
            uiStateManager.updateStatus(
                String.format("Imported %d bookmarks from PDF outline", imported)
            );
        }
    }

    /**
     * Analyzes document and creates smart bookmarks based on headings/chapters.
     */
    public void handleSmartBookmarks() {
        PDFDocument doc = documentSupplier != null ? documentSupplier.get() : null;
        if (doc == null) {
            uiStateManager.showError("No Document", "Please open a PDF document first.");
            return;
        }

        if (smartBookmarkManager == null) {
            uiStateManager.showError("Error", "Smart bookmark manager not initialized.");
            return;
        }

        // Check privacy consent first (AI will be used to improve bookmark titles)
        AIConfig config = AIConfig.getInstance();
        if (!config.isPrivacyConsented()) {
            boolean accepted = PrivacyConsentDialog.show(themeManager);
            if (!accepted) {
                CustomInfoDialog.show(
                    "Privacy Required",
                    "Privacy Consent Required",
                    "You need to accept the privacy policy to use AI-powered Smart Bookmarks.\n\n" +
                    "This feature sends detected heading titles to Groq AI to improve and clean up bookmark names.",
                    themeManager
                );
                return;
            }
            config.setPrivacyConsented(true);
            config.save();
        }

        boolean confirm = CustomConfirmDialog.show(
            "Smart Bookmarks",
            "Analyze Document Structure",
            "Analyze this PDF and automatically create bookmarks for detected chapters and headings?\n\n" +
            "This will detect:\n" +
            "• Chapter titles (Chapter 1, Chương 1, etc.)\n" +
            "• Large headings (font size ≥ 14pt)\n" +
            "• Bold section titles\n\n" +
            "AI will improve and clean up the detected titles.\n" +
            "Note: Heading titles will be sent to Groq AI for processing.",
            themeManager
        );

        if (confirm) {
            uiStateManager.updateStatus("Analyzing document structure with AI...");
            
            new Thread(() -> {
                try {
                    int created = smartBookmarkManager.analyzeAndCreateBookmarks(doc);
                    
                    Platform.runLater(() -> {
                        if (created > 0) {
                            CustomInfoDialog.show(
                                "Smart Bookmarks",
                                "Analysis Complete",
                                String.format("Created %d smart bookmarks based on document structure.", created),
                                themeManager
                            );
                            uiStateManager.updateStatus(
                                String.format("Created %d smart bookmarks", created)
                            );
                        } else {
                            CustomInfoDialog.show(
                                "Smart Bookmarks",
                                "No Headings Found",
                                "Could not detect any chapter titles or headings in this document.\n\n" +
                                "Try using 'Import from PDF Outline' if the PDF has a table of contents.",
                                themeManager
                            );
                            uiStateManager.updateStatus("No headings detected");
                        }
                    });
                    
                } catch (Exception e) {
                    logger.error("Error creating smart bookmarks", e);
                    Platform.runLater(() -> {
                        uiStateManager.showError("Error", 
                            "Failed to analyze document: " + e.getMessage());
                    });
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
