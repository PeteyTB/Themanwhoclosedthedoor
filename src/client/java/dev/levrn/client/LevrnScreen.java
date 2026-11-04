package dev.levrn.client;

import dev.levrn.core.Category;
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
    private record Control(String title, String description, Runnable action) { }

    public LevrnScreen() { super(Component.literal("Levrn workspace")); }
    public void requestRefresh() { refresh = true; }

    @Override protected void init() {
        panelWidth = Math.min(1100, width - 16); panelHeight = Math.min(660, height - 16);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        contentX = left + 12; contentY = top + 81; contentWidth = panelWidth - 24;
        viewportHeight = panelHeight - 112;
        button("Settings", left + panelWidth - 72, top + 12, 60, () -> { settings = !settings; selected = null; scroll = 0; rebuildWidgets(); }, settings);
        if (!settings && selected == null) {
            search = new EditBox(font, left + 105, top + 12, Math.max(50, panelWidth - 184), 20, Component.literal("Search all modules"));
            search.setHint(InterfaceText.component("Search: aura, sprint")); search.setMaxLength(100); search.setValue(query);
            search.setResponder(value -> { query = value; scroll = 0; refresh = true; });
            addRenderableWidget(search);
        } else {
            button("< Back", left + 105, top + 12, 65, () -> { settings = false; selected = null; scroll = 0; rebuildWidgets(); }, false);
        }
        int tabWidth = (contentWidth - 32) / 5;
        button("ALL", contentX, top + 44, tabWidth, () -> select(null), category == null && !settings && selected == null);
        int i = 1;
        for (Category item : Category.values()) {
            int x = contentX + i++ * (tabWidth + 8);
            button(item.label.toUpperCase(Locale.ROOT), x, top + 44, tabWidth, () -> select(item), category == item && !settings && selected == null);
        }
        button("PANIC", left + panelWidth - 58, top + panelHeight - 26, 46, () -> { LevrnClient.panic(); rebuildWidgets(); }, false);
        if (settings || selected != null) buildInspector(); else buildWorkspace();
    }
    private void select(Category value) { category = value; settings = false; selected = null; scroll = 0; rebuildWidgets(); }

    private void buildWorkspace() {
        var matches = LevrnClient.STATE.search(query.isBlank() ? category : null, query).stream()
            .filter(f -> !activeOnly || f.enabled()).toList();
        columns = contentWidth >= 540 ? 4 : contentWidth >= 260 ? 2 : 1;
        layout = category != null && query.isBlank() ? WorkspaceLayout.spreadCategory(matches, columns)
            : WorkspaceLayout.arrange(matches, columns);
        maxScroll = Math.max(0, layout.height() - viewportHeight);
        scroll = Math.clamp(scroll, 0, maxScroll);
        int laneWidth = (contentWidth - (columns - 1) * 10) / columns;
        for (var section : layout.sections()) {
            int x = contentX + section.column() * (laneWidth + 10);
            int row = 0;
            for (Feature feature : section.features()) {
                int y = contentY + section.top() + 25 + row++ * 25 - scroll;
                if (y < contentY || y + 20 > contentY + viewportHeight) continue;
                var toggle = button((feature.enabled() ? "+ " : "  ") + feature.name, x + 5, y, laneWidth - 31,
                    () -> { feature.toggle(); LevrnClient.save(); rebuildWidgets(); }, feature.enabled());
                toggle.setTooltip(Tooltip.create(Component.literal(feature.description)));
                button(">", x + laneWidth - 23, y, 18, () -> { selected = feature; scroll = 0; rebuildWidgets(); }, false);
            }
        }
    }
    private void buildInspector() {
        controls.clear(); var s = LevrnClient.STATE;
        if (selected != null) {
            controls.add(new Control(selected.name + ": " + (selected.enabled() ? "ON" : "OFF"), selected.description,
                () -> { selected.toggle(); changed(); }));
        }
        if (settings || selected != null && selected.category == Category.COMBAT) {
            controls.add(new Control("PvP targeting preset", "Players ON, teams OFF; sword/axe cooldown is read from the game.", () -> {
                s.setTargetPlayers(true); s.setTargetMobs(false); s.setRespectTeams(false); s.setRotateAura(true);
                s.setAttackRange(3); s.setCooldown(0.92); changed();
            }));
            toggle("Player targets", s.targetPlayers(), "Enable this to target other players.", () -> s.setTargetPlayers(!s.targetPlayers()));
            toggle("Mob targets", s.targetMobs(), "Target mobs independently of player targets.", () -> s.setTargetMobs(!s.targetMobs()));
            toggle("Respect teams", s.respectTeams(), "Disable on servers that put all duel players on one scoreboard team.", () -> s.setRespectTeams(!s.respectTeams()));
            toggle("Invisible targets", s.targetInvisible(), "Allow invisible entities that otherwise pass the filters.", () -> s.setTargetInvisible(!s.targetInvisible()));
            toggle("Aura visible rotation", s.rotateAura(), "Move your real view toward the selected hitbox before attacking.", () -> s.setRotateAura(!s.rotateAura()));
            controls.add(new Control(String.format(Locale.ROOT, "Reach %.1f / +0.25", s.attackRange()), "Hitbox distance, limited to normal interaction reach.", () -> { s.setAttackRange(s.attackRange() >= 3 ? 1 : s.attackRange() + 0.25); changed(); }));
            controls.add(new Control(String.format(Locale.ROOT, "Cooldown %.0f%% / +2", s.cooldown() * 100), "Uses the held item's live cooldown, not fixed clicks per second.", () -> { s.setCooldown(s.cooldown() >= 0.999 ? 0.8 : s.cooldown() + 0.02); changed(); }));
            controls.add(new Control(String.format(Locale.ROOT, "Aim speed %.0f / +5", s.aimSpeed()), "Maximum visible rotation degrees per tick.", () -> { s.setAimSpeed(s.aimSpeed() >= 90 ? 5 : s.aimSpeed() + 5); changed(); }));
            controls.add(new Control(String.format(Locale.ROOT, "Target FOV %.0f / +30", s.targetFov()), "Angular target selection limit.", () -> { s.setTargetFov(s.targetFov() >= 360 ? 30 : s.targetFov() + 30); changed(); }));
            controls.add(new Control(String.format(Locale.ROOT, "Totem health %.0f / +2", s.totemHealth()), "Health plus absorption threshold for Auto Totem.", () -> { s.setTotemHealth(s.totemHealth() >= 40 ? 2 : s.totemHealth() + 2); changed(); }));
        }
        if (settings) {
            controls.add(new Control("Theme / " + s.theme().label, "Cycle Nova, Dusk, Glacier and Ember.", () -> { s.setTheme(s.theme().next()); changed(); }));
            controls.add(new Control("Zoom FOV / " + s.zoomFov(), "Cycle 10 to 70 degrees.", () -> { s.setZoomFov(s.zoomFov() >= 70 ? 10 : s.zoomFov() + 5); changed(); }));
            controls.add(new Control("View / " + (activeOnly ? "active only" : "all modules"), "Filter the workspace to enabled modules.", () -> { activeOnly = !activeOnly; rebuildWidgets(); }));
            controls.add(new Control("Save profile", "Write the current validated profile atomically.", LevrnClient::save));
            controls.add(new Control("Reload profile", "Load saved settings; invalid files are preserved.", () -> { LevrnClient.load(); rebuildWidgets(); }));
            controls.add(new Control("Reset defaults", "Restore default modules and targeting configuration.", () -> { s.reset(); changed(); }));
        }
        int cols = contentWidth >= 330 ? 2 : 1;
        int rows = (controls.size() + cols - 1) / cols;
        int cellWidth = (contentWidth - (cols - 1) * 12) / cols;
        maxScroll = Math.max(0, 32 + rows * 31 - viewportHeight); scroll = Math.clamp(scroll, 0, maxScroll);
        for (int i = 0; i < controls.size(); i++) {
            var control = controls.get(i);
            int x = contentX + i % cols * (cellWidth + 12), y = contentY + 28 + i / cols * 31 - scroll;
            if (y < contentY + 24 || y + 20 > contentY + viewportHeight) continue;
            var button = button(control.title(), x, y, cellWidth, control.action(), false);
            button.setTooltip(Tooltip.create(Component.literal(control.description())));
        }
    }
    private void toggle(String title, boolean value, String description, Runnable action) {
        controls.add(new Control(title + " / " + (value ? "ON" : "OFF"), description, () -> { action.run(); changed(); }));
    }
    private void changed() { LevrnClient.save(); rebuildWidgets(); }
    private LevrnButton button(String text, int x, int y, int w, Runnable action, boolean selected) {
        return addRenderableWidget(new LevrnButton(text, x, y, Math.max(12, w), action, selected));
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll = Math.clamp(scroll - (int)(vertical * 34), 0, maxScroll); rebuildWidgets(); return true;
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (refresh) {
            boolean focus = search != null && search.isFocused(); int cursor = search == null ? 0 : search.getCursorPosition();
            refresh = false; rebuildWidgets(); if (focus && search != null) { setFocused(search); search.setCursorPosition(cursor); }
        }
        int accent = LevrnClient.STATE.theme().accent;
        g.fill(0, 0, width, height, 0xB005090F);
        LevrnButton.rounded(g, left - 1, top - 1, panelWidth + 2, panelHeight + 2, 0xFF29404B);
        LevrnButton.rounded(g, left, top, panelWidth, panelHeight, 0xFC080F17);
        for (int x = left + 16; x < left + panelWidth; x += 28) g.fill(x, top + 75, x + 1, top + panelHeight - 30, 0x0934D9E5);
        for (int y = top + 80; y < top + panelHeight - 30; y += 28) g.fill(left + 8, y, left + panelWidth - 8, y + 1, 0x0934D9E5);
        g.fill(left + 12, top, left + 90, top + 2, accent);
        InterfaceText.draw(g, "L E V R N", left + 13, top + 14, accent);
        InterfaceText.draw(g, "SYSTEM / 02", left + 14, top + 29, 0xFF71899A);
        g.fill(contentX, top + 71, left + panelWidth - 12, top + 72, 0xFF203440);
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
    }
    @Override public boolean isPauseScreen() { return false; }
}
