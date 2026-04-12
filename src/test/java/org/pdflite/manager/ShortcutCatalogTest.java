package org.pdflite.manager;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortcutCatalogTest {

    @Test
    void catalogDocumentsHelpAndPageNavigationShortcuts() {
        List<ShortcutCatalog.ShortcutEntry> entries = ShortcutCatalog.groups().stream()
                .flatMap(group -> group.entries().stream())
                .toList();

        assertTrue(entries.stream().anyMatch(entry ->
                "menu.help.keyboardShortcuts".equals(entry.labelKey()) && "? or Ctrl+K".equals(entry.accelerator())));
        assertTrue(entries.stream().anyMatch(entry ->
                "shortcuts.action.prevPage".equals(entry.labelKey()) && "Page Up".equals(entry.accelerator())));
        assertTrue(entries.stream().anyMatch(entry ->
                "shortcuts.action.nextPage".equals(entry.labelKey()) && "Page Down / Space".equals(entry.accelerator())));
    }
}
