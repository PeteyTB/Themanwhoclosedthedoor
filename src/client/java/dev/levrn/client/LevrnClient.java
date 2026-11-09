package dev.levrn.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.levrn.core.ClientState;
import dev.levrn.core.ConfigStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class LevrnClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("LVERN");
    public static final ClientState STATE = new ClientState();
    private static final ConfigStore CONFIG = new ConfigStore(
        FabricLoader.getInstance().getConfigDir().resolve("levrn/profile.properties"));
    static final ModuleRuntime runtime = new ModuleRuntime(STATE);
    private static AdaptiveStore novaStore;
    private static AdaptiveEngine novaEngine;
    private KeyMapping menu, panic, zoom;
    private static String status = "Ready";
    private static boolean initialized;
    private int uiSmokeTicks;
    private boolean uiSmokeOpened;
    private static int saveTicks;
    private static final java.util.Map<String,KeyMapping> MODULE_KEYS = new java.util.LinkedHashMap<>();

    @Override public synchronized void onInitializeClient() {
        if (initialized) return;
        initialized = true;
        load();
        novaStore = new AdaptiveStore(FabricLoader.getInstance().getConfigDir().resolve("levrn"));
        novaStore.loadGlobalSettings();
        menu = key("menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
        panic = key("panic", GLFW.GLFW_KEY_END);
        zoom = key("zoom", GLFW.GLFW_KEY_C);
        for (var feature : STATE.features()) if (!feature.id.equals("zoom")) MODULE_KEYS.put(feature.id,key("module."+feature.id,GLFW.GLFW_KEY_UNKNOWN));
        MODULE_KEYS.put("zoom",zoom);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof net.minecraft.client.gui.screens.TitleScreen || screen instanceof LevrnScreen) {
                ScreenKeyboardEvents.afterKeyPress(screen).register((active, input) -> {
                    if (menu.matches(input)) {
                        if (active instanceof LevrnScreen) active.onClose();
                        else client.setScreen(new LevrnScreen());
                    } else if (panic.matches(input) && active instanceof LevrnScreen workspace) {
                        panic(); workspace.requestRefresh();
                    }
                });
            }
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler,sender,client) -> {
            NetworkDiagnostics.joined();
            if (novaStore != null) {
                String serverIp = client.getCurrentServer()==null ? null : client.getCurrentServer().ip;
                ServerProfile profile = novaStore.getOrCreateProfile(serverIp);
                novaEngine = new AdaptiveEngine(profile.signals(), profile.weightMatrix());
                novaEngine.restoreState(profile.momentum(), profile.confidence());
                novaEngine.setLearningRateMultiplier(novaStore.learningRateMultiplier());
                novaEngine.setEnabled(STATE.adaptiveEnabled());
                runtime.nova = novaEngine;
                LOG.info("NOVA: Engine initialized for server {}", ServerProfile.hashServerIp(serverIp));
            }
        });
        ClientTickEvents.START_CLIENT_TICK.register(runtime::beginTick);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> {
            runtime.disconnected();
            if(novaEngine!=null) novaEngine.setEnabled(false);
            if (novaStore != null) {
                novaStore.capture(novaEngine);
                novaStore.save();
                novaStore.clearActive();
            }
            if (novaEngine != null) {
                novaEngine.setEnabled(false);
                runtime.nova = null;
                novaEngine = null;
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (saveTicks > 0 && --saveTicks == 0) save();
            if (Boolean.getBoolean("levrn.uiSmoke") && !uiSmokeOpened && ++uiSmokeTicks >= 100
                && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                uiSmokeOpened = true; client.setScreen(new LevrnScreen());
            }
            while (menu.consumeClick()) {
                if (client.screen instanceof LevrnScreen workspace) workspace.onClose();
                else if (client.screen == null || client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) client.setScreen(new LevrnScreen());
            }
            while (panic.consumeClick()) {
                panic();
            }
            while (zoom.consumeClick()) {
                if (client.screen == null) toggle(STATE.feature("zoom"));
            }
            MODULE_KEYS.forEach((id,binding) -> { while (binding.consumeClick()) if (client.screen == null) toggle(STATE.feature(id)); });
            if(novaEngine!=null) novaEngine.setEnabled(STATE.adaptiveEnabled());
            if(novaStore!=null) novaStore.setNovaEnabled(STATE.adaptiveEnabled());
            try { runtime.tick(client); } finally { runtime.restoreCamera(); }
            if (novaStore != null) novaStore.tick(novaEngine);
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("levrn", "overlay"), (graphics, delta) -> LevrnHud.render(graphics));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            runtime.restore(client);
            if(novaEngine!=null) novaEngine.setEnabled(false);
            if (novaStore != null) {novaStore.capture(novaEngine);novaStore.saveAll();}
            flushSave();
            // Changes are saved at the point of interaction, never overwrite an unreadable profile on exit.
        });
        LOG.info("LVERN 0.5.0-alpha initialized for Minecraft 1.21.11");
    }

    private KeyMapping key(String id, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.levrn." + id,
            InputConstants.Type.KEYSYM, code, KeyMapping.Category.MISC));
    }

    public static void packetSent(net.minecraft.network.protocol.Packet<?> packet) {NetworkDiagnostics.sent(packet);}
    public static void disconnectReason(String reason) {NetworkDiagnostics.disconnected(reason);}
    public static void stopAutomation(String reason) {
        NetworkDiagnostics.stopped(reason);
        STATE.features().stream().filter(f -> f.category!=dev.levrn.core.Category.VISUAL).forEach(f -> f.setEnabled(false));
        runtime.restore(Minecraft.getInstance());save();Notifications.add("AUTOMATION STOPPED",reason);LOG.warn("Stopped gameplay modules: {}",reason);
    }
    public static void beforeMovement() { runtime.beforeMovement(Minecraft.getInstance()); }
    public static void afterMovement() { runtime.afterMovement(Minecraft.getInstance()); }
    public static void corrected() { runtime.corrected(); }
    public static boolean gameplayActive() { var mc=Minecraft.getInstance();return mc.player!=null && mc.screen==null && mc.isWindowActive() && mc.player.isAlive(); }
    public static long scaledVelocityUpdates;
    public static String status() { return status; }
    public static String combatStatus() { return runtime.combatStatus; }
    public static void save() {
        saveTicks = 0;
        try { CONFIG.save(STATE); status = "Profile saved"; }
        catch (IOException e) { status = "Save failed: see log"; LOG.error("Could not save LVERN profile", e); }
    }
    public static void load() {
        try { CONFIG.load(STATE); status = "Profile loaded"; }
        catch (IOException | IllegalArgumentException e) {
            status = "Profile unreadable: see log"; LOG.error("Could not load LVERN profile", e);
        }
    }
    public static void panic() {
        STATE.disableAll();runtime.restore(Minecraft.getInstance());save();
        Notifications.add("ALL SYSTEMS DISABLED","Every LVERN module has been stopped");
    }
    public static void toggle(dev.levrn.core.Feature feature) {
        feature.toggle(); save(); Notifications.add(feature.name,feature.enabled() ? "ONLINE" : "OFFLINE");
    }
    public static void scheduleSave() { saveTicks = 8; status = "Saving changes..."; }
    public static void totemPopped() { runtime.totemPopped(); }
    public static AdaptiveEngine novaEngine() { return novaEngine; }
    public static void flushSave() { if (saveTicks > 0) save(); }
    public static String keyLabel(String id) {
        KeyMapping mapping = MODULE_KEYS.get(id);
        return mapping == null ? "Unassigned" : mapping.getTranslatedKeyMessage().getString();
    }
}
