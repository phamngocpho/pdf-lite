package org.pdflite.manager;

import javafx.scene.input.KeyCombination;

import java.util.List;

/**
 * Centralized shortcut catalog for UI display and key mapping.
 */
public final class ShortcutCatalog {

    private ShortcutCatalog() {
    }

    public record ShortcutEntry(String labelKey, String accelerator) {
    }

    public record ShortcutGroup(String titleKey, List<ShortcutEntry> entries) {
    }

    public static List<ShortcutGroup> groups() {
        return List.of(
                new ShortcutGroup("shortcuts.group.navigation", List.of(
                        new ShortcutEntry("menu.file.open", "Ctrl+O"),
                        new ShortcutEntry("menu.file.export", "Ctrl+Shift+E"),
                        new ShortcutEntry("menu.file.print", "Ctrl+P"),
                        new ShortcutEntry("menu.file.save", "Ctrl+S"),
                        new ShortcutEntry("menu.file.saveAs", "Ctrl+Shift+S"),
                        new ShortcutEntry("menu.file.optimize", "Ctrl+Shift+O"),
                        new ShortcutEntry("menu.file.properties", "Ctrl+D"),
                        new ShortcutEntry("menu.file.settings", "Ctrl+Alt+S"),
                        new ShortcutEntry("menu.file.exit", "Alt+F4")
                )),
                new ShortcutGroup("shortcuts.group.zoom", List.of(
                        new ShortcutEntry("menu.view.zoomIn", "Ctrl+Plus"),
                        new ShortcutEntry("menu.view.zoomOut", "Ctrl+Minus"),
                        new ShortcutEntry("menu.view.fitWidth", "Ctrl+0"),
                        new ShortcutEntry("menu.view.fitPage", "Ctrl+1")
                )),
                new ShortcutGroup("shortcuts.group.search", List.of(
                        new ShortcutEntry("menu.edit.find", "Ctrl+F"),
                        new ShortcutEntry("menu.edit.hideSearch", "Escape")
                )),
                new ShortcutGroup("shortcuts.group.sidebar", List.of(
                        new ShortcutEntry("menu.view.toggleBookmarks", "Ctrl+B"),
                        new ShortcutEntry("menu.view.addBookmark", "Ctrl+Shift+B"),
                        new ShortcutEntry("menu.view.smartBookmarks", "Ctrl+Alt+B"),
                        new ShortcutEntry("menu.view.importOutline", "Ctrl+Alt+O"),
                        new ShortcutEntry("menu.view.clearBookmarks", "Ctrl+Shift+Delete"),
                        new ShortcutEntry("menu.view.hideToolbar", "Ctrl+T"),
                        new ShortcutEntry("menu.view.autoHideUI", "Ctrl+Shift+H"),
                        new ShortcutEntry("menu.view.fullScreen", "F11")
                )),
                new ShortcutGroup("shortcuts.group.edit", List.of(
                        new ShortcutEntry("menu.edit.editText", "Ctrl+E"),
                        new ShortcutEntry("menu.edit.insertImage", "Ctrl+I"),
                        new ShortcutEntry("menu.edit.insertStamp", "Ctrl+Shift+I"),
                        new ShortcutEntry("menu.tools.highlight", "Ctrl+H"),
                        new ShortcutEntry("menu.tools.ocr", "Ctrl+Shift+T"),
                        new ShortcutEntry("menu.tools.watermark", "Ctrl+W"),
                        new ShortcutEntry("menu.tools.merge", "Ctrl+M"),
                        new ShortcutEntry("menu.tools.split", "Ctrl+Shift+P"),
                        new ShortcutEntry("menu.tools.extract", "Ctrl+Shift+X"),
                        new ShortcutEntry("menu.tools.reorder", "Ctrl+Shift+R"),
                        new ShortcutEntry("menu.tools.deletePage", "Delete"),
                        new ShortcutEntry("menu.tools.duplicatePage", "Ctrl+Shift+D"),
                        new ShortcutEntry("shortcuts.action.undo", "Shortcut+Z"),
                        new ShortcutEntry("shortcuts.action.redo", "Shortcut+Y")
                )),
                new ShortcutGroup("shortcuts.group.help", List.of(
                        new ShortcutEntry("menu.help.aiAssistant", "Ctrl+Shift+A"),
                        new ShortcutEntry("menu.help.keyboardShortcuts", "Ctrl+/"),
                        new ShortcutEntry("menu.help.about", "F1")
                ))
        );
    }

    public static String displayAccelerator(String accelerator) {
        if (accelerator == null || accelerator.isBlank()) {
            return "";
        }
        if ("Shortcut+MouseWheel".equals(accelerator)) {
            return isMac() ? "Cmd + Wheel" : "Ctrl + Wheel";
        }

        String display = accelerator
                .replace("Ctrl", isMac() ? "Cmd" : "Ctrl")
                .replace("Shortcut", isMac() ? "Cmd" : "Ctrl")
                .replace("BACK_SLASH", "\\")
                .replace("Plus", "+")
                .replace("Minus", "-");
        try {
            KeyCombination combo = KeyCombination.keyCombination(accelerator);
            if (combo != null) {
                return display;
            }
        } catch (Exception ignored) {
            return display;
        }
        return display;
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }
}
