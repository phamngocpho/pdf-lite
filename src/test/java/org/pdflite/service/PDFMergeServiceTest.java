package org.pdflite.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PDFMergeService.
 */
class PDFMergeServiceTest {

    private PDFMergeService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new PDFMergeService();
    }

    @Test
    void testMergePDFsWithNullInputFiles() {
        File outputFile = tempDir.resolve("output.pdf").toFile();
        assertThrows(IllegalArgumentException.class, () -> service.mergePDFs(null, outputFile));
    }

    @Test
    void testMergePDFsWithEmptyInputFiles() {
        List<File> inputFiles = new ArrayList<>();
        File outputFile = tempDir.resolve("output.pdf").toFile();
        assertThrows(IllegalArgumentException.class, () -> service.mergePDFs(inputFiles, outputFile));
    }

    @Test
    void testMergePDFsWithNullOutputFile() {
        List<File> inputFiles = List.of(new File("test.pdf"));
        assertThrows(IllegalArgumentException.class, () -> service.mergePDFs(inputFiles, null));
    }

    @Test
    void testIsValidPDFWithNull() {
        assertFalse(service.isValidPDF(null));
    }

    @Test
    void testIsValidPDFWithNonExistentFile() {
        File nonExistent = new File("non-existent.pdf");
        assertFalse(service.isValidPDF(nonExistent));
    }

    @Test
    void testGetPageCountWithNull() {
        assertEquals(-1, service.getPageCount(null));
    }

    @Test
    void testGetPageCountWithNonExistentFile() {
        File nonExistent = new File("non-existent.pdf");
        assertEquals(-1, service.getPageCount(nonExistent));
    }
}
