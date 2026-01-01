package org.pdflite.manager;

import javafx.application.Platform;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager responsible for updating UI elements when language changes.
 * Handles toolbar, menu bar, and other UI component text updates.
 */
public class UILanguageManager {

    private static final Logger logger = LoggerFactory.getLogger(UILanguageManager.class);

    private final LanguageManager languageManager;
    
    // UI Components
    private Label titleLabel;
    private MenuBar menuBar;
    private ToolBar toolbar;
    private Button openButton;
    private Button saveButton;
    private Button printButton;
    private Button prevButton;
    private Button nextButton;
    private Button zoomOutButton;
    private Button zoomInButton;
    private Button bookmarksButton;
    private Button aiChatButton;
    private Tooltip aiChatTooltip;
    private MenuButton drawingToolsMenu;
    private Label drawingToolsLabel;
    private Label drawingColorLabel;
    private Label highlightColorLabel;
    private Label strokeWidthTitleLabel;
    private RadioMenuItem englishItem;
    private RadioMenuItem vietnameseItem;
    private MenuItem toggleToolbarMenuItem;
    private MenuItem fullScreenMenuItem;
    private UIStateManager uiStateManager;

    public UILanguageManager(LanguageManager languageManager) {
        this.languageManager = languageManager;
    }

    /**
     * Sets all UI component references.
     */
    public void setUIComponents(
            Label titleLabel,
            MenuBar menuBar,
            ToolBar toolbar,
            Button openButton,
            Button saveButton,
            Button printButton,
            Button prevButton,
            Button nextButton,
            Button zoomOutButton,
            Button zoomInButton,
            Button bookmarksButton,
            Button aiChatButton,
            Tooltip aiChatTooltip,
            MenuButton drawingToolsMenu,
            Label drawingToolsLabel,
            Label drawingColorLabel,
            Label highlightColorLabel,
            Label strokeWidthTitleLabel,
            RadioMenuItem englishItem,
            RadioMenuItem vietnameseItem,
            MenuItem toggleToolbarMenuItem,
            MenuItem fullScreenMenuItem,
            UIStateManager uiStateManager
    ) {
        this.titleLabel = titleLabel;
        this.menuBar = menuBar;
        this.toolbar = toolbar;
        this.openButton = openButton;
        this.saveButton = saveButton;
        this.printButton = printButton;
        this.prevButton = prevButton;
        this.nextButton = nextButton;
        this.zoomOutButton = zoomOutButton;
        this.zoomInButton = zoomInButton;
        this.bookmarksButton = bookmarksButton;
        this.aiChatButton = aiChatButton;
        this.aiChatTooltip = aiChatTooltip;
        this.drawingToolsMenu = drawingToolsMenu;
        this.drawingToolsLabel = drawingToolsLabel;
        this.drawingColorLabel = drawingColorLabel;
        this.highlightColorLabel = highlightColorLabel;
        this.strokeWidthTitleLabel = strokeWidthTitleLabel;
        this.englishItem = englishItem;
        this.vietnameseItem = vietnameseItem;
        this.toggleToolbarMenuItem = toggleToolbarMenuItem;
        this.fullScreenMenuItem = fullScreenMenuItem;
        this.uiStateManager = uiStateManager;
    }

    /**
     * Initializes language selection based on saved preference.
     */
    public void initializeLanguageSelection() {
        Platform.runLater(() -> {
            if (languageManager.isEnglish() && englishItem != null) {
                englishItem.setSelected(true);
            } else if (languageManager.isVietnamese() && vietnameseItem != null) {
                vietnameseItem.setSelected(true);
            }
            
            // Initial update of language menu graphics
            updateLanguageMenuGraphics();
        });
    }

    /**
     * Updates language menu graphics with bullet indicators.
     */
    public void updateLanguageMenuGraphics() {
        if (englishItem == null || vietnameseItem == null) {
            return;
        }

        // Create bullet graphic for selected item
        javafx.scene.shape.Circle englishBullet = englishItem.isSelected() 
            ? new javafx.scene.shape.Circle(3, javafx.scene.paint.Color.web("#0A84FF")) 
            : null;
        javafx.scene.shape.Circle vietnameseBullet = vietnameseItem.isSelected() 
            ? new javafx.scene.shape.Circle(3, javafx.scene.paint.Color.web("#0A84FF")) 
            : null;

        // Set graphics - bullet for selected, null for others
        englishItem.setGraphic(englishBullet);
        vietnameseItem.setGraphic(vietnameseBullet);
    }

