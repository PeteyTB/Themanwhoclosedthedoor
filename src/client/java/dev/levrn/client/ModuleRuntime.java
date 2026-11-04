package dev.levrn.client;

import dev.levrn.core.ClientState;
import dev.levrn.core.TargetPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Client-thread modules using normal game interactions; no packet suppression or spoofed reach. */
final class ModuleRuntime {
    private final ClientState state;
    private final Map<OptionInstance<?>, Object> options = new HashMap<>();
    private boolean sprintOwned, respawnRequested;
    private int previousSlot = -1, autoSelectedSlot = -1, inventoryDelay;
    private LocalPlayer owner;
    LivingEntity currentTarget;
    String combatStatus = "Combat idle";
    long sentAttacks;

    ModuleRuntime(ClientState state) { this.state = state; }

    void tick(Minecraft mc) {
        if (owner != mc.player) { restore(mc); owner = mc.player; respawnRequested = false; }
        if (mc.player == null || mc.level == null) { restore(mc); combatStatus = "Join a world to use combat"; return; }
        option(mc.options.gamma(), state.enabled("brightness"), 1.0);
        option(mc.options.fov(), state.enabled("zoom"), state.zoomFov());
        option(mc.options.autoJump(), state.enabled("auto_jump"), true);
        option(mc.options.bobView(), state.enabled("no_bob"), false);
        option(mc.options.fovEffectScale(), state.enabled("no_fov"), 0.0);
        option(mc.options.damageTiltStrength(), state.enabled("no_hurt_cam"), 0.0);
        option(mc.options.screenEffectScale(), state.enabled("no_nausea"), 0.0);
        option(mc.options.darknessEffectScale(), state.enabled("no_darkness"), 0.0);
        if (mc.player.isAlive()) respawnRequested = false;
        if (state.enabled("auto_respawn") && !mc.player.isAlive() && mc.player.deathTime >= 20 && !respawnRequested) {
            mc.player.respawn(); respawnRequested = true;
        }
        if (mc.screen != null || !mc.isWindowActive() || !mc.player.isAlive()) {
            restoreSprint(mc); restoreTool(); currentTarget = null;
            combatStatus = mc.screen != null ? "Paused: menu open" : "Paused: inactive or dead";
            return;
        }
        boolean shouldSprint = state.enabled("sprint") && mc.options.keyUp.isDown()
            && !mc.player.isCrouching() && !mc.player.isUsingItem()
            && (mc.player.getFoodData().getFoodLevel() > 6 || mc.player.getAbilities().mayfly);
        if (shouldSprint) {
            if (!mc.player.isSprinting()) { mc.player.setSprinting(true); sprintOwned = true; }
        } else restoreSprint(mc);
        if (inventoryDelay > 0) inventoryDelay--;
        inventory(mc);

        boolean mining = mc.options.keyAttack.isDown() && mc.hitResult instanceof BlockHitResult;
        if (state.enabled("auto_tool") && mining) {
            var hit = (BlockHitResult)mc.hitResult;
            var block = mc.level.getBlockState(hit.getBlockPos());
            int best = mc.player.getInventory().getSelectedSlot();
            float speed = mc.player.getInventory().getItem(best).getDestroySpeed(block);
            for (int slot = 0; slot < 9; slot++) {
                float candidate = mc.player.getInventory().getItem(slot).getDestroySpeed(block);
                if (candidate > speed) { speed = candidate; best = slot; }
            }
            selectTool(best);
        } else restoreTool();
        boolean attacking = state.enabled("aura") || state.enabled("trigger");
        boolean assisting = state.enabled("aim_assist") && mc.options.keyAttack.isDown();
        currentTarget = null;
        if (!attacking && !assisting) { combatStatus = "Combat idle"; return; }
        if (mc.gameMode == null || mc.player.isUsingItem() || mining) {
            combatStatus = mining ? "Paused: mining" : "Paused: using item"; return;
        }
        if (state.enabled("trigger") && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living) {
            String rejection = rejection(mc, living);
            combatStatus = rejection.isEmpty() ? "Crosshair target ready" : rejection;
            if (rejection.isEmpty()) currentTarget = living;
        }
        if (currentTarget == null && (state.enabled("aura") || assisting)) {
            currentTarget = mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(state.attackRange() + 1), entity -> rejection(mc, entity).isEmpty())
                .stream().min(Comparator.comparingDouble(entity -> distance(mc, entity))).orElse(null);
        }
        if (currentTarget == null) {
            if (!state.targetPlayers() && !state.targetMobs()) combatStatus = "All target types OFF - open combat settings";
            else if (!(mc.hitResult instanceof EntityHitResult)) combatStatus = state.enabled("aura") ? "No eligible visible target" : "Trigger: aim at a target";
            return;
        }
        if ((state.enabled("auto_weapon") || state.enabled("shield_breaker")) && chooseWeapon(mc, currentTarget)) {
            combatStatus = "Weapon changed - waiting for cooldown"; return;
        }
        if (assisting || state.enabled("aura") && state.rotateAura()) aim(mc, currentTarget);
        boolean triggerTarget = state.enabled("trigger") && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == currentTarget;
        if (!triggerTarget && !state.enabled("aura")) { combatStatus = "Aim Assist tracking - no auto attack"; return; }
        if (!aimedAt(mc, currentTarget)) { combatStatus = "Aligning view with target"; return; }
        float charge = mc.player.getAttackStrengthScale(0.5F);
        if (charge < state.cooldown()) { combatStatus = "Weapon cooldown " + Math.round(charge * 100) + "%"; return; }
        if (distance(mc, currentTarget) > Math.min(state.attackRange(), mc.player.entityInteractionRange())) {
            combatStatus = "Outside current weapon interaction range"; return;
        }
        // Explicitly transmit the visible view used for the attack; there are no silent rotations.
        mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(mc.player.getYRot(), mc.player.getXRot(), mc.player.onGround(), mc.player.horizontalCollision));
        mc.gameMode.attack(mc.player, currentTarget);
        mc.player.swing(InteractionHand.MAIN_HAND);
        sentAttacks++;
        combatStatus = "Attack sent: " + currentTarget.getName().getString();
    }

    private void inventory(Minecraft mc) {
        if (inventoryDelay > 0 || mc.gameMode == null || mc.player.containerMenu != mc.player.inventoryMenu
            || !mc.player.containerMenu.getCarried().isEmpty()) return;
        if (state.enabled("auto_totem") && mc.player.getHealth() + mc.player.getAbsorptionAmount() <= state.totemHealth()
            && !mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            for (int slot = 0; slot < 36; slot++) if (mc.player.getInventory().getItem(slot).is(Items.TOTEM_OF_UNDYING)) {
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, menuSlot(slot), 40, ClickType.SWAP, mc.player);
                inventoryDelay = 5; return;
            }
        }
        if (state.enabled("auto_armor")) {
            for (int slot = 0; slot < 36; slot++) {
                var item = mc.player.getInventory().getItem(slot);
                var equippable = item.get(DataComponents.EQUIPPABLE);
                if (equippable == null || !(equippable.slot() == EquipmentSlot.HEAD || equippable.slot() == EquipmentSlot.CHEST
                    || equippable.slot() == EquipmentSlot.LEGS || equippable.slot() == EquipmentSlot.FEET)) continue;
                if (!mc.player.getItemBySlot(equippable.slot()).isEmpty()) continue;
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, menuSlot(slot), 0, ClickType.QUICK_MOVE, mc.player);
                inventoryDelay = 5; return;
            }
        }
    }
    private int menuSlot(int slot) { return slot < 9 ? slot + 36 : slot; }
    private boolean chooseWeapon(Minecraft mc, LivingEntity target) {
        boolean axe = state.enabled("shield_breaker") && target.isBlocking();
        int best = mc.player.getInventory().getSelectedSlot();
        double bestDamage = -1;
        for (int slot = 0; slot < 9; slot++) {
            var item = mc.player.getInventory().getItem(slot);
            if (axe && !item.is(ItemTags.AXES)) continue;
            if (!state.enabled("auto_weapon") && !axe) return false;
            double damage = item.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .compute(Attributes.ATTACK_DAMAGE, 1, EquipmentSlot.MAINHAND);
            if (damage > bestDamage) { bestDamage = damage; best = slot; }
        }
        if (best != mc.player.getInventory().getSelectedSlot()) { mc.player.getInventory().setSelectedSlot(best); return true; }
        return false;
    }
    private String rejection(Minecraft mc, LivingEntity target) {
        return TargetPolicy.reject(state, new TargetPolicy.Candidate(target == mc.player, target.isAlive(), target.isSpectator(),
            target.isInvulnerable(), target instanceof Player, target instanceof Player p && p.isCreative(), target instanceof Mob,
            mc.player.isAlliedTo(target), target.isInvisible(), mc.player.hasLineOfSight(target), distance(mc, target), angle(mc, target)));
    }
    private double distance(Minecraft mc, LivingEntity target) {
        Vec3 eye = mc.player.getEyePosition(); var box = target.getBoundingBox();
        return eye.distanceTo(new Vec3(Mth.clamp(eye.x, box.minX, box.maxX), Mth.clamp(eye.y, box.minY, box.maxY), Mth.clamp(eye.z, box.minZ, box.maxZ)));
    }
    private Vec3 aimPoint(LivingEntity target) {
        return target.getBoundingBox().getCenter().add(0, target.getBbHeight() * 0.15, 0);
    }
    private double angle(Minecraft mc, LivingEntity target) {
        Vec3 delta = aimPoint(target).subtract(mc.player.getEyePosition());
        float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90;
        return Math.abs(Mth.wrapDegrees(yaw - mc.player.getYRot()));
    }
    private void aim(Minecraft mc, LivingEntity target) {
        Vec3 delta = aimPoint(target).subtract(mc.player.getEyePosition());
        float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90;
        float pitch = (float)-Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        mc.player.setYRot(TargetPolicy.approachAngle(mc.player.getYRot(), yaw, (float)state.aimSpeed()));
        mc.player.setXRot(Math.clamp(TargetPolicy.approachAngle(mc.player.getXRot(), pitch, (float)state.aimSpeed()), -90, 90));
    }
    private boolean aimedAt(Minecraft mc, LivingEntity target) {
        Vec3 eye = mc.player.getEyePosition();
        return target.getBoundingBox().inflate(0.01).clip(eye, eye.add(mc.player.getLookAngle().scale(state.attackRange()))).isPresent()
            || target.getBoundingBox().contains(eye);
    }
    private <T> void option(OptionInstance<T> option, boolean enabled, T value) {
        if (enabled) { options.putIfAbsent(option, option.get()); option.set(value); }
        else if (options.containsKey(option)) restoreOption(option);
    }
    @SuppressWarnings("unchecked") private <T> void restoreOption(OptionInstance<T> option) { option.set((T)options.remove(option)); }
    void restore(Minecraft mc) {
        for (var option : java.util.List.copyOf(options.keySet())) restoreOption(option);
        restoreSprint(mc); restoreTool(); currentTarget = null;
    }
    private void restoreSprint(Minecraft mc) {
        if (sprintOwned && owner != null && !mc.options.keySprint.isDown()) owner.setSprinting(false);
        sprintOwned = false;
    }
    private void selectTool(int slot) {
        if (slot != owner.getInventory().getSelectedSlot()) {
            if (previousSlot < 0) previousSlot = owner.getInventory().getSelectedSlot();
            owner.getInventory().setSelectedSlot(slot); autoSelectedSlot = slot;
        }
    }
    private void restoreTool() {
        if (previousSlot >= 0 && owner != null && owner.getInventory().getSelectedSlot() == autoSelectedSlot)
            owner.getInventory().setSelectedSlot(previousSlot);
        previousSlot = -1; autoSelectedSlot = -1;
    }
}
