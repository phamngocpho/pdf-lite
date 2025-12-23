package org.pdflite.view;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import org.pdflite.config.AIConfig;
import org.pdflite.dialog.PrivacyConsentDialog;
import org.pdflite.manager.ThemeManager;
import org.pdflite.model.AICommand;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.AICommandExecutor;
import org.pdflite.service.AICommandParser;
import org.pdflite.service.GroqService;
import org.pdflite.service.PDFService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Chat sidebar panel for AI-powered PDF operations.
 * Displays as a side panel instead of a dialog.
 */
public class ChatSidebarPanel extends VBox {
    private static final Logger logger = LoggerFactory.getLogger(ChatSidebarPanel.class);

    private final VBox chatContainer;
    private final ScrollPane scrollPane;
    private TextField inputField;
    private Button sendButton;
    private final GroqService groqService;
    private final AICommandParser commandParser;
    private final AICommandExecutor commandExecutor;
    private final Supplier<PDFDocument> documentSupplier;
    private final PDFService pdfService;
    private ThemeManager themeManager;
    
    // Chat history for context
    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 10;

    private record ChatMessage(String role, String content) {}

    public ChatSidebarPanel(GroqService groqService, AICommandExecutor commandExecutor, 
                           Supplier<PDFDocument> documentSupplier) {
        this.groqService = groqService;
        this.commandParser = new AICommandParser();
        this.commandExecutor = commandExecutor;
        this.documentSupplier = documentSupplier;
        this.pdfService = new PDFService();

        setSpacing(0);
        setPrefWidth(320);
        setMinWidth(280);
        setMaxWidth(400);
        getStyleClass().add("chat-sidebar");

        // Header
        HBox header = createHeader();

        // Chat area
        chatContainer = new VBox(10);
        chatContainer.setPadding(new Insets(10));
        chatContainer.getStyleClass().add("chat-container");

        scrollPane = new ScrollPane(chatContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("chat-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Input area
        HBox inputArea = createInputArea();

        getChildren().addAll(header, scrollPane, inputArea);

        // Add welcome message
        addWelcomeMessage();
    }

    private HBox createHeader() {
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 15, 12, 15));
        header.getStyleClass().add("chat-header");

        Label titleLabel = new Label("AI Assistant");
        titleLabel.getStyleClass().add("chat-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button helpButton = new Button("?");
        helpButton.getStyleClass().add("chat-help-button");
        helpButton.setOnAction(e -> sendMessage("help"));

        header.getChildren().addAll(titleLabel, spacer, helpButton);
        return header;
    }

    private HBox createInputArea() {
        HBox inputArea = new HBox(8);
        inputArea.setAlignment(Pos.CENTER);
        inputArea.setPadding(new Insets(10, 12, 12, 12));
        inputArea.getStyleClass().add("chat-input-area");

        inputField = new TextField();
        inputField.setPromptText("Nhập lệnh...");
        inputField.getStyleClass().add("chat-input");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        inputField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !inputField.getText().trim().isEmpty()) {
                sendMessage(inputField.getText().trim());
            }
        });

        sendButton = new Button("➤");
        sendButton.getStyleClass().add("chat-send-button");
        sendButton.setOnAction(e -> {
            if (!inputField.getText().trim().isEmpty()) {
                sendMessage(inputField.getText().trim());
            }
        });

        inputArea.getChildren().addAll(inputField, sendButton);
        return inputArea;
    }

    private void addWelcomeMessage() {
        String welcome = "Xin chào! Tôi có thể giúp bạn:\n\n" +
                "- Tách/trích xuất trang\n" +
                "- Xóa trang\n" +
                "- Sắp xếp lại trang\n" +
                "- Thêm bookmark\n" +
                "- Đọc/tóm tắt nội dung\n" +
                "- Di chuyển đến trang\n\n" +
                "Nhập lệnh bằng ngôn ngữ tự nhiên!";
        addMessage(welcome, false);
    }

    private void sendMessage(String message) {
        // Check privacy consent first
        AIConfig config = AIConfig.getInstance();
        if (!config.isPrivacyConsented()) {
            boolean accepted = PrivacyConsentDialog.show(themeManager);
            if (!accepted) {
                addMessage("Bạn cần đồng ý với chính sách bảo mật để sử dụng tính năng AI.", false);
                return;
            }
            config.setPrivacyConsented(true);
            config.save();
        }

        // Add user message
        addMessage(message, true);
        inputField.clear();
        inputField.setDisable(true);
        sendButton.setDisable(true);

        // Add loading indicator
        HBox loadingBox = createLoadingIndicator();
        chatContainer.getChildren().add(loadingBox);
        scrollToBottom();

        // Build context with document info and chat history
        String contextMessage = buildContextMessage(message);

        // Send to AI with context (use fast model for command parsing)
        groqService.chat(contextMessage, chatHistory, true)
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        // Parse command
                        AICommand command = commandParser.parse(response);
                        logger.info("Parsed command: {}", command);

                        // Add to history
                        addToHistory("user", message);
                        addToHistory("assistant", response);

                        // Check if this is a summarize request that needs PDF content
                        if (command.getAction() == AICommand.Action.SUMMARIZE) {
                            // Update loading message
                            ((Label) loadingBox.getChildren().get(0)).setText("Đang tóm tắt nội dung...");
                            handleSummarize(command, message, loadingBox);
                        } else {
                            chatContainer.getChildren().remove(loadingBox);
                            // Execute command and show result
                            commandExecutor.execute(command)
                                    .thenAccept(result -> {
                                        Platform.runLater(() -> {
                                            addMessage(result, false);
                                            inputField.setDisable(false);
                                            sendButton.setDisable(false);
                                            inputField.requestFocus();
                                        });
                                    });
                        }
                    });
                })
                .exceptionally(e -> {
                    Platform.runLater(() -> {
                        chatContainer.getChildren().remove(loadingBox);
                        addMessage("Lỗi: " + e.getMessage(), false);
                        inputField.setDisable(false);
                        sendButton.setDisable(false);
                    });
                    return null;
                });
    }

    private void handleSummarize(AICommand command, String originalMessage, HBox loadingBox) {
        PDFDocument doc = documentSupplier.get();
        if (doc == null) {
            chatContainer.getChildren().remove(loadingBox);
            addMessage("Vui lòng mở một file PDF trước.", false);
            inputField.setDisable(false);
            sendButton.setDisable(false);
            return;
        }

        // Get pages to summarize
        List<Integer> pages = command.getPages();
        if (pages.isEmpty()) {
            pages = List.of(doc.getCurrentPage() + 1);
        }

        // Extract text from PDF
        StringBuilder textContent = new StringBuilder();
        int totalPages = doc.getTotalPages();
        
        try {
            for (int page : pages) {
                if (page < 1 || page > totalPages) continue;
                String text = pdfService.extractTextFromPage(doc, page - 1);
                logger.info("Page {} text length: {}", page, text != null ? text.trim().length() : 0);
                if (text != null && !text.trim().isEmpty()) {
                    textContent.append(text.trim()).append("\n\n");
                }
            }
        } catch (Exception e) {
            logger.error("Error extracting text", e);
            chatContainer.getChildren().remove(loadingBox);
            addMessage("Lỗi khi đọc nội dung: " + e.getMessage(), false);
            inputField.setDisable(false);
            sendButton.setDisable(false);
            return;
        }

        if (textContent.isEmpty()) {
            chatContainer.getChildren().remove(loadingBox);
            addMessage("Không tìm thấy text trong trang " + pages + ". Trang có thể chỉ chứa hình ảnh.", false);
            inputField.setDisable(false);
            sendButton.setDisable(false);
            return;
        }

        logger.info("Total extracted text length: {}", textContent.length());

        // Limit text for API
        String content = textContent.toString();
        if (content.length() > 3000) {
            content = content.substring(0, 3000) + "...";
        }

        // Send to AI with PDF content for summarization
        String summarizeRequest = "[PDF Content]\n" + content + "\n\n[User request]\n" + originalMessage;
        
        logger.info("Sending summarize request to AI...");
        long startTime = System.currentTimeMillis();
        
        groqService.chat(summarizeRequest, chatHistory)
                .thenAccept(response -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    logger.info("AI summarize response received in {}ms", elapsed);
                    Platform.runLater(() -> {
                        chatContainer.getChildren().remove(loadingBox);
                        AICommand summaryCommand = commandParser.parse(response);
                        addMessage(summaryCommand.getMessage(), false);
                        inputField.setDisable(false);
                        sendButton.setDisable(false);
                        inputField.requestFocus();
                    });
                })
                .exceptionally(e -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    logger.error("AI summarize failed after {}ms", elapsed, e);
                    Platform.runLater(() -> {
                        chatContainer.getChildren().remove(loadingBox);
                        addMessage("Lỗi khi tóm tắt: " + e.getMessage(), false);
                        inputField.setDisable(false);
                        sendButton.setDisable(false);
                    });
                    return null;
                });
    }

    private String buildContextMessage(String userMessage) {
        PDFDocument doc = documentSupplier.get();
        StringBuilder context = new StringBuilder();
        
        if (doc != null) {
            context.append("[Document Context]\n");
            context.append("- File: ").append(doc.getFile() != null ? doc.getFile().getName() : "Untitled").append("\n");
            context.append("- Total pages: ").append(doc.getTotalPages()).append("\n");
            context.append("- Current page: ").append(doc.getCurrentPage() + 1).append("\n\n");
        } else {
            context.append("[No document open]\n\n");
        }
        
        context.append("[User request]\n");
        context.append(userMessage);
        
        return context.toString();
    }

    private void addToHistory(String role, String content) {
        chatHistory.add(new ChatMessage(role, content));
        // Keep only last N messages
        while (chatHistory.size() > MAX_HISTORY * 2) {
            chatHistory.remove(0);
        }
    }

    private HBox createLoadingIndicator() {
        HBox wrapper = new HBox();
        wrapper.setAlignment(Pos.CENTER_LEFT);
        
        Label loadingLabel = new Label("Đang xử lý...");
        loadingLabel.getStyleClass().add("chat-loading");
        
        wrapper.getChildren().add(loadingLabel);
        return wrapper;
    }

    private void addMessage(String message, boolean isUser) {
        VBox messageBox = new VBox(3);
        messageBox.getStyleClass().add(isUser ? "chat-message-user" : "chat-message-bot");
        messageBox.setMaxWidth(260);

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.getStyleClass().add("chat-message-text");

        messageBox.getChildren().add(messageLabel);

        HBox wrapper = new HBox();
        wrapper.setAlignment(isUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        wrapper.getChildren().add(messageBox);

        chatContainer.getChildren().add(wrapper);
        scrollToBottom();
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    public void clearChat() {
        chatContainer.getChildren().clear();
        chatHistory.clear();
        addWelcomeMessage();
    }

    public void focusInput() {
        Platform.runLater(() -> inputField.requestFocus());
    }

    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }
}
