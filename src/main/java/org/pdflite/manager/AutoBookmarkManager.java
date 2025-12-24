package org.pdflite.manager;

import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Automatically creates bookmarks based on user reading behavior.
 * Creates bookmarks for pages where the user spends significant time.
 */
public class AutoBookmarkManager {
    private static final Logger logger = LoggerFactory.getLogger(AutoBookmarkManager.class);
    
    // Thời gian đọc tối thiểu để tự động bookmark (giây)
    private static final int MIN_READ_TIME_SECONDS = 30;
    
    // Kiểm tra mỗi 5 giây
    private static final int CHECK_INTERVAL_MS = 5000;
    
    private final BookmarkManager bookmarkManager;
    private final Map<Integer, PageReadTime> pageReadTimes;
    private Timer checkTimer;
    private PDFDocument currentDocument;
    private int currentPage = -1;
    private boolean enabled = true;
    
    /**
     * Tracks reading time for a page.
     */
    private static class PageReadTime {
        LocalDateTime startTime;
        int totalSeconds;
        boolean bookmarked;
        
        PageReadTime() {
            this.startTime = LocalDateTime.now();
            this.totalSeconds = 0;
            this.bookmarked = false;
        }
    }
    
    public AutoBookmarkManager(BookmarkManager bookmarkManager) {
        this.bookmarkManager = bookmarkManager;
        this.pageReadTimes = new HashMap<>();
    }
    
    /**
     * Starts tracking reading time.
     */
    public void start(PDFDocument document) {
        this.currentDocument = document;
        this.pageReadTimes.clear();
        
        if (checkTimer != null) {
            checkTimer.cancel();
        }
        
        checkTimer = new Timer("AutoBookmarkTimer", true);
        checkTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkAndCreateBookmark();
            }
        }, CHECK_INTERVAL_MS, CHECK_INTERVAL_MS);
        
        logger.info("Auto-bookmark tracking started");
    }
    
    /**
     * Stops tracking.
     */
    public void stop() {
        if (checkTimer != null) {
            checkTimer.cancel();
            checkTimer = null;
        }
        logger.info("Auto-bookmark tracking stopped");
    }
    
    /**
     * Called when user navigates to a new page.
     */
    public void onPageChanged(int newPage) {
        if (!enabled || currentDocument == null) {
            return;
        }
        
        // Update previous page's read time
        if (currentPage >= 0) {
            PageReadTime readTime = pageReadTimes.get(currentPage);
            if (readTime != null) {
                long secondsOnPage = java.time.Duration.between(
                    readTime.startTime, 
                    LocalDateTime.now()
                ).getSeconds();
                readTime.totalSeconds += (int) secondsOnPage;
            }
        }
        
        // Start tracking new page
        currentPage = newPage;
        if (!pageReadTimes.containsKey(newPage)) {
            pageReadTimes.put(newPage, new PageReadTime());
        } else {
            // Reset start time for this page
            pageReadTimes.get(newPage).startTime = LocalDateTime.now();
        }
    }
    
    /**
     * Checks if current page should be auto-bookmarked.
     */
    private void checkAndCreateBookmark() {
        if (!enabled || currentDocument == null || currentPage < 0) {
            return;
        }
        
        PageReadTime readTime = pageReadTimes.get(currentPage);
        if (readTime == null || readTime.bookmarked) {
            return;
        }
        
        // Calculate total time on this page
        long currentSessionSeconds = java.time.Duration.between(
            readTime.startTime, 
            LocalDateTime.now()
        ).getSeconds();
        
        int totalSeconds = readTime.totalSeconds + (int) currentSessionSeconds;
        
        // Auto-bookmark if read time exceeds threshold
        if (totalSeconds >= MIN_READ_TIME_SECONDS) {
            // Check if bookmark already exists (manual bookmark)
            if (!bookmarkManager.hasBookmark(currentPage)) {
                String title = generateAutoBookmarkTitle(currentPage, totalSeconds);
                bookmarkManager.addBookmark(currentPage, title);
                logger.info("Auto-bookmarked page {} after {} seconds", currentPage + 1, totalSeconds);
            }
            readTime.bookmarked = true;
        }
    }
    
    /**
     * Generates a title for auto-created bookmark.
     */
    private String generateAutoBookmarkTitle(int pageNumber, int readTimeSeconds) {
        int minutes = readTimeSeconds / 60;
        return String.format("📖 Page %d (read %dm)", pageNumber + 1, minutes);
    }
    
    /**
     * Enables or disables auto-bookmarking.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("Auto-bookmark {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Checks if auto-bookmarking is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets reading statistics for a page.
     */
    public int getPageReadTime(int pageNumber) {
        PageReadTime readTime = pageReadTimes.get(pageNumber);
        return readTime != null ? readTime.totalSeconds : 0;
    }
    
    /**
     * Clears all tracking data.
     */
    public void clear() {
        pageReadTimes.clear();
        currentPage = -1;
    }
}
