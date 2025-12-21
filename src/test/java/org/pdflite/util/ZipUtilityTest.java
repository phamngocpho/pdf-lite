package org.pdflite.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ZipUtility.
 */
class ZipUtilityTest {

    @TempDir
    Path tempDir;

    private List<File> testFiles;
    private File zipFile;

    @BeforeEach
    void setUp() throws IOException {
        testFiles = new ArrayList<>();
        
        // Create test files
        for (int i = 1; i <= 3; i++) {
            Path file = tempDir.resolve("test" + i + ".txt");
            Files.writeString(file, "Test content " + i);
            testFiles.add(file.toFile());
        }
        
        zipFile = tempDir.resolve("test.zip").toFile();
    }

    @AfterEach
    void tearDown() {
        if (zipFile != null && zipFile.exists()) {
            zipFile.delete();
        }
    }

    @Test
    void testCreateZipArchive() throws IOException {
        ZipUtility.createZipArchive(testFiles, zipFile);
        
        assertTrue(zipFile.exists());
        assertTrue(zipFile.length() > 0);
    }

    @Test
    void testCreateZipArchiveWithNullFiles() {
        assertThrows(IllegalArgumentException.class, 
            () -> ZipUtility.createZipArchive(null, zipFile));
    }

    @Test
    void testCreateZipArchiveWithEmptyList() {
        assertThrows(IllegalArgumentException.class, 
            () -> ZipUtility.createZipArchive(new ArrayList<>(), zipFile));
    }

    @Test
    void testCreateZipArchiveWithNullZipFile() {
        assertThrows(IllegalArgumentException.class, 
            () -> ZipUtility.createZipArchive(testFiles, null));
    }

    @Test
    void testZipContainsAllFiles() throws IOException {
        ZipUtility.createZipArchive(testFiles, zipFile);
        
        try (ZipFile zip = new ZipFile(zipFile)) {
            assertEquals(3, zip.size());
            
            assertNotNull(zip.getEntry("test1.txt"));
            assertNotNull(zip.getEntry("test2.txt"));
            assertNotNull(zip.getEntry("test3.txt"));
        }
    }

    @Test
    void testCreateZipArchiveWithProgress() throws IOException {
        List<Double> progressValues = new ArrayList<>();
        
        ZipUtility.createZipArchiveWithProgress(testFiles, zipFile, 
            (progress, processed, total) -> {
                progressValues.add(progress);
                assertTrue(progress >= 0.0 && progress <= 1.0);
                assertTrue(processed <= total);
            });
        
        assertTrue(zipFile.exists());
        assertFalse(progressValues.isEmpty());
        assertEquals(1.0, progressValues.get(progressValues.size() - 1), 0.01);
    }

    @Test
    void testCreateZipArchiveWithProgressNullCallback() throws IOException {
        // Should not throw exception with null callback
        assertDoesNotThrow(() -> 
            ZipUtility.createZipArchiveWithProgress(testFiles, zipFile, null));
        
        assertTrue(zipFile.exists());
    }

    @Test
    void testProgressCallbackValues() throws IOException {
        List<Integer> processedCounts = new ArrayList<>();
        
        ZipUtility.createZipArchiveWithProgress(testFiles, zipFile, 
            (progress, processed, total) -> {
                processedCounts.add(processed);
                assertEquals(3, total);
            });
        
        assertEquals(3, processedCounts.size());
        assertEquals(1, processedCounts.get(0));
        assertEquals(2, processedCounts.get(1));
        assertEquals(3, processedCounts.get(2));
    }
}
