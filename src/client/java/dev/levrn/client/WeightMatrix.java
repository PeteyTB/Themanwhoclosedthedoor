package dev.levrn.client;

import java.util.Properties;

/**
 * The adaptive engine's connection weights — a 2D matrix linking every adjustable
 * setting to every performance signal.
 *
 * <p>Each cell {@code weights[settingIndex][signalIndex]} represents how strongly
 * the engine believes that a positive change in the given signal should push the
 * setting higher (positive weight) or lower (negative weight).
 *
 * <p>Weights start with hand-tuned biases and are refined via a Hebbian-like update
 * rule as the engine observes which adjustments actually improve performance.
 */
final class WeightMatrix {

    // ---- Setting indices ----
    static final int ATTACK_RANGE       = 0;
    static final int COOLDOWN           = 1;
    static final int AIM_SPEED          = 2;
    static final int TARGET_FOV         = 3;
    static final int VELOCITY_H         = 4;
    static final int VELOCITY_V         = 5;
    static final int TOTEM_HEALTH       = 6;
    static final int AURA_ENABLED       = 7;
    static final int TRIGGER_ENABLED    = 8;
    static final int CRIT_ENABLED       = 9;
    static final int WTAP_ENABLED       = 10;
    static final int AUTO_TOTEM_ENABLED = 11;
    static final int SHIELD_BREAKER     = 12;

    static final int NUM_SETTINGS = 13;
    static final int NUM_SIGNALS  = PerformanceSignals.Signal.values().length;

    /** Human-readable names for settings, aligned with the indices above. */
    static final String[] SETTING_NAMES = {
        "attackRange", "cooldown", "aimSpeed", "targetFov",
        "velocityH", "velocityV", "totemHealth",
        "aura", "trigger", "criticals", "wTap", "autoTotem", "shieldBreaker"
    };

    /** Step sizes for continuous settings; binary settings ignore this. */
    static final double[] STEP_SIZES = {
        0.05,   // attackRange
        0.005,  // cooldown
        1.0,    // aimSpeed
        5.0,    // targetFov
        2.0,    // velocityH
        2.0,    // velocityV
        0.5,    // totemHealth
        1.0,    // aura (binary — not used as step)
        1.0,    // trigger
        1.0,    // criticals
        1.0,    // wTap
        1.0,    // autoTotem
        1.0     // shieldBreaker
    };

    /** Min/max bounds for each continuous setting. Binary settings use 0/1. */
    static final double[][] BOUNDS = {
        { 1.0,  3.0},   // attackRange
        { 0.8,  1.0},   // cooldown
        { 1.0, 90.0},   // aimSpeed
        {15.0, 360.0},  // targetFov
        { 0.0, 100.0},  // velocityH
        { 0.0, 100.0},  // velocityV
        { 1.0,  40.0},  // totemHealth
        { 0.0,   1.0},  // aura (binary)
        { 0.0,   1.0},  // trigger
        { 0.0,   1.0},  // criticals
        { 0.0,   1.0},  // wTap
        { 0.0,   1.0},  // autoTotem
        { 0.0,   1.0},  // shieldBreaker
    };

    /** Whether a setting is binary (on/off) rather than continuous. */
    static boolean isBinary(int settingIndex) {
        return settingIndex >= AURA_ENABLED;
    }

    private final double[][] weights;

    WeightMatrix() {
        weights = new double[NUM_SETTINGS][NUM_SIGNALS];
        initializeDefaults();
    }

    // Signal index helpers — mirrors PerformanceSignals.Signal ordinals.
    private static final int S_KILL_RATE    = PerformanceSignals.Signal.KILL_RATE.ordinal();
    private static final int S_DEATH_RATE   = PerformanceSignals.Signal.DEATH_RATE.ordinal();
    private static final int S_KD_RATIO     = PerformanceSignals.Signal.KD_RATIO.ordinal();
    private static final int S_HIT_RATE     = PerformanceSignals.Signal.HIT_RATE.ordinal();
    private static final int S_CORRECTIONS  = PerformanceSignals.Signal.CORRECTION_RATE.ordinal();
    private static final int S_DAMAGE_EFF   = PerformanceSignals.Signal.DAMAGE_EFFICIENCY.ordinal();
    private static final int S_SURVIVAL     = PerformanceSignals.Signal.SURVIVAL_TIME.ordinal();
    private static final int S_TOTEM_POPS   = PerformanceSignals.Signal.TOTEM_POPS.ordinal();
    private static final int S_COMBAT_DUR   = PerformanceSignals.Signal.COMBAT_DURATION.ordinal();
    private static final int S_TARGET_ACQ   = PerformanceSignals.Signal.TARGET_ACQUISITION_TIME.ordinal();

