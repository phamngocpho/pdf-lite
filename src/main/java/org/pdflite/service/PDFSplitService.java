package org.pdflite.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for splitting PDF files into multiple documents.
 * Supports splitting by page ranges or individual pages.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class PDFSplitService {

    private static final Logger logger = LoggerFactory.getLogger(PDFSplitService.class);

    /**
     * Represents a page range for splitting.
     *
     * @param startPage 1-based
     * @param endPage   1-based, inclusive
     */
    public record PageRange(int startPage, int endPage, String outputFileName) {
        @Override
        public String toString() {
            return String.format("Pages %d-%d -> %s", startPage, endPage, outputFileName);
        }
    }

    /**
     * Splits a PDF into multiple files based on page ranges.
     *
     * @param sourceFile      The source PDF file
     * @param outputDirectory The directory where split files will be saved
     * @param pageRanges      List of page ranges to extract
     * @return List of created output files
     * @throws IOException if an error occurs during splitting
     */
    public List<File> splitPDF(File sourceFile, File outputDirectory, List<PageRange> pageRanges)
            throws IOException {

        validateFile(sourceFile);
        validateOutputDirectory(outputDirectory);
        validatePageRanges(pageRanges);

        logger.info("Splitting PDF file: {} into {} parts", sourceFile.getName(), pageRanges.size());

        try (PDDocument sourceDoc = Loader.loadPDF(sourceFile)) {
            return splitPDFInternal(sourceDoc, outputDirectory, pageRanges);
        }
    }

    /**
     * Splits a PDF into multiple files based on page ranges (using already-opened PDDocument).
     * This method is useful for encrypted PDFs that are already decrypted in memory.
     *
     * @param sourceDoc       The source PDDocument (already opened/decrypted)
     * @param outputDirectory The directory where split files will be saved
     * @param pageRanges      List of page ranges to extract
     * @return List of created output files
     * @throws IOException if an error occurs during splitting
     */
    public List<File> splitPDF(PDDocument sourceDoc, File outputDirectory, List<PageRange> pageRanges)
            throws IOException {

        validateDocument(sourceDoc);
        validateOutputDirectory(outputDirectory);
        validatePageRanges(pageRanges);

        logger.info("Splitting PDF document into {} parts", pageRanges.size());

        return splitPDFInternal(sourceDoc, outputDirectory, pageRanges);
    }

    /**
     * Internal method to split a PDF document into multiple files.
     */
    private List<File> splitPDFInternal(PDDocument sourceDoc, File outputDirectory, List<PageRange> pageRanges)
            throws IOException {

        List<File> outputFiles = new ArrayList<>();
        int totalPages = sourceDoc.getNumberOfPages();

        for (PageRange range : pageRanges) {
            validatePageRange(range, totalPages);

            File outputFile = new File(outputDirectory, range.outputFileName());
            extractPages(sourceDoc, range.startPage(), range.endPage(), outputFile);
            outputFiles.add(outputFile);

            logger.debug("Created: {} (pages {}-{})", outputFile.getName(), range.startPage(), range.endPage());
        }

        logger.info("Successfully split PDF into {} files", outputFiles.size());
        return outputFiles;
    }

    /**
     * Splits a PDF into individual pages (one page per file).
     *
     * @param sourceFile      The source PDF file
     * @param outputDirectory The directory where split files will be saved
     * @param baseFileName    Base name for output files (e.g., "page" -> "page_1.pdf", "page_2.pdf")
     * @return List of created output files
     * @throws IOException if an error occurs during splitting
     */
    public List<File> splitIntoIndividualPages(File sourceFile, File outputDirectory, String baseFileName)
            throws IOException {

        validateFile(sourceFile);
        validateOutputDirectory(outputDirectory);

        logger.info("Splitting PDF file: {} into individual pages", sourceFile.getName());

        try (PDDocument sourceDoc = Loader.loadPDF(sourceFile)) {
            return splitIntoIndividualPagesInternal(sourceDoc, outputDirectory, baseFileName);
        }
    }

    /**
     * Splits a PDF into individual pages (using already-opened PDDocument).
     *
     * @param sourceDoc       The source PDDocument (already opened/decrypted)
     * @param outputDirectory The directory where split files will be saved
     * @param baseFileName    Base name for output files
     * @return List of created output files
     * @throws IOException if an error occurs during splitting
     */
    public List<File> splitIntoIndividualPages(PDDocument sourceDoc, File outputDirectory, String baseFileName)
            throws IOException {

        validateDocument(sourceDoc);
        validateOutputDirectory(outputDirectory);

        logger.info("Splitting PDF document into individual pages");

        return splitIntoIndividualPagesInternal(sourceDoc, outputDirectory, baseFileName);
    }

    /**
     * Internal method to split PDF into individual pages.
     */
    private List<File> splitIntoIndividualPagesInternal(PDDocument sourceDoc, File outputDirectory, String baseFileName)
            throws IOException {

        List<File> outputFiles = new ArrayList<>();
        int totalPages = sourceDoc.getNumberOfPages();

        for (int i = 1; i <= totalPages; i++) {
            String fileName = String.format("%s_%d.pdf", baseFileName, i);
            File outputFile = new File(outputDirectory, fileName);

            extractPages(sourceDoc, i, i, outputFile);
            outputFiles.add(outputFile);

            logger.debug("Created: {} (page {})", fileName, i);
        }

        logger.info("Successfully split PDF into {} individual pages", outputFiles.size());
        return outputFiles;
    }

    /**
     * Splits a PDF by page count (e.g., every 10 pages).
     *
     * @param sourceFile      The source PDF file
     * @param outputDirectory The directory where split files will be saved
     * @param pagesPerFile    Number of pages per output file
     * @param baseFileName    Base name for output files
     * @return List of created output files
     * @throws IOException if an error occurs during splitting
     */
    public List<File> splitByPageCount(File sourceFile, File outputDirectory,
                                       int pagesPerFile, String baseFileName) throws IOException {

        if (pagesPerFile < 1) {
            throw new IllegalArgumentException("Pages per file must be at least 1");
        }

        List<PageRange> ranges = new ArrayList<>();

        try (PDDocument sourceDoc = Loader.loadPDF(sourceFile)) {
            int totalPages = sourceDoc.getNumberOfPages();
            int partNumber = 1;

            for (int startPage = 1; startPage <= totalPages; startPage += pagesPerFile) {
                int endPage = Math.min(startPage + pagesPerFile - 1, totalPages);
                String fileName = String.format("%s_part%d.pdf", baseFileName, partNumber++);
                ranges.add(new PageRange(startPage, endPage, fileName));
            }
        }

        return splitPDF(sourceFile, outputDirectory, ranges);
    }

    /**
     * Extracts specific pages from a source document and saves to a new file.
     * Uses PDFMergerUtility to ensure all resources including fonts are properly preserved.
     *
     * @param sourceDoc  The source PDF document
     * @param startPage  Start page (1-based, inclusive)
     * @param endPage    End page (1-based, inclusive)
     * @param outputFile The output file
     * @throws IOException if an error occurs during extraction
     */
    private void extractPages(PDDocument sourceDoc, int startPage, int endPage, File outputFile)
            throws IOException {

        // Create temporary files for each page to extract
        // This approach ensures all resources are properly copied
        List<File> tempFiles = new ArrayList<>();

        try {
            // First, extract each page individually to ensure resources are preserved
            for (int i = startPage - 1; i < endPage; i++) {
                org.apache.pdfbox.pdmodel.PDPage sourcePage = sourceDoc.getPage(i);

                // Create a temporary document for this single page
                PDDocument tempDoc = new PDDocument();
                File tempFile;
                try {
                    // Import the page - this should copy all resources
                    org.apache.pdfbox.pdmodel.PDPage importedPage = tempDoc.importPage(sourcePage);
                    importedPage.setRotation(sourcePage.getRotation());

                    // Save to a temporary file
                    tempFile = File.createTempFile("pdf_extract_page_", ".pdf");
                    tempFile.deleteOnExit();

                    // Save and ensure the file is flushed
                    tempDoc.save(tempFile);

                    // CRITICAL: Close document BEFORE adding to the list to ensure the file is fully written
                    tempDoc.close();
                    tempDoc = null;

                    // Force file system sync to ensure the file is completely written
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile, true)) {
                        fos.getFD().sync();
                    }

                    // Verify temp file is valid before adding
                    if (tempFile.exists() && tempFile.length() > 0) {
                        tempFiles.add(tempFile);
                        logger.debug("Extracted page {} to temporary file ({} bytes)", i + 1, tempFile.length());
                    } else {
                        throw new IOException("Failed to create valid temporary file for page " + (i + 1));
                    }
                } finally {
                    // Ensure the document is closed
                    if (tempDoc != null) {
                        try {
                            tempDoc.close();
                        } catch (Exception e) {
                            logger.warn("Error closing temporary document", e);
                        }
                    }
                }
            }

            // CRITICAL: Ensure all temp files are fully written and closed before merging
            // Verify all temp files exist and have content
            for (File tempFile : tempFiles) {
                if (!tempFile.exists() || tempFile.length() == 0) {
                    throw new IOException("Temporary file is invalid: " + tempFile.getName());
                }
            }

            // Now merge all temporary files into the final output
            // PDFMergerUtility properly handles resource merging including fonts
            if (tempFiles.size() == 1) {
                // Single page - just copy the temp file
                java.nio.file.Files.copy(
                        tempFiles.getFirst().toPath(),
                        outputFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );

                // Force sync after copy
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile, true)) {
                    fos.getFD().sync();
                }
            } else {
                // Multiple pages - merge them
                PDFMergerUtility merger = new PDFMergerUtility();
                for (File tempFile : tempFiles) {
                    merger.addSource(tempFile);
                }
                merger.setDestinationFileName(outputFile.getAbsolutePath());
                merger.mergeDocuments(null);

                // Force sync after merge
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile, true)) {
                    fos.getFD().sync();
                }
            }

            // Verify the final output file
            if (!outputFile.exists() || outputFile.length() == 0) {
                throw new IOException("Failed to create output file: " + outputFile.getName());
            }

            logger.debug("Extracted pages {}-{} to: {} ({} bytes) with properly embedded resources",
                    startPage, endPage, outputFile.getName(), outputFile.length());

        } finally {
            // Clean up temporary files
            for (File tempFile : tempFiles) {
                try {
                    if (tempFile.exists()) {
                        // Small delay to ensure file handles are released
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logger.debug("Interrupted while waiting to delete temp file");
                        }
                        if (!tempFile.delete()) {
                            logger.warn("Could not delete temporary file: {}", tempFile.getName());
                            // Mark for deletion on exit as fallback
                            tempFile.deleteOnExit();
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to delete temporary file: {}", tempFile.getName(), e);
                    // Mark for deletion on exit as fallback
                    if (tempFile.exists()) {
                        tempFile.deleteOnExit();
                    }
                }
            }
        }
    }

    /**
     * Gets the total number of pages in a PDF file.
     *
     * @param file The PDF file
     * @return The number of pages
     * @throws IOException if the file cannot be read
     */
    public int getPageCount(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("Invalid file");
        }

        try (PDDocument doc = Loader.loadPDF(file)) {
            return doc.getNumberOfPages();
        }
    }

    /**
     * Validates if a page range is valid for a given PDF.
     *
     * @param file      The PDF file
     * @param startPage Start page (1-based)
     * @param endPage   End page (1-based)
     * @return true if the range is valid
     */
    public boolean isValidPageRange(File file, int startPage, int endPage) {
        try {
            int totalPages = getPageCount(file);
            return startPage >= 1 && endPage <= totalPages && startPage <= endPage;
        } catch (IOException e) {
            logger.error("Error validating page range", e);
            return false;
        }
    }

    // ==================== Validation Methods ====================

    /**
     * Validates source file.
     */
    private void validateFile(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists() || !sourceFile.canRead()) {
            throw new IllegalArgumentException("Invalid source file");
        }
    }

    /**
     * Validates PDDocument.
     */
    private void validateDocument(PDDocument sourceDoc) {
        if (sourceDoc == null) {
            throw new IllegalArgumentException("Source document cannot be null");
        }
    }

    /**
     * Validates output directory.
     */
    private void validateOutputDirectory(File outputDirectory) {
        if (outputDirectory == null || !outputDirectory.exists() || !outputDirectory.isDirectory()) {
            throw new IllegalArgumentException("Invalid output directory");
        }
    }

    /**
     * Validates a page ranges list.
     */
    private void validatePageRanges(List<PageRange> pageRanges) {
        if (pageRanges == null || pageRanges.isEmpty()) {
            throw new IllegalArgumentException("Page ranges cannot be null or empty");
        }
    }

    /**
     * Validates a single-page range.
     */
    private void validatePageRange(PageRange range, int totalPages) {
        if (range.startPage() < 1 || range.endPage() > totalPages || range.startPage() > range.endPage()) {
            throw new IllegalArgumentException(
                    String.format("Invalid page range: %d-%d (total pages: %d)",
                            range.startPage(), range.endPage(), totalPages));
        }
    }
}

