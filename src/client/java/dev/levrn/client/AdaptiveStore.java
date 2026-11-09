package dev.levrn.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Persistence layer for the NOVA adaptive engine.
 *
 * <p>Manages saving and loading {@link ServerProfile}s, one per server the player
 * has visited.  Profiles are stored as properties files under
 * {@code levrn/nova/profiles/<server_hash>.properties}.
 *
 * <p>Auto-saves every 5 minutes and on disconnect.  Global settings (enabled state,
 * learning rate multiplier) are stored in {@code levrn/nova/global.properties}.
 */
final class AdaptiveStore {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("LVERN/NOVA");

    private static final long AUTO_SAVE_INTERVAL_MS = 5L * 60 * 1000; // 5 minutes

    private final Path configDir;
    private final Map<String, ServerProfile> loadedProfiles = new HashMap<>();
    private ServerProfile activeProfile;
    private long lastSaveMs;
    private long checkpointMs;
    private int checkpointEncounters;

    // ---- Global settings ----
    private boolean novaEnabled = false;
    private double learningRateMultiplier = 1.0;

    AdaptiveStore(Path baseConfigDir) {
        this.configDir = baseConfigDir.resolve("nova");
        this.lastSaveMs = System.currentTimeMillis();
    }

    // ---- Profile management ----

    /**
     * Get or create the profile for the given server IP.
     * If this is a new server, initializes from global defaults (transfer learning).
     */
    ServerProfile getOrCreateProfile(String serverIp) {
        checkpointMs=System.currentTimeMillis();checkpointEncounters=0;
        String hash = ServerProfile.hashServerIp(serverIp);

        // Check if already loaded
        if (loadedProfiles.containsKey(hash)) {
            activeProfile = loadedProfiles.get(hash);
            return activeProfile;
        }

        // Try loading from disk
        try {
            ServerProfile loaded = ServerProfile.load(configDir, hash);
            if (loaded != null) {
                loadedProfiles.put(hash, loaded);
                activeProfile = loaded;
                return loaded;
            }
        } catch (IOException e) {
            LOG.warn("NOVA: Failed to load profile for server {}: {}", hash, e.getMessage());
        }

        // New server — create with transfer learning from all known profiles
        ServerProfile newProfile = new ServerProfile(serverIp);
        List<ServerProfile> allProfiles = loadAllProfiles();
        if (!allProfiles.isEmpty()) {
            ServerProfile defaults = ServerProfile.computeGlobalDefaults(allProfiles);
            newProfile.initializeFromDefaults(defaults);
            LOG.info("NOVA: Initialized new server profile from {} known servers", allProfiles.size());
        } else {
            LOG.info("NOVA: Created fresh profile for new server");
        }

        loadedProfiles.put(hash, newProfile);
        activeProfile = newProfile;
        return newProfile;
    }

    /** Active profile for the current server, or null if not connected. */
    ServerProfile activeProfile() { return activeProfile; }

    /** Clear the active profile reference (on disconnect). */
    void clearActive() { activeProfile = null; }

    // ---- Save/Load ----

    /** Save the active profile and global settings. */
    void save() {
        lastSaveMs = System.currentTimeMillis();
        if (activeProfile != null) {
            try {
                activeProfile.save(configDir);
            } catch (IOException e) {
                LOG.error("NOVA: Failed to save active profile", e);
            }
        }
        saveGlobalSettings();
    }

    /** Save all loaded profiles. */
    void saveAll() {
        for (ServerProfile profile : loadedProfiles.values()) {
            try {
                profile.save(configDir);
            } catch (IOException e) {
                LOG.error("NOVA: Failed to save profile {}", profile.serverHash(), e);
            }
        }
        saveGlobalSettings();
        lastSaveMs = System.currentTimeMillis();
    }

    /** Called once per tick to handle auto-save. */
    void capture(AdaptiveEngine engine) {
        if(engine==null || activeProfile==null) return;
        System.arraycopy(engine.momentumArray(),0,activeProfile.momentum(),0,WeightMatrix.NUM_SETTINGS);
        System.arraycopy(engine.confidenceArray(),0,activeProfile.confidence(),0,WeightMatrix.NUM_SETTINGS);
        long now=System.currentTimeMillis();activeProfile.addPlayTime(Math.max(0,now-checkpointMs));checkpointMs=now;
        while(checkpointEncounters<engine.encounters()) {activeProfile.incrementMatchCount();checkpointEncounters++;}
        if(engine.encounters()<checkpointEncounters) checkpointEncounters=engine.encounters();
    }
    void tick(AdaptiveEngine engine) {
        long now = System.currentTimeMillis();
        if (now - lastSaveMs > AUTO_SAVE_INTERVAL_MS) {
            capture(engine);save();
        }
    }

    /** Load all profiles from disk (for transfer learning computation). */
    private List<ServerProfile> loadAllProfiles() {
        Path profileDir = configDir.resolve("profiles");
        if (!Files.isDirectory(profileDir)) return List.of();

        List<ServerProfile> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(profileDir)) {
            files.filter(p -> p.toString().endsWith(".properties")).forEach(p -> {
                String filename = p.getFileName().toString();
                String hash = filename.substring(0, filename.length() - ".properties".length());
                // Skip if already loaded
                if (loadedProfiles.containsKey(hash)) {
                    result.add(loadedProfiles.get(hash));
                    return;
                }
                try {
                    ServerProfile profile = ServerProfile.load(configDir, hash);
                    if (profile != null) {
                        result.add(profile);
                        loadedProfiles.put(hash, profile);
                    }
                } catch (IOException e) {
                    LOG.warn("NOVA: Skipping corrupt profile {}: {}", hash, e.getMessage());
                }
            });
        } catch (IOException e) {
            LOG.warn("NOVA: Could not list profile directory", e);
        }
        return result;
    }

    // ---- Global settings ----

    boolean isNovaEnabled() { return novaEnabled; }
    void setNovaEnabled(boolean value) { novaEnabled = value; }

    double learningRateMultiplier() { return learningRateMultiplier; }
    void setLearningRateMultiplier(double value) {
        learningRateMultiplier = Math.clamp(value, 0.1, 3.0);
    }

    void saveGlobalSettings() {
        try {
            Files.createDirectories(configDir);
            Path file = configDir.resolve("global.properties");
            Properties props = new Properties();
            props.setProperty("schema", "1");
            props.setProperty("enabled", Boolean.toString(novaEnabled));
            props.setProperty("learningRateMultiplier", Double.toString(learningRateMultiplier));

            Path temp = Files.createTempFile(configDir, ".global-", ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                    props.store(writer, "LEVRN NOVA global settings");
                }
                try {
                    Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            LOG.error("NOVA: Failed to save global settings", e);
        }
    }

    void loadGlobalSettings() {
        Path file = configDir.resolve("global.properties");
        if (!Files.exists(file)) return;
        try {
            Properties props = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            novaEnabled = Boolean.parseBoolean(props.getProperty("enabled", "false"));
            String lrm = props.getProperty("learningRateMultiplier");
            if (lrm != null) {
                try {
                    double value=Double.parseDouble(lrm);if(Double.isFinite(value)) learningRateMultiplier = Math.clamp(value, 0.1, 3.0);
                } catch (NumberFormatException ignored) { }
            }
        } catch (IOException e) {
            LOG.warn("NOVA: Could not load global settings: {}", e.getMessage());
        }
    }
}
