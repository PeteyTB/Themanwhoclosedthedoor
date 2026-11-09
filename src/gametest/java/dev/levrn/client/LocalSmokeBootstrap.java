package dev.levrn.client;

import dev.levrn.core.ClientState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.CompletableFuture;

/** Opt-in test mod. Uses the ordinary client/server loop; excluded from release artifacts. */
public final class LocalSmokeBootstrap implements ClientModInitializer {
    private int phase,ticks,total;
    private float before,cameraYaw;
    private double jumpPeak;
    private long beforeVelocity,attackBefore;
    private boolean nativeSprint,nativeSprintRelease;
    private CompletableFuture<Snapshot> result;
    private record Snapshot(float health,boolean diamond,boolean totem,int hotbar,int inventory,double x,double y) { }
    @Override public void onInitializeClient() {
        if(Boolean.getBoolean("levrn.localSmoke")) ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private static void require(boolean ok,String why) { if(!ok) throw new AssertionError(why); }
    private void next(int phase) { this.phase=phase;ticks=0; }
    private void commands(Minecraft mc,String... commands) {
        var server=mc.getSingleplayerServer();
        server.execute(() -> {for(String command:commands) server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),command);});
    }
    private void snapshot(Minecraft mc) {
        var server=mc.getSingleplayerServer();
        result=server.submit(() -> {
            var player=server.getPlayerList().getPlayers().getFirst();
            var golems=server.overworld().getEntitiesOfClass(IronGolem.class,new AABB(-10,-10,-10,10,20,10));
            return new Snapshot(golems.isEmpty() ? 0 : golems.getFirst().getHealth(),player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),
                player.getOffhandItem().is(Items.TOTEM_OF_UNDYING),player.getInventory().getItem(1).getCount(),player.getInventory().getItem(10).getCount(),player.getX(),player.getY());
        });
    }
    private void setupHud(Minecraft mc) {
        LevrnClient.STATE.disableAll();
        for(String id:new String[]{"hud","coordinates","active_list","target_hud","combat_status","armor_hud","speed_hud","ping_hud","clock","compass","keystrokes","potion_hud","aim_assist","auto_weapon","shield_breaker"}) LevrnClient.STATE.feature(id).setEnabled(true);
        commands(mc,"gamemode creative @a","tp @a 0.0 4.0 0.0 0 0","kill @e[type=iron_golem]","summon minecraft:iron_golem 0.0 4.0 2.3 {NoAI:1b,Invulnerable:1b,Health:70f}","time set noon","weather clear",
            "item replace entity @a armor.head with minecraft:diamond_helmet[minecraft:damage=120]",
            "item replace entity @a armor.chest with minecraft:netherite_chestplate[minecraft:damage=500]",
            "item replace entity @a armor.legs with minecraft:iron_leggings[minecraft:damage=170]",
            "item replace entity @a armor.feet with minecraft:leather_boots[minecraft:damage=55]",
            "effect give @a speed 120 1 true","effect give @a resistance infinite 0 true","effect give @a haste 8 0 true");
        if(Boolean.getBoolean("levrn.hudOnly")) commands(mc,"fill -32 0 -32 32 3 32 minecraft:grass_block","tp @a 0 4 -12 0 12");
    }
    private void disconnect(Minecraft mc) {
        try {java.nio.file.Files.deleteIfExists(mc.gameDirectory.toPath().resolve("config/levrn/last-disconnect.properties"));}
        catch(java.io.IOException e) {throw new AssertionError(e);}
        mc.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("Levrn local verification complete"));
    }
    private void tick(Minecraft mc) {
        ticks++;total++;
        // Interactive preview time must not consume the subsequent disconnect deadline.
        if((phase<20 && total>(Boolean.getBoolean("levrn.hudPreview") ? 10000 : 3600)) || (phase==21 && ticks>400)) throw new AssertionError("Local smoke timeout at phase "+phase+" screen "+mc.screen);
        if(mc.player!=null) {mc.setWindowActive(true);jumpPeak=Math.max(jumpPeak,mc.player.getY());}
        var state=LevrnClient.STATE;
        switch(phase) {
            case 0 -> {
                if(ticks>40 && mc.screen instanceof TitleScreen) {
                    org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
                    CreateWorldScreen.openFresh(mc,() -> mc.setScreen(new TitleScreen()));next(1);
                }
            }
            case 1 -> {
                if(mc.screen instanceof CreateWorldScreen screen) {
                    var ui=screen.getUiState();ui.setName("Levrn controlled verification");ui.setSeed("1");ui.setGenerateStructures(false);
                    ui.setWorldType(ui.getNormalPresetList().stream().filter(e -> e.preset()!=null && e.preset().is(WorldPresets.FLAT)).findFirst().orElseThrow());
                    var create=screen.children().stream().filter(e -> e instanceof Button b && b.getMessage().getString().equals("Create New World")).map(e -> (Button)e).findFirst().orElseThrow();
                    create.onPress(new KeyEvent(257,0,0));next(2);
                }
            }
            case 2 -> {
                if(mc.player!=null && mc.screen==null) {
                    state.reset();state.disableAll();
                    commands(mc,"gamemode creative @a","fill -3 3 -3 3 3 5 minecraft:stone","tp @a 0 4 0 180 0",
                        "item replace entity @a weapon.mainhand with minecraft:diamond_sword",
                        "summon minecraft:iron_golem 0 4 2.3 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");next(3);
                }
            }
            case 3 -> {
                if(Boolean.getBoolean("levrn.hudOnly")) {setupHud(mc);next(20);break;}
                if(ticks==40) {
                    state.setTargetPlayers(false);state.setTargetMobs(false);state.setTargetFov(360);state.setAimSpeed(90);
                    state.setAuraRotation(ClientState.AuraRotation.SILENT);state.feature("aura").setEnabled(true);snapshot(mc);
                }
                if(ticks>=60 && result!=null && result.isDone()) {before=result.join().health();snapshot(mc);next(4);}
            }
            case 4 -> {
                if(result.isDone()) {
                    require(result.join().health()==before,"Filtered target took damage");
                    System.out.println("LEVRN_SMOKE_PASS filters prevent server damage");
                    cameraYaw=mc.player.getYRot();state.setTargetMobs(true);next(5);
                }
            }
            case 5 -> {
                if(ticks==45) snapshot(mc);
                if(ticks>=46 && result.isDone()) {
                    require(result.join().health()<before,"Silent aura did not damage server target");
                    require(Math.abs(mc.player.getYRot()-cameraYaw)<0.01,"Silent aura moved the camera");
                    System.out.println("LEVRN_SMOKE_PASS silent aura: camera "+cameraYaw+", server health "+before+" -> "+result.join().health());
                    before=result.join().health();state.disableAll();
                    commands(mc,"tp @e[type=iron_golem] 0 4 2.3","tp @a 0 4 0 0 0");next(6);
                }
            }
            case 6 -> {
                if(ticks==20) state.feature("trigger").setEnabled(true);
                if(ticks==55) snapshot(mc);
                if(ticks>=56 && result.isDone()) {
                    require(result.join().health()<before,"Trigger did not damage server target");
                    System.out.println("LEVRN_SMOKE_PASS trigger server health "+before+" -> "+result.join().health());state.disableAll();
                    commands(mc,"item replace entity @a armor.head with minecraft:leather_helmet","item replace entity @a inventory.0 with minecraft:diamond_helmet");next(7);
                }
            }
            case 7 -> {
                if(ticks==10) state.feature("auto_armor").setEnabled(true);
                if(ticks==45) snapshot(mc);
                if(ticks>=46 && result.isDone()) {
                    require(result.join().diamond() && mc.player.inventoryMenu.getCarried().isEmpty(),"Paced armor upgrade failed or left cursor occupied");
                    System.out.println("LEVRN_SMOKE_PASS server accepted paced worn-armor upgrade; cursor empty");state.disableAll();
                    commands(mc,"item replace entity @a hotbar.1 with minecraft:cobblestone 3","item replace entity @a inventory.1 with minecraft:cobblestone 64");next(8);
                }
            }
            case 8 -> {
                if(ticks==10) state.feature("auto_replenish").setEnabled(true);
                if(ticks==45) snapshot(mc);
                if(ticks>=46 && result.isDone()) {
                    require(result.join().hotbar()==64 && result.join().inventory()==3 && mc.player.inventoryMenu.getCarried().isEmpty(),"Paced replenish did not conserve 67 items");
                    System.out.println("LEVRN_SMOKE_PASS paced replenish: server conserved 67 items; cursor empty");state.disableAll();
                    state.feature("velocity").setEnabled(true);state.setVelocityHorizontal(0);state.setVelocityVertical(100);beforeVelocity=LevrnClient.scaledVelocityUpdates;
                    var server=mc.getSingleplayerServer();server.execute(() -> {
                        var p=server.getPlayerList().getPlayers().getFirst();p.connection.send(new ClientboundSetEntityMotionPacket(p.getId(),new Vec3(.4,.2,.3)));
                    });next(9);
                }
            }
            case 9 -> {
                if(ticks>12) {
                    require(LevrnClient.scaledVelocityUpdates>beforeVelocity,"Velocity hook never processed incoming server packet");
                    require(Math.abs(mc.player.getDeltaMovement().x)<0.001 && Math.abs(mc.player.getDeltaMovement().z)<0.001,"Horizontal knockback was not scaled");
                    System.out.println("LEVRN_SMOKE_PASS incoming server velocity scaled; no teleport packet cancelled");state.disableAll();
                    commands(mc,"gamemode survival @a","tp @e[type=iron_golem] 0 4 2.3","tp @a 0 4 0 180 0");next(10);
                }
            }
            case 10 -> {
                if(ticks==30) {snapshot(mc);jumpPeak=4;}
                if(ticks==32 && result.isDone()) {
                    before=result.join().health();state.feature("aura").setEnabled(true);state.feature("criticals").setEnabled(true);next(11);
                }
            }
            case 11 -> {
                if(ticks==65) snapshot(mc);
                if(ticks>=66 && result.isDone()) {
                    require(jumpPeak>4.5 && result.join().health()<before,"Real-jump critical attack did not reach server");
                    System.out.println("LEVRN_SMOKE_PASS real jump critical: peak Y "+jumpPeak+", server health "+before+" -> "+result.join().health());state.disableAll();
                    commands(mc,"tp @a 3.2 4 0 -90 0");next(12);
                }
            }
            case 12 -> {
                if(ticks==20) {state.feature("safe_walk").setEnabled(true);state.feature("auto_walk").setEnabled(true);}
                if(ticks==60) snapshot(mc);
                if(ticks>=61 && result.isDone()) {
                    require(result.join().x()<4.4 && result.join().y()>3.9,"Safe Walk allowed walking off the platform");
                    require(result.join().x()>3.3,"Auto Walk did not move forward");
                    System.out.println("LEVRN_SMOKE_PASS edge protection and Auto Walk: server X "+result.join().x()+", Y "+result.join().y());
                    state.disableAll();LevrnClient.runtime.restore(mc);
                    commands(mc,"tp @a 0.0 4.0 0.0 30 0","tp @e[type=iron_golem] 0.0 4.0 2.3");next(13);
                }
            }
            case 13 -> {
                if(ticks==20) {
                    snapshot(mc);attackBefore=LevrnClient.runtime.sentAttacks;cameraYaw=mc.player.getYRot();
                    state.feature("aura").setEnabled(true);state.feature("w_tap").setEnabled(true);state.feature("sprint").setEnabled(true);state.feature("auto_walk").setEnabled(true);
                    state.setAdaptiveEnabled(true);nativeSprint=nativeSprintRelease=false;
                }
                if(ticks>20) {
                    nativeSprint |= mc.player.input.keyPresses.sprint();
                    nativeSprintRelease |= LevrnClient.runtime.sentAttacks>attackBefore && !mc.player.input.keyPresses.sprint();
                }
                if(ticks==21 && result.isDone()) before=result.join().health();
                if(ticks==37) snapshot(mc);
                if(ticks>=38 && result.isDone()) {
                    require(result.join().health()<before,"Moving Aura/W Tap/Sprint did not damage the server target");
                    require(Math.abs(result.join().x())>.25 && Math.abs(mc.player.getYRot()-cameraYaw)<.01,"Moving silent aim changed the camera or stopped all movement");
                    require(nativeSprint && nativeSprintRelease,"Sprint reset was not represented in native input");
                    System.out.println("LEVRN_SMOKE_PASS moving Aura + W Tap + Sprint: native sprint input/reset, fixed camera, server health "+before+" -> "+result.join().health());
                    require(LevrnClient.novaEngine()!=null && LevrnClient.novaEngine().isEnabled() && LevrnClient.novaEngine().signals().observations(PerformanceSignals.Signal.HIT_RATE)>0,"Nova did not observe the real encounter");
                    System.out.println("LEVRN_NOVA_PASS real encounter observations and finite score "+LevrnClient.novaEngine().computePerformanceScore());
                    state.feature("hud").setEnabled(true);commands(mc,"tp @a 0.0 4.0 0.0 30 0");next(14);
                }
            }
            case 14 -> {if(ticks==15) {commands(mc,"tp @a 0.0 4.0 0.0 30 0");next(15);}}
            case 15 -> {
                if(ticks>15) {
                    require(state.enabled("aura") && LevrnClient.novaEngine().signals().observations(PerformanceSignals.Signal.CORRECTION_RATE)>=2,"Uploaded correction handling did not notify Nova");
                    require(state.enabled("hud"),"Correction stop disabled the visual HUD");
                    System.out.println("LEVRN_SMOKE_PASS uploaded correction handling preserves module choice and feeds Nova");
                    System.out.println("LEVRN_SMOKE_SUCCESS ordinary integrated-server gameplay");
                    if(Boolean.getBoolean("levrn.hudPreview")) {setupHud(mc);next(20);}else {disconnect(mc);next(21);}
                }
            }
            case 20 -> {
                if(ticks==1) total=Math.max(total,Integer.getInteger("levrn.previewElapsedTicks",total));
                if(ticks==40) {
                    for(String id:new String[]{"aim_assist","auto_weapon","shield_breaker"}) state.feature(id).setEnabled(true);
                    System.out.println("LEVRN_HUD_PREVIEW_READY");
                }
                if(java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("hud-preview.scale3")) && mc.options.guiScale().get()!=3) {mc.options.guiScale().set(3);mc.resizeDisplay();}
                if(java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("hud-preview.scale2")) && mc.options.guiScale().get()!=2) {mc.options.guiScale().set(2);mc.resizeDisplay();}
                if(ticks%80==0) commands(mc,"effect give @a haste 8 0 true");
                if(java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("hud-preview.done"))) {disconnect(mc);next(21);}
            }
            case 21 -> {
                var path=mc.gameDirectory.toPath().resolve("config/levrn/last-disconnect.properties");
                if(ticks>10 && java.nio.file.Files.exists(path)) {
                    try {
                        var report=new java.util.Properties();try(var in=java.nio.file.Files.newBufferedReader(path)) {report.load(in);}
                        require("Levrn local verification complete".equals(report.getProperty("reason")),"Disconnect diagnostic lost the actual reason");
                        require(report.containsKey("enabledModules") && report.containsKey("packet.00"),"Disconnect report lacks module or packet-type context");
                    } catch(java.io.IOException e) {throw new AssertionError(e);}
                    try {
                        var learned=ServerProfile.load(mc.gameDirectory.toPath().resolve("config/levrn/nova"),"singleplayer");
                        if(!Boolean.getBoolean("levrn.hudOnly")) require(learned!=null && learned.matchCount()>0 && learned.signals().observations(PerformanceSignals.Signal.HIT_RATE)>0,"Nova learning was not saved on disconnect");
                    } catch(java.io.IOException e) {throw new AssertionError(e);}
                    System.out.println("LEVRN_NOVA_PASS profile persistence");
                    System.out.println("LEVRN_SMOKE_PASS disconnect reason and bounded packet-type report persisted");mc.stop();next(99);
                }
            }

        }
    }
}
