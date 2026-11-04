package dev.levrn.client;

import dev.levrn.core.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;

/**
 * Adapted from the user's FuturisticHud: bracketed panels, scan sweep, armor bars and key glows.
 * Mojang 1.21.11 names; registered once by LevrnClient. Reads game state only.
 */
final class FuturisticHud {
    private static final int ACCENT=0xFF35F2FF,ACCENT_DIM=0x6635F2FF,PANEL=0xB0090E1A;
    private static final int TEXT=0xFFEAF6FF,MUTED=0xFF7F93AB,WARN=0xFFFFB454,DANGER=0xFFFF4D6D;
    private static final EquipmentSlot[] ARMOR={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private static final String[] ARMOR_TAG={"HEL","CHE","LEG","BOO"};
    private static final DateTimeFormatter CLOCK=DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final FuturisticHud INSTANCE=new FuturisticHud();
    private final float[] armorShown=new float[4],keyGlow=new float[6];
    private long lastNanos=System.nanoTime();
    private UUID owner;

    static void render(GuiGraphics g) { INSTANCE.renderHud(g); }
    private void renderHud(GuiGraphics g) {
        var mc=Minecraft.getInstance();var p=mc.player;var state=LevrnClient.STATE;
        long now=System.nanoTime();float dt=(float)Math.clamp((now-lastNanos)/1e9,0,0.1);lastNanos=now;
        if(p==null || mc.level==null) {owner=null;return;}
        if(mc.options.hideGui) return;
        if(!p.getUUID().equals(owner)) {
            owner=p.getUUID();Arrays.fill(keyGlow,0);
            for(int i=0;i<4;i++) armorShown[i]=durability(p.getItemBySlot(ARMOR[i]));
        }
        double scale=state.uiScale()/mc.getWindow().getGuiScale();
        int width=(int)(g.guiWidth()/scale),height=(int)(g.guiHeight()/scale);
        boolean still=state.reducedMotion();float time=(now%1_000_000_000_000L)/1e9f;
        g.pose().pushMatrix();g.pose().scale((float)scale,(float)scale);
        try {
            int bottom=height-(state.enabled("coordinates") ? 48 : 10),rowY=10;
            if(state.enabled("hud")) {watermark(g,mc,time,still,width);rowY=38;}
            rowY=infoRows(g,mc,rowY,bottom,width);
            if(state.enabled("coordinates")) coordinates(g,mc,width,height);
            if(state.enabled("armor_hud")) armor(g,mc,width,height,scale,dt,still);
            if(state.enabled("keystrokes")) keystrokes(g,mc,width,height,dt,still);
            int rightBottom=height-(state.enabled("keystrokes") ? 78 : 10);
            // Preserve vanilla effect icons, then stack our potion timers and active modules below them.
            int rightY=p.getActiveEffects().stream().noneMatch(effect -> effect.showIcon()) ? 10 : (int)Math.ceil(54/scale)+8;
            if(state.enabled("potion_hud")) rightY=potions(g,mc,width,rightY,rightBottom);
            if(state.enabled("active_list")) activeModules(g,width,rightY,rightBottom);
            if(state.enabled("combat_status") && (state.enabled("aura") || state.enabled("trigger") || state.enabled("aim_assist"))) combat(g,rowY,bottom,width);
            LivingEntity target=LevrnClient.runtime.currentTarget;
            if(target==null && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living) target=living;
            if(state.enabled("target_hud") && target!=null && target.isAlive()) target(g,target,width,height);
        } finally {g.pose().popMatrix();}
        if(!(mc.screen instanceof LevrnScreen)) Notifications.render(g);
    }
    private void watermark(GuiGraphics g,Minecraft mc,float time,boolean still,int width) {
        String fps=mc.getFps()+" FPS";
        int w=Math.min(width-20,24+HudText.width("LEVRN")+10+HudText.width(fps)+10);
        panel(g,10,10,w,22);
        if(!still) {
            int sweep=11+(int)((time*0.6f%1)*(w-2));g.fill(sweep,11,sweep+1,31,ACCENT_DIM);
        }
        g.fill(19,19,23,23,ACCENT);
        text(g,"LEVRN",30,17,TEXT);text(g,fps,30+HudText.width("LEVRN")+10,17,MUTED);
    }
    private int infoRows(GuiGraphics g,Minecraft mc,int y,int bottom,int width) {
        var s=LevrnClient.STATE;var p=mc.player;
        if(s.enabled("speed_hud")) y=row(g,"SPD",String.format(Locale.ROOT,"%.2f B/S",p.getDeltaMovement().horizontalDistance()*20),y,bottom,width);
        if(s.enabled("ping_hud")) {
            var entry=mc.getConnection()==null ? null : mc.getConnection().getPlayerInfo(p.getUUID());
            y=row(g,"NET",entry==null ? "--" : entry.getLatency()+" MS",y,bottom,width);
        }
        if(s.enabled("compass")) y=row(g,"DIR",p.getDirection().getName().toUpperCase(Locale.ROOT)+" "+Math.round(Mth.wrapDegrees(p.getYRot()))+"°",y,bottom,width);
        if(s.enabled("clock")) y=row(g,"LOCAL",LocalTime.now().format(CLOCK),y,bottom,width);
        return y;
    }
    private int row(GuiGraphics g,String tag,String value,int y,int bottom,int width) {
        if(y+14>bottom) return y;
        int tagWidth=HudText.width(tag),limit=Math.min(230,width-20);
        value=HudText.fit(value,Math.max(1,limit-tagWidth-38));
        int w=12+tagWidth+8+HudText.width(value)+10;
        g.fill(10,y,12,y+14,ACCENT);g.fill(12,y,10+w,y+14,PANEL);
        text(g,tag,18,y+3,MUTED);text(g,value,18+tagWidth+8,y+3,TEXT);return y+17;
    }
    private void coordinates(GuiGraphics g,Minecraft mc,int width,int height) {
        String label="XYZ",pos=String.format(Locale.ROOT,"%.1f  %.1f  %.1f",mc.player.getX(),mc.player.getY(),mc.player.getZ());
        String dim=mc.level.dimension().identifier().getPath().toUpperCase(Locale.ROOT);
        int cap=Math.min(280,width-20),prefix=HudText.width(label)+8;
        pos=HudText.fit(pos,cap-prefix-16);dim=HudText.fit(dim,cap-16);
        int w=Math.max(prefix+HudText.width(pos),HudText.width(dim))+16,y=height-38;
        panel(g,10,y,w,28);text(g,label,18,y+5,ACCENT);text(g,pos,18+prefix,y+5,TEXT);text(g,dim,18,y+16,MUTED);
    }
    private static float durability(net.minecraft.world.item.ItemStack stack) {
        return stack.isEmpty() ? 0 : stack.isDamageableItem() ? Math.clamp(1f-(float)stack.getDamageValue()/Math.max(1,stack.getMaxDamage()),0,1) : 1;
    }
    private void armor(GuiGraphics g,Minecraft mc,int width,int height,double scale,float dt,boolean still) {
        int barW=46,rowH=13,w=8+HudText.width("HEL")+8+barW+8,h=4*rowH+8;
        int x=width/2+(int)Math.ceil(91/scale)+10,y=height-14-h;
        int right=width-(LevrnClient.STATE.enabled("keystrokes") ? 78 : 10);
        if(x+w>right) {x=Math.max(10,width/2-w/2);y=Math.max(10,height-(int)Math.ceil(52/scale)-h-8);}
        panel(g,x,y,w,h);
        float blend=still ? 1 : (float)-Math.expm1(-dt*10);
        for(int i=0;i<4;i++) {
            var stack=mc.player.getItemBySlot(ARMOR[i]);float value=durability(stack);
            armorShown[i]+=(value-armorShown[i])*blend;
            int col=stack.isEmpty() ? MUTED : value>0.5f ? ACCENT : value>0.2f ? WARN : DANGER;
            int ry=y+5+i*rowH,bx=x+8+HudText.width("HEL")+8;
            text(g,ARMOR_TAG[i],x+8,ry+1,stack.isEmpty() ? MUTED : TEXT);
            g.fill(bx,ry+3,bx+barW,ry+6,0x40FFFFFF);g.fill(bx,ry+3,bx+Math.round(barW*armorShown[i]),ry+6,col);
        }
    }
    private int potions(GuiGraphics g,Minecraft mc,int width,int y,int bottom) {
        var effects=mc.player.getActiveEffects().stream().sorted(Comparator.comparing(e -> e.getEffect().value().getDescriptionId())).toList();
        int cap=Math.min(230,width/2-20),shown=0;
        for(var effect:effects) {
            if(y+31>bottom) break;
            String name=Component.translatable(effect.getEffect().value().getDescriptionId()).getString();
            if(effect.getAmplifier()>0) name+=" "+(effect.getAmplifier()+1);
            String time=effect.isInfiniteDuration() ? "∞" : String.format(Locale.ROOT,"%d:%02d",effect.getDuration()/1200,effect.getDuration()/20%60);
            name=HudText.fit(name,cap-HudText.width(time)-32);
            int w=10+HudText.width(name)+12+HudText.width(time)+10,x=width-w-10;
            boolean low=!effect.isInfiniteDuration() && effect.getDuration()<200;
            g.fill(x,y,x+w,y+14,PANEL);g.fill(x+w-2,y,x+w,y+14,low ? WARN : ACCENT);
            text(g,name,x+8,y+3,TEXT);text(g,time,x+8+HudText.width(name)+12,y+3,low ? WARN : MUTED);
            y+=17;shown++;
        }
        if(shown<effects.size() && y+14<=bottom) {rightRow(g,"+"+(effects.size()-shown)+" effects",width,y);y+=17;}
        return shown>0 ? y+5 : y;
    }
    private void activeModules(GuiGraphics g,int width,int y,int bottom) {
        var active=LevrnClient.STATE.features().stream().filter(Feature::enabled)
            .filter(f -> f.category!=dev.levrn.core.Category.VISUAL)
            .sorted(Comparator.comparingInt((Feature f) -> HudText.width(f.name)).reversed().thenComparing(f -> f.id)).toList();
        int shown=0;
        for(var feature:active) {
            if(y+31>bottom) break;
            rightRow(g,HudText.fit(feature.name,Math.min(210,width/2-40)),width,y);y+=17;shown++;
        }
        if(shown<active.size() && y+14<=bottom) rightRow(g,"+"+(active.size()-shown)+" modules",width,y);
    }
    private void rightRow(GuiGraphics g,String name,int width,int y) {
        int w=HudText.width(name)+18,x=width-w-10;g.fill(x,y,x+w,y+14,PANEL);g.fill(x+w-2,y,x+w,y+14,ACCENT);
        text(g,name,x+8,y+3,ACCENT);
    }
    private void keystrokes(GuiGraphics g,Minecraft mc,int width,int height,float dt,boolean still) {
        var o=mc.options;boolean[] down={UtilityModules.physical(mc,o.keyUp),UtilityModules.physical(mc,o.keyLeft),UtilityModules.physical(mc,o.keyDown),UtilityModules.physical(mc,o.keyRight),UtilityModules.physical(mc,o.keyJump),UtilityModules.physical(mc,o.keyAttack)};
        float blend=still ? 1 : (float)-Math.expm1(-dt*18);
        for(int i=0;i<6;i++) keyGlow[i]+=((down[i] ? 1 : 0)-keyGlow[i])*blend;
        int size=18,gap=2,x=width-10-(size*3+gap*2),y=height-10-(size*2+12+gap*2);
        key(g,"W",x+size+gap,y,size,size,keyGlow[0]);key(g,"A",x,y+size+gap,size,size,keyGlow[1]);
        key(g,"S",x+size+gap,y+size+gap,size,size,keyGlow[2]);key(g,"D",x+(size+gap)*2,y+size+gap,size,size,keyGlow[3]);
        key(g,"SPACE",x,y+(size+gap)*2,size*2+gap,12,keyGlow[4]);key(g,"LMB",x+(size+gap)*2,y+(size+gap)*2,size,12,keyGlow[5]);
    }
    private void key(GuiGraphics g,String label,int x,int y,int w,int h,float glow) {
        g.fill(x,y,x+w,y+h,PANEL);g.fill(x,y,x+w,y+h,(int)(0x70*glow)<<24|0x0035F2FF);border(g,x,y,w,h,glow>0.5f ? ACCENT : ACCENT_DIM);
        text(g,label,x+(w-HudText.width(label))/2,y+(h-8)/2,glow>0.5f ? TEXT : MUTED);
    }
    private void combat(GuiGraphics g,int y,int bottom,int width) {
        if(y+51>bottom) return;var s=LevrnClient.STATE;int w=Math.min(265,width-20);
        panel(g,10,y,w,48);
        text(g,HudText.fit(LevrnClient.combatStatus(),w-18),19,y+6,ACCENT);
        text(g,HudText.fit("PLAYERS "+(s.targetPlayers() ? "ON" : "OFF")+" / TEAMS "+(s.respectTeams() ? "ON" : "OFF"),w-18),19,y+19,TEXT);
        text(g,HudText.fit("SENT "+LevrnClient.runtime.sentAttacks+" / COOLDOWN "+Math.round(s.cooldown()*100)+"%",w-18),19,y+32,MUTED);
    }
    private void target(GuiGraphics g,LivingEntity target,int width,int height) {
        int w=Math.min(150,width-20),x=Math.min(width-w-10,width/2+16),y=Math.max(10,height/2-55);
        panel(g,x,y,w,43);text(g,HudText.fit(target.getName().getString(),w-18),x+9,y+6,TEXT);
        float ratio=Math.clamp(target.getHealth()/Math.max(1,target.getMaxHealth()),0,1);
        g.fill(x+9,y+31,x+w-9,y+34,0x40FFFFFF);g.fill(x+9,y+31,x+9+Math.round((w-18)*ratio),y+34,ACCENT);
        String hp=String.format(Locale.ROOT,"%.1f HP",target.getHealth()+target.getAbsorptionAmount());
        text(g,hp,x+9,y+18,MUTED);
    }
    private void panel(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x,y,x+w,y+h,PANEL);border(g,x,y,w,h,ACCENT_DIM);int c=4;
        g.fill(x,y,x+c,y+1,ACCENT);g.fill(x,y,x+1,y+c,ACCENT);
        g.fill(x+w-c,y,x+w,y+1,ACCENT);g.fill(x+w-1,y,x+w,y+c,ACCENT);
        g.fill(x,y+h-1,x+c,y+h,ACCENT);g.fill(x,y+h-c,x+1,y+h,ACCENT);
        g.fill(x+w-c,y+h-1,x+w,y+h,ACCENT);g.fill(x+w-1,y+h-c,x+w,y+h,ACCENT);
    }
    private void border(GuiGraphics g,int x,int y,int w,int h,int color) {
        g.fill(x,y,x+w,y+1,color);g.fill(x,y+h-1,x+w,y+h,color);g.fill(x,y,x+1,y+h,color);g.fill(x+w-1,y,x+w,y+h,color);
    }
    private void text(GuiGraphics g,String value,int x,int y,int color) {HudText.draw(g,value,x,y,color);}
}
