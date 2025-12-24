package org.pdflite.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a bookmark for a specific PDF page.
 * Bookmarks allow users to mark and quickly navigate to important pages.
 */
public class Bookmark {
    private final int pageNumber;
    private String title;
    private final LocalDateTime createdAt;
    private String thumbnailPath; // Optional: path to thumbnail image
    private float yPosition; // Y position on page (0.0 = top, 1.0 = bottom)

    /**
     * Creates a new bookmark.
     *
     * @param pageNumber the page number (0-indexed)
     * @param title      the bookmark title/description
     */
    public Bookmark(int pageNumber, String title) {
        this.pageNumber = pageNumber;
        this.title = title;
        this.createdAt = LocalDateTime.now();
        this.yPosition = 0.0f; // Default to top of page
    }

    /**
     * Creates a bookmark with Y position.
     *
     * @param pageNumber the page number (0-indexed)
     * @param title      the bookmark title/description
     * @param yPosition  the Y position on page (0.0 = top, 1.0 = bottom)
     */
    public Bookmark(int pageNumber, String title, float yPosition) {
        this.pageNumber = pageNumber;
        this.title = title;
        this.createdAt = LocalDateTime.now();
        this.yPosition = yPosition;
    }

    /**
     * Creates a bookmark with all fields (for deserialization).
     */
    public Bookmark(int pageNumber, String title, LocalDateTime createdAt, String thumbnailPath) {
        this.pageNumber = pageNumber;
        this.title = title;
        this.createdAt = createdAt;
        this.thumbnailPath = thumbnailPath;
        this.yPosition = 0.0f;
    }

    /**
     * Creates a bookmark with all fields including Y position (for deserialization).
     */
    public Bookmark(int pageNumber, String title, LocalDateTime createdAt, String thumbnailPath, float yPosition) {
        this.pageNumber = pageNumber;
        this.title = title;
        this.createdAt = createdAt;
        this.thumbnailPath = thumbnailPath;
        this.yPosition = yPosition;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    public float getYPosition() {
        return yPosition;
    }

    public void setYPosition(float yPosition) {
        this.yPosition = yPosition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bookmark bookmark = (Bookmark) o;
        return pageNumber == bookmark.pageNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageNumber);
    }

    @Override
    public String toString() {
        return "Bookmark{" +
                "pageNumber=" + pageNumber +
                ", title='" + title + '\'' +
                ", yPosition=" + yPosition +
                ", createdAt=" + createdAt +
                '}';
    }
}
