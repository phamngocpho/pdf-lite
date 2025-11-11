package org.pdflite.model;

import java.util.Objects;

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
    
    public String getFullContext() {
        return contextBefore + matchedText + contextAfter;
    }
    
    public String getDisplayText() {
        return String.format("Page %d: ...%s...", 
                           pageNumber, getFullContext().trim());
    }
    
    // ==================== EQUALITY & HASHING ====================
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        SearchResult other = (SearchResult) obj;
        
        if (pageNumber != other.pageNumber) return false;
        if (startIndex != other.startIndex) return false;
        if (endIndex != other.endIndex) return false;
        
        if (!Objects.equals(matchedText, other.matchedText)) return false;
        
        double positionTolerance = 1.0; // 1 pixel tolerance
        if (Math.abs(x - other.x) > positionTolerance) return false;
        if (Math.abs(y - other.y) > positionTolerance) return false;
        
        return true;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(pageNumber, startIndex, endIndex, matchedText);
    }
    
    // ==================== STRING REPRESENTATION ====================
    
    @Override
    public String toString() {
        return String.format("SearchResult{page=%d, start=%d, end=%d, text='%s', pos=(%.1f,%.1f)}", 
                           pageNumber, startIndex, endIndex, matchedText, x, y);
    }
}