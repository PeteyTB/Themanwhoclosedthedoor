package dev.levrn.client;

import dev.levrn.core.Category;
import dev.levrn.core.ClientState;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Three-panel workspace: sidebar categories, module list, and inspector panel.
 * Clean flat design with severity tags and professional layout.
 */
public final class LevrnScreen extends Screen {
    // Colors - neutral dark gray palette (no blue tint)
    private static final int BG_DARK = 0x9813161C;       // Main panel background
    private static final int BG_SIDEBAR = 0x6013151B;     // Sidebar background
    private static final int BG_PANEL = 0x5014181F;       // Content panel backgrounds
    private static final int BG_ROW = 0x4020252D;         // Module row background
    private static final int BG_ROW_HOVER = 0xFF1C1C1F;   // Module row hover
    private static final int BG_ROW_ACTIVE = 0xCC080A0E;  // Active module row
    private static final int BORDER = 0xCC080A0E;         // Borders (1px, subtle)
    private static final int TEXT_PRIMARY = 0xFFD8D8DC;   // Primary text
    private static final int TEXT_MUTED = 0xFF85858D;      // Muted text
    private static final int TAG_WARNING = 0xFFCC3344;     // WARNING tag
    private static final int TAG_STANDARD = 0xFF2A2A30;    // STANDARD tag
    private static final int TAG_TEXT_WARN = 0xFFFF8899;   // WARNING tag text
    private static final int TAG_TEXT_STD = 0xFF888890;    // STANDARD tag text

    private final Map<net.minecraft.client.gui.components.AbstractWidget,String> descriptions=new java.util.IdentityHashMap<>();
    private String hoveredDescription="";
    private long descriptionSince;
    private void describe(net.minecraft.client.gui.components.AbstractWidget widget,String description) { descriptions.put(widget,description); }
    private Category category=Category.COMBAT;
    private int canvasWidth,canvasHeight;
    private double canvasScale=1;
    private String query = "";
    private boolean settings, refresh, activeOnly, hudStudio;
    private Feature selected=LevrnClient.STATE.feature("aura");
    private boolean panelView,dockVisible=true,dockOnlyOn;
    private int dockX,dockWidth=160,dockY=10,dockHeight,dockScroll,dockMaxScroll,dockTab;
    private final Map<Category,Integer> panelScroll=new java.util.EnumMap<>(Category.class);
    private final java.util.Set<Category> collapsed=java.util.EnumSet.noneOf(Category.class);
    private final List<Card> cards=new ArrayList<>();
    private record Card(Category category,int x,int y,int width,int height) {}
    private int left, top, panelWidth, panelHeight;
    private int sidebarWidth, topBarHeight;
    // Center column (module list)
    private int centerX, centerY, centerWidth, centerViewportHeight;
    // Right panel (inspector) - only when selected or settings
    private int inspectorX, inspectorWidth;
    private int columns, scroll, maxScroll;
    // Inspector scroll state
    private int inspScroll, inspMaxScroll, inspScrollTarget;
    private LevrnSearchBox search;
    private WorkspaceLayout.Result layout = new WorkspaceLayout.Result(List.of(), 0);
    private final List<Control> controls = new ArrayList<>();
    private record Numeric(double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter, String suffix, double scale) { }
    private record Control(String title, String description, Runnable action, Numeric numeric) {
        Control(String title, String description, Runnable action) { this(title,description,action,null); }
    }
    private final Map<String,UiMotion.Channel> channels = new HashMap<>();
    private final Motion opening = new Motion(0), page = new Motion(0), scrolling = new Motion(0), indicator = new Motion(0);
    private final Motion inspScrolling = new Motion(0);
    private long lastFrame = System.nanoTime();
    private double elapsed, dragOffsetX, dragOffsetY;
    private int scrollTarget;
    private boolean closing, dragging;
    private UiMotion.Channel channel(String key) { return channels.computeIfAbsent(key,k -> new UiMotion.Channel()); }
    private void resetView() { scroll = scrollTarget = 0; scrolling.snap(0); page.snap(0); inspScroll = inspScrollTarget = 0; inspScrolling.snap(0); }
    private boolean inputReady() { return !closing && (LevrnClient.STATE.reducedMotion() || opening.value() > 0.85 && page.value() > 0.85); }
    private void rebuildPreservingInput() {
        boolean focused = search != null && search.isFocused(); int cursor = search == null ? 0 : search.getCursorPosition();
        int anchor=search==null ? 0 : search.anchor();
        rebuildWidgets();
        if (focused && search != null && !settings && !hudStudio) { setFocused(search); search.setCursorPosition(cursor);search.setHighlightPos(anchor); }
    }

    // HUD element definitions for HUD Studio
    private static final String[][] HUD_ELEMENTS = {
        {"hud", "Watermark"}, {"coordinates", "Coordinates"}, {"active_list", "Active Modules"},
        {"target_hud", "Target HUD"}, {"combat_status", "Combat Status"}, {"armor_hud", "Armor HUD"},
        {"speed_hud", "Speed HUD"}, {"ping_hud", "Ping HUD"}, {"clock", "Clock"},
        {"compass", "Compass"}, {"keystrokes", "Keystrokes"}, {"potion_hud", "Potion HUD"}
    };

    public LevrnScreen() { super(Component.literal("LVERN workspace")); }
    public void requestRefresh() { refresh = true; }
    private void updateQuery(String value) {
        if(query.equals(value)) return;
        query=value;scroll=scrollTarget=0;scrolling.snap(0);panelScroll.clear();requestRefresh();
    }

