package org.pdflite.manager;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Stateful sidebar manager for thumbnails and outline.
 */
public class DocumentSidebarManager {

    private static final Logger logger = LoggerFactory.getLogger(DocumentSidebarManager.class);
    private static final double THUMBNAIL_SCALE = 0.22;
    private static final PseudoClass ACTIVE_PSEUDO_CLASS = PseudoClass.getPseudoClass("active");

    private final PDFService pdfService;
    private final PageLabelManager pageLabelManager;
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "thumbnail-loader");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<PDFDocument, SidebarState> sidebarByDocument = new WeakHashMap<>();

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    public DocumentSidebarManager(PDFService pdfService, PageLabelManager pageLabelManager) {
        this.pdfService = pdfService;
        this.pageLabelManager = pageLabelManager;
    }

    public VBox createSidebar(PDFDocument document, Consumer<Integer> navigateToPage, Runnable hideSidebarAction) {
        SidebarState existing = sidebarByDocument.get(document);
        if (existing != null) {
            existing.hideSidebarAction = hideSidebarAction;
            return existing.sidebarRoot;
        }

        SidebarState state = new SidebarState(document, navigateToPage, hideSidebarAction);
        state.sidebarRoot = buildSidebarRoot(state);
        state.sidebarRoot.getChildren().add(buildSidebarHeader(state));
        state.tabPane = buildTabPane(state);
        state.sidebarRoot.getChildren().add(state.tabPane);

        sidebarByDocument.put(document, state);
        return state.sidebarRoot;
    }

    public void syncCurrentPage(PDFDocument document, int pageIndex) {
        SidebarState state = sidebarByDocument.get(document);
        if (state == null) {
            return;
        }
        state.currentPageIndex = pageIndex;

        if (state.thumbnailList != null) {
            state.suppressNavigate = true;
            state.thumbnailList.getSelectionModel().select(pageIndex);
            state.thumbnailList.scrollTo(Math.max(0, pageIndex - 2));
            state.thumbnailList.refresh();
            state.suppressNavigate = false;
        }

        if (state.outlineTree != null) {
            TreeItem<OutlineNodeData> best = findClosestOutlineItem(state, pageIndex);
            if (best != null) {
                state.suppressNavigate = true;
                expandParents(best);
                state.outlineTree.getSelectionModel().select(best);
                state.outlineTree.scrollTo(state.outlineTree.getRow(best));
                state.suppressNavigate = false;
            }
        }
    }

    public void refreshPageLabels(PDFDocument document) {
        SidebarState state = sidebarByDocument.get(document);
        if (state == null) {
            return;
        }

        if (state.thumbnailList != null) {
            state.thumbnailList.refresh();
        }
        if (state.outlineTab != null) {
            state.outlineTab.setContent(buildOutlineContent(state));
        }
    }

    public int getActiveTabIndex(PDFDocument document) {
        SidebarState state = sidebarByDocument.get(document);
        return state != null ? state.activeTabIndex : 0;
    }

    private VBox buildSidebarRoot(SidebarState state) {
        VBox sidebar = new VBox(8);
        sidebar.getStyleClass().add("document-sidebar");
        sidebar.setPadding(new Insets(8));
        sidebar.setPrefWidth(280);
        sidebar.setMinWidth(220);
        sidebar.setMaxWidth(420);
        return sidebar;
    }

    private HBox buildSidebarHeader(SidebarState state) {
        HBox header = new HBox();
        header.getStyleClass().add("document-sidebar-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(2, 2, 4, 2));

        Label title = new Label(lang().getString("sidebar.title"));
        title.getStyleClass().add("document-sidebar-title");

        Button closeButton = new Button("x");
        closeButton.getStyleClass().add("sidebar-close-button");
        closeButton.setFocusTraversable(false);
        closeButton.setOnAction(event -> {
            if (state.hideSidebarAction != null) {
                state.hideSidebarAction.run();
            }
        });

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, spacer, closeButton);
        return header;
    }

    private TabPane buildTabPane(SidebarState state) {
        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("document-sidebar-tabs");
        tabs.getTabs().add(buildThumbnailTab(state));
        tabs.getTabs().add(buildOutlineTab(state));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getSelectionModel().select(Math.min(state.activeTabIndex, tabs.getTabs().size() - 1));
        tabs.getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) ->
                state.activeTabIndex = Math.max(0, newVal.intValue()));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        return tabs;
    }

    private Tab buildThumbnailTab(SidebarState state) {
        state.thumbnailTab = new Tab(lang().getString("sidebar.thumbnails"));
        ListView<Integer> listView = new ListView<>();
        listView.getStyleClass().add("thumbnail-list");

        List<Integer> pageIndices = new ArrayList<>();
        for (int i = 0; i < state.document.getTotalPages(); i++) {
            pageIndices.add(i);
        }
        listView.setItems(FXCollections.observableArrayList(pageIndices));
        listView.setCellFactory(ignored -> new ThumbnailCell(state, pdfService, pageLabelManager, thumbnailExecutor));

        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || state.suppressNavigate) {
                return;
            }
            state.navigateToPage.accept(newVal);
        });

        state.thumbnailList = listView;
        state.thumbnailTab.setContent(listView);
        return state.thumbnailTab;
    }

    private Tab buildOutlineTab(SidebarState state) {
        state.outlineTab = new Tab(lang().getString("sidebar.outline"));
        state.outlineTab.setContent(buildOutlineContent(state));
        return state.outlineTab;
    }

    private javafx.scene.Node buildOutlineContent(SidebarState state) {
        state.outlineItemsByPage.clear();

        TreeItem<OutlineNodeData> root = new TreeItem<>(new OutlineNodeData("root", -1, ""));
        root.setExpanded(true);
        boolean hasOutline = buildOutlineTree(state, root);
        if (!hasOutline) {
            VBox emptyBox = new VBox();
            emptyBox.getStyleClass().add("sidebar-empty-state");
            emptyBox.setPadding(new Insets(14));
            Label emptyTitle = new Label(lang().getString("sidebar.outline"));
            emptyTitle.getStyleClass().add("sidebar-empty-title");
            Label emptyLabel = new Label(lang().getString("sidebar.noOutline"));
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("sidebar-empty-description");
            emptyBox.getChildren().addAll(emptyTitle, emptyLabel);
            return emptyBox;
        }

        TreeView<OutlineNodeData> treeView = new TreeView<>(root);
        treeView.getStyleClass().add("outline-tree");
        treeView.setShowRoot(false);
        treeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(OutlineNodeData item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayTitle());
            }
        });

        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.getValue() == null || state.suppressNavigate) {
                return;
            }
            int pageIndex = newVal.getValue().pageIndex();
            if (pageIndex >= 0) {
                state.navigateToPage.accept(pageIndex);
            }
        });

        treeView.addEventHandler(TreeItem.branchExpandedEvent(), event -> {
            TreeItem<?> treeItem = event.getTreeItem();
            if (treeItem instanceof TreeItem<?> rawItem && rawItem.getValue() instanceof OutlineNodeData) {
                @SuppressWarnings("unchecked")
                TreeItem<OutlineNodeData> typedItem = (TreeItem<OutlineNodeData>) rawItem;
                state.expandedOutlinePaths.add(getTreePathKey(typedItem));
            }
        });
        treeView.addEventHandler(TreeItem.branchCollapsedEvent(), event -> {
            TreeItem<?> treeItem = event.getTreeItem();
            if (treeItem instanceof TreeItem<?> rawItem && rawItem.getValue() instanceof OutlineNodeData) {
                @SuppressWarnings("unchecked")
                TreeItem<OutlineNodeData> typedItem = (TreeItem<OutlineNodeData>) rawItem;
                state.expandedOutlinePaths.remove(getTreePathKey(typedItem));
            }
        });

        restoreExpandedState(root, state.expandedOutlinePaths);

        state.outlineTree = treeView;
        syncCurrentPage(state.document, state.currentPageIndex);
        return treeView;
    }

    private boolean buildOutlineTree(SidebarState state, TreeItem<OutlineNodeData> root) {
        PDDocument pdDocument = state.document.getDocument();
        PDDocumentOutline outline = pdDocument.getDocumentCatalog().getDocumentOutline();
        if (outline == null || outline.getFirstChild() == null) {
            return false;
        }

        appendOutlineChildren(state, outline, root, state.document, pdDocument, "");
        return !root.getChildren().isEmpty();
    }

    private void appendOutlineChildren(SidebarState state, PDOutlineNode sourceNode, TreeItem<OutlineNodeData> targetNode,
                                       PDFDocument document, PDDocument pdDocument, String parentPath) {
        PDOutlineItem current = sourceNode.getFirstChild();
        int childIndex = 0;
        while (current != null) {
            String path = parentPath + "/" + childIndex;
            try {
                String title = current.getTitle();
                int pageIndex = resolveOutlinePageIndex(current, pdDocument);
                if (title != null && !title.isBlank()) {
                    String display = pageIndex >= 0
                            ? "[" + pageLabelManager.getPageLabel(document, pageIndex) + "] " + title.trim()
                            : title.trim();
                    TreeItem<OutlineNodeData> treeItem = new TreeItem<>(new OutlineNodeData(display, pageIndex, path));
                    targetNode.getChildren().add(treeItem);

                    if (pageIndex >= 0) {
                        state.outlineItemsByPage.computeIfAbsent(pageIndex, ignored -> new ArrayList<>()).add(treeItem);
                    }
                    if (current.hasChildren()) {
                        appendOutlineChildren(state, current, treeItem, document, pdDocument, path);
                    }
                }
            } catch (Exception e) {
                logger.debug("Failed to parse an outline node: {}", e.getMessage());
            }
            current = current.getNextSibling();
            childIndex++;
        }
    }

    private int resolveOutlinePageIndex(PDOutlineItem item, PDDocument pdDocument) {
        try {
            PDDestination destination = item.getDestination();
            if (destination instanceof PDPageDestination pageDestination) {
                PDPage page = pageDestination.getPage();
                if (page != null) {
                    return pdDocument.getPages().indexOf(page);
                }
            }

            PDAction action = item.getAction();
            if (action instanceof PDActionGoTo goToAction) {
                PDDestination actionDestination = goToAction.getDestination();
                if (actionDestination instanceof PDPageDestination pageDestination) {
                    PDPage page = pageDestination.getPage();
                    if (page != null) {
                        return pdDocument.getPages().indexOf(page);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to resolve outline destination: {}", e.getMessage());
        }
        return -1;
    }

    private TreeItem<OutlineNodeData> findClosestOutlineItem(SidebarState state, int pageIndex) {
        int bestPage = -1;
        for (Integer outlinePage : state.outlineItemsByPage.keySet()) {
            if (outlinePage <= pageIndex && outlinePage > bestPage) {
                bestPage = outlinePage;
            }
        }
        if (bestPage < 0) {
            return null;
        }
        List<TreeItem<OutlineNodeData>> items = state.outlineItemsByPage.get(bestPage);
        return items == null || items.isEmpty() ? null : items.getFirst();
    }

    private void expandParents(TreeItem<?> item) {
        TreeItem<?> current = item;
        while (current != null) {
            current.setExpanded(true);
            current = current.getParent();
        }
    }

    private void restoreExpandedState(TreeItem<OutlineNodeData> root, Set<String> expandedPaths) {
        for (TreeItem<OutlineNodeData> child : root.getChildren()) {
            String path = getTreePathKey(child);
            child.setExpanded(expandedPaths.contains(path));
            restoreExpandedState(child, expandedPaths);
        }
    }

    private String getTreePathKey(TreeItem<OutlineNodeData> item) {
        return item != null && item.getValue() != null ? item.getValue().path() : "";
    }

    private record OutlineNodeData(String displayTitle, int pageIndex, String path) {
    }

    private static final class SidebarState {
        private final PDFDocument document;
        private final Consumer<Integer> navigateToPage;
        private Runnable hideSidebarAction;
        private final Map<Integer, Image> thumbnailCache = new HashMap<>();
        private final Map<Integer, List<TreeItem<OutlineNodeData>>> outlineItemsByPage = new HashMap<>();
        private final Set<String> expandedOutlinePaths = new HashSet<>();

        private VBox sidebarRoot;
        private TabPane tabPane;
        private Tab thumbnailTab;
        private Tab outlineTab;
        private ListView<Integer> thumbnailList;
        private TreeView<OutlineNodeData> outlineTree;
        private int activeTabIndex = 0;
        private int currentPageIndex = 0;
        private boolean suppressNavigate = false;

        private SidebarState(PDFDocument document, Consumer<Integer> navigateToPage, Runnable hideSidebarAction) {
            this.document = document;
            this.navigateToPage = navigateToPage;
            this.hideSidebarAction = hideSidebarAction;
        }
    }

    private static class ThumbnailCell extends ListCell<Integer> {
        private final SidebarState sidebarState;
        private final PDFService pdfService;
        private final PageLabelManager pageLabelManager;
        private final ExecutorService thumbnailExecutor;

        private ThumbnailCell(SidebarState sidebarState, PDFService pdfService, PageLabelManager pageLabelManager,
                              ExecutorService thumbnailExecutor) {
            this.sidebarState = sidebarState;
            this.pdfService = pdfService;
            this.pageLabelManager = pageLabelManager;
            this.thumbnailExecutor = thumbnailExecutor;
        }

        @Override
        protected void updateItem(Integer pageIndex, boolean empty) {
            super.updateItem(pageIndex, empty);
            getStyleClass().remove("active");
            pseudoClassStateChanged(ACTIVE_PSEUDO_CLASS, false);

            if (empty || pageIndex == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            HBox container = new HBox(10);
            container.getStyleClass().add("thumbnail-item");
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(6));

            ImageView imageView = new ImageView();
            imageView.getStyleClass().add("thumbnail-preview");
            imageView.setFitWidth(78);
            imageView.setFitHeight(104);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            Label pageLabel = new Label(pageLabelManager.getPageLabel(sidebarState.document, pageIndex));
            pageLabel.getStyleClass().add("thumbnail-page-label");
            Label rawPageLabel = new Label(lang().getString("sidebar.page") + " " + (pageIndex + 1));
            rawPageLabel.getStyleClass().add("thumbnail-page-index");

            VBox textBox = new VBox(2, pageLabel, rawPageLabel);
            container.getChildren().addAll(imageView, textBox);

            Image cached = sidebarState.thumbnailCache.get(pageIndex);
            if (cached != null) {
                imageView.setImage(cached);
            } else {
                loadThumbnailAsync(pageIndex, imageView);
            }

            boolean isActive = pageIndex == sidebarState.currentPageIndex;
            pseudoClassStateChanged(ACTIVE_PSEUDO_CLASS, isActive);
            if (isActive) {
                getStyleClass().add("active");
            }

            setGraphic(container);
        }

        private void loadThumbnailAsync(int pageIndex, ImageView imageView) {
            CompletableFuture.supplyAsync(() -> {
                try {
                    return pdfService.renderPage(sidebarState.document, pageIndex, (float) THUMBNAIL_SCALE);
                } catch (IOException e) {
                    return null;
                }
            }, thumbnailExecutor).thenAccept(image -> {
                if (image == null) {
                    return;
                }
                sidebarState.thumbnailCache.put(pageIndex, image);
                Platform.runLater(() -> {
                    Integer currentItem = getItem();
                    if (currentItem != null && currentItem == pageIndex) {
                        imageView.setImage(image);
                    }
                });
            });
        }
    }
}