    /** Load the hand-tuned starting biases. All un-set cells remain 0. */
    private void initializeDefaults() {
        // attackRange: corrections=-0.8, hitRate=+0.3, kdRatio=+0.2
        weights[ATTACK_RANGE][S_CORRECTIONS] = -0.8;
        weights[ATTACK_RANGE][S_HIT_RATE]    =  0.3;
        weights[ATTACK_RANGE][S_KD_RATIO]    =  0.2;

        // cooldown: corrections=-0.5, hitRate=+0.4, damageEff=+0.3
        weights[COOLDOWN][S_CORRECTIONS] = -0.5;
        weights[COOLDOWN][S_HIT_RATE]    =  0.4;
        weights[COOLDOWN][S_DAMAGE_EFF]  =  0.3;

        // aimSpeed: hitRate=+0.5, targetAcq=-0.4, corrections=-0.3
        weights[AIM_SPEED][S_HIT_RATE]    =  0.5;
        weights[AIM_SPEED][S_TARGET_ACQ]  = -0.4;
        weights[AIM_SPEED][S_CORRECTIONS] = -0.3;

        // targetFov: hitRate=+0.2, targetAcq=-0.3, kdRatio=+0.2
        weights[TARGET_FOV][S_HIT_RATE]   =  0.2;
        weights[TARGET_FOV][S_TARGET_ACQ] = -0.3;
        weights[TARGET_FOV][S_KD_RATIO]   =  0.2;

        // velocityH: survivalTime=+0.4, deathRate=-0.5
        weights[VELOCITY_H][S_SURVIVAL]   =  0.4;
        weights[VELOCITY_H][S_DEATH_RATE] = -0.5;

        // velocityV: survivalTime=+0.3, corrections=-0.4
        weights[VELOCITY_V][S_SURVIVAL]    =  0.3;
        weights[VELOCITY_V][S_CORRECTIONS] = -0.4;

        // totemHealth: totemPops=-0.3, survivalTime=+0.5, deathRate=-0.4
        weights[TOTEM_HEALTH][S_TOTEM_POPS] = -0.3;
        weights[TOTEM_HEALTH][S_SURVIVAL]   =  0.5;
        weights[TOTEM_HEALTH][S_DEATH_RATE] = -0.4;

        // auraEnabled: kdRatio=+0.6, corrections=-0.4, hitRate=+0.3
        weights[AURA_ENABLED][S_KD_RATIO]    =  0.6;
        weights[AURA_ENABLED][S_CORRECTIONS] = -0.4;
        weights[AURA_ENABLED][S_HIT_RATE]    =  0.3;

        // triggerEnabled: hitRate=+0.5, corrections=-0.3
        weights[TRIGGER_ENABLED][S_HIT_RATE]    =  0.5;
        weights[TRIGGER_ENABLED][S_CORRECTIONS] = -0.3;

        // critEnabled: kdRatio=+0.4, corrections=-0.5, damageEff=+0.3
        weights[CRIT_ENABLED][S_KD_RATIO]    =  0.4;
        weights[CRIT_ENABLED][S_CORRECTIONS] = -0.5;
        weights[CRIT_ENABLED][S_DAMAGE_EFF]  =  0.3;

        // wTapEnabled: kdRatio=+0.3, corrections=-0.6
        weights[WTAP_ENABLED][S_KD_RATIO]    =  0.3;
        weights[WTAP_ENABLED][S_CORRECTIONS] = -0.6;

        // autoTotemEnabled: survivalTime=+0.6, deathRate=-0.5, totemPops=+0.2
        weights[AUTO_TOTEM_ENABLED][S_SURVIVAL]    =  0.6;
        weights[AUTO_TOTEM_ENABLED][S_DEATH_RATE]  = -0.5;
        weights[AUTO_TOTEM_ENABLED][S_TOTEM_POPS]  =  0.2;

        // shieldBreaker: kdRatio=+0.2, damageEff=+0.3
        weights[SHIELD_BREAKER][S_KD_RATIO]   = 0.2;
        weights[SHIELD_BREAKER][S_DAMAGE_EFF] = 0.3;
    }

