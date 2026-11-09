package dev.levrn.core;

public enum Theme {
    DUSK("Dusk", 0xFFC5B6FF),
    GLACIER("Glacier", 0xFF83DBDC),
    EMBER("Ember", 0xFFF4B28C),
    NOVA("Nova", 0xFFBEC8DF),
    MIDNIGHT("Midnight", 0xFF4A7CF7),
    CRIMSON("Crimson", 0xFFFF3B5C),
    PHANTOM("Phantom", 0xFFB44AFF),
    NEON("Neon", 0xFF39FF7F);
    public final String label;
    public final int accent;
    Theme(String label, int accent) { this.label = label; this.accent = accent; }
    public Theme next() { return values()[(ordinal() + 1) % values().length]; }
}
