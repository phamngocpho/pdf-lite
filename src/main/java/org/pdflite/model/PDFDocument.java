package org.pdflite.model;

import org.apache.pdfbox.pdmodel.PDDocument;
import javafx.scene.image.Image;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Model class representing a PDF document
 */
public class PDFDocument {
    private final PDDocument document;
    private final File file;
    private int currentPage;
    private double zoomLevel;
    private int rotation;
    private final List<Annotation> annotations;
    private final Map<String, Image> imageCache;
    private static final int MAX_CACHE_SIZE = 20; // Cache up to 20 pages

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

    public PDDocument getDocument() {
        return document;
    }

    public File getFile() {
        return file;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        if (currentPage >= 0 && currentPage < getTotalPages()) {
            this.currentPage = currentPage;
        }
    }

    public int getTotalPages() {
        return document.getNumberOfPages();
    }

    public double getZoomLevel() {
        return zoomLevel;
    }

    public void setZoomLevel(double zoomLevel) {
        double newZoom = Math.max(0.1, Math.min(5.0, zoomLevel));
        if (Math.abs(this.zoomLevel - newZoom) > 0.001) {
            this.zoomLevel = newZoom;
            clearCache(); // Clear cache when zoom changes
        }
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation % 360;
    }

    public List<Annotation> getAnnotations() {
        return annotations;
    }

    public void addAnnotation(Annotation annotation) {
        this.annotations.add(annotation);
    }

    public String getFileName() {
        return file != null ? file.getName() : "Untitled";
    }

    public Image getCachedImage(int pageIndex, float zoom) {
        String key = pageIndex + "_" + zoom;
        return imageCache.get(key);
    }

    public void cacheImage(int pageIndex, float zoom, Image image) {
        String key = pageIndex + "_" + zoom;
        imageCache.put(key, image);
    }

    public void clearCache() {
        imageCache.clear();
    }
}
