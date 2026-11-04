package dev.levrn.client;

import dev.levrn.core.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Comparator;
import java.util.Locale;

final class LevrnHud {
    static final int TEXT = 0xFFF3F0FF, MUTED = 0xFFACA7BD, PANEL = 0xD916161F;
    static void render(GuiGraphics g) {
        var mc = Minecraft.getInstance();
        var state = LevrnClient.STATE;
        int ACCENT = state.theme().accent;
        if (mc.player == null || mc.options.hideGui) return;
        if (state.enabled("hud")) {
            panel(g, 10, 10, 152, 26);
            g.fill(10, 10, 12, 36, ACCENT);
            g.drawString(mc.font, "LEVRN", 20, 19, TEXT, false);
            g.drawString(mc.font, mc.getFps() + " FPS  /  1.21.11", 63, 19, MUTED, false);
        }
        if (state.enabled("coordinates")) {
            String pos = String.format(Locale.ROOT, "XYZ  %.1f  %.1f  %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
            int y = g.guiHeight() - 38;
            panel(g, 10, y, mc.font.width(pos) + 18, 28);
            g.drawString(mc.font, pos, 19, y + 5, TEXT, false);
            g.drawString(mc.font, mc.level.dimension().identifier().getPath(), 19, y + 16, MUTED, false);
        }
        if (state.enabled("active_list")) {
            var active = state.features().stream().filter(Feature::enabled)
                .filter(f -> f.category != dev.levrn.core.Category.VISUAL)
                .sorted(Comparator.comparingInt((Feature f) -> mc.font.width(f.name)).reversed()).toList();
            int y = 10;
            for (var f : active) {
                int w = mc.font.width(f.name) + 20, x = g.guiWidth() - w - 10;
                panel(g, x, y, w, 20); g.fill(x + w - 2, y, x + w, y + 20, ACCENT);
                g.drawString(mc.font, f.name, x + 8, y + 6, ACCENT, false); y += 23;
            }
        }
        if (state.enabled("target_hud") && mc.hitResult instanceof EntityHitResult hit
            && hit.getEntity() instanceof LivingEntity target) {
            int x = g.guiWidth() / 2 + 16, y = g.guiHeight() / 2 + 16;
            panel(g, x, y, 140, 39);
            g.drawString(mc.font, mc.font.plainSubstrByWidth(target.getName().getString(), 120), x + 9, y + 7, TEXT, false);
            float ratio = Math.clamp(target.getHealth() / Math.max(1, target.getMaxHealth()), 0, 1);
            g.fill(x + 9, y + 24, x + 131, y + 29, 0xFF34313F);
            g.fill(x + 9, y + 24, x + 9 + (int)(122 * ratio), y + 29, ACCENT);
        }
    }
    static void panel(GuiGraphics g, int x, int y, int w, int h) { g.fill(x, y, x + w, y + h, PANEL); }
}
