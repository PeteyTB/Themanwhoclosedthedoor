package dev.levrn.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Properties;

/** Bounded packet TYPE history: no chat contents, coordinates, server addresses or credentials. */
final class NetworkDiagnostics {
    private static final ArrayDeque<String> TYPES=new ArrayDeque<>();
    private static String stopReason="",stopModules="";
    static synchronized void stopped(String reason) {stopReason=reason;stopModules=LevrnClient.STATE.features().stream().filter(f -> f.enabled()).map(f -> f.id).collect(java.util.stream.Collectors.joining(","));}
    static synchronized void sent(Packet<?> packet) {
        if(TYPES.size()==64) TYPES.removeFirst();TYPES.addLast(packet.type().toString());
    }
    static synchronized void joined() {TYPES.clear();stopReason=stopModules="";}
    static void disconnected(String reason) {
        var mc=Minecraft.getInstance();
        if(!mc.isSameThread()) {mc.execute(() -> disconnected(reason));return;}
        var report=new Properties();
        report.setProperty("version","0.5.0-alpha");report.setProperty("time",Instant.now().toString());
        report.setProperty("reason",reason.substring(0,Math.min(4096,reason.length())));
        report.setProperty("enabledModules",LevrnClient.STATE.features().stream().filter(f -> f.enabled()).map(f -> f.id).collect(java.util.stream.Collectors.joining(",")));
        report.setProperty("auraRotation",LevrnClient.STATE.auraRotation().name());
        report.setProperty("sentAttacks",Long.toString(LevrnClient.runtime.sentAttacks));report.setProperty("serverCorrections",Long.toString(LevrnClient.runtime.serverCorrections));
        synchronized(NetworkDiagnostics.class) {report.setProperty("automationStopReason",stopReason);report.setProperty("enabledAtStop",stopModules);int i=0;for(String type:TYPES) report.setProperty(String.format(java.util.Locale.ROOT,"packet.%02d",i++),type);}
        var path=FabricLoader.getInstance().getConfigDir().resolve("levrn/last-disconnect.properties");
        try {
            Files.createDirectories(path.getParent());var temp=Files.createTempFile(path.getParent(),"disconnect-",".tmp");
            try {
                try(var out=Files.newBufferedWriter(temp)) {report.store(out,"LVERN disconnect diagnostic; packet types only");}
                Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);
            } finally {Files.deleteIfExists(temp);}
            LevrnClient.LOG.info("Disconnect diagnostic written to {}",path);
        } catch(java.io.IOException error) {LevrnClient.LOG.warn("Could not write disconnect diagnostic",error);}
    }
    private NetworkDiagnostics() { }
}
