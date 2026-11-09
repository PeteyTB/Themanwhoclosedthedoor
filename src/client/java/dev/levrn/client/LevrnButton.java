package dev.levrn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import dev.levrn.core.Motion;

/** Clean flat button with subtle hover transitions and switch-style toggles. */
final class LevrnButton extends Button {
    private String severity;
    void severity(String label) {severity=label;}
    private boolean settingRow;
    void settingRow() { settingRow=true; }
    private boolean leftAligned;
    LevrnButton alignLeft() { leftAligned=true;return this; }
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
        motion.update(isHoveredOrFocused(),settingRow ? getMessage().getString().endsWith("ON") : highlighted);
        double hover = motion.hover.value(), on = motion.enabled.value();

        if(settingRow) {
            String full=getMessage().getString();
            int split=full.lastIndexOf(" / ");
            if(split<0) split=full.lastIndexOf(": ");
            String label=split<0 ? full : full.substring(0,split);
            String value=split<0 ? "" : full.substring(split+(full.charAt(split)==':' ? 2 : 3));
            boolean toggle=value.equals("ON") || value.equals("OFF");
            if(hover>.01) rounded(g,x,y,w,16,UiMotion.fade((int)(hover*24)<<24|0xA0A8B8));
            int reserve=toggle ? 24 : value.isEmpty() ? 8 : InterfaceText.width(value)+14;
            InterfaceText.draw(g,InterfaceText.fit(label,w-reserve-10),x+5,y+4,UiMotion.fade(0xFFD8D8DC));
            if(toggle) {
                var state=motion.enabled;
                int tx=x+w-20;
                rounded(g,tx,y+6,14,5,UiMotion.fade(Motion.mix(0xFF343A46,0xFF77829A,state.value())));
                rounded(g,tx+1+(int)Math.round(state.value()*8),y+6,5,5,UiMotion.fade(value.equals("ON")?0xFFF0F1F5:0xFF8E96A7));
            } else if(!value.isEmpty()) {
                int vw=InterfaceText.width(value);
                rounded(g,x+w-vw-11,y+3,vw+7,11,UiMotion.fade(0xA043495A));
                InterfaceText.draw(g,value,x+w-vw-7,y+4,UiMotion.fade(0xFFC7CDDA));
            }
            return;
        }

        // Clean flat background with subtle hover transition
        int bg = Motion.mix(Motion.mix(0x5513161C, 0xD0080A0D, on), 0xB020232A, hover * 0.8);
        int border = Motion.mix(0x202A303B, accent, on * 0.08 + hover * 0.18);
        rounded(g, x, y, w, 16, UiMotion.fade(border));
        rounded(g, x + 1, y + 1, w - 2, 14, UiMotion.fade(bg));

        var font = Minecraft.getInstance().font;
        int badge=severity==null ? 0 : (int)Math.ceil(InterfaceText.width(severity)*.7)+6;
        String text = InterfaceText.fit(getMessage().getString(), Math.max(1, w - (switchStyle && badge==0 ? 29 : 8)- (badge>0 ? badge+5 : 0)));
        int textColor = Motion.mix(0xFFA0A0A6, on > 0.5 ? accent : 0xFFD8D8DC, Math.max(on, hover));
        InterfaceText.draw(g, text, switchStyle || leftAligned ? x+5+(badge>0 ? badge+5 : 0) : x + (w - InterfaceText.width(text)) / 2, y + 4,
            UiMotion.fade(textColor));

        if(badge>0) {
            boolean warning=severity.equals("WARNING");int bx=x+5;
            g.fill(bx,y+4,bx+badge,y+12,UiMotion.fade(warning?0x992E0D14:0x99525668));
            g.pose().pushMatrix();g.pose().translate(bx+3,y+4);g.pose().scale(.7f,.7f);
            InterfaceText.draw(g,severity,0,0,UiMotion.fade(warning?0xFFFF5969:0xFFC5C9D8));g.pose().popMatrix();
        }
        if (switchStyle && badge==0) {
            // Clean pill-style toggle switch
            int trackX = x+w-21;
            int trackBg = Motion.mix(0xFF222226, accent, on * 0.7);
            rounded(g,trackX,y+5,16,6,UiMotion.fade(trackBg));
            // Switch knob - clean circle
            int knobX = trackX+1+(int)Math.round(on*10);
            int knobColor = on > 0.5 ? 0xFFE8E8EA : 0xFF606066;
            rounded(g,knobX,y+6,4,4,UiMotion.fade(knobColor));
        } else if (on > 0.01) {
            // Subtle bottom accent line for active non-switch buttons
            g.fill(x+2, y+15, x+w-2, y+16, UiMotion.fade(Motion.mix(0x00000000, accent, on * 0.5)));
        }
    }
    static void rounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w<=0 || h<=0 || color>>>24==0) return;
        int radius=Math.min(h>40 ? 8 : 4,Math.min(w,h)/2);
        if (radius<1) { g.fill(x,y,x+w,y+h,color); return; }
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
