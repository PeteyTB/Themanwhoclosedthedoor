package dev.levrn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

final class InterfaceText {
    private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("levrn", "interface"));
    static Component component(String text) {
        // Late attachment does not register a new resource pack; retain legible vanilla text in that mode.
        boolean resource = Minecraft.getInstance().getResourceManager().getResource(Identifier.fromNamespaceAndPath("levrn", "font/interface.json")).isPresent();
        return resource ? Component.literal(text).withStyle(s -> s.withFont(FONT)) : Component.literal(text);
    }
    static int width(String text) { return Minecraft.getInstance().font.width(component(text)); }
    static String fit(String text, int maxWidth) {
        if (width(text) <= maxWidth) return text;
        int end = text.length();
        while (end > 0 && width(text.substring(0, end) + "..") > maxWidth) end--;
        return end == 0 ? "" : text.substring(0, end) + "..";
    }
    static void draw(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(Minecraft.getInstance().font, component(text), x, y, color, false);
    }
    private InterfaceText() { }
}
