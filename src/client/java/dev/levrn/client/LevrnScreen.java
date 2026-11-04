package dev.levrn.client;

import dev.levrn.core.Category;
import dev.levrn.core.Feature;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Native widgets retain keyboard navigation, narration, and configurable GUI scale. */
public final class LevrnScreen extends Screen {
    private Category category;
    private String query = "";
    private boolean settings;
    private int page;
    private int left, top, panelWidth, panelHeight, sidebar, contentX, contentWidth, pageSize;
    private EditBox search;

    public LevrnScreen() { super(Component.literal("Levrn")); }

    @Override protected void init() {
        panelWidth = Math.min(760, width - 16);
        panelHeight = Math.min(450, height - 16);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        sidebar = panelWidth < 460 ? 82 : 116;
        contentX = left + sidebar + 14; contentWidth = panelWidth - sidebar - 28;
        button("Overview", left + 8, top + 63, sidebar - 16, () -> select(null));
        int y = top + 91;
        for (Category item : Category.values()) {
            button(item.label, left + 8, y, sidebar - 16, () -> select(item)); y += 28;
        }
        button("Settings", left + 8, top + panelHeight - 62, sidebar - 16, () -> {
            settings = true; rebuildWidgets();
        });
        button("Disable all", left + 8, top + panelHeight - 34, sidebar - 16, () -> {
            LevrnClient.panic(); rebuildWidgets();
        });
        if (settings) { settingsWidgets(); return; }
        search = new EditBox(font, contentX, top + 37, contentWidth, 20, Component.literal("Search modules"));
        search.setHint(Component.literal("Search modules..."));
        search.setMaxLength(80);
        search.setValue(query);
        search.setResponder(value -> {
            query = value; page = 0;
            // Rebuild on the next render so the responder does not mutate the widget list during input dispatch.
            refresh = true;
        });
        addRenderableWidget(search);
        buildCards();
    }

    private boolean refresh;
    private List<Feature> visible = List.of();

    private void select(Category value) { category = value; page = 0; settings = false; rebuildWidgets(); }

    private void buildCards() {
        var matches = LevrnClient.STATE.search(category, query);
        int columns = contentWidth >= 330 ? 2 : 1;
        int rows = Math.max(1, (panelHeight - 114) / 66);
        pageSize = rows * columns;
        int pages = Math.max(1, (matches.size() + pageSize - 1) / pageSize);
        page = Math.clamp(page, 0, pages - 1);
        visible = matches.subList(Math.min(matches.size(), page * pageSize), Math.min(matches.size(), (page + 1) * pageSize));
        int cardWidth = (contentWidth - (columns - 1) * 10) / columns;
        for (int i = 0; i < visible.size(); i++) {
            Feature feature = visible.get(i);
            int x = contentX + (i % columns) * (cardWidth + 10), y = top + 72 + (i / columns) * 66;
            button(feature.enabled() ? "ON" : "OFF", x + cardWidth - 45, y + 5, 39, () -> {
                feature.toggle(); LevrnClient.save(); rebuildWidgets();
            });
        }
        button("<", contentX, top + panelHeight - 32, 26, () -> { page = Math.max(0, page - 1); rebuildWidgets(); });
        button(">", contentX + 31, top + panelHeight - 32, 26, () -> { page = Math.min(pages - 1, page + 1); rebuildWidgets(); });
    }

