package org.pdflite.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.pdflite.model.AICommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses AI response JSON into AICommand objects.
 */
public class AICommandParser {
    private static final Logger logger = LoggerFactory.getLogger(AICommandParser.class);
    private final Gson gson = new Gson();

    /**
     * Parses the AI response string into an AICommand.
     */
    public AICommand parse(String aiResponse) {
        try {
            // Clean up response - remove markdown code blocks if present
            String cleaned = cleanResponse(aiResponse);
            
            JsonObject json = gson.fromJson(cleaned, JsonObject.class);
            
            String actionStr = json.has("action") ? json.get("action").getAsString().toUpperCase() : "UNKNOWN";
            JsonObject params = json.has("params") ? json.getAsJsonObject("params") : new JsonObject();
            String message = json.has("message") ? json.get("message").getAsString() : "";

            AICommand.Action action;
            try {
                action = AICommand.Action.valueOf(actionStr);
            } catch (IllegalArgumentException e) {
                action = AICommand.Action.UNKNOWN;
            }

            return new AICommand(action, params, message);

        } catch (JsonSyntaxException e) {
            logger.error("Failed to parse AI response: {}", aiResponse, e);
            return new AICommand(
                    AICommand.Action.UNKNOWN,
                    new JsonObject(),
                    "Không thể phân tích phản hồi từ AI"
            );
        }
    }

    /**
     * Cleans the AI response by removing markdown code blocks.
     */
    private String cleanResponse(String response) {
        if (response == null) return "{}";
        
        String cleaned = response.trim();
        
        // Remove markdown code blocks
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        
        return cleaned.trim();
    }
}
