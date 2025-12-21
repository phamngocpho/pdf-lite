package org.pdflite.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pdflite.controller.PageRenderer;
import org.pdflite.controller.ScrollHandler;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RenderingManager.
 */
@ExtendWith(MockitoExtension.class)
class RenderingManagerTest {

    @Mock
    private PDFService pdfService;

    @Mock
    private PageRenderer pageRenderer;

    @Mock
    private ScrollHandler scrollHandler;

    @Mock
    private ZoomManager zoomManager;

    @Mock
    private PDFDocument pdfDocument;

    private RenderingManager renderingManager;

    @BeforeEach
    void setUp() {
        renderingManager = new RenderingManager(pdfService, pageRenderer, scrollHandler, zoomManager);
    }

    @Test
    void testInitialization() {
        assertNotNull(renderingManager);
    }

    @Test
    void testSetDocument() {
        assertDoesNotThrow(() -> renderingManager.setDocument(pdfDocument));
    }

    @Test
    void testSetUIComponents() {
        assertDoesNotThrow(() -> renderingManager.setUIComponents(null, null, null));
    }

    @Test
    void testRenderAllPagesWithoutDocument() {
        // Should not throw exception when document is null
        assertDoesNotThrow(() -> renderingManager.renderAllPages());
    }

    @Test
    void testPreserveScrollPositionWithoutDocument() {
        // Should not throw exception when document is null
        assertDoesNotThrow(() -> renderingManager.preserveScrollPositionAndApplyZoom(1.5));
    }

    @Test
    void testSetTwoPageModeWithoutDocument() {
        // Should not throw exception when document is null
        assertDoesNotThrow(() -> renderingManager.setTwoPageMode(true));
    }

    @Test
    void testGetPagesContainer() {
        assertNull(renderingManager.getPagesContainer());
    }

    @Test
    void testClearPageRendererCache() {
        assertDoesNotThrow(() -> renderingManager.clearPageRendererCache());
        verify(pageRenderer).clearCache();
    }
}
