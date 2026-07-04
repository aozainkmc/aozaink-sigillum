package com.aozainkmc.sigillum.grade;

public enum TalismanGrade {
    EXQUISITE("极品", 0.95, 1.00f),
    FINE("良品", 0.75, 0.80f),
    INFERIOR("劣质", 0.50, 0.55f),
    WASTE("废符", 0.00, 0.00f);

    private final String display;
    private final double threshold;
    private final float multiplier;

    TalismanGrade(String display, double threshold, float multiplier) {
        this.display = display;
        this.threshold = threshold;
        this.multiplier = multiplier;
    }

    public String display() {
        return display;
    }

    public float multiplier() {
        return multiplier;
    }

    public static TalismanGrade gradeOf(double composite) {
        if (composite >= EXQUISITE.threshold) return EXQUISITE;
        if (composite >= FINE.threshold) return FINE;
        if (composite >= INFERIOR.threshold) return INFERIOR;
        return WASTE;
    }

    public static TalismanGrade byName(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public TalismanGrade nextLower() {
        return switch (this) {
            case EXQUISITE -> FINE;
            case FINE -> INFERIOR;
            case INFERIOR -> WASTE;
            case WASTE -> WASTE;
        };
    }
}
