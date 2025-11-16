package org.pdflite.util;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.state.*;
import org.apache.pdfbox.contentstream.operator.graphics.*;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.pdflite.model.ImageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p><b>Critical Fix:</b> Registers ALL graphics state operators
 * to ensure CTM is tracked correctly before 'Do' operator.</p>
 * 
 * @see <a href="../../../docs/knowledge_3.md">Knowledge Base - Matrix Tracking</a>
 */
public class ImageExtractor extends PDFStreamEngine {
    private static final Logger logger = LoggerFactory.getLogger(ImageExtractor.class);
    
    private final List<ImageInfo> images = new ArrayList<>();
    private int currentPageIndex;

    public ImageExtractor() {
        addOperator(new DrawObject(this));
        addOperator(new Concatenate(this));
        addOperator(new Save(this));
        addOperator(new Restore(this));
        addOperator(new SetGraphicsStateParameters(this));
        addOperator(new SetMatrix(this));
    }
    
    /**
     * Extracts all images from a PDF page.
     */
    public List<ImageInfo> extractImages(PDPage page, int pageIndex) throws IOException {
        this.currentPageIndex = pageIndex;
        this.images.clear();
        processPage(page);
        return new ArrayList<>(images);
    }
    
    /**
     *
     */
    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) 
            throws IOException {
        
        String operatorName = operator.getName();
        
        if ("Do".equals(operatorName)) {
            COSName objectName = (COSName) operands.getFirst();
            PDXObject pdxObject = getResources().getXObject(objectName);
            
            if (pdxObject instanceof PDImageXObject) {
                processImageXObject((PDImageXObject) pdxObject, objectName.getName());
                
            } else if (pdxObject instanceof PDFormXObject form) {
                showForm(form);
            }
        } else {
            super.processOperator(operator, operands);
        }
    }
    
    /**
     * Extract image with position from CTM.
     */
    private void processImageXObject(PDImageXObject pdImage, String name) throws IOException {
        // Get CTM (now contains REAL transformation values!)
        Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
        
        // Extract position
        float xPosition = ctm.getTranslateX();
        float yPosition = ctm.getTranslateY();
        
        // Extract rendered size (scaled dimensions)
        float renderedWidth = Math.abs(ctm.getScaleX());
        float renderedHeight = Math.abs(ctm.getScaleY());
        
        // Validate extracted values
        if (xPosition == 0 && yPosition == 0 && renderedWidth == 1 && renderedHeight == 1) {
            logger.warn("Image '{}' has identity CTM - may indicate missing matrix tracking!", name);
        }
        
        // Get pixel data
        BufferedImage bufferedImage = pdImage.getImage();
        
        // Create ImageInfo
        ImageInfo imageInfo = new ImageInfo(
            currentPageIndex,
            xPosition,
            yPosition,
            renderedWidth,
            renderedHeight,
            bufferedImage
        );
        
        images.add(imageInfo);
    }
}