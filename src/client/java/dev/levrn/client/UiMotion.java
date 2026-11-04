package dev.levrn.client;

import dev.levrn.core.Motion;

final class UiMotion {
    static double opacity = 1;
    static int fade(int color) { return ((int)(((color >>> 24) & 255) * Math.clamp(opacity, 0, 1)) << 24) | (color & 0xFFFFFF); }
    static final class Channel {
        final Motion hover = new Motion(0), enabled = new Motion(0);
        private long last = System.nanoTime();
        void update(boolean hovering, boolean active) {
            long now = System.nanoTime(); double dt = Math.min(0.05, (now - last) / 1e9) * LevrnClient.STATE.motionSpeed(); last = now;
            if (LevrnClient.STATE.reducedMotion()) { hover.snap(hovering ? 1 : 0); enabled.snap(active ? 1 : 0); }
            else { hover.step(hovering ? 1 : 0, dt, 22); enabled.step(active ? 1 : 0, dt, 18); }
        }
    }
    private UiMotion() { }
}
