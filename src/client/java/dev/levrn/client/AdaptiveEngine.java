package dev.levrn.client;

import dev.levrn.core.ClientState;

import java.util.*;

/**
 * Self-learning adaptive combat engine for LEVRN.
 *
 * <p>The engine observes combat performance signals (kills, deaths, hit rates,
 * server corrections, etc.) and uses a momentum-stabilized gradient system to
 * gradually adjust the client's combat settings toward optimal values.
 *
 * <h2>Anti-oscillation guarantees</h2>
 * <ul>
 *   <li><b>Momentum smoothing</b> (0.9 decay) absorbs short-term noise so that a
 *       single spike in corrections does not whiplash a setting back and forth.</li>
 *   <li><b>Oscillation counter</b> — when the adjustment direction flips too often,
 *       the setting is frozen for several evaluation cycles.</li>
 *   <li><b>Confidence ramp</b> — high confidence (many observations confirming the
 *       current value) shrinks the learning rate, making converged settings stable.</li>
 *   <li><b>tanh bounding</b> — even extreme gradients produce bounded deltas.</li>
 * </ul>
 *
 * <h2>Integration</h2>
 * Event callbacks ({@link #onKill()}, {@link #onDeath()}, etc.) should be wired from
 * {@link ModuleRuntime} and other hooks.  The engine is designed to be called from the
 * client thread only — no synchronization is provided.
 */
final class AdaptiveEngine {

    // ---- Tuning constants ----
    private static final double BASE_LEARNING_RATE     = 0.05;
    private static final double MOMENTUM_DECAY         = 0.9;
    private static final double MOMENTUM_INPUT         = 0.1;
    private static final double MOMENTUM_THRESHOLD     = 0.05;
    private static final int    OSCILLATION_FREEZE     = 5;  // evaluations to skip after oscillation detected
    private static final int    OSCILLATION_TRIGGER    = 3;  // direction flips before freeze
    private static final double CONFIDENCE_INCREMENT   = 0.02;
    private static final double CONFIDENCE_DECAY       = 0.005; // per 10 minutes of play
    private static final long   CONFIDENCE_DECAY_INTERVAL_MS = 10L * 60 * 1000; // 10 minutes
    private static final int    BINARY_EVAL_INTERVAL   = 5;  // evaluate binary settings every N cycles
    private static final int    BINARY_MIN_OBSERVATIONS = 3; // need this many ON and OFF observations
    private static final double META_LEARNING_RATE     = 0.01; // weight update rate
    private static final int    HISTORY_SIZE           = 20;
    private static final long   PERIODIC_EVAL_MS       = 60_000; // 60 seconds between periodic evaluations

    // ---- Performance score weights ----
    private static final double W_KD_RATIO       = 0.35;
    private static final double W_HIT_RATE       = 0.20;
    private static final double W_DAMAGE_EFF     = 0.20;
    private static final double W_SURVIVAL       = 0.15;
    private static final double W_CORRECTIONS    = 0.10;

    // ---- Per-setting state ----
    private final double[] momentum     = new double[WeightMatrix.NUM_SETTINGS];
    private final double[] confidence   = new double[WeightMatrix.NUM_SETTINGS];
    private final int[]    lastDirection = new int[WeightMatrix.NUM_SETTINGS]; // +1, -1, or 0
    private final int[]    oscillationCount = new int[WeightMatrix.NUM_SETTINGS];
    private final int[]    freezeCounter    = new int[WeightMatrix.NUM_SETTINGS]; // >0 means frozen

    /** Circular buffer entry for adjustment history. */
    record Adjustment(long timestamp, int settingIndex, double oldValue, double newValue, double gradient) {}
    private final Deque<Adjustment> adjustmentHistory = new ArrayDeque<>();

    // ---- Binary setting tracking ----
    /** Running performance score averages for module ON vs OFF states. */
    private final double[] binaryScoreOn  = new double[WeightMatrix.NUM_SETTINGS];
    private final double[] binaryScoreOff = new double[WeightMatrix.NUM_SETTINGS];
    private final int[]    binaryCountOn  = new int[WeightMatrix.NUM_SETTINGS];
    private final int[]    binaryCountOff = new int[WeightMatrix.NUM_SETTINGS];

    // ---- Engine state ----
    private final PerformanceSignals signals;
    private final WeightMatrix weightMatrix;
    private boolean enabled = false;
    private int evaluationCycle = 0;
    private double previousScore = Double.NaN;
    private long lastEvalTimeMs = 0;
    private long lastConfidenceDecayMs = 0;

