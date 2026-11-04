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
    private long beforeVelocity;
    private CompletableFuture<Snapshot> result;
    private record Snapshot(float health,boolean diamond,boolean totem,int hotbar,int inventory,double x,double y) { }
    @Override public void onInitializeClient() {
        if(Boolean.getBoolean("levrn.localSmoke")) ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private static void require(boolean ok,String why) { if(!ok) throw new AssertionError(why); }
    private void next(int phase) { this.phase=phase;ticks=0; }
    private void commands(Minecraft mc,String... commands) {
        var server=mc.getSingleplayerServer();
        server.execute(() -> {for(String command:commands) server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),command);});
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
    private void tick(Minecraft mc) {
        ticks++;total++;
        if(total>3600) throw new AssertionError("Local smoke timeout at phase "+phase+" screen "+mc.screen);
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
                    state.disableAll();LevrnClient.runtime.restore(mc);System.out.println("LEVRN_SMOKE_SUCCESS ordinary integrated-server loop");mc.stop();next(13);
                }
            }
        }
    }
}
