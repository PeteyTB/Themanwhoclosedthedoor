package dev.levrn.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Properties;

/**
 * Per-server learned configuration for the adaptive engine.
 *
 * <p>Each server the player has visited gets its own profile, keyed by a SHA-256
 * hash of the server IP for privacy.  The profile stores the weight matrix,
 * performance signal EMAs, momentum/confidence vectors, and play-time statistics.
 *
 * <p>When connecting to a brand-new server, the engine initializes from global
 * defaults computed as a weighted average of all known profiles (transfer learning).
 */
final class ServerProfile {

    private final String serverHash;
    private final WeightMatrix weightMatrix;
    private final PerformanceSignals signals;
    private final double[] momentum;
    private final double[] confidence;
    private int matchCount;
    private long totalPlayTimeMs;

    ServerProfile(String serverIp) {
        this.serverHash = hashServerIp(serverIp);
        this.weightMatrix = new WeightMatrix();
        this.signals = new PerformanceSignals();
        this.momentum = new double[WeightMatrix.NUM_SETTINGS];
        this.confidence = new double[WeightMatrix.NUM_SETTINGS];
    }

    /** Create with an already-computed hash (for loading from disk). */
    private ServerProfile(String hash, boolean isHash) {
        this.serverHash = hash;
        this.weightMatrix = new WeightMatrix();
        this.signals = new PerformanceSignals();
        this.momentum = new double[WeightMatrix.NUM_SETTINGS];
        this.confidence = new double[WeightMatrix.NUM_SETTINGS];
    }

    // ---- Accessors ----

    String serverHash() { return serverHash; }
    WeightMatrix weightMatrix() { return weightMatrix; }
    PerformanceSignals signals() { return signals; }
    double[] momentum() { return momentum; }
    double[] confidence() { return confidence; }
    int matchCount() { return matchCount; }
    long totalPlayTimeMs() { return totalPlayTimeMs; }

    void incrementMatchCount() { matchCount++; }
    void addPlayTime(long ms) { totalPlayTimeMs += ms; }

    // ---- Persistence ----