    /**
     * Updates all UI text when language changes.
     */
    public void updateUILanguage() {
        Platform.runLater(() -> {
            // Update title bar
            if (titleLabel != null) {
                titleLabel.setText(languageManager.getString("app.title"));
            }
            
            // Update menu bar
            updateMenuLanguage();
            
            // Update toolbar
            updateToolbarLanguage();
            
            // Update status
            if (uiStateManager != null) {
                uiStateManager.updateStatus(languageManager.getString("status.ready"));
            }
            
            // Update language menu graphics
            updateLanguageMenuGraphics();
            
            logger.info("UI language updated to: {}", languageManager.getCurrentLocale().getDisplayLanguage());
        });
    }

    /**
     * Updates toolbar text based on the current language.
     */
    private void updateToolbarLanguage() {
        if (toolbar == null) return;
        
        // Update direct button references
        updateButton(openButton, "toolbar.open", "tooltip.open");
        updateButton(saveButton, "toolbar.save", "tooltip.save");
        updateButton(printButton, "toolbar.print", "tooltip.print");
        updateButton(prevButton, "toolbar.previous", "tooltip.previous");
        updateButton(nextButton, "toolbar.next", "tooltip.next");
        updateButton(zoomOutButton, "toolbar.zoomOut", "tooltip.zoomOut");
        updateButton(zoomInButton, "toolbar.zoomIn", "tooltip.zoomIn");
        updateButton(bookmarksButton, "toolbar.bookmarks", "tooltip.bookmarks");
        
        if (aiChatButton != null) {
            aiChatButton.setText(languageManager.getString("ai.button"));
            if (aiChatTooltip != null) aiChatTooltip.setText(languageManager.getString("ai.tooltip"));
        }
        
        // Update drawing tools menu directly
        if (drawingToolsMenu != null) {
            drawingToolsMenu.setText(languageManager.getString("toolbar.draw"));
            if (drawingToolsMenu.getTooltip() != null) {
                drawingToolsMenu.getTooltip().setText(languageManager.getString("tooltip.drawingTools"));
            }
        }
        
        // Update other toolbar elements
        for (var node : toolbar.getItems()) {
            if (node instanceof Button btn) {
                updateUndoRedoButton(btn);
            } else if (node instanceof MenuButton menuBtn) {
                updateToolbarMenuButton(menuBtn);
            } else if (node instanceof ToggleButton toggleBtn) {
                updateDrawingToolToggle(toggleBtn);
            }
        }
        
        // Update drawing tools labels
        if (drawingToolsLabel != null) drawingToolsLabel.setText(languageManager.getString("draw.title"));
        if (drawingColorLabel != null) drawingColorLabel.setText(languageManager.getString("draw.color"));
        if (highlightColorLabel != null) highlightColorLabel.setText(languageManager.getString("draw.highlightColor"));
        if (strokeWidthTitleLabel != null) strokeWidthTitleLabel.setText(languageManager.getString("draw.strokeWidth"));
    }

    private void updateButton(Button button, String textKey, String tooltipKey) {
        if (button != null) {
            button.setText(languageManager.getString(textKey));
            if (button.getTooltip() != null) {
                button.getTooltip().setText(languageManager.getString(tooltipKey));
            }
        }
    }

    private void updateUndoRedoButton(Button btn) {
        if (btn.getTooltip() != null) {
            String tooltipText = btn.getTooltip().getText();
            if (tooltipText != null) {
                if (tooltipText.contains("Undo") || tooltipText.contains("Hoàn tác")) {
                    btn.getTooltip().setText(languageManager.getString("tooltip.undo"));
                } else if (tooltipText.contains("Redo") || tooltipText.contains("Làm lại")) {
                    btn.getTooltip().setText(languageManager.getString("tooltip.redo"));
                }
            }
        }
    }

