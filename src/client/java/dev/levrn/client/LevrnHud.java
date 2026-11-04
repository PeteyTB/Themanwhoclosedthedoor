package dev.levrn.client;

import dev.levrn.core.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
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
        int rowY = 44;
        if (state.enabled("speed_hud")) rowY = info(g, rowY, String.format(Locale.ROOT, "SPEED / %.2f BPS", mc.player.getDeltaMovement().horizontalDistance() * 20));
        if (state.enabled("ping_hud") && mc.getConnection() != null) {
            var entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (entry != null) rowY = info(g, rowY, "LATENCY / " + entry.getLatency() + " MS");
        }
        if (state.enabled("clock")) rowY = info(g, rowY, "LOCAL / " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
        if (state.enabled("compass")) rowY = info(g, rowY, "HEADING / " + mc.player.getDirection().getName().toUpperCase(Locale.ROOT) + " " + Math.round(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot())));
        if (state.enabled("keystrokes")) rowY = info(g, rowY, "INPUT / "
            + (mc.options.keyUp.isDown() ? "W" : "_") + (mc.options.keyLeft.isDown() ? "A" : "_")
            + (mc.options.keyDown.isDown() ? "S" : "_") + (mc.options.keyRight.isDown() ? "D" : "_")
            + (mc.options.keyJump.isDown() ? " JUMP" : "") + (mc.options.keyAttack.isDown() ? " ATTACK" : ""));
        if (state.enabled("armor_hud")) {
            for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                var item = mc.player.getItemBySlot(slot);
                String durability = item.isEmpty() ? "EMPTY" : item.isDamageableItem()
                    ? Math.round(100.0 * (item.getMaxDamage() - item.getDamageValue()) / item.getMaxDamage()) + "%" : "--";
                rowY = info(g, rowY, slot.getName().toUpperCase(Locale.ROOT) + " / " + durability);
            }
        }
        if (state.enabled("potion_hud")) {
            int count = 0;
            for (var effect : mc.player.getActiveEffects()) {
                if (count++ >= 4) break;
                String name = net.minecraft.network.chat.Component.translatable(effect.getEffect().value().getDescriptionId()).getString();
                rowY = info(g, rowY, name + " " + (effect.getAmplifier() + 1) + " / " + effect.getDuration() / 20 + "s");
            }
        }
        if (state.enabled("combat_status") && (state.enabled("aura") || state.enabled("trigger") || state.enabled("aim_assist"))) {
            int w = Math.min(270, g.guiWidth() - 20), y = Math.min(rowY + 4, g.guiHeight() - 95);
            panel(g, 10, y, w, 51); g.fill(10, y, 12, y + 51, ACCENT);
            InterfaceText.draw(g, InterfaceText.fit(LevrnClient.combatStatus(), w - 18), 19, y + 7, ACCENT);
            InterfaceText.draw(g, "PLAYERS " + (state.targetPlayers() ? "ON" : "OFF") + " / TEAMS " + (state.respectTeams() ? "ON" : "OFF"), 19, y + 21, TEXT);
            InterfaceText.draw(g, "SENT " + LevrnClient.runtime.sentAttacks + " / COOLDOWN " + Math.round(state.cooldown() * 100) + "%", 19, y + 35, MUTED);
        }
        LivingEntity target = LevrnClient.runtime.currentTarget;
        if (target == null && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living) target = living;
        if (state.enabled("target_hud") && target != null) {
            int x = g.guiWidth() / 2 + 16, y = g.guiHeight() / 2 + 16;
            panel(g, x, y, 140, 39);
            g.drawString(mc.font, mc.font.plainSubstrByWidth(target.getName().getString(), 120), x + 9, y + 7, TEXT, false);
            float ratio = Math.clamp(target.getHealth() / Math.max(1, target.getMaxHealth()), 0, 1);
            g.fill(x + 9, y + 24, x + 131, y + 29, 0xFF34313F);
            g.fill(x + 9, y + 24, x + 9 + (int)(122 * ratio), y + 29, ACCENT);
        }
    }
    private static int info(GuiGraphics g, int y, String text) {
        if (y > g.guiHeight() - 60) return y;
        String fitted = InterfaceText.fit(text, Math.min(210, g.guiWidth() - 40));
        panel(g, 10, y, InterfaceText.width(fitted) + 18, 17);
        InterfaceText.draw(g, fitted, 19, y + 4, TEXT);
        return y + 19;
    }
    static void panel(GuiGraphics g, int x, int y, int w, int h) { LevrnButton.rounded(g, x, y, w, h, PANEL); }
}
