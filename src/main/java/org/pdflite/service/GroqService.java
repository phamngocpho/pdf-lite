package org.pdflite.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.pdflite.config.AIConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for interacting with Groq AI API.
 */
public class GroqService {
    private static final Logger logger = LoggerFactory.getLogger(GroqService.class);
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private final HttpClient httpClient;
    private final Gson gson;
    private final AIConfig config;

    private static final String SYSTEM_PROMPT = """
        You are a PDF assistant that helps users manipulate PDF documents.
        You MUST respond with a valid JSON object only, no other text.
        
        Available actions:
        1. split - Extract specific pages to a new file
           Parameters: pages (array of page numbers, 1-based), outputName (optional filename)
        2. extract - Same as split, extract pages
           Parameters: pages (array of page numbers, 1-based), outputName (optional filename)
        3. delete - Delete specific pages from document
           Parameters: pages (array of page numbers, 1-based)
        4. reorder - Reorder ALL pages in document (must specify all pages)
           Parameters: newOrder (array of ALL page numbers in new order, 1-based)
        5. swap - Swap/exchange positions of two pages
           Parameters: page1 (first page, 1-based), page2 (second page, 1-based)
        6. move - Move a page to a new position
           Parameters: fromPage (page to move, 1-based), toPage (destination position, 1-based)
        7. bookmark - Add bookmark to current page
           Parameters: title (bookmark title), page (optional, 1-based, defaults to current)
        8. goto - Navigate to a specific page
           Parameters: page (page number, 1-based)
        9. readtext - Read/extract text content from specific pages (shows raw text)
           Parameters: pages (array of page numbers, 1-based; if empty, reads current page)
        10. summarize - Summarize/analyze text content from specific pages
            Parameters: pages (array of page numbers, 1-based; if empty, uses current page)
            Note: When you receive [PDF Content] in context, write your summary in the "message" field
        11. info - Get document information
            Parameters: none
        12. help - Show available commands
            Parameters: none
        13. unknown - Use this when:
            - The request is NOT related to PDF operations
            - The request is unclear
            Parameters: message (polite explanation)
        
        IMPORTANT RULES:
        - You CAN read text from PDF pages using "readtext" action
        - You CAN summarize/analyze content using "summarize" action
        - When [PDF Content] is provided in context, use it to write summary in "message" field
        - You CANNOT perform OCR on images
        
        Response format (JSON only):
        {
          "action": "action_name",
          "params": { ... },
          "message": "Human readable response"
        }
        
        Examples:
        User: "Tách trang 1 đến 5"
        Response: {"action":"split","params":{"pages":[1,2,3,4,5]},"message":"Đang tách trang 1-5 thành file mới"}
        
        User: "Đọc nội dung trang 1"
        Response: {"action":"readtext","params":{"pages":[1]},"message":"Đang đọc nội dung trang 1"}
        
        User: "Tóm tắt nội dung trang 1" (without PDF content)
        Response: {"action":"summarize","params":{"pages":[1]},"message":"Đang đọc nội dung để tóm tắt..."}
        
        User: "Tóm tắt nội dung" with [PDF Content]: "Lorem ipsum..."
        Response: {"action":"summarize","params":{},"message":"Tóm tắt: Nội dung nói về..."}
        
        User: "Đổi vị trí trang 1 với trang 2"
        Response: {"action":"swap","params":{"page1":1,"page2":2},"message":"Đang hoán đổi trang 1 và trang 2"}
        
        User: "Đánh dấu trang này là Chương 1"
        Response: {"action":"bookmark","params":{"title":"Chương 1"},"message":"Đã thêm bookmark 'Chương 1'"}
        
        IMPORTANT: 
        - For swapping two pages, use "swap" action
        - For moving one page to another position, use "move" action
        - For reading raw text, use "readtext" action
        - For summarizing/analyzing content, use "summarize" action
        - Always respond in the same language as the user's input.
        """;

    public GroqService() {
        this.config = AIConfig.getInstance();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.gson = new Gson();

        if (!config.isConfigured()) {
            logger.warn("Groq API key not configured. Please edit: {}", AIConfig.getConfigFilePath());
        } else {
            logger.info("Groq API configured successfully");
        }
    }

    /**
     * Sends a message to Groq API and returns the response.
     */
    public CompletableFuture<String> chat(String userMessage) {
        return chat(userMessage, List.of());
    }

    /**
     * Sends a message to Groq API with chat history for context.
     */
    public <T> CompletableFuture<String> chat(String userMessage, List<T> history) {
        return chat(userMessage, history, false);
    }

    /**
     * Sends a message to Groq API with option for fast model.
     */
    public <T> CompletableFuture<String> chat(String userMessage, List<T> history, boolean useFastModel) {
        if (!config.isConfigured()) {
            return CompletableFuture.completedFuture(
                    "{\"action\":\"unknown\",\"params\":{},\"message\":\"API key chưa được cấu hình. Vui lòng chỉnh sửa file: " + AIConfig.getConfigFilePath() + "\"}"
            );
        }

        if (!config.isEnabled()) {
            return CompletableFuture.completedFuture(
                    "{\"action\":\"unknown\",\"params\":{},\"message\":\"AI đã bị tắt trong cấu hình.\"}"
            );
        }

        JsonObject requestBody = buildRequestBody(userMessage, history, useFastModel);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getGroqApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .timeout(Duration.ofSeconds(30))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.error("Groq API error: {} - {}", response.statusCode(), response.body());
                        return "{\"action\":\"unknown\",\"params\":{},\"message\":\"Lỗi API: " + response.statusCode() + "\"}";
                    }
                    return extractContent(response.body());
                })
                .exceptionally(e -> {
                    logger.error("Error calling Groq API", e);
                    return "{\"action\":\"unknown\",\"params\":{},\"message\":\"Lỗi kết nối: " + e.getMessage() + "\"}";
                });
    }

    private <T> JsonObject buildRequestBody(String userMessage, List<T> history, boolean useFastModel) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", useFastModel ? config.getFastModel() : config.getModel());
        requestBody.addProperty("temperature", 0.1);
        requestBody.addProperty("max_tokens", useFastModel ? 512 : 1024);

        JsonArray messages = new JsonArray();

        // System message
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", SYSTEM_PROMPT);
        messages.add(systemMsg);

        // Add chat history for context
        for (T item : history) {
            try {
                // Use reflection to get role and content
                var roleMethod = item.getClass().getMethod("role");
                var contentMethod = item.getClass().getMethod("content");
                String role = (String) roleMethod.invoke(item);
                String content = (String) contentMethod.invoke(item);
                
                JsonObject historyMsg = new JsonObject();
                historyMsg.addProperty("role", role);
                historyMsg.addProperty("content", content);
                messages.add(historyMsg);
            } catch (Exception e) {
                logger.debug("Could not add history item", e);
            }
        }

        // Current user message
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);

        requestBody.add("messages", messages);
        return requestBody;
    }

    private String extractContent(String responseBody) {
        try {
            JsonObject response = gson.fromJson(responseBody, JsonObject.class);
            JsonArray choices = response.getAsJsonArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                JsonObject message = firstChoice.getAsJsonObject("message");
                return message.get("content").getAsString();
            }
        } catch (Exception e) {
            logger.error("Error parsing Groq response", e);
        }
        return "{\"action\":\"unknown\",\"params\":{},\"message\":\"Không thể xử lý phản hồi từ AI\"}";
    }

    public boolean isConfigured() {
        return config.isConfigured();
    }

    /**
     * Gets the config file path for display.
     */
    public String getConfigFilePath() {
        return AIConfig.getConfigFilePath();
    }
}
