package dev.levrn.core;

public enum Theme {
    DUSK("Dusk", 0xFFC5B6FF), GLACIER("Glacier", 0xFF83DBDC), EMBER("Ember", 0xFFF4B28C);
    public final String label;
    public final int accent;
    Theme(String label, int accent) { this.label = label; this.accent = accent; }
    public Theme next() { return values()[(ordinal() + 1) % values().length]; }
}