    // ---- Session counters (reset on connect) ----
    private int sessionKills, sessionDeaths;
    private long sessionStartMs;
    private float sessionDamageDealt, sessionDamageTaken;
    private int sessionAttacksSent, sessionAttacksHit;
    private int sessionCorrections, sessionTotemPops;
    private long lastDeathTimeMs, lastKillTimeMs;
    private long combatStartMs; // when current combat bout started
    private boolean inCombat;
    private long lastCombatEventMs;
    private int encounters;
    private double learningRateMultiplier=1;
    private final ClientState state;
    private final Runnable settingsChanged;

    AdaptiveEngine(PerformanceSignals signals, WeightMatrix weightMatrix) {
        this(signals,weightMatrix,LevrnClient.STATE,LevrnClient::scheduleSave);
    }
    AdaptiveEngine(PerformanceSignals signals,WeightMatrix weightMatrix,ClientState state) {this(signals,weightMatrix,state,() -> {});}
    AdaptiveEngine(PerformanceSignals signals,WeightMatrix weightMatrix,ClientState state,Runnable settingsChanged) {
        this.settingsChanged=Objects.requireNonNull(settingsChanged);
        this.state=Objects.requireNonNull(state);
        this.signals = Objects.requireNonNull(signals);
        this.weightMatrix = Objects.requireNonNull(weightMatrix);
        this.sessionStartMs = System.currentTimeMillis();
        this.lastConfidenceDecayMs = sessionStartMs;
    }

    // ======================================================================
    //  Event callbacks — called from ModuleRuntime / hooks
    // ======================================================================

    /** Record a kill event. */
    void onKill() {
        if (!enabled) return;
        sessionKills++;
        lastKillTimeMs = System.currentTimeMillis();
        updateCombatSignals();endCombat();evaluate();
    }

    /** Record a death, trigger an evaluation. */
    void onDeath() {
        if (!enabled) return;
        sessionDeaths++;
        long now = System.currentTimeMillis();

        // Record survival time (seconds since last death or session start)
        long refTime = lastDeathTimeMs > 0 ? lastDeathTimeMs : sessionStartMs;
        double survivalSeconds = (now - refTime) / 1000.0;
        signals.record(PerformanceSignals.Signal.SURVIVAL_TIME, survivalSeconds);

        lastDeathTimeMs = now;
        endCombat();
        updateCombatSignals();
        evaluate();
    }

    /** Record a server correction event. */
    void onCorrection() {
        if (!enabled) return;
        sessionCorrections++;
        long elapsedMinutes = Math.max(1, (System.currentTimeMillis() - sessionStartMs) / 60_000);
        signals.record(PerformanceSignals.Signal.CORRECTION_RATE, (double) sessionCorrections / elapsedMinutes);
    }

    /** Record outgoing damage. */
    void onDamageDealt(float amount) {
        if (!enabled || amount <= 0) return;
        sessionDamageDealt += amount;
        beginCombat();
        updateDamageEfficiency();
    }

    /** Record incoming damage. */
    void onDamageTaken(float amount) {
        if (!enabled || amount <= 0) return;
        sessionDamageTaken += amount;
        beginCombat();
        updateDamageEfficiency();
    }

    /** Record a totem use. */
    void onTotemPop() {
        if (!enabled) return;
        sessionTotemPops++;
        int lives = Math.max(1, sessionDeaths + 1);
        signals.record(PerformanceSignals.Signal.TOTEM_POPS, (double) sessionTotemPops / lives);
    }

    /** Record how long target acquisition took (in ticks). */
    void onTargetAcquired(int ticks) {
        if (!enabled) return;
        signals.record(PerformanceSignals.Signal.TARGET_ACQUISITION_TIME, ticks);
    }

    /** Record an attack attempt. */
    void onAttackSent() {
        if (!enabled) return;
        sessionAttacksSent++;
        beginCombat();
        updateHitRate();
    }

    /** Record a successful hit. */
    void onAttackHit() {
        if (!enabled) return;
        sessionAttacksHit++;
        updateHitRate();
    }

