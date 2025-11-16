package org.pdflite.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
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

        if (sourceFile == null || !sourceFile.exists() || !sourceFile.canRead()) {
            throw new IllegalArgumentException("Invalid source file");
        }

        if (outputDirectory == null || !outputDirectory.exists() || !outputDirectory.isDirectory()) {
            throw new IllegalArgumentException("Invalid output directory");
        }

        if (pageRanges == null || pageRanges.isEmpty()) {
            throw new IllegalArgumentException("Page ranges cannot be null or empty");
        }

        logger.info("Splitting PDF: {} into {} parts", sourceFile.getName(), pageRanges.size());

        List<File> outputFiles = new ArrayList<>();

        try (PDDocument sourceDoc = Loader.loadPDF(sourceFile)) {
            int totalPages = sourceDoc.getNumberOfPages();

            for (PageRange range : pageRanges) {
                // Validate range
                if (range.startPage() < 1 || range.endPage() > totalPages ||
                        range.startPage() > range.endPage()) {
                    throw new IllegalArgumentException(
                            String.format("Invalid page range: %d-%d (total pages: %d)",
                                    range.startPage(), range.endPage(), totalPages));
                }

                File outputFile = new File(outputDirectory, range.outputFileName());
                extractPages(sourceDoc, range.startPage(), range.endPage(), outputFile);
                outputFiles.add(outputFile);

                logger.info("Created: {} (pages {}-{})",
                        outputFile.getName(), range.startPage(), range.endPage());
            }
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

        if (sourceFile == null || !sourceFile.exists() || !sourceFile.canRead()) {
            throw new IllegalArgumentException("Invalid source file");
        }

        if (outputDirectory == null || !outputDirectory.exists() || !outputDirectory.isDirectory()) {
            throw new IllegalArgumentException("Invalid output directory");
        }

        logger.info("Splitting PDF: {} into individual pages", sourceFile.getName());

        List<File> outputFiles = new ArrayList<>();

        try (PDDocument sourceDoc = Loader.loadPDF(sourceFile)) {
            int totalPages = sourceDoc.getNumberOfPages();

            for (int i = 1; i <= totalPages; i++) {
                String fileName = String.format("%s_%d.pdf", baseFileName, i);
                File outputFile = new File(outputDirectory, fileName);

                extractPages(sourceDoc, i, i, outputFile);
                outputFiles.add(outputFile);

                logger.debug("Created: {} (page {})", fileName, i);
            }
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
     *
     * @param sourceDoc  The source PDF document
     * @param startPage  Start page (1-based, inclusive)
     * @param endPage    End page (1-based, inclusive)
     * @param outputFile The output file
     * @throws IOException if an error occurs during extraction
     */
    private void extractPages(PDDocument sourceDoc, int startPage, int endPage, File outputFile)
            throws IOException {

        try (PDDocument outputDoc = new PDDocument()) {
            // Convert to 0-based index
            int startIndex = startPage - 1;
            int endIndex = endPage - 1;

            for (int i = startIndex; i <= endIndex; i++) {
                PDPage page = sourceDoc.getPage(i);
                outputDoc.addPage(page);
            }

            outputDoc.save(outputFile);
            logger.debug("Extracted pages {}-{} to: {}", startPage, endPage, outputFile.getName());
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
}

