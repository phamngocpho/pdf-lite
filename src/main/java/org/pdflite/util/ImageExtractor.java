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

    public ImageExtractor() throws IOException {
        addOperator(new DrawObject(this));
        addOperator(new Concatenate(this));
        addOperator(new Save(this));
        addOperator(new Restore(this));
        addOperator(new SetGraphicsStateParameters(this));
        addOperator(new SetMatrix(this));
    }
    
    /**
     * Extracts all images from a PDF page.
     * @param page
     * @param pageIndex
     * @return 
     * @throws java.io.IOException
     */
    public List<ImageInfo> extractImages(PDPage page, int pageIndex) throws IOException {
        this.currentPageIndex = pageIndex;
        this.images.clear();
        processPage(page);
        return new ArrayList<>(images);
    }
    
    /**
     *
     * @param operator
     * @param operands
     * @throws java.io.IOException
     */
    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) 
            throws IOException {
        
        String operatorName = operator.getName();
        
        if ("Do".equals(operatorName)) {
            COSName objectName = (COSName) operands.get(0);
            PDXObject xobject = getResources().getXObject(objectName);
            
            if (xobject instanceof PDImageXObject) {
                processImageXObject((PDImageXObject) xobject, objectName.getName());
                
            } else if (xobject instanceof PDFormXObject) {
                PDFormXObject form = (PDFormXObject) xobject;
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
        /*
        logger.debug("Image '{}' extracted:", name);
        logger.debug("Position (PDF Y=bottom): ({:.2f}, {:.2f}) pt", xPosition, yPosition);
        logger.debug("Rendered Size: {:.2f} x {:.2f} pt", renderedWidth, renderedHeight);
        logger.debug("Pixel Size: {} x {} px", 
            bufferedImage.getWidth(), bufferedImage.getHeight());
         */
    }
}