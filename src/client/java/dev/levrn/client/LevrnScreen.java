package dev.levrn.client;

import dev.levrn.core.Category;
import dev.levrn.core.Motion;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleConsumer;
import dev.levrn.core.Feature;
import dev.levrn.core.WorkspaceLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compact multi-category workspace with global search, scrolling, and a separate module inspector. */
public final class LevrnScreen extends Screen {
    private Category category;
    private String query = "";
    private boolean settings, refresh, activeOnly;
    private Feature selected;
    private int left, top, panelWidth, panelHeight, contentX, contentY, contentWidth, viewportHeight, columns, scroll, maxScroll;
    private EditBox search;
    private WorkspaceLayout.Result layout = new WorkspaceLayout.Result(List.of(), 0);
    private final List<Control> controls = new ArrayList<>();
    private record Numeric(double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter, String suffix, double scale) { }
    private record Control(String title, String description, Runnable action, Numeric numeric) {
        Control(String title, String description, Runnable action) { this(title,description,action,null); }
    }
    private final Map<String,UiMotion.Channel> channels = new HashMap<>();
    private final Motion opening = new Motion(0), page = new Motion(0), scrolling = new Motion(0), indicator = new Motion(0);
    private long lastFrame = System.nanoTime();
    private double elapsed, dragOffsetX, dragOffsetY;
    private int scrollTarget;
    private boolean closing, dragging;
    private UiMotion.Channel channel(String key) { return channels.computeIfAbsent(key,k -> new UiMotion.Channel()); }
    private void resetView() { scroll = scrollTarget = 0; scrolling.snap(0); page.snap(0); }
    private boolean inputReady() { return !closing && (LevrnClient.STATE.reducedMotion() || opening.value() > 0.85 && page.value() > 0.85); }
    private void rebuildPreservingInput() {
        boolean focused = search != null && search.isFocused(); int cursor = search == null ? 0 : search.getCursorPosition();
        rebuildWidgets();
        if (focused && search != null && !settings && selected == null) { setFocused(search); search.setCursorPosition(cursor); }
    }

    public LevrnScreen() { super(Component.literal("Levrn workspace")); }
    public void requestRefresh() { refresh = true; }

    @Override protected void init() {
        panelWidth = Math.min(900, Math.max(240,(int)(width * 0.92))); panelHeight = Math.min(620,Math.max(160,(int)(height * 0.92)));
        left = Math.clamp((int)Math.round(LevrnClient.STATE.windowX()*(width-panelWidth)),8,Math.max(8,width-panelWidth-8));
        top = Math.clamp((int)Math.round(LevrnClient.STATE.windowY()*(height-panelHeight)),8,Math.max(8,height-panelHeight-8));
        contentX = left + 12; contentY = top + 81; contentWidth = panelWidth - 24;
        viewportHeight = panelHeight - 112;
        button("Settings", left + panelWidth - 72, top + 12, 60, () -> { settings = !settings; selected = null; resetView(); requestRefresh(); }, settings);
        if (!settings && selected == null) {
            search = new EditBox(font, left + 105, top + 12, Math.max(50, panelWidth - 184), 20, Component.literal("Search all modules"));
            search.setHint(InterfaceText.component("Search: aura, sprint")); search.setMaxLength(100); search.setValue(query);
            search.setResponder(value -> { query = value; resetView(); refresh = true; });
            addRenderableWidget(search);
        } else {
            button("< Back", left + 105, top + 12, 65, () -> { settings = false; selected = null; resetView(); requestRefresh(); }, false);
        }
        int tabWidth = (contentWidth - 32) / 5;
        button("ALL", contentX, top + 44, tabWidth, () -> select(null), category == null && !settings && selected == null);
        int i = 1;
        for (Category item : Category.values()) {
            int x = contentX + i++ * (tabWidth + 8);
            button(item.label.toUpperCase(Locale.ROOT), x, top + 44, tabWidth, () -> select(item), category == item && !settings && selected == null);
        }
        button("PANIC", left + panelWidth - 58, top + panelHeight - 26, 46, () -> { LevrnClient.panic(); requestRefresh(); }, false);
        if (settings || selected != null) buildInspector(); else buildWorkspace();
    }
    private void select(Category value) { category = value; settings = false; selected = null; resetView(); requestRefresh(); }

