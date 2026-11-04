package dev.levrn.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/** Exercises real client interactions against an isolated integrated server, never a public server. */
public final class LocalServerIntegrationTest implements FabricClientGameTest {
    private static final AABB AREA = new AABB(-5,0,-5,5,12,8);
    private static void require(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private static float health(TestServerContext server) {
        return server.computeOnServer(s -> s.overworld().getEntitiesOfClass(IronGolem.class,AREA).getFirst().getHealth());
    }
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("fill -3 3 -3 3 3 5 minecraft:stone");
            server.runCommand("tp @a 0 4 0 0 0");
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword");
            server.runCommand("summon minecraft:iron_golem 0 4 2.3 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
            context.waitTicks(15);
            context.runOnClient(mc -> {
                LevrnClient.STATE.reset(); LevrnClient.STATE.disableAll();
                LevrnClient.STATE.setTargetPlayers(false); LevrnClient.STATE.setTargetMobs(false);
                LevrnClient.STATE.setTargetFov(360); LevrnClient.STATE.setAimSpeed(90);
                LevrnClient.STATE.feature("aura").setEnabled(true);
                mc.setWindowActive(true); mc.setScreen(null);
            });
            float untouched = health(server);
            context.waitTicks(20);
            require(health(server)==untouched,"Filtered target took damage");
            System.out.println("LEVRN_LOCAL_PASS filters prevent attacks");
            context.runOnClient(mc -> LevrnClient.STATE.setTargetMobs(true));
            context.waitFor(mc -> LevrnClient.runtime.sentAttacks > 0,100);
            context.waitTicks(5);
            require(health(server)<untouched,"Aura sent attacks but server health did not decrease");
            System.out.println("LEVRN_LOCAL_PASS aura server health "+untouched+" -> "+health(server));

            context.runOnClient(mc -> {
                LevrnClient.STATE.disableAll(); mc.player.setYRot(0);mc.player.setXRot(0);
                LevrnClient.STATE.feature("trigger").setEnabled(true);
            });
            float beforeTrigger=health(server);
            context.waitTicks(35);
            require(health(server)<beforeTrigger,"Trigger did not damage the crosshair target on the server");
            System.out.println("LEVRN_LOCAL_PASS trigger server health "+beforeTrigger+" -> "+health(server));

            context.runOnClient(mc -> LevrnClient.STATE.disableAll());
            server.runCommand("item replace entity @a armor.head with minecraft:leather_helmet");
            server.runCommand("item replace entity @a inventory.0 with minecraft:diamond_helmet");
            context.waitTicks(3);
            context.runOnClient(mc -> LevrnClient.STATE.feature("auto_armor").setEnabled(true));
            context.waitFor(mc -> mc.player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),80);
            context.waitTicks(3);
            require(server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)),"Server rejected upgraded armor");
            require(context.computeOnClient(mc -> mc.player.inventoryMenu.getCarried().isEmpty()),"Armor swap left an item on the cursor");
            System.out.println("LEVRN_LOCAL_PASS server accepted armor upgrade; cursor empty");

            context.runOnClient(mc -> LevrnClient.STATE.disableAll());
            server.runCommand("item replace entity @a weapon.offhand with minecraft:air");
            server.runCommand("item replace entity @a inventory.1 with minecraft:totem_of_undying");
            server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().setHealth(6));
            context.waitTicks(3);
            context.runOnClient(mc -> LevrnClient.STATE.feature("auto_totem").setEnabled(true));
            context.waitFor(mc -> mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING),80);
            context.waitTicks(3);
            require(server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getOffhandItem().is(Items.TOTEM_OF_UNDYING)),"Server rejected totem swap");
            System.out.println("LEVRN_LOCAL_PASS server accepted totem swap");

            context.runOnClient(mc -> LevrnClient.STATE.disableAll());
            server.runCommand("item replace entity @a hotbar.1 with minecraft:cobblestone 3");
            server.runCommand("item replace entity @a inventory.1 with minecraft:cobblestone 64");
            context.waitTicks(3);
            context.runOnClient(mc -> LevrnClient.STATE.feature("auto_replenish").setEnabled(true));
            context.waitFor(mc -> mc.player.getInventory().getItem(1).getCount()==64,80);
            context.waitTicks(3);
            require(server.computeOnServer(s -> {
                var inventory=s.getPlayerList().getPlayers().getFirst().getInventory();
                return inventory.getItem(1).getCount()==64 && inventory.getItem(10).getCount()==3;
            }),"Replenish item counts were not conserved on server");
            require(context.computeOnClient(mc -> mc.player.inventoryMenu.getCarried().isEmpty()),"Replenish left cursor occupied");
            context.runOnClient(mc -> LevrnClient.STATE.disableAll());
            System.out.println("LEVRN_LOCAL_PASS server replenish conserved 67 items; cursor empty");
        }
    }
}
