package dev.levrn.core;

/** Protocol sanity and real-jump critical timing, independent of Minecraft classes. */
public final class CombatGuard {
    public static boolean validPose(double x,double y,double z,float yaw,float pitch) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
            && Math.abs(x)<=30_000_000 && Math.abs(y)<=20_000_000 && Math.abs(z)<=30_000_000
            && Float.isFinite(yaw) && Float.isFinite(pitch) && pitch>=-90 && pitch<=90;
    }
    public enum Critical { NORMAL, JUMP, WAIT, HIT }
    public static Critical critical(boolean enabled,boolean possible,boolean ground,double falling,double vertical,float cooldown) {
        if (!enabled || !possible) return Critical.NORMAL;
        if (cooldown<0.9f) return Critical.WAIT;
        if (ground) return Critical.JUMP;
        return falling>0 && vertical<0 ? Critical.HIT : Critical.WAIT;
    }
    private CombatGuard() { }
}
