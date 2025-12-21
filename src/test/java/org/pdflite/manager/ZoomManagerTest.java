package org.pdflite.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ZoomManager using Mockito.
 * Note: JavaFX components (ComboBox, ScrollPane) cannot be mocked,
 * so we test the core zoom logic without UI components.
 */
@ExtendWith(MockitoExtension.class)
class ZoomManagerTest {

    @Mock
    private PDFService pdfService;

    @Mock
    private ZoomManager.ZoomChangeListener zoomChangeListener;

    @Mock
    private PDFDocument pdfDocument;

    private ZoomManager zoomManager;

    @BeforeEach
    void setUp() {
        zoomManager = new ZoomManager(pdfService, zoomChangeListener);
    }

    @Test
    void testInitialZoom() {
        assertEquals(1.0, zoomManager.getCurrentZoom());
    }

    @Test
    void testSetCurrentZoom() {
        zoomManager.setCurrentZoom(1.5);
        assertEquals(1.5, zoomManager.getCurrentZoom());
    }

    @Test
    void testZoomIn() {
        zoomManager.setDocument(pdfDocument);
        double initialZoom = zoomManager.getCurrentZoom();
        
        zoomManager.zoomIn();
        
        assertTrue(zoomManager.getCurrentZoom() > initialZoom);
        verify(pdfDocument).setZoomLevel(anyDouble());
        verify(zoomChangeListener).onZoomChanged(anyDouble());
    }

    @Test
    void testZoomOut() {
        zoomManager.setDocument(pdfDocument);
        zoomManager.setCurrentZoom(1.5);
        
        zoomManager.zoomOut();
        
        assertTrue(zoomManager.getCurrentZoom() < 1.5);
        verify(pdfDocument).setZoomLevel(anyDouble());
        verify(zoomChangeListener).onZoomChanged(anyDouble());
    }

    @Test
    void testZoomInMaxLimit() {
        zoomManager.setDocument(pdfDocument);
        zoomManager.setCurrentZoom(5.0); // Max zoom
        
        zoomManager.zoomIn();
        
        assertEquals(5.0, zoomManager.getCurrentZoom()); // Should not exceed max
    }

    @Test
    void testZoomOutMinLimit() {
        zoomManager.setDocument(pdfDocument);
        zoomManager.setCurrentZoom(0.1); // Min zoom
        
        zoomManager.zoomOut();
        
        // Should not go below min (0.1), but zoom step is 0.25
        // So it will try 0.1 - 0.25 = -0.15, which gets clamped to 0.1
        assertTrue(zoomManager.getCurrentZoom() >= 0.1);
    }

    @Test
    void testSetZoomChangeListener() {
        ZoomManager.ZoomChangeListener newListener = mock(ZoomManager.ZoomChangeListener.class);
        zoomManager.setZoomChangeListener(newListener);
        zoomManager.setDocument(pdfDocument);
        
        zoomManager.zoomIn();
        
        verify(newListener).onZoomChanged(anyDouble());
    }

    @Test
    void testCalculateInitialZoomFromDimensions() {
        double zoom = zoomManager.calculateInitialZoomFromDimensions(800, 600);
        
        // Without scrollPane, should return default 0.7
        assertEquals(0.7, zoom);
    }

    @Test
    void testZoomWithoutDocument() {
        // Should not throw exception when document is null
        assertDoesNotThrow(() -> zoomManager.zoomIn());
        assertDoesNotThrow(() -> zoomManager.zoomOut());
    }

    @Test
    void testSetDocument() {
        zoomManager.setDocument(pdfDocument);
        
        // Verify document is set by testing zoom operation
        zoomManager.zoomIn();
        verify(pdfDocument).setZoomLevel(anyDouble());
    }

    @Test
    void testZoomInCallsListener() {
        zoomManager.setDocument(pdfDocument);
        
        zoomManager.zoomIn();
        
        verify(zoomChangeListener).onZoomApplied(anyDouble(), anyString());
    }

    @Test
    void testZoomOutCallsListener() {
        zoomManager.setDocument(pdfDocument);
        
        zoomManager.zoomOut();
        
        verify(zoomChangeListener).onZoomApplied(anyDouble(), anyString());
    }
}
