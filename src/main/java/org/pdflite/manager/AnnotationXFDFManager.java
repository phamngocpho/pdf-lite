package org.pdflite.manager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.scene.paint.Color;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.fdf.*;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.pdflite.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class AnnotationXFDFManager {
    private static final Logger logger = LoggerFactory.getLogger(AnnotationXFDFManager.class);
    private static final String PAYLOAD_MARKER = "PDFLITE_ANNOTATIONS:";
    private static final Type PAYLOAD_LIST_TYPE = new TypeToken<List<AnnotationPayload>>() {}.getType();
    private final Gson gson = new Gson();

    private static final String TYPE_HIGHLIGHT = "HIGHLIGHT";
    private static final String TYPE_COMMENT = "COMMENT";
    private static final String TYPE_RECTANGLE = "RECTANGLE";
    private static final String TYPE_CIRCLE = "CIRCLE";
    private static final String TYPE_ARROW = "ARROW";
    private static final String TYPE_FREEHAND = "FREEHAND";

    private static final String META_PREFIX = "PL_";
    private static final COSName META_TYPE = COSName.getPDFName(META_PREFIX + "TYPE");
    private static final COSName META_X = COSName.getPDFName(META_PREFIX + "X");
    private static final COSName META_Y = COSName.getPDFName(META_PREFIX + "Y");
    private static final COSName META_W = COSName.getPDFName(META_PREFIX + "W");
    private static final COSName META_H = COSName.getPDFName(META_PREFIX + "H");
    private static final COSName META_EX = COSName.getPDFName(META_PREFIX + "EX");
    private static final COSName META_EY = COSName.getPDFName(META_PREFIX + "EY");
    private static final COSName META_OPACITY = COSName.getPDFName(META_PREFIX + "OPACITY");
    private static final COSName META_LINE_WIDTH = COSName.getPDFName(META_PREFIX + "LINE_WIDTH");
    private static final COSName META_LINE_STYLE = COSName.getPDFName(META_PREFIX + "LINE_STYLE");
    private static final COSName META_BATCH_ID = COSName.getPDFName(META_PREFIX + "BATCH_ID");

    public record ExportOptions(Set<String> types, Set<Integer> pageIndexes) {
        public ExportOptions {
            types = normalizeTypes(types);
            pageIndexes = pageIndexes == null ? Set.of() : Set.copyOf(pageIndexes);
        }

        public static ExportOptions all() {
            return new ExportOptions(Set.of(), Set.of());
        }
    }

    public record ImportOptions(boolean replaceExisting, boolean skipDuplicates) {
        public static ImportOptions mergeSkipDuplicates() {
            return new ImportOptions(false, true);
        }
    }

    public record ExportSummary(int exportedCount, int skippedCount) {}

    public record ImportSummary(int importedCount, int replacedCount, int duplicateCount, int invalidCount,
                                Set<Integer> affectedPages) {}

    public ExportSummary exportAnnotations(PDFDocument document, File outputFile, ExportOptions options) throws IOException {
        if (document == null) throw new IOException("No active document.");
        if (outputFile == null) throw new IOException("No output file selected.");

        ExportOptions effective = options == null ? ExportOptions.all() : options;
        List<FDFAnnotation> xfdfAnnotations = new ArrayList<>();
        Map<Integer, List<FDFAnnotation>> annotationsByPage = new LinkedHashMap<>();
        List<Annotation> exportedModelAnnotations = new ArrayList<>();
        int skipped = 0;

        for (Annotation annotation : document.getAnnotations()) {
            String type = normalizeType(annotation);
            if (type == null || (!effective.types().isEmpty() && !effective.types().contains(type))
                    || (!effective.pageIndexes().isEmpty() && !effective.pageIndexes().contains(annotation.getPageNumber()))) {
                skipped++;
                continue;
            }
            FDFAnnotation converted = toFdfAnnotation(annotation, type);
            if (converted == null) {
                skipped++;
                continue;
            }
            xfdfAnnotations.add(converted);
            annotationsByPage.computeIfAbsent(annotation.getPageNumber(), ignored -> new ArrayList<>()).add(converted);
            exportedModelAnnotations.add(annotation);
        }

        try (FDFDocument fdfDocument = new FDFDocument()) {
            FDFCatalog catalog = new FDFCatalog();
            FDFDictionary dictionary = new FDFDictionary();
            dictionary.setAnnotations(xfdfAnnotations);
            COSArray rawAnnots = new COSArray();
            for (FDFAnnotation annotation : xfdfAnnotations) {
                rawAnnots.add(annotation.getCOSObject());
            }
            dictionary.getCOSObject().setItem(COSName.ANNOTS, rawAnnots);
            dictionary.setPages(buildPagesWithAnnotations(annotationsByPage));
            catalog.setFDF(dictionary);
            fdfDocument.setCatalog(catalog);
            fdfDocument.saveXFDF(outputFile);
        }
        appendPayload(outputFile, exportedModelAnnotations);

        return new ExportSummary(xfdfAnnotations.size(), skipped);
    }

    public ImportSummary importAnnotations(PDFDocument document, File inputFile, ImportOptions options) throws IOException {
        if (document == null) throw new IOException("No active document.");
        if (inputFile == null) throw new IOException("No XFDF file selected.");

        ImportOptions effective = options == null ? ImportOptions.mergeSkipDuplicates() : options;
        int replaced = 0;
        if (effective.replaceExisting()) {
            replaced = document.getAnnotations().size();
            document.getAnnotations().clear();
        }

        Set<String> existingSignatures = new HashSet<>();
        if (effective.skipDuplicates()) {
            for (Annotation annotation : document.getAnnotations()) {
                existingSignatures.add(annotationSignature(annotation));
            }
        }

        int imported = 0;
        int duplicates = 0;
        int invalid = 0;
        Set<Integer> affectedPages = new LinkedHashSet<>();

        List<Annotation> importedAnnotations = loadModelAnnotationsFromFile(document, inputFile);
        for (Annotation converted : importedAnnotations) {
            if (converted == null || !isValidForDocument(document, converted)) {
                invalid++;
                continue;
            }

            String signature = annotationSignature(converted);
            if (effective.skipDuplicates() && existingSignatures.contains(signature)) {
                duplicates++;
                continue;
            }

            document.addAnnotation(converted);
            existingSignatures.add(signature);
            imported++;
            affectedPages.add(converted.getPageNumber());
        }

        logger.info("XFDF import complete: imported={}, duplicates={}, invalid={}", imported, duplicates, invalid);
        return new ImportSummary(imported, replaced, duplicates, invalid, Set.copyOf(affectedPages));
    }

    private List<FDFAnnotation> loadAnnotationsFromFile(File inputFile) throws IOException {
        List<FDFAnnotation> annotations;
        try (FDFDocument xfdf = Loader.loadXFDF(inputFile)) {
            FDFDictionary dictionary = xfdf.getCatalog() != null ? xfdf.getCatalog().getFDF() : null;
            annotations = extractAnnotations(dictionary);
        }

        if (!annotations.isEmpty()) {
            return annotations;
        }

        try (FDFDocument fdf = Loader.loadFDF(inputFile)) {
            FDFDictionary dictionary = fdf.getCatalog() != null ? fdf.getCatalog().getFDF() : null;
            annotations = extractAnnotations(dictionary);
        } catch (Exception e) {
            logger.debug("Loader.loadFDF fallback failed, keep XFDF/payload path", e);
        }
        return annotations;
    }

    private List<Annotation> loadModelAnnotationsFromFile(PDFDocument document, File inputFile) throws IOException {
        List<Annotation> converted = new ArrayList<>();
        for (FDFAnnotation fdfAnnotation : loadAnnotationsFromFile(inputFile)) {
            Annotation model = toModelAnnotation(document, fdfAnnotation);
            if (model != null) {
                converted.add(model);
            }
        }
        if (!converted.isEmpty()) {
            return converted;
        }
        return loadPayloadAnnotations(inputFile);
    }

    private void appendPayload(File file, List<Annotation> annotations) throws IOException {
        List<AnnotationPayload> payloads = new ArrayList<>();
        for (Annotation annotation : annotations) {
            AnnotationPayload payload = AnnotationPayload.fromAnnotation(annotation, normalizeType(annotation));
            if (payload != null) {
                payloads.add(payload);
            }
        }
        String encoded = Base64.getEncoder().encodeToString(gson.toJson(payloads).getBytes(StandardCharsets.UTF_8));
        String xml = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        String marker = "\n<!-- " + PAYLOAD_MARKER + encoded + " -->\n";
        int insertIndex = xml.lastIndexOf("</xfdf>");
        if (insertIndex >= 0) {
            xml = xml.substring(0, insertIndex) + marker + xml.substring(insertIndex);
        } else {
            xml = xml + marker;
        }
        Files.writeString(file.toPath(), xml, StandardCharsets.UTF_8);
    }

    private List<Annotation> loadPayloadAnnotations(File file) throws IOException {
        String xml = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        int markerStart = xml.indexOf(PAYLOAD_MARKER);
        if (markerStart < 0) {
            return List.of();
        }
        int dataStart = markerStart + PAYLOAD_MARKER.length();
        int markerEnd = xml.indexOf("-->", dataStart);
        if (markerEnd < 0) {
            return List.of();
        }

        String encoded = xml.substring(dataStart, markerEnd).trim();
        if (encoded.isEmpty()) {
            return List.of();
        }

        String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        List<AnnotationPayload> payloads = gson.fromJson(json, PAYLOAD_LIST_TYPE);
        if (payloads == null || payloads.isEmpty()) {
            return List.of();
        }

        List<Annotation> annotations = new ArrayList<>();
        for (AnnotationPayload payload : payloads) {
            Annotation annotation = payload.toAnnotation();
            if (annotation != null) {
                annotations.add(annotation);
            }
        }
        return annotations;
    }

    private List<FDFAnnotation> extractAnnotations(FDFDictionary dictionary) {
        if (dictionary == null) return List.of();

        List<FDFAnnotation> collected = new ArrayList<>();
        try {
            List<FDFAnnotation> parsed = dictionary.getAnnotations();
            if (parsed != null && !parsed.isEmpty()) collected.addAll(parsed);
        } catch (IOException e) {
            logger.debug("Standard XFDF annotation parsing failed, using COS fallback", e);
        }

        if (collected.isEmpty()) {
            COSBase rawAnnots = dictionary.getCOSObject().getDictionaryObject(COSName.ANNOTS);
            if (rawAnnots instanceof COSArray cosArray) {
                collected.addAll(parseCosAnnotationArray(cosArray));
            }
        }

        if (collected.isEmpty()) {
            List<FDFPage> pages = dictionary.getPages();
            if (pages != null) {
                for (FDFPage page : pages) {
                    COSBase rawPageAnnots = page.getCOSObject().getDictionaryObject(COSName.ANNOTS);
                    if (rawPageAnnots instanceof COSArray pageArray) {
                        collected.addAll(parseCosAnnotationArray(pageArray));
                    }
                }
            }
        }
        return collected;
    }

    private List<FDFPage> buildPagesWithAnnotations(Map<Integer, List<FDFAnnotation>> annotationsByPage) {
        if (annotationsByPage.isEmpty()) {
            return List.of();
        }
        List<FDFPage> pages = new ArrayList<>();
        for (Map.Entry<Integer, List<FDFAnnotation>> entry : annotationsByPage.entrySet()) {
            FDFPage page = new FDFPage();
            COSArray annots = new COSArray();
            for (FDFAnnotation annotation : entry.getValue()) {
                annots.add(annotation.getCOSObject());
            }
            page.getCOSObject().setItem(COSName.ANNOTS, annots);
            page.getCOSObject().setInt(COSName.PAGE, entry.getKey());
            pages.add(page);
        }
        return pages;
    }

    private List<FDFAnnotation> parseCosAnnotationArray(COSArray cosArray) {
        List<FDFAnnotation> parsed = new ArrayList<>();
        for (int i = 0; i < cosArray.size(); i++) {
            COSBase item = cosArray.getObject(i);
            if (item instanceof COSDictionary cosDictionary) {
                FDFAnnotation annotation = FDFAnnotation.create(cosDictionary);
                if (annotation != null) parsed.add(annotation);
            }
        }
        return parsed;
    }

    private static Set<String> normalizeTypes(Set<String> types) {
        if (types == null || types.isEmpty()) return Set.of();
        Set<String> normalized = new LinkedHashSet<>();
        for (String type : types) {
            if (type != null && !type.isBlank()) normalized.add(type.trim().toUpperCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }

    private FDFAnnotation toFdfAnnotation(Annotation annotation, String type) {
        try {
            return switch (type) {
                case TYPE_HIGHLIGHT -> toFdfHighlight((HighlightAnnotation) annotation);
                case TYPE_COMMENT -> toFdfComment((CommentAnnotation) annotation);
                case TYPE_RECTANGLE -> toFdfRectangle((RectangleAnnotation) annotation);
                case TYPE_CIRCLE -> toFdfCircle((CircleAnnotation) annotation);
                case TYPE_ARROW -> toFdfArrow((ArrowAnnotation) annotation);
                case TYPE_FREEHAND -> toFdfFreehand((FreehandAnnotation) annotation);
                default -> null;
            };
        } catch (Exception e) {
            logger.warn("Cannot convert annotation type {}", annotation.getType(), e);
            return null;
        }
    }

    private FDFAnnotationHighlight toFdfHighlight(HighlightAnnotation annotation) {
        FDFAnnotationHighlight fdf = new FDFAnnotationHighlight();
        setCommonFields(fdf, annotation, TYPE_HIGHLIGHT);
        fdf.setRectangle(rect(annotation.getX(), annotation.getY(), annotation.getWidth(), annotation.getHeight()));
        fdf.setCoords(new float[]{
                asFloat(annotation.getX()), asFloat(annotation.getY()),
                asFloat(annotation.getX() + annotation.getWidth()), asFloat(annotation.getY()),
                asFloat(annotation.getX()), asFloat(annotation.getY() + annotation.getHeight()),
                asFloat(annotation.getX() + annotation.getWidth()), asFloat(annotation.getY() + annotation.getHeight())
        });
        setMeta(fdf.getCOSObject(), META_W, annotation.getWidth());
        setMeta(fdf.getCOSObject(), META_H, annotation.getHeight());
        setMeta(fdf.getCOSObject(), META_BATCH_ID, annotation.getBatchId());
        return fdf;
    }

    private FDFAnnotationText toFdfComment(CommentAnnotation annotation) {
        FDFAnnotationText fdf = new FDFAnnotationText();
        setCommonFields(fdf, annotation, TYPE_COMMENT);
        fdf.setContents(annotation.getComment());
        fdf.setRectangle(rect(annotation.getX(), annotation.getY(), 24.0, 18.0));
        return fdf;
    }

    private FDFAnnotationSquare toFdfRectangle(RectangleAnnotation annotation) {
        FDFAnnotationSquare fdf = new FDFAnnotationSquare();
        setCommonFields(fdf, annotation, TYPE_RECTANGLE);
        Rectangle2D bounds = shapeBounds(annotation);
        fdf.setRectangle(rect(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()));
        setShapeStyle(fdf, annotation);
        setMeta(fdf.getCOSObject(), META_EX, annotation.getEndX());
        setMeta(fdf.getCOSObject(), META_EY, annotation.getEndY());
        return fdf;
    }

    private FDFAnnotationCircle toFdfCircle(CircleAnnotation annotation) {
        FDFAnnotationCircle fdf = new FDFAnnotationCircle();
        setCommonFields(fdf, annotation, TYPE_CIRCLE);
        Rectangle2D bounds = shapeBounds(annotation);
        fdf.setRectangle(rect(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()));
        setShapeStyle(fdf, annotation);
        setMeta(fdf.getCOSObject(), META_EX, annotation.getEndX());
        setMeta(fdf.getCOSObject(), META_EY, annotation.getEndY());
        return fdf;
    }

    private FDFAnnotationLine toFdfArrow(ArrowAnnotation annotation) {
        FDFAnnotationLine fdf = new FDFAnnotationLine();
        setCommonFields(fdf, annotation, TYPE_ARROW);
        fdf.setLine(new float[]{
                asFloat(annotation.getX()),
                asFloat(annotation.getY()),
                asFloat(annotation.getEndX()),
                asFloat(annotation.getEndY())
        });
        fdf.setStartPointEndingStyle("None");
        fdf.setEndPointEndingStyle("ClosedArrow");
        setShapeStyle(fdf, annotation);
        setMeta(fdf.getCOSObject(), META_EX, annotation.getEndX());
        setMeta(fdf.getCOSObject(), META_EY, annotation.getEndY());
        return fdf;
    }

    private FDFAnnotationInk toFdfFreehand(FreehandAnnotation annotation) {
        FDFAnnotationInk fdf = new FDFAnnotationInk();
        setCommonFields(fdf, annotation, TYPE_FREEHAND);

        List<Double> xs = annotation.getPointsX();
        List<Double> ys = annotation.getPointsY();
        int pointCount = annotation.getPointCount();
        float[] stroke = new float[pointCount * 2];
        for (int i = 0; i < pointCount; i++) {
            stroke[i * 2] = asFloat(xs.get(i));
            stroke[i * 2 + 1] = asFloat(ys.get(i));
        }
        fdf.setInkList(List.of(stroke));
        setShapeBorderStyle(fdf, annotation.getLineWidth(), annotation.getLineStyle());

        Rectangle2D bounds = freehandBounds(xs, ys, pointCount);
        fdf.setRectangle(rect(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()));
        return fdf;
    }

    private void setCommonFields(FDFAnnotation fdf, Annotation annotation, String type) {
        fdf.setPage(annotation.getPageNumber());
        setMeta(fdf.getCOSObject(), META_TYPE, type);
        setMeta(fdf.getCOSObject(), META_X, annotation.getX());
        setMeta(fdf.getCOSObject(), META_Y, annotation.getY());

        if (annotation instanceof HighlightAnnotation highlight) {
            fdf.setColor(toAwtColor(highlight.getColor()));
            fdf.setOpacity((float) highlight.getOpacity());
            setMeta(fdf.getCOSObject(), META_OPACITY, highlight.getOpacity());
            return;
        }
        if (annotation instanceof CommentAnnotation comment) {
            fdf.setColor(toAwtColor(comment.getColor()));
            fdf.setOpacity(1.0f);
            return;
        }
        if (annotation instanceof ShapeAnnotation shape) {
            fdf.setColor(toAwtColor(shape.getColor()));
            fdf.setOpacity((float) shape.getOpacity());
            setMeta(fdf.getCOSObject(), META_OPACITY, shape.getOpacity());
            setMeta(fdf.getCOSObject(), META_LINE_WIDTH, shape.getLineWidth());
            setMeta(fdf.getCOSObject(), META_LINE_STYLE, shape.getLineStyle().name());
            setShapeBorderStyle(fdf, shape.getLineWidth(), shape.getLineStyle());
            return;
        }
        if (annotation instanceof FreehandAnnotation freehand) {
            fdf.setColor(toAwtColor(freehand.getColor()));
            fdf.setOpacity((float) freehand.getOpacity());
            setMeta(fdf.getCOSObject(), META_OPACITY, freehand.getOpacity());
            setMeta(fdf.getCOSObject(), META_LINE_WIDTH, freehand.getLineWidth());
            setMeta(fdf.getCOSObject(), META_LINE_STYLE, freehand.getLineStyle().name());
        }
    }

    private void setShapeStyle(FDFAnnotation fdf, ShapeAnnotation annotation) {
        setShapeBorderStyle(fdf, annotation.getLineWidth(), annotation.getLineStyle());
    }

    private void setShapeBorderStyle(FDFAnnotation fdf, double lineWidth, AnnotationLineStyle lineStyle) {
        PDBorderStyleDictionary border = new PDBorderStyleDictionary();
        border.setWidth((float) lineWidth);
        if (lineStyle == AnnotationLineStyle.SOLID) {
            border.setStyle(PDBorderStyleDictionary.STYLE_SOLID);
        } else {
            border.setStyle(PDBorderStyleDictionary.STYLE_DASHED);
            COSArray pattern = new COSArray();
            double[] dashPattern = lineStyle.getDashPattern(lineWidth, 1.0);
            if (dashPattern.length == 0) {
                pattern.add(new org.apache.pdfbox.cos.COSFloat((float) Math.max(1.0, lineWidth * 2.0)));
                pattern.add(new org.apache.pdfbox.cos.COSFloat((float) Math.max(1.0, lineWidth)));
            } else {
                for (double value : dashPattern) {
                    pattern.add(new org.apache.pdfbox.cos.COSFloat((float) Math.max(0.5, value)));
                }
            }
            border.setDashStyle(pattern);
        }
        fdf.setBorderStyle(border);
    }

    private Annotation toModelAnnotation(PDFDocument document, FDFAnnotation fdfAnnotation) {
        String type = getMeta(fdfAnnotation.getCOSObject(), META_TYPE);
        if (type == null || type.isBlank()) {
            type = inferType(fdfAnnotation);
        }
        if (type == null) return null;

        Integer pageValue = fdfAnnotation.getPage();
        int pageIndex = pageValue == null ? 0 : pageValue;
        if (pageIndex < 0 || pageIndex >= document.getTotalPages()) return null;

        return switch (type) {
            case TYPE_HIGHLIGHT -> fromFdfHighlight(pageIndex, fdfAnnotation);
            case TYPE_COMMENT -> fromFdfComment(pageIndex, fdfAnnotation);
            case TYPE_RECTANGLE -> fromFdfRectangle(pageIndex, fdfAnnotation);
            case TYPE_CIRCLE -> fromFdfCircle(pageIndex, fdfAnnotation);
            case TYPE_ARROW -> fromFdfArrow(pageIndex, fdfAnnotation);
            case TYPE_FREEHAND -> fromFdfFreehand(pageIndex, fdfAnnotation);
            default -> null;
        };
    }

    private String inferType(FDFAnnotation annotation) {
        if (annotation instanceof FDFAnnotationHighlight) return TYPE_HIGHLIGHT;
        if (annotation instanceof FDFAnnotationText) return TYPE_COMMENT;
        if (annotation instanceof FDFAnnotationSquare) return TYPE_RECTANGLE;
        if (annotation instanceof FDFAnnotationCircle) return TYPE_CIRCLE;
        if (annotation instanceof FDFAnnotationLine) return TYPE_ARROW;
        if (annotation instanceof FDFAnnotationInk) return TYPE_FREEHAND;
        return null;
    }

    private Annotation fromFdfHighlight(int pageIndex, FDFAnnotation fdfAnnotation) {
        double x = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_X), rectX(fdfAnnotation), 0.0);
        double y = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_Y), rectY(fdfAnnotation), 0.0);
        double w = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_W), rectW(fdfAnnotation), 0.0);
        double h = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_H), rectH(fdfAnnotation), 0.0);
        double opacity = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_OPACITY), fdfAnnotation.getOpacity(), 0.4);
        String batchId = getMeta(fdfAnnotation.getCOSObject(), META_BATCH_ID);
        Color color = toFxColor(fdfAnnotation.getColor(), Color.YELLOW);
        return new HighlightAnnotation(pageIndex, x, y, w, h, color, opacity, batchId);
    }

    private Annotation fromFdfComment(int pageIndex, FDFAnnotation fdfAnnotation) {
        double x = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_X), rectX(fdfAnnotation), 0.0);
        double y = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_Y), rectY(fdfAnnotation), 0.0);
        String comment = fdfAnnotation.getContents() == null ? "" : fdfAnnotation.getContents();
        Color color = toFxColor(fdfAnnotation.getColor(), Color.YELLOW);
        return new CommentAnnotation(pageIndex, x, y, comment, color);
    }

    private Annotation fromFdfRectangle(int pageIndex, FDFAnnotation fdfAnnotation) {
        double startX = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_X), rectX(fdfAnnotation), 0.0);
        double startY = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_Y), rectY(fdfAnnotation), 0.0);
        double endX = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_EX), startX + rectW(fdfAnnotation), startX);
        double endY = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_EY), startY + rectH(fdfAnnotation), startY);
        double lineWidth = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_LINE_WIDTH), borderWidth(fdfAnnotation), 2.0);
        double opacity = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_OPACITY), fdfAnnotation.getOpacity(), 1.0);
        AnnotationLineStyle lineStyle = parseLineStyle(getMeta(fdfAnnotation.getCOSObject(), META_LINE_STYLE));
        Color color = toFxColor(fdfAnnotation.getColor(), Color.WHITE);
        return new RectangleAnnotation(pageIndex, startX, startY, endX, endY, color, lineWidth, lineStyle, opacity);
    }

    private Annotation fromFdfCircle(int pageIndex, FDFAnnotation fdfAnnotation) {
        double startX = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_X), rectX(fdfAnnotation), 0.0);
        double startY = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_Y), rectY(fdfAnnotation), 0.0);
        double endX = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_EX), startX + rectW(fdfAnnotation), startX);
        double endY = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_EY), startY + rectH(fdfAnnotation), startY);
        double lineWidth = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_LINE_WIDTH), borderWidth(fdfAnnotation), 2.0);
        double opacity = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_OPACITY), fdfAnnotation.getOpacity(), 1.0);
        AnnotationLineStyle lineStyle = parseLineStyle(getMeta(fdfAnnotation.getCOSObject(), META_LINE_STYLE));
        Color color = toFxColor(fdfAnnotation.getColor(), Color.WHITE);
        return new CircleAnnotation(pageIndex, startX, startY, endX, endY, color, lineWidth, lineStyle, opacity);
    }

    private Annotation fromFdfArrow(int pageIndex, FDFAnnotation fdfAnnotation) {
        float[] line = fdfAnnotation instanceof FDFAnnotationLine fdfLine ? fdfLine.getLine() : null;
        double startX = line != null && line.length >= 4 ? line[0] : firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_X), 0.0, 0.0);
        double startY = line != null && line.length >= 4 ? line[1] : firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_Y), 0.0, 0.0);
        double endX = line != null && line.length >= 4 ? line[2] : firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_EX), startX, startX);
        double endY = line != null && line.length >= 4 ? line[3] : firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_EY), startY, startY);
        double lineWidth = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_LINE_WIDTH), borderWidth(fdfAnnotation), 2.0);
        double opacity = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_OPACITY), fdfAnnotation.getOpacity(), 1.0);
        AnnotationLineStyle lineStyle = parseLineStyle(getMeta(fdfAnnotation.getCOSObject(), META_LINE_STYLE));
        Color color = toFxColor(fdfAnnotation.getColor(), Color.WHITE);
        return new ArrowAnnotation(pageIndex, startX, startY, endX, endY, color, lineWidth, lineStyle, opacity);
    }

    private Annotation fromFdfFreehand(int pageIndex, FDFAnnotation fdfAnnotation) {
        if (!(fdfAnnotation instanceof FDFAnnotationInk fdfInk)) return null;
        List<float[]> ink = fdfInk.getInkList();
        if (ink == null || ink.isEmpty()) return null;

        List<Double> pointsX = new ArrayList<>();
        List<Double> pointsY = new ArrayList<>();
        for (float[] stroke : ink) {
            if (stroke == null || stroke.length < 4) continue;
            for (int i = 0; i + 1 < stroke.length; i += 2) {
                pointsX.add((double) stroke[i]);
                pointsY.add((double) stroke[i + 1]);
            }
        }
        if (pointsX.size() < 2) return null;

        double lineWidth = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_LINE_WIDTH), borderWidth(fdfAnnotation), 2.0);
        double opacity = firstNumber(getMeta(fdfAnnotation.getCOSObject(), META_OPACITY), fdfAnnotation.getOpacity(), 1.0);
        AnnotationLineStyle lineStyle = parseLineStyle(getMeta(fdfAnnotation.getCOSObject(), META_LINE_STYLE));
        Color color = toFxColor(fdfAnnotation.getColor(), Color.WHITE);
        return new FreehandAnnotation(pageIndex, pointsX, pointsY, color, lineWidth, lineStyle, opacity);
    }

    private String normalizeType(Annotation annotation) {
        if (annotation instanceof HighlightAnnotation) return TYPE_HIGHLIGHT;
        if (annotation instanceof CommentAnnotation) return TYPE_COMMENT;
        if (annotation instanceof RectangleAnnotation) return TYPE_RECTANGLE;
        if (annotation instanceof CircleAnnotation) return TYPE_CIRCLE;
        if (annotation instanceof ArrowAnnotation) return TYPE_ARROW;
        if (annotation instanceof FreehandAnnotation) return TYPE_FREEHAND;
        return null;
    }

    private boolean isValidForDocument(PDFDocument document, Annotation annotation) {
        if (annotation == null) return false;
        if (annotation.getPageNumber() < 0 || annotation.getPageNumber() >= document.getTotalPages()) return false;
        if (!isFinite(annotation.getX()) || !isFinite(annotation.getY())) return false;

        if (annotation instanceof HighlightAnnotation highlight) {
            return isFinite(highlight.getWidth()) && isFinite(highlight.getHeight())
                    && highlight.getWidth() > 0 && highlight.getHeight() > 0;
        }
        if (annotation instanceof ShapeAnnotation shape) {
            return isFinite(shape.getEndX()) && isFinite(shape.getEndY()) && shape.getLineWidth() > 0;
        }
        if (annotation instanceof FreehandAnnotation freehand) {
            return freehand.getPointCount() >= 2 && freehand.getLineWidth() > 0;
        }
        return true;
    }

    private String annotationSignature(Annotation annotation) {
        if (annotation instanceof HighlightAnnotation highlight) {
            return "H|" + highlight.getPageNumber() + "|" + coordinate(highlight.getX()) + "|" + coordinate(highlight.getY())
                    + "|" + coordinate(highlight.getWidth()) + "|" + coordinate(highlight.getHeight())
                    + "|" + colorSignature(highlight.getColor()) + "|" + coordinate(highlight.getOpacity());
        }
        if (annotation instanceof CommentAnnotation comment) {
            return "C|" + comment.getPageNumber() + "|" + coordinate(comment.getX()) + "|" + coordinate(comment.getY())
                    + "|" + safe(comment.getComment());
        }
        if (annotation instanceof ShapeAnnotation shape) {
            return "S|" + normalizeType(shape) + "|" + shape.getPageNumber() + "|" + coordinate(shape.getX()) + "|"
                    + coordinate(shape.getY()) + "|" + coordinate(shape.getEndX()) + "|" + coordinate(shape.getEndY())
                    + "|" + coordinate(shape.getLineWidth()) + "|" + shape.getLineStyle().name() + "|"
                    + colorSignature(shape.getColor()) + "|" + coordinate(shape.getOpacity());
        }
        if (annotation instanceof FreehandAnnotation freehand) {
            StringBuilder builder = new StringBuilder("F|").append(freehand.getPageNumber()).append("|")
                    .append(coordinate(freehand.getLineWidth())).append("|")
                    .append(freehand.getLineStyle().name()).append("|")
                    .append(colorSignature(freehand.getColor())).append("|")
                    .append(coordinate(freehand.getOpacity()));
            List<Double> xs = freehand.getPointsX();
            List<Double> ys = freehand.getPointsY();
            int pointCount = freehand.getPointCount();
            for (int i = 0; i < pointCount; i++) {
                builder.append("|").append(coordinate(xs.get(i))).append(",").append(coordinate(ys.get(i)));
            }
            return builder.toString();
        }
        return annotation.getType() + "|" + annotation.getPageNumber();
    }

    private String colorSignature(Color color) {
        return coordinate(color.getRed()) + ":" + coordinate(color.getGreen()) + ":" + coordinate(color.getBlue()) + ":"
                + coordinate(color.getOpacity());
    }

    private String coordinate(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private String safe(String text) {
        return text == null ? "" : text.trim();
    }

    private void setMeta(COSDictionary dictionary, COSName key, Object value) {
        if (dictionary == null || key == null || value == null) return;
        dictionary.setString(key, String.valueOf(value));
    }

    private String getMeta(COSDictionary dictionary, COSName key) {
        if (dictionary == null || key == null) return null;
        return dictionary.getString(key);
    }

    private double firstNumber(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate == null) continue;
            if (candidate instanceof Number number && isFinite(number.doubleValue())) {
                return number.doubleValue();
            }
            if (candidate instanceof String text) {
                try {
                    double value = Double.parseDouble(text.trim());
                    if (isFinite(value)) return value;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0.0;
    }

    private boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private PDRectangle rect(double x, double y, double w, double h) {
        return new PDRectangle(asFloat(x), asFloat(y), asFloat(w), asFloat(h));
    }

    private float asFloat(double value) {
        return (float) value;
    }

    private Rectangle2D shapeBounds(ShapeAnnotation shape) {
        double x = Math.min(shape.getX(), shape.getEndX());
        double y = Math.min(shape.getY(), shape.getEndY());
        double w = Math.abs(shape.getEndX() - shape.getX());
        double h = Math.abs(shape.getEndY() - shape.getY());
        return new Rectangle2D.Double(x, y, w, h);
    }

    private Rectangle2D freehandBounds(List<Double> pointsX, List<Double> pointsY, int pointCount) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < pointCount; i++) {
            minX = Math.min(minX, pointsX.get(i));
            minY = Math.min(minY, pointsY.get(i));
            maxX = Math.max(maxX, pointsX.get(i));
            maxY = Math.max(maxY, pointsY.get(i));
        }
        return new Rectangle2D.Double(minX, minY, Math.max(0.01, maxX - minX), Math.max(0.01, maxY - minY));
    }

    private java.awt.Color toAwtColor(Color color) {
        Color safe = color == null ? Color.WHITE : color;
        return new java.awt.Color((float) safe.getRed(), (float) safe.getGreen(), (float) safe.getBlue());
    }

    private Color toFxColor(java.awt.Color color, Color fallback) {
        if (color == null) return fallback;
        return Color.rgb(color.getRed(), color.getGreen(), color.getBlue());
    }

    private double rectX(FDFAnnotation annotation) {
        return annotation.getRectangle() == null ? 0.0 : annotation.getRectangle().getLowerLeftX();
    }

    private double rectY(FDFAnnotation annotation) {
        return annotation.getRectangle() == null ? 0.0 : annotation.getRectangle().getLowerLeftY();
    }

    private double rectW(FDFAnnotation annotation) {
        return annotation.getRectangle() == null ? 0.0 : annotation.getRectangle().getWidth();
    }

    private double rectH(FDFAnnotation annotation) {
        return annotation.getRectangle() == null ? 0.0 : annotation.getRectangle().getHeight();
    }

    private double borderWidth(FDFAnnotation annotation) {
        PDBorderStyleDictionary border = annotation.getBorderStyle();
        return border == null ? 2.0 : border.getWidth();
    }

    private AnnotationLineStyle parseLineStyle(String text) {
        return AnnotationLineStyle.fromString(text);
    }

    private static final class AnnotationPayload {
        private String type;
        private int page;
        private double x;
        private double y;
        private double w;
        private double h;
        private double ex;
        private double ey;
        private String color;
        private double opacity;
        private double lineWidth;
        private String lineStyle;
        private String comment;
        private String batchId;
        private List<Double> pointsX;
        private List<Double> pointsY;

        private static AnnotationPayload fromAnnotation(Annotation annotation, String mappedType) {
            if (mappedType == null) {
                return null;
            }
            AnnotationPayload payload = new AnnotationPayload();
            payload.type = mappedType;
            payload.page = annotation.getPageNumber();
            payload.x = annotation.getX();
            payload.y = annotation.getY();

            if (annotation instanceof HighlightAnnotation highlight) {
                payload.w = highlight.getWidth();
                payload.h = highlight.getHeight();
                payload.color = highlight.getColor().toString();
                payload.opacity = highlight.getOpacity();
                payload.batchId = highlight.getBatchId();
                return payload;
            }
            if (annotation instanceof CommentAnnotation comment) {
                payload.color = comment.getColor().toString();
                payload.comment = comment.getComment();
                payload.opacity = 1.0;
                return payload;
            }
            if (annotation instanceof ShapeAnnotation shape) {
                payload.ex = shape.getEndX();
                payload.ey = shape.getEndY();
                payload.color = shape.getColor().toString();
                payload.opacity = shape.getOpacity();
                payload.lineWidth = shape.getLineWidth();
                payload.lineStyle = shape.getLineStyle().name();
                return payload;
            }
            if (annotation instanceof FreehandAnnotation freehand) {
                payload.pointsX = freehand.getPointsX();
                payload.pointsY = freehand.getPointsY();
                payload.color = freehand.getColor().toString();
                payload.opacity = freehand.getOpacity();
                payload.lineWidth = freehand.getLineWidth();
                payload.lineStyle = freehand.getLineStyle().name();
                return payload;
            }
            return null;
        }

        private Annotation toAnnotation() {
            Color fxColor = parseColor(color, Color.WHITE);
            return switch (type) {
                case TYPE_HIGHLIGHT -> new HighlightAnnotation(page, x, y, w, h, parseColor(color, Color.YELLOW), opacity, batchId);
                case TYPE_COMMENT -> new CommentAnnotation(page, x, y, comment == null ? "" : comment, parseColor(color, Color.YELLOW));
                case TYPE_RECTANGLE -> new RectangleAnnotation(page, x, y, ex, ey, fxColor, lineWidth, AnnotationLineStyle.fromString(lineStyle), opacity);
                case TYPE_CIRCLE -> new CircleAnnotation(page, x, y, ex, ey, fxColor, lineWidth, AnnotationLineStyle.fromString(lineStyle), opacity);
                case TYPE_ARROW -> new ArrowAnnotation(page, x, y, ex, ey, fxColor, lineWidth, AnnotationLineStyle.fromString(lineStyle), opacity);
                case TYPE_FREEHAND -> new FreehandAnnotation(
                        page,
                        pointsX == null ? List.of() : pointsX,
                        pointsY == null ? List.of() : pointsY,
                        fxColor,
                        lineWidth <= 0 ? 1.0 : lineWidth,
                        AnnotationLineStyle.fromString(lineStyle),
                        opacity
                );
                default -> null;
            };
        }

        private static Color parseColor(String value, Color fallback) {
            try {
                return value == null || value.isBlank() ? fallback : Color.web(value);
            } catch (Exception ignored) {
                return fallback;
            }
        }
    }
}
