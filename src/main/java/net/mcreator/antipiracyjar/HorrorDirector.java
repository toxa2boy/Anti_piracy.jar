package net.mcreator.antipiracyjar;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid="anti_piracyjar")
public final class HorrorDirector {
    private static MinecraftServer loaded;
    private static SceneGate gate = new SceneGate();
    private static long clock;
    private static boolean multiplayer;
    private static boolean sceneTest;
    private static int partySize;
    private static final int[] STORY_MINUTES = {5,10,18,26,34,38};

    private static void load(MinecraftServer server) {
        if (loaded == server) return;
        loaded=server; gate=new SceneGate();
        var data=HorrorWorldState.get(server).data;
        clock=data.getLong("directorClock");
        multiplayer=data.getBoolean("directorMultiplayer");
        partySize=Math.max(1,data.getInt("directorPartySize")); sceneTest=false;
        gate.restoreQuiet(data.getLong("directorQuiet"));
        if (data.getBoolean("directorActive")) {
            gate.restoreQuiet(clock+(multiplayer ? 2400 : 1800));
            String type=data.getString("directorActiveType"), owner=data.getString("directorActiveOwner");
            if (!type.isEmpty() && !data.getBoolean("directorActiveTest")) record(server,type,owner);
            data.putBoolean("directorActive",false);
            HorrorWorldState.get(server).setDirty();
        }
    }

    public static long clock(MinecraftServer server) { load(server); return clock; }
    public static long at(MinecraftServer server, int minutes) { load(server); return minutes*(multiplayer ? 1440L : 1200L); }
    public static long storyAt(MinecraftServer server, int stage) { return at(server,STORY_MINUTES[Math.min(stage,5)]); }
    public static boolean active(MinecraftServer server, String type) { load(server); return gate.type().equals(type); }
    public static boolean owns(ServerPlayer player, String type) { load(player.server); return gate.matches(type,player.getUUID().toString()); }
    public static boolean available(MinecraftServer server) { load(server); return gate.idle(clock); }

    private static void save(MinecraftServer server) {
        var state=HorrorWorldState.get(server);
        state.data.putLong("directorClock",clock);
        state.data.putLong("directorQuiet",gate.quietUntil());
        state.data.putBoolean("directorActive",gate.active());
        state.data.putString("directorActiveType",gate.type());
        state.data.putString("directorActiveOwner",gate.owner());
        state.data.putBoolean("directorActiveTest",sceneTest);
        state.data.putBoolean("directorMultiplayer",multiplayer);
        state.data.putInt("directorPartySize",partySize);
        state.setDirty();
    }

    private static int pause(MinecraftServer server) { return (multiplayer ? 2400 : 1800)+server.overworld().random.nextInt(1201); }

    private static int limit(String type) {
        return switch (type) {
            case "turnaround", "knock" -> 1;
            case "watch", "roommate", "inventory" -> 2;
            case "omen_chat" -> 3;
            default -> Integer.MAX_VALUE;
        };
    }

    private static boolean underLimit(MinecraftServer server,String type,String owner) {
        int personal=limit(type);
        if (personal==Integer.MAX_VALUE) return true;
        var data=HorrorWorldState.get(server).data;
        int repeatMinute=switch(type) {
            case "watch" -> 25;
            case "inventory" -> 30;
            case "roommate" -> 33;
            default -> 0;
        };
        if (repeatMinute>0 && data.getInt("directorCount_"+type)>0 && clock<at(server,repeatMinute)) return false;
        int total=personal+(type.equals("omen_chat") ? 0 : Math.min(3,partySize-1));
        if (data.getInt("directorCount_"+type)>=total) return false;
        if (!owner.isEmpty() && data.getInt("directorCount_"+type+"_"+owner)>=personal) return false;
        return !type.equals(data.getString("directorLastType"));
    }

    public static boolean start(MinecraftServer server, String type, String owner, int ticks, boolean test) {
        load(server);
        if (!test && !underLimit(server,type,owner)) return false;
        if (!gate.start(clock,type,owner,ticks,test)) return false;
        sceneTest=test;
        save(server);
        AntiPiracyjarMod.LOGGER.info("Horror scene started: {} at pacing tick {}",type,clock);
        return true;
    }

    public static boolean start(ServerPlayer player, String type, int ticks, boolean test) {
        return start(player.server,type,player.getUUID().toString(),ticks,test);
    }

    public static void renew(ServerPlayer player, String type, int ticks) {
        load(player.server); gate.renew(clock,type,player.getUUID().toString(),ticks);
    }

    public static void finish(MinecraftServer server, String type, String owner, boolean occurred) {
        load(server);
        if (!gate.finish(clock,type,owner,occurred ? pause(server) : 100)) return;
        if (occurred && !sceneTest) record(server,type,owner);
        sceneTest=false;
        save(server);
        AntiPiracyjarMod.LOGGER.info("Horror scene ended: {}; quiet until {}",type,gate.quietUntil());
    }

