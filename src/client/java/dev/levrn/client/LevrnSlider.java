package dev.levrn.client;

import dev.levrn.core.Motion;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/** Native drag/keyboard semantics with a continuous, step-quantized numeric control. */
final class LevrnSlider extends AbstractSliderButton {
    private final String label, suffix;
    private final double min, max, step, scale;
    private final DoubleConsumer setter;
    private final UiMotion.Channel motion;
    LevrnSlider(int x, int y, int width, String label, double min, double max, double step,
        double current, DoubleConsumer setter, String suffix, double scale, UiMotion.Channel motion) {
        super(x, y, width, 26, InterfaceText.component(label), Math.clamp((current - min) / (max - min), 0, 1));
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
        LevrnButton.rounded(g, getX(), getY(), getWidth(), getHeight(), UiMotion.fade(Motion.mix(0xFF101C2A,0xFF1D3448,motion.hover.value())));
        InterfaceText.draw(g, InterfaceText.fit(getMessage().getString(), getWidth()-14), getX()+7, getY()+4, UiMotion.fade(LevrnHud.TEXT));
        int x = getX()+8, y = getY()+20, w = getWidth()-16;
        g.fill(x,y,x+w,y+2,UiMotion.fade(0xFF2B4253));
        int handle = x+(int)Math.round(w*value);
        g.fill(x,y,handle,y+2,UiMotion.fade(accent));
        LevrnButton.rounded(g,handle-3,y-3,6,8,UiMotion.fade(accent));
    }
}
