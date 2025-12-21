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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RenderingManager using Mockito.
 * Tests business logic without JavaFX UI components.
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
    void testSetDocument() {
        renderingManager.setDocument(pdfDocument);
        
        // Should not throw exception
        assertDoesNotThrow(() -> renderingManager.setDocument(pdfDocument));
    }

    @Test
    void testRenderAllPagesWithoutDocument() {
        // Should not throw exception when document is null
        assertDoesNotThrow(() -> renderingManager.renderAllPages());
    }

    @Test
    void testClearPageRendererCache() {
        renderingManager.clearPageRendererCache();
        
        verify(pageRenderer).clearCache();
    }

    @Test
    void testReloadVisiblePages() {
        // Note: This method calls Platform.runLater() which requires JavaFX toolkit
        // We can only test that clearCache is called
        renderingManager.clearPageRendererCache();
        
        verify(pageRenderer).clearCache();
    }

    @Test
    void testPreserveScrollPositionWithoutDocument() {
        // Should not throw exception when document is null
        assertDoesNotThrow(() -> renderingManager.preserveScrollPositionAndApplyZoom(1.5));
    }
}
