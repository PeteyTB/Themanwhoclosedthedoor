package dev.levrn.client;

import dev.levrn.core.Motion;
import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayDeque;

final class Notifications {
    private record Notice(String title, String detail, long created) { }
    private static final ArrayDeque<Notice> QUEUE = new ArrayDeque<>();
    static void add(String title, String detail) {
        if (QUEUE.size() >= 3) QUEUE.removeFirst();
        QUEUE.addLast(new Notice(title, detail, System.nanoTime()));
    }
    static void render(GuiGraphics g) {
        double scale=LevrnClient.STATE.uiScale()/net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        g.pose().pushMatrix();g.pose().scale((float)scale,(float)scale);
        renderNotices(g,(int)(g.guiWidth()/scale),(int)(g.guiHeight()/scale));
        g.pose().popMatrix();
    }
    private static void renderNotices(GuiGraphics g,int canvasWidth,int canvasHeight) {
        long now = System.nanoTime(); QUEUE.removeIf(n -> now - n.created() > 3_000_000_000L);
        boolean lowerHud=net.minecraft.client.Minecraft.getInstance().player!=null && (LevrnClient.STATE.enabled("keystrokes") || LevrnClient.STATE.enabled("armor_hud"));
        int y = canvasHeight - (lowerHud ? 134 : 58);
        for (Notice notice : QUEUE.reversed()) {
            double age = (now-notice.created())/1e9;
            double phase = LevrnClient.STATE.reducedMotion() ? 1 : Math.min(Math.clamp(age/0.25,0,1),Math.clamp((3-age)/0.4,0,1));
            double ease = 1-Math.pow(1-phase,3);
            int w = Math.min(210,canvasWidth-20), x = canvasWidth-w-10+(int)((1-ease)*(w+12));
            int alpha = (int)(230*phase);
            LevrnButton.rounded(g,x-2,y-2,w+4,42,(int)(30*phase)<<24 | LevrnClient.STATE.theme().accent & 0xFFFFFF);
            LevrnButton.rounded(g,x,y,w,38,alpha<<24|0x0C1825);
            g.fill(x,y,x+2,y+38,(int)(255*phase)<<24|LevrnClient.STATE.theme().accent & 0xFFFFFF);
            InterfaceText.draw(g,InterfaceText.fit(notice.title(),w-16),x+8,y+7,(int)(255*phase)<<24|0xECF8FF);
            InterfaceText.draw(g,InterfaceText.fit(notice.detail(),w-16),x+8,y+22,(int)(255*phase)<<24|0x95AFBF);
            g.fill(x+8,y+35,x+8+(int)((w-16)*(1-age/3)),y+36,(int)(150*phase)<<24|LevrnClient.STATE.theme().accent & 0xFFFFFF);
            y -= 46;
        }
    }
    private Notifications() { }
}
