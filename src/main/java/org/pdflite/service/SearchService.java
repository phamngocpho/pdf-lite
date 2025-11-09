package org.pdflite.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.pdflite.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 * Service for searching text in PDF documents with accurate coordinates
 */
public class SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);
    private static final int CONTEXT_LENGTH = 50;

    private volatile boolean cancelled = false;

    /**
     * Search for keyword in document with accurate text coordinates
     */
    public List<SearchResult> searchInDocument(
            PDDocument document,
            String keyword,
            boolean caseSensitive,
            boolean wholeWord) throws IOException {

        cancelled = false;
        List<SearchResult> allResults = new ArrayList<>();

        int totalPages = document.getNumberOfPages();
        logger.info("Starting search for '{}' in {} pages (caseSensitive={}, wholeWord={})",
                keyword, totalPages, caseSensitive, wholeWord);

        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            if (cancelled) {
                logger.info("Search cancelled");
                break;
            }

            PDPage page = document.getPage(pageIndex);
            List<SearchResult> pageResults = searchInPage(
                    document, pageIndex, page, keyword, caseSensitive, wholeWord
            );

            allResults.addAll(pageResults);

            logger.debug("Page {} - Found {} results", pageIndex + 1, pageResults.size());
        }

        logger.info("Search completed - Total results: {}", allResults.size());
        return allResults;
    }

    /**
     * Search in a single page with coordinate extraction
     */
    private List<SearchResult> searchInPage(
            PDDocument document,
            int pageIndex,
            PDPage page,
            String keyword,
            boolean caseSensitive,
            boolean wholeWord) throws IOException {

        // Create custom stripper to capture text positions
        CustomTextStripper stripper = new CustomTextStripper(keyword, caseSensitive, wholeWord);
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.setSortByPosition(true);

        // Extract text and capture positions
        StringWriter writer = new StringWriter();
        stripper.writeText(document, writer);

        // Get results with coordinates
        List<SearchResult> results = stripper.getResults();

        // Set page number for all results
        for (SearchResult result : results) {
            // Page number already set in CustomTextStripper
        }

        return results;
    }

    /**
     * Cancel ongoing search operation
     */
    public void cancelSearch() {
        cancelled = true;
        logger.info("Search cancellation requested");
    }

    /**
     * Check if search was cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Get context text around a position
     */
    private String getContext(String text, int position, int length, boolean after) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        if (after) {
            int end = Math.min(position + length, text.length());
            return text.substring(position, end);
        } else {
            int start = Math.max(0, position - length);
            return text.substring(start, position);
        }
    }

private class CustomTextStripper extends PDFTextStripper {
        private final String keyword;
        private final boolean caseSensitive;
        private final boolean wholeWord;
        private final List<SearchResult> results = new ArrayList<>();
        private int currentPageNumber = 0;
        private String fullPageText; // To get context
        private PDPage currentPage; // ✅ To get page dimensions

        public CustomTextStripper(String keyword, boolean caseSensitive, boolean wholeWord)
                throws IOException {
            super();
            this.keyword = caseSensitive ? keyword : keyword.toLowerCase();
            this.caseSensitive = caseSensitive;
            this.wholeWord = wholeWord;
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            super.startPage(page);
            this.currentPage = page; // ✅ Store current page
            currentPageNumber = getCurrentPageNo();
            // Extract full text for context fetching later
            PDFTextStripper contextStripper = new PDFTextStripper();
            contextStripper.setStartPage(currentPageNumber);
            contextStripper.setEndPage(currentPageNumber);
            this.fullPageText = contextStripper.getText(document);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            String currentText = caseSensitive ? text : text.toLowerCase();
            
            int index = 0;
            while ((index = currentText.indexOf(keyword, index)) != -1) {
                // Check for whole word match
                if (wholeWord) {
                    boolean startOk = (index == 0) || !Character.isLetterOrDigit(text.charAt(index - 1));
                    boolean endOk = (index + keyword.length() == text.length()) || !Character.isLetterOrDigit(text.charAt(index + keyword.length()));
                    if (!startOk || !endOk) {
                        index++;
                        continue;
                    }
                }

                // Found a match, calculate its precise bounding box
                BoundingBox bbox = calculateBoundingBoxForMatch(textPositions, index, keyword.length());
                if (bbox != null) {
                    String matchedText = text.substring(index, index + keyword.length());
                    
                    int globalIndex = fullPageText.indexOf(matchedText, 0); 
                    String contextBefore = getContext(fullPageText, globalIndex, CONTEXT_LENGTH, false);
                    String contextAfter = getContext(fullPageText, globalIndex + matchedText.length(), CONTEXT_LENGTH, true);

                    // ✅ FIX: Store RAW PDF coordinates, let rendering handle transformation
                    SearchResult result = new SearchResult(
                        currentPageNumber,
                        globalIndex, 
                        globalIndex + matchedText.length(),
                        matchedText,
                        contextBefore,
                        contextAfter,
                        bbox.x,      // ✅ Raw PDF X (unchanged)
                        bbox.y,      // ✅ Raw PDF Y (unchanged) 
                        bbox.width,  // ✅ Raw PDF width
                        bbox.height  // ✅ Raw PDF height
                    );
                    results.add(result);
                }
                index++;
            }
            super.writeString(text, textPositions);
        }

