package org.pdflite.manager;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatically detects and creates bookmarks based on the document structure.
 * Analyzes text formatting (font size, style) to identify chapter titles and headings.
 * Uses AI to improve and clean up detected titles.
 */
public class SmartBookmarkManager {
    private static final Logger logger = LoggerFactory.getLogger(SmartBookmarkManager.class);
    
    private final BookmarkManager bookmarkManager;
    private org.pdflite.service.GroqService groqService;
    
    // Thresholds for detecting headings
    private static final float HEADING_FONT_SIZE_THRESHOLD = 14.0f; // Font size >= 14pt
    
    // Common chapter/section patterns
    private static final Pattern[] CHAPTER_PATTERNS = {
        Pattern.compile("^Chapter\\s+\\d+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^Chương\\s+\\d+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^CHAPTER\\s+[IVXLCDM]+"), // Roman numerals
        Pattern.compile("^\\d+\\.\\s+[A-Z]"), // "1. Introduction"
        Pattern.compile("^Part\\s+\\d+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^Phần\\s+\\d+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^Section\\s+\\d+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^§\\s*\\d+"), // Section symbol
    };
    
    public SmartBookmarkManager(BookmarkManager bookmarkManager) {
        this.bookmarkManager = bookmarkManager;
    }
    
    /**
     * Sets the Groq AI service for improving bookmark titles.
     */
    public void setGroqService(org.pdflite.service.GroqService groqService) {
        this.groqService = groqService;
    }
    
    /**
     * Analyzes PDF and creates bookmarks for detected headings/chapters.
     * 
     * @param pdfDocument the PDF document
     * @return number of bookmarks created
     */
    public int analyzeAndCreateBookmarks(PDFDocument pdfDocument) {
        if (pdfDocument == null || pdfDocument.getDocument() == null) {
            logger.warn("Cannot analyze: no document");
            return 0;
        }
        
        PDDocument pdDoc = pdfDocument.getDocument();
        List<DetectedHeading> headings = new ArrayList<>();
        
        try {
            // Analyze each page for headings
            for (int pageIndex = 0; pageIndex < pdDoc.getNumberOfPages(); pageIndex++) {
                List<DetectedHeading> pageHeadings = analyzePageForHeadings(pdDoc, pageIndex);
                headings.addAll(pageHeadings);
            }
            
            // Remove duplicates and filter low-quality headings
            headings = filterAndDeduplicateHeadings(headings);
            
            // Use AI to improve titles if available
            if (groqService != null && !headings.isEmpty()) {
                headings = improveHeadingsWithAI(headings);
            }
            
            // Create bookmarks - collect all first, then add in batch
            List<org.pdflite.model.Bookmark> toAdd = new ArrayList<>();
            for (DetectedHeading heading : headings) {
                if (!bookmarkManager.hasBookmark(heading.pageNumber)) {
                    // Calculate a normalized Y position (0.0 = top, 1.0 = bottom)
                    float normalizedY = heading.yPosition / 800.0f; // Approximate page height
                    normalizedY = Math.max(0.0f, Math.min(1.0f, normalizedY));
                    toAdd.add(new org.pdflite.model.Bookmark(heading.pageNumber, heading.text, normalizedY));
                }
            }
            
            // Add all bookmarks at once
            if (!toAdd.isEmpty()) {
                bookmarkManager.addBookmarksBatch(toAdd);
            }
            
            int created = toAdd.size();
            logger.info("Created {} smart bookmarks from {} detected headings", created, headings.size());
            return created;
        } catch (Exception e) {
            logger.error("Error analyzing document for bookmarks", e);
            return 0;
        }
    }
    
    /**
     * Filters out duplicates and low-quality headings.
     */
    private List<DetectedHeading> filterAndDeduplicateHeadings(List<DetectedHeading> headings) {
        List<DetectedHeading> filtered = new ArrayList<>();
        Set<String> seenTexts = new HashSet<>();
        Set<Integer> seenPages = new HashSet<>();
        
        for (DetectedHeading heading : headings) {
            String normalizedText = heading.text.toLowerCase().trim();
            
            // Skip if too short or too generic
            if (normalizedText.length() < 5) {
                continue;
            }
            
            // Skip common generic words
            if (isGenericHeading(normalizedText)) {
                continue;
            }
            
            // Skip if we already have a bookmark on this page
            if (seenPages.contains(heading.pageNumber)) {
                continue;
            }
            
            // Skip if a very similar text already exists
            boolean isDuplicate = false;
            for (String seen : seenTexts) {
                if (isSimilarText(normalizedText, seen)) {
                    isDuplicate = true;
                    break;
                }
            }
            
            if (!isDuplicate) {
                filtered.add(heading);
                seenTexts.add(normalizedText);
                seenPages.add(heading.pageNumber);
            }
        }
        
        logger.info("Filtered {} headings down to {} unique headings", headings.size(), filtered.size());
        return filtered;
    }
    
