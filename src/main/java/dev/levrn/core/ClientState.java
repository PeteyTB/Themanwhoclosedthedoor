package dev.levrn.core;

import java.util.List;
import java.util.Locale;

public final class ClientState {
    private final List<Feature> features = List.of(
        new Feature("trigger", "Trigger", "Attack the entity under your crosshair at full cooldown.", Category.COMBAT, false),
        new Feature("aura", "Aura", "Attack the nearest visible target within normal reach.", Category.COMBAT, false),
        new Feature("sprint", "Sprint", "Sprint while walking forward when food allows.", Category.MOVEMENT, false),
        new Feature("auto_tool", "Auto Tool", "Select the fastest hotbar tool while mining.", Category.PLAYER, false),
        new Feature("zoom", "Zoom", "A configurable narrow field of view. Toggle with C.", Category.VISUAL, false),
        new Feature("brightness", "Brightness", "Set vanilla brightness to maximum, then restore it.", Category.VISUAL, false),
        new Feature("hud", "Watermark", "Levrn identity and frame rate in a compact panel.", Category.VISUAL, true),
        new Feature("coordinates", "Coordinates", "Position and dimension, always within view.", Category.VISUAL, true),
        new Feature("active_list", "Active Modules", "A right-aligned list of enabled modules.", Category.VISUAL, true),
        new Feature("target_hud", "Target HUD", "Name and health of the entity under your crosshair.", Category.VISUAL, false)
    );
    private double attackRange = 3.0;
    private int zoomFov = 30;
    private boolean targetPlayers;
    private Theme theme = Theme.DUSK;

    public List<Feature> features() { return features; }
    public Feature feature(String id) {
        return features.stream().filter(f -> f.id.equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown module: " + id));
    }
    public boolean enabled(String id) { return feature(id).enabled(); }
    public double attackRange() { return attackRange; }
    public void setAttackRange(double value) {
        if (Double.isFinite(value)) attackRange = Math.clamp(value, 1.0, 3.0);
    }
    public int zoomFov() { return zoomFov; }
    public void setZoomFov(int value) { zoomFov = Math.clamp(value, 10, 70); }
    public boolean targetPlayers() { return targetPlayers; }
    public void setTargetPlayers(boolean value) { targetPlayers = value; }
    public Theme theme() { return theme; }
    public void setTheme(Theme value) { theme = java.util.Objects.requireNonNull(value); }
    public List<Feature> search(Category category, String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return features.stream().filter(f -> category == null || f.category == category)
            .filter(f -> (f.name + " " + f.description).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    public void disableAll() { features.forEach(f -> f.setEnabled(false)); }
    public void reset() {
        features.forEach(f -> f.setEnabled(f.defaultEnabled));
        attackRange = 3.0; zoomFov = 30; targetPlayers = false;
        theme = Theme.DUSK;
    }
}
