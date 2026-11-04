package dev.levrn.core;

/** Closed-form critically damped spring: consistent motion at different render frame rates. */
public final class Motion {
    private double value, velocity;
    public Motion(double value) { snap(value); }
    public double value() { return value; }
    public void snap(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite motion value");
        this.value = value; velocity = 0;
    }
    public double step(double target, double seconds, double frequency) {
        if (!Double.isFinite(target) || !Double.isFinite(seconds) || !Double.isFinite(frequency)
            || seconds < 0 || frequency <= 0) throw new IllegalArgumentException("Invalid motion parameters");
        double offset = value - target, impulse = velocity + frequency * offset;
        double decay = Math.exp(-frequency * seconds);
        value = target + (offset + impulse * seconds) * decay;
        velocity = (velocity - frequency * impulse * seconds) * decay;
        if (Math.abs(value - target) < 0.0001 && Math.abs(velocity) < 0.001) snap(target);
        return value;
    }
    public static int mix(int first, int second, double fraction) {
        double t = Math.clamp(fraction, 0, 1); int result = 0;
        for (int shift=0;shift<=24;shift+=8) result |= (int)Math.round(((first >>> shift) & 255) * (1-t) + ((second >>> shift) & 255) * t) << shift;
        return result;
    }
    public static double quantize(double value, double min, double max, double step) {
        if (!Double.isFinite(value) || !Double.isFinite(min) || !Double.isFinite(max) || !Double.isFinite(step) || max <= min || step <= 0)
            throw new IllegalArgumentException("Invalid numeric setting");
        return Math.clamp(min + Math.round((value - min) / step) * step, min, max);
    }
}
