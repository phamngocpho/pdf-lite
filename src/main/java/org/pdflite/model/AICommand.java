package org.pdflite.model;

import com.google.gson.JsonObject;
import java.util.List;

/**
 * Represents a parsed AI command.
 */
public class AICommand {
    
    public enum Action {
        SPLIT,
        EXTRACT,
        DELETE,
        REORDER,
        SWAP,
        MOVE,
        BOOKMARK,
        GOTO,
        INFO,
        READTEXT,
        SUMMARIZE,
        HELP,
        UNKNOWN
    }

    private final Action action;
    private final JsonObject params;
    private final String message;

    public AICommand(Action action, JsonObject params, String message) {
        this.action = action;
        this.params = params;
        this.message = message;
    }

    public Action getAction() {
        return action;
    }

    public JsonObject getParams() {
        return params;
    }

    public String getMessage() {
        return message;
    }

    // Helper methods to extract common parameters
    public List<Integer> getPages() {
        if (params == null || !params.has("pages")) {
            return List.of();
        }
        return params.getAsJsonArray("pages").asList().stream()
                .map(e -> e.getAsInt())
                .toList();
    }

    public List<Integer> getNewOrder() {
        if (params == null || !params.has("newOrder")) {
            return List.of();
        }
        return params.getAsJsonArray("newOrder").asList().stream()
                .map(e -> e.getAsInt())
                .toList();
    }

    public String getOutputName() {
        if (params == null || !params.has("outputName")) {
            return null;
        }
        return params.get("outputName").getAsString();
    }

    public String getTitle() {
        if (params == null || !params.has("title")) {
            return null;
        }
        return params.get("title").getAsString();
    }

    public int getPage() {
        if (params == null || !params.has("page")) {
            return -1;
        }
        return params.get("page").getAsInt();
    }

    public int getFromPage() {
        if (params == null || !params.has("fromPage")) {
            return -1;
        }
        return params.get("fromPage").getAsInt();
    }

    public int getToPage() {
        if (params == null || !params.has("toPage")) {
            return -1;
        }
        return params.get("toPage").getAsInt();
    }

    public int getPage1() {
        if (params == null || !params.has("page1")) {
            return -1;
        }
        return params.get("page1").getAsInt();
    }

    public int getPage2() {
        if (params == null || !params.has("page2")) {
            return -1;
        }
        return params.get("page2").getAsInt();
    }

    @Override
    public String toString() {
        return "AICommand{action=" + action + ", params=" + params + ", message='" + message + "'}";
    }
}
