package dev.levrn.core;

public enum Category {
    COMBAT("Combat"), MOVEMENT("Movement"), PLAYER("Player"), VISUAL("Visual");
    public final String label;
    Category(String label) { this.label = label; }
}
