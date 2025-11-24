package org.pdflite.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Smart text selector that extracts text based on character positions
 * rather than rectangular regions. This provides more accurate text selection
 * that follows the natural reading order and line breaks.
 * <p>
 * Similar to the implementation in SmartPDFViewer.java but adapted for JavaFX.
 * </p>
 */
public class SmartTextSelector {

    private final List<CharacterInfo> characters = new ArrayList<>();

    /**
     * Information about a single character in the PDF.
     */
    public static class CharacterInfo {
        private final String text;
        private final float x, y, width, height;
        private final int lineNumber;

        public CharacterInfo(TextPosition tp, int page, int line) {
            this.text = tp.getUnicode();
            this.x = tp.getX();
            this.y = tp.getY();
            this.width = tp.getWidth();
            this.height = tp.getHeight();
            this.lineNumber = line;
        }

        public Rectangle2D.Float getBounds() {
            return new Rectangle2D.Float(x, y - height, width, height);
        }

        public String getText() {
            return text;
        }

        public int getLineNumber() {
            return lineNumber;
        }
    }

    /**
     * Extracts text positions from a PDF page.
     *
     * @param doc      The PDF document
     * @param pageNum Zero-based page number
     * @throws IOException if extraction fails
     */
    public void extractTextPositions(PDDocument doc, int pageNum) throws IOException {
        characters.clear();

        PDFTextStripper stripper = new PDFTextStripper() {
            private int currentLine = 0;

            @Override
            protected void writeString(String text, List<TextPosition> textPositions) {
                for (TextPosition tp : textPositions) {
                    characters.add(new CharacterInfo(tp, pageNum, currentLine));
                }
                if (text.contains("\n") || text.contains("\r")) {
                    currentLine++;
                }
            }
        };

        stripper.setStartPage(pageNum + 1);
        stripper.setEndPage(pageNum + 1);
        stripper.getText(doc);
    }

    /**
     * Gets selected text between two points, following natural reading order.
     *
     * @param start Start point in PDF coordinates
     * @param end   End point in PDF coordinates
     * @return Selected text with proper line breaks
     */
    public String getSelectedText(Point2D start, Point2D end) {
        if (characters.isEmpty()) {
            return "";
        }

        CharacterInfo startChar = findNearestCharacter(start);
        CharacterInfo endChar = findNearestCharacter(end);

        if (startChar == null || endChar == null) {
            return "";
        }

        int startIdx = characters.indexOf(startChar);
        int endIdx = characters.indexOf(endChar);

        if (startIdx > endIdx) {
            int temp = startIdx;
            startIdx = endIdx;
            endIdx = temp;
        }

        return buildTextFromIndices(startIdx, endIdx);
    }

    /**
     * Selects a word at the given point.
     *
     * @param point Point in PDF coordinates
     * @return Selected word
     */
    public String selectWord(Point2D point) {
        CharacterInfo ch = findNearestCharacter(point);
        if (ch == null) {
            return "";
        }

        int idx = characters.indexOf(ch);

        int start = idx;
        while (start > 0 && isWordBoundary(characters.get(start - 1).text)) {
            start--;
        }

        int end = idx;
        while (end < characters.size() - 1 && isWordBoundary(characters.get(end + 1).text)) {
            end++;
        }

        return buildText(start, end);
    }

    /**
     * Selects a line at the given point.
     *
     * @param point Point in PDF coordinates
     * @return Selected line
     */
    public String selectLine(Point2D point) {
        CharacterInfo ch = findNearestCharacter(point);
        if (ch == null) {
            return "";
        }

        int lineNum = ch.lineNumber;
        int start = 0, end = characters.size() - 1;

        for (int i = 0; i < characters.size(); i++) {
            if (characters.get(i).lineNumber == lineNum) {
                start = i;
                break;
            }
        }

        for (int i = characters.size() - 1; i >= 0; i--) {
            if (characters.get(i).lineNumber == lineNum) {
                end = i;
                break;
            }
        }

        return buildText(start, end);
    }

