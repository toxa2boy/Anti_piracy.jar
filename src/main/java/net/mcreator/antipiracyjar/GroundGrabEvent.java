package net.mcreator.antipiracyjar;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="anti_piracyjar")
public final class GroundGrabEvent {
    private static final Map<UUID,Grab> ACTIVE=new HashMap<>();
    private static final class Grab {
        final Vec3 position;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        int age, presses, lastPress=-10, sinking=-1;
        Grab(ServerPlayer player) { position=player.position(); dimension=player.level().dimension(); }
    }
    private static void send(ServerPlayer player,int effect) { PacketDistributor.sendToPlayer(player,new HorrorNetwork.EffectPayload(effect)); }
    public static boolean begin(ServerPlayer player) { return begin(player,true); }
    private static boolean begin(ServerPlayer player,boolean test) {
        if (!player.isAlive() || player.isSpectator() || !player.onGround() || player.isInWater() || player.isPassenger() || player.isSleeping()
                || ACTIVE.containsKey(player.getUUID()) || !HorrorDirector.start(player,"ground_grab",700,test)) return false;
        ACTIVE.put(player.getUUID(),new Grab(player));
        PacketDistributor.sendToPlayer(player,new HorrorNetwork.GrabStartPayload(player.getX(),player.getY(),player.getZ()));
        return true;
    }
    public static void receive(ServerPlayer player,int action) {
        Grab grab=ACTIVE.get(player.getUUID());
        if (grab==null) return;
        if (action==0) { finish(player,false); return; }
        if (action!=1 || grab.age<30 || grab.sinking>=0 || grab.age-grab.lastPress<2 || !player.isAlive()) return;
        grab.lastPress=grab.age;
        grab.presses++;
        send(player,200+grab.presses);
        if (grab.presses>=10) { grab.sinking=0; send(player,41); }
    }
    private static void schedule(net.minecraft.server.MinecraftServer server) {
        if (server.overworld().getGameTime()%20!=0 || HorrorDirector.clock(server)<18000 || !HorrorDirector.available(server)) return;
        var state=HorrorWorldState.get(server);
        if(state.data.getBoolean("groundGrabOccurred")) return;
        int stage=state.data.getInt("storyStage");
        if(stage<6 && HorrorDirector.clock(server)>=HorrorDirector.storyAt(server,stage)) return;
        var candidates=server.getPlayerList().getPlayers().stream().filter(p->p.isAlive() && !p.isCreative()
            && !p.isSpectator() && !p.isSleeping() && p.onGround() && !p.isInWater() && !p.isPassenger()
            && p.level().dimension()==net.minecraft.world.level.Level.OVERWORLD).toList();
        if(candidates.isEmpty()) return;
        var player=candidates.get(server.overworld().random.nextInt(candidates.size()));
        if(begin(player,false)) { state.data.putBoolean("groundGrabOccurred",true);state.flush(server); }
    }
    private static void finish(ServerPlayer player,boolean occurred) {
        ACTIVE.remove(player.getUUID());
        HorrorDirector.finish(player,"ground_grab",occurred);
        send(player,42);
    }
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();
        schedule(server);
        for (UUID id:java.util.List.copyOf(ACTIVE.keySet())) {
            var player=server.getPlayerList().getPlayer(id);
            var grab=ACTIVE.get(id);
            if (player==null) { ACTIVE.remove(id); HorrorDirector.finish(server,"ground_grab",id.toString(),true); continue; }
            if (!player.isAlive() || player.isSpectator() || player.level().dimension()!=grab.dimension
                    || player.position().distanceToSqr(grab.position)>100 || ++grab.age>600) { finish(player,true); continue; }
            player.stopRiding();
            player.setDeltaMovement(Vec3.ZERO); player.fallDistance=0; player.hurtMarked=true;
            if (player.position().distanceToSqr(grab.position)>.0001)
                player.connection.teleport(grab.position.x,grab.position.y,grab.position.z,player.getYRot(),player.getXRot());
            if (grab.sinking>=0) { if (++grab.sinking>=30) finish(player,true); }
            else if (grab.age>=40 && grab.age%5==0) {
                player.invulnerableTime=0;
                player.hurt(player.damageSources().generic(),1F);
            }
        }
    }
    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ACTIVE.remove(player.getUUID())!=null)
            HorrorDirector.finish(player,"ground_grab",true);
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); }
    @SubscribeEvent
    public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("druntest").requires(s->s.hasPermission(2))
            .then(net.minecraft.commands.Commands.literal("grab").executes(c->run(c.getSource(),c.getSource().getPlayerOrException()))
            .then(net.minecraft.commands.Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player())
            .executes(c->run(c.getSource(),net.minecraft.commands.arguments.EntityArgument.getPlayer(c,"player"))))));
    }
    private static int run(net.minecraft.commands.CommandSourceStack source,ServerPlayer player) {
        if (!begin(player)) { source.sendFailure(Component.literal("Stand on solid ground; another horror scene may be active.")); return 0; }
        source.sendSuccess(()->Component.literal("Drun grab started for "+player.getScoreboardName()+"."),false);return 1;
    }
}
