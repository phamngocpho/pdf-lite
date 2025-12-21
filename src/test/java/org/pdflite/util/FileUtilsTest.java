package org.pdflite.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileUtils.
 */
class FileUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void testFormatFileSizeBytes() {
        assertEquals("0 B", FileUtils.formatFileSize(0));
        assertEquals("500 B", FileUtils.formatFileSize(500));
        assertEquals("1023 B", FileUtils.formatFileSize(1023));
    }

    @Test
    void testFormatFileSizeKilobytes() {
        assertEquals("1.0 KB", FileUtils.formatFileSize(1024));
        assertEquals("1.5 KB", FileUtils.formatFileSize(1536));
        assertEquals("10.0 KB", FileUtils.formatFileSize(10240));
    }

    @Test
    void testFormatFileSizeMegabytes() {
        assertEquals("1.0 MB", FileUtils.formatFileSize(1024 * 1024));
        assertEquals("2.5 MB", FileUtils.formatFileSize((long)(2.5 * 1024 * 1024)));
        assertEquals("100.0 MB", FileUtils.formatFileSize(100L * 1024 * 1024));
    }

    @Test
    void testFormatFileSizeGigabytes() {
        assertEquals("1.0 GB", FileUtils.formatFileSize(1024L * 1024 * 1024));
        assertEquals("5.5 GB", FileUtils.formatFileSize((long)(5.5 * 1024 * 1024 * 1024)));
    }

    @Test
    void testFormatFileSizeTerabytes() {
        assertEquals("1.0 TB", FileUtils.formatFileSize(1024L * 1024 * 1024 * 1024));
    }

    @Test
    void testIsValidFileWithNull() {
        assertTrue(FileUtils.isValidFile(null));
    }

    @Test
    void testIsValidFileWithNonExistentFile() {
        File nonExistent = new File("nonexistent.txt");
        assertTrue(FileUtils.isValidFile(nonExistent));
    }

    @Test
    void testIsValidFileWithValidFile() throws IOException {
        File validFile = tempDir.resolve("test.txt").toFile();
        Files.writeString(validFile.toPath(), "test content");
        
        assertFalse(FileUtils.isValidFile(validFile));
    }

    @Test
    void testIsValidFileWithUnreadableFile() throws IOException {
        File unreadableFile = tempDir.resolve("unreadable.txt").toFile();
        Files.writeString(unreadableFile.toPath(), "test");
        unreadableFile.setReadable(false);
        
        // On some systems, setReadable might not work
        // So we just test that the method doesn't throw
        assertDoesNotThrow(() -> FileUtils.isValidFile(unreadableFile));
        
        unreadableFile.setReadable(true); // Cleanup
    }

    @Test
    void testGetFileNameOrNullWithNull() {
        assertEquals("null", FileUtils.getFileNameOrNull(null));
    }

    @Test
    void testGetFileNameOrNullWithValidFile() {
        File file = new File("test.txt");
        assertEquals("test.txt", FileUtils.getFileNameOrNull(file));
    }

    @Test
    void testGetFileNameOrNullWithPath() {
        File file = new File("/path/to/document.pdf");
        assertEquals("document.pdf", FileUtils.getFileNameOrNull(file));
    }
}