    private void updateToolbarMenuButton(MenuButton menuBtn) {
        String text = menuBtn.getText();
        if (text != null) {
            if (text.contains("Draw") || text.contains("Vẽ")) {
                menuBtn.setText(languageManager.getString("toolbar.draw"));
                if (menuBtn.getTooltip() != null) menuBtn.getTooltip().setText(languageManager.getString("tooltip.drawingTools"));
            } else if (text.equals("PDF Tools") || text.equals("Công cụ PDF")) {
                menuBtn.setText(languageManager.getString("toolbar.pdfTools"));
                if (menuBtn.getTooltip() != null) menuBtn.getTooltip().setText(languageManager.getString("tooltip.pdfOperations"));
                updatePDFToolsMenuItems(menuBtn);
            } else if (text.equals("View") || text.equals("Xem")) {
                menuBtn.setText(languageManager.getString("toolbar.view"));
                if (menuBtn.getTooltip() != null) menuBtn.getTooltip().setText(languageManager.getString("tooltip.viewOptions"));
                updateViewToolbarMenuItems(menuBtn);
            } else if (text.equals("Annotate") || text.equals("Chú thích")) {
                menuBtn.setText(languageManager.getString("toolbar.annotate"));
                if (menuBtn.getTooltip() != null) menuBtn.getTooltip().setText(languageManager.getString("tooltip.annotationTools"));
                updateAnnotateMenuItems(menuBtn);
            }
        }
    }

    private void updateDrawingToolToggle(ToggleButton toggleBtn) {
        if (toggleBtn.getTooltip() != null) {
            String tooltipText = toggleBtn.getTooltip().getText();
            if (tooltipText != null) {
                if (tooltipText.contains("Rectangle") || tooltipText.contains("hình chữ nhật")) {
                    toggleBtn.getTooltip().setText(languageManager.getString("tooltip.drawRect"));
                } else if (tooltipText.contains("Circle") || tooltipText.contains("hình tròn")) {
                    toggleBtn.getTooltip().setText(languageManager.getString("tooltip.drawCircle"));
                } else if (tooltipText.contains("Arrow") || tooltipText.contains("mũi tên")) {
                    toggleBtn.getTooltip().setText(languageManager.getString("tooltip.drawArrow"));
                }
            }
        }
    }

    private void updatePDFToolsMenuItems(MenuButton menuBtn) {
        var items = menuBtn.getItems();
        if (items.size() >= 17) {
            setMenuItemText(items, 0, "pdftools.insertPage");
            setMenuItemText(items, 1, "menu.tools.watermark");
            setMenuItemText(items, 3, "menu.tools.ocr");
            setMenuItemText(items, 5, "menu.tools.merge");
            setMenuItemText(items, 6, "menu.tools.split");
            setMenuItemText(items, 7, "menu.tools.extract");
            setMenuItemText(items, 8, "menu.tools.reorder");
            setMenuItemText(items, 10, "menu.tools.encrypt");
            setMenuItemText(items, 11, "menu.tools.digitalSignature");
            setMenuItemText(items, 12, "menu.tools.verifySignatures");
            setMenuItemText(items, 13, "menu.tools.decrypt");
            setMenuItemText(items, 14, "menu.tools.permissions");
            setMenuItemText(items, 16, "menu.tools.deletePage");
            setMenuItemText(items, 17, "menu.tools.duplicatePage");
        }
    }

    private void updateViewToolbarMenuItems(MenuButton menuBtn) {
        var items = menuBtn.getItems();
        if (items.size() >= 4) {
            setMenuItemText(items, 0, "menu.view.rotateLeft");
            setMenuItemText(items, 1, "menu.view.rotateRight");
            setMenuItemText(items, 3, "menu.view.fullScreen");
        }
    }

    private void updateAnnotateMenuItems(MenuButton menuBtn) {
        var items = menuBtn.getItems();
        if (items.size() >= 5) {
            setMenuItemText(items, 0, "annotate.highlight");
            setMenuItemText(items, 2, "annotate.search");
            setMenuItemText(items, 3, "annotate.searchLeft");
            setMenuItemText(items, 4, "annotate.searchRight");
        }
    }

    /**
     * Updates menu text based on current language.
     */
    private void updateMenuLanguage() {
        if (menuBar == null) return;
        
        var menus = menuBar.getMenus();
        if (menus.size() >= 7) {
            menus.get(0).setText(languageManager.getString("menu.file"));
            updateFileMenuItems(menus.get(0));
            
            menus.get(1).setText(languageManager.getString("menu.edit"));
            updateEditMenuItems(menus.get(1));
            
            menus.get(2).setText(languageManager.getString("menu.view"));
            updateViewMenuItems(menus.get(2));
            
            menus.get(3).setText(languageManager.getString("menu.tools"));
            updateToolsMenuItems(menus.get(3));
            
            menus.get(4).setText(languageManager.getString("menu.help"));
            updateHelpMenuItems(menus.get(4));
            
            menus.get(5).setText(languageManager.getString("menu.theme"));
            updateThemeMenuItems(menus.get(5));
            
            menus.get(6).setText(languageManager.getString("menu.language"));
        }
    }

