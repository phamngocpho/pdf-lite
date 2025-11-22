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

public class SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);
    private static final int CONTEXT_LENGTH = 50;

    private volatile boolean cancelled = false;

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
     * Searches for a keyword in a specific page.
     *
     * @param document      the PDF document
     * @param pageIndex     the zero-based page index
     * @param page          the PDPage object (unused in current implementation)
     * @param keyword       the keyword to search for
     * @param caseSensitive whether the search should be case-sensitive
     * @param wholeWord     whether to match whole words only
     * @return a list of search results found on the page
     * @throws IOException if an error occurs during text extraction
     */
    private List<SearchResult> searchInPage(
            PDDocument document,
            int pageIndex,
            PDPage page,
            String keyword,
            boolean caseSensitive,
            boolean wholeWord) throws IOException {

        CustomTextStripper stripper = new CustomTextStripper(keyword, caseSensitive, wholeWord);
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.setSortByPosition(true);
        StringWriter writer = new StringWriter();
        stripper.writeText(document, writer);
        return stripper.getResults();
    }

    /**
     * Cancels the current search operation.
     */
    public void cancelSearch() {
        cancelled = true;
        logger.info("Search cancellation requested");
    }

    /**
     * Checks if the search operation has been cancelled.
     *
     * @return true if the search has been cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Extracts context text around a position.
     *
     * @param text     the full text string
     * @param position the position in the text
     * @param after    true to get context after position, false to get context before
     * @return the context string (up to CONTEXT_LENGTH characters)
     */
    private String getContext(String text, int position, boolean after) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        if (after) {
            int end = Math.min(position + SearchService.CONTEXT_LENGTH, text.length());
            return text.substring(position, end);
        } else {
            int start = Math.max(0, position - SearchService.CONTEXT_LENGTH);
            return text.substring(start, position);
        }
    }

    /**
     * Custom PDFTextStripper that extracts search results with bounding boxes.
     */
    private class CustomTextStripper extends PDFTextStripper {

        private final String keyword;
        private final boolean caseSensitive;
        private final boolean wholeWord;
        private final List<SearchResult> results = new ArrayList<>();
        private int currentPageNumber = 0;
        private String fullPageText;

        /**
         * Creates a new CustomTextStripper.
         *
         * @param keyword       the keyword to search for
         * @param caseSensitive whether the search is case-sensitive
         * @param wholeWord     whether to match whole words only
         */
        public CustomTextStripper(String keyword, boolean caseSensitive, boolean wholeWord) {
            super();
            this.keyword = caseSensitive ? keyword : keyword.toLowerCase();
            this.caseSensitive = caseSensitive;
            this.wholeWord = wholeWord;
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            super.startPage(page);
            currentPageNumber = getCurrentPageNo();
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
                if (wholeWord) {
                    boolean startOk = (index == 0) || !Character.isLetterOrDigit(text.charAt(index - 1));
                    boolean endOk = (index + keyword.length() == text.length()) || !Character.isLetterOrDigit(text.charAt(index + keyword.length()));
                    if (!startOk || !endOk) {
                        index++;
                        continue;
                    }
                }

                BoundingBox bbox = calculateBoundingBoxForMatch(textPositions, index, keyword.length());
                if (bbox != null) {
                    String matchedText = text.substring(index, index + keyword.length());

                    int globalIndex = fullPageText.indexOf(matchedText);
                    String contextBefore = getContext(fullPageText, globalIndex, false);
                    String contextAfter = getContext(fullPageText, globalIndex + matchedText.length(), true);

                    SearchResult result = new SearchResult(
                            currentPageNumber,
                            globalIndex,
                            globalIndex + matchedText.length(),
                            matchedText,
                            contextBefore,
                            contextAfter,
                            bbox.x,
                            bbox.y,
                            bbox.width,
                            bbox.height
                    );
                    results.add(result);
                }
                index++;
            }
            super.writeString(text, textPositions);
        }

        /**
         * Calculates the bounding box for a matched text region.
         *
         * @param textPositions   list of text positions from PDFBox
         * @param matchStartIndex start index of the match in the text string
         * @param matchLength     length of the matched text
         * @return the bounding box, or null if no match found
         */
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

            final float DESCENT_RATIO = 0.25f;
            int charIndex = 0;
            for (TextPosition textPos : textPositions) {
                int textPosLen = textPos.getUnicode().length();
                int textPosEndIndex = charIndex + textPosLen;

                if (Math.max(charIndex, matchStartIndex) < Math.min(textPosEndIndex, matchEndIndex)) {
                    matchFound = true;

                    float ascent = textPos.getHeight();
                    float baseline = textPos.getY();
                    float topY = baseline - ascent;
                    float bottomY = baseline + (ascent * DESCENT_RATIO);

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
         * Checks if a match represents a whole word.
         *
         * @param text   the full text string
         * @param start  the start index of the match
         * @param length the length of the match
         * @return true if the match is a whole word
         */
        private boolean isWholeWord(String text, int start, int length) {
            if (start > 0) {
                char before = text.charAt(start - 1);
                if (Character.isLetterOrDigit(before)) {
                    return false;
                }
            }

            int end = start + length;
            if (end < text.length()) {
                char after = text.charAt(end);
                return !Character.isLetterOrDigit(after);
            }

            return true;
        }

        /**
         * Gets the list of search results found.
         *
         * @return the list of search results
         */
        public List<SearchResult> getResults() {
            return results;
        }

        /**
         * Simple record to hold bounding box coordinates.
         *
         * @param x      X coordinate in PDF points
         * @param y      Y coordinate in PDF points
         * @param width  Width in PDF points
         * @param height Height in PDF points
         */
        private record BoundingBox(float x, float y, float width, float height) {
        }
    }
}