    private void buildWorkspace() {
        var matches = LevrnClient.STATE.search(query.isBlank() ? category : null, query).stream()
            .filter(f -> !activeOnly || f.enabled()).toList();
        columns = contentWidth >= 540 ? 4 : contentWidth >= 260 ? 2 : 1;
        layout = category != null && query.isBlank() ? WorkspaceLayout.spreadCategory(matches, columns)
            : WorkspaceLayout.arrange(matches, columns);
        maxScroll = Math.max(0, layout.height() - viewportHeight);
        scroll = Math.clamp(scroll, 0, maxScroll); scrollTarget = Math.clamp(scrollTarget,0,maxScroll);
        int laneWidth = (contentWidth - (columns - 1) * 10) / columns;
        for (var section : layout.sections()) {
            int x = contentX + section.column() * (laneWidth + 10);
            int row = 0;
            for (Feature feature : section.features()) {
                int y = contentY + section.top() + 25 + row++ * 25 - scroll;
                if (y < contentY || y + 20 > contentY + viewportHeight) continue;
                var toggle = addRenderableWidget(new LevrnButton(feature.name,x+5,y,laneWidth-31,
                    () -> { LevrnClient.toggle(feature); requestRefresh(); },feature.enabled(),channel("module/"+feature.id),true));
                toggle.setTooltip(Tooltip.create(Component.literal(feature.description)));
                addRenderableWidget(new LevrnButton(">",x+laneWidth-23,y,18,() -> { selected=feature; resetView(); requestRefresh(); },false,channel("details/"+feature.id),false));
            }
        }
    }
    private void buildInspector() {
        controls.clear(); var s = LevrnClient.STATE;
        if (selected != null) {
            controls.add(new Control(selected.name + ": " + (selected.enabled() ? "ON" : "OFF"), selected.description,
                () -> { LevrnClient.toggle(selected); requestRefresh(); }));
        }
        if (selected != null) controls.add(new Control("KEY / " + LevrnClient.keyLabel(selected.id),"Rebind this module in Minecraft Controls.",
            () -> minecraft.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this,minecraft.options))));
        boolean targeting = settings || selected != null && java.util.Set.of("aura","trigger","aim_assist").contains(selected.id);
        if (targeting) {
            controls.add(new Control("PvP targeting preset", "Players ON, teams OFF; sword/axe cooldown is read from the game.", () -> {
                s.setTargetPlayers(true); s.setTargetMobs(false); s.setRespectTeams(false); s.setRotateAura(true);
                s.setAttackRange(3); s.setCooldown(0.92); changed();
            }));
            toggle("Player targets", s.targetPlayers(), "Enable this to target other players.", () -> s.setTargetPlayers(!s.targetPlayers()));
            toggle("Mob targets", s.targetMobs(), "Target mobs independently of player targets.", () -> s.setTargetMobs(!s.targetMobs()));
            toggle("Respect teams", s.respectTeams(), "Disable on servers that put all duel players on one scoreboard team.", () -> s.setRespectTeams(!s.respectTeams()));
            toggle("Invisible targets", s.targetInvisible(), "Allow invisible entities that otherwise pass the filters.", () -> s.setTargetInvisible(!s.targetInvisible()));
            toggle("Aura visible rotation", s.rotateAura(), "Move your real view toward the selected hitbox before attacking.", () -> s.setRotateAura(!s.rotateAura()));
            numeric("Reach", "Eye-to-hitbox distance within normal interaction range.",1,3,0.05,s::attackRange,s::setAttackRange," blocks",1);
            numeric("Cooldown", "Uses the held weapon's actual cooldown.",0.8,1,0.01,s::cooldown,s::setCooldown,"%",100);
            numeric("Aim speed", "Maximum visible degrees per tick.",1,90,1,s::aimSpeed,s::setAimSpeed," deg/t",1);
            numeric("Target FOV", "Angular target selection limit.",15,360,5,s::targetFov,s::setTargetFov," deg",1);
            toggle("Stable target lock",s.stickyTargets(),"Retain a valid target until another is substantially closer.",() -> s.setStickyTargets(!s.stickyTargets()));

        }
        if (settings || selected != null && selected.id.equals("auto_totem")) numeric("Totem health","Health plus absorption threshold.",1,40,1,s::totemHealth,s::setTotemHealth," HP",1);
        if (settings || selected != null && selected.id.equals("auto_armor")) {
            toggle("Upgrade worn armor",s.armorUpgrade(),"Compare base armor, toughness and remaining durability.",() -> s.setArmorUpgrade(!s.armorUpgrade()));
            toggle("Preserve Elytra",s.preserveElytra(),"Leave an equipped Elytra in the chest slot.",() -> s.setPreserveElytra(!s.preserveElytra()));
        }
        if (settings || selected != null && selected.id.equals("auto_replenish"))
            numeric("Refill below","Refill only stacks with identical item components.",1,32,1,() -> s.replenishBelow(),v -> s.setReplenishBelow((int)v)," items",1);
        if (settings || selected != null && java.util.Set.of("auto_armor","auto_totem","auto_replenish","auto_tool","auto_weapon","shield_breaker").contains(selected.id))
            numeric("Inventory pacing","Minimum ticks between inventory transactions.",2,10,1,s::inventoryInterval,s::setInventoryInterval," ticks",1);
        if (settings || selected != null && selected.id.equals("zoom")) numeric("Zoom FOV","Narrower values zoom in further.",10,70,1,() -> s.zoomFov(),v -> s.setZoomFov((int)v)," deg",1);
        if (settings) {
            toggle("Reduced motion",s.reducedMotion(),"Stop animated decoration and make transitions instant.",() -> s.setReducedMotion(!s.reducedMotion()));
            numeric("Animation speed","Adjust opening, hover and transition timing.",0.5,2.5,0.1,s::motionSpeed,s::setMotionSpeed,"x",1);
            controls.add(new Control("Theme / " + s.theme().label, "Cycle Nova, Dusk, Glacier and Ember.", () -> { s.setTheme(s.theme().next()); changed(); }));
            controls.add(new Control("View / " + (activeOnly ? "active only" : "all modules"), "Filter the workspace to enabled modules.", () -> { activeOnly = !activeOnly; requestRefresh(); }));
            controls.add(new Control("Save profile", "Write the current validated profile atomically.", LevrnClient::save));
            controls.add(new Control("Reload profile", "Load saved settings; invalid files are preserved.", () -> { LevrnClient.load(); requestRefresh(); }));
            controls.add(new Control("Reset defaults", "Restore default modules and targeting configuration.", () -> { s.reset(); changed(); }));
        }
        int cols = contentWidth >= 330 ? 2 : 1;
        int rows = (controls.size() + cols - 1) / cols;
        int cellWidth = (contentWidth - (cols - 1) * 12) / cols;
        maxScroll = Math.max(0, 32 + rows * 34 - viewportHeight); scroll = Math.clamp(scroll, 0, maxScroll); scrollTarget = Math.clamp(scrollTarget,0,maxScroll);
        for (int i = 0; i < controls.size(); i++) {
            var control = controls.get(i);
            int x = contentX + i % cols * (cellWidth + 12), y = contentY + 28 + i / cols * 34 - scroll;
            if (y < contentY + 24 || y + 26 > contentY + viewportHeight) continue;
            if (control.numeric() != null) {
                var n = control.numeric();
                var slider = addRenderableWidget(new LevrnSlider(x,y,cellWidth,control.title(),n.min(),n.max(),n.step(),n.getter().getAsDouble(),n.setter(),n.suffix(),n.scale(),channel("slider/"+control.title())));
                slider.setTooltip(Tooltip.create(Component.literal(control.description())));
            } else {
                var button = button(control.title(), x, y, cellWidth, control.action(), false);
                button.setTooltip(Tooltip.create(Component.literal(control.description())));
            }
        }
    }
    private void toggle(String title, boolean value, String description, Runnable action) {
        controls.add(new Control(title + " / " + (value ? "ON" : "OFF"), description, () -> { action.run(); changed(); }));
    }
    private void numeric(String title,String description,double min,double max,double step,DoubleSupplier getter,DoubleConsumer setter,String suffix,double scale) {
        controls.add(new Control(title,description,null,new Numeric(min,max,step,getter,setter,suffix,scale)));
    }
    private void changed() { LevrnClient.save(); requestRefresh(); }
    private LevrnButton button(String text, int x, int y, int w, Runnable action, boolean selected) {
        return addRenderableWidget(new LevrnButton(text, x, y, Math.max(12, w), action, selected,channel("button/"+text.split(" / ")[0]),false));
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!closing) scrollTarget = Math.clamp(scrollTarget - (int)(vertical * 42),0,maxScroll);
        return true;
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime(); double dt = Math.min(0.05,(now-lastFrame)/1e9)*LevrnClient.STATE.motionSpeed(); lastFrame = now; elapsed += dt;
        boolean still = LevrnClient.STATE.reducedMotion();
        if (still) { opening.snap(closing ? 0 : 1); page.snap(1); scrolling.snap(scrollTarget); }
        else { opening.step(closing ? 0 : 1,dt,closing ? 24 : 17); page.step(1,dt,20); scrolling.step(scrollTarget,dt,22); }
        if (closing && opening.value() < 0.02) { LevrnClient.flushSave(); minecraft.setScreen(null); return; }
        int animatedScroll = Math.clamp((int)Math.round(scrolling.value()),0,maxScroll);
        if (animatedScroll != scroll) { scroll = animatedScroll; refresh = true; }
        if (refresh) { refresh = false; rebuildPreservingInput(); }
        UiMotion.opacity = Math.clamp(opening.value()*page.value(),0,1);
        int accent = LevrnClient.STATE.theme().accent;
        g.fill(0, 0, width, height, (int)(180*opening.value())<<24|0x03060F);
        int reveal = (int)(panelHeight*Math.clamp(opening.value(),0,1));
        g.enableScissor(left-8,top+(panelHeight-reveal)/2-8,left+panelWidth+8,top+(panelHeight+reveal)/2+8);
        LevrnButton.rounded(g,left-7,top-7,panelWidth+14,panelHeight+14,UiMotion.fade(0x1624CDE9));
        LevrnButton.rounded(g,left-3,top-3,panelWidth+6,panelHeight+6,UiMotion.fade(0x30285E80));
        LevrnButton.rounded(g, left - 1, top - 1, panelWidth + 2, panelHeight + 2, UiMotion.fade(0xFF31526F));
        LevrnButton.rounded(g, left, top, panelWidth, panelHeight, UiMotion.fade(0xEE080F1D));
        for (int x = left + 16; x < left + panelWidth; x += 28) g.fill(x, top + 75, x + 1, top + panelHeight - 30, 0x0934D9E5);
        for (int y = top + 80; y < top + panelHeight - 30; y += 28) g.fill(left + 8, y, left + panelWidth - 8, y + 1, 0x0934D9E5);
        if (!still) drawConstellation(g,accent);
        g.fill(left + 12, top, left + 90, top + 2, UiMotion.fade(accent));
        InterfaceText.draw(g, "L E V R N", left + 13, top + 14, accent);
        InterfaceText.draw(g, "NEXUS / 03", left + 14, top + 29, 0xFF71899A);
        g.fill(contentX, top + 71, left + panelWidth - 12, top + 72, 0xFF203440);
        if (!settings && selected == null) {
            double targetIndex = category == null ? 0 : category.ordinal()+1;
            if (still) indicator.snap(targetIndex); else indicator.step(targetIndex,dt,18);
            int tabWidth = (contentWidth-32)/5, ix = contentX+(int)Math.round(indicator.value()*(tabWidth+8));
            g.fill(ix+8,top+67,ix+tabWidth-8,top+68,UiMotion.fade(accent));
        }
        if (settings || selected != null) {
            InterfaceText.draw(g, settings ? "CONTROL CENTER" : selected.name.toUpperCase(Locale.ROOT), contentX, contentY + 4, accent);
        } else {
            g.enableScissor(contentX, contentY, contentX + contentWidth, contentY + viewportHeight);
            int laneWidth = (contentWidth - (columns - 1) * 10) / columns;
            for (var section : layout.sections()) {
                int x = contentX + section.column() * (laneWidth + 10), y = contentY + section.top() - scroll;
                g.fill(x, y, x + laneWidth, y + section.height(), 0x75121F29);
                g.fill(x, y, x + laneWidth, y + 21, 0xFF122632);
                InterfaceText.draw(g, section.category().label.toUpperCase(Locale.ROOT) + " / " + section.features().size(), x + 7, y + 6, accent);
            }
            if (layout.sections().isEmpty()) InterfaceText.draw(g, "No matches. Use commas for multiple modules.", contentX + 8, contentY + 15, LevrnHud.MUTED);
            g.disableScissor();
        }
        if (maxScroll > 0) {
            int h = Math.max(12, viewportHeight * viewportHeight / (viewportHeight + maxScroll));
            int y = contentY + (viewportHeight - h) * scroll / maxScroll;
            g.fill(left + panelWidth - 6, contentY, left + panelWidth - 4, contentY + viewportHeight, 0xFF1C303A);
            g.fill(left + panelWidth - 6, y, left + panelWidth - 4, y + h, accent);
        }
        long enabled = LevrnClient.STATE.features().stream().filter(Feature::enabled).count();
        String footer = enabled + "/" + LevrnClient.STATE.features().size() + " ACTIVE / " + LevrnClient.status();
        InterfaceText.draw(g, InterfaceText.fit(footer, panelWidth - 85), contentX, top + panelHeight - 20, 0xFF91ACB9);
        super.render(g, mouseX, mouseY, delta);
        g.disableScissor(); UiMotion.opacity = 1; Notifications.render(g);
    }
    private void drawConstellation(GuiGraphics g,int accent) {
        int color = (int)(24*opening.value())<<24|accent&0xFFFFFF;
        for (int i=0;i<18;i++) {
            int x=left+10+(int)((i*47.0+elapsed*(3+i%3))%Math.max(1,panelWidth-20));
            int y=top+76+(int)((i*71.0+Math.sin(elapsed*.7+i)*12)%Math.max(1,panelHeight-110));
            int a=(int)((25+22*Math.sin(elapsed*1.6+i))*opening.value());
            g.fill(x-1,y-1,x+2,y+2,a<<24|accent&0xFFFFFF);
            if (i%3==0) { g.fill(x,y,x+22,y+1,color); g.fill(x+21,y,x+22,y+14,color); }
        }
        int scanY=top+75+(int)((elapsed*26)%Math.max(1,panelHeight-108));
        for (int i=0;i<10;i++) g.fill(left+8,scanY+i,left+panelWidth-8,scanY+i+1,(int)((10-i)*1.5*opening.value())<<24|accent&0xFFFFFF);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubled) {
        if (!inputReady()) return true;
        if (event.button()==0 && event.x()>=left+8 && event.x()<left+97 && event.y()>=top+5 && event.y()<top+39) {
            dragging=true; dragOffsetX=event.x()-left; dragOffsetY=event.y()-top; return true;
        }
        return super.mouseClicked(event,doubled);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
        if (dragging) {
            int nx=Math.clamp((int)(event.x()-dragOffsetX),8,Math.max(8,width-panelWidth-8));
            int ny=Math.clamp((int)(event.y()-dragOffsetY),8,Math.max(8,height-panelHeight-8));
            LevrnClient.STATE.setWindowX(nx/(double)Math.max(1,width-panelWidth));
            LevrnClient.STATE.setWindowY(ny/(double)Math.max(1,height-panelHeight));
            LevrnClient.scheduleSave(); requestRefresh(); return true;
        }
        return !inputReady() || super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) { dragging=false; LevrnClient.flushSave(); return true; }
        return super.mouseReleased(event);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (!inputReady()) { if (event.key()==256) onClose(); return true; }
        return super.keyPressed(event);
    }
    @Override public void onClose() { closing=true; dragging=false; LevrnClient.flushSave(); }
    @Override public void removed() { LevrnClient.flushSave(); super.removed(); }

    @Override public boolean isPauseScreen() { return false; }
}
