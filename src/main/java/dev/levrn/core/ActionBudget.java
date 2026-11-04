package dev.levrn.core;

/** Coordinates inventory modules: one logical inventory/slot transaction per client tick. */
public final class ActionBudget {
    private boolean used;
    public void nextTick() { used = false; }
    public boolean take() { if (used) return false; used = true; return true; }
    public boolean used() { return used; }
}
