package dev.levrn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Original visual treatment over vanilla's accessible button/input behavior. */
final class LevrnButton extends Button {
    private final boolean highlighted;
    LevrnButton(String text, int x, int y, int width, Runnable action, boolean highlighted) {
        super(x, y, width, 20, InterfaceText.component(text), b -> action.run(), DEFAULT_NARRATION);
        this.highlighted = highlighted;
    }
    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth();
        int accent = LevrnClient.STATE.theme().accent;
        int border = isHoveredOrFocused() ? accent : highlighted ? 0xFF31616A : 0xFF263239;
        rounded(g, x, y, w, 20, border);
        rounded(g, x + 1, y + 1, w - 2, 18,
            isHoveredOrFocused() ? 0xFF16343E : highlighted ? 0xFF102D37 : 0xFF101A22);
        var font = Minecraft.getInstance().font;
        String text = InterfaceText.fit(getMessage().getString(), Math.max(1, w - 8));
        InterfaceText.draw(g, text, x + (w - InterfaceText.width(text)) / 2, y + 6,
            highlighted || isHoveredOrFocused() ? accent : LevrnHud.TEXT);
    }
    static void rounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w < 4 || h < 4) { g.fill(x, y, x + w, y + h, color); return; }
        g.fill(x + 3, y, x + w - 3, y + h, color);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
        g.fill(x, y + 3, x + w, y + h - 3, color);
    }
}
