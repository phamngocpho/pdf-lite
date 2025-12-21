package org.pdflite.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.pdflite.util.Constants;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ZoomManager using Mockito.
 * Tests business logic without JavaFX UI components.
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
        assertEquals(Constants.DEFAULT_ZOOM, zoomManager.getCurrentZoom());
    }

    @Test
    void testSetCurrentZoom() {
        zoomManager.setCurrentZoom(1.5);
        assertEquals(1.5, zoomManager.getCurrentZoom());
    }

    @Test
    void testZoomIn() {
        double initialZoom = zoomManager.getCurrentZoom();
        zoomManager.setDocument(pdfDocument);
        
        zoomManager.zoomIn();
        
        assertEquals(initialZoom + Constants.ZOOM_STEP, zoomManager.getCurrentZoom());
        verify(pdfDocument).setZoomLevel(anyDouble());
    }

    @Test
    void testZoomOut() {
        zoomManager.setCurrentZoom(1.0);
        zoomManager.setDocument(pdfDocument);
        
        zoomManager.zoomOut();
        
        assertEquals(1.0 - Constants.ZOOM_STEP, zoomManager.getCurrentZoom());
        verify(pdfDocument).setZoomLevel(anyDouble());
    }

    @Test
    void testZoomInMaxLimit() {
        zoomManager.setCurrentZoom(Constants.MAX_ZOOM);
        zoomManager.setDocument(pdfDocument);
        
        zoomManager.zoomIn();
        
        assertEquals(Constants.MAX_ZOOM, zoomManager.getCurrentZoom());
    }

    @Test
    void testZoomOutMinLimit() {
        zoomManager.setCurrentZoom(Constants.MIN_ZOOM);
        zoomManager.setDocument(pdfDocument);
        
        zoomManager.zoomOut();
        
        assertEquals(Constants.MIN_ZOOM, zoomManager.getCurrentZoom());
    }

    @Test
    void testZoomInWithoutDocument() {
        double initialZoom = zoomManager.getCurrentZoom();
        
        zoomManager.zoomIn();
        
        // Zoom should change but no document operations
        assertEquals(initialZoom + Constants.ZOOM_STEP, zoomManager.getCurrentZoom());
        verify(pdfDocument, never()).setZoomLevel(anyDouble());
    }

    @Test
    void testZoomChangeListenerCalled() {
        zoomManager.setDocument(pdfDocument);
        
        zoomManager.zoomIn();
        
        verify(zoomChangeListener).onZoomChanged(anyDouble());
        verify(zoomChangeListener).onZoomApplied(anyDouble(), anyString());
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
    void testCalculateInitialZoomWithoutScrollPane() {
        double zoom = zoomManager.calculateInitialZoomFromDimensions(1000, 800);
        
        assertEquals(0.7, zoom);
    }

    @Test
    void testSetDocument() {
        zoomManager.setDocument(pdfDocument);
        
        // Should not throw exception
        assertDoesNotThrow(() -> zoomManager.zoomIn());
    }
}
