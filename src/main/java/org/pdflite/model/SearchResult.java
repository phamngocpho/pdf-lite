package org.pdflite.model;

/**
 * Model class representing a single search result
 * Contains information about the matched text location and context
 */
public class SearchResult {
    private final int pageNumber;
    private final int startIndex;
    private final int endIndex;
    private final String matchedText;
    private final String contextBefore;
    private final String contextAfter;
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    
    public SearchResult(int pageNumber, int startIndex, int endIndex, 
                       String matchedText, String contextBefore, String contextAfter) {
        this(pageNumber, startIndex, endIndex, matchedText, contextBefore, contextAfter, 0, 0, 0, 0);
    }
    
    public SearchResult(int pageNumber, int startIndex, int endIndex, 
                       String matchedText, String contextBefore, String contextAfter,
                       double x, double y, double width, double height) {
        this.pageNumber = pageNumber;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.matchedText = matchedText;
        this.contextBefore = contextBefore;
        this.contextAfter = contextAfter;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    // Getters
    public int getPageNumber() {
        return pageNumber;
    }
    
    public int getStartIndex() {
        return startIndex;
    }
    
    public int getEndIndex() {
        return endIndex;
    }
    
    public String getMatchedText() {
        return matchedText;
    }
    
    public String getContextBefore() {
        return contextBefore;
    }
    
    public String getContextAfter() {
        return contextAfter;
    }
    
    public double getX() {
        return x;
    }
    
    public double getY() {
        return y;
    }
    
    public double getWidth() {
        return width;
    }
    
    public double getHeight() {
        return height;
    }
    
    /**
     * Get full context (before + match + after)
     * @return Full context string
     */
    public String getFullContext() {
        return contextBefore + matchedText + contextAfter;
    }
    
    /**
     * Get display text for UI (page number + context)
     * @return Display string
     */
    public String getDisplayText() {
        return String.format("Page %d: ...%s...", 
                           pageNumber, getFullContext().trim());
    }
    
    @Override
    public String toString() {
        return String.format("SearchResult{page=%d, start=%d, end=%d, text='%s'}", 
                           pageNumber, startIndex, endIndex, matchedText);
    }
}