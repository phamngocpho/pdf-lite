package org.pdflite.manager;

import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.pdflite.controller.PageRenderer;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.AICommandExecutor;
import org.pdflite.service.GroqService;
import org.pdflite.util.NavigationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Manager for AI Chat operations.
 */
public class AIChatManager {

    private static final Logger logger = LoggerFactory.getLogger(AIChatManager.class);

    private final BorderPane rootPane;
    private final UIStateManager uiStateManager;
    private final PageInfoManager pageInfoManager;
    private final NavigationHelper navigationHelper;
    private final PageRenderer pageRenderer;

    private GroqService groqService;
    private ChatUIManager chatUIManager;
    private ThemeManager themeManager;
    private BookmarkManager bookmarkManager;

    private Supplier<PDFDocument> documentSupplier;
    private Supplier<RenderingManager> renderingManagerSupplier;
    private Supplier<Stage> stageSupplier;

    public AIChatManager(BorderPane rootPane,
                         UIStateManager uiStateManager,
                         PageInfoManager pageInfoManager,
                         NavigationHelper navigationHelper,
                         PageRenderer pageRenderer) {
        this.rootPane = rootPane;
        this.uiStateManager = uiStateManager;
        this.pageInfoManager = pageInfoManager;
        this.navigationHelper = navigationHelper;
        this.pageRenderer = pageRenderer;
    }

    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
        if (chatUIManager != null) {
            chatUIManager.setThemeManager(themeManager);
        }
    }

    public void setBookmarkManager(BookmarkManager bookmarkManager) {
        this.bookmarkManager = bookmarkManager;
    }

    public void setDocumentSupplier(Supplier<PDFDocument> documentSupplier) {
        this.documentSupplier = documentSupplier;
    }

    public void setRenderingManagerSupplier(Supplier<RenderingManager> renderingManagerSupplier) {
        this.renderingManagerSupplier = renderingManagerSupplier;
    }

    public void setStageSupplier(Supplier<Stage> stageSupplier) {
        this.stageSupplier = stageSupplier;
    }

    public void setGroqService(GroqService groqService) {
        this.groqService = groqService;
    }

    /**
     * Toggles the AI Chat sidebar.
     */
    public void handleOpenChat() {
        if (chatUIManager == null) {
            initializeChatUIManager();
        }

        if (chatUIManager != null) {
            chatUIManager.toggleChat();
            logger.debug("AI Chat toggled");
        }
    }

    private void initializeChatUIManager() {
        if (groqService == null) {
            groqService = new GroqService();
        }

        AICommandExecutor commandExecutor = new AICommandExecutor(
                documentSupplier,
                () -> bookmarkManager,
                pageIndex -> navigationHelper.navigateToPage(pageIndex),
                status -> uiStateManager.updateStatus(status),
                () -> {
                    PDFDocument doc = documentSupplier != null ? documentSupplier.get() : null;
                    if (doc != null) {
                        doc.clearCache();
                        pageRenderer.clearCache();
                        RenderingManager rm = renderingManagerSupplier != null ? renderingManagerSupplier.get() : null;
                        if (rm != null) {
                            rm.renderAllPages();
                        }
                        pageInfoManager.updatePageInfo(doc);
                    }
                },
                stageSupplier
        );

        chatUIManager = new ChatUIManager(rootPane, groqService, commandExecutor, documentSupplier);
        chatUIManager.setThemeManager(themeManager);
        logger.info("AI Chat UI Manager initialized");
    }

    public ChatUIManager getChatUIManager() {
        return chatUIManager;
    }

    public GroqService getGroqService() {
        return groqService;
    }
}
