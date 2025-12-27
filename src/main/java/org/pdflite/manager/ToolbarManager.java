package org.pdflite.manager;

import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager for toolbar visibility operations.
 */
public class ToolbarManager {

    private static final Logger logger = LoggerFactory.getLogger(ToolbarManager.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private final ToolBar toolbar;
    private final MenuItem toggleToolbarMenuItem;

    public ToolbarManager(ToolBar toolbar, MenuItem toggleToolbarMenuItem) {
        this.toolbar = toolbar;
        this.toggleToolbarMenuItem = toggleToolbarMenuItem;
    }

    /**
     * Toggles the toolbar visibility.
     */
    public void handleToggleToolbar() {
        if (toolbar == null) return;

        boolean isToolbarVisible = toolbar.isVisible();

        if (isToolbarVisible) {
            toolbar.setManaged(false);
            toolbar.setVisible(false);
            if (toggleToolbarMenuItem != null) {
                toggleToolbarMenuItem.setText(lang().getString("menu.view.showToolbar"));
            }
            logger.debug("Toolbar hidden");
        } else {
            toolbar.setManaged(true);
            toolbar.setVisible(true);
            if (toggleToolbarMenuItem != null) {
                toggleToolbarMenuItem.setText(lang().getString("menu.view.hideToolbar"));
            }
            logger.debug("Toolbar shown");
        }
    }

    public boolean isToolbarVisible() {
        return toolbar != null && toolbar.isVisible();
    }
}
