package dev.levrn.core;

/** A second authoritative correction within five seconds trips the automation stop. */
public final class CorrectionWindow {
    private long previous=Long.MIN_VALUE;
    public boolean record(long tick) {
        boolean repeated=previous!=Long.MIN_VALUE && tick>=previous && tick-previous<=100;
        previous=tick;return repeated;
    }
    public void reset() {previous=Long.MIN_VALUE;}
}
