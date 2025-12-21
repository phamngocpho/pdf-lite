package org.pdflite.manager;

import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages PDF document metadata operations.
 * Handles reading and writing document properties like title, author, subject, keywords, etc.
 */
public class MetadataManager {
    private static final Logger logger = LoggerFactory.getLogger(MetadataManager.class);

    /**
     * Retrieves all metadata from a PDF document.
     *
     * @param pdfDocument the PDF document
     * @return map of metadata key-value pairs
     */
    public Map<String, String> getMetadata(PDFDocument pdfDocument) {
        Map<String, String> metadata = new HashMap<>();

        if (pdfDocument == null || pdfDocument.getDocument() == null) {
            logger.warn("Cannot get metadata: document is null");
            return metadata;
        }

        try {
            PDDocumentInformation info = pdfDocument.getDocument().getDocumentInformation();

            metadata.put("title", info.getTitle() != null ? info.getTitle() : "");
            metadata.put("author", info.getAuthor() != null ? info.getAuthor() : "");
            metadata.put("subject", info.getSubject() != null ? info.getSubject() : "");
            metadata.put("keywords", info.getKeywords() != null ? info.getKeywords() : "");
            metadata.put("creator", info.getCreator() != null ? info.getCreator() : "");
            metadata.put("producer", info.getProducer() != null ? info.getProducer() : "");

            // Format dates
            if (info.getCreationDate() != null) {
                metadata.put("creationDate", formatDate(info.getCreationDate()));
            } else {
                metadata.put("creationDate", "");
            }

            if (info.getModificationDate() != null) {
                metadata.put("modificationDate", formatDate(info.getModificationDate()));
            } else {
                metadata.put("modificationDate", "");
            }

            logger.debug("Retrieved metadata for document");
        } catch (Exception e) {
            logger.error("Error retrieving metadata", e);
        }

        return metadata;
    }

    /**
     * Updates metadata in a PDF document.
     *
     * @param pdfDocument the PDF document
     * @param metadata    map of metadata key-value pairs to update
     * @return true if successful, false otherwise
     */
    public boolean updateMetadata(PDFDocument pdfDocument, Map<String, String> metadata) {
        if (pdfDocument == null || pdfDocument.getDocument() == null) {
            logger.warn("Cannot update metadata: document is null");
            return false;
        }

        try {
            PDDocumentInformation info = pdfDocument.getDocument().getDocumentInformation();

            // Update editable fields
            if (metadata.containsKey("title")) {
                info.setTitle(metadata.get("title"));
            }
            if (metadata.containsKey("author")) {
                info.setAuthor(metadata.get("author"));
            }
            if (metadata.containsKey("subject")) {
                info.setSubject(metadata.get("subject"));
            }
            if (metadata.containsKey("keywords")) {
                info.setKeywords(metadata.get("keywords"));
            }
            if (metadata.containsKey("creator")) {
                info.setCreator(metadata.get("creator"));
            }

            // Update modification date to now
            info.setModificationDate(Calendar.getInstance());

            pdfDocument.getDocument().setDocumentInformation(info);
            pdfDocument.setHasUnsavedEdits(true);

            logger.info("Updated document metadata");
            return true;
        } catch (Exception e) {
            logger.error("Error updating metadata", e);
            return false;
        }
    }

    /**
     * Formats a Calendar date to a readable string.
     *
     * @param calendar the calendar date
     * @return formatted date string
     */
    private String formatDate(Calendar calendar) {
        if (calendar == null) {
            return "";
        }
        return String.format("%tF %<tT", calendar);
    }
}
