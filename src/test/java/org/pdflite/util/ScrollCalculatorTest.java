package org.pdflite.util;

import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScrollCalculator.
 */
class ScrollCalculatorTest {

    private VBox pagesContainer;

    @BeforeEach
    void setUp() {
        pagesContainer = new VBox();
    }

    @Test
    void testPageSpacingConstant() {
        assertEquals(10.0, ScrollCalculator.PAGE_SPACING);
    }

    @Test
    void testCalculatePageYPositionFirstPage() {
        addPage(100);
        addPage(150);
        addPage(200);
        
        double y = ScrollCalculator.calculatePageYPosition(pagesContainer, 0);
        
        assertEquals(0.0, y);
    }

    @Test
    void testCalculatePageYPositionSecondPage() {
        addPage(100);
        addPage(150);
        addPage(200);
        
        double y = ScrollCalculator.calculatePageYPosition(pagesContainer, 1);
        
        assertEquals(110.0, y); // 100 + 10 spacing
    }

    @Test
    void testCalculatePageYPositionThirdPage() {
        addPage(100);
        addPage(150);
        addPage(200);
        
        double y = ScrollCalculator.calculatePageYPosition(pagesContainer, 2);
        
        assertEquals(270.0, y); // (100 + 10) + (150 + 10)
    }

    @Test
    void testCalculateContentHeight() {
        addPage(100);
        addPage(150);
        addPage(200);
        
        double height = ScrollCalculator.calculateContentHeight(pagesContainer, 3);
        
        assertTrue(height >= 470.0); // 100 + 10 + 150 + 10 + 200 + 10
    }

    @Test
    void testCalculatePageBounds() {
        addPage(100);
        addPage(150);
        addPage(200);
        
        ScrollCalculator.PageBounds bounds = ScrollCalculator.calculatePageBounds(pagesContainer, 1);
        
        assertEquals(110.0, bounds.start()); // After first page + spacing
        assertEquals(260.0, bounds.end());   // Start + height
        assertEquals(150.0, bounds.height());
    }

    @Test
    void testPageBoundsContains() {
        ScrollCalculator.PageBounds bounds = new ScrollCalculator.PageBounds(100.0, 200.0, 100.0);
        
        assertTrue(bounds.contains(100.0));
        assertTrue(bounds.contains(150.0));
        assertTrue(bounds.contains(199.9));
        assertFalse(bounds.contains(200.0));
        assertFalse(bounds.contains(99.9));
    }

    @Test
    void testPageBoundsOverlaps() {
        ScrollCalculator.PageBounds bounds = new ScrollCalculator.PageBounds(100.0, 200.0, 100.0);
        
        assertTrue(bounds.overlaps(50.0, 150.0));
        assertTrue(bounds.overlaps(150.0, 250.0));
        assertTrue(bounds.overlaps(100.0, 200.0));
        assertTrue(bounds.overlaps(50.0, 250.0));
        assertFalse(bounds.overlaps(0.0, 50.0));
        assertFalse(bounds.overlaps(250.0, 300.0));
    }

    @Test
    void testCalculatePageYPositionEmptyContainer() {
        double y = ScrollCalculator.calculatePageYPosition(pagesContainer, 0);
        
        assertEquals(0.0, y);
    }

    @Test
    void testCalculateContentHeightEmptyContainer() {
        double height = ScrollCalculator.calculateContentHeight(pagesContainer, 0);
        
        assertEquals(0.0, height);
    }

    @Test
    void testTwoPageMode() {
        pagesContainer.getProperties().put("twoPageMode", true);
        
        // Add two rows with 2 pages each
        HBox row1 = new HBox();
        row1.getChildren().add(createPageBox(100));
        row1.getChildren().add(createPageBox(120));
        pagesContainer.getChildren().add(row1);
        
        HBox row2 = new HBox();
        row2.getChildren().add(createPageBox(150));
        row2.getChildren().add(createPageBox(140));
        pagesContainer.getChildren().add(row2);
        
        // First row Y position should be 0
        double y0 = ScrollCalculator.calculatePageYPosition(pagesContainer, 0);
        assertEquals(0.0, y0);
        
        // Second row Y position should be max(100, 120) + spacing
        double y2 = ScrollCalculator.calculatePageYPosition(pagesContainer, 2);
        assertEquals(130.0, y2); // 120 + 10
    }

    @Test
    void testPageBoundsHeight() {
        ScrollCalculator.PageBounds bounds = new ScrollCalculator.PageBounds(100.0, 250.0, 150.0);
        
        assertEquals(150.0, bounds.height());
    }

    @Test
    void testPageBoundsEdgeCases() {
        ScrollCalculator.PageBounds bounds = new ScrollCalculator.PageBounds(0.0, 0.0, 0.0);
        
        assertFalse(bounds.contains(0.0));
        assertTrue(bounds.overlaps(0.0, 0.0));
    }

    private void addPage(double height) {
        VBox pageBox = createPageBox(height);
        pagesContainer.getChildren().add(pageBox);
    }

    private VBox createPageBox(double height) {
        VBox pageBox = new VBox();
        pageBox.setPrefHeight(height);
        return pageBox;
    }
}
