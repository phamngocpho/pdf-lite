package org.pdflite.manager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RecentFilesManager.
 */
class RecentFilesManagerTest {

    @TempDir
    Path tempDir;

    private RecentFilesManager manager;
    private String originalUserDir;

    @BeforeEach
    void setUp() {
        // Save original user.dir
        originalUserDir = System.getProperty("user.dir");
        
        // Set user.dir to temp directory for testing
        System.setProperty("user.dir", tempDir.toString());
        
        manager = new RecentFilesManager();
    }

    @AfterEach
    void tearDown() {
        // Restore original user.dir
        System.setProperty("user.dir", originalUserDir);
    }

    @Test
    void testInitialState() {
        List<String> recentFiles = manager.getRecentFiles();
        assertNotNull(recentFiles);
        assertTrue(recentFiles.isEmpty());
    }

    @Test
    void testAddRecentFile() throws IOException {
        Path testFile = tempDir.resolve("test.pdf");
        Files.createFile(testFile);
        
        manager.addRecentFile(testFile.toString());
        
        List<String> recentFiles = manager.getRecentFiles();
        assertEquals(1, recentFiles.size());
        assertEquals(testFile.toString(), recentFiles.get(0));
    }

    @Test
    void testAddMultipleRecentFiles() throws IOException {
        Path file1 = tempDir.resolve("file1.pdf");
        Path file2 = tempDir.resolve("file2.pdf");
        Files.createFile(file1);
        Files.createFile(file2);
        
        manager.addRecentFile(file1.toString());
        manager.addRecentFile(file2.toString());
        
        List<String> recentFiles = manager.getRecentFiles();
        assertEquals(2, recentFiles.size());
        assertEquals(file2.toString(), recentFiles.get(0)); // Most recent first
        assertEquals(file1.toString(), recentFiles.get(1));
    }

    @Test
    void testAddDuplicateFile() throws IOException {
        Path testFile = tempDir.resolve("test.pdf");
        Files.createFile(testFile);
        
        manager.addRecentFile(testFile.toString());
        manager.addRecentFile(testFile.toString());
        
        List<String> recentFiles = manager.getRecentFiles();
        assertEquals(1, recentFiles.size()); // Should not duplicate
    }

    @Test
    void testMaxRecentFilesLimit() throws IOException {
        // Add 12 files (max is 10)
        for (int i = 0; i < 12; i++) {
            Path file = tempDir.resolve("file" + i + ".pdf");
            Files.createFile(file);
            manager.addRecentFile(file.toString());
        }
        
        List<String> recentFiles = manager.getRecentFiles();
        assertEquals(10, recentFiles.size()); // Should not exceed max
    }

    @Test
    void testClearRecentFiles() throws IOException {
        Path testFile = tempDir.resolve("test.pdf");
        Files.createFile(testFile);
        
        manager.addRecentFile(testFile.toString());
        manager.clearRecentFiles();
        
        List<String> recentFiles = manager.getRecentFiles();
        assertTrue(recentFiles.isEmpty());
    }

    @Test
    void testAddNullFile() {
        manager.addRecentFile(null);
        
        List<String> recentFiles = manager.getRecentFiles();
        assertTrue(recentFiles.isEmpty());
    }

    @Test
    void testAddEmptyString() {
        manager.addRecentFile("");
        
        List<String> recentFiles = manager.getRecentFiles();
        assertTrue(recentFiles.isEmpty());
    }

    @Test
    void testGetRecentFilesFiltersNonExistent() throws IOException {
        Path existingFile = tempDir.resolve("existing.pdf");
        Files.createFile(existingFile);
        
        manager.addRecentFile(existingFile.toString());
        manager.addRecentFile(tempDir.resolve("non-existent.pdf").toString());
        
        List<String> recentFiles = manager.getRecentFiles();
        assertEquals(1, recentFiles.size()); // Only existing file
        assertEquals(existingFile.toString(), recentFiles.get(0));
    }

    @Test
    void testLastOpenedFile() throws IOException {
        Path testFile = tempDir.resolve("last.pdf");
        Files.createFile(testFile);
        
        manager.addRecentFile(testFile.toString());
        
        String lastOpened = manager.getLastOpenedFile();
        assertEquals(testFile.toString(), lastOpened);
    }

    @Test
    void testLastOpenedFileWhenNone() {
        String lastOpened = manager.getLastOpenedFile();
        assertNull(lastOpened);
    }
}
