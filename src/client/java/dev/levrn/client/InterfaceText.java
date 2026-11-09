package dev.levrn.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class InterfaceText {
    static Component component(String text) {
        return PixelText.component(text,PixelText.interfaceSize());
    }
    static int width(String text) { return PixelText.width(text,PixelText.interfaceSize()); }
    static String fit(String text, int maxWidth) {
        return PixelText.fit(text,maxWidth,PixelText.interfaceSize());
    }
    static void draw(GuiGraphics g, String text, int x, int y, int color) {
        PixelText.draw(g,text,x,y,color,PixelText.interfaceSize());
    }
    static int badgeWidth(String text) {return PixelText.width(text,PixelText.badgeSize());}
    static void badge(GuiGraphics g,String text,int x,int y,int color) {PixelText.draw(g,text,x,y,color,PixelText.badgeSize());}
    private InterfaceText() { }
}
