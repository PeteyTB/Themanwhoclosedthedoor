package dev.levrn.client;

import dev.levrn.core.ClientState;
import dev.levrn.core.TargetPolicy;
import dev.levrn.core.ActionBudget;
import dev.levrn.core.TargetRetention;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import dev.levrn.core.CombatGuard;
import net.minecraft.world.effect.MobEffects;
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

/** Client-thread modules coordinated with vanilla movement serialization and inventory handling. */
final class ModuleRuntime {
    private final ClientState state;
    private final Map<OptionInstance<?>, Object> options = new HashMap<>();
    private final ActionBudget actionBudget = new ActionBudget();
    private final net.minecraft.world.item.ItemStack[] hotbarTemplates = new net.minecraft.world.item.ItemStack[9];
    private boolean sprintOwned, respawnRequested;
    private int previousSlot = -1, autoSelectedSlot = -1, inventoryDelay, toolRestoreDelay;
    private LocalPlayer owner;
    private final InventoryTransaction inventoryTransaction=new InventoryTransaction();
    private final UtilityModules utility=new UtilityModules();
    private LocalPlayer rotatingPlayer;
    private float cameraYaw,cameraPitch,cameraYawOld,cameraPitchOld,serverYaw,serverPitch;
    private boolean jumpRequested;
    private LivingEntity preparedTarget;
    private long worldTicks;
    private boolean silentRotation,serverRotationKnown,attackPending;
    private int correctionWait=20,sprintWait;
    long serverCorrections;
    void beginTick(Minecraft mc) {
        restoreCamera();preparedTarget=null;worldTicks++;actionBudget.nextTick();
        if(correctionWait>0) correctionWait--;
        if(sprintWait>0) sprintWait--;
        if(correctionWait>0 || inventoryTransaction.active()) utility.restore(mc);
        else {utility.beginTick(mc,state,actionBudget,sprintWait>0 || state.enabled("criticals") && (state.enabled("aura") || state.enabled("trigger")) && currentTarget!=null,jumpRequested && state.enabled("criticals"));prepareRotation(mc);}
        jumpRequested=false;
    }
    private long lastAttackExpiry;
    AdaptiveEngine nova; // NOVA adaptive engine reference, set by LevrnClient
    void corrected() {
        var mc=Minecraft.getInstance();inventoryTransaction.abort(mc,false);restoreCamera();correctionWait=10;serverRotationKnown=false;attackPending=false;currentTarget=null;preparedTarget=null;serverCorrections++;
        AdaptiveEngine.notifyCorrection(nova);
    }
    void disconnected() { inventoryTransaction.abort(Minecraft.getInstance());restoreCamera();utility.restore(Minecraft.getInstance());correctionWait=20;serverRotationKnown=false;attackPending=false;currentTarget=null;previousPlayerHealth=-1;wasAlive=true;lastAttackTarget=null; }
    void totemPopped() { AdaptiveEngine.notifyTotemPop(nova); }
    private boolean canInteract(Minecraft mc) {
        return mc.player!=null && mc.level!=null && mc.gameMode!=null && mc.screen==null && mc.isWindowActive()
            && mc.player.isAlive() && !mc.player.isSpectator() && !mc.player.isPassenger() && mc.getCameraEntity()==mc.player
            && CombatGuard.validPose(mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.getYRot(),mc.player.getXRot());
    }
    void restoreCamera() {
        if(silentRotation && rotatingPlayer!=null) { rotatingPlayer.setYRot(cameraYaw);rotatingPlayer.setXRot(cameraPitch);rotatingPlayer.yRotO=cameraYawOld;rotatingPlayer.xRotO=cameraPitchOld; }
        silentRotation=false;rotatingPlayer=null;
    }
    LivingEntity currentTarget;
    String combatStatus = "Combat idle";
    long sentAttacks;
    // NOVA combat tracking state
    private LivingEntity lastAttackTarget;
    private float lastAttackTargetHealth;
    private float previousPlayerHealth = -1;
    private boolean wasAlive = true;

    ModuleRuntime(ClientState state) { this.state = state; }

