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
    private boolean eating,recast;
    private static boolean physical(Minecraft mc,KeyMapping mapping) {
        var key=KeyBindingHelper.getBoundKeyOf(mapping);
        if(key.getType()==InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(mc.getWindow().handle(),key.getValue())==GLFW.GLFW_PRESS;
        return key.getValue()>=0 && key.getType()==InputConstants.Type.KEYSYM && InputConstants.isKeyDown(mc.getWindow(),key.getValue());
    }
    private void hold(KeyMapping key) { key.setDown(true);owned.add(key); }
    private void releaseKeys(Minecraft mc) { for(var key:owned) key.setDown(physical(mc,key));owned.clear(); }
    void beginTick(Minecraft mc,ClientState state,ActionBudget budget) {
        releaseKeys(mc);
        if(mc.player==null || !mc.player.isAlive() || !mc.isWindowActive()) { restore(mc);return; }
        if(state.enabled("inventory_move") && mc.screen instanceof AbstractContainerScreen<?>) {
            for(var key:new KeyMapping[]{mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyJump,mc.options.keySprint}) {
                key.setDown(physical(mc,key));owned.add(key);
            }
        }
        if(mc.screen!=null) { finishEating(mc,budget);return; }
        if(state.enabled("auto_walk")) hold(mc.options.keyUp);
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
        releaseKeys(mc);
        if(eater!=null && priorSlot>=0 && eater.getInventory().getSelectedSlot()==foodSlot) eater.getInventory().setSelectedSlot(priorSlot);
        eating=false;eater=null;priorSlot=foodSlot=-1;fishDelay=0;recast=false;
    }
}
