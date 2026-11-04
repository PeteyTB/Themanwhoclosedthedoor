package dev.levrn.core;

/** Pure targeting rules shared by the game adapter and regression tests. */
public final class TargetPolicy {
    public record Candidate(boolean self, boolean alive, boolean spectator, boolean invulnerable,
        boolean player, boolean creative, boolean mob, boolean allied, boolean invisible,
        boolean visible, double hitboxDistance, double angle) { }
    public static String reject(ClientState state, Candidate candidate) {
        if (candidate.self()) return "Self";
        if (!candidate.alive() || candidate.spectator() || candidate.invulnerable() || candidate.creative()) return "Not attackable";
        if (candidate.player() ? !state.targetPlayers() : !candidate.mob() || !state.targetMobs()) return "Target type filtered";
        if (state.respectTeams() && candidate.allied()) return "Team filtered";
        if (candidate.invisible() && !state.targetInvisible()) return "Invisible filtered";
        if (!candidate.visible()) return "Line of sight blocked";
        if (!Double.isFinite(candidate.hitboxDistance()) || candidate.hitboxDistance() < 0 || candidate.hitboxDistance() > state.attackRange()) return "Out of range";
        if (!Double.isFinite(candidate.angle()) || candidate.angle() > state.targetFov() / 2) return "Outside FOV";
        return "";
    }
    public static float approachAngle(float current, float desired, float maxStep) {
        float difference = (desired - current) % 360;
        if (difference >= 180) difference -= 360;
        if (difference < -180) difference += 360;
        return current + Math.clamp(difference, -maxStep, maxStep);
    }
    private TargetPolicy() { }
}
