package dev.levrn.client;

import net.minecraft.client.gui.GuiGraphics;

/** Retains the existing overlay registration and menu palette. */
final class LevrnHud {
    static final int TEXT=0xFFF3F0FF,MUTED=0xFFACA7BD;
    static void render(GuiGraphics graphics) { FuturisticHud.render(graphics); }
    private LevrnHud() { }
}