    /**
     * Periodic tick — should be called once per client tick.
     * Handles periodic evaluations and confidence decay.
     */
    void tick() {
        if (!enabled) return;
        long now = System.currentTimeMillis();

        if(inCombat && now-lastCombatEventMs>5000) {endCombat();updateCombatSignals();evaluate();}
        // Periodic evaluation every 60 seconds of combat
        if (inCombat && now - lastEvalTimeMs > PERIODIC_EVAL_MS) {
            updateCombatSignals();
            evaluate();
        }

        // Confidence decay every 10 minutes
        if (now - lastConfidenceDecayMs > CONFIDENCE_DECAY_INTERVAL_MS) {
            lastConfidenceDecayMs = now;
            for (int i = 0; i < WeightMatrix.NUM_SETTINGS; i++) {
                confidence[i] = Math.max(0.0, confidence[i] - CONFIDENCE_DECAY);
            }
        }
    }

    // ======================================================================
    //  Core evaluation algorithm
    // ======================================================================

    /**
     * Run the evaluation/adjustment cycle for all settings.
     *
     * <p>Called after each death and every 60 seconds of active combat.
     */
    void evaluate() {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        lastEvalTimeMs = now;
        evaluationCycle++;

        if(signals.snapshot().values().stream().allMatch(value -> value==0)) return;
        double currentScore = computePerformanceScore();

        for (int s = 0; s < WeightMatrix.NUM_SETTINGS; s++) {
            // Binary settings are evaluated less frequently
            if (WeightMatrix.isBinary(s) && evaluationCycle % BINARY_EVAL_INTERVAL != 0) continue;

            // Skip frozen settings
            if (freezeCounter[s] > 0) {
                freezeCounter[s]--;
                continue;
            }

            // 1. Compute gradient
            double grad = weightMatrix.gradient(s, signals);

            // 2. Update momentum with decay
            momentum[s] = MOMENTUM_DECAY * momentum[s] + MOMENTUM_INPUT * grad;

            // 3. Oscillation detection
            int currentDir = momentum[s] > 0 ? 1 : (momentum[s] < 0 ? -1 : 0);
            if (currentDir != 0 && lastDirection[s] != 0 && currentDir != lastDirection[s]) {
                oscillationCount[s]++;
                if (oscillationCount[s] >= OSCILLATION_TRIGGER) {
                    freezeCounter[s] = OSCILLATION_FREEZE;
                    continue; // Skip this evaluation, setting is frozen
                }
            } else if (currentDir == lastDirection[s]) {
                oscillationCount[s] = Math.max(0, oscillationCount[s] - 1);
            }

            if (WeightMatrix.isBinary(s)) {
                adjustBinarySetting(s, state, currentScore);
            } else {
                adjustContinuousSetting(s, state, currentDir);
            }

            // 8. Meta-learning: update weights based on whether this evaluation improved
            if (!Double.isNaN(previousScore) && evaluationCycle > 1) {
                boolean improved = currentScore > previousScore;
                weightMatrix.updateWeights(s, signals, META_LEARNING_RATE, improved);
            }
        }

        previousScore = currentScore;
    }

    /** Adjust a continuous setting by its momentum-smoothed gradient. */
    private void adjustContinuousSetting(int s, ClientState state, int currentDir) {
        // 4. Adaptive learning rate
        double confidenceFactor = 1.0 - (confidence[s] * 0.85);
        double oscillationPenalty = 1.0 / (1 + oscillationCount[s]);
        double lr = BASE_LEARNING_RATE * learningRateMultiplier * confidenceFactor * oscillationPenalty;

        // 5. Only adjust if momentum exceeds threshold
        if (Math.abs(momentum[s]) <= MOMENTUM_THRESHOLD) return;

        double delta = lr * Math.tanh(momentum[s]) * WeightMatrix.STEP_SIZES[s];
        double oldValue = readSetting(s, state);
        double newValue = Math.clamp(oldValue + delta, WeightMatrix.BOUNDS[s][0], WeightMatrix.BOUNDS[s][1]);

        if (Math.abs(newValue - oldValue) > 1e-9) {
            writeSetting(s, state, newValue);
            recordAdjustment(s, oldValue, newValue, momentum[s]);
        }

        // 6. Update confidence
        confidence[s] = Math.min(1.0, confidence[s] + CONFIDENCE_INCREMENT);

        // 7. Record direction
        lastDirection[s] = currentDir;
    }

