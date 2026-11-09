package dev.levrn.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Native-pixel TrueType strikes shared by HUD measurements and drawing. */
final class HudText {
    static Component component(String value) { return PixelText.component(value,PixelText.hudSize()); }
    static int width(String value) { return PixelText.width(value,PixelText.hudSize()); }
    static String fit(String value,int maxWidth) {
        return PixelText.fit(value,maxWidth,PixelText.hudSize());
    }
    static void draw(GuiGraphics graphics,String value,int x,int y,int color) {
        PixelText.draw(graphics,value,x,y,color,PixelText.hudSize());
    }
    private HudText() { }
}
