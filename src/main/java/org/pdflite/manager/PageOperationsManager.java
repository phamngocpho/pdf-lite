package org.pdflite.manager;

import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.pdflite.controller.InsertDialogController;
import org.pdflite.controller.PageRenderer;
import org.pdflite.controller.ScrollHandler;
import org.pdflite.dialog.DuplicatePageDialog;
import org.pdflite.model.DocumentContext;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Manages page operations such as duplication, deletion, insertion, and reordering.
 */
public class PageOperationsManager {

    private static final Logger logger = LoggerFactory.getLogger(PageOperationsManager.class);

    private final UIStateManager uiStateManager;
    private final PageDuplicationManager pageDuplicationManager;
    private final RenderingManager renderingManager;
    private final PageInfoManager pageInfoManager;
    private final SaveStatusManager saveStatusManager;
    private final PageRenderer pageRenderer;
    private final ThemeManager themeManager;
    private final DocumentOperationManager documentOperationManager;
    private final DialogManager dialogManager;
    private final ScrollHandler scrollHandler;
    private final Set<Integer> loadingPages;
    
    // Suppliers for dynamic context
    private Supplier<PDFDocument> currentDocumentSupplier;
    private Supplier<VBox> currentPagesContainerSupplier;
    private Supplier<ScrollPane> currentScrollPaneSupplier;
    private Supplier<DocumentContext> currentContextSupplier;

    public PageOperationsManager(UIStateManager uiStateManager,
                                 PageDuplicationManager pageDuplicationManager,
                                 RenderingManager renderingManager,
                                 PageInfoManager pageInfoManager,
                                 SaveStatusManager saveStatusManager,
                                 PageRenderer pageRenderer,
                                 ThemeManager themeManager,
                                 DocumentOperationManager documentOperationManager,
                                 DialogManager dialogManager,
                                 ScrollHandler scrollHandler,
                                 Set<Integer> loadingPages) {
        this.uiStateManager = uiStateManager;
        this.pageDuplicationManager = pageDuplicationManager;
        this.renderingManager = renderingManager;
        this.pageInfoManager = pageInfoManager;
        this.saveStatusManager = saveStatusManager;
        this.pageRenderer = pageRenderer;
        this.themeManager = themeManager;
        this.documentOperationManager = documentOperationManager;
        this.dialogManager = dialogManager;
        this.scrollHandler = scrollHandler;
        this.loadingPages = loadingPages;
    }
    
    /**
     * Sets suppliers for dynamic context retrieval.
     */
    public void setContextSuppliers(Supplier<PDFDocument> currentDocumentSupplier,
                                    Supplier<VBox> currentPagesContainerSupplier,
                                    Supplier<ScrollPane> currentScrollPaneSupplier,
                                    Supplier<DocumentContext> currentContextSupplier) {
        this.currentDocumentSupplier = currentDocumentSupplier;
        this.currentPagesContainerSupplier = currentPagesContainerSupplier;
        this.currentScrollPaneSupplier = currentScrollPaneSupplier;
        this.currentContextSupplier = currentContextSupplier;
    }

    /**
     * Handles page duplication operation.
     *
     * @param document The current PDF document
     */
    public void handleDuplicatePage(PDFDocument document) {
        if (document == null) {
            uiStateManager.showError("No Document", "Please open a PDF file first.");
            return;
        }

        try {
            int currentPage = document.getCurrentPage();
            int totalPages = document.getTotalPages();

            // Show duplicate page dialog
            DuplicatePageDialog dialog = new DuplicatePageDialog(currentPage, totalPages, themeManager);

            if (dialog.showAndWait()) {
                int sourcePageIndex = dialog.getSourcePageIndex();
                int insertPosition = dialog.getInsertPosition();
                int numberOfCopies = dialog.getNumberOfCopies();

                // Duplicate the page
                if (pageDuplicationManager.duplicatePage(document, sourcePageIndex,
                        insertPosition, numberOfCopies)) {
                    // Clear caches and re-render
                    document.clearCache();
                    pageRenderer.clearCache();
                    renderingManager.renderAllPages();

                    // Update page info
                    pageInfoManager.updatePageInfo(document);

                    uiStateManager.updateStatus(
                            String.format("Duplicated page %d (%d copies) - Don't forget to save!",
                                    sourcePageIndex + 1, numberOfCopies));
                    logger.info("Page {} duplicated {} times at position {}",
                            sourcePageIndex + 1, numberOfCopies, insertPosition + 1);

                    // Trigger auto-save
                    if (saveStatusManager != null) {
                        saveStatusManager.triggerAutoSave();
                    }
                } else {
                    uiStateManager.showError("Duplication Failed",
                            "Failed to duplicate the page.");
                }
            }
        } catch (Exception e) {
            logger.error("Error duplicating page", e);
            uiStateManager.showError("Error", "Failed to duplicate page: " + e.getMessage());
        }
    }
    
    /**
     * Handles page insertion operation.
     */
    public void handleInsertPage() {
        PDFDocument currentDocument = currentDocumentSupplier != null ? currentDocumentSupplier.get() : null;
        VBox pagesContainer = currentPagesContainerSupplier != null ? currentPagesContainerSupplier.get() : null;
        ScrollPane scrollPane = currentScrollPaneSupplier != null ? currentScrollPaneSupplier.get() : null;
        
        if (currentDocument == null) {
            uiStateManager.showError("No PDF", "Please open a PDF file first.");
            return;
        }

        InsertDialogController controller = dialogManager.openInsertDialog(currentDocument);
        if (controller == null || controller.isInsertClicked()) {
            return;
        }

        AtomicReference<VBox> pagesContainerRef = new AtomicReference<>(pagesContainer);

        PDFDocument updatedDocument = documentOperationManager.insertBlankPages(
                currentDocument, controller, pagesContainerRef, loadingPages,
                pageRenderer, scrollHandler, scrollPane);

        if (updatedDocument != null) {
            VBox updatedContainer = pagesContainerRef.get();
            // Update context with a new container
            DocumentContext context = currentContextSupplier != null ? currentContextSupplier.get() : null;
            if (context != null && updatedContainer != null) {
                // Recreate annotation manager with updated container
                AnnotationManager newAnnotationManager = new AnnotationManager(
                    updatedContainer, uiStateManager, currentDocument);
                context.setAnnotationManager(newAnnotationManager);
                
                logger.info("Page inserted and annotation manager updated");
            }
        }
    }

    /**
     * Handles page reordering operation.
     */
    public void handleReorderPages() {
        PDFDocument currentDocument = currentDocumentSupplier != null ? currentDocumentSupplier.get() : null;
        
        if (currentDocument == null) {
            uiStateManager.showError("No PDF", "Please open a PDF file first.");
            return;
        }
        
        dialogManager.openPageReorderDialog(currentDocument, () -> Platform.runLater(() -> {
            // Clear all caches to force re-render with a new page order
            currentDocument.clearCache();
            pageRenderer.clearCache();

            // Re-render all pages
            renderingManager.renderAllPages();

            // Update status
            uiStateManager.updateStatus("Pages reordered - Don't forget to save!");

            // Trigger auto-save
            if (saveStatusManager != null) {
                saveStatusManager.triggerAutoSave();
            }

            logger.info("View refreshed after page reorder");
        }));
    }
}