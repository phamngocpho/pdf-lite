package org.pdflite.manager;

import javafx.scene.layout.BorderPane;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.AICommandExecutor;
import org.pdflite.service.GroqService;
import org.pdflite.view.ChatSidebarPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Manages the AI Chat sidebar panel visibility and state.
 */
public class ChatUIManager {
    private static final Logger logger = LoggerFactory.getLogger(ChatUIManager.class);

    private final BorderPane rootPane;
    private final GroqService groqService;
    private final AICommandExecutor commandExecutor;
    private final Supplier<PDFDocument> documentSupplier;
    
    private ChatSidebarPanel chatPanel;
    private boolean isVisible = false;

    public ChatUIManager(BorderPane rootPane, GroqService groqService, AICommandExecutor commandExecutor,
                        Supplier<PDFDocument> documentSupplier) {
        this.rootPane = rootPane;
        this.groqService = groqService;
        this.commandExecutor = commandExecutor;
        this.documentSupplier = documentSupplier;
    }

    /**
     * Toggles the chat sidebar visibility.
     */
    public void toggleChat() {
        if (isVisible) {
            hideChat();
        } else {
            showChat();
        }
    }

    /**
     * Shows the chat sidebar on the right side.
     */
    public void showChat() {
        if (chatPanel == null) {
            chatPanel = new ChatSidebarPanel(groqService, commandExecutor, documentSupplier);
        }

        // Remove any existing right panel first
        if (rootPane.getRight() != null && rootPane.getRight() != chatPanel) {
            rootPane.setRight(null);
        }

        rootPane.setRight(chatPanel);
        isVisible = true;
        chatPanel.focusInput();
        logger.info("Chat sidebar shown");
    }

    /**
     * Hides the chat sidebar.
     */
    public void hideChat() {
        if (rootPane.getRight() == chatPanel) {
            rootPane.setRight(null);
        }
        isVisible = false;
        logger.info("Chat sidebar hidden");
    }

    /**
     * Checks if chat sidebar is currently visible.
     */
    public boolean isVisible() {
        return isVisible;
    }

    /**
     * Clears the chat history.
     */
    public void clearChat() {
        if (chatPanel != null) {
            chatPanel.clearChat();
        }
    }
}