    /**
     * Adjust a binary (on/off) setting.
     *
     * <p>Tracks running average performance scores with the module ON vs OFF.
     * Only switches state after at least {@link #BINARY_MIN_OBSERVATIONS} of each.
     */
    private void adjustBinarySetting(int s, ClientState state, double currentScore) {
        boolean currentlyOn = readSetting(s, state) > 0.5;

        // Record the current score under the current state
        if (currentlyOn) {
            binaryCountOn[s]++;
            binaryScoreOn[s] += (currentScore - binaryScoreOn[s]) / binaryCountOn[s];
        } else {
            binaryCountOff[s]++;
            binaryScoreOff[s] += (currentScore - binaryScoreOff[s]) / binaryCountOff[s];
        }

        // Only decide once we have enough observations of both states
        if (binaryCountOn[s] >= BINARY_MIN_OBSERVATIONS && binaryCountOff[s] >= BINARY_MIN_OBSERVATIONS) {
            boolean shouldBeOn = binaryScoreOn[s] >= binaryScoreOff[s];
            if (shouldBeOn != currentlyOn) {
                double oldVal = currentlyOn ? 1.0 : 0.0;
                double newVal = shouldBeOn ? 1.0 : 0.0;
                writeSetting(s, state, newVal);
                recordAdjustment(s, oldVal, newVal, momentum[s]);
            }
        }

        // Update confidence and direction
        confidence[s] = Math.min(1.0, confidence[s] + CONFIDENCE_INCREMENT);
        lastDirection[s] = readSetting(s, state) > 0.5 ? 1 : -1;
    }

    // ======================================================================
    //  Performance score
    // ======================================================================

    /**
     * Compute a single performance score combining all signals.
     * Each component is normalized to roughly 0..1 before weighting.
     */
    double computePerformanceScore() {
        double kd = normalize(signals.get(PerformanceSignals.Signal.KD_RATIO), 0, 5);
        double hit = signals.get(PerformanceSignals.Signal.HIT_RATE); // already 0..1
        double dmgEff = normalize(signals.get(PerformanceSignals.Signal.DAMAGE_EFFICIENCY), 0, 5);
        double survival = normalize(signals.get(PerformanceSignals.Signal.SURVIVAL_TIME), 0, 300); // 5 min max
        double corrections = normalize(signals.get(PerformanceSignals.Signal.CORRECTION_RATE), 0, 10);

        return W_KD_RATIO    * kd
             + W_HIT_RATE    * hit
             + W_DAMAGE_EFF  * dmgEff
             + W_SURVIVAL    * survival
             - W_CORRECTIONS * corrections;
    }

    /** Normalize a value to 0..1 given a known range. */
    private static double normalize(double value, double min, double max) {
        if (max <= min) return 0;
        return Math.clamp((value - min) / (max - min), 0.0, 1.0);
    }

    // ======================================================================
    //  Signal updates (internal)
    // ======================================================================

    private void updateCombatSignals() {
        long elapsedMinutes = Math.max(1, (System.currentTimeMillis() - sessionStartMs) / 60_000);
        signals.record(PerformanceSignals.Signal.KILL_RATE, (double) sessionKills / elapsedMinutes);
        signals.record(PerformanceSignals.Signal.DEATH_RATE, (double) sessionDeaths / elapsedMinutes);
        double kd = sessionDeaths == 0 ? sessionKills : (double) sessionKills / sessionDeaths;
        signals.record(PerformanceSignals.Signal.KD_RATIO, kd);
    }

    private void updateHitRate() {
        if (sessionAttacksSent > 0) {
            signals.record(PerformanceSignals.Signal.HIT_RATE, (double) sessionAttacksHit / sessionAttacksSent);
        }
    }

    private void updateDamageEfficiency() {
        if (sessionDamageTaken > 0) {
            signals.record(PerformanceSignals.Signal.DAMAGE_EFFICIENCY, sessionDamageDealt / sessionDamageTaken);
        }
    }

    private void beginCombat() {
        lastCombatEventMs=System.currentTimeMillis();
        if (!inCombat) {
            inCombat = true;
            combatStartMs = System.currentTimeMillis();
        }
    }

    private void endCombat() {
        if (inCombat) {
            double durationSeconds = (System.currentTimeMillis() - combatStartMs) / 1000.0;
            signals.record(PerformanceSignals.Signal.COMBAT_DURATION, durationSeconds);
            inCombat = false;encounters++;
        }
    }

    // ======================================================================
    //  Setting read/write via ClientState
    // ======================================================================

