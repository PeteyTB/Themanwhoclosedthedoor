package dev.levrn.client;

import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/**
 * Tracks real-time combat performance metrics using exponential moving averages (EMA).
 *
 * <p>Each signal is smoothed so that recent observations matter more than old ones,
 * but history is never entirely lost.  A configurable alpha controls the tradeoff
 * (default 0.15 — roughly a half-life of ~4 observations).
 *
 * <p>Confidence for each signal grows toward 1.0 over approximately 30 observations,
 * giving the adaptive engine a measure of how much data backs each metric.
 */
final class PerformanceSignals {

    /** Every metric the adaptive engine can observe. */
    enum Signal {
        KILL_RATE,              // kills per minute of active combat
        DEATH_RATE,             // deaths per minute
        KD_RATIO,               // running kills / deaths ratio
        HIT_RATE,               // successful attacks / total attacks
        CORRECTION_RATE,        // server corrections per minute
        DAMAGE_EFFICIENCY,      // damage dealt / damage taken
        SURVIVAL_TIME,          // average seconds alive between deaths
        TOTEM_POPS,             // totem uses per life
        COMBAT_DURATION,        // average fight length in seconds
        TARGET_ACQUISITION_TIME // average ticks to lock onto a new target
    }

    private final double alpha;
    private final EnumMap<Signal, Double> values = new EnumMap<>(Signal.class);
    private final EnumMap<Signal, Integer> observationCounts = new EnumMap<>(Signal.class);

    PerformanceSignals() {
        this(0.15);
    }

    PerformanceSignals(double alpha) {
        this.alpha = Math.clamp(alpha, 0.01, 1.0);
        reset();
    }

    /** Feed a new observation for the given signal. */
    void record(Signal signal, double value) {
        if (!Double.isFinite(value)) return;
        int count = observationCounts.getOrDefault(signal, 0);
        if (count == 0) {
            // First observation — seed the EMA directly.
            values.put(signal, value);
        } else {
            double prev = values.getOrDefault(signal, 0.0);
            values.put(signal, alpha * value + (1.0 - alpha) * prev);
        }
        observationCounts.put(signal, count + 1);
    }

    /** Current smoothed value. Returns 0.0 if no observations yet. */
    double get(Signal signal) {
        return values.getOrDefault(signal, 0.0);
    }

    /**
     * Confidence in this signal's EMA, from 0.0 (no data) to 1.0 (saturated).
     * Reaches 1.0 after approximately 30 observations using a saturating curve.
     */
    double confidence(Signal signal) {
        int n = observationCounts.getOrDefault(signal, 0);
        // 1 - e^(-n/10) reaches ~0.95 at n=30
        return Math.min(1.0, 1.0 - Math.exp(-n / 10.0));
    }

    /** Number of raw observations recorded for the given signal. */
    int observations(Signal signal) {
        return observationCounts.getOrDefault(signal, 0);
    }

    /** Clear all accumulated data (e.g. when joining a new server for the first time). */
    void reset() {
        for (Signal s : Signal.values()) {
            values.put(s, 0.0);
            observationCounts.put(s, 0);
        }
    }

    // ---- Persistence ----

    /** Write all EMA values and observation counts into the given properties map. */
    void serialize(Properties props, String prefix) {
        for (Signal s : Signal.values()) {
            String key = prefix + s.name();
            props.setProperty(key + ".value", Double.toString(values.getOrDefault(s, 0.0)));
            props.setProperty(key + ".count", Integer.toString(observationCounts.getOrDefault(s, 0)));
        }
    }

    /** Restore state from properties previously written by {@link #serialize}. */
    void deserialize(Properties props, String prefix) {
        for (Signal s : Signal.values()) {
            String key = prefix + s.name();
            String v = props.getProperty(key + ".value");
            String c = props.getProperty(key + ".count");
            if (v != null && c != null) {
                try {
                    double val = Double.parseDouble(v);
                    int cnt = Integer.parseInt(c);
                    if (Double.isFinite(val) && cnt >= 0) {
                        values.put(s, val);
                        observationCounts.put(s, cnt);
                    }
                } catch (NumberFormatException ignored) { /* keep defaults */ }
            }
        }
    }

    /** Copy all values and counts from another instance (for transfer learning). */
    void copyFrom(PerformanceSignals other) {
        for (Signal s : Signal.values()) {
            values.put(s, other.values.getOrDefault(s, 0.0));
            observationCounts.put(s, other.observationCounts.getOrDefault(s, 0));
        }
    }

    /** Return a snapshot of every signal's current EMA for read-only iteration. */
    Map<Signal, Double> snapshot() {
        return new EnumMap<>(values);
    }
}
