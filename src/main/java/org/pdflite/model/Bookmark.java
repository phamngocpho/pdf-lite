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
    }

    /**
     * Creates a bookmark with all fields (for deserialization).
     */
    public Bookmark(int pageNumber, String title, LocalDateTime createdAt, String thumbnailPath) {
        this.pageNumber = pageNumber;
        this.title = title;
        this.createdAt = createdAt;
        this.thumbnailPath = thumbnailPath;
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
                ", createdAt=" + createdAt +
                '}';
    }
}