        /**
         * ✅ REWRITTEN: Calculates the precise bounding box of a match that may span
         * multiple TextPosition objects.
         */
        // Trong file: SearchService.java
// Bắt buộc phải thêm "throws IOException"
// Trong file: SearchService.java
// KHÔNG cần "throws IOException" nữa

private BoundingBox calculateBoundingBoxForMatch(List<TextPosition> textPositions, 
                                                 int matchStartIndex, 
                                                 int matchLength) {
    if (textPositions.isEmpty()) {
        return null;
    }

    int matchEndIndex = matchStartIndex + matchLength;
    
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = Float.MIN_VALUE;
    float maxY = Float.MIN_VALUE;
    boolean matchFound = false;

    // (Heuristic) Tỷ lệ phần đuôi (descent) so với phần trên (ascent)
    // Chúng ta đoán rằng đuôi chữ (như 'y') chiếm khoảng 25% chiều cao
    final float GUESSED_DESCENT_RATIO = 0.25f; 

    int charIndex = 0;
    for (TextPosition textPos : textPositions) {
        int textPosLen = textPos.getUnicode().length();
        int textPosEndIndex = charIndex + textPosLen;

        if (Math.max(charIndex, matchStartIndex) < Math.min(textPosEndIndex, matchEndIndex)) {
            matchFound = true;
            
            float ascent = textPos.getHeight(); // Giả sử đây là chiều cao trên baseline
            float baseline = textPos.getY();
            
            // Tính toán cạnh trên (Giống code gốc của bạn, đã đúng)
            float topY = baseline - ascent;
            
            // ✅ SỬA LỖI: Tính cạnh dưới
            // Thêm một phần padding bằng 25% chiều cao để bao trọn đuôi chữ
            float bottomY = baseline + (ascent * GUESSED_DESCENT_RATIO); 

            minX = Math.min(minX, textPos.getX());
            maxX = Math.max(maxX, textPos.getX() + textPos.getWidth());
            
            // Cập nhật Y với logic mới
            minY = Math.min(minY, topY);
            maxY = Math.max(maxY, bottomY);
        }
        
        charIndex = textPosEndIndex;
    }

    if (!matchFound) {
        return null;
    }

    return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
}
        /**
         * Check if match is a whole word
         */
        private boolean isWholeWord(String text, int start, int length) {
            // Check character before
            if (start > 0) {
                char before = text.charAt(start - 1);
                if (Character.isLetterOrDigit(before)) {
                    return false;
                }
            }

            // Check character after
            int end = start + length;
            if (end < text.length()) {
                char after = text.charAt(end);
                if (Character.isLetterOrDigit(after)) {
                    return false;
                }
            }

            return true;
        }

        public List<SearchResult> getResults() {
            return results;
        }

        /**
         * Simple bounding box container
         */
        private static class BoundingBox {

            final float x, y, width, height;

            BoundingBox(float x, float y, float width, float height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }
        }
    }
}