    /** Read a setting's current value from ClientState. */
    private double readSetting(int index, ClientState state) {
        return switch (index) {
            case WeightMatrix.ATTACK_RANGE       -> state.attackRange();
            case WeightMatrix.COOLDOWN           -> state.cooldown();
            case WeightMatrix.AIM_SPEED          -> state.aimSpeed();
            case WeightMatrix.TARGET_FOV         -> state.targetFov();
            case WeightMatrix.VELOCITY_H         -> state.velocityHorizontal();
            case WeightMatrix.VELOCITY_V         -> state.velocityVertical();
            case WeightMatrix.TOTEM_HEALTH       -> state.totemHealth();
            case WeightMatrix.AURA_ENABLED       -> state.enabled("aura") ? 1.0 : 0.0;
            case WeightMatrix.TRIGGER_ENABLED    -> state.enabled("trigger") ? 1.0 : 0.0;
            case WeightMatrix.CRIT_ENABLED       -> state.enabled("criticals") ? 1.0 : 0.0;
            case WeightMatrix.WTAP_ENABLED       -> state.enabled("w_tap") ? 1.0 : 0.0;
            case WeightMatrix.AUTO_TOTEM_ENABLED -> state.enabled("auto_totem") ? 1.0 : 0.0;
            case WeightMatrix.SHIELD_BREAKER     -> state.enabled("shield_breaker") ? 1.0 : 0.0;
            default -> 0;
        };
    }

    /** Write a setting value into ClientState, clamping to valid bounds. */
    private void writeSetting(int index, ClientState state, double value) {
        switch (index) {
            case WeightMatrix.ATTACK_RANGE       -> state.setAttackRange(value);
            case WeightMatrix.COOLDOWN           -> state.setCooldown(value);
            case WeightMatrix.AIM_SPEED          -> state.setAimSpeed(value);
            case WeightMatrix.TARGET_FOV         -> state.setTargetFov(value);
            case WeightMatrix.VELOCITY_H         -> state.setVelocityHorizontal(value);
            case WeightMatrix.VELOCITY_V         -> state.setVelocityVertical(value);
            case WeightMatrix.TOTEM_HEALTH       -> state.setTotemHealth(value);
            case WeightMatrix.AURA_ENABLED       -> state.feature("aura").setEnabled(value > 0.5);
            case WeightMatrix.TRIGGER_ENABLED    -> state.feature("trigger").setEnabled(value > 0.5);
            case WeightMatrix.CRIT_ENABLED       -> state.feature("criticals").setEnabled(value > 0.5);
            case WeightMatrix.WTAP_ENABLED       -> state.feature("w_tap").setEnabled(value > 0.5);
            case WeightMatrix.AUTO_TOTEM_ENABLED -> state.feature("auto_totem").setEnabled(value > 0.5);
            case WeightMatrix.SHIELD_BREAKER     -> state.feature("shield_breaker").setEnabled(value > 0.5);
        }
    }

    // ======================================================================
    //  History
    // ======================================================================

    private void recordAdjustment(int settingIndex, double oldValue, double newValue, double gradient) {
        while (adjustmentHistory.size() >= HISTORY_SIZE) adjustmentHistory.pollFirst();
        state.setCurrentPreset(ClientState.CombatPreset.CUSTOM);settingsChanged.run();
        adjustmentHistory.addLast(new Adjustment(System.currentTimeMillis(), settingIndex, oldValue, newValue, gradient));
    }

    // ======================================================================
    //  Public API
    // ======================================================================

    int encounters() {return encounters;}
    void setLearningRateMultiplier(double value) {if(Double.isFinite(value)) learningRateMultiplier=Math.clamp(value,.1,3);}
    boolean isEnabled() { return enabled; }

    void setEnabled(boolean value) {
        if(this.enabled==value) return;
        this.enabled = value;
        if (value) resetSession();else {endCombat();updateCombatSignals();}
    }

    /** Reset per-session counters (called on connect or enable). */
    void resetSession() {
        lastCombatEventMs=0;
        sessionKills = 0;
        sessionDeaths = 0;
        sessionStartMs = System.currentTimeMillis();
        lastEvalTimeMs = sessionStartMs;
        lastConfidenceDecayMs = sessionStartMs;
        sessionDamageDealt = 0;
        sessionDamageTaken = 0;
        sessionAttacksSent = 0;
        sessionAttacksHit = 0;
        sessionCorrections = 0;
        sessionTotemPops = 0;
        lastDeathTimeMs = 0;
        lastKillTimeMs = 0;
        combatStartMs = 0;
        inCombat = false;
        evaluationCycle = 0;
        previousScore = Double.NaN;
    }