    /**
     * Checks if the heading is too generic or invalid.
     */
    private boolean isGenericHeading(String text) {
        // Skip HTML/XML tags
        if (text.matches(".*<[^>]+>.*")) {
            return true;
        }
        
        // Skip code-like content
        if (text.contains("<!") || text.contains("/>") || text.contains("();") || 
            text.contains("{}") || text.contains("[]") || text.contains("//")) {
            return true;
        }
        
        // Skip if contains too many special characters
        int specialCount = text.replaceAll("[a-zA-Z0-9\\s]", "").length();
        if (specialCount > text.length() * 0.3) {
            return true;
        }
        
        // Skip single words that are likely not chapter titles
        String[] invalidWords = {
            "introduction", "conclusion", "summary", "abstract", "references",
            "appendix", "index", "contents", "preface", "acknowledgments",
            "http", "https", "www", "url", "host", "rmi", "html", "xml",
            "public", "private", "class", "void", "static", "final",
            "delete", "insert", "update", "select", "table", "body",
            "head", "title", "script", "style", "div", "span",
            "true", "false", "null", "return", "import", "export",
            "parameters", "reading", "sessions", "processes", "types"
        };
        
        String lowerText = text.toLowerCase().trim();
        for (String word : invalidWords) {
            if (lowerText.equals(word) || lowerText.equals(word + ",") || 
                lowerText.equals(word + ".") || lowerText.equals(word + ";")) {
                return true;
            }
        }
        
        // Skip if all uppercase and looks like a constant/keyword
        return text.equals(text.toUpperCase()) && !text.contains(" ") && text.length() < 15;
    }
    
    /**
     * Checks if two texts are similar (for deduplication).
     */
    private boolean isSimilarText(String text1, String text2) {
        // Exact match
        if (text1.equals(text2)) {
            return true;
        }
        
        // One contains the other
        if (text1.contains(text2) || text2.contains(text1)) {
            return true;
        }
        
        // Calculate similarity ratio (simple approach)
        int maxLen = Math.max(text1.length(), text2.length());
        int minLen = Math.min(text1.length(), text2.length());
        
        // If lengths are very different, not similar
        if (minLen < maxLen * 0.5) {
            return false;
        }
        
        // Count common characters
        int common = 0;
        for (int i = 0; i < minLen; i++) {
            if (text1.charAt(i) == text2.charAt(i)) {
                common++;
            }
        }
        
        // If more than 70% similar, consider duplicate
        return (double) common / maxLen > 0.7;
    }
    
    /**
     * Analyzes a single page for headings.
     */
    private List<DetectedHeading> analyzePageForHeadings(PDDocument pdDoc, int pageIndex) {
        List<DetectedHeading> headings = new ArrayList<>();
        
        try {
            HeadingDetectorStripper stripper = new HeadingDetectorStripper(pageIndex);
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);
            stripper.getText(pdDoc);
            
            headings.addAll(stripper.getDetectedHeadings());
            
        } catch (IOException e) {
            logger.warn("Error analyzing page {}: {}", pageIndex, e.getMessage());
        }
        