    private void settingsWidgets() {
        var s = LevrnClient.STATE;
        int y = top + 61;
        button("Players: " + (s.targetPlayers() ? "included" : "excluded"), contentX, y, contentWidth, () -> {
            s.setTargetPlayers(!s.targetPlayers()); changed();
        });
        button("Theme: " + s.theme().label, contentX, y + 25, contentWidth, () -> {
            s.setTheme(s.theme().next()); changed();
        });
        button(String.format(java.util.Locale.ROOT, "Attack range: %.1f blocks  (+0.5)", s.attackRange()), contentX, y + 50, contentWidth, () -> {
            s.setAttackRange(s.attackRange() >= 3 ? 1 : s.attackRange() + 0.5); changed();
        });
        button("Zoom FOV: " + s.zoomFov() + "  (+5)", contentX, y + 75, contentWidth, () -> {
            s.setZoomFov(s.zoomFov() >= 70 ? 10 : s.zoomFov() + 5); changed();
        });
        int half = (contentWidth - 8) / 2;
        button("Save profile", contentX, y + 106, half, () -> { LevrnClient.save(); });
        button("Reload profile", contentX + half + 8, y + 106, half, () -> { LevrnClient.load(); rebuildWidgets(); });
        button("Restore defaults", contentX, y + 137, contentWidth, () -> { s.reset(); changed(); });
    }

    private void changed() { LevrnClient.save(); rebuildWidgets(); }
    private void button(String text, int x, int y, int w, Runnable action) {
        boolean highlighted = text.equals("ON") || (!settings && category != null && text.equals(category.label))
            || (!settings && category == null && text.equals("Overview")) || (settings && text.equals("Settings"));
        addRenderableWidget(new LevrnButton(text, x, y, w, action, highlighted));
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (refresh) {
            boolean focused = search != null && search.isFocused();
            int cursor = search == null ? 0 : search.getCursorPosition();
            refresh = false; rebuildWidgets();
            if (focused && search != null) { setFocused(search); search.setCursorPosition(cursor); }
        }
        g.fill(0, 0, width, height, 0x90090910);
        g.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, 0xFF393441);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xFF15151E);
        g.fill(left, top, left + sidebar, top + panelHeight, 0xFF1B1A25);
        g.fill(left, top, left + panelWidth, top + 2, LevrnClient.STATE.theme().accent);
        g.drawString(font, "LEVRN", left + 14, top + 19, LevrnHud.TEXT, false);
        g.drawString(font, font.plainSubstrByWidth("The next chapter.", sidebar - 18), left + 10, top + 37, LevrnHud.MUTED, false);
        g.drawString(font, settings ? "Client settings" : category == null ? "Your workspace" : category.label,
            contentX, top + 18, LevrnHud.TEXT, false);
        if (!settings) renderCards(g);
        else g.drawString(font, "Changes save automatically", contentX, top + 38, LevrnHud.MUTED, false);
        String status = font.plainSubstrByWidth(LevrnClient.status(), Math.max(10, contentWidth - 70));
        g.drawString(font, status, contentX + 67, top + panelHeight - 25, LevrnHud.MUTED, false);
        super.render(g, mouseX, mouseY, delta);
    }

    private void renderCards(GuiGraphics g) {
        int columns = contentWidth >= 330 ? 2 : 1;
        int cardWidth = (contentWidth - (columns - 1) * 10) / columns;
        if (visible.isEmpty()) g.drawString(font, "No matching modules", contentX, top + 88, LevrnHud.MUTED, false);
        for (int i = 0; i < visible.size(); i++) {
            var f = visible.get(i);
            int x = contentX + (i % columns) * (cardWidth + 10), y = top + 72 + (i / columns) * 66;
            LevrnButton.rounded(g, x, y, cardWidth, 58, f.enabled() ? 0xFF292536 : 0xFF201F2B);
            g.fill(x, y + 5, x + 2, y + 53, f.enabled() ? LevrnClient.STATE.theme().accent : 0xFF3B3747);
            g.drawString(font, font.plainSubstrByWidth(f.name, cardWidth - 58), x + 9, y + 11, LevrnHud.TEXT, false);
            var lines = font.split(Component.literal(f.description), cardWidth - 18);
            for (int line = 0; line < Math.min(2, lines.size()); line++)
                g.drawString(font, lines.get(line), x + 9, y + 31 + line * 10, LevrnHud.MUTED, false);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