    @Override protected void init() {
        descriptions.clear();
        canvasScale=LevrnClient.STATE.uiScale()/minecraft.getWindow().getGuiScale();
        canvasWidth=(int)(width/canvasScale);canvasHeight=(int)(height/canvasScale);

        dockWidth=Math.min(160,canvasWidth/4);dockX=canvasWidth-dockWidth-8;dockHeight=canvasHeight-20;
        int available=dockVisible ? dockX-16 : canvasWidth-16;
        panelWidth=Math.min(600,available);panelHeight=Math.min(410,canvasHeight-72);
        left=Math.clamp((int)(LevrnClient.STATE.windowX()*(available-panelWidth)),8,Math.max(8,available-panelWidth-8));top=Math.clamp((int)(LevrnClient.STATE.windowY()*(canvasHeight-panelHeight)),36,Math.max(36,canvasHeight-panelHeight-8));
        sidebarWidth=100;topBarHeight=40;
        boolean hasInspector=selected!=null || settings;
        if(hasInspector) {centerWidth=Math.min(158,(panelWidth-sidebarWidth-24)/3);inspectorX=left+sidebarWidth+centerWidth+16;inspectorWidth=panelWidth-sidebarWidth-centerWidth-28;}
        else {centerWidth=panelWidth-sidebarWidth-20;inspectorX=0;inspectorWidth=0;}
        sideButton(panelView?"Workspace":"Panels",Math.max(160,available-80),12,72,() -> {panelView=!panelView;resetView();requestRefresh();},false);
        if(panelView) {buildPanels(available);if(dockVisible) buildDock();return;}
        centerX = left + sidebarWidth + 8;
        centerY = top + topBarHeight + 4;
        centerViewportHeight = panelHeight - topBarHeight - 28;

        // Top bar: search box (right-aligned in top bar)
        if(!hudStudio) {
            int searchW = Math.min(180, panelWidth - sidebarWidth - 120);
            int searchX = left + panelWidth - searchW - 16;
            search=new LevrnSearchBox(font,searchX+2,top+12,searchW,16,Component.literal("Search modules"));
            search.setTextShadow(false);search.setBordered(false);search.setTextColor(TEXT_PRIMARY);search.setHint(InterfaceText.component("Search..."));search.setMaxLength(100);search.setValue(query);
            search.setResponder(this::updateQuery);addRenderableWidget(search);
        }

        // Sidebar category buttons
        int sideY = top + topBarHeight + 8;
        sideButton("Overview",left+6,sideY,sidebarWidth-12,() -> select(null),category==null&&!settings&&selected==null&&!hudStudio).alignLeft();
        int i=1;
        for(Category item:Category.values()) {
            sideButton(item.label,left+6,sideY+i*22,sidebarWidth-12,() -> select(item),category==item&&!settings&&selected==null&&!hudStudio).alignLeft();
            i++;
        }

        // Sidebar bottom buttons
        int bottomY = top + panelHeight - 78;
        sideButton("NOVA",left+6,bottomY-22,sidebarWidth-12,() -> {selected=LevrnClient.STATE.feature("adaptive");category=Category.COMBAT;settings=false;hudStudio=false;resetView();requestRefresh();},selected!=null && selected.id.equals("adaptive")).alignLeft();
        sideButton("HUD Studio",left+6,bottomY,sidebarWidth-12,() -> { dockVisible=!dockVisible;requestRefresh(); },hudStudio).alignLeft();
        sideButton("Settings",left+6,bottomY+22,sidebarWidth-12,() -> {settings=!settings;selected=null;hudStudio=false;resetView();requestRefresh();},settings).alignLeft();
        sideButton("PANIC",left+6,bottomY+44,sidebarWidth-12,() -> {LevrnClient.panic();requestRefresh();},false).alignLeft();

        // Always build the module list (center column)
        if(hudStudio) buildHudStudio();
        else buildWorkspace();

        // Build inspector in right panel if needed
        if(hasInspector && !hudStudio) buildInspector();
        if(dockVisible) buildDock();
    }

    private void select(Category value) { category = value; settings = false; selected = null; hudStudio = false; resetView(); requestRefresh(); }

