package dev.levrn.attach;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.jar.JarFile;

/** Small JDK-only agent. Game code is loaded by Fabric, never by the agent's system loader. */
public final class LevrnAgent {
    private static boolean attached;
    private static boolean pending;
    private LevrnAgent() { }

    public static synchronized void agentmain(String options, Instrumentation instrumentation) {
        Path result = null;
        try {
            Path request = Path.of(new String(Base64.getUrlDecoder().decode(options), StandardCharsets.UTF_8)).toRealPath();
            Properties data = new Properties();
            try (InputStream in = Files.newInputStream(request)) { data.load(in); }
            result = Path.of(data.getProperty("result")).toAbsolutePath().normalize();
            if (!result.getParent().equals(request.getParent())) throw new IllegalArgumentException("Invalid acknowledgement path");
            if (attached) { reply(result, "OK", "Levrn is already attached. Right Shift opens the menu."); return; }
            if (pending) { reply(result, "ERROR", "A previous Levrn attachment is still waiting for the game thread."); return; }
            Path payload = Path.of(data.getProperty("payload")).toRealPath();
            boolean startupHooks;
            try (JarFile jar = new JarFile(payload.toFile())) {
                startupHooks = jar.getJarEntry("levrn.client.mixins.json") != null;
                if (jar.getJarEntry("fabric.mod.json") == null || jar.getJarEntry("dev/levrn/client/LevrnClient.class") == null)
                    throw new IllegalArgumentException("Not a packaged Levrn client JAR");
            }
            Class<?> launcherBase = null;
            for (Class<?> type : instrumentation.getAllLoadedClasses()) {
                if (type.getName().equals("net.fabricmc.loader.impl.launch.FabricLauncherBase")) { launcherBase = type; break; }
            }
            if (launcherBase == null) throw new IllegalStateException("This instance has no Fabric Loader. Start Minecraft 1.21.11 with Fabric and Fabric API first.");
            Object launcher = launcherBase.getMethod("getLauncher").invoke(null);
            ClassLoader parent = launcherBase.getClassLoader();
            Class<?> launcherApi = Class.forName("net.fabricmc.loader.impl.launch.FabricLauncher", true, parent);
            ClassLoader gameLoader = (ClassLoader) launcherApi.getMethod("getTargetClassLoader").invoke(launcher);
            Class<?> loaderApi = Class.forName("net.fabricmc.loader.api.FabricLoader", true, gameLoader);
            Object loader = loaderApi.getMethod("getInstance").invoke(null);
            requireMod(loaderApi, loader, "fabric-api");
            var minecraft = requireMod(loaderApi, loader, "minecraft");
            Class<?> modContainer = Class.forName("net.fabricmc.loader.api.ModContainer", true, gameLoader);
            Object metadata = modContainer.getMethod("getMetadata").invoke(minecraft);
            Class<?> metadataApi = Class.forName("net.fabricmc.loader.api.metadata.ModMetadata", true, gameLoader);
            Object version = metadataApi.getMethod("getVersion").invoke(metadata);
            Class<?> versionApi = Class.forName("net.fabricmc.loader.api.Version", true, gameLoader);
            if (!versionApi.getMethod("getFriendlyString").invoke(version).equals("1.21.11"))
                throw new IllegalStateException("Only Minecraft 1.21.11 is supported. Nothing was initialized.");

            if (startupHooks) throw new IllegalStateException("This Levrn build requires startup hooks. Run the installer, close Minecraft, then launch the Levrn Fabric profile. Live attachment cannot install these hooks.");

            Object resolver = loaderApi.getMethod("getMappingResolver").invoke(loader);
            Class<?> resolverApi = Class.forName("net.fabricmc.loader.api.MappingResolver", true, gameLoader);
            String namespace = (String) resolverApi.getMethod("getCurrentRuntimeNamespace").invoke(resolver);
            if (!namespace.equals("intermediary"))
                throw new IllegalStateException("Use a production Fabric instance; this attachment JAR targets the intermediary namespace.");
            String mcName = (String) resolverApi.getMethod("mapClassName", String.class, String.class)
                .invoke(resolver, "intermediary", "net.minecraft.class_310");
            String getInstance = (String) resolverApi.getMethod("mapMethodName", String.class, String.class, String.class, String.class)
                .invoke(resolver, "intermediary", "net.minecraft.class_310", "method_1551", "()Lnet/minecraft/class_310;");
            Class<?> mcClass = Class.forName(mcName, false, gameLoader);
            Object game = mcClass.getMethod(getInstance).invoke(null);
            if (!(game instanceof Executor executor)) throw new IllegalStateException("Minecraft's client executor is unavailable");

            launcherApi.getMethod("addToClassPath", Path.class, String[].class).invoke(launcher, payload, new String[]{"dev.levrn."});
            Path acknowledgement = result;
            pending = true;
            executor.execute(() -> initialize(gameLoader, acknowledgement));
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError error) throw error;
            if (failure instanceof ThreadDeath death) throw death;
            pending = false;
            if (result != null) reply(result, "ERROR", describe(failure));
            else System.err.println("[Levrn attach] " + describe(failure));
        }
    }

    private static Object requireMod(Class<?> api, Object loader, String id) throws Exception {
        Object container = api.getMethod("getModContainer", String.class).invoke(loader, id);
        if (!(container instanceof java.util.Optional<?> value) || value.isEmpty())
            throw new IllegalStateException("Required mod missing: " + id + ". Install it and restart Minecraft first.");
        return value.get();
    }
    private static synchronized void initialize(ClassLoader loader, Path result) {
        try {
            Class<?> client = Class.forName("dev.levrn.client.LevrnClient", true, loader);
            client.getMethod("onInitializeClient").invoke(client.getConstructor().newInstance());
            attached = true;
            reply(result, "OK", "Levrn initialized on the Minecraft client thread. Right Shift opens the menu; End disables all modules.");
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError error) throw error;
            if (failure instanceof ThreadDeath death) throw death;
            reply(result, "ERROR", describe(failure));
        } finally { pending = false; }
    }
    private static String describe(Throwable failure) {
        while (failure instanceof InvocationTargetException && failure.getCause() != null) failure = failure.getCause();
        return failure.getClass().getSimpleName() + ": " + failure.getMessage();
    }
    private static void reply(Path path, String status, String message) {
        try {
            // The requester may already have timed out. Do not recreate a removed request directory.
            if (!Files.isDirectory(path.getParent())) return;
            Path temp = Files.createTempFile(path.getParent(), "reply-", ".tmp");
            try {
                Properties response = new Properties(); response.setProperty("status", status); response.setProperty("message", message);
                try (OutputStream out = Files.newOutputStream(temp)) { response.store(out, "Levrn result"); }
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
        } catch (Exception e) { System.err.println("[Levrn attach] Cannot write acknowledgement: " + e.getMessage()); }
    }
}
