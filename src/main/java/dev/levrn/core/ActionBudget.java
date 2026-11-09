package dev.levrn.core;

/**
 * Coordinates inventory modules and enforces a per-tick packet ceiling.
 * Only one logical inventory/slot action is allowed per tick, AND the total
 * number of mod-initiated packets is capped to avoid bursts that trip
 * server-side rate limiters.
 */
public final class ActionBudget {
    /** Hard cap on mod-initiated packets per tick (attack, swing, inventory click, use-item). */
    private static final int PACKET_CEILING = 3;
    private boolean used;
    private int packets;
    private boolean attacked;
    public void nextTick() { used = false; packets = 0; attacked = false; }
    /** Try to claim the single per-tick action slot (inventory click, slot change, use-item). Returns false if already used or packet ceiling reached. */
    public boolean take() { if (used || packets >= PACKET_CEILING) return false; used = true; return true; }
    public boolean used() { return used; }
    /** Record that one mod-initiated packet will be sent. Returns false (and does not record) if the ceiling has been reached. */
    public boolean recordPacket() { if (packets >= PACKET_CEILING) return false; packets++; return true; }
    /** Mark that an attack was sent this tick, consuming the action slot so inventory cannot also fire. */
    public void markAttacked() { attacked = true; used = true; }
    public boolean attacked() { return attacked; }
}
