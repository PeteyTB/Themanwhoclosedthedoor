package dev.levrn.core;

import java.util.List;
import java.util.Locale;

public final class ClientState {
    private final List<Feature> features = List.of(
        new Feature("trigger", "Trigger", "Attack the entity under your crosshair at full cooldown.", Category.COMBAT, false),
        new Feature("aura", "Aura", "Attack the nearest visible target within normal reach.", Category.COMBAT, false),
        new Feature("aim_assist", "Aim Assist", "Smooth visible aim while holding attack; does not click.", Category.COMBAT, false),
        new Feature("auto_weapon", "Auto Weapon", "Choose the highest attack damage hotbar weapon for combat.", Category.COMBAT, false),
        new Feature("shield_breaker", "Shield Breaker", "Prefer an axe against a target actively blocking.", Category.COMBAT, false),
        new Feature("auto_totem", "Auto Totem", "Equip a carried totem in the offhand at low health.", Category.COMBAT, false),
        new Feature("sprint", "Sprint", "Sprint while walking forward when food allows.", Category.MOVEMENT, false),
        new Feature("auto_jump", "Auto Jump", "Enable vanilla obstacle auto jump and restore it on disable.", Category.MOVEMENT, false),
        new Feature("auto_tool", "Auto Tool", "Select the fastest hotbar tool while mining.", Category.PLAYER, false),
        new Feature("auto_armor", "Auto Armor", "Fill empty armor slots from carried equipment.", Category.PLAYER, false),
        new Feature("auto_respawn", "Auto Respawn", "Request respawn after the normal death delay.", Category.PLAYER, false),
        new Feature("zoom", "Zoom", "A configurable narrow field of view. Toggle with C.", Category.VISUAL, false),
        new Feature("brightness", "Brightness", "Set vanilla brightness to maximum, then restore it.", Category.VISUAL, false),
        new Feature("no_bob", "No Bob", "Remove view bobbing while this module is enabled.", Category.VISUAL, false),
        new Feature("no_fov", "Stable FOV", "Remove movement-induced field of view changes.", Category.VISUAL, false),
        new Feature("no_hurt_cam", "No Hurt Cam", "Remove damage camera tilt without changing damage.", Category.VISUAL, false),
        new Feature("no_nausea", "No Nausea", "Suppress screen distortion and restore your setting on disable.", Category.VISUAL, false),
        new Feature("no_darkness", "No Darkness", "Suppress darkness screen intensity effects.", Category.VISUAL, false),
        new Feature("hud", "Watermark", "Levrn identity and frame rate in a compact panel.", Category.VISUAL, true),
        new Feature("coordinates", "Coordinates", "Position and dimension, always within view.", Category.VISUAL, true),
        new Feature("active_list", "Active Modules", "A right-aligned list of enabled modules.", Category.VISUAL, true),
        new Feature("target_hud", "Target HUD", "Name and health of the current combat or crosshair target.", Category.VISUAL, false),
        new Feature("combat_status", "Combat Status", "Show targeting filters, cooldown, and sent-attack diagnostics.", Category.VISUAL, true),
        new Feature("armor_hud", "Armor HUD", "Show durability of each equipped armor piece.", Category.VISUAL, false),
        new Feature("speed_hud", "Speed HUD", "Show horizontal movement speed in blocks per second.", Category.VISUAL, false),
        new Feature("ping_hud", "Ping HUD", "Show the server-reported connection latency.", Category.VISUAL, false),
        new Feature("clock", "Clock", "A compact local-time clock in your HUD.", Category.VISUAL, false),
        new Feature("compass", "Compass", "Show your current cardinal heading and yaw.", Category.VISUAL, false),
        new Feature("keystrokes", "Keystrokes", "Display movement, jump, and attack input state.", Category.VISUAL, false),
        new Feature("potion_hud", "Potion HUD", "List your active status effects and remaining duration.", Category.VISUAL, false)
    );
    private double attackRange = 3.0;
    private int zoomFov = 30;
    private boolean targetPlayers = true, targetMobs = false, respectTeams, targetInvisible, rotateAura = true;
    private double cooldown = 0.92, aimSpeed = 25, targetFov = 180, totemHealth = 12;
    private Theme theme = Theme.NOVA;

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
    public boolean targetMobs() { return targetMobs; }
    public void setTargetMobs(boolean value) { targetMobs = value; }
    public boolean respectTeams() { return respectTeams; }
    public void setRespectTeams(boolean value) { respectTeams = value; }
    public boolean targetInvisible() { return targetInvisible; }
    public void setTargetInvisible(boolean value) { targetInvisible = value; }
    public boolean rotateAura() { return rotateAura; }
    public void setRotateAura(boolean value) { rotateAura = value; }
    public double cooldown() { return cooldown; }
    public void setCooldown(double value) { if (Double.isFinite(value)) cooldown = Math.clamp(value, 0.8, 1); }
    public double aimSpeed() { return aimSpeed; }
    public void setAimSpeed(double value) { if (Double.isFinite(value)) aimSpeed = Math.clamp(value, 1, 90); }
    public double targetFov() { return targetFov; }
    public void setTargetFov(double value) { if (Double.isFinite(value)) targetFov = Math.clamp(value, 15, 360); }
    public double totemHealth() { return totemHealth; }
    public void setTotemHealth(double value) { if (Double.isFinite(value)) totemHealth = Math.clamp(value, 1, 40); }
    public Theme theme() { return theme; }
    public void setTheme(Theme value) { theme = java.util.Objects.requireNonNull(value); }
    public List<Feature> search(Category category, String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return features.stream().filter(f -> category == null || f.category == category)
            .filter(f -> {
                if (needle.isEmpty()) return true;
                String haystack = (f.name + " " + f.description + " " + f.category.label).toLowerCase(Locale.ROOT);
                for (String group : needle.split("[,|]+")) {
                    if (!group.isBlank() && java.util.Arrays.stream(group.strip().split("\\s+")).allMatch(haystack::contains)) return true;
                }
                return false;
            }).toList();
    }
    public void disableAll() { features.forEach(f -> f.setEnabled(false)); }
    public void reset() {
        features.forEach(f -> f.setEnabled(f.defaultEnabled));
        attackRange = 3.0; zoomFov = 30; targetPlayers = true; targetMobs = false; respectTeams = false;
        targetInvisible = false; rotateAura = true; cooldown = 0.92; aimSpeed = 25; targetFov = 180; totemHealth = 12;
        theme = Theme.NOVA;
    }
}