        return headings;
    }

    /**
         * Represents a detected heading in the document.
         */
        private record DetectedHeading(int pageNumber, String text, float fontSize, boolean isBold,
                                       boolean isChapterPattern, float yPosition) {

        @Override
            public String toString() {
                return String.format("Page %d: %s (%.1fpt, %s)",
                        pageNumber + 1, text, fontSize, isBold ? "bold" : "normal");
            }
        }
    
    /**
     * Custom PDFTextStripper that detects headings based on font properties.
     */
    private static class HeadingDetectorStripper extends PDFTextStripper {
        private final int pageIndex;
        private final List<DetectedHeading> detectedHeadings = new ArrayList<>();
        private final List<TextLine> currentPageLines = new ArrayList<>();
        
        HeadingDetectorStripper(int pageIndex) throws IOException {
            super();
            this.pageIndex = pageIndex;
            this.setSortByPosition(true);
        }
        
        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            if (textPositions.isEmpty()) {
                return;
            }
            
            // Get font properties from the first character
            TextPosition firstPos = textPositions.getFirst();
            float fontSize = firstPos.getFontSizeInPt();
            String fontName = firstPos.getFont().getName();
            boolean isBold = fontName != null && 
                (fontName.toLowerCase().contains("bold") || 
                 fontName.toLowerCase().contains("black") ||
                 fontName.toLowerCase().contains("heavy"));
            
            float yPosition = firstPos.getY();
            
            // Store line info
            currentPageLines.add(new TextLine(text.trim(), fontSize, isBold, yPosition));
            
            super.writeString(text, textPositions);
        }
        
        @Override
        protected void endPage(PDPage page) throws IOException {
            super.endPage(page);
            
            // Analyze collected lines
            analyzeLines();
            currentPageLines.clear();
        }
        
        /**
         * Analyzes collected lines to detect headings.
         */
        private void analyzeLines() {
            if (currentPageLines.isEmpty()) {
                return;
            }
            
            // Calculate the average font size for the page
            float avgFontSize = (float) currentPageLines.stream()
                .mapToDouble(line -> line.fontSize)
                .average()
                .orElse(12.0);
            
            // Find max font size on the page
            float maxFontSize = (float) currentPageLines.stream()
                .mapToDouble(line -> line.fontSize)
                .max()
                .orElse(14.0);
            
            for (TextLine line : currentPageLines) {
                String text = line.text;
                
                // Skip empty or very short lines
                if (text.length() < 3) {
                    continue;
                }
                
                // Skip lines that are all numbers or special characters
                if (text.matches("^[\\d\\s\\-_.,:;]+$")) {
                    continue;
                }
                
                // Skip lines with HTML/code patterns
                if (text.matches(".*[<>{}\\[\\]]{2,}.*") || 
                    text.contains("<!") || text.contains("/>") || 
                    text.contains("();") || text.contains("=\"")) {
                    continue;
                }
                
                boolean isHeading = false;
                boolean isChapterPattern = false;
                
                // Check 1: Matches the chapter/section pattern (the highest priority)
                for (Pattern pattern : CHAPTER_PATTERNS) {
                    Matcher matcher = pattern.matcher(text);
                    if (matcher.find()) {
                        isHeading = true;
                        isChapterPattern = true;
                        break;
                    }
                }
                
                // Check 2: Large font size (significantly larger than average)
                if (!isHeading && line.fontSize >= HEADING_FONT_SIZE_THRESHOLD && 
                    line.fontSize >= avgFontSize * 1.2 &&
                    line.fontSize >= maxFontSize * 0.9) { // Close to max font on the page
                    // Must look like a title (starts with a letter, has reasonable length)
                    if (text.length() <= 100 && Character.isLetter(text.charAt(0))) {
                        isHeading = true;
                    }
                }
                
                // Check 3: Bold text with large font
                if (!isHeading && line.isBold && line.fontSize >= avgFontSize * 1.1 && text.length() <= 100 && Character.isLetter(text.charAt(0))) {
                    isHeading = true;
                }
                
                if (isHeading) {
                    // Clean up the text
                    String cleanText = cleanHeadingText(text);
                    
                    if (cleanText.length() >= 3 && cleanText.length() <= 100) {
                        detectedHeadings.add(new DetectedHeading(
                            pageIndex, 
                            cleanText, 
                            line.fontSize, 
                            line.isBold,
                            isChapterPattern,
                            line.yPosition
                        ));
                    }
                }
            }
        }
        
        /**
         * Cleans heading text (removes extra spaces, page numbers, etc.)
         */
        private String cleanHeadingText(String text) {
            // Remove leading/trailing whitespace
            text = text.trim();
            
            // Remove trailing page numbers like "... 25"
            text = text.replaceAll("\\s+\\d+$", "");
            
            // Remove multiple spaces
            text = text.replaceAll("\\s+", " ");
            
            // Limit length
            if (text.length() > 100) {
                text = text.substring(0, 97) + "...";
            }
            
            return text;
        }
        
        List<DetectedHeading> getDetectedHeadings() {
            return detectedHeadings;
        }

        /**
                 * Represents a line of text with its properties.
                 */
                private record TextLine(String text, float fontSize, boolean isBold, float yPosition) {
        }
    }
    
    /**
     * Uses AI to improve and clean up detected heading titles.
     */
    private List<DetectedHeading> improveHeadingsWithAI(List<DetectedHeading> headings) {
        if (headings.isEmpty()) {
            return headings;
        }
        
        try {
            // Build prompt with all headings
            StringBuilder prompt = new StringBuilder();
            prompt.append("You are a PDF bookmark title optimizer. Your task is to clean up and improve bookmark titles extracted from a PDF document.\n\n");
            
            prompt.append("## STRICT RULES:\n");
            prompt.append("1. KEEP the original meaning - do NOT change the topic or add unrelated information\n");
            prompt.append("2. Remove trailing page numbers, dots (...), and special characters\n");
            prompt.append("3. Fix broken text from PDF extraction (e.g., 'Net work' → 'Network')\n");
            prompt.append("4. Use Title Case for chapter/section headings\n");
            prompt.append("5. Keep titles concise: 5-50 characters preferred, max 60 characters\n");
            prompt.append("6. For single-word technical terms, keep them as-is (e.g., 'TCP', 'HTTP', 'RMI')\n");
            prompt.append("7. Do NOT expand abbreviations unless the title is unclear\n");
            prompt.append("8. Do NOT add explanations or descriptions that weren't in the original\n");
            prompt.append("9. If title is already good, return it unchanged\n");
            prompt.append("10. Remove duplicate words or stuttering (e.g., 'Network Network' → 'Network')\n\n");
            
            prompt.append("## EXAMPLES:\n");
            prompt.append("- 'Chapter 2 Network Architectures . . . . . 15' → 'Chapter 2: Network Architectures'\n");
            prompt.append("- 'INTRODUCTION' → 'Introduction'\n");
            prompt.append("- 'Net work Topo logies' → 'Network Topologies'\n");
            prompt.append("- 'TCP/IP' → 'TCP/IP'\n");
            prompt.append("- '2.1 Introduction' → '2.1 Introduction'\n");
            prompt.append("- 'Ad-Hoc' → 'Ad-Hoc'\n\n");
            
            prompt.append("## INPUT TITLES TO IMPROVE:\n");
            
            for (int i = 0; i < headings.size(); i++) {
                prompt.append(String.format("%d. %s\n", 
                    i + 1, 
                    headings.get(i).text));
            }
            
            prompt.append("\n## OUTPUT FORMAT:\n");
            prompt.append("Return EXACTLY ").append(headings.size()).append(" lines, one improved title per line.\n");
            prompt.append("Format each line as: NUMBER. IMPROVED_TITLE\n");
            prompt.append("Example:\n1. Chapter 1: Introduction\n2. Network Topologies\n\n");
            prompt.append("DO NOT include any explanations, comments, or extra text. ONLY the numbered titles.");

            // Call AI (async) and wait for the result
            String response = groqService.chat(prompt.toString())
                .get(30, java.util.concurrent.TimeUnit.SECONDS); // Wait max 30 seconds
            
            if (response != null && !response.trim().isEmpty()) {
                // Parse AI response
                List<DetectedHeading> improved = getDetectedHeadingList(headings, response);

                // If we got valid improvements, use them
                if (improved.size() == headings.size()) {
                    logger.info("AI improved {} bookmark titles", improved.size());
                    return improved;
                }
            }
            
        } catch (java.util.concurrent.TimeoutException e) {
            logger.warn("AI request timed out after 30 seconds");
        } catch (Exception e) {
            logger.warn("Failed to improve headings with AI: {}", e.getMessage());
        }
        
        // Return original if AI fails
        return headings;
    }

    private static List<DetectedHeading> getDetectedHeadingList(List<DetectedHeading> headings, String response) {
        String[] lines = response.trim().split("\n");
        List<DetectedHeading> improved = new ArrayList<>();

        for (int i = 0; i < headings.size() && i < lines.length; i++) {
            String line = lines[i].trim();

            // Remove numbering (1., 2., etc.)
            line = line.replaceFirst("^\\d+\\.\\s*", "");
            line = line.replaceFirst("^\\d+\\)\\s*", "");
            line = line.replaceFirst("^-\\s*", "");
            line = line.trim();

            if (line.length() >= 5) {
                DetectedHeading original = headings.get(i);
                improved.add(new DetectedHeading(
                    original.pageNumber,
                    line,
                    original.fontSize,
                    original.isBold,
                    original.isChapterPattern,
                    original.yPosition
                ));
            } else {
                // Keep original if AI response is invalid
                improved.add(headings.get(i));
            }
        }
        return improved;
    }
}
