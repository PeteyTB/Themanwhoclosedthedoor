package dev.levrn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import dev.levrn.core.Motion;

/** Original visual treatment over vanilla's accessible button/input behavior. */
final class LevrnButton extends Button {
    private final boolean highlighted;
    private final UiMotion.Channel motion;
    private final boolean switchStyle;
    private static final double[][] CORNER_INSETS = cornerInsets();
    private static double[][] cornerInsets() {
        double[][] result = new double[9][];
        for (int radius=1;radius<=8;radius++) {
            result[radius] = new double[radius];
            for (int row=0;row<radius;row++) {
                double dy=radius-row-0.5;
                result[radius][row]=radius-Math.sqrt(radius*radius-dy*dy);
            }
        }
        return result;
    }
    LevrnButton(String text, int x, int y, int width, Runnable action, boolean highlighted) {
        this(text,x,y,width,action,highlighted,new UiMotion.Channel(),false);
    }
    LevrnButton(String text, int x, int y, int width, Runnable action, boolean highlighted, UiMotion.Channel motion, boolean switchStyle) {
        super(x, y, width, 20, InterfaceText.component(text), b -> action.run(), DEFAULT_NARRATION);
        setHeight(16);
        this.highlighted = highlighted;
        this.motion = motion; this.switchStyle = switchStyle;
    }
    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth();
        int accent = LevrnClient.STATE.theme().accent;
        motion.update(isHoveredOrFocused(),highlighted);
        double hover = motion.hover.value(), on = motion.enabled.value();
        int border = Motion.mix(Motion.mix(0xFF243647,accent,on*0.45),accent,hover*0.8);
        rounded(g, x-1, y-1, w+2, 18, UiMotion.fade((int)(hover*45)<<24 | accent & 0xFFFFFF));
        rounded(g, x, y, w, 16, UiMotion.fade(border));
        rounded(g, x + 1, y + 1, w - 2, 14,
            UiMotion.fade(Motion.mix(Motion.mix(0xFF0C1724,0xFF16384A,on),0xFF214359,hover*0.65)));
        var font = Minecraft.getInstance().font;
        String text = InterfaceText.fit(getMessage().getString(), Math.max(1, w - (switchStyle ? 29 : 8)));
        InterfaceText.draw(g, text, switchStyle ? x+5 : x + (w - InterfaceText.width(text)) / 2, y + 4,
            UiMotion.fade(Motion.mix(LevrnHud.TEXT,accent,Math.max(on,hover))));
        if (switchStyle) {
            int trackX = x+w-21;
            rounded(g,trackX,y+5,16,6,UiMotion.fade(Motion.mix(0xFF314350,accent,on*0.7)));
            rounded(g,trackX+1+(int)Math.round(on*10),y+6,4,4,UiMotion.fade(Motion.mix(0xFF8495A1,0xFFE8FFFF,on)));
        } else if (hover>0.01 || on>0.01) {
            int length = (int)((w-10)*Math.max(hover,on));
            g.fill(x+(w-length)/2,y+14,x+(w+length)/2,y+15,UiMotion.fade(accent));
        }
    }
    static void rounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w<=0 || h<=0 || color>>>24==0) return;
        int radius=Math.min(h>40 ? 8 : 4,Math.min(w,h)/2);
        if (radius<1) { g.fill(x,y,x+w,y+h,color); return; }
        // Disjoint bands keep translucent surfaces uniform; cached edge coverage smooths corners.
        g.fill(x,y+radius,x+w,y+h-radius,color);
        for (int row=0;row<radius;row++) {
            double inset=CORNER_INSETS[radius][row];
            int edge=(int)Math.ceil(inset);
            int edgeColor=(int)((color>>>24)*(edge-inset))<<24 | color&0xFFFFFF;
            int upper=y+row,lower=y+h-row-1;
            g.fill(x+edge,upper,x+w-edge,upper+1,color);
            g.fill(x+edge,lower,x+w-edge,lower+1,color);
            if (edgeColor>>>24!=0) {
                g.fill(x+edge-1,upper,x+edge,upper+1,edgeColor);
                g.fill(x+w-edge,upper,x+w-edge+1,upper+1,edgeColor);
                g.fill(x+edge-1,lower,x+edge,lower+1,edgeColor);
                g.fill(x+w-edge,lower,x+w-edge+1,lower+1,edgeColor);
            }
        }
    }
}
