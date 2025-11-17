package org.pdflite.model;

import java.util.Objects;

/**
 * Model class representing a single search result
 * Contains information about the matched text location and context
 */
public record SearchResult(int pageNumber, int startIndex, int endIndex, String matchedText, String contextBefore,
                           String contextAfter, double x, double y, double width, double height) {

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
        return !(Math.abs(y - other.y) > positionTolerance);
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