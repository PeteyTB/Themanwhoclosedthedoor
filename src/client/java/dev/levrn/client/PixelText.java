package dev.levrn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** Text strikes are rasterized at their final pixel size and drawn without layout scaling. */
final class PixelText {
    private static final FontDescription[] FONTS=new FontDescription[21];
    static {
        for(int size=8;size<FONTS.length;size++)
            FONTS[size]=new FontDescription.Resource(Identifier.fromNamespaceAndPath("levrn","pixel_"+size));
    }
    static double scale() {return LevrnClient.STATE.uiScale();}
    static int interfaceSize() {return Math.clamp((int)Math.round(8.5*scale()),11,18);}
    static int badgeSize() {return Math.clamp((int)Math.round(6.5*scale()),8,13);}
    static int hudSize() {return Math.clamp((int)Math.round(10*scale()),12,20);}
    static Component component(String text,int size) {return Component.literal(text).withStyle(s -> s.withFont(FONTS[size]));}
    static int pixels(String text,int size) {return Minecraft.getInstance().font.width(component(text,size));}
    static int width(String text,int size) {return (int)Math.ceil(pixels(text,size)/scale());}
    static String fit(String text,int limit,int size) {
        int pixels=(int)Math.floor(limit*scale());
        if(pixels(text,size)<=pixels) return text;
        if(pixels("…",size)>pixels) return "";
        int end=text.length();
        while(end>0 && pixels(text.substring(0,end)+"…",size)>pixels) end=text.offsetByCodePoints(end,-1);
        return text.substring(0,end)+"…";
    }
    static void draw(GuiGraphics g,String text,int x,int y,int color,int size) {
        if(text.isEmpty() || color>>>24==0) return;
        var pose=g.pose();
        float gui=(float)Minecraft.getInstance().getWindow().getGuiScale();
        // Include the current pose so notifications and nested widgets share the same grid.
        float px=Math.round((pose.m00()*x+pose.m10()*y+pose.m20())*gui);
        float py=Math.round((pose.m01()*x+pose.m11()*y+pose.m21())*gui);
        pose.pushMatrix();
        pose.identity().translate(px/gui,py/gui).scale(1/gui,1/gui);
        g.drawString(Minecraft.getInstance().font,component(text,size),0,0,color,false);
        pose.popMatrix();
    }
    private PixelText() {}
}
