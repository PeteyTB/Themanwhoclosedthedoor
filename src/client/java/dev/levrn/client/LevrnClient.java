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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class LevrnClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Levrn");
    public static final ClientState STATE = new ClientState();
    private static final ConfigStore CONFIG = new ConfigStore(
        FabricLoader.getInstance().getConfigDir().resolve("levrn/profile.properties"));
    private final ModuleRuntime runtime = new ModuleRuntime(STATE);
    private KeyMapping menu, panic, zoom;
    private static String status = "Ready";
    private static boolean initialized;

    @Override public synchronized void onInitializeClient() {
        if (initialized) return;
        initialized = true;
        load();
        menu = key("menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
        panic = key("panic", GLFW.GLFW_KEY_END);
        zoom = key("zoom", GLFW.GLFW_KEY_C);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menu.consumeClick()) {
                if (client.screen instanceof LevrnScreen) client.setScreen(null);
                else if (client.screen == null) client.setScreen(new LevrnScreen());
            }
            while (panic.consumeClick()) {
                STATE.disableAll(); runtime.restore(client); save();
            }
            while (zoom.consumeClick()) {
                if (client.screen == null) { STATE.feature("zoom").toggle(); save(); }
            }
            runtime.tick(client);
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("levrn", "overlay"), (graphics, delta) -> LevrnHud.render(graphics));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            runtime.restore(client);
            // Changes are saved at the point of interaction, never overwrite an unreadable profile on exit.
        });
        LOG.info("Levrn 0.1.0-alpha initialized for Minecraft 1.21.11");
    }

    private KeyMapping key(String id, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.levrn." + id,
            InputConstants.Type.KEYSYM, code, KeyMapping.Category.MISC));
    }

    public static String status() { return status; }
    public static void save() {
        try { CONFIG.save(STATE); status = "Profile saved"; }
        catch (IOException e) { status = "Save failed: see log"; LOG.error("Could not save Levrn profile", e); }
    }
    public static void load() {
        try { CONFIG.load(STATE); status = "Profile loaded"; }
        catch (IOException | IllegalArgumentException e) {
            status = "Profile unreadable: see log"; LOG.error("Could not load Levrn profile", e);
        }
    }
    public static void panic() {
        STATE.disableAll(); save();
    }
}
