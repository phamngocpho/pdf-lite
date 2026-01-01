package org.pdflite.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Service for performing OCR (Optical Character Recognition) on PDF pages.
 * Uses Tesseract OCR engine via Tess4J wrapper.
 */
public class OCRService {

    private static final Logger logger = LoggerFactory.getLogger(OCRService.class);
    
    private final Tesseract tesseract;
    private final ExecutorService executor;
    private String tessDataPath;
    private boolean isInitialized = false;

    public OCRService() {
        this.tesseract = new Tesseract();
        this.executor = Executors.newFixedThreadPool(2);
        initializeTesseract();
    }

    private void initializeTesseract() {
        try {
            // Try to find tessdata in common locations
            tessDataPath = findTessDataPath();
            if (tessDataPath != null) {
                tesseract.setDatapath(tessDataPath);
                tesseract.setLanguage("eng+vie"); // Support English and Vietnamese
                tesseract.setPageSegMode(1); // Automatic page segmentation with OSD
                tesseract.setOcrEngineMode(1); // LSTM only
                isInitialized = true;
                logger.info("Tesseract initialized with datapath: {}", tessDataPath);
            } else {
                logger.warn("Tessdata not found. OCR will not be available.");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize Tesseract", e);
        }
    }

    private String findTessDataPath() {
        List<String> possiblePaths = new ArrayList<>();
        
        // Check environment variable first
        String envPath = System.getenv("TESSDATA_PREFIX");
        if (envPath != null) {
            possiblePaths.add(envPath);
        }

        // Dynamically find all drive letters on Windows
        File[] roots = File.listRoots();
        for (File root : roots) {
            String drive = root.getAbsolutePath().replace("\\", "/");
            possiblePaths.add(drive + "Program Files/Tesseract-OCR/tessdata");
            possiblePaths.add(drive + "Program Files (x86)/Tesseract-OCR/tessdata");
            possiblePaths.add(drive + "Tesseract-OCR/tessdata");
        }
        
        // User home paths
        possiblePaths.add(System.getProperty("user.home") + "/AppData/Local/Tesseract-OCR/tessdata");
        possiblePaths.add(System.getProperty("user.home") + "/Tesseract-OCR/tessdata");
        
        // Common Linux/Mac paths
        possiblePaths.add("/usr/share/tesseract-ocr/4.00/tessdata");
        possiblePaths.add("/usr/share/tesseract-ocr/5/tessdata");
        possiblePaths.add("/usr/local/share/tessdata");
        possiblePaths.add("/opt/homebrew/share/tessdata");
        
        // Application directory
        possiblePaths.add("./tessdata");
        possiblePaths.add("../tessdata");

        for (String path : possiblePaths) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                // Check if eng.traineddata exists
                File engData = new File(dir, "eng.traineddata");
                if (engData.exists()) {
                    return path;
                }
            }
        }
        return null;
    }

    public boolean isAvailable() {
        return isInitialized;
    }

    public String getTessDataPath() {
        return tessDataPath;
    }

    /**
     * Manually sets the tessdata path and reinitializes Tesseract.
     * @param path Path to tessdata folder
     */
    public void setTessDataPath(String path) {
        this.tessDataPath = path;
        try {
            tesseract.setDatapath(path);
            tesseract.setLanguage("eng+vie");
            tesseract.setPageSegMode(1);
            tesseract.setOcrEngineMode(1);
            isInitialized = true;
            logger.info("Tesseract manually configured with datapath: {}", path);
        } catch (Exception e) {
            logger.error("Failed to set Tesseract datapath", e);
            isInitialized = false;
        }
    }

    /**
     * Sets the OCR language(s).
     * @param language Language code (e.g., "eng", "vie", "eng+vie")
     */
    public void setLanguage(String language) {
        tesseract.setLanguage(language);
    }

    /**
     * Performs OCR on a single page.
     */
    public String recognizePage(PDDocument document, int pageIndex, int dpi) throws IOException, TesseractException {
        if (!isInitialized) {
            throw new IllegalStateException("Tesseract is not initialized. Please install Tesseract OCR.");
        }

        PDFRenderer renderer = new PDFRenderer(document);
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi);
        return tesseract.doOCR(image);
    }

    /**
     * Performs OCR on multiple pages asynchronously.
     */
    public CompletableFuture<List<String>> recognizePagesAsync(
            PDDocument document, 
            List<Integer> pageIndices, 
            int dpi,
            Consumer<Integer> progressCallback) {
        
        return CompletableFuture.supplyAsync(() -> {
            List<String> results = new ArrayList<>();
            PDFRenderer renderer = new PDFRenderer(document);
            
            for (int i = 0; i < pageIndices.size(); i++) {
                int pageIndex = pageIndices.get(i);
                try {
                    BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi);
                    String text = tesseract.doOCR(image);
                    results.add(text);
                    
                    if (progressCallback != null) {
                        progressCallback.accept(i + 1);
                    }
                } catch (Exception e) {
                    logger.error("OCR failed for page {}", pageIndex, e);
                    results.add("[OCR Error: " + e.getMessage() + "]");
                }
            }
            return results;
        }, executor);
    }

    /**
     * Performs OCR on all pages of a document.
     */
    public CompletableFuture<String> recognizeAllPagesAsync(
            PDDocument document, 
            int dpi,
            Consumer<Integer> progressCallback) {
        
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder fullText = new StringBuilder();
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();
            
            for (int i = 0; i < totalPages; i++) {
                try {
                    BufferedImage image = renderer.renderImageWithDPI(i, dpi);
                    String text = tesseract.doOCR(image);
                    fullText.append("--- Page ").append(i + 1).append(" ---\n");
                    fullText.append(text).append("\n\n");
                    
                    if (progressCallback != null) {
                        progressCallback.accept(i + 1);
                    }
                } catch (Exception e) {
                    logger.error("OCR failed for page {}", i, e);
                    fullText.append("--- Page ").append(i + 1).append(" ---\n");
                    fullText.append("[OCR Error: ").append(e.getMessage()).append("]\n\n");
                }
            }
            return fullText.toString();
        }, executor);
    }

    /**
     * Exports OCR result to a text file.
     */
    public void exportToTextFile(String text, File outputFile) throws IOException {
        Files.writeString(outputFile.toPath(), text);
        logger.info("OCR result exported to: {}", outputFile.getAbsolutePath());
    }

    public void shutdown() {
        executor.shutdown();
    }
}
