package org.pdflite.manager;

import org.pdflite.model.PDFDocument;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.WeakHashMap;

/**
 * Manages page labels and custom numbering rules per document.
 */
public class PageLabelManager {

    public enum NumberingStyle {
        DECIMAL,
        ROMAN_UPPER,
        ROMAN_LOWER,
        LETTER_UPPER,
        LETTER_LOWER
    }

    private record LabelRule(int startPageIndex, NumberingStyle style, String prefix, int startNumber) {
    }

    private final Map<PDFDocument, List<LabelRule>> labelRulesByDocument = new WeakHashMap<>();

    public void initializeDocument(PDFDocument document) {
        if (document == null) {
            return;
        }
        labelRulesByDocument.computeIfAbsent(document, doc -> defaultRules());
    }

    public void resetToDefault(PDFDocument document) {
        if (document == null) {
            return;
        }
        labelRulesByDocument.put(document, defaultRules());
    }

    public void applyCustomRule(PDFDocument document, int startPageOneBased, NumberingStyle style,
                                String prefix, int startNumber) {
        if (document == null) {
            return;
        }

        int startPageIndex = Math.max(0, startPageOneBased - 1);
        NumberingStyle numberingStyle = style != null ? style : NumberingStyle.DECIMAL;
        String safePrefix = prefix != null ? prefix : "";
        int safeStartNumber = Math.max(1, startNumber);

        List<LabelRule> rules = labelRulesByDocument.computeIfAbsent(document, doc -> defaultRules());
        rules.removeIf(rule -> rule.startPageIndex() == startPageIndex);
        rules.add(new LabelRule(startPageIndex, numberingStyle, safePrefix, safeStartNumber));
        rules.sort(Comparator.comparingInt(LabelRule::startPageIndex));
    }

    public String getPageLabel(PDFDocument document, int pageIndex) {
        if (document == null || pageIndex < 0) {
            return "";
        }

        List<LabelRule> rules = labelRulesByDocument.computeIfAbsent(document, doc -> defaultRules());
        LabelRule rule = rules.getFirst();
        for (LabelRule candidate : rules) {
            if (candidate.startPageIndex() <= pageIndex) {
                rule = candidate;
            } else {
                break;
            }
        }

        int sequenceValue = rule.startNumber() + (pageIndex - rule.startPageIndex());
        return rule.prefix() + formatValue(sequenceValue, rule.style());
    }

    /**
     * Resolves user input to a physical page index.
     * Input can be an index (1-based) or a page label.
     */
    public OptionalInt resolvePageIndex(PDFDocument document, String input) {
        if (document == null || input == null || input.isBlank()) {
            return OptionalInt.empty();
        }

        String normalizedInput = normalize(input);
        try {
            int oneBased = Integer.parseInt(normalizedInput);
            if (oneBased >= 1 && oneBased <= document.getTotalPages()) {
                return OptionalInt.of(oneBased - 1);
            }
        } catch (NumberFormatException ignored) {
            // Try matching by page label below.
        }

        for (int i = 0; i < document.getTotalPages(); i++) {
            String label = getPageLabel(document, i);
            if (normalize(label).equalsIgnoreCase(normalizedInput)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    private List<LabelRule> defaultRules() {
        List<LabelRule> rules = new ArrayList<>();
        rules.add(new LabelRule(0, NumberingStyle.DECIMAL, "", 1));
        return rules;
    }

    private String formatValue(int value, NumberingStyle style) {
        return switch (style) {
            case DECIMAL -> String.valueOf(value);
            case ROMAN_UPPER -> toRoman(value);
            case ROMAN_LOWER -> toRoman(value).toLowerCase();
            case LETTER_UPPER -> toAlphabetic(value, true);
            case LETTER_LOWER -> toAlphabetic(value, false);
        };
    }

    private String toRoman(int value) {
        if (value <= 0) {
            return "0";
        }

        int[] numbers = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

        int remaining = value;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < numbers.length; i++) {
            while (remaining >= numbers[i]) {
                result.append(symbols[i]);
                remaining -= numbers[i];
            }
        }
        return result.toString();
    }

    private String toAlphabetic(int value, boolean upper) {
        if (value <= 0) {
            return "0";
        }

        int current = value;
        StringBuilder result = new StringBuilder();
        while (current > 0) {
            current--;
            char ch = (char) ((upper ? 'A' : 'a') + (current % 26));
            result.insert(0, ch);
            current /= 26;
        }
        return result.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "");
    }
}
