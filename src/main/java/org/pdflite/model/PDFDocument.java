package org.pdflite.model;

import org.apache.pdfbox.pdmodel.PDDocument;
import javafx.scene.image.Image;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Model class representing a PDF document with viewing state and caching.
 * <p>
 * This class wraps an Apache PDFBox {@link PDDocument} and provides additional
 * functionality for PDF viewing, including:
 * <ul>
 *   <li>Page navigation (current page tracking)</li>
 *   <li>Zoom level management</li>
 *   <li>Page rotation</li>
 *   <li>Annotation storage</li>
 *   <li>LRU caching of rendered page images</li>
 * </ul>
 * </p>
 * <p>
 * The image cache uses the Least Recently Used (LRU) eviction policy to limit
 * memory usage while improving performance for frequently accessed pages.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @see org.apache.pdfbox.pdmodel.PDDocument
 * @see Annotation
 * @since 1.0.0
 */
public class PDFDocument {
    private PDDocument document;
    private final File file;
    private int currentPage;
    private double zoomLevel;
    private int rotation;
    private final List<Annotation> annotations;
    private final Map<String, Image> imageCache;

    /**
     * Maximum number of pages to keep in the image cache.
     * <p>
     * When this limit is exceeded, the least recently used images are evicted.
     * </p>
     */
    private static final int MAX_CACHE_SIZE = 20; // Cache up to 20 pages

    /**
     * Creates a new PDFDocument wrapper for the given PDFBox document.
     * <p>
     * Initializes the document with default viewing settings:
     * <ul>
     *   <li>Current page: 0 (first page)</li>
     *   <li>Zoom level: 1.0 (100%)</li>
     *   <li>Rotation: 0 degrees</li>
     *   <li>Empty annotations list</li>
     *   <li>Empty LRU image cache</li>
     * </ul>
     * </p>
     *
     * @param document the Apache PDFBox document to wrap
     * @param file     the source file of the PDF document
     * @throws NullPointerException if a document or file is null
     */
    public PDFDocument(PDDocument document, File file) {
        this.document = document;
        this.file = file;
        this.currentPage = 0;
        this.zoomLevel = 1.0;
        this.rotation = 0;
        this.annotations = new ArrayList<>();
        // Use LinkedHashMap with access order for LRU cache
        this.imageCache = new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        };
    }

    /**
     * Gets the underlying Apache PDFBox document.
     *
     * @return the PDDocument instance
     */
    public PDDocument getDocument() {
        return document;
    }

    /**
     * Gets the source file of this PDF document.
     *
     * @return the File object representing the PDF file
     */
    public File getFile() {
        return file;
    }

    /**
     * Gets the current page number (zero-based).
     *
     * @return the current page index (0 for first page)
     */
    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * Sets the current page number.
     * <p>
     * The page number is validated to ensure it's within valid bounds.
     * Invalid page numbers are silently ignored.
     * </p>
     *
     * @param currentPage the page index to set (zero-based)
     */
    public void setCurrentPage(int currentPage) {
        if (currentPage >= 0 && currentPage < getTotalPages()) {
            this.currentPage = currentPage;
        }
    }

    /**
     * Gets the total number of pages in the document.
     *
     * @return the total page count
     */
    public int getTotalPages() {
        return document.getNumberOfPages();
    }

    /**
     * Gets the current zoom level.
     *
     * @return the zoom level as a multiplier (1.0 = 100%)
     */
    public double getZoomLevel() {
        return zoomLevel;
    }

    /**
     * Sets the zoom level for the document.
     * <p>
     * The zoom level is automatically clamped between 0.1 (10%) and 5.0 (500%).
     * When the zoom level changes, the image cache is cleared to force re-rendering
     * of pages at the new zoom level.
     * </p>
     *
     * @param zoomLevel the new zoom level as a multiplier (1.0 = 100%)
     */
    public void setZoomLevel(double zoomLevel) {
        double newZoom = Math.max(0.1, Math.min(5.0, zoomLevel));
        if (Math.abs(this.zoomLevel - newZoom) > 0.001) {
            this.zoomLevel = newZoom;
            clearCache(); // Clear cache when zoom changes
        }
    }

    /**
     * Gets the current rotation angle.
     *
     * @return the rotation angle in degrees (0, 90, 180, or 270)
     */
    public int getRotation() {
        return rotation;
    }

    /**
     * Sets the rotation angle for the document.
     * <p>
     * The rotation is automatically normalized to be within 0-359 degrees.
     * </p>
     *
     * @param rotation the rotation angle in degrees
     */
    public void setRotation(int rotation) {
        int newRotation = rotation % 360;
        if (newRotation < 0) newRotation += 360;

        if (this.rotation != newRotation) {
            this.rotation = newRotation;
            clearCache();
        }
    }

    /**
     * Gets the list of annotations associated with this document.
     *
     * @return a mutable list of annotations
     */
    public List<Annotation> getAnnotations() {
        return annotations;
    }

    /**
     * Adds an annotation to the document.
     *
     * @param annotation the annotation to add
     */
    public void addAnnotation(Annotation annotation) {
        this.annotations.add(annotation);
    }

    /**
     * Gets all annotations for a specific page.
     * <p>
     * This method filters the document's annotations and returns only those
     * that belong to the specified page index.
     * </p>
     *
     * @param pageIndex the zero-based page index
     * @return a new list containing annotations for the specified page, never null
     */
    public List<Annotation> getAnnotationsForPage(int pageIndex) {
        List<Annotation> pageAnns = new ArrayList<>();
        for (Annotation a : annotations) {
            if (a.getPageNumber() == pageIndex) {
                pageAnns.add(a);
            }
        }
        return pageAnns;
    }

    /**
     * Gets the filename of the PDF document.
     *
     * @return the filename, or "Untitled" if the file is null
     */
    public String getFileName() {
        return file != null ? file.getName() : "Untitled";
    }

    /**
     * Retrieves a cached rendered image for the specified page and zoom level.
     * <p>
     * This method uses an LRU cache, so accessing a cached image marks it as
     * recently used and prevents it from being evicted.
     * </p>
     *
     * @param pageIndex the zero-based page index
     * @param zoom      the zoom level as a multiplier
     * @return the cached Image, or null if not cached
     */
    public Image getCachedImage(int pageIndex, float zoom) {
        String key = pageIndex + "_" + zoom;
        return imageCache.get(key);
    }

    /**
     * Stores a rendered image in the cache.
     * <p>
     * If the cache is full, the least recently used entry is automatically evicted.
     * </p>
     *
     * @param pageIndex the zero-based page index
     * @param zoom      the zoom level as a multiplier
     * @param image     the rendered image to cache
     */
    public void cacheImage(int pageIndex, float zoom, Image image) {
        String key = pageIndex + "_" + zoom;
        imageCache.put(key, image);
    }

    /**
     * Clears all cached images.
     * <p>
     * This should be called when the zoom level changes or when memory
     * needs to be freed.
     * </p>
     */
    public void clearCache() {
        imageCache.clear();
    }

    public void updateDocument(PDDocument newDocument) {
        this.document = newDocument;
        clearCache();
    }

}
