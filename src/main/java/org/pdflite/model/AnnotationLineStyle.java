package org.pdflite.model;

/**
 * Stroke pattern options for drawable annotations.
 */
public enum AnnotationLineStyle {
    SOLID("Solid"),
    DASHED("Dashed"),
    DOTTED("Dotted");

    private final String displayName;

    AnnotationLineStyle(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double[] getDashPattern(double lineWidth, double scale) {
        double scaledWidth = Math.max(1.0, lineWidth * scale);
        return switch (this) {
            case SOLID -> new double[0];
            case DASHED -> new double[]{scaledWidth * 4.0, scaledWidth * 2.0};
            case DOTTED -> new double[]{scaledWidth, scaledWidth * 2.0};
        };
    }

    public static AnnotationLineStyle fromString(String value) {
        if (value == null || value.isBlank()) {
            return SOLID;
        }

        for (AnnotationLineStyle style : values()) {
            if (style.name().equalsIgnoreCase(value) || style.displayName.equalsIgnoreCase(value)) {
                return style;
            }
        }
        return SOLID;
    }
}
