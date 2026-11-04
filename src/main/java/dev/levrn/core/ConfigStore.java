package dev.levrn.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Only known fields are accepted. Parse fully before mutating the live state. */
public final class ConfigStore {
    private final Path path;
    public ConfigStore(Path path) { this.path = path.toAbsolutePath(); }

    public void load(ClientState state) throws IOException {
        if (!Files.exists(path)) return;
        if (Files.size(path) > 1_048_576) throw new IOException("Levrn config exceeds 1 MiB");
        Properties p = new Properties();
        ClientState parsed = new ClientState();
        try {
            try (Reader reader = Files.newBufferedReader(path)) { p.load(reader); }
            String schema = p.getProperty("schema", "1");
            if (!schema.equals("1") && !schema.equals("2") && !schema.equals("3")) throw new IllegalArgumentException("Unsupported config schema");
            for (Feature f : parsed.features()) {
                String value = p.getProperty("module." + f.id);
                if (value != null) f.setEnabled(parseBoolean(value));
            }
            double range = Double.parseDouble(p.getProperty("combat.range", "3.0"));
            if (!Double.isFinite(range)) throw new IllegalArgumentException("Non-finite range");
            parsed.setAttackRange(range);
            parsed.setZoomFov(Integer.parseInt(p.getProperty("visual.zoomFov", "30")));
            parsed.setTargetPlayers(parseBoolean(p.getProperty("combat.players", "true")));
            parsed.setTargetMobs(parseBoolean(p.getProperty("combat.mobs", schema.equals("1") ? "true" : "false")));
            parsed.setRespectTeams(parseBoolean(p.getProperty("combat.teams", "false")));
            parsed.setTargetInvisible(parseBoolean(p.getProperty("combat.invisible", "false")));
            parsed.setRotateAura(parseBoolean(p.getProperty("combat.rotate", "true")));
            parsed.setCooldown(number(p, "combat.cooldown", 0.92));
            parsed.setAimSpeed(number(p, "combat.aimSpeed", 25));
            parsed.setTargetFov(number(p, "combat.fov", 180));
            parsed.setTotemHealth(number(p, "combat.totemHealth", 12));
            parsed.setTheme(Theme.valueOf(p.getProperty("visual.theme", "NOVA")));
            parsed.setReducedMotion(parseBoolean(p.getProperty("ui.reducedMotion", "false")));
            parsed.setMotionSpeed(number(p, "ui.motionSpeed", 1));
            parsed.setStickyTargets(parseBoolean(p.getProperty("combat.stickyTargets", "true")));
            parsed.setArmorUpgrade(parseBoolean(p.getProperty("inventory.upgradeArmor", "true")));
            parsed.setPreserveElytra(parseBoolean(p.getProperty("inventory.preserveElytra", "true")));
            parsed.setInventoryInterval(number(p, "inventory.interval", 4));
            parsed.setWindowX(number(p, "ui.windowX", 0.5)); parsed.setWindowY(number(p, "ui.windowY", 0.5));
            parsed.setReplenishBelow((int)number(p,"inventory.replenishBelow",8));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid Levrn config; original file preserved", e);
        }
        for (Feature f : state.features()) f.setEnabled(parsed.enabled(f.id));
        state.setAttackRange(parsed.attackRange());
        state.setZoomFov(parsed.zoomFov());
        state.setTargetPlayers(parsed.targetPlayers());
        state.setTheme(parsed.theme());
        state.setTargetMobs(parsed.targetMobs()); state.setRespectTeams(parsed.respectTeams());
        state.setTargetInvisible(parsed.targetInvisible()); state.setRotateAura(parsed.rotateAura());
        state.setCooldown(parsed.cooldown()); state.setAimSpeed(parsed.aimSpeed());
        state.setTargetFov(parsed.targetFov()); state.setTotemHealth(parsed.totemHealth());
        state.setReducedMotion(parsed.reducedMotion()); state.setMotionSpeed(parsed.motionSpeed());
        state.setStickyTargets(parsed.stickyTargets()); state.setArmorUpgrade(parsed.armorUpgrade());
        state.setPreserveElytra(parsed.preserveElytra()); state.setInventoryInterval(parsed.inventoryInterval());
        state.setWindowX(parsed.windowX()); state.setWindowY(parsed.windowY());
        state.setReplenishBelow(parsed.replenishBelow());
    }

    private static double number(Properties properties, String key, double fallback) {
        double result = Double.parseDouble(properties.getProperty(key, Double.toString(fallback)));
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Invalid number: " + key);
        return result;
    }

    private static boolean parseBoolean(String value) {
        if (value.equals("true")) return true;
        if (value.equals("false")) return false;
        throw new IllegalArgumentException("Expected true or false");
    }

    public void save(ClientState state) throws IOException {
        Properties p = new Properties();
        p.setProperty("schema", "3");
        for (Feature f : state.features()) p.setProperty("module." + f.id, Boolean.toString(f.enabled()));
        p.setProperty("combat.range", Double.toString(state.attackRange()));
        p.setProperty("combat.players", Boolean.toString(state.targetPlayers()));
        p.setProperty("visual.zoomFov", Integer.toString(state.zoomFov()));
        p.setProperty("visual.theme", state.theme().name());
        p.setProperty("combat.mobs", Boolean.toString(state.targetMobs()));
        p.setProperty("combat.teams", Boolean.toString(state.respectTeams()));
        p.setProperty("combat.invisible", Boolean.toString(state.targetInvisible()));
        p.setProperty("combat.rotate", Boolean.toString(state.rotateAura()));
        p.setProperty("combat.cooldown", Double.toString(state.cooldown()));
        p.setProperty("combat.aimSpeed", Double.toString(state.aimSpeed()));
        p.setProperty("combat.fov", Double.toString(state.targetFov()));
        p.setProperty("combat.totemHealth", Double.toString(state.totemHealth()));
        p.setProperty("ui.reducedMotion", Boolean.toString(state.reducedMotion()));
        p.setProperty("ui.motionSpeed", Double.toString(state.motionSpeed()));
        p.setProperty("combat.stickyTargets", Boolean.toString(state.stickyTargets()));
        p.setProperty("inventory.upgradeArmor", Boolean.toString(state.armorUpgrade()));
        p.setProperty("inventory.preserveElytra", Boolean.toString(state.preserveElytra()));
        p.setProperty("inventory.interval", Double.toString(state.inventoryInterval()));
        p.setProperty("ui.windowX", Double.toString(state.windowX())); p.setProperty("ui.windowY", Double.toString(state.windowY()));
        p.setProperty("inventory.replenishBelow",Integer.toString(state.replenishBelow()));
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), ".levrn-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp)) { p.store(writer, "Levrn profile v3"); }
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
