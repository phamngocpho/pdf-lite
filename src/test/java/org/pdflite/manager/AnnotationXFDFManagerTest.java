package org.pdflite.manager;

import javafx.scene.paint.Color;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.pdflite.model.Annotation;
import org.pdflite.model.AnnotationLineStyle;
import org.pdflite.model.CommentAnnotation;
import org.pdflite.model.FreehandAnnotation;
import org.pdflite.model.PDFDocument;
import org.pdflite.model.RectangleAnnotation;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationXFDFManagerTest {

    @Test
    void exportAndImportRoundTripPreservesCoreProperties() throws Exception {
        AnnotationXFDFManager manager = new AnnotationXFDFManager();
        PDFDocument document = createPdfDocument("xfdf-roundtrip", 2);
        File xfdfFile = Files.createTempFile("xfdf-roundtrip", ".xfdf").toFile();

        try {
            RectangleAnnotation rectangle = new RectangleAnnotation(
                    0, 10, 20, 100, 200, Color.DODGERBLUE, 3.0, AnnotationLineStyle.DASHED, 0.75
            );
            CommentAnnotation comment = new CommentAnnotation(1, 25, 35, "review-note", Color.YELLOW);
            FreehandAnnotation freehand = new FreehandAnnotation(
                    1,
                    List.of(11.0, 15.0, 19.5),
                    List.of(30.0, 41.0, 44.0),
                    Color.FORESTGREEN,
                    2.5,
                    AnnotationLineStyle.DOTTED,
                    0.6
            );
            document.addAnnotation(rectangle);
            document.addAnnotation(comment);
            document.addAnnotation(freehand);

            AnnotationXFDFManager.ExportSummary exportSummary =
                    manager.exportAnnotations(document, xfdfFile, AnnotationXFDFManager.ExportOptions.all());
            assertEquals(3, exportSummary.exportedCount());

            document.getAnnotations().clear();
            AnnotationXFDFManager.ImportSummary importSummary =
                    manager.importAnnotations(document, xfdfFile, AnnotationXFDFManager.ImportOptions.mergeSkipDuplicates());
            assertEquals(3, importSummary.importedCount());
            assertEquals(0, importSummary.invalidCount());

            RectangleAnnotation importedRectangle = find(document.getAnnotations(), RectangleAnnotation.class);
            assertNotNull(importedRectangle);
            assertEquals(10, importedRectangle.getX(), 0.001);
            assertEquals(20, importedRectangle.getY(), 0.001);
            assertEquals(100, importedRectangle.getEndX(), 0.001);
            assertEquals(200, importedRectangle.getEndY(), 0.001);
            assertEquals(3.0, importedRectangle.getLineWidth(), 0.001);
            assertEquals(AnnotationLineStyle.DASHED, importedRectangle.getLineStyle());
            assertEquals(0.75, importedRectangle.getOpacity(), 0.001);

            CommentAnnotation importedComment = find(document.getAnnotations(), CommentAnnotation.class);
            assertNotNull(importedComment);
            assertEquals("review-note", importedComment.getComment());

            FreehandAnnotation importedFreehand = find(document.getAnnotations(), FreehandAnnotation.class);
            assertNotNull(importedFreehand);
            assertEquals(3, importedFreehand.getPointCount());
            assertEquals(AnnotationLineStyle.DOTTED, importedFreehand.getLineStyle());
            assertEquals(0.6, importedFreehand.getOpacity(), 0.001);
        } finally {
            document.getDocument().close();
            if (!xfdfFile.delete()) {
                xfdfFile.deleteOnExit();
            }
        }
    }

    @Test
    void exportSupportsTypeAndPageFiltering() throws Exception {
        AnnotationXFDFManager manager = new AnnotationXFDFManager();
        PDFDocument document = createPdfDocument("xfdf-filter", 3);
        File xfdfFile = Files.createTempFile("xfdf-filter", ".xfdf").toFile();

        try {
            document.addAnnotation(new CommentAnnotation(1, 11, 12, "keep", Color.YELLOW));
            document.addAnnotation(new CommentAnnotation(2, 11, 12, "skip-page", Color.YELLOW));
            document.addAnnotation(new RectangleAnnotation(1, 1, 1, 20, 20, Color.BLUE, 1.5));

            AnnotationXFDFManager.ExportOptions options =
                    new AnnotationXFDFManager.ExportOptions(Set.of("COMMENT"), Set.of(1));

            AnnotationXFDFManager.ExportSummary summary = manager.exportAnnotations(document, xfdfFile, options);
            assertEquals(1, summary.exportedCount());

            document.getAnnotations().clear();
            AnnotationXFDFManager.ImportSummary importSummary =
                    manager.importAnnotations(document, xfdfFile, AnnotationXFDFManager.ImportOptions.mergeSkipDuplicates());
            assertEquals(1, importSummary.importedCount());
        } finally {
            document.getDocument().close();
            if (!xfdfFile.delete()) {
                xfdfFile.deleteOnExit();
            }
        }
    }

    @Test
    void importCanMergeAndSkipDuplicates() throws Exception {
        AnnotationXFDFManager manager = new AnnotationXFDFManager();
        PDFDocument target = createPdfDocument("xfdf-merge-target", 2);
        PDFDocument source = createPdfDocument("xfdf-merge-source", 2);
        File xfdfFile = Files.createTempFile("xfdf-merge", ".xfdf").toFile();

        try {
            CommentAnnotation existing = new CommentAnnotation(0, 10, 20, "same", Color.YELLOW);
            target.addAnnotation(existing);

            source.addAnnotation(new CommentAnnotation(0, 10, 20, "same", Color.YELLOW));
            source.addAnnotation(new CommentAnnotation(1, 30, 40, "new", Color.YELLOW));
            manager.exportAnnotations(source, xfdfFile, AnnotationXFDFManager.ExportOptions.all());

            AnnotationXFDFManager.ImportSummary summary = manager.importAnnotations(
                    target,
                    xfdfFile,
                    new AnnotationXFDFManager.ImportOptions(false, true)
            );

            assertEquals(1, summary.importedCount());
            assertEquals(1, summary.duplicateCount());
            assertEquals(2, target.getAnnotations().size());
        } finally {
            target.getDocument().close();
            source.getDocument().close();
            if (!xfdfFile.delete()) {
                xfdfFile.deleteOnExit();
            }
        }
    }

    private PDFDocument createPdfDocument(String prefix, int pages) throws Exception {
        Path file = Files.createTempFile(prefix, ".pdf");
        PDDocument pd = new PDDocument();
        for (int i = 0; i < pages; i++) {
            pd.addPage(new PDPage());
        }
        return new PDFDocument(pd, file.toFile());
    }

    private <T extends Annotation> T find(List<Annotation> annotations, Class<T> type) {
        for (Annotation annotation : annotations) {
            if (type.isInstance(annotation)) {
                return type.cast(annotation);
            }
        }
        return null;
    }
}
