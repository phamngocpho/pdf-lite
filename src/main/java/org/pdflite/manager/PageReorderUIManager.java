package org.pdflite.manager;

import javafx.application.Platform;
import org.pdflite.controller.PageRenderer;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Manager for page reorder UI operations.
 */
public class PageReorderUIManager {

    private static final Logger logger = LoggerFactory.getLogger(PageReorderUIManager.class);

    private final DialogManager dialogManager;
    private final UIStateManager uiStateManager;
    private final PageRenderer pageRenderer;
    private final SaveStatusManager saveStatusManager;

    private Supplier<PDFDocument> documentSupplier;
    private Supplier<RenderingManager> renderingManagerSupplier;

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    public PageReorderUIManager(DialogManager dialogManager,
                                UIStateManager uiStateManager,
                                PageRenderer pageRenderer,
                                SaveStatusManager saveStatusManager) {
        this.dialogManager = dialogManager;
        this.uiStateManager = uiStateManager;
        this.pageRenderer = pageRenderer;
        this.saveStatusManager = saveStatusManager;
    }

    public void setDocumentSupplier(Supplier<PDFDocument> documentSupplier) {
        this.documentSupplier = documentSupplier;
    }

    public void setRenderingManagerSupplier(Supplier<RenderingManager> renderingManagerSupplier) {
        this.renderingManagerSupplier = renderingManagerSupplier;
    }

    /**
     * Opens the page reorder dialog and handles the reorder operation.
     */
    public void handleReorderPages() {
        PDFDocument currentDocument = documentSupplier != null ? documentSupplier.get() : null;
        RenderingManager currentRenderingManager = renderingManagerSupplier != null ? renderingManagerSupplier.get() : null;

        if (currentDocument == null) {
            uiStateManager.showError(lang().getString("error.noPdfLoaded"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        dialogManager.openPageReorderDialog(currentDocument, () -> {
            Platform.runLater(() -> {
                // Clear all caches to force re-render with new page order
                currentDocument.clearCache();
                pageRenderer.clearCache();

                // Re-render all pages
                if (currentRenderingManager != null) {
                    currentRenderingManager.renderAllPages();
                }

                uiStateManager.updateStatus(lang().getString("status.complete"));

                if (saveStatusManager != null) {
                    saveStatusManager.triggerAutoSave();
                }

                logger.info("View refreshed after page reorder");
            });
        });
    }
}