    private static void record(MinecraftServer server,String type,String owner) {
        var state=HorrorWorldState.get(server);
        state.data.putLong("directorLast_"+type+"_"+owner,clock);
        state.data.putString("directorLastType",type);
        state.data.putInt("directorCount_"+type,state.data.getInt("directorCount_"+type)+1);
        if (!owner.isEmpty()) state.data.putInt("directorCount_"+type+"_"+owner,state.data.getInt("directorCount_"+type+"_"+owner)+1);
        state.setDirty();
    }

    public static void finish(ServerPlayer player, String type, boolean occurred) {
        finish(player.server,type,player.getUUID().toString(),occurred);
    }

    public static boolean roll(ServerPlayer player, String type, int earliestMinute, int minimumRepeatSeconds, int meanTicks) {
        return roll(player,type,earliestMinute,minimumRepeatSeconds,meanTicks,false);
    }

    public static boolean rollShared(ServerPlayer player, String type, int earliestMinute, int minimumRepeatSeconds, int meanTicks) {
        return roll(player,type,earliestMinute,minimumRepeatSeconds,meanTicks,true);
    }

    private static boolean roll(ServerPlayer player, String type, int earliestMinute, int minimumRepeatSeconds, int meanTicks, boolean shared) {
        if (!available(player.server) || clock < at(player.server,earliestMinute) || !player.isAlive()
                || player.isSpectator() || player.isSleeping() || player.level().dimension()!=Level.OVERWORLD) return false;
        if (!underLimit(player.server,type,shared ? "" : player.getUUID().toString())) return false;
        var state=HorrorWorldState.get(player.server);
        String key="directorLast_"+type+"_"+(shared ? "" : player.getUUID());
        if (state.data.contains(key) && clock-state.data.getLong(key)<minimumRepeatSeconds*20L) return false;
        int stage=state.data.getInt("storyStage");
        if (stage<6 && clock>=storyAt(player.server,stage)) return false;
        if (state.data.getBoolean("messagesFinished") && !state.data.getBoolean("pirateDone") && clock>=at(player.server,40)) return false;
        long count=player.server.getPlayerList().getPlayers().stream().filter(p->p.isAlive() && !p.isSpectator() && p.level().dimension()==Level.OVERWORLD).count();
        double chance=-Math.expm1(20.0/(shared ? 1 : Math.max(1,count))*Math.log1p(-1.0/meanTicks));
        return player.getRandom().nextDouble()<chance;
    }

    public static boolean forcedWake(ServerPlayer player) {
        return player.getPersistentData().getLong("directorForcedWakeUntil")>=player.server.overworld().getGameTime()
                && player.getPersistentData().contains("directorForcedWakeUntil");
    }

    public static void markForcedWake(ServerPlayer player) {
        player.getPersistentData().putLong("directorForcedWakeUntil",player.server.overworld().getGameTime()+100);
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer(); load(server);
        if (server.getPlayerList().getPlayers().stream().noneMatch(p->p.isAlive() && !p.isSpectator())) return;
        clock++;
        partySize=Math.max(partySize,server.getPlayerList().getPlayerCount());
        if (partySize>1) multiplayer=true;
        boolean dimensionScene=server.getPlayerList().getPlayers().stream().anyMatch(p->p.isAlive()
                && p.level().dimension().location().toString().equals("anti_piracyjar:flat_land"));
        boolean huntScene=HorrorWorldState.get(server).data.hasUUID(StoneHuntEvent.HUNTER)
                && server.getPlayerList().getPlayers().stream().anyMatch(p->p.isAlive() && !p.isSpectator()
                && p.level().dimension().location().toString().equals("anti_piracyjar:kamniizmerenie"));
        if ((dimensionScene || huntScene) && !gate.active()) {
            gate.start(clock,"dimension_scene","",100,true);
            sceneTest=false;
        }
        if (huntScene && (gate.type().equals("stone_hunt") || gate.type().equals("dimension_scene")))
            gate.renew(clock,gate.type(),gate.owner(),100);
        if (dimensionScene && (gate.type().equals("dimension_scene") || gate.type().equals("pirate") || gate.type().equals("dream")))
            gate.renew(clock,gate.type(),gate.owner(),100);
        if (gate.expired(clock)) finish(server,gate.type(),gate.owner(),true);
        if (clock%20==0) save(server);
    }

    @SubscribeEvent
    public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("horrorpacing").requires(s->s.hasPermission(2))
                .executes(context->{var server=context.getSource().getServer();load(server);
                    String text="Pacing: "+(clock/1200)+" min; scene: "+(gate.active()?gate.type():"none")
                            +"; quiet: "+Math.max(0,(gate.quietUntil()-clock+19)/20)+" sec; finale target: "+(multiplayer?48:40)+" min.";
                    context.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal(text),false);return 1;}));
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { loaded=null; gate=new SceneGate(); }
}