    /**
     * Compute the weighted gradient score for a setting given current signal values.
     *
     * <p>The gradient is the dot product of the setting's weight row with the signal
     * values, weighted by each signal's confidence so that unreliable signals have
     * reduced influence.
     *
     * @param settingIndex index into {@link #SETTING_NAMES}
     * @param signals      current performance signals with EMA values
     * @return the raw gradient (positive = increase setting, negative = decrease)
     */
    double gradient(int settingIndex, PerformanceSignals signals) {
        double sum = 0.0;
        for (PerformanceSignals.Signal s : PerformanceSignals.Signal.values()) {
            double w   = weights[settingIndex][s.ordinal()];
            double val = signals.get(s);
            double conf = signals.confidence(s);
            sum += w * val * conf;
        }
        return sum;
    }

    /**
     * Hebbian-like weight update: strengthen or weaken the connection between
     * a setting and its signals based on whether an adjustment improved performance.
     *
     * @param settingIndex  which setting was adjusted
     * @param signals       current signal values
     * @param learningRate  step size for the weight update (small, e.g. 0.01)
     * @param positive      true if the adjustment improved performance, false if it hurt
     */
    void updateWeights(int settingIndex, PerformanceSignals signals, double learningRate, boolean positive) {
        double sign = positive ? 1.0 : -0.5; // weaken more gently than strengthen
        for (PerformanceSignals.Signal s : PerformanceSignals.Signal.values()) {
            double val  = signals.get(s);
            double conf = signals.confidence(s);
            if (conf < 0.1 || Math.abs(val) < 1e-6) continue; // skip noise
            double delta = learningRate * sign * val * conf;
            weights[settingIndex][s.ordinal()] = clampWeight(weights[settingIndex][s.ordinal()] + delta);
        }
    }

    /** Keep weights within a reasonable range to prevent runaway growth. */
    private static double clampWeight(double w) {
        return Math.clamp(w, -2.0, 2.0);
    }

    /** Direct access for inspection / transfer learning. */
    double getWeight(int setting, int signal) {
        return weights[setting][signal];
    }

    void setWeight(int setting, int signal, double value) {
        if(!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite weight");
        weights[setting][signal] = clampWeight(value);
    }

    // ---- Persistence ----

    void serialize(Properties props, String prefix) {
        for (int s = 0; s < NUM_SETTINGS; s++) {
            for (int g = 0; g < NUM_SIGNALS; g++) {
                {
                    props.setProperty(prefix + s + "." + g, Double.toString(weights[s][g]));
                }
            }
        }
    }

    void deserialize(Properties props, String prefix) {
        for (int s = 0; s < NUM_SETTINGS; s++) {
            for (int g = 0; g < NUM_SIGNALS; g++) {
                String v = props.getProperty(prefix + s + "." + g);
                if (v != null) {
                    try {
                        double val = Double.parseDouble(v);
                        if (Double.isFinite(val)) weights[s][g] = clampWeight(val);
                    } catch (NumberFormatException ignored) { /* keep default */ }
                }
            }
        }
    }

    /** Blend another matrix into this one (for transfer learning). */
    void blendFrom(WeightMatrix other, double otherWeight) {
        double thisWeight = 1.0 - otherWeight;
        for (int s = 0; s < NUM_SETTINGS; s++) {
            for (int g = 0; g < NUM_SIGNALS; g++) {
                weights[s][g] = clampWeight(thisWeight * weights[s][g] + otherWeight * other.weights[s][g]);
            }
        }
    }

    /** Deep copy. */
    WeightMatrix copy() {
        WeightMatrix clone = new WeightMatrix();
        for (int s = 0; s < NUM_SETTINGS; s++) {
            System.arraycopy(this.weights[s], 0, clone.weights[s], 0, NUM_SIGNALS);
        }
        return clone;
    }
}