    private static String severity(Feature feature) {
        return java.util.Set.of("aura","trigger","criticals","velocity","adaptive","auto_totem","auto_eat","auto_fish","fast_place","auto_replenish").contains(feature.id) ? "WARNING" : "STANDARD";
    }
    private void buildPanels(int available) {
        cards.clear();
        search=new LevrnSearchBox(font,166,14,Math.max(80,Math.min(220,available-260)),16,Component.literal("Search modules"));
        search.setTextShadow(false);search.setBordered(false);search.setHint(InterfaceText.component("Search modules, settings..."));search.setValue(query);
        search.setResponder(this::updateQuery);addRenderableWidget(search);
        int count=Math.clamp((available-16)/145,1,4),w=Math.min(185,(available-16-(count-1)*8)/count);
        int[] ys=new int[count];java.util.Arrays.fill(ys,48);int i=0;
        for(Category cat:Category.values()) {
            var list=LevrnClient.STATE.search(cat,query);int col=i++%count,x=8+col*(w+8),y=ys[col];
            int h=collapsed.contains(cat)?24:Math.min(canvasHeight-y-12,30+list.size()*20);ys[col]+=h+8;
            var card=new Card(cat,x,y,w,h);cards.add(card);
            sideButton(cat.label+" / "+list.size()+"  ˅",x+3,y+3,w-6,() -> {if(!collapsed.add(cat)) collapsed.remove(cat);requestRefresh();},false);
            if(collapsed.contains(cat)) continue;
            int offset=panelScroll.getOrDefault(cat,0);
            for(int row=0;row<list.size();row++) {
                var feature=list.get(row);int ry=y+27+row*20-offset;
                if(ry<y+24 || ry+16>y+h-3) continue;
                var toggle=addRenderableWidget(new LevrnButton(feature.name,x+4,ry,w-24,() -> {LevrnClient.toggle(feature);requestRefresh();},feature.enabled(),channel("panel/"+feature.id),true));
                toggle.severity(severity(feature));describe(toggle,feature.description);
                sideButton("⋮",x+w-18,ry,14,() -> {selected=feature;category=cat;panelView=false;resetView();requestRefresh();},false);
            }
        }
    }
    private void drawPanels(GuiGraphics g) {
        for(var card:cards) {
            LevrnButton.rounded(g,card.x(),card.y(),card.width(),card.height(),UiMotion.fade(0xC014181D));
            g.fill(card.x()+1,card.y()+23,card.x()+card.width()-1,card.y()+24,UiMotion.fade(BORDER));
        }
    }
    private void drawBrand(GuiGraphics g) {
        LevrnButton.rounded(g,8,8,148,24,UiMotion.fade(0xE5252B36));
        InterfaceText.draw(g,"●  LVERN",15,15,UiMotion.fade(TEXT_PRIMARY));
        InterfaceText.draw(g,minecraft.getFps()+" fps  ·  "+java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),75,15,UiMotion.fade(TEXT_MUTED));
    }
    private List<String[]> dockItems() {
        return java.util.Arrays.stream(HUD_ELEMENTS).filter(item -> !dockOnlyOn || LevrnClient.STATE.enabled(item[0])).toList();
    }
    private void buildDock() {
        sideButton("×",dockX+dockWidth-20,dockY+4,15,() -> {dockVisible=false;requestRefresh();},false);
        String[] tabs={"Library","Layouts","Style"};
        for(int i=0;i<3;i++) {final int tab=i;sideButton(tabs[i],dockX+6+i*(dockWidth-12)/3,dockY+24,(dockWidth-12)/3-2,() -> {dockTab=tab;dockScroll=0;requestRefresh();},dockTab==i);}
        sideButton("Preview HUD",dockX+6,dockY+45,dockWidth-12,() -> minecraft.setScreen(null),false);
        if(dockTab==0) {
            sideButton(dockOnlyOn ? "Showing: On" : "Showing: All",dockX+6,dockY+65,dockWidth-12,() -> {dockOnlyOn=!dockOnlyOn;dockScroll=0;requestRefresh();},dockOnlyOn);
            var items=dockItems();dockMaxScroll=Math.max(0,items.size()*92-(dockHeight-90));dockScroll=Math.clamp(dockScroll,0,dockMaxScroll);
            for(int i=0;i<items.size();i++) {
                var item=items.get(i);var feature=LevrnClient.STATE.feature(item[0]);int y=dockY+88+i*92-dockScroll;
                if(y<dockY+84 || y+88>dockY+dockHeight) continue;
                var toggle=addRenderableWidget(new LevrnButton(item[1],dockX+6,y+58,dockWidth-12,() -> {LevrnClient.toggle(feature);requestRefresh();},feature.enabled(),channel("dock/"+item[0]),true));describe(toggle,feature.description);
            }
        } else if(dockTab==1) {
            String[] names={"Minimal","Essentials","Combat"};
            String[][] ids={{"hud","coordinates"},{"hud","coordinates","active_list","armor_hud","potion_hud"},{"hud","target_hud","combat_status","armor_hud","keystrokes","ping_hud"}};
            for(int i=0;i<3;i++) {final int choice=i;sideButton(names[i],dockX+8,dockY+88+i*28,dockWidth-16,() -> {
                var chosen=java.util.Set.of(ids[choice]);for(var item:HUD_ELEMENTS) LevrnClient.STATE.feature(item[0]).setEnabled(chosen.contains(item[0]));changed();
            },false);}
        } else {
            int i=0;for(var theme:dev.levrn.core.Theme.values()) {sideButton(theme.label,dockX+8,dockY+88+i++*23,dockWidth-16,() -> {LevrnClient.STATE.setTheme(theme);changed();},LevrnClient.STATE.theme()==theme);}
            sideButton("Size / "+String.format(Locale.ROOT,"%.1f",LevrnClient.STATE.uiScale()),dockX+8,dockY+288,dockWidth-16,() -> {var state=LevrnClient.STATE;state.setUiScale(state.uiScale()>=1.99?1:Math.round((state.uiScale()+.1)*10)/10.0);changed();},false);
            sideButton("Reduced motion",dockX+8,dockY+312,dockWidth-16,() -> {LevrnClient.STATE.setReducedMotion(!LevrnClient.STATE.reducedMotion());changed();},LevrnClient.STATE.reducedMotion());
        }
    }
    private void drawDock(GuiGraphics g) {
        if(!dockVisible) return;
        LevrnButton.rounded(g,dockX,dockY,dockWidth,dockHeight,UiMotion.fade(0xEE12151C));
        InterfaceText.draw(g,"HUD STUDIO",dockX+8,dockY+7,UiMotion.fade(TEXT_PRIMARY));
        if(dockTab!=0) return;
        var items=dockItems();
        g.enableScissor(dockX+2,dockY+84,dockX+dockWidth-2,dockY+dockHeight-3);
        for(int i=0;i<items.size();i++) {
            var item=items.get(i);boolean on=LevrnClient.STATE.enabled(item[0]);int y=dockY+88+i*92-dockScroll;
            if(y+88<dockY+84 || y>dockY+dockHeight) continue;
            g.fill(dockX+5,y,dockX+dockWidth-5,y+87,UiMotion.fade(0xCC1B2029));
            LevrnButton.rounded(g,dockX+8,y+3,dockWidth-16,50,UiMotion.fade(0xFF090B0F));
            if(!on) {InterfaceText.draw(g,"OFF",dockX+12,y+8,UiMotion.fade(TEXT_MUTED));InterfaceText.draw(g,"Switch on to preview",dockX+12,y+32,UiMotion.fade(TEXT_MUTED));}
            else if(item[0].equals("clock")) InterfaceText.draw(g,java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),dockX+35,y+22,UiMotion.fade(TEXT_PRIMARY));
            else if(item[0].equals("hud")) {LevrnButton.rounded(g,dockX+14,y+16,dockWidth-28,23,0xFF303747);InterfaceText.draw(g,"LVERN   "+minecraft.getFps()+" fps",dockX+23,y+24,TEXT_PRIMARY);}
            else if(item[0].equals("armor_hud")) {for(int part=0;part<4;part++){var stack=minecraft.player==null?net.minecraft.world.item.ItemStack.EMPTY:minecraft.player.getItemBySlot(new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET}[part]);double value=stack.isEmpty()?0:stack.isDamageableItem()?1-stack.getDamageValue()/(double)Math.max(1,stack.getMaxDamage()):1;int bx=dockX+22+part*(dockWidth-40)/4;g.fill(bx,y+12,bx+12,y+42,0xFF2A303B);g.fill(bx,y+42-(int)(30*value),bx+12,y+42,0xFFB5BDCE);}}
            else InterfaceText.draw(g,InterfaceText.fit(item[1],dockWidth-30),dockX+15,y+24,UiMotion.fade(TEXT_PRIMARY));
            InterfaceText.draw(g,InterfaceText.fit(LevrnClient.STATE.feature(item[0]).description,dockWidth-16),dockX+8,y+77,UiMotion.fade(TEXT_MUTED));
        }
        g.disableScissor();
    }

    private void buildWorkspace() {
        var matches = LevrnClient.STATE.search(query.isBlank() ? category : null, query).stream()
            .filter(f -> !activeOnly || f.enabled()).toList();
        // Single column list for clean rows
        columns = 1;
        layout = category != null && query.isBlank() ? WorkspaceLayout.spreadCategory(matches, columns)
            : WorkspaceLayout.arrange(matches, columns);
        maxScroll = Math.max(0, layout.height() - centerViewportHeight);
        scroll = Math.clamp(scroll, 0, maxScroll); scrollTarget = Math.clamp(scrollTarget,0,maxScroll);
        int laneWidth = centerWidth;
        for (var section : layout.sections()) {
            int x = centerX;
            int row = 0;
            for (Feature feature : section.features()) {
                int y = centerY + section.top() + 22 + row++ * 18 - scroll;
                if (y < centerY || y + 16 > centerY + centerViewportHeight) continue;
                // Module toggle button - takes most of the width
                int btnWidth = laneWidth - 20;
                var toggle = addRenderableWidget(new LevrnButton(feature.name,x+3,y,btnWidth,
                    () -> { LevrnClient.toggle(feature); requestRefresh(); },feature.enabled(),channel("module/"+feature.id),true));
                toggle.severity(severity(feature));describe(toggle,feature.description);
                // Settings button
                addRenderableWidget(new LevrnButton("⋮",x+laneWidth-15,y,13,() -> { selected=feature; hudStudio=false; inspScroll=inspScrollTarget=0;inspScrolling.snap(0); requestRefresh(); },selected==feature,channel("details/"+feature.id),false));
            }
        }
    }

    private void buildHudStudio() {
        int cols = centerWidth >= 360 ? 3 : centerWidth >= 240 ? 2 : 1;
        int cellWidth = (centerWidth - (cols - 1) * 10) / cols;
        maxScroll = Math.max(0, (HUD_ELEMENTS.length / cols + 1) * 42 - centerViewportHeight);
        scroll = Math.clamp(scroll, 0, maxScroll); scrollTarget = Math.clamp(scrollTarget,0,maxScroll);
        for (int idx = 0; idx < HUD_ELEMENTS.length; idx++) {
            String id = HUD_ELEMENTS[idx][0];
            String name = HUD_ELEMENTS[idx][1];
            Feature feature = LevrnClient.STATE.feature(id);
            int col = idx % cols, row = idx / cols;
            int x = centerX + col * (cellWidth + 10);
            int y = centerY + 24 + row * 42 - scroll;
            if (y < centerY + 20 || y + 36 > centerY + centerViewportHeight) continue;
            var toggle = addRenderableWidget(new LevrnButton(name, x, y, cellWidth,
                () -> { LevrnClient.toggle(feature); requestRefresh(); }, feature.enabled(), channel("hud/"+id), true));
            describe(toggle, feature.description);
        }
    }

    private void buildInspector() {
        controls.clear(); var s = LevrnClient.STATE;
        int ix = inspectorX;
        int iw = inspectorWidth;
        if (selected != null) {
            controls.add(new Control(selected.name + ": " + (selected.enabled() ? "ON" : "OFF"), selected.description,
                () -> { LevrnClient.toggle(selected); requestRefresh(); }));
        }
        if (selected != null) controls.add(new Control("KEY / " + LevrnClient.keyLabel(selected.id),"Rebind this module in Minecraft Controls.",
            () -> minecraft.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this,minecraft.options))));
        boolean targeting = selected != null && java.util.Set.of("aura","trigger","aim_assist").contains(selected.id);
        if (targeting) {
            controls.add(new Control("PvP targeting preset", "Players ON, teams OFF; sword/axe cooldown is read from the game.", () -> {
                s.setTargetPlayers(true); s.setTargetMobs(false); s.setRespectTeams(false); s.setRotateAura(true);
                s.setAttackRange(3); s.setCooldown(0.92); s.setTargetFov(360);s.setAuraRotation(ClientState.AuraRotation.SILENT);changed();
            }));
            toggle("Player targets", s.targetPlayers(), "Enable this to target other players.", () -> s.setTargetPlayers(!s.targetPlayers()));
            toggle("Mob targets", s.targetMobs(), "Target mobs independently of player targets.", () -> s.setTargetMobs(!s.targetMobs()));
            toggle("Respect teams", s.respectTeams(), "Disable on servers that put all duel players on one scoreboard team.", () -> s.setRespectTeams(!s.respectTeams()));
            toggle("Invisible targets", s.targetInvisible(), "Allow invisible entities that otherwise pass the filters.", () -> s.setTargetInvisible(!s.targetInvisible()));
            controls.add(new Control("Aura rotation / "+s.auraRotation(),"Silent keeps the camera free; visible turns your view; off requires manual aim.",() -> {
                var modes=ClientState.AuraRotation.values();s.setAuraRotation(modes[(s.auraRotation().ordinal()+1)%modes.length]);changed();
            }));
            numeric("Reach", "Eye-to-hitbox distance within normal interaction range.",1,3,0.05,s::attackRange,s::setAttackRange," blocks",1);
            numeric("Cooldown", "Uses the held weapon's actual cooldown.",0.8,1,0.01,s::cooldown,s::setCooldown,"%",100);
            numeric("Aim speed", "Maximum rotation degrees per movement update.",1,90,1,s::aimSpeed,s::setAimSpeed," deg/t",1);
            numeric("Target FOV", "Angular target selection limit.",15,360,5,s::targetFov,s::setTargetFov," deg",1);
            toggle("Stable target lock",s.stickyTargets(),"Retain a valid target until another is substantially closer.",() -> s.setStickyTargets(!s.stickyTargets()));
        }
        if (selected != null && selected.id.equals("auto_totem")) {
            toggle("Always keep totem",s.totemAlways(),"Proactively equip a totem in offhand regardless of health.",() -> s.setTotemAlways(!s.totemAlways()));
            numeric("Totem health","Health plus absorption threshold (ignored when Always is ON).",1,40,1,s::totemHealth,s::setTotemHealth," HP",1);
        }
        if (selected != null && selected.id.equals("auto_armor")) {
            toggle("Upgrade worn armor",s.armorUpgrade(),"Compare base armor, toughness and remaining durability.",() -> s.setArmorUpgrade(!s.armorUpgrade()));
            toggle("Preserve Elytra",s.preserveElytra(),"Leave an equipped Elytra in the chest slot.",() -> s.setPreserveElytra(!s.preserveElytra()));
        }
        if (selected != null && selected.id.equals("auto_replenish"))
            numeric("Refill below","Refill only stacks with identical item components.",1,32,1,() -> s.replenishBelow(),v -> s.setReplenishBelow((int)v)," items",1);
        if (selected != null && java.util.Set.of("auto_armor","auto_totem","auto_replenish","auto_tool","auto_weapon","shield_breaker").contains(selected.id))
            numeric("Inventory pacing","Minimum ticks between inventory transactions.",2,10,1,s::inventoryInterval,s::setInventoryInterval," ticks",1);
        if (selected != null && selected.id.equals("zoom")) numeric("Zoom FOV","Narrower values zoom in further.",10,70,1,() -> s.zoomFov(),v -> s.setZoomFov((int)v)," deg",1);
        if(selected!=null && selected.id.equals("velocity")) {
            numeric("Horizontal knockback","Incoming entity velocity; 100% preserves it.",0,100,5,s::velocityHorizontal,s::setVelocityHorizontal,"%",1);
            numeric("Vertical knockback","Teleport corrections remain authoritative.",0,100,5,s::velocityVertical,s::setVelocityVertical,"%",1);
        }
        if(selected!=null && selected.id.equals("auto_eat")) numeric("Eat below hunger","Hotbar food; excludes teleporting, poisonous and golden foods.",1,19,1,() -> s.eatHunger(),v -> s.setEatHunger((int)v),"",1);
        if(selected!=null && selected.id.equals("fast_place")) numeric("Place delay","Minimum two ticks; only block placement.",2,4,1,() -> s.placeDelay(),v -> s.setPlaceDelay((int)v)," ticks",1);
        if (selected != null && selected.id.equals("adaptive")) {
            AdaptiveEngine engine = LevrnClient.novaEngine();
            toggle("NOVA enabled", s.adaptiveEnabled(), "Enable the adaptive combat AI engine.", () -> {
                s.setAdaptiveEnabled(!s.adaptiveEnabled());
                if (engine != null) engine.setEnabled(s.adaptiveEnabled());
            });
            // The rest of the NOVA inspector (performance bar, confidence, adjustments)
            // is rendered as custom graphics in renderContents / renderWorkspace
        }
        if (settings) {
            controls.add(new Control("Interface size / "+String.format(Locale.ROOT,"%.1fx",s.uiScale()),"Independent of Minecraft GUI scale; cycle 1.0 through 2.0.",() -> {s.setUiScale(s.uiScale()>=1.99 ? 1 : Math.round((s.uiScale()+0.1)*10)/10.0);changed();}));
            controls.add(new Control("Combat defaults", "Target filters, rotation mode and cooldown.",() -> {settings=false;selected=s.feature("aura");resetView();requestRefresh();}));
            controls.add(new Control("Inventory defaults", "Armor preservation and inventory pacing.",() -> {settings=false;selected=s.feature("auto_armor");resetView();requestRefresh();}));
            toggle("Reduced motion",s.reducedMotion(),"Stop animated decoration and make transitions instant.",() -> s.setReducedMotion(!s.reducedMotion()));
            numeric("Animation speed","Adjust opening, hover and transition timing.",0.5,2.5,0.1,s::motionSpeed,s::setMotionSpeed,"x",1);
            controls.add(new Control("Theme / " + s.theme().label, "Cycle through all available themes.", () -> { s.setTheme(s.theme().next()); changed(); }));
            controls.add(new Control("View / " + (activeOnly ? "active only" : "all modules"), "Filter the workspace to enabled modules.", () -> { activeOnly = !activeOnly; requestRefresh(); }));
            controls.add(new Control("Save profile", "Write the current validated profile atomically.", LevrnClient::save));
            controls.add(new Control("Reload profile", "Load saved settings; invalid files are preserved.", () -> { LevrnClient.load(); requestRefresh(); }));
            controls.add(new Control("Reset defaults", "Restore default modules and targeting configuration.", () -> { s.reset(); changed(); }));
        }
        // Place controls in the inspector panel (single column)
        int inspContentY = centerY + 56;
        int inspViewH = centerViewportHeight - 56;
        inspMaxScroll = Math.max(0, controls.size() * 22 + (selected!=null && selected.id.equals("adaptive") ? 270 : 0) - inspViewH);
        inspScroll = Math.clamp(inspScroll, 0, inspMaxScroll); inspScrollTarget = Math.clamp(inspScrollTarget,0,inspMaxScroll);
        for (int ci = 0; ci < controls.size(); ci++) {
            var control = controls.get(ci);
            int x = ix + 4, y = inspContentY + ci * 22 - inspScroll;
            int cellWidth = iw - 8;
            if (y < inspContentY - 2 || y + 20 > inspContentY + inspViewH) continue;
            if (control.numeric() != null) {
                var n = control.numeric();
                var slider = addRenderableWidget(new LevrnSlider(x,y,cellWidth,control.title(),n.min(),n.max(),n.step(),n.getter().getAsDouble(),n.setter(),n.suffix(),n.scale(),channel("slider/"+control.title())));
                describe(slider,control.description());
            } else {
                var btn = addRenderableWidget(new LevrnButton(control.title(), x, y, Math.max(12, cellWidth), control.action(), false,channel("button/"+control.title().split(" / ")[0]),false));
                btn.settingRow();describe(btn,control.description());
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
    private LevrnButton sideButton(String text, int x, int y, int w, Runnable action, boolean selected) {
        return addRenderableWidget(new LevrnButton(text, x, y, Math.max(12, w), action, selected,channel("button/"+text.split(" / ")[0]),false));
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (closing) return true;
        double mx = x / canvasScale,my=y/canvasScale;
        if(dockVisible && mx>=dockX) {dockScroll=Math.clamp(dockScroll-(int)(vertical*38),0,dockMaxScroll);requestRefresh();return true;}
        if(panelView) {for(var c:cards) if(mx>=c.x() && mx<c.x()+c.width() && my>=c.y() && my<c.y()+c.height()) {int max=Math.max(0,LevrnClient.STATE.search(c.category(),"").size()*20+30-c.height());panelScroll.put(c.category(),Math.clamp(panelScroll.getOrDefault(c.category(),0)-(int)(vertical*30),0,max));requestRefresh();break;}return true;}
        // If mouse is over inspector panel, scroll inspector
        if ((selected != null || settings) && mx >= inspectorX) {
            inspScrollTarget = Math.clamp(inspScrollTarget - (int)(vertical * 42),0,inspMaxScroll);
        } else {
            scrollTarget = Math.clamp(scrollTarget - (int)(vertical * 42),0,maxScroll);
        }
        return true;
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.pose().pushMatrix();g.pose().scale((float)canvasScale,(float)canvasScale);
        renderWorkspace(g,(int)(mouseX/canvasScale),(int)(mouseY/canvasScale),delta);
        g.pose().popMatrix();Notifications.render(g);
    }
    private void renderWorkspace(GuiGraphics g,int mouseX,int mouseY,float delta) {
        long now = System.nanoTime(); double dt = Math.min(0.05,(now-lastFrame)/1e9)*LevrnClient.STATE.motionSpeed(); lastFrame = now; elapsed += dt;
        boolean still = LevrnClient.STATE.reducedMotion();
        if (still) { opening.snap(closing ? 0 : 1); page.snap(1); scrolling.snap(scrollTarget); inspScrolling.snap(inspScrollTarget); }
        else { opening.step(closing ? 0 : 1,dt,closing ? 24 : 17); page.step(1,dt,20); scrolling.step(scrollTarget,dt,22); inspScrolling.step(inspScrollTarget,dt,22); }
        if (closing && opening.value() < 0.02) { LevrnClient.flushSave(); minecraft.setScreen(null); return; }
        int animatedScroll = Math.clamp((int)Math.round(scrolling.value()),0,maxScroll);
        if (animatedScroll != scroll) { scroll = animatedScroll; refresh = true; }
        int animatedInspScroll = Math.clamp((int)Math.round(inspScrolling.value()),0,Math.max(0,inspMaxScroll));
        if (animatedInspScroll != inspScroll) { inspScroll = animatedInspScroll; refresh = true; }
        if (refresh) { refresh = false; rebuildPreservingInput(); }
        UiMotion.opacity = Math.clamp(opening.value()*page.value(),0,1);
        int accent = LevrnClient.STATE.theme().accent;
        boolean hasInspector = selected != null || settings;

        // Full-screen dark overlay
        g.fill(0,0,canvasWidth,canvasHeight,(int)(110*opening.value())<<24|0x000000);

        int reveal = (int)(panelHeight*Math.clamp(opening.value(),0,1));
        g.enableScissor(left-2,top+(panelHeight-reveal)/2-2,left+panelWidth+2,top+(panelHeight+reveal)/2+2);

        if(panelView) {g.disableScissor();drawPanels(g);drawDock(g);drawBrand(g);super.render(g,mouseX,mouseY,delta);UiMotion.opacity=1;drawDescription(g,mouseX,mouseY);return;}

        // Main panel border + background
        g.fill(left-1,top-1,left+panelWidth+1,top+panelHeight+1,UiMotion.fade(BORDER));
        g.fill(left,top,left+panelWidth,top+panelHeight,UiMotion.fade(BG_DARK));

        // ===== TOP BAR =====
        g.fill(left, top, left+panelWidth, top+topBarHeight, UiMotion.fade(0x6013151B));
        // Thin accent line at top
        g.fill(left, top, left+panelWidth, top+1, UiMotion.fade(accent));
        // Divider below top bar
        g.fill(left, top+topBarHeight-1, left+panelWidth, top+topBarHeight, UiMotion.fade(BORDER));

        // Brand
        InterfaceText.draw(g, "LVERN", left + sidebarWidth/2 - InterfaceText.width("LVERN")/2, top + 14, accent);

        // Preset buttons in top bar (center area)
        int presetX = left + sidebarWidth + 10;
        var presets = ClientState.CombatPreset.values();
        ClientState.CombatPreset current = LevrnClient.STATE.currentPreset();
        for (int pi = 0; pi < presets.length; pi++) {
            var preset = presets[pi];
            int pw = InterfaceText.width(preset.label) + 12;
            int presetY = top + 12;
            boolean isActive = current == preset;
            var presetCh = channel("preset/" + preset.name());
            presetCh.update(mouseX >= presetX && mouseX < presetX + pw && mouseY >= presetY && mouseY < presetY + 16, isActive);
            double hover = presetCh.hover.value(), on = presetCh.enabled.value();
            int presetBg = on > 0.5 ? Motion.mix(0xCC080A0E, accent, 0.15) : hover > 0.01 ? 0xCC080A0E : 0xFF141416;
            g.fill(presetX, presetY, presetX + pw, presetY + 16, UiMotion.fade(presetBg));
            InterfaceText.draw(g, preset.label, presetX + 6, presetY + 4,
                UiMotion.fade(isActive ? accent : (int)(hover > 0.01 ? 0xFFA0A0A6 : 0xFF85858D)));
            presetX += pw + 4;
        }

        // "none applied" or current preset label
        String presetLabel = current.label.toLowerCase(Locale.ROOT);
        InterfaceText.draw(g, presetLabel, presetX + 4, top + 16, UiMotion.fade(TEXT_MUTED));

        // Search bar styling (right side of top bar)
        if(!hudStudio && search!=null) {
            var searchState=channel("search");searchState.update(search.isFocused(),false);
            int searchW = Math.min(180, panelWidth - sidebarWidth - 120);
            int searchX = left + panelWidth - searchW - 16;
            g.fill(searchX-2, top+9, searchX+searchW+4, top+29, UiMotion.fade(searchState.hover.value() > 0.01 ? 0xCC080A0E : 0xFF141416));
            g.fill(searchX-2, top+9, searchX+searchW+4, top+10, UiMotion.fade(BORDER));
            g.fill(searchX-2, top+28, searchX+searchW+4, top+29, UiMotion.fade(BORDER));
            g.fill(searchX-2, top+9, searchX-1, top+29, UiMotion.fade(BORDER));
            g.fill(searchX+searchW+3, top+9, searchX+searchW+4, top+29, UiMotion.fade(BORDER));
        }

        // ===== SIDEBAR =====
        g.fill(left, top+topBarHeight, left+sidebarWidth, top+panelHeight, UiMotion.fade(BG_SIDEBAR));
        // Sidebar right border
        g.fill(left+sidebarWidth-1, top+topBarHeight, left+sidebarWidth, top+panelHeight, UiMotion.fade(BORDER));

        // Category indicator (active marker)
        if (!settings && selected == null && !hudStudio) {
            double targetIndex = category == null ? 0 : category.ordinal()+1;
            if (still) indicator.snap(targetIndex); else indicator.step(targetIndex,dt,18);
            int iy = top + topBarHeight + 8 + (int)Math.round(indicator.value()*22);
            g.fill(left, iy, left+3, iy+18, UiMotion.fade(accent));
        }

        // Category module counts in sidebar
        if (!settings && selected == null && !hudStudio) {
            int cy = top + topBarHeight + 8;
            long overviewCount = LevrnClient.STATE.features().stream().filter(Feature::enabled).count();
            String overviewStr = String.valueOf(overviewCount);
            InterfaceText.draw(g, overviewStr, left + sidebarWidth - 14 - InterfaceText.width(overviewStr), cy + 4, UiMotion.fade(overviewCount > 0 ? TEXT_MUTED : 0xFF303034));
            int ci = 1;
            for (Category cat : Category.values()) {
                long count = LevrnClient.STATE.features().stream().filter(f -> f.category == cat && f.enabled()).count();
                String countStr = String.valueOf(count);
                InterfaceText.draw(g, countStr, left + sidebarWidth - 14 - InterfaceText.width(countStr), cy + ci * 22 + 4, UiMotion.fade(count > 0 ? TEXT_MUTED : 0xFF303034));
                ci++;
            }
        }

        // ===== CENTER COLUMN: Module List =====
        // Center panel background
        g.fill(centerX, top+topBarHeight, centerX+centerWidth, top+panelHeight, UiMotion.fade(BG_PANEL));
        // Right border of center panel (only when inspector visible)
        if (hasInspector) {
            g.fill(centerX+centerWidth, top+topBarHeight, centerX+centerWidth+1, top+panelHeight, UiMotion.fade(BORDER));
        }

        if (hudStudio) {
            InterfaceText.draw(g, "HUD STUDIO", centerX + 8, centerY + 4, accent);
            InterfaceText.draw(g, "Toggle HUD elements", centerX + InterfaceText.width("HUD STUDIO") + 12 + 8, centerY + 4, TEXT_MUTED);
            g.fill(centerX + 4, centerY + 18, centerX + centerWidth - 4, centerY + 19, UiMotion.fade(BORDER));
            g.enableScissor(centerX, centerY + 20, centerX + centerWidth, centerY + centerViewportHeight);
            int cols = centerWidth >= 360 ? 3 : centerWidth >= 240 ? 2 : 1;
            int cellWidth = (centerWidth - (cols - 1) * 6 - 12) / cols;
            for (int idx = 0; idx < HUD_ELEMENTS.length; idx++) {
                String id = HUD_ELEMENTS[idx][0];
                Feature feature = LevrnClient.STATE.feature(id);
                int col = idx % cols, row = idx / cols;
                int x = centerX + 6 + col * (cellWidth + 6);
                int y = centerY + 24 + row * 42 - scroll;
                if (y + 36 < centerY + 20 || y > centerY + centerViewportHeight) continue;
                int cardBg = feature.enabled() ? Motion.mix(BG_ROW, accent, 0.08) : BG_ROW;
                g.fill(x, y - 4, x + cellWidth, y + 32, UiMotion.fade(cardBg));
                // Left accent bar
                g.fill(x, y - 4, x + 2, y + 32, UiMotion.fade(feature.enabled() ? accent : 0xFF222226));
            }
            g.disableScissor();
        } else {
            // Module list as flat rows with severity tags
            g.enableScissor(centerX, centerY, centerX + centerWidth, centerY + centerViewportHeight);
            for (var section : layout.sections()) {
                int x = centerX;
                int y = centerY + section.top() - scroll;
                // Section header - flat bar
                g.fill(x, y, x + centerWidth, y + 18, UiMotion.fade(0xFF111114));
                // Category label
                String sectionLabel = section.category().label.toUpperCase(Locale.ROOT);
                InterfaceText.draw(g, sectionLabel, x + 8, y + 5, accent);
                // Module count
                String countLabel = String.valueOf(section.features().size());
                InterfaceText.draw(g, countLabel, x + centerWidth - InterfaceText.width(countLabel) - 8, y + 5, TEXT_MUTED);

                // Module rows with severity tags
                int modRow = 0;
                for (Feature feature : section.features()) {
                    int my = centerY + section.top() + 22 + modRow * 18 - scroll;
                    modRow++;
                    if (my < centerY || my + 16 > centerY + centerViewportHeight) continue;

                    // Row background - highlight if this module is selected in inspector
                    boolean isSelected = feature == selected;
                    int rowBg = isSelected ? BG_ROW_ACTIVE : BG_ROW;
                    g.fill(x + 2, my - 1, x + centerWidth - 2, my + 17, UiMotion.fade(rowBg));

                    // Selected indicator
                    if (isSelected) {
                        g.fill(x + 2, my - 1, x + 4, my + 17, UiMotion.fade(accent));
                    }


                }
            }
            if (layout.sections().isEmpty()) InterfaceText.draw(g, "No matches.", centerX + 8, centerY + 15, TEXT_MUTED);
            g.disableScissor();
        }

        // Scrollbar for center
        if (maxScroll > 0) {
            int h = Math.max(12, centerViewportHeight * centerViewportHeight / (centerViewportHeight + maxScroll));
            int y = centerY + (centerViewportHeight - h) * scroll / maxScroll;
            g.fill(centerX + centerWidth - 4, centerY, centerX + centerWidth - 2, centerY + centerViewportHeight, UiMotion.fade(0xFF111114));
            g.fill(centerX + centerWidth - 4, y, centerX + centerWidth - 2, y + h, UiMotion.fade(accent));
        }

        // ===== RIGHT PANEL: Inspector =====
        if (hasInspector) {
            // Inspector background
            g.fill(inspectorX, top+topBarHeight, inspectorX+inspectorWidth, top+panelHeight, UiMotion.fade(BG_PANEL));

            int inspY = centerY;
            if (selected != null) {
                // Module name header
                InterfaceText.draw(g, selected.name.toUpperCase(Locale.ROOT), inspectorX + 8, inspY + 4, TEXT_PRIMARY);
                // Severity tag next to name
                boolean isCombat = severity(selected).equals("WARNING");
                String tag = isCombat ? "WARNING" : "STANDARD";
                int tagBg = isCombat ? TAG_WARNING : TAG_STANDARD;
                int tagTxt = isCombat ? TAG_TEXT_WARN : TAG_TEXT_STD;
                int tagX = inspectorX + InterfaceText.width(selected.name.toUpperCase(Locale.ROOT)) + 16;
                int tagW = InterfaceText.width(tag) + 8;
                g.fill(tagX, inspY + 2, tagX + tagW, inspY + 14, UiMotion.fade(tagBg));
                InterfaceText.draw(g, tag, tagX + 4, inspY + 4, UiMotion.fade(tagTxt));
                // Description
                String desc = InterfaceText.fit(selected.description, inspectorWidth - 16);
                InterfaceText.draw(g, desc, inspectorX + 8, inspY + 18, TEXT_MUTED);
            } else if (settings) {
                InterfaceText.draw(g, "SETTINGS", inspectorX + 8, inspY + 4, TEXT_PRIMARY);
            }
            // Separator
            g.fill(inspectorX + 4, inspY + 32, inspectorX + inspectorWidth - 4, inspY + 33, UiMotion.fade(BORDER));
            // "SETTINGS" section label
            if (selected != null) {
                InterfaceText.draw(g, "SETTINGS", inspectorX + 8, inspY + 38, TEXT_MUTED);
            }

            // Inspector scrollbar
            if (inspMaxScroll > 0) {
                int inspViewH = centerViewportHeight - 56;
                int h = Math.max(12, inspViewH * inspViewH / (inspViewH + inspMaxScroll));
                int sy = centerY + 56 + (inspViewH - h) * inspScroll / inspMaxScroll;
                g.fill(inspectorX + inspectorWidth - 4, centerY + 56, inspectorX + inspectorWidth - 2, centerY + 56 + inspViewH, UiMotion.fade(0xFF111114));
                g.fill(inspectorX + inspectorWidth - 4, sy, inspectorX + inspectorWidth - 2, sy + h, UiMotion.fade(accent));
            }

            // NOVA adaptive engine inspector panel
            if (selected != null && selected.id.equals("adaptive")) {
                AdaptiveEngine engine = LevrnClient.novaEngine();
                g.enableScissor(inspectorX+4,centerY+56,inspectorX+inspectorWidth-6,centerY+centerViewportHeight);
                int novaY = inspY + 64 + controls.size() * 22 - inspScroll;
                int novaLeft = inspectorX + 8;
                int novaWidth = inspectorWidth - 16;

                // Separator
                g.fill(inspectorX + 4, novaY, inspectorX + inspectorWidth - 4, novaY + 1, UiMotion.fade(BORDER));
                novaY += 6;

                // Section label
                InterfaceText.draw(g, "NOVA ENGINE", novaLeft, novaY, UiMotion.fade(TEXT_MUTED));
                novaY += 14;

                if (engine == null) {
                    InterfaceText.draw(g, "Join a world to start NOVA", novaLeft, novaY, UiMotion.fade(TEXT_MUTED));
                } else {
                    // Status line
                    String statusLine = engine.getStatus();
                    InterfaceText.draw(g, InterfaceText.fit(statusLine, novaWidth), novaLeft, novaY, UiMotion.fade(TEXT_PRIMARY));
                    novaY += 14;

                    // Performance score bar
                    double score = Math.clamp(engine.computePerformanceScore(), 0.0, 1.0);
                    InterfaceText.draw(g, "Performance", novaLeft, novaY, UiMotion.fade(TEXT_MUTED));
                    novaY += 12;
                    int barH = 6;
                    g.fill(novaLeft, novaY, novaLeft + novaWidth, novaY + barH, UiMotion.fade(0xCC080A0E));
                    int filledW = (int)(novaWidth * score);
                    int barColor = score > 0.6 ? accent : (score > 0.3 ? 0xFFCC8833 : 0xFFCC3344);
                    if (filledW > 0) g.fill(novaLeft, novaY, novaLeft + filledW, novaY + barH, UiMotion.fade(barColor));
                    // Score label
                    String scoreLabel = String.format(Locale.ROOT, "%.0f%%", score * 100);
                    InterfaceText.draw(g, scoreLabel, novaLeft + novaWidth - InterfaceText.width(scoreLabel), novaY - 1, UiMotion.fade(TEXT_MUTED));
                    novaY += barH + 10;

                    // Confidence per setting
                    InterfaceText.draw(g, "CONFIDENCE", novaLeft, novaY, UiMotion.fade(TEXT_MUTED));
                    novaY += 12;
                    var confidenceMap = engine.getConfidenceMap();
                    for (var entry : confidenceMap.entrySet()) {
                        String name = entry.getKey();
                        double conf = Math.clamp(entry.getValue(), 0.0, 1.0);
                        // Name on left
                        String fittedName = InterfaceText.fit(name, novaWidth / 2 - 4);
                        InterfaceText.draw(g, fittedName, novaLeft, novaY + 1, UiMotion.fade(TEXT_MUTED));
                        // Bar on right
                        int confBarX = novaLeft + novaWidth / 2 + 2;
                        int confBarW = novaWidth / 2 - 2;
                        g.fill(confBarX, novaY, confBarX + confBarW, novaY + 5, UiMotion.fade(0xCC080A0E));
                        int confFilled = (int)(confBarW * conf);
                        if (confFilled > 0) {
                            int confColor = conf > 0.7 ? accent : 0xFF505056;
                            g.fill(confBarX, novaY, confBarX + confFilled, novaY + 5, UiMotion.fade(confColor));
                        }
                        novaY += 10;
                    }
                    novaY += 4;

                    // Last adjustment
                    String lastAdj = engine.getLastAdjustmentDescription();
                    if (lastAdj != null) {
                        InterfaceText.draw(g, "LAST ADJUSTMENT", novaLeft, novaY, UiMotion.fade(TEXT_MUTED));
                        novaY += 12;
                        InterfaceText.draw(g, InterfaceText.fit(lastAdj, novaWidth), novaLeft, novaY, UiMotion.fade(TEXT_PRIMARY));
                    }
                }
                g.disableScissor();
            }
        }

        // ===== FOOTER =====
        g.fill(left, top+panelHeight-20, left+panelWidth, top+panelHeight-19, UiMotion.fade(BORDER));
        long enabled = LevrnClient.STATE.features().stream().filter(Feature::enabled).count();
        String footer = enabled + "/" + LevrnClient.STATE.features().size() + " active";
        InterfaceText.draw(g, footer, left + 8, top + panelHeight - 13, TEXT_MUTED);
        // Status
        String statusText = LevrnClient.status();
        String fittedStatus = InterfaceText.fit(statusText, panelWidth / 3);
        InterfaceText.draw(g, fittedStatus, left + panelWidth - 8 - InterfaceText.width(fittedStatus), top + panelHeight - 13, TEXT_MUTED);

        g.disableScissor();drawDock(g);drawBrand(g);
        super.render(g, mouseX, mouseY, delta);
        UiMotion.opacity = 1; drawDescription(g,mouseX,mouseY);
    }

    private void drawDescription(GuiGraphics g,int mouseX,int mouseY) {
        String text=descriptions.entrySet().stream().filter(e -> e.getKey().isMouseOver(mouseX,mouseY)).map(Map.Entry::getValue).findFirst().orElse("");
        long now=System.nanoTime();
        if(!text.equals(hoveredDescription)) { hoveredDescription=text;descriptionSince=now; }
        if(text.isBlank() || !inputReady() || now-descriptionSince<350_000_000L) return;
        var lines=new ArrayList<String>();String line="";
        for(String word:text.split(" ")) {
            String next=line.isEmpty() ? word : line+" "+word;
            if(!line.isEmpty() && InterfaceText.width(next)>220) {lines.add(line);line=word;}else line=next;
        }
        if(!line.isEmpty()) lines.add(line);
        int w=lines.stream().mapToInt(InterfaceText::width).max().orElse(0)+16,h=lines.size()*12+12;
        int x=Math.clamp(mouseX+10,4,Math.max(4,canvasWidth-w-4)),y=Math.clamp(mouseY+12,4,Math.max(4,canvasHeight-h-4));
        // Clean tooltip
        g.fill(x-1,y-1,x+w+1,y+h+1,BORDER);
        g.fill(x,y,x+w,y+h,0xFA0E0E10);
        for(int i=0;i<lines.size();i++) InterfaceText.draw(g,lines.get(i),x+8,y+6+i*12,TEXT_PRIMARY);
    }
    private MouseButtonEvent canvasEvent(MouseButtonEvent e) { return new MouseButtonEvent(e.x()/canvasScale,e.y()/canvasScale,e.buttonInfo()); }
    @Override public void mouseMoved(double x,double y) { super.mouseMoved(x/canvasScale,y/canvasScale); }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubled) {
        event=canvasEvent(event);
        if (!inputReady()) return true;

        if(panelView) return super.mouseClicked(event,doubled);

        // Handle preset button clicks
        int presetX = left + sidebarWidth + 10;
        var presets = ClientState.CombatPreset.values();
        for (int pi = 0; pi < presets.length; pi++) {
            var preset = presets[pi];
            int pw = InterfaceText.width(preset.label) + 12;
            int presetY = top + 12;
            if (event.button() == 0 && event.x() >= presetX && event.x() < presetX + pw && event.y() >= presetY && event.y() < presetY + 16) {
                LevrnClient.STATE.applyCombatPreset(preset);
                LevrnClient.save();
                Notifications.add("Preset", preset.label.toUpperCase(Locale.ROOT) + " applied");
                requestRefresh();
                return true;
            }
            presetX += pw + 4;
        }

        // Panel dragging from the brand area
        if (event.button()==0 && event.x()>=left && event.x()<left+sidebarWidth && event.y()>=top && event.y()<top+topBarHeight) {
            dragging=true; dragOffsetX=event.x()-left; dragOffsetY=event.y()-top; return true;
        }
        return super.mouseClicked(event,doubled);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
        event=canvasEvent(event);dx/=canvasScale;dy/=canvasScale;
        if (dragging) {
            int available=dockVisible ? dockX-16 : canvasWidth-16;
            int nx=Math.clamp((int)(event.x()-dragOffsetX),8,Math.max(8,available-panelWidth-8));
            int ny=Math.clamp((int)(event.y()-dragOffsetY),36,Math.max(36,canvasHeight-panelHeight-8));
            LevrnClient.STATE.setWindowX(nx/(double)Math.max(1,available-panelWidth));
            LevrnClient.STATE.setWindowY(ny/(double)Math.max(1,canvasHeight-panelHeight));
            LevrnClient.scheduleSave(); requestRefresh(); return true;
        }
        return !inputReady() || super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        event=canvasEvent(event);
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
