package dev.levrn.client;

import dev.levrn.core.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Comparator;

/** All mutations happen on the client tick thread; no packet queues or worker threads. */
final class ModuleRuntime {
    private final ClientState state;
    private Double previousGamma;
    private Integer previousFov;
    private boolean sprintOwned;
    private int previousSlot = -1;
    private int autoSelectedSlot = -1;
    private LocalPlayer owner;

    ModuleRuntime(ClientState state) { this.state = state; }

    void tick(Minecraft mc) {
        if (owner != mc.player) { restore(mc); owner = mc.player; }
        if (mc.player == null || mc.level == null) { restore(mc); return; }
        if (state.enabled("brightness")) {
            if (previousGamma == null) previousGamma = mc.options.gamma().get();
            mc.options.gamma().set(1.0);
        } else restoreGamma(mc);
        if (state.enabled("zoom")) {
            if (previousFov == null) previousFov = mc.options.fov().get();
            mc.options.fov().set(state.zoomFov());
        } else restoreFov(mc);

        if (mc.screen != null || !mc.isWindowActive() || !mc.player.isAlive()) {
            restoreSprint(mc); restoreTool(mc); return;
        }
        boolean shouldSprint = state.enabled("sprint") && mc.options.keyUp.isDown()
            && !mc.player.isCrouching() && !mc.player.isUsingItem()
            && (mc.player.getFoodData().getFoodLevel() > 6 || mc.player.getAbilities().mayfly);
        if (shouldSprint) {
            if (!mc.player.isSprinting()) { mc.player.setSprinting(true); sprintOwned = true; }
        } else restoreSprint(mc);

        if (state.enabled("auto_tool") && mc.options.keyAttack.isDown()
            && mc.hitResult instanceof BlockHitResult hit) {
            var block = mc.level.getBlockState(hit.getBlockPos());
            int best = mc.player.getInventory().getSelectedSlot();
            float speed = mc.player.getInventory().getItem(best).getDestroySpeed(block);
            for (int slot = 0; slot < 9; slot++) {
                float candidate = mc.player.getInventory().getItem(slot).getDestroySpeed(block);
                if (candidate > speed) { speed = candidate; best = slot; }
            }
            if (best != mc.player.getInventory().getSelectedSlot()) {
                if (previousSlot < 0) previousSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(best);
                autoSelectedSlot = best;
            }
        } else restoreTool(mc);

        if (mc.gameMode == null || mc.player.isUsingItem() || mc.player.getAttackStrengthScale(0.0F) < 1.0F) return;
        LivingEntity target = null;
        if (state.enabled("trigger") && mc.hitResult instanceof EntityHitResult hit
            && hit.getEntity() instanceof LivingEntity living && valid(mc, living)) target = living;
        if (target == null && state.enabled("aura")) {
            target = mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(state.attackRange()), entity -> valid(mc, entity))
                .stream().min(Comparator.comparingDouble(mc.player::distanceToSqr)).orElse(null);
        }
        if (target != null) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private boolean valid(Minecraft mc, LivingEntity target) {
        if (target == mc.player || !target.isAlive() || target.isSpectator() || target.isInvulnerable()
            || !mc.player.hasLineOfSight(target) || mc.player.isAlliedTo(target)
            || mc.player.distanceToSqr(target) > state.attackRange() * state.attackRange()) return false;
        if (target instanceof Player player) return state.targetPlayers() && !player.isCreative();
        return target instanceof Mob;
    }

    void restore(Minecraft mc) {
        restoreGamma(mc); restoreFov(mc); restoreSprint(mc); restoreTool(mc);
    }
    private void restoreGamma(Minecraft mc) {
        if (previousGamma != null) { mc.options.gamma().set(previousGamma); previousGamma = null; }
    }
    private void restoreFov(Minecraft mc) {
        if (previousFov != null) { mc.options.fov().set(previousFov); previousFov = null; }
    }
    private void restoreSprint(Minecraft mc) {
        if (sprintOwned && owner != null && !mc.options.keySprint.isDown()) owner.setSprinting(false);
        sprintOwned = false;
    }
    private void restoreTool(Minecraft mc) {
        if (previousSlot >= 0 && owner != null && owner.getInventory().getSelectedSlot() == autoSelectedSlot)
            owner.getInventory().setSelectedSlot(previousSlot);
        previousSlot = -1;
        autoSelectedSlot = -1;
    }
}
