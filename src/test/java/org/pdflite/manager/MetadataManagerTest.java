package org.pdflite.manager;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.Before;
import org.junit.Test;
import org.pdflite.model.PDFDocument;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Test class for MetadataManager.
 */
public class MetadataManagerTest {
    
    private MetadataManager metadataManager;
    private PDFDocument testDocument;

    @Before
    public void setUp() throws IOException {
        metadataManager = new MetadataManager();
        
        // Create a test PDF document
        PDDocument pdDocument = new PDDocument();
        pdDocument.addPage(new PDPage());
        
        File tempFile = File.createTempFile("test-metadata", ".pdf");
        tempFile.deleteOnExit();
        pdDocument.save(tempFile);
        
        testDocument = new PDFDocument(pdDocument, tempFile);
    }

    @Test
    public void testGetMetadata() {
        Map<String, String> metadata = metadataManager.getMetadata(testDocument);
        
        assertNotNull("Metadata should not be null", metadata);
        assertTrue("Metadata should contain title", metadata.containsKey("title"));
        assertTrue("Metadata should contain author", metadata.containsKey("author"));
        assertTrue("Metadata should contain subject", metadata.containsKey("subject"));
        assertTrue("Metadata should contain keywords", metadata.containsKey("keywords"));
        assertTrue("Metadata should contain creator", metadata.containsKey("creator"));
        assertTrue("Metadata should contain producer", metadata.containsKey("producer"));
    }

    @Test
    public void testUpdateMetadata() {
        Map<String, String> newMetadata = new HashMap<>();
        newMetadata.put("title", "Test Document");
        newMetadata.put("author", "Test Author");
        newMetadata.put("subject", "Testing");
        newMetadata.put("keywords", "test, metadata, pdf");
        newMetadata.put("creator", "PDF Lite Test");
        
        boolean result = metadataManager.updateMetadata(testDocument, newMetadata);
        assertTrue("Update should succeed", result);
        assertTrue("Document should be marked as modified", testDocument.hasUnsavedEdits());
        
        // Verify the metadata was updated
        Map<String, String> retrievedMetadata = metadataManager.getMetadata(testDocument);
        assertEquals("Title should match", "Test Document", retrievedMetadata.get("title"));
        assertEquals("Author should match", "Test Author", retrievedMetadata.get("author"));
        assertEquals("Subject should match", "Testing", retrievedMetadata.get("subject"));
        assertEquals("Keywords should match", "test, metadata, pdf", retrievedMetadata.get("keywords"));
        assertEquals("Creator should match", "PDF Lite Test", retrievedMetadata.get("creator"));
    }

    @Test
    public void testUpdateMetadataWithEmptyValues() {
        Map<String, String> newMetadata = new HashMap<>();
        newMetadata.put("title", "");
        newMetadata.put("author", "");
        
        boolean result = metadataManager.updateMetadata(testDocument, newMetadata);
        assertTrue("Update should succeed with empty values", result);
        
        Map<String, String> retrievedMetadata = metadataManager.getMetadata(testDocument);
        assertEquals("Title should be empty", "", retrievedMetadata.get("title"));
        assertEquals("Author should be empty", "", retrievedMetadata.get("author"));
    }

    @Test
    public void testGetMetadataWithNullDocument() {
        Map<String, String> metadata = metadataManager.getMetadata(null);
        
        assertNotNull("Metadata should not be null", metadata);
        assertTrue("Metadata should be empty for null document", metadata.isEmpty());
    }

    @Test
    public void testUpdateMetadataWithNullDocument() {
        Map<String, String> newMetadata = new HashMap<>();
        newMetadata.put("title", "Test");
        
        boolean result = metadataManager.updateMetadata(null, newMetadata);
        assertFalse("Update should fail with null document", result);
    }

    @Test
    public void testMetadataModificationDateUpdated() throws InterruptedException {
        // Get initial modification date
        Map<String, String> initialMetadata = metadataManager.getMetadata(testDocument);
        String initialModDate = initialMetadata.get("modificationDate");
        
        // Wait a bit to ensure time difference
        Thread.sleep(1000);
        
        // Update metadata
        Map<String, String> newMetadata = new HashMap<>();
        newMetadata.put("title", "Updated Title");
        metadataManager.updateMetadata(testDocument, newMetadata);
        
        // Get updated modification date
        Map<String, String> updatedMetadata = metadataManager.getMetadata(testDocument);
        String updatedModDate = updatedMetadata.get("modificationDate");
        
        assertNotEquals("Modification date should be updated", initialModDate, updatedModDate);
    }
}
