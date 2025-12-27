package org.pdflite.manager;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.pdflite.controller.InsertDialogController;
import org.pdflite.controller.PageRenderer;
import org.pdflite.controller.ScrollHandler;
import org.pdflite.model.DocumentContext;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Manager for page insertion operations.
 */
public class PageInsertManager {

    private static final Logger logger = LoggerFactory.getLogger(PageInsertManager.class);

    private final UIStateManager uiStateManager;
    private final DialogManager dialogManager;
    private final DocumentOperationManager documentOperationManager;
    private final PageRenderer pageRenderer;
    private final ScrollHandler scrollHandler;
    private final Set<Integer> loadingPages;

    private Supplier<DocumentContext> contextSupplier;
    private Supplier<PDFDocument> documentSupplier;
    private Supplier<VBox> pagesContainerSupplier;
    private Supplier<ScrollPane> scrollPaneSupplier;
    private Supplier<RenderingManager> renderingManagerSupplier;

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    public PageInsertManager(UIStateManager uiStateManager,
                             DialogManager dialogManager,
                             DocumentOperationManager documentOperationManager,
                             PageRenderer pageRenderer,
                             ScrollHandler scrollHandler,
                             Set<Integer> loadingPages) {
        this.uiStateManager = uiStateManager;
        this.dialogManager = dialogManager;
        this.documentOperationManager = documentOperationManager;
        this.pageRenderer = pageRenderer;
        this.scrollHandler = scrollHandler;
        this.loadingPages = loadingPages;
    }

    public void setContextSupplier(Supplier<DocumentContext> contextSupplier) {
        this.contextSupplier = contextSupplier;
    }

    public void setDocumentSupplier(Supplier<PDFDocument> documentSupplier) {
        this.documentSupplier = documentSupplier;
    }

    public void setPagesContainerSupplier(Supplier<VBox> pagesContainerSupplier) {
        this.pagesContainerSupplier = pagesContainerSupplier;
    }

    public void setScrollPaneSupplier(Supplier<ScrollPane> scrollPaneSupplier) {
        this.scrollPaneSupplier = scrollPaneSupplier;
    }

    public void setRenderingManagerSupplier(Supplier<RenderingManager> renderingManagerSupplier) {
        this.renderingManagerSupplier = renderingManagerSupplier;
    }

    /**
     * Handles inserting blank pages into the document.
     */
    public void handleInsertPage() {
        PDFDocument currentDocument = documentSupplier != null ? documentSupplier.get() : null;
        VBox pagesContainer = pagesContainerSupplier != null ? pagesContainerSupplier.get() : null;
        ScrollPane scrollPane = scrollPaneSupplier != null ? scrollPaneSupplier.get() : null;
        RenderingManager currentRenderingManager = renderingManagerSupplier != null ? renderingManagerSupplier.get() : null;

        if (currentDocument == null) {
            uiStateManager.showError(lang().getString("error.noPdfLoaded"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        InsertDialogController controller = dialogManager.openInsertDialog(currentDocument);
        if (controller == null || controller.isInsertClicked()) {
            return;
        }

        AtomicReference<VBox> pagesContainerRef = new AtomicReference<>(pagesContainer);

        PDFDocument updatedDocument = documentOperationManager.insertBlankPages(
                currentDocument, controller, pagesContainerRef, loadingPages,
                pageRenderer, scrollHandler, scrollPane, currentRenderingManager);

        if (updatedDocument != null) {
            VBox updatedContainer = pagesContainerRef.get();
            DocumentContext context = contextSupplier != null ? contextSupplier.get() : null;
            if (context != null && updatedContainer != null) {
                AnnotationManager newAnnotationManager = new AnnotationManager(
                        updatedContainer, uiStateManager, currentDocument);
                context.setAnnotationManager(newAnnotationManager);
            }
            logger.info("Pages inserted successfully");
        }
    }
}