    void tick(Minecraft mc) {
        if (owner != mc.player) { restore(mc); owner = mc.player; previousPlayerHealth=0;wasAlive=false;lastAttackTarget=null;respawnRequested = false;worldTicks=0; correctionWait=20; serverRotationKnown=false; java.util.Arrays.fill(hotbarTemplates,null); }
        if (mc.player == null || mc.level == null) { restore(mc); combatStatus = "Join a world to use combat"; return; }
        if(inventoryTransaction.active()) { inventoryTransaction.tick(mc,actionBudget,(int)state.inventoryInterval());currentTarget=null;combatStatus="Inventory transaction - synchronizing";return; }
        trackHotbar(mc);
        // NOVA combat state tracking
        if (nova != null) {
            nova.tick();
            float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            // Detect damage taken
            if (previousPlayerHealth > 0 && hp < previousPlayerHealth - 0.01f) {
                AdaptiveEngine.notifyDamageTaken(nova, previousPlayerHealth - hp);
            }
            previousPlayerHealth = hp;
            // Detect death
            if (wasAlive && !mc.player.isAlive()) {
                AdaptiveEngine.notifyDeath(nova);
            }
            wasAlive = mc.player.isAlive();
            // Detect hit and kill on attack target
            if (lastAttackTarget != null) {
                float targetHp = lastAttackTarget.getHealth();
                if (targetHp < lastAttackTargetHealth - 0.01f) {
                    AdaptiveEngine.notifyAttackHit(nova);
                    AdaptiveEngine.notifyDamageDealt(nova, lastAttackTargetHealth - targetHp);
                    if (targetHp <= 0) {
                        AdaptiveEngine.notifyKill(nova);
                    }
                }
                if(targetHp<lastAttackTargetHealth-.01f || worldTicks>lastAttackExpiry || !lastAttackTarget.isAlive()) lastAttackTarget = null;
            }
        }
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
        if (mc.screen != null || !mc.isWindowActive() || !mc.player.isAlive() || correctionWait>0) {
            restoreSprint(mc); restoreTool(); currentTarget = null;
            combatStatus = mc.screen != null ? "Paused: menu open" : "Paused: inactive or dead";
            return;
        }
        if (inventoryDelay > 0) inventoryDelay--;
        if (toolRestoreDelay > 0) toolRestoreDelay--;
        // Defer inventory operations while sprinting to reduce packet bursts (Prestige-inspired).
        if (!mc.player.isSprinting()) inventory(mc);

        boolean mining = mc.options.keyAttack.isDown() && mc.hitResult instanceof BlockHitResult;
        if (state.enabled("auto_tool") && mining) {
            toolRestoreDelay = 4; // delay restore so rapid click/release does not oscillate slots
            var hit = (BlockHitResult)mc.hitResult;
            var block = mc.level.getBlockState(hit.getBlockPos());
            int best = mc.player.getInventory().getSelectedSlot();
            float speed = mc.player.getInventory().getItem(best).getDestroySpeed(block);
            for (int slot = 0; slot < 9; slot++) {
                float candidate = mc.player.getInventory().getItem(slot).getDestroySpeed(block);
                if (candidate > speed) { speed = candidate; best = slot; }
            }
            selectTool(best);
        } else if (toolRestoreDelay <= 0) restoreTool();
        if (actionBudget.used()) { combatStatus = actionBudget.attacked() ? "Attack sent - waiting" : "Inventory change - synchronizing"; currentTarget = null; return; }
        utility.tick(mc,state,actionBudget);
    }

