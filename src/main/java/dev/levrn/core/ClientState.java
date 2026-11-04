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
        new Feature("criticals", "Criticals", "Time Aura/Trigger attacks to real descending jumps.", Category.COMBAT, false),
        new Feature("velocity", "Velocity", "Scale incoming entity knockback; server corrections remain authoritative.", Category.COMBAT, false),
        new Feature("w_tap", "W Tap", "Reset sprint briefly after Aura/Trigger attacks.", Category.COMBAT, false),
        new Feature("safe_walk", "Safe Walk", "Apply vanilla edge protection without forcing your sneak key.", Category.MOVEMENT, false),
        new Feature("auto_walk", "Auto Walk", "Walk forward automatically; stop while a screen is open.", Category.MOVEMENT, false),
        new Feature("inventory_move", "Inventory Move", "Read real movement keys inside inventory screens, without packet buffering.", Category.MOVEMENT, false),
        new Feature("auto_eat", "Auto Eat", "Select hotbar food and eat at low hunger, then restore your slot.", Category.PLAYER, false),
        new Feature("auto_fish", "Auto Fish", "Reel on the bobber bite state and recast using normal rod interactions.", Category.PLAYER, false),
        new Feature("fast_place", "Fast Place", "Use a bounded block placement delay while holding use.", Category.PLAYER, false),
        new Feature("no_fire", "No Fire Overlay", "Hide the first-person fire overlay without changing damage.", Category.VISUAL, false),
        new Feature("sprint", "Sprint", "Sprint while walking forward when food allows.", Category.MOVEMENT, false),
        new Feature("auto_jump", "Auto Jump", "Enable vanilla obstacle auto jump and restore it on disable.", Category.MOVEMENT, false),
        new Feature("auto_tool", "Auto Tool", "Select the fastest hotbar tool while mining.", Category.PLAYER, false),
        new Feature("auto_armor", "Auto Armor", "Equip stronger carried armor, respecting removal rules and Elytra.", Category.PLAYER, false),
        new Feature("auto_replenish", "Auto Replenish", "Refill matching hotbar stacks and restore depleted items from inventory.", Category.PLAYER, false),
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
    private double cooldown = 0.92, aimSpeed = 25, targetFov = 360, totemHealth = 12;
    private Theme theme = Theme.NOVA;
    private boolean reducedMotion, stickyTargets = true, armorUpgrade = true, preserveElytra = true;
    private double motionSpeed = 1.0, inventoryInterval = 4;
    private double windowX = 0.5, windowY = 0.5;
    private int replenishBelow = 8;
    public enum AuraRotation { SILENT, VISIBLE, OFF }
    private AuraRotation auraRotation = AuraRotation.SILENT;
    private double velocityHorizontal=0, velocityVertical=100, uiScale=1.5;
    private int eatHunger=14, placeDelay=2;
    public AuraRotation auraRotation() { return auraRotation; }
    public void setAuraRotation(AuraRotation value) { auraRotation=java.util.Objects.requireNonNull(value); }
    public double velocityHorizontal() { return velocityHorizontal; }
    public double velocityVertical() { return velocityVertical; }
    public void setVelocityHorizontal(double value) { if(Double.isFinite(value)) velocityHorizontal=Math.clamp(value,0,100); }
    public void setVelocityVertical(double value) { if(Double.isFinite(value)) velocityVertical=Math.clamp(value,0,100); }
    public double uiScale() { return uiScale; }
    public void setUiScale(double value) { if(Double.isFinite(value)) uiScale=Math.clamp(value,1,2); }
    public int eatHunger() { return eatHunger; }
    public void setEatHunger(int value) { eatHunger=Math.clamp(value,1,19); }
    public int placeDelay() { return placeDelay; }
    public void setPlaceDelay(int value) { placeDelay=Math.clamp(value,2,4); }

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
    public boolean reducedMotion() { return reducedMotion; }
    public void setReducedMotion(boolean value) { reducedMotion = value; }
    public boolean stickyTargets() { return stickyTargets; }
    public void setStickyTargets(boolean value) { stickyTargets = value; }
    public boolean armorUpgrade() { return armorUpgrade; }
    public void setArmorUpgrade(boolean value) { armorUpgrade = value; }
    public boolean preserveElytra() { return preserveElytra; }
    public void setPreserveElytra(boolean value) { preserveElytra = value; }
    public double motionSpeed() { return motionSpeed; }
    public void setMotionSpeed(double value) { if (Double.isFinite(value)) motionSpeed = Math.clamp(value, 0.5, 2.5); }
    public double inventoryInterval() { return inventoryInterval; }
    public void setInventoryInterval(double value) { if (Double.isFinite(value)) inventoryInterval = Math.clamp(Math.round(value), 2, 10); }
    public double windowX() { return windowX; }
    public double windowY() { return windowY; }
    public void setWindowX(double value) { if (Double.isFinite(value)) windowX = Math.clamp(value, 0, 1); }
    public void setWindowY(double value) { if (Double.isFinite(value)) windowY = Math.clamp(value, 0, 1); }
    public int replenishBelow() { return replenishBelow; }
    public void setReplenishBelow(int value) { replenishBelow = Math.clamp(value,1,32); }
    public List<Feature> search(Category category, String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return features.stream().filter(f -> category == null || f.category == category)
            .filter(f -> {
                if (needle.isEmpty()) return true;
                String haystack = (f.id + " " + f.name + " " + f.category.label).toLowerCase(Locale.ROOT);
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
        targetInvisible = false; rotateAura = true; cooldown = 0.92; aimSpeed = 25; targetFov = 360; totemHealth = 12;
        theme = Theme.NOVA;
        reducedMotion = false; stickyTargets = true; armorUpgrade = true; preserveElytra = true; motionSpeed = 1; inventoryInterval = 4;
        windowX = 0.5; windowY = 0.5;
        replenishBelow = 8; auraRotation=AuraRotation.SILENT; velocityHorizontal=0; velocityVertical=100; uiScale=1.5; eatHunger=14; placeDelay=2;
    }
}