    /**
     * Gets highlight regions for visual feedback.
     *
     * @param start Start point in PDF coordinates
     * @param end   End point in PDF coordinates
     * @return List of rectangles representing highlight regions (one per line)
     */
    public List<Rectangle2D> getHighlightRegions(Point2D start, Point2D end) {
        List<Rectangle2D> regions = new ArrayList<>();

        if (characters.isEmpty()) {
            return regions;
        }

        CharacterInfo startChar = findNearestCharacter(start);
        CharacterInfo endChar = findNearestCharacter(end);

        if (startChar == null || endChar == null) {
            return regions;
        }

        int startIdx = characters.indexOf(startChar);
        int endIdx = characters.indexOf(endChar);

        if (startIdx > endIdx) {
            int temp = startIdx;
            startIdx = endIdx;
            endIdx = temp;
        }

        // Group consecutive characters on the same line into continuous rectangles
        if (startIdx > endIdx) {
            return regions;
        }

        // Calculate average character width for gap detection
        float avgCharWidth = 0;
        int charCount = 0;
        for (int i = startIdx; i <= endIdx && i < startIdx + 10; i++) {
            avgCharWidth += characters.get(i).width;
            charCount++;
        }
        if (charCount > 0) {
            avgCharWidth /= charCount;
        } else {
            avgCharWidth = 5.0f; // Default fallback
        }
        
        float currentMinX = characters.get(startIdx).x;
        float currentMaxX = characters.get(startIdx).x + characters.get(startIdx).width;
        float currentMinY = characters.get(startIdx).y - characters.get(startIdx).height;
        float currentMaxY = characters.get(startIdx).y;
        int currentLine = characters.get(startIdx).lineNumber;

        for (int i = startIdx + 1; i <= endIdx; i++) {
            CharacterInfo ch = characters.get(i);
            
            // Check if character is on the same line and close enough
            boolean sameLine = ch.lineNumber == currentLine;
            float charMinX = ch.x;
            float charMaxX = ch.x + ch.width;
            
            // Only group if gap is small (less than 1.5x average character width)
            // This prevents grouping across large spaces (like between words)
            float gap = charMinX - currentMaxX;
            boolean closeEnough = gap >= 0 && gap <= avgCharWidth * 1.5f;
            
            if (sameLine && closeEnough) {
                // Extend current rectangle
                currentMinX = Math.min(currentMinX, charMinX);
                currentMaxX = Math.max(currentMaxX, charMaxX);
                currentMinY = Math.min(currentMinY, ch.y - ch.height);
                currentMaxY = Math.max(currentMaxY, ch.y);
            } else {
                // Finish current rectangle
                regions.add(new Rectangle2D.Float(
                    currentMinX,
                    currentMinY,
                    currentMaxX - currentMinX,
                    currentMaxY - currentMinY
                ));
                
                // Start new rectangle
                currentMinX = charMinX;
                currentMaxX = charMaxX;
                currentMinY = ch.y - ch.height;
                currentMaxY = ch.y;
                currentLine = ch.lineNumber;
            }
        }
        
        // Add the last rectangle
        regions.add(new Rectangle2D.Float(
            currentMinX,
            currentMinY,
            currentMaxX - currentMinX,
            currentMaxY - currentMinY
        ));

        return regions;
    }

    /**
     * Finds the nearest character to a given point.
     */
    private CharacterInfo findNearestCharacter(Point2D point) {
        if (characters.isEmpty()) {
            return null;
        }

        CharacterInfo nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (CharacterInfo ch : characters) {
            Rectangle2D bounds = ch.getBounds();
            double centerX = bounds.getCenterX();
            double centerY = bounds.getCenterY();
            double distance = point.distance(centerX, centerY);

            if (distance < minDistance) {
                minDistance = distance;
                nearest = ch;
            }
        }

        return nearest;
    }

    /**
     * Checks if a character is a word boundary.
     */
    private boolean isWordBoundary(String text) {
        return !text.matches("\\s|\\p{Punct}");
    }

    /**
     * Builds text from character indices.
     */
    private String buildText(int start, int end) {
        return buildTextFromIndices(start, end);
    }

    /**
     * Builds text from character indices with proper line breaks.
     *
     * @param startIdx Start character index (inclusive)
     * @param endIdx   End character index (inclusive)
     * @return Built text with line breaks
     */
    private String buildTextFromIndices(int startIdx, int endIdx) {
        StringBuilder sb = new StringBuilder();
        int currentLine = -1;

        for (int i = startIdx; i <= endIdx; i++) {
            CharacterInfo ch = characters.get(i);
            if (currentLine != -1 && ch.lineNumber != currentLine) {
                sb.append('\n');
            }
            sb.append(ch.text);
            currentLine = ch.lineNumber;
        }

        return sb.toString();
    }

    /**
     * Clears all extracted character positions.
     */
    public void clear() {
        characters.clear();
    }
}

