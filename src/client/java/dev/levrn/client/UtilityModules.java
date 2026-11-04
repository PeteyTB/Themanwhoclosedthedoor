package dev.levrn.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.levrn.client.mixin.FishingHookAccessor;
import dev.levrn.client.mixin.MinecraftAccessor;
import dev.levrn.core.ActionBudget;
import dev.levrn.core.ClientState;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import java.util.HashSet;
import java.util.Set;

/** Input ownership and bounded normal interactions, with explicit deactivation restoration. */
final class UtilityModules {
    private final Set<KeyMapping> owned=new HashSet<>();
    private LocalPlayer eater;
    private int priorSlot=-1,foodSlot=-1,fishDelay;
    private boolean eating,recast,sprintOwned;
    private LocalPlayer sprintOwner;
    static boolean physical(Minecraft mc,KeyMapping mapping) {
        var key=KeyBindingHelper.getBoundKeyOf(mapping);
        if(key.getType()==InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(mc.getWindow().handle(),key.getValue())==GLFW.GLFW_PRESS;
        return key.getValue()>=0 && key.getType()==InputConstants.Type.KEYSYM && InputConstants.isKeyDown(mc.getWindow(),key.getValue());
    }
    private void hold(KeyMapping key) { key.setDown(true);owned.add(key); }
    private void releaseKeys(Minecraft mc) { for(var key:owned) key.setDown(physical(mc,key));owned.clear(); }
    void beginTick(Minecraft mc,ClientState state,ActionBudget budget,boolean suppressSprint,boolean jumpRequested) {
        releaseKeys(mc);
        if(mc.player==null || !mc.player.isAlive() || !mc.isWindowActive()) { restore(mc);return; }
        if(state.enabled("inventory_move") && mc.screen instanceof AbstractContainerScreen<?>) {
            for(var key:new KeyMapping[]{mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyJump,mc.options.keySprint}) {
                key.setDown(physical(mc,key));owned.add(key);
            }
        }
        if(mc.screen!=null) { finishEating(mc,budget);return; }
        if(state.enabled("auto_walk")) hold(mc.options.keyUp);
        if(suppressSprint) {mc.options.keySprint.setDown(false);owned.add(mc.options.keySprint);mc.player.setSprinting(false);}
        else if(state.enabled("sprint") && mc.options.keyUp.isDown() && !mc.player.isCrouching() && !mc.player.isUsingItem()) {
            if(!mc.player.isSprinting()) {sprintOwned=true;sprintOwner=mc.player;}
            hold(mc.options.keySprint);
        } else if(sprintOwned) restoreSprint(mc);
        if(jumpRequested) hold(mc.options.keyJump);
        if(state.enabled("safe_walk") && mc.player.onGround() && !mc.player.getAbilities().flying) {
            double forward=(mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0),side=(mc.options.keyLeft.isDown()?1:0)-(mc.options.keyRight.isDown()?1:0);
            double yaw=Math.toRadians(mc.player.getYRot()),dx=side*Math.cos(yaw)-forward*Math.sin(yaw),dz=forward*Math.cos(yaw)+side*Math.sin(yaw);
            if((dx!=0 || dz!=0) && mc.level.noCollision(mc.player,mc.player.getBoundingBox().move(mc.player.getDeltaMovement().x+dx*.25,-.6,mc.player.getDeltaMovement().z+dz*.25))) hold(mc.options.keyShift);
        }
        if(eating && (eater!=mc.player || !state.enabled("auto_eat") || mc.player.getFoodData().getFoodLevel()>=20
            || mc.player.getInventory().getSelectedSlot()!=foodSlot || mc.player.getMainHandItem().isEmpty())) finishEating(mc,budget);
        if(!eating && state.enabled("auto_eat") && mc.player.getFoodData().getFoodLevel()<=state.eatHunger()
            && !mc.player.isUsingItem() && !physical(mc,mc.options.keyUse) && !mc.options.keyAttack.isDown()) {
            int best=-1,nutrition=0;
            for(int slot=0;slot<9;slot++) {
                var item=mc.player.getInventory().getItem(slot);var food=item.get(DataComponents.FOOD);
                if(food!=null && food.nutrition()>nutrition && !item.is(Items.ROTTEN_FLESH) && !item.is(Items.POISONOUS_POTATO)
                    && !item.is(Items.SPIDER_EYE) && !item.is(Items.PUFFERFISH) && !item.is(Items.CHORUS_FRUIT)
                    && !item.is(Items.GOLDEN_APPLE) && !item.is(Items.ENCHANTED_GOLDEN_APPLE)) { best=slot;nutrition=food.nutrition(); }
            }
            if(best>=0 && budget.take()) {
                eater=mc.player;priorSlot=mc.player.getInventory().getSelectedSlot();foodSlot=best;
                mc.player.getInventory().setSelectedSlot(best);eating=true;
            }
        }
        if(eating) hold(mc.options.keyUse);
    }
    void remapMovement(Minecraft mc,float cameraYaw,float serverYaw) {
        var keys=dev.levrn.core.MovementKeys.remap(mc.options.keyUp.isDown(),mc.options.keyDown.isDown(),mc.options.keyLeft.isDown(),mc.options.keyRight.isDown(),cameraYaw,serverYaw);
        mc.options.keyUp.setDown(keys.forward());mc.options.keyDown.setDown(keys.backward());mc.options.keyLeft.setDown(keys.left());mc.options.keyRight.setDown(keys.right());
        owned.add(mc.options.keyUp);owned.add(mc.options.keyDown);owned.add(mc.options.keyLeft);owned.add(mc.options.keyRight);
    }
    private void restoreSprint(Minecraft mc) {
        if(sprintOwned && sprintOwner==mc.player && !physical(mc,mc.options.keySprint)) sprintOwner.setSprinting(false);
        sprintOwned=false;sprintOwner=null;
    }
    private void finishEating(Minecraft mc,ActionBudget budget) {
        if(!eating) return;
        if(eater==mc.player && priorSlot>=0 && eater.getInventory().getSelectedSlot()==foodSlot) {
            if(!budget.take()) return;
            eater.getInventory().setSelectedSlot(priorSlot);
        }
        eating=false;eater=null;priorSlot=foodSlot=-1;
    }
    void tick(Minecraft mc,ClientState state,ActionBudget budget) {
        if(mc.player==null || mc.gameMode==null || mc.screen!=null || eating || mc.player.isUsingItem()) return;
        if(state.enabled("fast_place") && mc.options.keyUse.isDown() && mc.player.getMainHandItem().getItem() instanceof BlockItem) {
            var access=(MinecraftAccessor)mc;
            if(access.levrn$getUseDelay()>state.placeDelay()) access.levrn$setUseDelay(state.placeDelay());
        }
        if(!state.enabled("auto_fish")) { fishDelay=0;recast=false;return; }
        InteractionHand hand=mc.player.getMainHandItem().is(Items.FISHING_ROD) ? InteractionHand.MAIN_HAND
            : mc.player.getOffhandItem().is(Items.FISHING_ROD) ? InteractionHand.OFF_HAND : null;
        if(hand==null || physical(mc,mc.options.keyUse)) { recast=false;return; }
        if(fishDelay>0) { fishDelay--;return; }
        if(mc.player.fishing!=null && ((FishingHookAccessor)mc.player.fishing).levrn$isBiting() && budget.take()) {
            mc.gameMode.useItem(mc.player,hand);fishDelay=12;recast=true;
        } else if(recast && mc.player.fishing==null && budget.take()) {
            mc.gameMode.useItem(mc.player,hand);fishDelay=20;recast=false;
        }
    }
    void restore(Minecraft mc) {
        releaseKeys(mc);restoreSprint(mc);
        if(eater!=null && priorSlot>=0 && eater.getInventory().getSelectedSlot()==foodSlot) eater.getInventory().setSelectedSlot(priorSlot);
        eating=false;eater=null;priorSlot=foodSlot=-1;fishDelay=0;recast=false;
    }
}