    /** Human-readable status string for the HUD. */
    String getStatus() {
        if (!enabled) return "NOVA: OFF";
        double score = computePerformanceScore();
        int numConverged = 0;
        for (int i = 0; i < WeightMatrix.NUM_SETTINGS; i++) {
            if (confidence[i] > 0.7) numConverged++;
        }
        return String.format("NOVA: Score %.2f | Converged %d/%d | Cycle %d",
            score, numConverged, WeightMatrix.NUM_SETTINGS, evaluationCycle);
    }

    /** Map of setting name to confidence level, for UI display. */
    Map<String, Double> getConfidenceMap() {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < WeightMatrix.NUM_SETTINGS; i++) {
            map.put(WeightMatrix.SETTING_NAMES[i], confidence[i]);
        }
        return map;
    }

    /** List of recent adjustments for UI/notifications. */
    List<Adjustment> getLastAdjustments() {
        return List.copyOf(adjustmentHistory);
    }

    /** Get the latest adjustment as a human-readable string, or null if none. */
    String getLastAdjustmentDescription() {
        if (adjustmentHistory.isEmpty()) return null;
        Adjustment a = adjustmentHistory.peekLast();
        String name = WeightMatrix.SETTING_NAMES[a.settingIndex];
        if (WeightMatrix.isBinary(a.settingIndex)) {
            return name + ": " + (a.newValue > 0.5 ? "ON" : "OFF");
        }
        String dir = a.newValue > a.oldValue ? "+" : "";
        return String.format("%s: %.3f -> %.3f (%s%.3f)", name, a.oldValue, a.newValue, dir, a.newValue - a.oldValue);
    }

    // ---- Persistence helpers ----

    PerformanceSignals signals() { return signals; }
    WeightMatrix weightMatrix() { return weightMatrix; }
    double[] momentumArray() { return momentum; }
    double[] confidenceArray() { return confidence; }

    /** Restore momentum/confidence arrays from loaded data. */
    void restoreState(double[] loadedMomentum, double[] loadedConfidence) {
        int mLen = Math.min(loadedMomentum.length, WeightMatrix.NUM_SETTINGS);
        for(int i=0;i<mLen;i++) momentum[i]=Double.isFinite(loadedMomentum[i]) ? Math.clamp(loadedMomentum[i],-100,100) : 0;
        int cLen = Math.min(loadedConfidence.length, WeightMatrix.NUM_SETTINGS);
        for(int i=0;i<cLen;i++) confidence[i]=Double.isFinite(loadedConfidence[i]) ? Math.clamp(loadedConfidence[i],0,1) : 0;
    }

    // ======================================================================
    //  Static convenience hooks for integration
    //  TODO: Wire these calls from ModuleRuntime, LevrnClient, etc.
    // ======================================================================

    /**
     * Called from {@code ModuleRuntime.corrected()} to record a server correction.
     * @param engine the active engine instance (may be null if not initialized)
     */
    static void notifyCorrection(AdaptiveEngine engine) {
        if (engine != null) engine.onCorrection();
    }

    /**
     * Called after a successful attack in {@code ModuleRuntime.afterMovement()}.
     * @param engine the active engine instance
     */
    static void notifyAttackSent(AdaptiveEngine engine) {
        if (engine != null) engine.onAttackSent();
    }

    /**
     * Called when an attack visibly hits a target.
     * @param engine the active engine instance
     */
    static void notifyAttackHit(AdaptiveEngine engine) {
        if (engine != null) engine.onAttackHit();
    }

    /**
     * Called on player death.
     * @param engine the active engine instance
     */
    static void notifyDeath(AdaptiveEngine engine) {
        if (engine != null) engine.onDeath();
    }

    /**
     * Called on confirmed kill.
     * @param engine the active engine instance
     */
    static void notifyKill(AdaptiveEngine engine) {
        if (engine != null) engine.onKill();
    }

    /**
     * Called when player deals damage.
     * @param engine the active engine instance
     * @param amount damage dealt
     */
    static void notifyDamageDealt(AdaptiveEngine engine, float amount) {
        if (engine != null) engine.onDamageDealt(amount);
    }

    /**
     * Called when player takes damage.
     * @param engine the active engine instance
     * @param amount damage taken
     */
    static void notifyDamageTaken(AdaptiveEngine engine, float amount) {
        if (engine != null) engine.onDamageTaken(amount);
    }

    /**
     * Called when a totem of undying is consumed.
     * @param engine the active engine instance
     */
    static void notifyTotemPop(AdaptiveEngine engine) {
        if (engine != null) engine.onTotemPop();
    }
}