    private void updateFileMenuItems(Menu menu) {
        var items = menu.getItems();
        if (items.size() >= 14) {
            setMenuItemText(items, 0, "menu.file.open");
            items.get(1).setText(languageManager.getString("menu.file.recentFiles"));
            setMenuItemText(items, 2, "menu.file.clearRecent");
            setMenuItemText(items, 4, "menu.file.export");
            setMenuItemText(items, 5, "menu.file.print");
            setMenuItemText(items, 7, "menu.file.save");
            setMenuItemText(items, 8, "menu.file.saveAs");
            setMenuItemText(items, 10, "menu.file.optimize");
            setMenuItemText(items, 11, "menu.file.properties");
            setMenuItemText(items, 13, "menu.file.settings");
            setMenuItemText(items, 15, "menu.file.exit");
        }
    }

    private void updateEditMenuItems(Menu menu) {
        var items = menu.getItems();
        if (items.size() >= 7) {
            setMenuItemText(items, 0, "menu.edit.editText");
            setMenuItemText(items, 2, "menu.edit.insertImage");
            setMenuItemText(items, 3, "menu.edit.insertStamp");
            setMenuItemText(items, 5, "menu.edit.find");
            setMenuItemText(items, 7, "menu.edit.hideSearch");
        }
    }

    private void updateViewMenuItems(Menu menu) {
        var items = menu.getItems();
        if (items.size() >= 15) {
            setMenuItemText(items, 0, "menu.view.zoomIn");
            setMenuItemText(items, 1, "menu.view.zoomOut");
            setMenuItemText(items, 2, "menu.view.fitWidth");
            setMenuItemText(items, 3, "menu.view.fitPage");
            setMenuItemText(items, 5, "menu.view.toggleBookmarks");
            setMenuItemText(items, 6, "menu.view.addBookmark");
            setMenuItemText(items, 8, "menu.view.smartBookmarks");
            setMenuItemText(items, 9, "menu.view.importOutline");
            setMenuItemText(items, 11, "menu.view.clearBookmarks");
            
            // Update toggle toolbar text based on current state
            if (toggleToolbarMenuItem != null) {
                if (toolbar != null && toolbar.isVisible()) {
                    toggleToolbarMenuItem.setText(languageManager.getString("menu.view.hideToolbar"));
                } else {
                    toggleToolbarMenuItem.setText(languageManager.getString("menu.view.showToolbar"));
                }
            }
            
            // Update full screen menu item
            if (fullScreenMenuItem != null) {
                fullScreenMenuItem.setText(languageManager.getString("menu.view.fullScreen"));
            }
        }
    }

    private void updateToolsMenuItems(Menu menu) {
        var items = menu.getItems();
        if (items.size() >= 19) {
            setMenuItemText(items, 0, "menu.tools.highlight");
            setMenuItemText(items, 2, "menu.tools.ocr");
            setMenuItemText(items, 4, "menu.tools.watermark");
            setMenuItemText(items, 6, "menu.tools.merge");
            setMenuItemText(items, 7, "menu.tools.split");
            setMenuItemText(items, 8, "menu.tools.extract");
            setMenuItemText(items, 9, "menu.tools.reorder");
            setMenuItemText(items, 11, "menu.tools.encrypt");
            setMenuItemText(items, 12, "menu.tools.digitalSignature");
            setMenuItemText(items, 13, "menu.tools.verifySignatures");
            setMenuItemText(items, 14, "menu.tools.decrypt");
            setMenuItemText(items, 15, "menu.tools.permissions");
            setMenuItemText(items, 17, "menu.tools.deletePage");
            setMenuItemText(items, 18, "menu.tools.duplicatePage");
        }
    }

    private void updateHelpMenuItems(Menu menu) {
        var items = menu.getItems();
        if (items.size() >= 3) {
            setMenuItemText(items, 0, "menu.help.aiAssistant");
            setMenuItemText(items, 2, "menu.help.about");
        }
    }

    private void updateThemeMenuItems(Menu menu) {
        var items = menu.getItems();
        if (items.size() >= 4) {
            setMenuItemText(items, 0, "menu.theme.system");
            setMenuItemText(items, 2, "menu.theme.light");
            setMenuItemText(items, 3, "menu.theme.dark");
        }
    }

    private void setMenuItemText(javafx.collections.ObservableList<MenuItem> items, int index, String key) {
        if (index < items.size()) {
            items.get(index).setText(languageManager.getString(key));
        }
    }
}