    /** Save this profile to a properties file in the given directory. */
    void save(Path configDir) throws IOException {
        Path dir = configDir.resolve("profiles");
        Files.createDirectories(dir);
        Path file = dir.resolve(serverHash + ".properties");

        Properties props = new Properties();
        props.setProperty("schema", "1");
        props.setProperty("matchCount", Integer.toString(matchCount));
        props.setProperty("totalPlayTimeMs", Long.toString(totalPlayTimeMs));

        weightMatrix.serialize(props, "w.");
        signals.serialize(props, "s.");

        for (int i = 0; i < WeightMatrix.NUM_SETTINGS; i++) {
            props.setProperty("m." + i, Double.toString(momentum[i]));
            props.setProperty("c." + i, Double.toString(confidence[i]));
        }

        // Atomic write
        Path temp = Files.createTempFile(dir, ".profile-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                props.store(writer, "LEVRN NOVA profile v1 — " + serverHash);
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Load a profile from a properties file. Returns null if the file does not exist. */
    static ServerProfile load(Path configDir, String hash) throws IOException {
        Path file = configDir.resolve("profiles").resolve(hash + ".properties");
        if (!Files.exists(file)) return null;
        if (Files.size(file) > 1_048_576) throw new IOException("Profile too large: " + hash);

        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        }

        String schema = props.getProperty("schema", "1");
        if (!schema.equals("1")) throw new IOException("Unsupported profile schema: " + schema);

        ServerProfile profile = new ServerProfile(hash, true);
        try {
            profile.matchCount = Integer.parseInt(props.getProperty("matchCount", "0"));
            profile.totalPlayTimeMs = Long.parseLong(props.getProperty("totalPlayTimeMs", "0"));
        } catch (NumberFormatException e) {
            throw new IOException("Corrupt profile metadata: " + hash, e);
        }

        profile.weightMatrix.deserialize(props, "w.");
        profile.signals.deserialize(props, "s.");

        for (int i = 0; i < WeightMatrix.NUM_SETTINGS; i++) {
            String m = props.getProperty("m." + i);
            String c = props.getProperty("c." + i);
            if (m != null) {
                try {double value=Double.parseDouble(m);if(Double.isFinite(value)) profile.momentum[i] = Math.clamp(value,-100,100);}
                catch (NumberFormatException ignored) { }
            }
            if (c != null) {
                try {double value=Double.parseDouble(c);if(Double.isFinite(value)) profile.confidence[i] = Math.clamp(value, 0, 1);}
                catch (NumberFormatException ignored) { }
            }
        }

        return profile;
    }

    // ---- Transfer learning ----

    /**
     * Compute global defaults from all known profiles, weighted by match count.
     *
     * <p>This implements transfer learning: a new server starts from the weighted
     * average of all servers the player has experience with.  Servers with more
     * matches have proportionally more influence.
     *
     * @param allProfiles all saved profiles (from different servers)
     * @return a fresh profile initialized with the blended data, or a default
     *         profile if no prior data exists
     */
    static ServerProfile computeGlobalDefaults(List<ServerProfile> allProfiles) {
        ServerProfile global = new ServerProfile("__global__");
        if (allProfiles.isEmpty()) return global;

        long totalMatches = 0;
        for (ServerProfile p : allProfiles) totalMatches += Math.max(1, p.matchCount);
        if (totalMatches == 0) return global;

        for(int setting=0;setting<WeightMatrix.NUM_SETTINGS;setting++) for(int signal=0;signal<WeightMatrix.NUM_SIGNALS;signal++) {
            double sum=0;for(ServerProfile p:allProfiles) sum+=p.weightMatrix.getWeight(setting,signal)*Math.max(1,p.matchCount)/totalMatches;
            global.weightMatrix.setWeight(setting,signal,sum);
        }

        // Weighted blend of signal EMAs — pick the profile with the most matches
        // as the base, since blending EMAs is less meaningful
        ServerProfile best = allProfiles.get(0);
        for (ServerProfile p : allProfiles) {
            if (p.matchCount > best.matchCount) best = p;
        }
        global.signals.copyFrom(best.signals);

        // Average the momentum and confidence vectors
        for (int i = 0; i < WeightMatrix.NUM_SETTINGS; i++) {
            double mSum = 0, cSum = 0;
            for (ServerProfile p : allProfiles) {
                double w = (double) Math.max(1, p.matchCount) / totalMatches;
                mSum += p.momentum[i] * w;
                cSum += p.confidence[i] * w;
            }
            global.momentum[i] = mSum;
            global.confidence[i] = Math.clamp(cSum, 0, 1);
        }

        return global;
    }

    /** Apply the data from a global-defaults profile onto this one (for a new server). */
    void initializeFromDefaults(ServerProfile defaults) {
        weightMatrix.blendFrom(defaults.weightMatrix, 1.0); // full copy
        signals.copyFrom(defaults.signals);
        System.arraycopy(defaults.momentum, 0, momentum, 0, WeightMatrix.NUM_SETTINGS);
        System.arraycopy(defaults.confidence, 0, confidence, 0, WeightMatrix.NUM_SETTINGS);
        // Reduce confidence slightly — this is a new environment
        for (int i = 0; i < WeightMatrix.NUM_SETTINGS; i++) {
            confidence[i] *= 0.5;
        }
    }

    // ---- Hashing ----

    /** SHA-256 hash of the server IP, truncated to 16 hex characters for filenames. */
    static String hashServerIp(String serverIp) {
        if (serverIp == null || serverIp.isBlank()) return "singleplayer";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(serverIp.strip().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed in every JRE — this cannot happen
            throw new AssertionError("SHA-256 not available", e);
        }
    }
}