    // Invoked immediately before vanilla constructs its one movement update.
    void beforeMovement(Minecraft mc) {
        attackPending=false;
        if(sprintWait>0 && mc.player!=null && mc.player.isSprinting()) mc.player.setSprinting(false);
        if (!canInteract(mc) || correctionWait>0 || actionBudget.used() || inventoryTransaction.active()) {
            currentTarget=null;combatStatus=correctionWait>0 ? "Waiting for server synchronization" : "Paused: input or inventory";return;
        }
        boolean mining=mc.options.keyAttack.isDown() && mc.hitResult instanceof BlockHitResult;
        boolean attacking = state.enabled("aura") || state.enabled("trigger");
        boolean assisting = state.enabled("aim_assist") && mc.options.keyAttack.isDown();
        LivingEntity previousTarget = currentTarget;
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
            var eligible = mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(state.attackRange() + 1), entity -> rejection(mc, entity).isEmpty());
            int selectedId = TargetRetention.choose(previousTarget == null ? -1 : previousTarget.getId(),
                eligible.stream().map(entity -> new TargetRetention.Candidate(entity.getId(),distance(mc,entity))).toList(),state.stickyTargets());
            currentTarget = eligible.stream().filter(entity -> entity.getId() == selectedId).findFirst().orElse(null);
        }
        if (currentTarget == null) {
            if (!state.targetPlayers() && !state.targetMobs()) combatStatus = "All target types OFF - open combat settings";
            else if (!(mc.hitResult instanceof EntityHitResult)) combatStatus = state.enabled("aura") ? "No eligible visible target" : "Trigger: aim at a target";
            return;
        }
        if ((state.enabled("auto_weapon") || state.enabled("shield_breaker")) && chooseWeapon(mc, currentTarget)) {
            combatStatus = "Weapon changed - waiting for cooldown"; return;
        }
        if((state.enabled("aura") && state.auraRotation()!=ClientState.AuraRotation.OFF || assisting) && currentTarget!=preparedTarget) {
            combatStatus="Waiting for synchronized rotation";return;
        }
        boolean triggerTarget = state.enabled("trigger") && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == currentTarget;
        if (!triggerTarget && !state.enabled("aura")) { combatStatus = "Aim Assist tracking - no auto attack"; return; }
        if (!aimedAt(mc, currentTarget)) { combatStatus = "Aligning view with target"; return; }
        float charge = mc.player.getAttackStrengthScale(0.5F);
        if (charge < state.cooldown()) { combatStatus = "Weapon cooldown " + Math.round(charge * 100) + "%"; return; }
        if (distance(mc, currentTarget) > Math.min(state.attackRange(), mc.player.entityInteractionRange())) {
            combatStatus = "Outside current weapon interaction range"; return;
        }
        var critical=CombatGuard.critical(state.enabled("criticals"),!mc.player.isInWater() && !mc.player.isInLava()
            && !mc.player.onClimbable() && !mc.player.hasEffect(MobEffects.BLINDNESS) && !mc.player.isFallFlying()
            && !mc.player.getAbilities().flying,mc.player.onGround(),mc.player.fallDistance,mc.player.getDeltaMovement().y,charge);
        if(critical==CombatGuard.Critical.JUMP) { if(mc.player.isSprinting()) mc.player.setSprinting(false);sprintWait=3;jumpRequested=true;combatStatus="Criticals: preparing native jump input";return; }
        if(critical==CombatGuard.Critical.WAIT) { combatStatus="Criticals: waiting for descent";return; }
        if(critical==CombatGuard.Critical.HIT) { if(mc.player.isSprinting()) mc.player.setSprinting(false);sprintWait=3; }
        attackPending=true;
    }
    // Vanilla has now emitted movement/rotation. Never add a second movement packet here.
    void afterMovement(Minecraft mc) {
        try {
            if(!attackPending || !canInteract(mc) || currentTarget==null || !rejection(mc,currentTarget).isEmpty()
                || !aimedAt(mc,currentTarget) || mc.player.getAttackStrengthScale(0.5F)<state.cooldown()) return;
            if(!actionBudget.recordPacket() || !actionBudget.recordPacket()) return; // need 2 slots: attack + swing
            mc.gameMode.attack(mc.player,currentTarget);
            mc.player.swing(InteractionHand.MAIN_HAND);sentAttacks++;
            AdaptiveEngine.notifyAttackSent(nova);
            lastAttackTarget=currentTarget;lastAttackTargetHealth=currentTarget.getHealth();lastAttackExpiry=worldTicks+20;
            actionBudget.markAttacked(); // consume action slot so inventory does not also fire this tick
            if(state.enabled("w_tap") && mc.player.isSprinting()) { mc.player.setSprinting(false);sprintWait=3; }
            combatStatus="Attack sent: "+currentTarget.getName().getString();
        } finally { attackPending=false;restoreCamera(); }
    }

    private void prepareRotation(Minecraft mc) {
        if(!canInteract(mc) || actionBudget.used() || mc.player.isUsingItem() || mc.options.keyUse.isDown() || mc.options.keyAttack.isDown() && mc.hitResult instanceof BlockHitResult) return;
        boolean aura=state.enabled("aura") && state.auraRotation()!=ClientState.AuraRotation.OFF;
        boolean assist=state.enabled("aim_assist") && mc.options.keyAttack.isDown();
        if(!aura && !assist) return;
        if((state.enabled("trigger") || mc.options.keyAttack.isDown()) && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living && rejection(mc,living).isEmpty()) preparedTarget=living;
        if(preparedTarget==null) {
            var eligible=mc.level.getEntitiesOfClass(LivingEntity.class,mc.player.getBoundingBox().inflate(state.attackRange()+1),entity -> rejection(mc,entity).isEmpty());
            int id=TargetRetention.choose(currentTarget==null ? -1 : currentTarget.getId(),eligible.stream().map(e -> new TargetRetention.Candidate(e.getId(),distance(mc,e))).toList(),state.stickyTargets());
            preparedTarget=eligible.stream().filter(e -> e.getId()==id).findFirst().orElse(null);
        }
        if(preparedTarget==null) return;
        cameraYaw=mc.player.getYRot();cameraPitch=mc.player.getXRot();cameraYawOld=mc.player.yRotO;cameraPitchOld=mc.player.xRotO;
        rotatingPlayer=mc.player;silentRotation=aura && state.auraRotation()==ClientState.AuraRotation.SILENT;
        if(silentRotation && serverRotationKnown) {mc.player.setYRot(serverYaw);mc.player.setXRot(serverPitch);}
        aim(mc,preparedTarget);serverYaw=mc.player.getYRot();serverPitch=mc.player.getXRot();serverRotationKnown=true;
        if(silentRotation) utility.remapMovement(mc,cameraYaw,serverYaw);
    }

    private void inventory(Minecraft mc) {
        if (inventoryDelay > 0 || mc.gameMode == null || mc.player.containerMenu != mc.player.inventoryMenu
            || !mc.player.containerMenu.getCarried().isEmpty()) return;
        if (state.enabled("auto_totem") && !mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            boolean shouldEquip = state.totemAlways()
                || mc.player.getHealth() + mc.player.getAbsorptionAmount() <= state.totemHealth();
            if (shouldEquip) {
                for (int slot = 0; slot < 36; slot++) if (mc.player.getInventory().getItem(slot).is(Items.TOTEM_OF_UNDYING)) {
                    if (!actionBudget.recordPacket() || !actionBudget.take()) return;
                    mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, menuSlot(slot), 40, ClickType.SWAP, mc.player);
                    inventoryDelay = (int)state.inventoryInterval(); return;
                }
            }
        }
        if (state.enabled("auto_replenish")) {
            for (int hotbar=0;hotbar<9;hotbar++) {
                var current = mc.player.getInventory().getItem(hotbar);
                var template = hotbarTemplates[hotbar];
                if (template == null || template.isEmpty() || template.getMaxStackSize() <= 1
                    || !current.isEmpty() && current.getCount() > state.replenishBelow()) continue;
                for (int source=9;source<36;source++) {
                    var carried = mc.player.getInventory().getItem(source);
                    if (carried.isEmpty() || !net.minecraft.world.item.ItemStack.isSameItemSameComponents(carried,template)) continue;
                    if (!actionBudget.recordPacket() || !actionBudget.take()) return;
                    int container = mc.player.inventoryMenu.containerId;
                    if (current.isEmpty()) mc.gameMode.handleInventoryMouseClick(container,source,hotbar,ClickType.SWAP,mc.player);
                    else {
                        inventoryTransaction.begin(mc.player,source,hotbar+36);
                    }
                    inventoryDelay = (int)state.inventoryInterval(); return;
                }
            }
        }
        if (state.enabled("auto_armor")) {
            EquipmentSlot[] armor = {EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
            for (int part = 0; part < armor.length; part++) {
                EquipmentSlot equipmentSlot = armor[part];
                var worn = mc.player.getItemBySlot(equipmentSlot);
                if (equipmentSlot == EquipmentSlot.CHEST && state.preserveElytra() && worn.is(Items.ELYTRA)) continue;
                if (!worn.isEmpty() && !state.armorUpgrade()) continue;
                var destination = mc.player.inventoryMenu.getSlot(5+part);
                if (!destination.mayPickup(mc.player)) continue;
                int best = -1; double bestScore = armorScore(worn,equipmentSlot);
                for (int slot=0;slot<36;slot++) {
                    var item = mc.player.getInventory().getItem(slot);
                    var equippable = item.get(DataComponents.EQUIPPABLE);
                    if (equippable == null || equippable.slot() != equipmentSlot || !destination.mayPlace(item)) continue;
                    double score = armorScore(item,equipmentSlot);
                    if (score > bestScore + 0.0001) { best = slot; bestScore = score; }
                }
                if (best < 0 || !actionBudget.recordPacket() || !actionBudget.take()) continue;
                int container = mc.player.inventoryMenu.containerId, from = menuSlot(best);
                if (worn.isEmpty()) mc.gameMode.handleInventoryMouseClick(container,from,0,ClickType.QUICK_MOVE,mc.player);
                else {
                    inventoryTransaction.begin(mc.player,from,5+part);
                }
                inventoryDelay = (int)state.inventoryInterval(); return;
            }
        }
    }
    private void trackHotbar(Minecraft mc) {
        for (int slot=0;slot<9;slot++) {
            var item = mc.player.getInventory().getItem(slot);
            if (!state.enabled("auto_replenish")) hotbarTemplates[slot] = null;
            else if (!item.isEmpty() || mc.screen != null) hotbarTemplates[slot] = item.copyWithCount(1);
        }
    }
    private double armorScore(net.minecraft.world.item.ItemStack item,EquipmentSlot slot) {
        if (item.isEmpty()) return -1;
        var modifiers = item.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY);
        double armor = modifiers.compute(Attributes.ARMOR,0,slot);
        double toughness = modifiers.compute(Attributes.ARMOR_TOUGHNESS,0,slot);
        double durability = item.isDamageableItem() ? (item.getMaxDamage()-item.getDamageValue())/(double)Math.max(1,item.getMaxDamage()) : 1;
        return armor*100 + toughness*10 + durability;
    }
    private int menuSlot(int slot) { return slot < 9 ? slot + 36 : slot; }
    private boolean chooseWeapon(Minecraft mc, LivingEntity target) {
        boolean axe = state.enabled("shield_breaker") && target.isBlocking()
            && target.getLookAngle().multiply(1,0,1).dot(mc.player.position().subtract(target.position()).multiply(1,0,1).normalize()) > 0;
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
        if (best != mc.player.getInventory().getSelectedSlot()) {
            if (actionBudget.take()) mc.player.getInventory().setSelectedSlot(best);
            return true;
        }
        return false;
    }
    private String rejection(Minecraft mc, LivingEntity target) {
        return TargetPolicy.reject(state, new TargetPolicy.Candidate(target == mc.player, target.isAlive(), target.isSpectator(),
            target.isInvulnerable(), target instanceof Player, target instanceof Player p && p.isCreative(), target instanceof Mob,
            mc.player.isAlliedTo(target), target.isInvisible(), mc.player.hasLineOfSight(target) && mc.level.clip(new net.minecraft.world.level.ClipContext(mc.player.getEyePosition(),aimPoint(target),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player)).getType()==net.minecraft.world.phys.HitResult.Type.MISS, distance(mc, target), angle(mc, target)));
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
        return Math.abs(Mth.wrapDegrees(yaw - (silentRotation ? cameraYaw : mc.player.getYRot())));
    }
    private void aim(Minecraft mc, LivingEntity target) {
        Vec3 delta = aimPoint(target).subtract(mc.player.getEyePosition());
        float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90;
        float pitch = (float)-Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        if(!Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
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
        inventoryTransaction.abort(mc);restoreCamera();utility.restore(mc);attackPending=false;jumpRequested=false;preparedTarget=null;
        for (var option : java.util.List.copyOf(options.keySet())) restoreOption(option);
        restoreSprint(mc); restoreTool(true); currentTarget = null;
    }
    private void restoreSprint(Minecraft mc) {
        if (sprintOwned && owner != null && !mc.options.keySprint.isDown() && owner.isSprinting()) owner.setSprinting(false);
        sprintOwned = false;
    }
    private void selectTool(int slot) {
        if (slot != owner.getInventory().getSelectedSlot() && actionBudget.take()) {
            if (previousSlot < 0) previousSlot = owner.getInventory().getSelectedSlot();
            owner.getInventory().setSelectedSlot(slot); autoSelectedSlot = slot;
        }
    }
    private void restoreTool() {
        restoreTool(false);
    }
    private void restoreTool(boolean immediate) {
        if (previousSlot >= 0 && owner != null && owner.getInventory().getSelectedSlot() == autoSelectedSlot) {
            if (!immediate && !actionBudget.take()) return;
            owner.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1; autoSelectedSlot = -1;
    }
}
