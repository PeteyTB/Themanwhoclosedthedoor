package dev.levrn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** One oversampled TrueType font for both HUD measurements and drawing. */
final class HudText {
    private static final FontDescription FONT=new FontDescription.Resource(Identifier.fromNamespaceAndPath("levrn","hud"));
    static Component component(String value) { return Component.literal(value).withStyle(style -> style.withFont(FONT)); }
    static int width(String value) { return Minecraft.getInstance().font.width(component(value)); }
    static String fit(String value,int maxWidth) {
        if(width(value)<=maxWidth) return value;
        int end=value.length();
        while(end>0 && width(value.substring(0,end)+"…")>maxWidth) end=value.offsetByCodePoints(end,-1);
        return end==0 ? "" : value.substring(0,end)+"…";
    }
    static void draw(GuiGraphics graphics,String value,int x,int y,int color) {
        graphics.drawString(Minecraft.getInstance().font,component(value),x,y,color,false);
    }
    private HudText() { }
}
