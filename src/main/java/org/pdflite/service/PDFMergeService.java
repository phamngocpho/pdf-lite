package org.pdflite.service;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Service for merging multiple PDF files into a single PDF document.
 * Handles large files (>50MB) efficiently using PDFBox's memory management.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class PDFMergeService {

    private static final Logger logger = LoggerFactory.getLogger(PDFMergeService.class);

    // Memory settings for handling large files
    private static final long MAX_MAIN_MEMORY_BYTES = 100 * 1024 * 1024; // 100MB
    private static final long MAX_STORAGE_BYTES = 500 * 1024 * 1024; // 500MB

    /**
     * Merges multiple PDF files into a single output file.
     *
     * @param inputFiles List of PDF files to merge (in order)
     * @param outputFile The destination file for the merged PDF
     * @throws IOException              if an error occurs during merging
     * @throws IllegalArgumentException if inputFiles is null, empty, or contains invalid files
     */
    public void mergePDFs(List<File> inputFiles, File outputFile) throws IOException {
        if (inputFiles == null || inputFiles.isEmpty()) {
            throw new IllegalArgumentException("Input files list cannot be null or empty");
        }

        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null");
        }

        // Validate all input files exist and are readable
        for (File file : inputFiles) {
            if (file == null || !file.exists() || !file.canRead()) {
                throw new IllegalArgumentException("Invalid or unreadable file: " +
                        (file != null ? file.getName() : "null"));
            }
        }

        logger.info("Starting merge of {} PDF files into: {}", inputFiles.size(), outputFile.getName());

        PDFMergerUtility merger = new PDFMergerUtility();
        merger.setDestinationFileName(outputFile.getAbsolutePath());

        // Add all source files
        for (File file : inputFiles) {
            logger.debug("Adding file to merge: {} ({})", file.getName(), formatFileSize(file.length()));
            merger.addSource(file);
        }

        // Perform the merge
        try {
            // Configure memory settings for large files
            MemoryUsageSetting memorySettings = MemoryUsageSetting.setupMixed(
                    MAX_MAIN_MEMORY_BYTES,
                    MAX_STORAGE_BYTES
            );
            merger.mergeDocuments(memorySettings.streamCache);
            logger.info("Successfully merged {} files into: {} ({})",
                    inputFiles.size(),
                    outputFile.getName(),
                    formatFileSize(outputFile.length()));
        } catch (IOException e) {
            logger.error("Error merging PDF files", e);
            // Clean up partial output file if merge failed
            if (outputFile.exists()) {
                outputFile.delete();
            }
            throw new IOException("Failed to merge PDF files: " + e.getMessage(), e);
        }
    }

    /**
     * Validates if a file is a valid PDF that can be merged.
     *
     * @param file The file to validate
     * @return true if the file is a valid PDF, false otherwise
     */
    public boolean isValidPDF(File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            return false;
        }

        // Check file extension
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".pdf")) {
            return false;
        }

        // Try to open with PDFBox to verify it's a valid PDF
        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                     org.apache.pdfbox.Loader.loadPDF(file)) {
            return doc.getNumberOfPages() > 0;
        } catch (IOException e) {
            logger.warn("File {} is not a valid PDF: {}", file.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Gets the number of pages in a PDF file.
     *
     * @param file The PDF file
     * @return The number of pages, or -1 if the file cannot be read
     */
    public int getPageCount(File file) {
        if (file == null || !file.exists()) {
            return -1;
        }

        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                     org.apache.pdfbox.Loader.loadPDF(file)) {
            return doc.getNumberOfPages();
        } catch (IOException e) {
            logger.error("Error reading page count from: {}", file.getName(), e);
            return -1;
        }
    }

    /**
     * Formats file size in human-readable format.
     *
     * @param bytes File size in bytes
     * @return Formatted string (e.g., "1.5 MB")
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}

