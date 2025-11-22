package org.pdflite.util;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Utility class for working with page containers in PDF Lite.
 * <p>
 * This class provides helper methods for extracting page boxes from
 * containers that support both single-page and two-page layout modes.
 * </p>
 * <p>
 * This is a utility class and cannot be instantiated.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class PageContainerUtils {

    /**
     * Collects all VBox page boxes from a pages container.
     * <p>
     * This method handles both single-page and two-page layout modes.
     * In single-page mode, pages are direct children of the container.
     * In two-page mode, pages are nested within HBox rows.
     * </p>
     *
     * @param pagesContainer the container holding the page boxes, may be null
     * @return a list of VBox page boxes, never null (empty list if container is null)
     */
    public static List<VBox> collectPageBoxes(VBox pagesContainer) {
        List<VBox> list = new ArrayList<>();
        if (pagesContainer == null) {
            return list;
        }

        Object twoMode = pagesContainer.getProperties().get("twoPageMode");
        boolean twoPage = twoMode instanceof Boolean && (Boolean) twoMode;

        if (!twoPage) {
            for (Node node : pagesContainer.getChildren()) {
                if (node instanceof VBox vb) {
                    list.add(vb);
                }
            }
            return list;
        }

        for (Node node : pagesContainer.getChildren()) {
            if (node instanceof HBox row) {
                for (Node child : row.getChildren()) {
                    if (child instanceof VBox vb) {
                        list.add(vb);
                    }
                }
            }
        }

        return list;
    }

    /**
     * Finds a specific VBox page box by page index.
     * <p>
     * This method handles both single-page and two-page layout modes.
     * In single-page mode, the page box is a direct child at the given index.
     * In two-page mode, pages are nested within HBox rows (2 pages per row).
     * </p>
     *
     * @param pagesContainer the container holding the page boxes, may be null
     * @param pageIndex the zero-based index of the page to find
     * @return the VBox page box at the given index, or null if not found or container is null
     */
    public static VBox findPageBox(VBox pagesContainer, int pageIndex) {
        if (pagesContainer == null) {
            return null;
        }

        Object twoMode = pagesContainer.getProperties().get("twoPageMode");
        boolean twoPage = twoMode instanceof Boolean && (Boolean) twoMode;

        if (!twoPage) {
            if (pageIndex >= 0 && pageIndex < pagesContainer.getChildren().size()) {
                Node node = pagesContainer.getChildren().get(pageIndex);
                if (node instanceof VBox vb) {
                    return vb;
                }
            }
            return null;
        }

        int rowIndex = pageIndex / 2;
        int innerIndex = pageIndex % 2;

        if (rowIndex < 0 || rowIndex >= pagesContainer.getChildren().size()) {
            return null;
        }

        Node rowNode = pagesContainer.getChildren().get(rowIndex);
        if (rowNode instanceof HBox row) {
            if (innerIndex >= 0 && innerIndex < row.getChildren().size()) {
                Node child = row.getChildren().get(innerIndex);
                if (child instanceof VBox vb) {
                    return vb;
                }
            }
        }

        return null;
    }

    /**
     * Private constructor to prevent instantiation.
     * <p>
     * This is a utility class containing only static methods and should
     * not be instantiated.
     * </p>
     *
     * @throws AssertionError if an attempt is made to instantiate this class
     */
    private PageContainerUtils() {
        // Prevent instantiation
        throw new AssertionError("Cannot instantiate PageContainerUtils class");
    }
}

