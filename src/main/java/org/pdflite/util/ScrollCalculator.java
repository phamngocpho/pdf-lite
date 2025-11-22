package org.pdflite.util;

import javafx.scene.layout.VBox;

/**
 * Utility class for scroll position calculations.
 * Provides methods to calculate Y positions and content heights for PDF page containers.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class ScrollCalculator {

    /**
     * Standard spacing between pages in pixels.
     */
    public static final double PAGE_SPACING = 10.0;

    private ScrollCalculator() {
        // Private constructor to prevent instantiation
    }

    /**
     * Calculates the Y position of a specific page in the container.
     * This is the cumulative height of all pages before the target page.
     *
     * @param pagesContainer the container holding all page boxes
     * @param targetPageIndex the index of the target page (0-based)
     * @return the Y position in pixels
     */
    public static double calculatePageYPosition(VBox pagesContainer, int targetPageIndex) {
        double currentY = 0;

        Object twoMode = pagesContainer.getProperties().get("twoPageMode");
        boolean twoPage = twoMode instanceof Boolean && (Boolean) twoMode;

        if (!twoPage) {
            for (int i = 0; i < targetPageIndex && i < pagesContainer.getChildren().size(); i++) {
                VBox pageBox = (VBox) pagesContainer.getChildren().get(i);
                currentY += pageBox.getPrefHeight() + PAGE_SPACING;
            }
            return currentY;
        }

        // Two-page mode: pagesContainer children are rows (HBox), each containing up to 2 page VBoxes
        int targetRow = targetPageIndex / 2;
        int rows = pagesContainer.getChildren().size();
        for (int r = 0; r < targetRow && r < rows; r++) {
            javafx.scene.Node rowNode = pagesContainer.getChildren().get(r);
            double rowHeight = 0;
            if (rowNode instanceof javafx.scene.layout.HBox row) {
                for (javafx.scene.Node child : row.getChildren()) {
                    if (child instanceof VBox pageBox) {
                        rowHeight = Math.max(rowHeight, pageBox.getPrefHeight());
                    }
                }
            }
            currentY += rowHeight + PAGE_SPACING;
        }

        return currentY;
    }

    /**
     * Calculates the total content height of all pages in the container.
     * This is the sum of all page heights plus spacing.
     *
     * @param pagesContainer the container holding all page boxes
     * @param totalPages the total number of pages
     * @return the total content height in pixels
     */
    public static double calculateContentHeight(VBox pagesContainer, int totalPages) {
        Object twoMode = pagesContainer.getProperties().get("twoPageMode");
        boolean twoPage = twoMode instanceof Boolean && (Boolean) twoMode;

        double calculatedHeight = 0;

        if (!twoPage) {
            calculatedHeight = calculatePageYPosition(pagesContainer, totalPages);
        } else {
            // total rows = ceil(totalPages / 2)
            int rows = pagesContainer.getChildren().size();
            for (int r = 0; r < rows; r++) {
                javafx.scene.Node rowNode = pagesContainer.getChildren().get(r);
                double rowHeight = 0;
                if (rowNode instanceof javafx.scene.layout.HBox row) {
                    for (javafx.scene.Node child : row.getChildren()) {
                        if (child instanceof VBox pageBox) {
                            rowHeight = Math.max(rowHeight, pageBox.getPrefHeight());
                        }
                    }
                }
                calculatedHeight += rowHeight + PAGE_SPACING;
            }
        }

        // Use the larger of calculated height or actual container height
        return Math.max(calculatedHeight, pagesContainer.getHeight());
    }

    /**
     * Calculates page bounds information for a specific page.
     *
     * @param pagesContainer the container holding all page boxes
     * @param pageIndex the index of the page (0-based)
     * @return PageBounds object containing start, end, and height information
     */
    public static PageBounds calculatePageBounds(VBox pagesContainer, int pageIndex) {
        Object twoMode = pagesContainer.getProperties().get("twoPageMode");
        boolean twoPage = twoMode instanceof Boolean && (Boolean) twoMode;

        double currentY = calculatePageYPosition(pagesContainer, pageIndex);

        if (!twoPage) {
            if (pageIndex >= pagesContainer.getChildren().size()) {
                return new PageBounds(currentY, currentY, 0);
            }

            VBox pageBox = (VBox) pagesContainer.getChildren().get(pageIndex);
            double pageHeight = pageBox.getPrefHeight();
            double pageEnd = currentY + pageHeight;

            return new PageBounds(currentY, pageEnd, pageHeight);
        }

        // Two-page: find page VBox inside its row
        int rowIndex = pageIndex / 2;
        if (rowIndex >= pagesContainer.getChildren().size()) {
            return new PageBounds(currentY, currentY, 0);
        }

        javafx.scene.Node rowNode = pagesContainer.getChildren().get(rowIndex);
        double pageHeight = 0;
        if (rowNode instanceof javafx.scene.layout.HBox row) {
            int innerIndex = pageIndex % 2;
            if (innerIndex < row.getChildren().size() && row.getChildren().get(innerIndex) instanceof VBox pageBox) {
                pageHeight = pageBox.getPrefHeight();
            } else {
                // If missing, take max child height
                for (javafx.scene.Node child : row.getChildren()) {
                    if (child instanceof VBox pb) pageHeight = Math.max(pageHeight, pb.getPrefHeight());
                }
            }
        }

        double pageEnd = currentY + pageHeight;
        return new PageBounds(currentY, pageEnd, pageHeight);
    }

    /**
     * Represents the bounds of a page in the scroll container.
     *
     * @param start the Y position where the page starts
     * @param end the Y position where the page ends
     * @param height the height of the page
     */
    public record PageBounds(double start, double end, double height) {
        /**
         * Checks if a Y position is within this page's bounds.
         *
         * @param y the Y position to check
         * @return true if the position is within the page bounds
         */
        public boolean contains(double y) {
            return y >= start && y < end;
        }

        /**
         * Checks if this page overlaps with a range.
         *
         * @param rangeStart the start of the range
         * @param rangeEnd the end of the range
         * @return true if the page overlaps with the range
         */
        public boolean overlaps(double rangeStart, double rangeEnd) {
            return end >= rangeStart && start <= rangeEnd;
        }
    }
}

