package net.mcreator.antipiracyjar;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.mcreator.antipiracyjar.entity.DrunEntity;
import net.mcreator.antipiracyjar.init.AntiPiracyjarModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="anti_piracyjar",value=Dist.CLIENT)
public final class GroundGrabClient {
    private static DrunEntity drun;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static Vec3 anchor, feet;
    private static int age, presses, sinking=-1, nextId=-5000000;
    private static boolean active;
    private static net.minecraft.client.multiplayer.ClientLevel soundWorld;
    private static final java.util.List<net.minecraft.client.resources.sounds.SoundInstance> sounds=new java.util.ArrayList<>();
    private static void sound(String id,float pitch,float volume) {
        var sound=net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
            net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.parse(id)),pitch,volume);
        sounds.add(sound);soundWorld=Minecraft.getInstance().level;Minecraft.getInstance().getSoundManager().play(sound);
    }
    private static void report(int action) { if (Minecraft.getInstance().getConnection()!=null) PacketDistributor.sendToServer(new HorrorNetwork.GrabStatePayload(action)); }
    public static void effect(int effect) {
        if (effect==41 && active) { sinking=0; drun.setEventAnimation("event_grab_sink"); }
        else if (effect==42) clear(false);
        else if (effect>=200 && effect<=210 && active) presses=effect-200;
    }
    public static void begin(HorrorNetwork.GrabStartPayload payload) {
        clear();
        var mc=Minecraft.getInstance();
        if (mc.player==null || mc.level==null) { report(0);return; }
        world=mc.level;anchor=new Vec3(payload.x(),payload.y(),payload.z());
        Vec3 front=Vec3.directionFromRotation(0,mc.player.getYRot());
        feet=anchor.add(front.scale(.30));
        drun=AntiPiracyjarModEntities.DRUN.get().create(world);
        if (drun==null) { report(0);return; }
        while(world.getEntity(nextId)!=null) nextId--;
        drun.setId(nextId--);drun.setNoAi(true);drun.setNoGravity(true);drun.noPhysics=true;
        drun.setSilent(true);drun.setInvulnerable(true);
        float yaw=mc.player.getYRot()+180;
        drun.moveTo(feet.x,feet.y-2.8,feet.z,yaw,0);
        drun.setYBodyRot(yaw);drun.setYHeadRot(yaw);drun.yRotO=drun.yBodyRotO=drun.yHeadRotO=yaw;
        drun.setEventAnimation("event_grab_rise");world.addEntity(drun);
        if (world.getEntity(drun.getId())!=drun) { clear();report(0);return; }
        active=true;age=presses=0;sinking=-1;
        sound("anti_piracyjar:drun_grab_eruption",1F,.95F);
    }
    @SubscribeEvent
    public static void key(InputEvent.Key event) {
        var mc=Minecraft.getInstance();
        if (active && age>=30 && sinking<0 && mc.screen==null && !mc.isPaused()
                && event.getKey()==org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE && event.getAction()==org.lwjgl.glfw.GLFW.GLFW_PRESS) report(1);
    }
    @SubscribeEvent
    public static void input(MovementInputUpdateEvent event) {
        if (!active) return;
        var input=event.getInput();input.forwardImpulse=input.leftImpulse=0;
        input.up=input.down=input.left=input.right=input.jumping=input.shiftKeyDown=false;
    }
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if (!active) {
            if (!sounds.isEmpty() && (mc.player==null || !mc.player.isAlive() || mc.level!=soundWorld)) clear();
            return;
        }
        if(mc.player==null || !mc.player.isAlive() || mc.level!=world || drun==null || drun.isRemoved()) { clear();return; }
        if(mc.isPaused()) return;
        if(++age>640) { report(0);clear();return; }
        mc.player.setDeltaMovement(Vec3.ZERO);mc.player.setPos(anchor);mc.player.setSprinting(false);
        double rise=Math.min(1,age/30.0);rise=rise*rise*(3-2*rise);
        double y=feet.y-2.8+rise*1.5;
        if(sinking>=0) { double t=Math.min(1,++sinking/30.0);y=feet.y-1.3-1.5*t*t; }
        else if(age==30) drun.setEventAnimation("event_grab_hold");
        drun.setPos(feet.x,y,feet.z);
        mc.player.displayClientMessage(Component.literal(sinking>=0 ? "You're free." : age<30 ? "Something has your legs." : "Press SPACE: "+presses+" / 10"),true);
    }
    public static void clear() { clear(true); }
    private static void clear(boolean stopAudio) {
        if(stopAudio) {
            for(var sound:sounds) Minecraft.getInstance().getSoundManager().stop(sound);
            sounds.clear();soundWorld=null;
        }
        if(world!=null && drun!=null && world.getEntity(drun.getId())==drun)world.removeEntity(drun.getId(),Entity.RemovalReason.DISCARDED);
        active=false;drun=null;world=null;anchor=feet=null;age=presses=0;sinking=-1;
    }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
