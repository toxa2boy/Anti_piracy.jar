package net.mcreator.antipiracyjar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;


import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "anti_piracyjar")
public final class DrunSleepEvent {
    private record Site(BlockPos target, BlockPos feet, BlockState original, boolean glass, java.util.List<BlockPos> route) {}
    private static class Visit {
        final Site site;
        final boolean test;
        int age;
        int knocking = -1;
        int voice;
        Visit(Site site, boolean test) { this.site = site; this.test = test; }
    }
    private static final Map<UUID, Visit> VISITS = new HashMap<>();
    private static final Set<UUID> SLEEPING = new HashSet<>();

    private static boolean glass(BlockState state) {
        return state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS) || state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES)
                || state.is(Blocks.GLASS_PANE);
    }

    private static Site find(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos bed = player.getSleepingPos().orElse(player.blockPosition());
        Site best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos mutable : BlockPos.betweenClosed(bed.offset(-8,0,-8),bed.offset(8,4,8))) {
            BlockPos pos = mutable.immutable();
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            boolean window = glass(state);
            if (!window && !(state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                    && !state.getValue(DoorBlock.OPEN))) continue;
            for (Direction outward : Direction.Plane.HORIZONTAL) {
                BlockPos inside = pos.relative(outward.getOpposite());
                BlockPos outside = pos.relative(outward);
                if (!level.hasChunkAt(outside) || outside.distSqr(bed) <= inside.distSqr(bed)) continue;
                Vec3 eye = Vec3.atCenterOf(bed).add(0,.7,0);
                if (level.clip(new ClipContext(eye, Vec3.atCenterOf(inside), ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) continue;
                BlockPos feet = window ? outside.below(2) : outside;
                if (!level.isInWorldBounds(feet) || !level.hasChunkAt(feet)
                        || !level.getBlockState(feet.below()).isFaceSturdy(level,feet.below(),Direction.UP)
                        || !level.canSeeSky(feet.above(3))) continue;
                double headY = feet.getY() + 2.375;
                if (window && (headY < pos.getY() + .15 || headY > pos.getY() + .85)) continue;
                Vec3 center = Vec3.atBottomCenterOf(feet);
                if (!level.noCollision(new AABB(center.x-.3, center.y, center.z-.3, center.x+.3,center.y+2.7,center.z+.3))) continue;
                double distance = pos.distSqr(bed) + (window ? 0 : 4);
                if (distance < bestDistance) { var route = DrunVisitPath.find(level,feet);
                    if (!route.isEmpty()) { bestDistance = distance; best = new Site(pos,feet,state,window,route); } }
            }
        }
        return best;
    }

    private static boolean begin(ServerPlayer player, boolean test) {
        if (VISITS.containsKey(player.getUUID()) || player.level().dimension() != Level.OVERWORLD) return false;
        if (!HorrorDirector.available(player.server) && !test) return false;
        if (!test && HorrorDirector.clock(player.server)<HorrorDirector.at(player.server,23)) return false;
        Site site = find(player);
        if (site == null) return false;
        for (Visit visit : VISITS.values()) if (visit.site.target().equals(site.target())) return false;
        if (!HorrorDirector.start(player,"knock",660,test)) return false;
        var visit = new Visit(site,test);
        visit.voice = player.getRandom().nextInt(6);
        VISITS.put(player.getUUID(),visit);
        PacketDistributor.sendToPlayer(player,new HorrorNetwork.KnockPayload(site.target(),site.feet(),site.route(),visit.voice,false));
        return true;
    }

    private static void stop(ServerPlayer player, Visit visit) {
        HorrorDirector.finish(player,"knock",true);
        PacketDistributor.sendToPlayer(player,new HorrorNetwork.KnockPayload(visit.site.target(),visit.site.feet(),java.util.List.of(),0,true));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        SLEEPING.removeIf(id -> { var p = server.getPlayerList().getPlayer(id); return p == null || !p.isSleeping(); });
        for (var player : server.getPlayerList().getPlayers()) {
            if (!player.isSleeping() || player.level().dimension() != Level.OVERWORLD || !SLEEPING.add(player.getUUID())) continue;
            var saved = HorrorWorldState.get(server);
            String key = "drunKnockNight_" + player.getUUID();
            long night = player.serverLevel().getDayTime() / 24000;
            long time = player.serverLevel().getDayTime() % 24000;
            if (time < 12542 || time > 23460 || (saved.data.contains(key) && saved.data.getLong(key) == night)) continue;
            saved.data.putLong(key,night); saved.setDirty();
            if (player.getRandom().nextFloat() < .50F) begin(player,false);
        }
        var iterator = VISITS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var player = server.getPlayerList().getPlayer(entry.getKey());
            Visit visit = entry.getValue();
            if (player == null) { iterator.remove(); continue; }
            if (!player.isAlive() || player.level().dimension() != Level.OVERWORLD) {
                stop(player,visit); iterator.remove(); continue;
            }
            visit.age++;
            var level = player.serverLevel();
            if (!level.hasChunkAt(visit.site.target()) || visit.age > 600) {
                stop(player,visit); iterator.remove(); continue;
            }
            if (visit.knocking < 0) continue;
            visit.knocking++;
            if (visit.knocking == 12 || visit.knocking == 26 || visit.knocking == 40) {
                SoundEvent sound = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("anti_piracyjar",
                        visit.site.glass() ? "drun_knock_glass" : "drun_knock_wood"));
                level.playSound(null,visit.site.glass() ? visit.site.target() : visit.site.target().above(),sound,SoundSource.HOSTILE,1F,1F);
                if (player.isSleeping()) { HorrorDirector.markForcedWake(player); player.stopSleepInBed(true,true); }
            }
            if (visit.knocking == 52) {
                SoundEvent sound = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("anti_piracyjar","drun_voice_" + visit.voice));
                level.playSound(null,visit.site.target(),sound,SoundSource.HOSTILE,1F,1F);
            }
        }
    }

    public static void receive(ServerPlayer player, int action) {
        Visit visit = VISITS.get(player.getUUID());
        if (visit == null) return;
        if (action == 1 && visit.knocking < 0 && visit.age >= 5) visit.knocking = 0;
        if (action == 2) { VISITS.remove(player.getUUID()); HorrorDirector.finish(player,"knock",visit.knocking>=0); }
    }

    @SubscribeEvent
    public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("druntest").requires(source -> source.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("inventory").executes(context -> {
                    PacketDistributor.sendToPlayer(context.getSource().getPlayerOrException(),new HorrorNetwork.EffectPayload(25));
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("knock").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    boolean result = begin(player,true);
                    if (!result) context.getSource().sendFailure(Component.literal("No suitable window or closed door with a hidden walkable approach outside within 8 blocks, or a visit is already active."));
                    return result ? 1 : 0;
                })));
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { VISITS.clear(); SLEEPING.clear(); }
}
