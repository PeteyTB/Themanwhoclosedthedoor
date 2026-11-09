package dev.levrn.client;

import net.minecraft.client.gui.GuiGraphics;

/** Retains the existing overlay registration and menu palette. */
final class LevrnHud {
    static final int TEXT=0xFFD8D8DC,MUTED=0xFF707076;
    static void render(GuiGraphics graphics) { FuturisticHud.render(graphics); }
    private LevrnHud() { }
}
