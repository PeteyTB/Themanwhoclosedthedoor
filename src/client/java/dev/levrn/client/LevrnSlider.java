package dev.levrn.client;

import dev.levrn.core.Motion;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/** Futuristic slider with glow track and accent handle. */
final class LevrnSlider extends AbstractSliderButton {
    private final String label, suffix;
    private final double min, max, step, scale;
    private final DoubleConsumer setter;
    private final UiMotion.Channel motion;
    LevrnSlider(int x, int y, int width, String label, double min, double max, double step,
        double current, DoubleConsumer setter, String suffix, double scale, UiMotion.Channel motion) {
        super(x, y, width, 20, InterfaceText.component(label), Math.clamp((current - min) / (max - min), 0, 1));
        this.label = label; this.min = min; this.max = max; this.step = step;
        this.setter = setter; this.suffix = suffix; this.scale = scale; this.motion = motion; updateMessage();
    }
    private double actual() { return Motion.quantize(min + value * (max - min), min, max, step); }
    @Override protected void updateMessage() {
        setMessage(InterfaceText.component(label + " / " + String.format(Locale.ROOT, "%.2f", actual() * scale) + suffix));
    }
    @Override protected void applyValue() { setter.accept(actual()); LevrnClient.scheduleSave(); }
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        motion.update(isHoveredOrFocused(), true);
        int accent = LevrnClient.STATE.theme().accent;
        double hover = motion.hover.value();

        if(hover>.01) LevrnButton.rounded(g,getX(),getY(),getWidth(),getHeight(),UiMotion.fade((int)(hover*20)<<24|0xA0A8B8));
        String valueLabel=String.format(Locale.ROOT,"%.2f",actual()*scale)+suffix;
        InterfaceText.draw(g,InterfaceText.fit(label,getWidth()-InterfaceText.width(valueLabel)-24),getX()+5,getY()+3,UiMotion.fade(LevrnHud.TEXT));
        InterfaceText.draw(g,valueLabel,getX()+getWidth()-InterfaceText.width(valueLabel)-5,getY()+3,UiMotion.fade(0xFFA6ADBF));

        int x = getX()+8, y = getY()+16, w = getWidth()-16;
        // Track background
        g.fill(x,y,x+w,y+1,UiMotion.fade(0xFF3B4252));
        // Filled portion with accent
        int handle = x+(int)Math.round(w*value);
        g.fill(x,y,handle,y+1,UiMotion.fade(accent));
        // Handle with glow
        if (hover > 0.2) {
            LevrnButton.rounded(g,handle-5,y-5,10,12,UiMotion.fade((int)(hover*25)<<24|accent&0xFFFFFF));
        }
        LevrnButton.rounded(g,handle-2,y-2,4,5,UiMotion.fade(accent));
    }
}
