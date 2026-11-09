package dev.levrn.client;

import dev.levrn.core.ActionBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/** A cursor swap/merge, paced one vanilla click at a time and bound to its original menu. */
final class InventoryTransaction {
    private LocalPlayer owner;
    private int source,destination,phase,wait;
    private ItemStack expected=ItemStack.EMPTY;
    boolean active() { return owner!=null; }
    void begin(LocalPlayer player,int source,int destination) {
        if(active()) throw new IllegalStateException("Inventory transaction already active");
        owner=player;this.source=source;this.destination=destination;phase=wait=0;expected=ItemStack.EMPTY;
    }
    private boolean ownsCursor(Minecraft mc) {
        return owner==mc.player && owner.containerMenu==owner.inventoryMenu
            && ItemStack.matches(owner.inventoryMenu.getCarried(),expected);
    }
    void tick(Minecraft mc,ActionBudget budget,int interval) {
        if(!active()) return;
        if(!ownsCursor(mc)) { clear();return; }
        if(mc.screen!=null || !mc.isWindowActive() || !owner.isAlive()) { abort(mc);return; }
        if(wait>0) { wait--;return; }
        if(!budget.recordPacket() || !budget.take()) return;
        int slot=phase==1 ? destination : source;
        mc.gameMode.handleInventoryMouseClick(owner.inventoryMenu.containerId,slot,0,ClickType.PICKUP,owner);
        expected=owner.inventoryMenu.getCarried().copy();phase++;
        if(phase==3) { clear();return; }
        wait=Math.max(1,interval)-1;
    }
    /**
     * Abort the current transaction. When {@code sendRestore} is true AND the cursor
     * still holds the expected item, put it back in the source slot. Pass false during
     * server-correction handling to avoid injecting an extra packet at an irregular time
     * (the server's position correction already resyncs the inventory state).
     */
    void abort(Minecraft mc, boolean sendRestore) {
        if(sendRestore && active() && ownsCursor(mc) && !expected.isEmpty()
            && owner.inventoryMenu.getSlot(source).mayPlace(expected)
            && owner.inventoryMenu.getSlot(source).getItem().isEmpty() && mc.gameMode!=null) {
            mc.gameMode.handleInventoryMouseClick(owner.inventoryMenu.containerId,source,0,ClickType.PICKUP,owner);
        }
        clear();
    }
    /** Abort and attempt to restore cursor item to source slot. */
    void abort(Minecraft mc) { abort(mc, true); }
    private void clear() { owner=null;expected=ItemStack.EMPTY;phase=wait=0; }
}
