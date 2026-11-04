package dev.levrn.core;

import java.util.Comparator;
import java.util.List;

public final class TargetRetention {
    public record Candidate(int id, double distance) { }
    /** Retain a valid target unless another is significantly closer. Invalid targets never stick. */
    public static int choose(int current, List<Candidate> eligible, boolean sticky) {
        var best = eligible.stream().filter(c -> Double.isFinite(c.distance()) && c.distance() >= 0)
            .min(Comparator.comparingDouble(Candidate::distance)).orElse(null);
        if (best == null) return -1;
        if (sticky) {
            var previous = eligible.stream().filter(c -> c.id() == current && Double.isFinite(c.distance()) && c.distance() >= 0).findFirst().orElse(null);
            if (previous != null && previous.distance() - best.distance() < 0.4) return previous.id();
        }
        return best.id();
    }
    private TargetRetention() { }
}
